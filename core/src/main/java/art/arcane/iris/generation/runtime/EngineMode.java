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

import art.arcane.iris.generation.stage.IrisBiomeActuator;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.context.IrisContext;
import art.arcane.volmlib.util.documentation.BlockCoordinates;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.iris.generation.concurrent.BurstExecutor;
import art.arcane.iris.generation.concurrent.MultiBurst;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBlockState;

public interface EngineMode extends Staged {
    KList<EngineStage> getTerrainStages();

    void registerTerrainStage(EngineStage stage);

    EngineStage getTransitionStage();

    default void generateTerrain(int x, int z, Hunk<PlatformBlockState> blocks, Hunk<PlatformBiome> biomes,
                                 boolean multicore, ChunkContext context) {
        context.setTerrainBiomeOutput(biomes);
        for (EngineStage stage : getTerrainStages()) {
            stage.generate(x, z, blocks, biomes, multicore, context);
        }
        getTransitionStage().generate(x, z, blocks, biomes, multicore, context);
    }

    void close();

    Engine getEngine();

    default MultiBurst burst() {
        return getEngine().burst();
    }

    default EngineStage burst(EngineStage... stages) {
        return (x, z, blocks, biomes, multicore, ctx) -> {
            IrisEngine generationEngine = getEngine() instanceof IrisEngine irisEngine ? irisEngine : null;
            IrisEngine.GenerationRuntimeBinding generationRuntime = generationEngine == null
                    ? null
                    : generationEngine.captureGenerationRuntimeBinding();
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
                    try (IrisEngine.GenerationRuntimeScope runtimeScope = generationEngine == null
                            ? null
                            : generationEngine.openGenerationRuntimeScope(generationRuntime);
                         IrisContext.Scope stageScope = IrisContext.open(getEngine(), ctx.getGenerationSessionId(), ctx)) {
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

    /**
     * Runs {@code parallel} on the burst pool while {@code inline} runs on the calling thread, then
     * waits for all of them. Matter generation is the inline stage: on the calling thread its
     * mantle window fans out across the pool, and biome and terrain overlap with it instead of
     * following it. Without multicore every stage simply runs here, parallel stages first.
     */
    default EngineStage burstAround(EngineStage inline, EngineStage... parallel) {
        EngineStage fanOut = burst(parallel);
        return (x, z, blocks, biomes, multicore, ctx) -> {
            if (!multicore) {
                fanOut.generate(x, z, blocks, biomes, false, ctx);
                inline.generate(x, z, blocks, biomes, false, ctx);
                return;
            }
            IrisEngine generationEngine = getEngine() instanceof IrisEngine irisEngine ? irisEngine : null;
            IrisEngine.GenerationRuntimeBinding generationRuntime = generationEngine == null
                    ? null
                    : generationEngine.captureGenerationRuntimeBinding();
            java.util.concurrent.CompletableFuture<Void> background = burst().completeValueAsync(() -> {
                try (IrisEngine.GenerationRuntimeScope runtimeScope = generationEngine == null
                        ? null
                        : generationEngine.openGenerationRuntimeScope(generationRuntime)) {
                    fanOut.generate(x, z, blocks, biomes, true, ctx);
                }
                return null;
            });
            Throwable inlineFailure = null;
            try {
                inline.generate(x, z, blocks, biomes, true, ctx);
            } catch (Throwable t) {
                inlineFailure = t;
            }
            try {
                background.join();
            } catch (java.util.concurrent.CompletionException e) {
                if (inlineFailure == null) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    if (cause instanceof Error error) {
                        throw error;
                    }
                    if (cause instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    }
                    throw new IllegalStateException("Burst stage failure during chunk generation", cause);
                }
                inlineFailure.addSuppressed(e);
            }
            if (inlineFailure != null) {
                if (inlineFailure instanceof Error error) {
                    throw error;
                }
                if (inlineFailure instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IllegalStateException("Inline stage failure during chunk generation", inlineFailure);
            }
        };
    }

    default IrisComplex getComplex() {
        return getEngine().getComplex();
    }

    default EngineMantle getMantle() {
        return getEngine().getMantle();
    }

    default void generateTerrainMatter(int x, int z, boolean multicore, ChunkContext context) {
        getMantle().generateTerrainMatter(x, z, multicore, context);
    }

    default void generateContentMatter(int x, int z, boolean multicore, ChunkContext context) {
        getMantle().generateContentMatter(x, z, multicore, context);
    }

    @BlockCoordinates
    default void generate(int x, int z, Hunk<PlatformBlockState> blocks, Hunk<PlatformBiome> biomes, boolean multicore, long generationSessionId) {
        boolean cacheContext = !getEngine().getPlatformHooks().shouldDisableChunkContextCache(getEngine());
        ChunkContext.PrefillPlan prefillPlan = cacheContext ? ChunkContext.PrefillPlan.NO_CAVE : ChunkContext.PrefillPlan.NONE;
        ChunkContext ctx = new ChunkContext(
                x,
                z,
                getComplex(),
                generationSessionId,
                cacheContext,
                prefillPlan,
                getEngine().getMetrics(),
                getEngine().getDimensionStackContext()
        );

        EngineStage[] stages = getStages().toArray(new EngineStage[0]);
        try (IrisContext.Scope chunkScope = IrisContext.open(getEngine(), generationSessionId, ctx)) {
            getComplex().getResolvedTerrain().generate(this, x, z, blocks, biomes, multicore, ctx);
            IrisBiomeActuator.publishNaturalMetadata(getEngine(), x, z, biomes, ctx);
            ctx.beginContent();
            for (EngineStage i : stages) {
                i.generate(x, z, blocks, biomes, multicore, ctx);
            }
        }
    }

    static boolean shouldDisableContextCacheForMaintenance(boolean maintenanceActive, boolean pregeneratorTargetsWorld) {
        return maintenanceActive && !pregeneratorTargetsWorld;
    }
}
