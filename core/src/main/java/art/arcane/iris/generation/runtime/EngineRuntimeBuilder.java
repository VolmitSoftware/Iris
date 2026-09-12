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

package art.arcane.iris.generation.runtime;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.generation.runtime.GenerationRuntime.BiomeMaxes;
import art.arcane.iris.generation.runtime.IrisEngine.LifecycleState;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.iris.world.history.TransitionGenerationPlan;
import art.arcane.iris.world.history.GenerationKernelRegistry;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomePaletteLayer;
import art.arcane.iris.generation.decoration.IrisDecorator;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.generation.context.IrisContext;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.math.M;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static art.arcane.iris.generation.runtime.EngineShutdownSequence.propagate;

/**
 * Assembles and publishes {@link EngineRuntime} snapshots for an {@link IrisEngine}.
 * A runtime is built into a thread-local {@link RuntimeAssembly} so partially constructed state is
 * visible only to the building thread, then frozen and published to the engine's volatile runtime
 * field as a single atomic swap.
 */
final class EngineRuntimeBuilder {
    private final IrisEngine engine;

    EngineRuntimeBuilder(IrisEngine engine) {
        this.engine = engine;
    }

    EngineRuntime buildRuntime() {
        return buildRuntime(
                engine.getTarget(),
                engine.getInitialMantleStorageDirectory(),
                engine.getInitialKernelVersion(),
                engine.getInitialTransitionPlan(),
                null);
    }

    EngineRuntime buildRuntime(EngineTarget runtimeTarget) {
        GenerationRuntime active = requireRuntime("hotload the engine runtime").generation();
        return buildRuntime(
                runtimeTarget,
                active.mantleStorageDirectory(),
                active.kernelVersion(),
                active.transitionPlan(),
                active.mantle());
    }

    private EngineRuntime buildRuntime(
            EngineTarget runtimeTarget,
            Path mantleStorageDirectory,
            GenerationKernelRegistry.Version kernelVersion,
            TransitionGenerationPlan transitionPlan,
            EngineMantle transferredMantle
    ) {
        RuntimeAssembly assembly = new RuntimeAssembly(
                RuntimeAssembly.nextRuntimeId(),
                runtimeTarget,
                mantleStorageDirectory,
                selectRuntimeKernel(kernelVersion),
                transitionPlan);
        engine.runtimeAssembly.set(assembly);
        try (IrisContext.Scope ignored = IrisContext.open(engine, engine.getGenerationSessions().currentSessionId(), null)) {
            IrisLogging.debug("Setup Engine " + assembly.cacheId);
            long started = M.ms();
            if (transferredMantle == null) {
                assembly.mantle = assembly.runtimeKernel.createMantle(engine, assembly.mantleStorageDirectory);
                assembly.ownsMantle = true;
            } else {
                assembly.mantle = transferredMantle;
            }
            IrisLogging.debug("[IrisEngine timing] new IrisEngineMantle=" + (M.ms() - started) + "ms");
            started = M.ms();
            assembly.complex = assembly.runtimeKernel.createComplex(engine, assembly.transitionPlan, false);
            IrisLogging.debug("[IrisEngine timing] complex=" + (M.ms() - started) + "ms");
            started = M.ms();
            assembly.dimensionStackContext = assembly.runtimeKernel.createDimensionStackContext(engine);
            IrisLogging.debug("[IrisEngine timing] buildDimensionStackContext=" + (M.ms() - started) + "ms");
            started = M.ms();
            assembly.upperContext = assembly.runtimeKernel.createUpperContext(engine);
            IrisLogging.debug("[IrisEngine timing] buildUpperContext=" + (M.ms() - started) + "ms");
            started = M.ms();
            assembly.effects = IrisServices.get(EngineEffectsProvider.class).create(engine);
            if (assembly.effects == null) {
                throw new IllegalStateException("Engine effects provider returned null");
            }
            IrisLogging.debug("[IrisEngine timing] EngineEffects=" + (M.ms() - started) + "ms");
            assembly.hash32 = new CompletableFuture<>();
            started = M.ms();
            assembly.mantle.hotload();
            IrisLogging.debug("[IrisEngine timing] mantle.hotload=" + (M.ms() - started) + "ms");
            started = M.ms();
            assembly.mode = assembly.runtimeKernel.createMode(engine);
            assembly.runtimeKernel.registerStaticObjects(engine, assembly.mode);
            assembly.runtimeKernel.registerDimensionStack(
                    engine,
                    assembly.mode,
                    assembly.dimensionStackContext
            );
            IrisLogging.debug("[IrisEngine timing] setupMode=" + (M.ms() - started) + "ms");
            started = M.ms();
            assembly.worldManager = IrisServices.get(EngineWorldManagerProvider.class).create(engine);
            if (assembly.worldManager == null) {
                throw new IllegalStateException("Engine world manager provider returned null");
            }
            IrisLogging.debug("[IrisEngine timing] IrisWorldManager=" + (M.ms() - started) + "ms");
            BiomeMaxes biomeMaxes = computeBiomeMaxes();
            return assembly.freezeRuntime(biomeMaxes);
        } catch (Throwable e) {
            Throwable cleanupFailure = engine.shutdownSequence.closeAssembly(assembly, e);
            if (cleanupFailure != e) {
                e.addSuppressed(cleanupFailure);
            }
            throw new IllegalStateException("Failed to build a complete Iris engine runtime.", e);
        } finally {
            engine.runtimeAssembly.remove();
        }
    }

