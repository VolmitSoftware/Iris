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

package com.volmit.iris.core.gui;

import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisBiomePaletteLayer;
import com.volmit.iris.engine.object.IrisDimension;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.noise.CNG;

/**
 * How thick one biome palette is at a spot and which noise picks its block there, sampled exactly the way the
 * generator does for that kind of palette. The generator seeds each palette's noise from the first call it makes,
 * so these use the same random sources the terrain, carving and post passes use.
 */
abstract class BiomePalettePlacement {
    protected final IrisBiomePaletteLayer layer;
    protected final IrisData data;

    private BiomePalettePlacement(IrisBiomePaletteLayer layer, IrisData data) {
        this.layer = layer;
        this.data = data;
    }

    static BiomePalettePlacement of(Engine engine, IrisBiome biome, BiomePalette palette, IrisBiomePaletteLayer layer) {
        IrisData data = engine.getData();
        RNG terrainRandom = new RNG(engine.getSeedManager().getTerrain());
        RNG carveRandom = new RNG(engine.getSeedManager().getCarve());
        RNG postRandom = new RNG(engine.getSeedManager().getPost());
        int seaLevel = seaLevel(engine.getDimension());
        return switch (palette.kind()) {
            case LAYER -> new SurfaceLayer(biome, palette.index(), layer, terrainRandom, data);
            case SEA_LAYER -> new LegacyStackedLayer(layer, data, terrainRandom.nextParallelRNG(palette.index()),
                    biome.getLayerSeaHeightGenerators(terrainRandom, data), palette.index());
            case CAVE_CEILING_LAYER -> new LegacyStackedLayer(layer, data, carveRandom.nextParallelRNG(palette.index()),
                    biome.getLayerHeightGenerators(terrainRandom, data), palette.index());
            case WALL -> new SingleBlocks(layer, data, carveRandom, seaLevel);
            case SLAB -> new SingleBlocks(layer, data, postRandom, seaLevel);
        };
    }

    private static int seaLevel(IrisDimension dimension) {
        return dimension == null ? 0 : dimension.getFluidHeight();
    }

    /**
     * How many blocks of this palette are stacked at the spot.
     */
    abstract int thickness(int blockX, int blockZ);

    abstract double noise(int blockX, int blockZ, int depth);

    /**
     * What the preview simplifies about this palette, or null when it shows exactly what the world places.
     */
    String simplification() {
        return null;
    }

    private static final class SurfaceLayer extends BiomePalettePlacement {
        private final IrisBiome biome;
        private final int index;
        private final RNG random;

        private SurfaceLayer(IrisBiome biome, int index, IrisBiomePaletteLayer layer, RNG random, IrisData data) {
            super(layer, data);
            this.biome = biome;
            this.index = index;
            this.random = random;
        }

        @Override
        int thickness(int blockX, int blockZ) {
            return biome.getLayerThickness(index, blockX, blockZ, random, data);
        }

        @Override
        double noise(int blockX, int blockZ, int depth) {
            return biome.getLayerNoise(index, blockX, blockZ, depth, random, data);
        }
    }

    /**
     * Sea and cave ceiling layers go through the older sampling path, which divides by the zoom twice and borrows
     * its thickness noise from another list: a cave ceiling layer uses the surface layer at the same position's.
     */
    private static final class LegacyStackedLayer extends BiomePalettePlacement {
        private final RNG random;
        private final CNG thicknessNoise;

        private LegacyStackedLayer(IrisBiomePaletteLayer layer, IrisData data, RNG random, KList<CNG> thicknessNoises,
                                   int index) {
            super(layer, data);
            this.random = random;
            this.thicknessNoise = index < thicknessNoises.size() ? thicknessNoises.get(index) : null;
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
            return layer.getLayerGenerator(random, data)
                    .noise((blockX + depth) / zoom / zoom, depth / zoom, (blockZ - depth) / zoom / zoom);
        }

        @Override
        String simplification() {
            return thicknessNoise == null
                    ? "The generator takes this layer's thickness from the surface layer at the same position, which doesn't exist, so it is never placed."
                    : null;
        }
    }

    /**
     * Wall and slab blocks are picked one at a time with noise that also changes with height.
     */
    private static final class SingleBlocks extends BiomePalettePlacement {
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

        @Override
        String simplification() {
            return "These blocks also change with height; the preview shows them at sea level (Y " + sampleHeight + ").";
        }
    }
}
