/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.engine;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.EngineBackgroundTasks.BackgroundTaskDrain;
import art.arcane.iris.engine.EngineRuntimeBuilder.RuntimeAssembly;
import art.arcane.iris.engine.IrisEngine.LifecycleState;
import art.arcane.iris.engine.framework.GenerationSessionException;
import art.arcane.iris.engine.framework.EngineTarget;
import art.arcane.iris.engine.framework.NativeStructureOwnershipStore;
import art.arcane.iris.engine.framework.PreservationRegistry;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.engine.history.GenerationAdmission;
import art.arcane.iris.engine.history.GenerationHistory;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the ordered teardown of a single {@link IrisEngine}, both for a normal close and for a
 * construction that failed part way through. Every resource release is gated on the previous one
 * having succeeded so a partially released engine never reports itself as closed.
 */
final class EngineShutdownSequence {
    private static final long CLOSE_RETRY_DRAIN_TIMEOUT_MILLIS = 5000L;

    private final IrisEngine engine;
    private final List<GenerationAdmission.RuntimeLease> generationAdmissions = new ArrayList<>(1);
    private final Set<RuntimeAssembly> incompleteAssemblies = ConcurrentHashMap.newKeySet();
    private final Set<EngineRuntime> unpublishedRuntimes = Collections.synchronizedSet(
            Collections.newSetFromMap(new IdentityHashMap<>()));
    private final Set<EngineTarget> incompleteTargets = Collections.synchronizedSet(
            Collections.newSetFromMap(new IdentityHashMap<>()));
    private boolean runtimeReleased;
    private boolean worldManagerStopped;
    private boolean targetReleased;
    private boolean engineDataReleased;
    private boolean preservationReleased;

    EngineShutdownSequence(IrisEngine engine) {
        this.engine = engine;
    }

    void retainGenerationHistory(GenerationHistory history) {
        generationAdmissions.add(history.retainRuntime());
    }

