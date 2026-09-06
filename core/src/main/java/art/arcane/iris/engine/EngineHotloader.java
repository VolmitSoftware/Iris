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
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.protocol.IrisProtocolServer;
import art.arcane.iris.engine.GenerationRuntime.BiomeMaxes;
import art.arcane.iris.engine.EngineRuntimeBuilder.RuntimeAssembly;
import art.arcane.iris.engine.IrisEngine.LifecycleState;
import art.arcane.iris.engine.framework.EngineTarget;
import art.arcane.iris.engine.framework.IrisStructureLocator;
import art.arcane.iris.engine.framework.StructureReachability;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.history.GenerationHistory;
import art.arcane.iris.engine.history.IrisBoundarySignatureSampler;
import art.arcane.iris.engine.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.engine.history.GenerationManifest;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.util.project.context.IrisContext;

import static art.arcane.iris.engine.EngineShutdownSequence.runCleanup;

/**
 * Reloads mutable engine runtimes and publishes immutable Studio generation activations.
 * Studio transitions drain sessions outside the lifecycle lock before freezing chunk ownership.
 * Failures before publication preserve the previous runtime; durable publication failures stop generation.
 */
final class EngineHotloader {
    private final IrisEngine engine;
    private final Object studioTransitionLock = new Object();

    EngineHotloader(IrisEngine engine) {
        this.engine = engine;
    }

    void hotload() {
        hotloadSilently();
        engine.getPlatformHooks().fireHotloadEvent(engine);
    }

    void hotloadComplex() {
        if (engine.isStudio() && engine.getGenerationHistoryRuntimeRouter().isPresent()) {
            hotloadSilently();
            return;
        }
        synchronized (engine.lifecycleLock) {
            engine.requireRunning("rebuild the biome complex");
            requireMutableGenerationRuntime("rebuild the biome complex");
            engine.awaitNativeStructureBootstrap("complex hotload");
            engine.lifecycleState = LifecycleState.HOTLOADING;
            EngineRuntime previous = engine.runtime;
            GenerationRuntime previousGeneration = previous.generation();
            IrisComplex nextComplex = null;
            try {
                engine.sealForTransition("complex hotload", false);
                engine.prepareRuntimeHotload();
                RuntimeAssembly assembly = new RuntimeAssembly(
                        RuntimeAssembly.nextRuntimeId(),
                        previousGeneration.target(),
                        previousGeneration.mantleStorageDirectory(),
                        previousGeneration.runtimeKernel(),
                        previousGeneration.transitionPlan());
                engine.runtimeAssembly.set(assembly);
                EngineRuntime next;
                try (IrisContext.Scope ignored = IrisContext.open(engine, engine.getGenerationSessions().currentSessionId(), null)) {
                    assembly.complex = assembly.runtimeKernel.createComplex(engine, assembly.transitionPlan);
                    nextComplex = assembly.complex;
                    assembly.dimensionStackContext = assembly.runtimeKernel.createDimensionStackContext(engine);
                    assembly.upperContext = assembly.runtimeKernel.createUpperContext(engine);
                    BiomeMaxes biomeMaxes = engine.runtimeBuilder.computeBiomeMaxes();
                    GenerationRuntime nextGeneration = previousGeneration.withComplex(
                            assembly.cacheId,
                            assembly.complex,
                            assembly.upperContext,
                            assembly.dimensionStackContext,
                            biomeMaxes);
                    next = previous.withGeneration(nextGeneration);
                } finally {
                    engine.runtimeAssembly.remove();
                }
                Throwable retirementFailure = runCleanup(null, previousGeneration.complex()::close);
                if (retirementFailure != null) {
                    retirementFailure = runCleanup(retirementFailure, nextComplex::close);
                    nextComplex = null;
                    engine.lifecycleState = LifecycleState.FAILED;
                    throw new IllegalStateException("Failed to retire the previous Iris biome complex.", retirementFailure);
                }
                engine.runtime = next;
                engine.getGenerationSessions().activateNextSession();
                engine.lifecycleState = LifecycleState.RUNNING;
                engine.getClosing().set(false);
                engine.backgroundTasks.openBackgroundTaskAdmission();
            } catch (Throwable e) {
                if (nextComplex != null && nextComplex != previousGeneration.complex()) {
                    Throwable cleanupFailure = runCleanup(null, nextComplex::close);
                    if (cleanupFailure != null) {
                        e.addSuppressed(cleanupFailure);
                    }
                }
                if (engine.lifecycleState != LifecycleState.FAILED) {
                    engine.runtimeBuilder.restoreRuntimeAfterFailedTransition(previous);
                }
                throw new IllegalStateException("Failed to rebuild the Iris biome complex.", e);
            }
        }
    }

