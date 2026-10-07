/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.decoration.IrisDecorator;
import art.arcane.iris.generation.decoration.IrisEngineDecorator;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.generation.decoration.IrisOreGenerator;
import art.arcane.iris.generation.decoration.IrisProceduralObjects;
import art.arcane.iris.generation.decoration.IrisProceduralPlacement;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.math.RNG;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class GenerationCacheWarmer {
    private GenerationCacheWarmer() {
    }

    public static void warm(Engine engine) {
        long start = M.ms();
        IrisData data = engine.getData();
        RNG root = new RNG(engine.getSeedManager().getComponent() + 7777L);
        int[] counter = {0};
        List<ProceduralWarmTask> proceduralTasks = new ArrayList<>();

        KList<IrisBiome> biomes = engine.getAllBiomes();
        biomes.sort(Comparator.comparing(IrisBiome::getLoadKey));
        for (IrisBiome biome : biomes) {
            warmPlacements(biome.getObjects(), root, counter, data, engine);
            warmDecorators(biome.getDecorators(), engine.getSeedManager().getComponent(), counter, data);
            warmOres(biome.getOres(), engine.getSeedManager().getTerrain(), counter, data);
            collectProcedural(biome.getProceduralObjects(), root, counter, proceduralTasks);
        }

        KList<IrisRegion> regions = engine.getDimension().getAllRegions(engine);
        regions.sort(Comparator.comparing(IrisRegion::getLoadKey));
        for (IrisRegion region : regions) {
            warmPlacements(region.getObjects(), root, counter, data, engine);
            warmOres(region.getOres(), engine.getSeedManager().getTerrain(), counter, data);
            collectProcedural(region.getProceduralObjects(), root, counter, proceduralTasks);
        }

        warmOres(engine.getDimension().getOres(), engine.getSeedManager().getTerrain(), counter, data);
        warmProcedural(proceduralTasks, data);

        IrisLogging.debug("[IrisEngine timing] cache warm " + counter[0] + " configs=" + (M.ms() - start) + "ms");
    }

    private static void warmPlacements(KList<IrisObjectPlacement> placements, RNG root, int[] counter,
                                       IrisData data, Engine engine) {
        if (placements == null) {
            return;
        }
        for (IrisObjectPlacement placement : placements) {
            if (placement == null) {
                continue;
            }
            RNG rng = root.nextParallelRNG(counter[0]++);
            placement.getSurfaceWarp(rng, data, engine);
            placement.getDensity(rng, 0, 0, data);
        }
    }

    private static void warmDecorators(KList<IrisDecorator> decorators, long componentSeed, int[] counter, IrisData data) {
        if (decorators == null) {
            return;
        }
        for (IrisDecorator decorator : decorators) {
            if (decorator == null) {
                continue;
            }
            counter[0]++;
            RNG rng = new RNG(IrisEngineDecorator.seedForPart(componentSeed, decorator.getPartOf()));
            decorator.getHeightGenerator(rng, data);
            decorator.getGenerator(rng, data);
            decorator.getVarianceGenerator(rng, data);
        }
    }

    private static void warmOres(KList<IrisOreGenerator> ores, long terrainSeed, int[] counter, IrisData data) {
        if (ores == null) {
            return;
        }
        for (IrisOreGenerator ore : ores) {
            if (ore == null) {
                continue;
            }
            counter[0]++;
            ore.warm(new RNG(terrainSeed), data);
        }
    }

    private static void collectProcedural(IrisProceduralObjects procedural, RNG root, int[] counter,
                                          List<ProceduralWarmTask> tasks) {
        if (procedural == null) {
            return;
        }
        for (IrisProceduralPlacement placement : procedural.getAllPlacements()) {
            if (placement != null) {
                tasks.add(new ProceduralWarmTask(placement, root.nextParallelRNG(counter[0]++)));
            }
        }
    }

    static void warmProcedural(List<ProceduralWarmTask> tasks, IrisData data) {
        if (tasks.isEmpty()) {
            return;
        }
        IdentityHashMap<IrisProceduralPlacement, List<ProceduralWarmTask>> identities = new IdentityHashMap<>();
        List<List<ProceduralWarmTask>> groups = new ArrayList<>();
        for (ProceduralWarmTask task : tasks) {
            List<ProceduralWarmTask> group = identities.get(task.placement());
            if (group == null) {
                group = new ArrayList<>();
                identities.put(task.placement(), group);
                groups.add(group);
            }
            group.add(task);
        }
        int workers = Math.min(groups.size(), Math.min(4, Runtime.getRuntime().availableProcessors()));
        if (workers == 1) {
            for (ProceduralWarmTask task : tasks) {
                task.placement().getVariantObject(data, task.rng(), null);
            }
            return;
        }
        Throwable failure = null;
        boolean interrupted = false;
        try (ExecutorService executor = Executors.newFixedThreadPool(workers, runnable -> {
            Thread thread = new Thread(runnable, "Iris-Procedural-Warm");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        })) {
            List<Future<?>> pending = new ArrayList<>(groups.size());
            for (List<ProceduralWarmTask> group : groups) {
                pending.add(executor.submit(() -> warmGroup(group, data)));
            }
            for (Future<?> future : pending) {
                boolean complete = false;
                while (!complete) {
                    try {
                        future.get();
                        complete = true;
                    } catch (InterruptedException exception) {
                        interrupted = true;
                    } catch (ExecutionException exception) {
                        Throwable cause = exception.getCause();
                        if (failure == null) {
                            failure = cause;
                        } else if (failure != cause) {
                            failure.addSuppressed(cause);
                        }
                        complete = true;
                    }
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure instanceof RuntimeException exception) {
            throw exception;
        }
        if (failure != null) {
            throw new IllegalStateException("Procedural generation cache warming failed.", failure);
        }
    }

    private static void warmGroup(List<ProceduralWarmTask> group, IrisData data) {
        for (ProceduralWarmTask task : group) {
            task.placement().getVariantObject(data, task.rng(), null);
        }
    }

    record ProceduralWarmTask(IrisProceduralPlacement placement, RNG rng) {
    }
}