    void close() {
        if (!engine.beginShutdown()) {
            return;
        }
        Throwable routerFailure = stopWorldManager();
        if (routerFailure == null) {
            routerFailure = runCleanup(null, engine::closeAttachedGenerationHistoryRuntimeRouter);
        }
        if (routerFailure != null) {
            synchronized (engine.lifecycleLock) {
                engine.lifecycleState = LifecycleState.FAILED;
            }
            reportIncompleteClose(routerFailure);
            return;
        }
        Throwable failure;
        synchronized (engine.lifecycleLock) {
            if (engine.closed) {
                return;
            }
            engine.awaitNativeStructureBootstrap("close");
            engine.lifecycleState = LifecycleState.CLOSING;
            engine.getClosing().set(true);
            engine.backgroundTasks.closeBackgroundTaskAdmission();
            EngineTickRegistry.unregisterTicking(engine);
            // Best-effort like every other step: a pregen join timeout must not escape the
            // synchronized block before anything is saved or released. On failure, save what
            // can be saved and leave the close incomplete-but-retryable (a live pregen writer
            // may still hold the mantle, so the releases are skipped).
            Throwable pregenFailure = runCleanup(null, () -> engine.getPlatformHooks().shutdownPregenerator(engine));
            if (pregenFailure != null) {
                engine.lifecycleState = LifecycleState.FAILED;
                failure = pregenFailure;
                failure = runCleanup(failure, engine::savePrefetchOnce);
                failure = runCleanup(failure, engine::saveEngineData);
                failure = runCleanup(failure, () -> engine.getMantle().saveAllNow());
                reportIncompleteClose(failure);
                return;
            }
            Throwable drainFailure = null;
            try {
                engine.getGenerationSessions().sealAndAwait("close", IrisEngine.SESSION_DRAIN_TIMEOUT_MILLIS, true);
            } catch (GenerationSessionException e) {
                drainFailure = e;
            }
            if (drainFailure != null) {
                // A drain timeout must not abandon teardown: give remaining admitted work
                // one final bounded drain before deciding whether resources can be released.
                IrisLogging.warn("Iris generation did not drain for close on " + engine.getWorld().name()
                        + "; waiting briefly for remaining work.");
                try {
                    engine.getGenerationSessions().sealAndAwait("close-retry", CLOSE_RETRY_DRAIN_TIMEOUT_MILLIS, true);
                    drainFailure = null;
                } catch (GenerationSessionException e) {
                    drainFailure = appendFailure(drainFailure, e);
                }
            }
            if (drainFailure != null) {
                // A live lease may be mid-write, so the mantle must not be closed at it — but
                // dirty plates can still be flushed (saveAll is synchronized) so terrain since
                // the last periodic save is not lost. The close stays incomplete and retryable.
                engine.lifecycleState = LifecycleState.FAILED;
                failure = runCleanup(drainFailure, () -> engine.getMantle().saveAllNow());
                reportIncompleteClose(failure);
                return;
            }

            BackgroundTaskDrain backgroundDrain = engine.backgroundTasks.drainBackgroundTasks("close");
            failure = backgroundDrain.failure();
            if (!backgroundDrain.allowsResourceRelease() && failure == null) {
                failure = new IllegalStateException("Iris background tasks remain active during close.");
            }

            if (backgroundDrain.allowsResourceRelease()) {
                Throwable ownershipFailure = runCleanup(null,
                        () -> NativeStructureOwnershipStore.close(engine));
                failure = appendFailure(failure, ownershipFailure);
                if (ownershipFailure == null) {
                    Throwable prefetchFailure = runCleanup(null, engine::savePrefetchOnce);
                    Throwable engineDataFailure = runCleanup(null, engine::saveEngineData);
                    failure = appendFailure(failure, prefetchFailure);
                    failure = appendFailure(failure, engineDataFailure);
                    failure = releaseRuntime(failure);
                    if (runtimeReleased) {
                        failure = releaseTarget(failure);
                    }
                    if (prefetchFailure == null
                            && engineDataFailure == null
                            && runtimeReleased
                            && targetReleased) {
                        failure = releaseEngineDataForShutdown(failure);
                    }
                    if (engineDataReleased) {
                        failure = releasePreservation(failure);
                    }
                }
            }
            if (failure == null
                    && runtimeReleased
                    && targetReleased
                    && engineDataReleased
                    && preservationReleased) {
                failure = releaseGenerationAdmissions(failure);
                if (failure == null) {
                    engine.closed = true;
                    engine.lifecycleState = LifecycleState.CLOSED;
                    IrisLogging.debug("Engine Fully Shutdown!");
                }
            }
        }
        if (failure != null) {
            reportIncompleteClose(failure);
        }
    }

    private Throwable releaseGenerationAdmissions(Throwable failure) {
        Iterator<GenerationAdmission.RuntimeLease> admissions = generationAdmissions.iterator();
        while (admissions.hasNext()) {
            GenerationAdmission.RuntimeLease admission = admissions.next();
            Throwable admissionFailure = runCleanup(null, admission::close);
            failure = appendFailure(failure, admissionFailure);
            if (admissionFailure == null) {
                admissions.remove();
            }
        }
        return failure;
    }

    private void reportIncompleteClose(Throwable failure) {
        IrisLogging.error("Iris engine shutdown remains incomplete after cleanup failures for " + engine.getWorld().name() + ".");
        IrisLogging.reportError(failure);
        throw new IllegalStateException("Iris engine shutdown remains incomplete after cleanup failures.", failure);
    }

