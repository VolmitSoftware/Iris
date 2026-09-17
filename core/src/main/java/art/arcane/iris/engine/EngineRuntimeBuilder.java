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

import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.engine.EngineRuntime.BiomeMaxes;
import art.arcane.iris.engine.IrisEngine.LifecycleState;
import art.arcane.iris.engine.framework.EngineEffects;
import art.arcane.iris.engine.framework.EngineEffectsProvider;
import art.arcane.iris.engine.framework.EngineMode;
import art.arcane.iris.engine.framework.EngineTarget;
import art.arcane.iris.engine.framework.EngineWorldManager;
import art.arcane.iris.engine.framework.EngineWorldManagerProvider;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.terrain.IrisBiomePaletteLayer;
import art.arcane.iris.engine.object.decoration.IrisDecorator;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.dimension.IrisDimensionMode;
import art.arcane.iris.engine.object.world.dimension.IrisDimensionModeType;
import art.arcane.iris.engine.object.placement.PlacementLifecycles;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.util.project.context.IrisContext;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.math.M;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static art.arcane.iris.engine.EngineShutdownSequence.propagate;

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
        return buildRuntime(engine.getTarget());
    }

    EngineRuntime buildRuntime(EngineTarget runtimeTarget) {
        RuntimeAssembly assembly = new RuntimeAssembly(RuntimeAssembly.nextRuntimeId(), runtimeTarget);
        engine.runtimeAssembly.set(assembly);
        try (IrisContext.Scope ignored = IrisContext.open(engine, engine.getGenerationSessions().currentSessionId(), null)) {
            IrisLogging.debug("Setup Engine " + assembly.cacheId);
            long started = M.ms();
            assembly.complex = new IrisComplex(engine);
            IrisLogging.debug("[IrisEngine timing] complex=" + (M.ms() - started) + "ms");
            started = M.ms();
            assembly.upperContext = buildUpperContext();
            IrisLogging.debug("[IrisEngine timing] buildUpperContext=" + (M.ms() - started) + "ms");
            started = M.ms();
            assembly.effects = IrisServices.get(EngineEffectsProvider.class).create(engine);
            if (assembly.effects == null) {
                throw new IllegalStateException("Engine effects provider returned null");
            }
            IrisLogging.debug("[IrisEngine timing] EngineEffects=" + (M.ms() - started) + "ms");
            assembly.hash32 = new CompletableFuture<>();
            started = M.ms();
            engine.getMantle().hotload();
            IrisLogging.debug("[IrisEngine timing] mantle.hotload=" + (M.ms() - started) + "ms");
            started = M.ms();
            assembly.mode = createMode();
            IrisLogging.debug("[IrisEngine timing] setupMode=" + (M.ms() - started) + "ms");
            started = M.ms();
            assembly.worldManager = IrisServices.get(EngineWorldManagerProvider.class).create(engine);
            if (assembly.worldManager == null) {
                throw new IllegalStateException("Engine world manager provider returned null");
            }
            IrisLogging.debug("[IrisEngine timing] IrisWorldManager=" + (M.ms() - started) + "ms");
            BiomeMaxes biomeMaxes = computeBiomeMaxes();
            return assembly.freeze(biomeMaxes);
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

    void publishRuntime(EngineRuntime next, EngineRuntime previous) {
        Throwable retirementFailure = engine.shutdownSequence.closeRuntime(previous, null);
        if (retirementFailure != null) {
            retirementFailure = engine.shutdownSequence.closeRuntime(next, retirementFailure);
            engine.lifecycleState = LifecycleState.FAILED;
            throw new IllegalStateException("Failed to retire the previous Iris engine runtime.", retirementFailure);
        }
        if (engine.runtime == previous) {
            engine.runtime = null;
        }

        engine.runtime = next;
        engine.publishedTarget = next.target();
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
            try {
                engine.getGenerationSessions().sealAndAwait(
                        "failed world manager start",
                        IrisEngine.SESSION_DRAIN_TIMEOUT_MILLIS,
                        true
                );
            } catch (Throwable drainFailure) {
                e.addSuppressed(drainFailure);
            }
            engine.shutdownSequence.closeRuntime(next, e);
            if (engine.runtime == next) {
                engine.runtime = null;
            }
            throw new IllegalStateException("Failed to start the Iris world manager.", e);
        }
        scheduleRuntimeTasks(next);
        IrisLogging.debug("Engine Setup Complete " + next.cacheId());
    }

    private void scheduleRuntimeTasks(EngineRuntime engineRuntime) {
        try {
            if (!engine.backgroundTasks.scheduleTrackedTask(() -> {
                try {
                    File[] roots = engine.getData().getLoaders()
                            .values()
                            .stream()
                            .map(ResourceLoader::getFolderName)
                            .map(name -> new File(engine.getData().getDataFolder(), name))
                            .filter(File::exists)
                            .filter(File::isDirectory)
                            .toArray(File[]::new);
                    engineRuntime.hash32().complete(IO.hashRecursiveMeta(roots));
                } catch (Throwable e) {
                    engineRuntime.hash32().completeExceptionally(e);
                    throw propagate(e);
                }
            })) {
                throw new IllegalStateException("Iris background task admission closed before pack hashing.");
            }
        } catch (Throwable e) {
            engineRuntime.hash32().completeExceptionally(e);
            IrisLogging.reportError(e);
        }
        try {
            if (!engine.backgroundTasks.scheduleTrackedTask(() -> engine.getPlatformHooks().refreshDatapackWorkspace(engine))) {
                throw new IllegalStateException("Iris background task admission closed before datapack workspace refresh.");
            }
        } catch (Throwable e) {
            IrisLogging.reportError(e);
        }
    }

    UpperDimensionContext buildUpperContext() {
        IrisDimension dim = engine.getDimension();
        if (!dim.hasUpperDimension()) {
            return null;
        }
        String upperKey = dim.getUpperDimension();
        IrisDimension upperDim = upperKey.equals(dim.getLoadKey())
                ? dim
                : IrisData.loadAnyDimension(upperKey, engine.getData());
        if (upperDim != null) {
            UpperDimensionContext ctx = UpperDimensionContext.create(engine, upperDim);
            IrisLogging.info("Upper dimension enabled: " + upperKey
                    + (ctx.isSelfReferencing() ? " (self-referencing)" : " (cross-referencing)"));
            return ctx;
        }
        IrisLogging.warn("Upper dimension '" + upperKey + "' could not be resolved, skipping upper terrain.");
        return null;
    }

    private EngineMode createMode() {
        Throwable configuredFailure = null;
        try {
            IrisDimensionMode configuredMode = engine.getDimension().getMode();
            if (configuredMode == null) {
                configuredMode = new IrisDimensionMode();
                engine.getDimension().setMode(configuredMode);
            }
            EngineMode configured = configuredMode.create(engine);
            if (configured == null) {
                throw new IllegalStateException("Dimension mode factory returned null");
            }
            return configured;
        } catch (Throwable e) {
            configuredFailure = e;
            IrisLogging.reportError(e);
            if (engine.getModeFallbackLogged().compareAndSet(false, true)) {
                IrisLogging.warn("Failed to initialize configured dimension mode for " + engine.getDimension().getLoadKey() + ", falling back to OVERWORLD mode.");
            }
        }

        try {
            EngineMode fallback = IrisDimensionModeType.OVERWORLD.create(engine);
            if (fallback == null) {
                throw new IllegalStateException("OVERWORLD mode factory returned null");
            }
            return fallback;
        } catch (Throwable fallbackFailure) {
            fallbackFailure.addSuppressed(configuredFailure);
            throw new IllegalStateException("Both configured and fallback Iris engine modes failed.", fallbackFailure);
        }
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

    static final class RuntimeAssembly {
        private static final AtomicInteger RUNTIME_IDS = new AtomicInteger();

        final int cacheId;
        final EngineTarget target;

        static int nextRuntimeId() {
            return RUNTIME_IDS.incrementAndGet();
        }
        IrisComplex complex;
        UpperDimensionContext upperContext;
        EngineEffects effects;
        EngineMode mode;
        EngineWorldManager worldManager;
        CompletableFuture<Long> hash32;
        final PlacementLifecycles placementLifecycles;

        RuntimeAssembly(int cacheId, EngineTarget target) {
            this.cacheId = cacheId;
            this.target = target;
            this.placementLifecycles = PlacementLifecycles.resolve();
        }

        EngineRuntime freeze(BiomeMaxes biomeMaxes) {
            if (complex == null || effects == null || mode == null || worldManager == null || hash32 == null) {
                throw new IllegalStateException("Cannot publish an incomplete Iris engine runtime.");
            }
            return new EngineRuntime(
                    cacheId,
                    target,
                    complex,
                    upperContext,
                    effects,
                    mode,
                    worldManager,
                    hash32,
                    biomeMaxes,
                    placementLifecycles);
        }
    }
}
