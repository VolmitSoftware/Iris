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
import com.volmit.iris.engine.decorator.IrisEngineDecorator;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisBiomePaletteLayer;
import com.volmit.iris.engine.object.IrisBlockData;
import com.volmit.iris.engine.object.IrisDecorationPart;
import com.volmit.iris.engine.object.IrisDecorator;
import com.volmit.iris.engine.object.IrisDimension;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.noise.CNG;
import org.bukkit.block.data.Bisected;

import java.util.ArrayList;
import java.util.List;

/**
 * How thick one biome palette is at a spot and which noise picks its block there, sampled exactly the way the
 * generator does for that kind of palette. The generator seeds each palette's noise from the first call it makes,
 * so these use the same random sources the terrain, carving, post and decorator passes use.
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
        return switch (palette.kind()) {
            case LAYER -> new SurfaceLayer(biome, palette.index(), palette.layerIn(biome), terrainRandom, data);
            case SEA_LAYER -> new LegacyStackedLayer(palette.layerIn(biome), data, terrainRandom.nextParallelRNG(palette.index()),
                    biome.getLayerSeaHeightGenerators(terrainRandom, data), palette.index());
            case CAVE_CEILING_LAYER -> new LegacyStackedLayer(palette.layerIn(biome), data, carveRandom.nextParallelRNG(palette.index()),
                    biome.getLayerHeightGenerators(terrainRandom, data), palette.index());
            case WALL -> new SingleBlocks(palette.layerIn(biome), data, carveRandom, seaLevel);
            case SLAB -> new SingleBlocks(palette.layerIn(biome), data, postRandom, seaLevel);
            case DECORATOR -> new Decorations(biome, palette.decoratorIn(biome), engine.getSeedManager().getComponent(),
                    seaLevel, data);
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

    abstract boolean hasSlopeCondition();

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

        @Override
        int minThickness() {
            return layer.getMinHeight();
        }

        @Override
        int maxThickness() {
            return layer.getMaxHeight();
        }

        @Override
        boolean hasSlopeCondition() {
            return !layer.getSlopeCondition().isDefault();
        }
    }

    private static final class SurfaceLayer extends LayerPlacement {
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
    private static final class LegacyStackedLayer extends LayerPlacement {
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
    private static final class SingleBlocks extends LayerPlacement {
        private final RNG random;
        private final int sampleHeight;

        private SingleBlocks(IrisBiomePaletteLayer layer, IrisData data, RNG random, int sampleHeight) {
            super(layer, data);
            this.random = random;
            this.sampleHeight = sampleHeight;
        }

        @Override
        int minThickness() {
            return 1;
        }

        @Override
        int maxThickness() {
            return 1;
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

    /**
     * A decorator places where its style noise stays under its chance, and its variance noise picks the block there.
     * The generator seeds the variance noise from whichever block it decorates first, so the preview reuses that noise
     * once the world has generated some decorations.
     */
    private static final class Decorations extends BiomePalettePlacement {
        private final IrisDecorator decorator;
        private final CNG placementNoise;
        private final CNG blockNoise;
        private final int sampleHeight;
        private final boolean sharesSpotsWithOtherDecorators;

        private Decorations(IrisBiome biome, IrisDecorator decorator, long componentSeed, int sampleHeight, IrisData data) {
            super(data);
            this.decorator = decorator;
            RNG partRandom = new RNG(IrisEngineDecorator.partSeed(componentSeed, decorator.getPartOf()));
            this.placementNoise = decorator.getGenerator(partRandom, data);
            this.blockNoise = decorator.getVarianceGenerator(partRandom, data);
            this.sampleHeight = sampleHeight;
            this.sharesSpotsWithOtherDecorators = biome.getDecorators().stream()
                    .anyMatch(other -> other != null && other != decorator && other.getPartOf() == decorator.getPartOf());
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
        boolean hasSlopeCondition() {
            return !decorator.getSlopeCondition().isDefault();
        }

        @Override
        int thickness(int blockX, int blockZ) {
            double zoom = zoom();
            return placementNoise.fitDouble(0D, 1D, blockX / zoom, blockZ / zoom) <= decorator.getChance() ? 1 : 0;
        }

        // The generator swaps X and Z when it picks the block.
        @Override
        double noise(int blockX, int blockZ, int depth) {
            return blockNoise.noise(blockZ, sampleHeight, blockX);
        }

        @Override
        String simplification() {
            List<String> notes = new ArrayList<>();
            String partNote = partNote(decorator.getPartOf());
            if (partNote != null) {
                notes.add(partNote);
            }
            if (sharesSpotsWithOtherDecorators) {
                notes.add("Where other decorators of this biome could place too, the world picks one of them at random.");
            }
            if (placesTwoBlockTallPlants()) {
                notes.add("Two block tall plants show both halves squeezed into one block.");
            }
            if (decorator.isStacking()) {
                notes.add("It stacks " + decorator.getStackMin() + "–" + decorator.getStackMax()
                        + " blocks high; the preview shows the bottom block.");
            }
            if (blocks().size() > 1) {
                notes.add("The block also changes with height; the preview picks it at sea level (Y " + sampleHeight + ").");
            }
            return notes.isEmpty() ? null : String.join(" ", notes);
        }

        // The generator places any two-part block as a bottom half with its top half above it.
        private boolean placesTwoBlockTallPlants() {
            return !decorator.isStacking() && blocks().stream().anyMatch(block -> block.getBlockData(data) instanceof Bisected);
        }

        private static String partNote(IrisDecorationPart part) {
            return switch (part) {
                case NONE -> null;
                case SHORE_LINE -> "It only places along shore lines.";
                case SEA_SURFACE -> "It only places on the sea surface.";
                case SEA_FLOOR -> "It only places on the sea floor.";
                case CEILING -> "It places on cave ceilings and the undersides of overhangs, not on the ground.";
            };
        }
    }
}
