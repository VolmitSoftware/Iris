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
import art.arcane.iris.core.protocol.IrisProtocolServer;
import art.arcane.iris.engine.EngineRuntime.BiomeMaxes;
import art.arcane.iris.engine.EngineRuntimeBuilder.RuntimeAssembly;
import art.arcane.iris.engine.IrisEngine.LifecycleState;
import art.arcane.iris.engine.framework.EngineTarget;
import art.arcane.iris.engine.framework.IrisStructureLocator;
import art.arcane.iris.engine.framework.StructureReachability;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.util.project.context.IrisContext;

import static art.arcane.iris.engine.EngineShutdownSequence.runCleanup;

/**
 * Performs the two supported live reload transitions for an {@link IrisEngine}: a full pack hotload
 * and a biome-complex-only rebuild. Both run entirely under the engine lifecycle lock, seal and
 * drain generation before swapping anything, and roll the previous runtime back on failure.
 */
final class EngineHotloader {
    private final IrisEngine engine;

    EngineHotloader(IrisEngine engine) {
        this.engine = engine;
    }

    void hotload() {
        hotloadSilently();
        engine.getPlatformHooks().fireHotloadEvent(engine);
    }

    void hotloadComplex() {
        synchronized (engine.lifecycleLock) {
            engine.requireRunning("rebuild the biome complex");
            engine.awaitNativeStructureBootstrap("complex hotload");
            engine.lifecycleState = LifecycleState.HOTLOADING;
            EngineRuntime previous = engine.runtime;
            IrisComplex nextComplex = null;
            try {
                engine.sealForTransition("complex hotload", false);
                RuntimeAssembly assembly = new RuntimeAssembly(RuntimeAssembly.nextRuntimeId(), previous.target());
                engine.runtimeAssembly.set(assembly);
                EngineRuntime next;
                try (IrisContext.Scope ignored = IrisContext.open(engine, engine.getGenerationSessions().currentSessionId(), null)) {
                    assembly.complex = new IrisComplex(engine);
                    nextComplex = assembly.complex;
                    assembly.upperContext = engine.runtimeBuilder.buildUpperContext();
                    BiomeMaxes biomeMaxes = engine.runtimeBuilder.computeBiomeMaxes();
                    next = previous.withComplex(assembly.cacheId, assembly.complex, assembly.upperContext, biomeMaxes,
                            assembly.placementLifecycles);
                } finally {
                    engine.runtimeAssembly.remove();
                }
                Throwable retirementFailure = runCleanup(null, previous.complex()::close);
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
                if (nextComplex != null && nextComplex != previous.complex()) {
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
        synchronized (engine.lifecycleLock) {
            engine.requireRunning("hotload");
            engine.awaitNativeStructureBootstrap("hotload");
            engine.lifecycleState = LifecycleState.HOTLOADING;
            EngineRuntime previousRuntime = engine.runtime;
            IrisDimension previousDimension = engine.getDimension();
            IrisData previousData = engine.getData();
            IrisData replacementData = null;
            boolean published = false;
            try {
                engine.sealForTransition("hotload", false);
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
                    if (replacementData != null) {
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
            String packKey = engine.getData().getDataFolder().getName();
            protocolServer.broadcastStudioHotload(packKey, 0, failed, message);
        } catch (Throwable broadcastFailure) {
            IrisLogging.error("Iris studio hotload broadcast failed: " + broadcastFailure.getClass().getSimpleName()
                    + ": " + broadcastFailure.getMessage());
        }
    }
}