    void hotloadSilently() {
        GenerationHistoryRuntimeRouter router = engine.getGenerationHistoryRuntimeRouter().orElse(null);
        if (engine.isStudio() && router != null) {
            hotloadStudio(router);
            return;
        }
        synchronized (engine.lifecycleLock) {
            engine.requireRunning("hotload");
            requireMutableGenerationRuntime("hotload");
            engine.awaitNativeStructureBootstrap("hotload");
            engine.lifecycleState = LifecycleState.HOTLOADING;
            EngineRuntime previousRuntime = engine.runtime;
            IrisDimension previousDimension = engine.getDimension();
            IrisData previousData = engine.getData();
            IrisData replacementData = null;
            boolean published = false;
            try {
                engine.sealForTransition("hotload", false);
                engine.prepareRuntimeHotload();
                replacementData = IrisData.openRuntime(previousData.getDataFolder());
                IrisDimension replacement = replacementData.getDimensionLoader().load(previousDimension.getLoadKey());
                if (replacement == null) {
                    throw new IllegalStateException("Studio hotload could not reload Iris dimension '" + previousDimension.getLoadKey() + "'");
                }
                engine.getPlatformHooks().validateDimensionHotload(engine, replacement);
                replacementData.registerEngine(engine);
                IrisStructureLocator.invalidate(engine);
                StructureReachability.invalidate(engine);
                EngineTarget replacementTarget = new EngineTarget(engine.getWorld(), replacement, replacementData);
                EngineRuntime nextRuntime = engine.runtimeBuilder.buildRuntime(replacementTarget);
                engine.runtimeBuilder.publishRuntime(nextRuntime, previousRuntime);
                published = true;
                previousData.unregisterEngine(engine);
                Throwable previousDataFailure = runCleanup(null, previousData::close);
                if (previousDataFailure != null) {
                    IrisLogging.error("Failed to completely release the previous Iris data runtime.");
                    IrisLogging.reportError(previousDataFailure);
                }
                engine.getPrefetchSaveStarted().set(false);
                engine.getEngineData().getStatistics().hotloaded();
                if (engine.getWorld().hasPlatformWorld()) {
                    if (!engine.backgroundTasks.scheduleTrackedTask(() -> {
                        engine.getPlatformHooks().refreshWorkspace(engine);
                        engine.getPlatformHooks().reloadDatapacks(engine);
                    })) {
                        throw new IllegalStateException("Iris background task admission closed before workspace refresh.");
                    }
                }
                engine.getPlatformHooks().applyWorldBoundary(engine);
                broadcastStudioHotload(false, "");
            } catch (Throwable e) {
                if (!published) {
                    if (replacementData != null && !engine.shutdownSequence.retainsData(replacementData)) {
                        replacementData.unregisterEngine(engine);
                        Throwable replacementDataFailure = runCleanup(null, replacementData::close);
                        if (replacementDataFailure != null) {
                            e.addSuppressed(replacementDataFailure);
                        }
                    }
                    IrisStructureLocator.invalidate(engine);
                    StructureReachability.invalidate(engine);
                    if (engine.lifecycleState != LifecycleState.FAILED) {
                        Throwable rollbackFailure = runCleanup(null, engine.getMantle()::hotload);
                        if (rollbackFailure == null) {
                            engine.runtimeBuilder.restoreRuntimeAfterFailedTransition(previousRuntime);
                        } else {
                            engine.runtime = previousRuntime;
                            engine.lifecycleState = LifecycleState.FAILED;
                            e.addSuppressed(rollbackFailure);
                        }
                    }
                }
                broadcastStudioHotload(true, e.getClass().getSimpleName() + ": " + e.getMessage());
                if (e instanceof Error error) {
                    throw error;
                }
                if (e instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IllegalStateException("Iris hotload failed.", e);
            }
        }
    }

    private void hotloadStudio(GenerationHistoryRuntimeRouter router) {
        synchronized (studioTransitionLock) {
            EngineRuntime previous = null;
            GenerationManifest previousManifest = router.history().manifest();
            GenerationHistoryRuntimeRouter.StudioCutover cutover = null;
            StudioGenerationUpdate update = null;
            boolean backgroundAdmissionClosed = false;
            try {
                engine.requireRunning("hotload the Studio pack");
                GenerationHistory history = router.history();
                update = StudioGenerationUpdate.prepare(engine, history);
                if (update == null) {
                    return;
                }
                engine.awaitNativeStructureBootstrap("Studio generation cutover");
                engine.backgroundTasks.closeBackgroundTaskAdmission();
                backgroundAdmissionClosed = true;
                engine.backgroundTasks.drainBackgroundTasks("Studio generation cutover")
                        .requireComplete("Studio generation cutover");
                cutover = router.beginStudioCutover(IrisEngine.SESSION_DRAIN_TIMEOUT_MILLIS);
                synchronized (engine.lifecycleLock) {
                    engine.requireRunning("prepare the Studio pack cutover");
                    previous = engine.runtime;
                    engine.studioGenerationTransition = true;
                    engine.lifecycleState = LifecycleState.HOTLOADING;
                }
                engine.sealForTransition("Studio generation cutover", false);
                IrisBoundarySignatureSampler.checkpoint(engine);
                synchronized (engine.lifecycleLock) {
                    if (engine.lifecycleState != LifecycleState.HOTLOADING || engine.runtime != previous) {
                        throw new IllegalStateException("Studio engine changed while its generation cutover was draining.");
                    }
                    engine.prepareRuntimeHotload();
                    history.stageUpdate(update.source(), update.fingerprint(), update.dimensionContract(),
                            update.registryContract(), IrisSettings.get().getGenerator().getGenerationTransitionWidthBlocks());
                    cutover.promotePending();
                    IrisStructureLocator.invalidate(engine);
                    StructureReachability.invalidate(engine);
                    engine.runtimeBuilder.refreshStudioRuntimeServices();
                    engine.getPrefetchSaveStarted().set(false);
                    engine.getEngineData().getStatistics().hotloaded();
                    engine.getPlatformHooks().applyWorldBoundary(engine);
                }
                broadcastStudioHotload(false, "");
            } catch (Throwable failure) {
                if (previous != null) {
                    synchronized (engine.lifecycleLock) {
                        if (engine.lifecycleState == LifecycleState.HOTLOADING
                                || engine.lifecycleState == LifecycleState.RUNNING) {
                            boolean unchanged = false;
                            try {
                                unchanged = router.history().manifest() == previousManifest;
                            } catch (Throwable historyFailure) {
                                failure.addSuppressed(historyFailure);
                            }
                            if (unchanged) {
                                engine.runtimeBuilder.restoreRuntimeAfterFailedTransition(previous);
                            } else {
                                engine.lifecycleState = LifecycleState.FAILED;
                                engine.getClosing().set(true);
                                engine.backgroundTasks.closeBackgroundTaskAdmission();
                            }
                        }
                    }
                } else if (backgroundAdmissionClosed && engine.lifecycleState == LifecycleState.RUNNING
                        && !engine.getClosing().get()) {
                    engine.backgroundTasks.openBackgroundTaskAdmission();
                }
                IrisLogging.reportError("Studio generation update failed for '" + engine.getWorld().name() + "'.", failure);
                broadcastStudioHotload(true, failure.getClass().getSimpleName() + ": " + failure.getMessage());
                throw EngineShutdownSequence.propagate(failure);
            } finally {
                synchronized (engine.lifecycleLock) {
                    engine.studioGenerationTransition = false;
                }
                if (cutover != null) {
                    cutover.close();
                }
                if (update != null) {
                    try {
                        update.close();
                    } catch (Throwable cleanupFailure) {
                        IrisLogging.reportError("Failed to remove the staged Studio pack after its update.", cleanupFailure);
                    }
                }
            }
        }
    }

    private void requireMutableGenerationRuntime(String operation) {
        if (engine.getGenerationHistoryRuntimeRouter().isPresent()) {
            throw new IllegalStateException("Cannot " + operation
                    + " while immutable Iris generation history is attached; stage a world update and restart instead.");
        }
    }

    /**
     * Never throws. It is called from the hotload failure handler, where a frame-cap
     * {@code IllegalStateException} out of the codec (a long pack key or exception message overruns
     * MAX_FRAME_BYTES) would otherwise propagate in place of the real hotload error and lose it.
     */
    private void broadcastStudioHotload(boolean failed, String message) {
        try {
            IrisProtocolServer protocolServer = IrisServices.getOrNull(IrisProtocolServer.class);
            if (protocolServer == null) {
                return;
            }
            String packKey = engine.getPackSource().getFileName().toString();
            protocolServer.broadcastStudioHotload(packKey, 0, failed, message);
        } catch (Throwable broadcastFailure) {
            IrisLogging.error("Iris studio hotload broadcast failed: " + broadcastFailure.getClass().getSimpleName()
                    + ": " + broadcastFailure.getMessage());
        }
    }
}