    void cleanupFailedConstruction(Throwable original) {
        engine.getClosing().set(true);
        engine.backgroundTasks.closeBackgroundTaskAdmission();
        EngineTickRegistry.unregisterTicking(engine);
        engine.lifecycleState = LifecycleState.FAILED;
        Throwable cleanupFailure = null;
        boolean sessionsDrained = true;
        try {
            engine.getGenerationSessions().sealAndAwait("failed initialization", 0L, true);
        } catch (Throwable e) {
            sessionsDrained = false;
            cleanupFailure = appendFailure(cleanupFailure, e);
        }
        engine.backgroundTasks.cancelBackgroundTasks("failed initialization");
        BackgroundTaskDrain backgroundDrain = engine.backgroundTasks.drainBackgroundTasks("failed initialization");
        cleanupFailure = appendFailure(cleanupFailure, backgroundDrain.failure());
        if (!sessionsDrained || !backgroundDrain.allowsResourceRelease()) {
            if (!backgroundDrain.allowsResourceRelease()) {
                cleanupFailure = appendFailure(cleanupFailure,
                        new IllegalStateException("Iris background tasks remain active after failed initialization."));
            }
            if (cleanupFailure != original) {
                original.addSuppressed(cleanupFailure);
            }
            return;
        }
        Throwable ownershipFailure = runCleanup(null,
                () -> NativeStructureOwnershipStore.close(engine));
        cleanupFailure = appendFailure(cleanupFailure, ownershipFailure);
        if (ownershipFailure == null) {
            cleanupFailure = releaseRuntime(cleanupFailure);
            if (runtimeReleased) {
                cleanupFailure = releaseTarget(cleanupFailure);
            }
            if (targetReleased) {
                cleanupFailure = releaseEngineDataForShutdown(cleanupFailure);
            }
            if (engineDataReleased) {
                cleanupFailure = releasePreservation(cleanupFailure);
            }
            if (runtimeReleased && targetReleased && engineDataReleased && preservationReleased) {
                cleanupFailure = releaseGenerationAdmissions(cleanupFailure);
                if (generationAdmissions.isEmpty()) {
                    engine.closed = true;
                }
            }
        }
        if (cleanupFailure != null && cleanupFailure != original) {
            original.addSuppressed(cleanupFailure);
        }
    }

    Throwable closeAssembly(RuntimeAssembly assembly, Throwable failure) {
        if (assembly == null) {
            return failure;
        }
        incompleteAssemblies.add(assembly);
        RuntimeAssembly previous = engine == null ? null : engine.runtimeAssembly.get();
        if (engine != null) {
            engine.runtimeAssembly.set(assembly);
        }
        Throwable assemblyFailure;
        try {
            assemblyFailure = closeAssemblyResources(assembly);
        } finally {
            if (engine != null) {
                if (previous == null) {
                    engine.runtimeAssembly.remove();
                } else {
                    engine.runtimeAssembly.set(previous);
                }
            }
        }
        if (assemblyFailure == null) {
            incompleteAssemblies.remove(assembly);
        } else if (engine != null) {
            engine.lifecycleState = LifecycleState.FAILED;
        }
        return appendFailure(failure, assemblyFailure);
    }

    void retainUnpublishedRuntime(EngineRuntime runtime) {
        unpublishedRuntimes.add(runtime);
    }

    boolean retainsData(IrisData data) {
        if (engine.runtime != null && engine.runtime.generation().data() == data) {
            return true;
        }
        for (RuntimeAssembly assembly : incompleteAssemblies) {
            if (assembly.target.getData() == data) {
                return true;
            }
        }
        for (EngineRuntime runtime : unpublishedRuntimes.toArray(new EngineRuntime[0])) {
            if (runtime.generation().data() == data) {
                return true;
            }
        }
        for (EngineTarget target : incompleteTargets.toArray(new EngineTarget[0])) {
            if (target.getData() == data) {
                return true;
            }
        }
        return false;
    }

    Throwable closeRuntime(EngineRuntime engineRuntime, Throwable failure) {
        return closeRuntime(engineRuntime, null, failure);
    }

    Throwable closeRuntime(
            EngineRuntime engineRuntime,
            EngineMantle retainedMantle,
            Throwable failure
    ) {
        if (engineRuntime == null) {
            return failure;
        }
        Throwable serviceFailure = engineRuntime == engine.runtime
                ? stopWorldManager()
                : runCleanup(null, engineRuntime.worldManager()::close);
        serviceFailure = runCleanup(serviceFailure, engineRuntime.effects()::close);
        if (serviceFailure != null) {
            return appendFailure(failure, serviceFailure);
        }
        return closeGenerationRuntime(engineRuntime.generation(), retainedMantle, failure);
    }

    Throwable closeGenerationRuntime(GenerationRuntime generationRuntime, Throwable failure) {
        return closeGenerationRuntime(generationRuntime, null, failure);
    }