    GenerationRuntime buildDetachedGenerationRuntime(EngineTarget runtimeTarget, Path mantleStorageDirectory) {
        return buildDetachedGenerationRuntime(
                runtimeTarget,
                mantleStorageDirectory,
                GenerationKernelRegistry.standard().current(),
                null);
    }

    GenerationRuntime buildDetachedGenerationRuntime(
            EngineTarget runtimeTarget,
            Path mantleStorageDirectory,
            GenerationKernelRegistry.Version kernelVersion,
            TransitionGenerationPlan transitionPlan
    ) {
        RuntimeAssembly assembly = new RuntimeAssembly(
                RuntimeAssembly.nextRuntimeId(),
                runtimeTarget,
                mantleStorageDirectory,
                selectRuntimeKernel(kernelVersion),
                transitionPlan);
        engine.runtimeAssembly.set(assembly);
        try (IrisContext.Scope ignored = IrisContext.open(engine, engine.getGenerationSessions().currentSessionId(), null)) {
            IrisLogging.debug("Setup Detached Generation Runtime " + assembly.cacheId);
            assembly.mantle = assembly.runtimeKernel.createMantle(engine, assembly.mantleStorageDirectory);
            assembly.ownsMantle = true;
            assembly.complex = assembly.runtimeKernel.createComplex(engine, assembly.transitionPlan, true);
            assembly.dimensionStackContext = assembly.runtimeKernel.createDimensionStackContext(engine);
            assembly.upperContext = assembly.runtimeKernel.createUpperContext(engine);
            assembly.hash32 = CompletableFuture.completedFuture(computePackHash(runtimeTarget.getData()));
            assembly.mantle.hotload();
            assembly.mode = assembly.runtimeKernel.createMode(engine);
            assembly.runtimeKernel.registerStaticObjects(engine, assembly.mode);
            assembly.runtimeKernel.registerDimensionStack(
                    engine,
                    assembly.mode,
                    assembly.dimensionStackContext
            );
            return assembly.freezeGeneration(computeBiomeMaxes());
        } catch (Throwable e) {
            Throwable cleanupFailure = engine.shutdownSequence.closeAssembly(assembly, e);
            if (cleanupFailure != e) {
                e.addSuppressed(cleanupFailure);
            }
            throw new IllegalStateException("Failed to build a detached Iris generation runtime.", e);
        } finally {
            engine.runtimeAssembly.remove();
        }
    }

    void publishRuntime(EngineRuntime next, EngineRuntime previous) {
        Throwable retirementFailure = engine.shutdownSequence.closeRuntime(
                previous,
                next.generation().mantle(),
                null);
        if (retirementFailure != null) {
            Throwable cleanupFailure = engine.shutdownSequence.closeRuntime(
                    next, previous == null ? null : previous.generation().mantle(), null);
            if (cleanupFailure != null) {
                engine.shutdownSequence.retainUnpublishedRuntime(next);
                retirementFailure = EngineShutdownSequence.appendFailure(retirementFailure, cleanupFailure);
            }
            engine.lifecycleState = LifecycleState.FAILED;
            throw new IllegalStateException("Failed to retire the previous Iris engine runtime.", retirementFailure);
        }
        if (engine.runtime == previous) {
            engine.runtime = null;
        }

        engine.runtime = next;
        engine.publishedTarget = next.generation().target();
        engine.getGenerationSessions().activateNextSession();
        engine.lifecycleState = LifecycleState.RUNNING;
        engine.getClosing().set(false);
        engine.backgroundTasks.openBackgroundTaskAdmission();
        try {
            next.worldManager().start();
        } catch (Throwable e) {
            engine.getClosing().set(true);
            engine.backgroundTasks.closeBackgroundTaskAdmission();
            engine.lifecycleState = LifecycleState.FAILED;
            Throwable cleanupFailure = null;
            try {
                engine.getGenerationSessions().sealAndAwait(
                        "failed world manager start",
                        IrisEngine.SESSION_DRAIN_TIMEOUT_MILLIS,
                        true
                );
            } catch (Throwable drainFailure) {
                cleanupFailure = drainFailure;
            }
            if (cleanupFailure == null) {
                cleanupFailure = engine.shutdownSequence.closeRuntime(next, null);
            }
            if (cleanupFailure == null) {
                if (engine.runtime == next) {
                    engine.runtime = null;
                }
            } else {
                e.addSuppressed(cleanupFailure);
            }
            throw new IllegalStateException("Failed to start the Iris world manager.", e);
        }
        scheduleRuntimeTasks(next);
        IrisLogging.debug("Engine Setup Complete " + next.generation().cacheId());
    }

