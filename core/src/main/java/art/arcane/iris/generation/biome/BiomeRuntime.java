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

import art.arcane.iris.generation.decoration.DecoratorRuntime;
import art.arcane.iris.generation.decoration.IrisDecorationPart;
import art.arcane.iris.generation.decoration.IrisDecorator;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Supplier;

/**
 * What one engine derives from an {@link IrisBiome}: its scatter generators, the children generators per shrink
 * scale, the height generators of its layer palettes and the runtimes of those palettes, linked by layer index so
 * painting a column reads them without looking each layer up.
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
    private final LinkedLayers surfaceLayers;
    private final LinkedLayers seaLayers;
    private final LinkedLayers ceilingLayers;
    private final AtomicReferenceArray<LinkedDecorators> decoratorBuckets =
            new AtomicReferenceArray<>(IrisDecorationPart.values().length);

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
        this.surfaceLayers = new LinkedLayers(biome::getLayers);
        this.seaLayers = new LinkedLayers(biome::getSeaLayers);
        this.ceilingLayers = new LinkedLayers(biome::getCaveCeilingLayers);
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

    /** The runtime of the surface layer at this index of {@link IrisBiome#getLayers()}. */
    public PaletteLayerRuntime surfaceLayer(int index) {
        return surfaceLayers.get(index);
    }

    /** The runtime of the sea layer at this index of {@link IrisBiome#getSeaLayers()}. */
    public PaletteLayerRuntime seaLayer(int index) {
        return seaLayers.get(index);
    }

    /** The runtime of the cave ceiling layer at this index of {@link IrisBiome#getCaveCeilingLayers()}. */
    public PaletteLayerRuntime ceilingLayer(int index) {
        return ceilingLayers.get(index);
    }

    /** The runtimes of this part's {@link IrisBiome#getDecoratorBucket} decorators, in bucket order. */
    public DecoratorRuntime[] decorators(IrisDecorationPart part) {
        IrisDecorator[] bucket = biome.getDecoratorBucket(part);
        LinkedDecorators linked = decoratorBuckets.getAcquire(part.ordinal());
        if (linked != null && linked.bucket() == bucket) {
            return linked.runtimes();
        }
        DecoratorRuntime[] runtimes = new DecoratorRuntime[bucket.length];
        for (int index = 0; index < bucket.length; index++) {
            runtimes[index] = runtime.decorator(bucket[index]);
        }
        decoratorBuckets.setRelease(part.ordinal(), new LinkedDecorators(bucket, runtimes));
        return runtimes;
    }

    private record LinkedDecorators(IrisDecorator[] bucket, DecoratorRuntime[] runtimes) {
    }

    private final class LinkedLayers {
        private final Supplier<KList<IrisBiomePaletteLayer>> layers;
        private volatile PaletteLayerRuntime[] linked;

        private LinkedLayers(Supplier<KList<IrisBiomePaletteLayer>> layers) {
            this.layers = layers;
        }

        private PaletteLayerRuntime get(int index) {
            PaletteLayerRuntime[] current = linked;
            if (current == null) {
                current = link(layers.get());
                linked = current;
            }
            return current[index];
        }

        private PaletteLayerRuntime[] link(KList<IrisBiomePaletteLayer> source) {
            PaletteLayerRuntime[] result = new PaletteLayerRuntime[source.size()];
            for (int index = 0; index < result.length; index++) {
                result[index] = runtime.paletteLayer(source.get(index));
            }
            return result;
        }
    }
}