    Throwable closeGenerationRuntime(
            GenerationRuntime generationRuntime,
            EngineMantle retainedMantle,
            Throwable failure
    ) {
        if (generationRuntime == null) {
            return failure;
        }
        Throwable modeFailure = runCleanup(null, generationRuntime.mode()::close);
        if (modeFailure != null) {
            return appendFailure(failure, modeFailure);
        }
        Throwable complexFailure = runCleanup(null, generationRuntime.complex()::close);
        failure = appendFailure(failure, complexFailure);
        if (complexFailure != null) {
            return failure;
        }
        failure = runCleanup(failure, () -> generationRuntime.hash32().cancel(true));
        if (generationRuntime.mantle() != retainedMantle) {
            failure = appendFailure(failure, closeMantle(generationRuntime.mantle()));
        }
        return failure;
    }

    Throwable closeDetachedGenerationRuntime(GenerationRuntime generationRuntime, Throwable failure) {
        IrisEngine.GenerationRuntimeBinding binding = new IrisEngine.GenerationRuntimeBinding(
                engine,
                generationRuntime);
        Throwable runtimeFailure;
        try (IrisEngine.GenerationRuntimeScope ignored = engine.generationRuntimeScopes.open(binding)) {
            runtimeFailure = closeGenerationRuntime(generationRuntime, null);
        } catch (Throwable scopeFailure) {
            runtimeFailure = scopeFailure;
        }
        if (runtimeFailure != null) {
            return appendFailure(failure, runtimeFailure);
        }
        return closeDetachedTarget(generationRuntime.target(), failure);
    }

    Throwable closeDetachedTarget(EngineTarget target, Throwable failure) {
        for (RuntimeAssembly assembly : incompleteAssemblies) {
            if (assembly.target == target) {
                return appendFailure(failure,
                        new IllegalStateException("Iris runtime assembly still owns the detached target."));
            }
        }
        for (EngineRuntime runtime : unpublishedRuntimes.toArray(new EngineRuntime[0])) {
            if (runtime.generation().target() == target) {
                return appendFailure(failure,
                        new IllegalStateException("Iris unpublished runtime still owns the detached target."));
            }
        }
        failure = runCleanup(failure, () -> target.getData().unregisterEngine(engine));
        failure = runCleanup(failure, target::close);
        failure = runCleanup(failure, target.getData()::close);
        return failure;
    }

    private Throwable releaseRuntime(Throwable failure) {
        if (runtimeReleased) {
            return failure;
        }
        Throwable incompleteFailure = closeIncompleteRuntimes();
        if (incompleteFailure != null) {
            return appendFailure(failure, incompleteFailure);
        }
        Throwable detachedFailure = closeDetachedGenerationRuntimes(null);
        if (detachedFailure != null) {
            return appendFailure(failure, detachedFailure);
        }
        Throwable runtimeFailure = closeRuntime(engine.runtime, null);
        if (runtimeFailure != null) {
            return appendFailure(failure, runtimeFailure);
        }
        engine.runtime = null;
        runtimeReleased = true;
        return failure;
    }

    private Throwable stopWorldManager() {
        if (worldManagerStopped || engine.runtime == null) {
            return null;
        }
        Throwable failure = runCleanup(null, engine.runtime.worldManager()::close);
        if (failure == null) {
            worldManagerStopped = true;
        }
        return failure;
    }

    private Throwable closeAssemblyResources(RuntimeAssembly assembly) {
        Throwable failure = runCleanup(null, () -> {
            if (assembly.worldManager != null) {
                assembly.worldManager.close();
            }
        });
        failure = runCleanup(failure, () -> {
            if (assembly.effects != null) {
                assembly.effects.close();
            }
        });
        if (failure != null) {
            return failure;
        }
        failure = runCleanup(null, () -> {
            if (assembly.mode != null) {
                assembly.mode.close();
            }
        });
        if (failure != null) {
            return failure;
        }
        failure = runCleanup(null, () -> {
            if (assembly.complex != null) {
                assembly.complex.close();
            }
        });
        if (failure != null) {
            return failure;
        }
        failure = runCleanup(null, () -> {
            if (assembly.hash32 != null) {
                assembly.hash32.cancel(true);
            }
        });
        if (assembly.mantle != null && assembly.ownsMantle) {
            Throwable mantleFailure = closeMantle(assembly.mantle);
            if (mantleFailure == null) {
                assembly.ownsMantle = false;
            }
            failure = appendFailure(failure, mantleFailure);
        }
        return failure;
    }

