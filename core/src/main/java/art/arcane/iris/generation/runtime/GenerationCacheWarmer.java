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
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.math.RNG;

import java.util.Comparator;

public final class GenerationCacheWarmer {
    private GenerationCacheWarmer() {
    }

    public static void warm(Engine engine) {
        long start = M.ms();
        IrisData data = engine.getData();
        PackRuntime runtime = PackRuntime.of(engine, data);
        RNG root = new RNG(engine.getSeedManager().getComponent() + 7777L);
        int[] counter = {0};

        KList<IrisBiome> biomes = engine.getAllBiomes();
        biomes.sort(Comparator.comparing(IrisBiome::getLoadKey));
        for (IrisBiome biome : biomes) {
            warmPlacements(biome.getObjects(), root, counter, runtime);
            warmDecorators(biome.getDecorators(), engine.getSeedManager().getComponent(), counter, runtime);
            warmOres(biome.getOres(), engine.getSeedManager().getTerrain(), counter, runtime);
        }

        KList<IrisRegion> regions = engine.getDimension().getAllRegions(engine);
        regions.sort(Comparator.comparing(IrisRegion::getLoadKey));
        for (IrisRegion region : regions) {
            warmPlacements(region.getObjects(), root, counter, runtime);
            warmOres(region.getOres(), engine.getSeedManager().getTerrain(), counter, runtime);
        }

        warmOres(engine.getDimension().getOres(), engine.getSeedManager().getTerrain(), counter, runtime);

        IrisLogging.debug("[IrisEngine timing] cache warm " + counter[0] + " configs=" + (M.ms() - start) + "ms");
    }

    private static void warmPlacements(KList<IrisObjectPlacement> placements, RNG root, int[] counter,
                                       PackRuntime runtime) {
        if (placements == null) {
            return;
        }
        for (IrisObjectPlacement placement : placements) {
            if (placement == null) {
                continue;
            }
            RNG rng = root.nextParallelRNG(counter[0]++);
            placement.getSurfaceWarp(rng, runtime);
            placement.getDensity(rng, 0, 0, runtime);
        }
    }

    private static void warmDecorators(KList<IrisDecorator> decorators, long componentSeed, int[] counter, PackRuntime runtime) {
        if (decorators == null) {
            return;
        }
        for (IrisDecorator decorator : decorators) {
            if (decorator == null) {
                continue;
            }
            counter[0]++;
            RNG rng = new RNG(IrisEngineDecorator.seedForPart(componentSeed, decorator.getPartOf()));
            decorator.getHeightGenerator(rng, runtime);
            decorator.getGenerator(rng, runtime);
            decorator.getVarianceGenerator(rng, runtime);
        }
    }

    private static void warmOres(KList<IrisOreGenerator> ores, long terrainSeed, int[] counter, PackRuntime runtime) {
        if (ores == null) {
            return;
        }
        for (IrisOreGenerator ore : ores) {
            if (ore == null) {
                continue;
            }
            counter[0]++;
            ore.warm(new RNG(terrainSeed), runtime);
        }
    }
}