    void refreshStudioRuntimeServices() {
        EngineRuntime previous = requireRuntime("refresh Studio runtime services");
        EngineEffects effects = null;
        EngineWorldManager worldManager = null;
        try {
            effects = Objects.requireNonNull(IrisServices.get(EngineEffectsProvider.class).create(engine),
                    "Studio engine effects");
            worldManager = Objects.requireNonNull(IrisServices.get(EngineWorldManagerProvider.class).create(engine),
                    "Studio world manager");
        } catch (Throwable failure) {
            if (effects != null) {
                EngineEffects createdEffects = effects;
                EngineShutdownSequence.runCleanup(failure, createdEffects::close);
            }
            throw propagate(failure);
        }
        EngineRuntime next = new EngineRuntime(previous.generation(), effects, worldManager);
        try {
            worldManager.start();
        } catch (Throwable failure) {
            failure = EngineShutdownSequence.runCleanup(failure, worldManager::close);
            failure = EngineShutdownSequence.runCleanup(failure, effects::close);
            throw new IllegalStateException("Failed to start the replacement Studio world manager.", failure);
        }
        Throwable retirementFailure = EngineShutdownSequence.runCleanup(null, previous.worldManager()::close);
        retirementFailure = EngineShutdownSequence.runCleanup(retirementFailure, previous.effects()::close);
        if (retirementFailure != null) {
            retirementFailure = EngineShutdownSequence.runCleanup(retirementFailure, worldManager::close);
            retirementFailure = EngineShutdownSequence.runCleanup(retirementFailure, effects::close);
            throw new IllegalStateException("Failed to retire Studio runtime services.", retirementFailure);
        }
        engine.runtime = next;
        engine.getGenerationSessions().activateNextSession();
        engine.lifecycleState = LifecycleState.RUNNING;
        engine.getClosing().set(false);
        engine.backgroundTasks.openBackgroundTaskAdmission();
        scheduleRuntimeTasks(next);
    }

    private void scheduleRuntimeTasks(EngineRuntime engineRuntime) {
        schedulePackHash(engineRuntime.generation());
        try {
            if (!engine.backgroundTasks.scheduleTrackedTask(() -> engine.getPlatformHooks().refreshDatapackWorkspace(engine))) {
                throw new IllegalStateException("Iris background task admission closed before datapack workspace refresh.");
            }
        } catch (Throwable e) {
            IrisLogging.reportError(e);
        }
    }

    private void schedulePackHash(GenerationRuntime generationRuntime) {
        try {
            if (!engine.backgroundTasks.scheduleTrackedTask(() -> {
                try {
                    generationRuntime.hash32().complete(computePackHash(generationRuntime.data()));
                } catch (Throwable e) {
                    generationRuntime.hash32().completeExceptionally(e);
                    throw propagate(e);
                }
            })) {
                throw new IllegalStateException("Iris background task admission closed before pack hashing.");
            }
        } catch (Throwable e) {
            generationRuntime.hash32().completeExceptionally(e);
            IrisLogging.reportError(e);
        }
    }

    private long computePackHash(IrisData data) {
        File[] roots = data.getLoaders()
                .values()
                .stream()
                .map(ResourceLoader::getFolderName)
                .map(name -> new File(data.getDataFolder(), name))
                .filter(File::exists)
                .filter(File::isDirectory)
                .toArray(File[]::new);
        return IO.hashRecursiveMeta(roots);
    }

    BiomeMaxes computeBiomeMaxes() {
        double objectDensity = 0D;
        double layerDensity = 0D;
        double decoratorDensity = 0D;
        for (IrisBiome i : engine.getDimension().getReachableBiomes(engine)) {
            double density = 0;

            for (IrisObjectPlacement j : i.getObjects()) {
                density += j.getDensity() * j.getChance();
            }

            objectDensity = Math.max(objectDensity, density);
            density = 0;

            for (IrisDecorator j : i.getDecorators()) {
                density += Math.max(j.getStackMax(), 1) * j.getChance();
            }

            decoratorDensity = Math.max(decoratorDensity, density);
            density = 0;

            for (IrisBiomePaletteLayer j : i.getLayers()) {
                density++;
            }

            layerDensity = Math.max(layerDensity, density);
        }
        return new BiomeMaxes(objectDensity, layerDensity, decoratorDensity);
    }

