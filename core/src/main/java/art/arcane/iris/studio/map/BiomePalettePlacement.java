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

package art.arcane.iris.studio.map;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.decoration.IrisDecorationPart;
import art.arcane.iris.generation.decoration.IrisEngineDecorator;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomePaletteLayer;
import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.decoration.IrisDecorator;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;

/**
 * How thick one biome palette is at a spot and which noise picks its block there, sampled exactly the way the
 * generator does for that kind of palette, with the same random sources the terrain, carving, post and decorator
 * passes use.
 */
abstract class BiomePalettePlacement {
    protected final IrisData data;

    private BiomePalettePlacement(IrisData data) {
        this.data = data;
    }

    static BiomePalettePlacement of(Engine engine, IrisBiome biome, BiomePalette palette) {
        IrisData data = engine.getData();
        RNG terrainRandom = new RNG(engine.getSeedManager().getTerrain());
        RNG carveRandom = new RNG(engine.getSeedManager().getCarve());
        RNG postRandom = new RNG(engine.getSeedManager().getPost());
        int seaLevel = seaLevel(engine.getDimension());
        int index = palette.index();
        return switch (palette.kind()) {
            case LAYER -> new StackedLayer(palette.layerIn(biome), index, biome.getLayerHeightGenerators(terrainRandom, data),
                    terrainRandom, data);
            case SEA_LAYER -> new StackedLayer(palette.layerIn(biome), index, biome.getLayerSeaHeightGenerators(terrainRandom, data),
                    terrainRandom, data);
            case CAVE_CEILING_LAYER -> new StackedLayer(palette.layerIn(biome), index,
                    biome.getCaveCeilingLayerHeightGenerators(carveRandom, data), carveRandom, data);
            case WALL -> new SingleBlocks(palette.layerIn(biome), data, carveRandom, seaLevel);
            case SLAB -> new SingleBlocks(palette.layerIn(biome), data, postRandom, seaLevel);
            case DECORATOR -> new Decorations(palette.decoratorIn(biome), engine.getSeedManager().getComponent(), seaLevel, data);
        };
    }

    private static int seaLevel(IrisDimension dimension) {
        return dimension == null ? 0 : dimension.getFluidHeight();
    }

    abstract KList<IrisBlockData> blocks();

    abstract double zoom();

    int minThickness() {
        return 1;
    }

    int maxThickness() {
        return 1;
    }

    /**
     * How many blocks of this palette are stacked at the spot.
     */
    abstract int thickness(int blockX, int blockZ);

    abstract double noise(int blockX, int blockZ, int depth);

    private abstract static class LayerPlacement extends BiomePalettePlacement {
        protected final IrisBiomePaletteLayer layer;

        private LayerPlacement(IrisBiomePaletteLayer layer, IrisData data) {
            super(data);
            this.layer = layer;
        }

        @Override
        KList<IrisBlockData> blocks() {
            return layer.getPalette();
        }

        @Override
        double zoom() {
            return layer.getZoom();
        }
    }

    /**
     * Surface, sea and cave ceiling layers. Each layer's thickness comes from its own noise in the list the generator
     * builds, and every depth reseeds the block noise, sampled through the zoom twice.
     */
    private static final class StackedLayer extends LayerPlacement {
        private final int index;
        private final RNG random;
        private final CNG thicknessNoise;

        private StackedLayer(IrisBiomePaletteLayer layer, int index, KList<CNG> thicknessNoises, RNG random, IrisData data) {
            super(layer, data);
            this.index = index;
            this.random = random;
            this.thicknessNoise = index < thicknessNoises.size() ? thicknessNoises.get(index) : null;
        }

        @Override
        int minThickness() {
            return layer.getMinHeight();
        }

        @Override
        int maxThickness() {
            return layer.getMaxHeight();
        }

        @Override
        int thickness(int blockX, int blockZ) {
            if (thicknessNoise == null) {
                return 0;
            }
            double zoom = layer.getZoom();
            return Math.max(0, thicknessNoise.fit(layer.getMinHeight(), layer.getMaxHeight(), blockX / zoom, blockZ / zoom));
        }

        @Override
        double noise(int blockX, int blockZ, int depth) {
            double zoom = layer.getZoom();
            return layer.getLayerGenerator(random, index + depth, data)
                    .noise((blockX + depth) / zoom / zoom, depth / zoom, (blockZ - depth) / zoom / zoom);
        }
    }

    /**
     * Wall and slab blocks are picked one at a time with noise that also changes with height.
     */
    private static final class SingleBlocks extends LayerPlacement {
        private final RNG random;
        private final int sampleHeight;

        private SingleBlocks(IrisBiomePaletteLayer layer, IrisData data, RNG random, int sampleHeight) {
            super(layer, data);
            this.random = random;
            this.sampleHeight = sampleHeight;
        }

        @Override
        int thickness(int blockX, int blockZ) {
            return 1;
        }

        @Override
        double noise(int blockX, int blockZ, int depth) {
            double zoom = layer.getZoom();
            return layer.getLayerGenerator(random, data).noise(blockX / zoom, sampleHeight / zoom, blockZ / zoom);
        }
    }

    /**
     * A decorator places where its style noise stays under its chance, and its variance noise picks the block there.
     * Surface and ceiling decorators pick single blocks from flat noise; sea and shore decorators pick from noise that
     * also changes with height.
     */
    private static final class Decorations extends BiomePalettePlacement {
        private final IrisDecorator decorator;
        private final CNG placementNoise;
        private final CNG blockNoise;
        private final boolean picksFromFlatNoise;
        private final int sampleHeight;

        private Decorations(IrisDecorator decorator, long componentSeed, int sampleHeight, IrisData data) {
            super(data);
            this.decorator = decorator;
            RNG partRandom = new RNG(IrisEngineDecorator.seedForPart(componentSeed, decorator.getPartOf()));
            this.placementNoise = decorator.getGenerator(partRandom, data);
            this.blockNoise = decorator.getVarianceGenerator(partRandom, data);
            this.picksFromFlatNoise = decorator.getPartOf() == IrisDecorationPart.NONE
                    || decorator.getPartOf() == IrisDecorationPart.CEILING;
            this.sampleHeight = sampleHeight;
        }

        @Override
        KList<IrisBlockData> blocks() {
            return decorator.getPalette();
        }

        @Override
        double zoom() {
            return decorator.getStyle().getZoom();
        }

        @Override
        int thickness(int blockX, int blockZ) {
            double zoom = zoom();
            return placementNoise.fitDouble(0D, 1D, blockX / zoom, blockZ / zoom) <= decorator.getChance() ? 1 : 0;
        }

        // The generator swaps X and Z when it picks the block.
        @Override
        double noise(int blockX, int blockZ, int depth) {
            return picksFromFlatNoise ? blockNoise.noiseFast2D(blockZ, blockX) : blockNoise.noise(blockZ, sampleHeight, blockX);
        }
    }
}
