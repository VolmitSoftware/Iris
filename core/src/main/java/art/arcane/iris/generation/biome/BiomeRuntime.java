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

package art.arcane.iris.generation.biome;

import art.arcane.iris.generation.runtime.PackRuntime;
import art.arcane.iris.generation.runtime.SeedTable;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.noise.CNG;

import java.util.concurrent.ConcurrentHashMap;

/**
 * What one engine derives from an {@link IrisBiome}: its scatter generators, the children generators per shrink
 * scale, and the height generators of its layer palettes.
 */
public final class BiomeRuntime {
    private static final int BIOME_SEED_CAPACITY = 8;
    private static final int CHILD_SEED_CAPACITY = 8;
    private static final int LAYER_HEIGHT_SEED_CAPACITY = 32;
    private static final int SURFACE_LAYER_SEED_OFFSET = 7235;
    private static final int SEA_LAYER_SEED_OFFSET = 7735;
    private static final int CEILING_LAYER_SEED_OFFSET = 7235;

    private final IrisBiome biome;
    private final PackRuntime runtime;
    private final SeedTable<CNG> biomeGenerators;
    private final ConcurrentHashMap<Long, SeedTable<CNG>> childGeneratorsByScale = new ConcurrentHashMap<>();
    private final SeedTable<KList<CNG>> layerHeights;
    private final SeedTable<KList<CNG>> seaLayerHeights;
    private final SeedTable<KList<CNG>> ceilingLayerHeights;

    public BiomeRuntime(IrisBiome biome, PackRuntime runtime) {
        this.biome = biome;
        this.runtime = runtime;
        this.biomeGenerators = new SeedTable<>(BIOME_SEED_CAPACITY, seed -> biome.createBiomeGenerator(seed, runtime));
        this.layerHeights = new SeedTable<>(LAYER_HEIGHT_SEED_CAPACITY, seed ->
                IrisBiomeLayerGenerator.createHeightGenerators(biome.getLayers(), SURFACE_LAYER_SEED_OFFSET, seed, runtime));
        this.seaLayerHeights = new SeedTable<>(LAYER_HEIGHT_SEED_CAPACITY, seed ->
                IrisBiomeLayerGenerator.createHeightGenerators(biome.getSeaLayers(), SEA_LAYER_SEED_OFFSET, seed, runtime));
        this.ceilingLayerHeights = new SeedTable<>(LAYER_HEIGHT_SEED_CAPACITY, seed ->
                IrisBiomeLayerGenerator.createHeightGenerators(biome.getCaveCeilingLayers(), CEILING_LAYER_SEED_OFFSET, seed, runtime));
    }

    public CNG biomeGenerator(long seed) {
        return biomeGenerators.get(seed);
    }

    public CNG childrenGenerator(long seed, double scale) {
        return childGeneratorsByScale.computeIfAbsent(Double.doubleToLongBits(scale), ignored ->
                new SeedTable<>(CHILD_SEED_CAPACITY, childSeed -> biome.createChildrenGenerator(childSeed, scale, runtime)))
                .get(seed);
    }

    public KList<CNG> layerHeights(long seed) {
        return layerHeights.get(seed);
    }

    public KList<CNG> seaLayerHeights(long seed) {
        return seaLayerHeights.get(seed);
    }

    public KList<CNG> ceilingLayerHeights(long seed) {
        return ceilingLayerHeights.get(seed);
    }
}