    private Throwable closeIncompleteRuntimes() {
        Throwable failure = null;
        for (RuntimeAssembly assembly : incompleteAssemblies.toArray(new RuntimeAssembly[0])) {
            Throwable assemblyFailure = closeAssembly(assembly, null);
            if (assemblyFailure == null && assembly.target != engine.publishedTarget) {
                incompleteTargets.add(assembly.target);
            }
            failure = appendFailure(failure, assemblyFailure);
        }
        EngineMantle retainedMantle = engine.runtime == null ? null : engine.runtime.generation().mantle();
        for (EngineRuntime unpublished : unpublishedRuntimes.toArray(new EngineRuntime[0])) {
            Throwable runtimeFailure = closeRuntime(unpublished, retainedMantle, null);
            if (runtimeFailure == null) {
                unpublishedRuntimes.remove(unpublished);
                if (unpublished.generation().target() != engine.publishedTarget) {
                    incompleteTargets.add(unpublished.generation().target());
                }
            }
            failure = appendFailure(failure, runtimeFailure);
        }
        for (EngineTarget target : incompleteTargets.toArray(new EngineTarget[0])) {
            Throwable targetFailure = closeDetachedTarget(target, null);
            if (targetFailure == null) {
                incompleteTargets.remove(target);
            }
            failure = appendFailure(failure, targetFailure);
        }
        return failure;
    }

    private static Throwable closeMantle(EngineMantle mantle) {
        Throwable failure = runCleanup(null, mantle::saveAllNow);
        return failure == null ? runCleanup(null, mantle::close) : failure;
    }

    private Throwable closeDetachedGenerationRuntimes(Throwable failure) {
        GenerationRuntime[] detachedRuntimes = engine.detachedGenerationRuntimes.toArray(new GenerationRuntime[0]);
        for (GenerationRuntime detached : detachedRuntimes) {
            Throwable detachedFailure = closeDetachedGenerationRuntime(detached, null);
            failure = appendFailure(failure, detachedFailure);
            if (detachedFailure == null) {
                engine.detachedGenerationRuntimes.remove(detached);
            }
        }
        return failure;
    }

    private Throwable releaseTarget(Throwable failure) {
        if (targetReleased) {
            return failure;
        }
        Throwable targetFailure = runCleanup(null, engine.publishedTarget::close);
        if (targetFailure != null) {
            return appendFailure(failure, targetFailure);
        }
        targetReleased = true;
        return failure;
    }

    private Throwable releaseEngineDataForShutdown(Throwable failure) {
        if (engineDataReleased) {
            return failure;
        }
        Throwable dataFailure = runCleanup(null, engine.engineDataStore::releaseEngineData);
        if (dataFailure != null) {
            return appendFailure(failure, dataFailure);
        }
        engineDataReleased = true;
        return failure;
    }

    private Throwable releasePreservation(Throwable failure) {
        if (preservationReleased) {
            return failure;
        }
        Throwable preservationFailure = runCleanup(null, () -> {
            PreservationRegistry registry = IrisServices.getOrNull(PreservationRegistry.class);
            if (registry != null) {
                registry.dereference();
            }
        });
        if (preservationFailure != null) {
            return appendFailure(failure, preservationFailure);
        }
        preservationReleased = true;
        return failure;
    }

    static Throwable runCleanup(Throwable failure, Runnable cleanup) {
        try {
            cleanup.run();
        } catch (Throwable e) {
            return appendFailure(failure, e);
        }
        return failure;
    }

    static Throwable appendFailure(Throwable failure, Throwable additional) {
        if (additional == null) {
            return failure;
        }
        if (failure == null) {
            return additional;
        }
        if (failure != additional) {
            failure.addSuppressed(additional);
        }
        return failure;
    }

    static RuntimeException propagate(Throwable throwable) {
        if (throwable instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (throwable instanceof Error error) {
            throw error;
        }
        return new IllegalStateException(throwable);
    }
}
