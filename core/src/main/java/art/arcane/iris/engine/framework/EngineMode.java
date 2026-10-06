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

package art.arcane.iris.engine.framework;

import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.core.modules.ModuleChunkGeneration;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.project.context.IrisContext;
import art.arcane.volmlib.util.documentation.BlockCoordinates;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.iris.util.common.parallel.BurstExecutor;
import art.arcane.iris.util.common.parallel.MultiBurst;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBlockState;

public interface EngineMode extends Staged {
    void close();

    Engine getEngine();

    default MultiBurst burst() {
        return getEngine().burst();
    }

    default EngineStage burst(EngineStage... stages) {
        return (x, z, blocks, biomes, multicore, ctx) -> {
            BurstExecutor e = burst().burst(stages.length);
            e.setMulticore(multicore);
            // BurstExecutor.complete() logs-and-swallows stage failures; without re-propagation
            // a multicore run would commit a half-written chunk that the inline (production)
            // path correctly aborts.
            java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();

            for (EngineStage i : stages) {
                e.queue(() -> {
                    if (failure.get() != null) {
                        return;
                    }
                    try (IrisContext.Scope stageScope = IrisContext.open(getEngine(), ctx.getGenerationSessionId(), ctx)) {
                        i.generate(x, z, blocks, biomes, multicore, ctx);
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                        // Rethrow so the inline (multicore=false) path still aborts out of
                        // queue() on the first failure, exactly as before.
                        if (t instanceof Error error) {
                            throw error;
                        }
                        if (t instanceof RuntimeException runtimeException) {
                            throw runtimeException;
                        }
                        throw new IllegalStateException(t);
                    }
                });
            }

            e.complete();

            Throwable t = failure.get();
            if (t != null) {
                if (t instanceof Error error) {
                    throw error;
                }
                if (t instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IllegalStateException("Burst stage failure during chunk generation", t);
            }
        };
    }

    default IrisComplex getComplex() {
        return getEngine().getComplex();
    }

    default EngineMantle getMantle() {
        return getEngine().getMantle();
    }

    default void generateMatter(int x, int z, boolean multicore, ChunkContext context) {
        getMantle().generateMatter(x, z, multicore, context);
    }

    @BlockCoordinates
    default void generate(int x, int z, Hunk<PlatformBlockState> blocks, Hunk<PlatformBiome> biomes, boolean multicore, long generationSessionId) {
        boolean cacheContext = !getEngine().getPlatformHooks().shouldDisableChunkContextCache(getEngine());
        ChunkContext.PrefillPlan prefillPlan = cacheContext ? ChunkContext.PrefillPlan.NO_CAVE : ChunkContext.PrefillPlan.NONE;
        ChunkContext ctx = new ChunkContext(x, z, getComplex(), generationSessionId, cacheContext, prefillPlan, getEngine().getMetrics());

        EngineStage[] stages = getStages().toArray(new EngineStage[0]);
        try (IrisContext.Scope chunkScope = IrisContext.open(getEngine(), generationSessionId, ctx)) {
            for (EngineStage i : stages) {
                i.generate(x, z, blocks, biomes, multicore, ctx);
            }
            ModuleChunkGeneration.generate(getEngine(), x, z, blocks, ctx);
        }
    }

    static boolean shouldDisableContextCacheForMaintenance(boolean maintenanceActive, boolean pregeneratorTargetsWorld) {
        return maintenanceActive && !pregeneratorTargetsWorld;
    }
}