    void restoreRuntimeAfterFailedTransition(EngineRuntime previous) {
        engine.runtime = previous;
        if (engine.getGenerationSessions().activeLeases() == 0) {
            engine.getGenerationSessions().activateNextSession();
            engine.lifecycleState = LifecycleState.RUNNING;
            engine.getClosing().set(false);
            engine.backgroundTasks.openBackgroundTaskAdmission();
            return;
        }
        engine.lifecycleState = LifecycleState.FAILED;
    }

    EngineRuntime requireRuntime(String operation) {
        EngineRuntime current = engine.runtime;
        if (current == null) {
            throw new IllegalStateException("Cannot " + operation + " without an active Iris runtime for "
                    + engine.getWorld().name() + ".");
        }
        return current;
    }

    static GenerationKernelRegistry.RuntimeKernel selectRuntimeKernel(
            GenerationKernelRegistry.Version version
    ) {
        try {
            return GenerationKernelRegistry.standard().select(version);
        } catch (IOException failure) {
            throw new IllegalArgumentException("Unsupported executable Iris generation kernel "
                    + version + ".", failure);
        }
    }

    static final class RuntimeAssembly {
        private static final AtomicInteger RUNTIME_IDS = new AtomicInteger();

        final int cacheId;
        final EngineTarget target;
        final Path mantleStorageDirectory;
        final GenerationKernelRegistry.Version kernelVersion;
        final GenerationKernelRegistry.RuntimeKernel runtimeKernel;
        final SeedManager seedManager;
        final TransitionGenerationPlan transitionPlan;

        static int nextRuntimeId() {
            return RUNTIME_IDS.incrementAndGet();
        }
        IrisComplex complex;
        UpperDimensionContext upperContext;
        DimensionStackContext dimensionStackContext;
        EngineEffects effects;
        EngineMode mode;
        EngineMantle mantle;
        EngineWorldManager worldManager;
        CompletableFuture<Long> hash32;
        boolean ownsMantle;

        RuntimeAssembly(int cacheId, EngineTarget target) {
            this(
                    cacheId,
                    target,
                    null,
                    selectRuntimeKernel(GenerationKernelRegistry.standard().current()),
                    null);
        }

        RuntimeAssembly(int cacheId, EngineTarget target, Path mantleStorageDirectory) {
            this(
                    cacheId,
                    target,
                    mantleStorageDirectory,
                    selectRuntimeKernel(GenerationKernelRegistry.standard().current()),
                    null);
        }

        RuntimeAssembly(
                int cacheId,
                EngineTarget target,
                Path mantleStorageDirectory,
                GenerationKernelRegistry.RuntimeKernel runtimeKernel,
                TransitionGenerationPlan transitionPlan
        ) {
            this.cacheId = cacheId;
            this.target = target;
            this.mantleStorageDirectory = mantleStorageDirectory == null
                    ? null
                    : IrisEngineMantle.normalizeStorageDirectory(mantleStorageDirectory);
            this.runtimeKernel = Objects.requireNonNull(runtimeKernel, "executable generation kernel");
            this.seedManager = this.runtimeKernel.createSeedManager(target.getWorld().getRawWorldSeed());
            this.kernelVersion = this.runtimeKernel.version();
            this.transitionPlan = transitionPlan;
        }

        GenerationRuntime freezeGeneration(BiomeMaxes biomeMaxes) {
            if (complex == null || mode == null || mantle == null || mantleStorageDirectory == null || hash32 == null) {
                throw new IllegalStateException("Cannot publish an incomplete Iris generation runtime.");
            }
            return new GenerationRuntime(
                    cacheId,
                    target,
                    target.getData(),
                    target.getDimension(),
                    kernelVersion,
                    runtimeKernel,
                    seedManager,
                    transitionPlan,
                    complex,
                    upperContext,
                    dimensionStackContext,
                    mode,
                    mantle,
                    mantleStorageDirectory,
                    hash32,
                    biomeMaxes);
        }

        EngineRuntime freezeRuntime(BiomeMaxes biomeMaxes) {
            if (effects == null || worldManager == null) {
                throw new IllegalStateException("Cannot publish an incomplete Iris engine runtime.");
            }
            return new EngineRuntime(freezeGeneration(biomeMaxes), effects, worldManager);
        }

    }
}
