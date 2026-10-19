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

import com.volmit.iris.core.gui.BlockTextures.BlockTexture;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisBiomePaletteLayer;
import com.volmit.iris.engine.object.IrisBlockData;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.RNG;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class PaletteLayerPreview {
    static final int HISTOGRAM_BINS = 50;
    static final int STATISTICS_GRID_SIZE = 64;
    static final double STATISTICS_SPACING_BLOCKS = 37D;
    private static final int MISSING_BLOCK_COLOR = 0x5A5F6B;
    private static final double FAR_FROM_WEIGHT_FACTOR = 2D;
    private static final int BLOCK_CACHE_SIZE = 1 << 16;

    private final BiomePalette palette;
    private final KList<IrisBlockData> blocks;
    private final BiomePalettePlacement placement;
    private final int depth;
    private final IrisData data;
    private final int[] entryBySlot;
    private final BiomeTintField biomeTints;
    private final BlockTexture[] textureByEntry;
    private final BlockTexture[] legendTextureByEntry;
    private final int[] legendColorByEntry;
    private final List<Entry> entries;
    private final SampledBlock[] blockCache = new SampledBlock[BLOCK_CACHE_SIZE];
    private final long[] histogram;
    private final double absentShare;
    private final double outOfRangeShare;

    private PaletteLayerPreview(BiomePalette palette, BiomePalettePlacement placement, int depth, IrisData data,
                                BlockTextures textures, BiomeTintField biomeTints) {
        this.palette = palette;
        this.blocks = placement.blocks();
        this.placement = placement;
        this.depth = depth;
        this.data = data;
        this.entryBySlot = IrisBiomePaletteLayer.paletteEntryPerSlot(blocks, data).stream().mapToInt(Integer::intValue).toArray();
        if (entryBySlot.length == 0) {
            throw new IllegalStateException("No block of " + palette.biomeKey() + " " + palette.lowercaseLabel() + " could be found");
        }
        this.biomeTints = biomeTints;
        this.textureByEntry = resolveEntryTextures(textures);
        this.legendTextureByEntry = tintForLegend(textureByEntry);
        this.legendColorByEntry = resolveLegendColors();

        Statistics statistics = measure();
        this.histogram = statistics.histogram();
        this.absentShare = statistics.absentShare();
        this.outOfRangeShare = statistics.outOfRangeShare();
        this.entries = describeEntries(statistics.sharesByEntry());
    }

    static PaletteLayerPreview load(Engine engine, BiomePalette palette, int depth, BlockTextures textures) {
        if (engine == null || engine.isClosed()) {
            throw new IllegalStateException("Open a studio world to preview pack palettes");
        }
        IrisData data = engine.getData();
        IrisBiome biome = data.getBiomeLoader().load(palette.biomeKey());
        if (biome == null || palette.blocksIn(biome) == null) {
            throw new IllegalStateException("Palette removed during hotload: " + palette.biomeKey() + " " + palette.lowercaseLabel());
        }
        BiomePalettePlacement placement = BiomePalettePlacement.of(engine, biome, palette);
        BiomeTintField biomeTints = textures == null
                ? null
                : new BiomeTintField(biome, new RNG(engine.getSeedManager().getBiome()), textures.biomeColors());
        return new PaletteLayerPreview(palette, placement, depth, data, textures, biomeTints);
    }

    // The world picks one block per whole coordinate, and zoomed in, many pixels fall on the same block.
    double sample(double x, double z) {
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        int cacheSlot = blockCacheSlot(blockX, blockZ);
        SampledBlock cached = blockCache[cacheSlot];
        if (cached != null && cached.x() == blockX && cached.z() == blockZ) {
            return cached.noise();
        }
        double noise = sampleBlock(blockX, blockZ);
        blockCache[cacheSlot] = new SampledBlock(blockX, blockZ, noise);
        return noise;
    }

    private double sampleBlock(int blockX, int blockZ) {
        if (placement.thickness(blockX, blockZ) <= depth) {
            return Double.NaN;
        }
        return placement.noise(blockX, blockZ, depth);
    }

    private static int blockCacheSlot(int blockX, int blockZ) {
        long hash = blockX * 0x9E3779B97F4A7C15L ^ blockZ * 0xC2B2AE3D27D4EB4FL;
        return (int) ((hash ^ (hash >>> 29)) & (BLOCK_CACHE_SIZE - 1));
    }

    int colorForNoise(double noise) {
        return legendColorByEntry[entryIndexForNoise(noise)];
    }

    int blockColorAt(double noise, double worldX, double worldZ, boolean showTexturePixels) {
        int entry = entryIndexForNoise(noise);
        BlockTexture texture = textureByEntry[entry];
        if (texture == null) {
            return legendColorByEntry[entry];
        }
        int untintedColor = showTexturePixels ? texture.texelAt(worldX, worldZ) : texture.averageColor();
        if (texture.biomeTint() == null) {
            return untintedColor;
        }
        int tint = biomeTints.colorAt(texture.biomeTint(), (int) Math.floor(worldX), (int) Math.floor(worldZ));
        return BlockTexture.multiply(untintedColor, tint);
    }

    Entry entryForNoise(double noise) {
        return entries.get(entryIndexForNoise(noise));
    }

    String biomeKey() {
        return palette.biomeKey();
    }

    BiomePalette palette() {
        return palette;
    }

    String simplification() {
        return placement.simplification();
    }

    int depth() {
        return depth;
    }

    int minThickness() {
        return placement.minThickness();
    }

    int maxThickness() {
        return placement.maxThickness();
    }

    double zoom() {
        return placement.zoom();
    }

    boolean hasSlopeCondition() {
        return placement.hasSlopeCondition();
    }

    List<Entry> entries() {
        return entries;
    }

    long[] histogram() {
        return histogram.clone();
    }

    double absentShare() {
        return absentShare;
    }

    double outOfRangeShare() {
        return outOfRangeShare;
    }

    static double statisticsAreaBlocks() {
        return STATISTICS_GRID_SIZE * STATISTICS_SPACING_BLOCKS;
    }

    private int entryIndexForNoise(double noise) {
        return entryBySlot[IrisBiomePaletteLayer.slotForNoise(noise, entryBySlot.length)];
    }

    private BlockTexture[] resolveEntryTextures(BlockTextures textures) {
        BlockTexture[] resolved = new BlockTexture[blocks.size()];
        if (textures == null) {
            return resolved;
        }
        for (int entry = 0; entry < blocks.size(); entry++) {
            BlockData block = blocks.get(entry).getBlockData(data);
            resolved[entry] = block == null ? null : textures.topTexture(block).orElse(null);
        }
        return resolved;
    }

    // The legend shows tinted blocks as they look at the world origin, where the statistics are sampled too.
    private BlockTexture[] tintForLegend(BlockTexture[] textures) {
        BlockTexture[] tinted = new BlockTexture[textures.length];
        for (int entry = 0; entry < textures.length; entry++) {
            BlockTexture texture = textures[entry];
            tinted[entry] = texture == null || texture.biomeTint() == null
                    ? texture
                    : texture.tinted(biomeTints.colorAt(texture.biomeTint(), 0, 0));
        }
        return tinted;
    }

    // Blocks without a texture, like custom blocks or everything before the textures load, fall back to map colours.
    private int[] resolveLegendColors() {
        List<BlockData> untexturedBlocks = new ArrayList<>();
        for (int entry = 0; entry < blocks.size(); entry++) {
            BlockData block = blocks.get(entry).getBlockData(data);
            if (block != null && textureByEntry[entry] == null) {
                untexturedBlocks.add(block);
            }
        }
        Map<String, Integer> mapColorsByBlock = BlockPreviewColors.colorsByBlock(untexturedBlocks);
        int[] colors = new int[blocks.size()];
        for (int entry = 0; entry < blocks.size(); entry++) {
            BlockData block = blocks.get(entry).getBlockData(data);
            if (block == null) {
                colors[entry] = MISSING_BLOCK_COLOR;
            } else if (legendTextureByEntry[entry] != null) {
                colors[entry] = legendTextureByEntry[entry].averageColor();
            } else {
                colors[entry] = mapColorsByBlock.get(block.getAsString(true));
            }
        }
        return colors;
    }

    private Statistics measure() {
        long[] values = new long[HISTOGRAM_BINS];
        long[] samplesByEntry = new long[blocks.size()];
        long present = 0;
        long absent = 0;
        long outOfRange = 0;
        double origin = -statisticsAreaBlocks() / 2D;
        for (int gridX = 0; gridX < STATISTICS_GRID_SIZE; gridX++) {
            for (int gridZ = 0; gridZ < STATISTICS_GRID_SIZE; gridZ++) {
                double noise = sample(origin + gridX * STATISTICS_SPACING_BLOCKS, origin + gridZ * STATISTICS_SPACING_BLOCKS);
                if (Double.isNaN(noise)) {
                    absent++;
                    continue;
                }
                present++;
                if (noise < 0D || noise > 1D) {
                    outOfRange++;
                }
                values[Math.clamp((int) (noise * HISTOGRAM_BINS), 0, HISTOGRAM_BINS - 1)]++;
                samplesByEntry[entryIndexForNoise(noise)]++;
            }
        }
        double[] sharesByEntry = new double[samplesByEntry.length];
        for (int entry = 0; entry < samplesByEntry.length; entry++) {
            sharesByEntry[entry] = present == 0 ? 0D : samplesByEntry[entry] / (double) present;
        }
        long total = present + absent;
        return new Statistics(values, sharesByEntry, absent / (double) total, present == 0 ? 0D : outOfRange / (double) present);
    }

    private List<Entry> describeEntries(double[] sharesByEntry) {
        int slotCount = entryBySlot.length;
        List<Entry> described = new ArrayList<>(blocks.size());
        for (int entry = 0; entry < blocks.size(); entry++) {
            IrisBlockData block = blocks.get(entry);
            int firstSlot = firstSlotOf(entry);
            int lastSlot = lastSlotOf(entry);
            boolean placed = firstSlot >= 0;
            described.add(new Entry(
                    block.getBlock(),
                    block.getWeight(),
                    block.getBlockData(data) != null,
                    legendColorByEntry[entry],
                    legendTextureByEntry[entry],
                    placed ? (lastSlot - firstSlot + 1) / (double) slotCount : 0D,
                    sharesByEntry[entry],
                    placed ? slotLowerBound(firstSlot, slotCount) : Double.NaN,
                    placed ? slotUpperBound(lastSlot, slotCount) : Double.NaN
            ));
        }
        return List.copyOf(described);
    }

    private int firstSlotOf(int entry) {
        for (int slot = 0; slot < entryBySlot.length; slot++) {
            if (entryBySlot[slot] == entry) {
                return slot;
            }
        }
        return -1;
    }

    private int lastSlotOf(int entry) {
        for (int slot = entryBySlot.length - 1; slot >= 0; slot--) {
            if (entryBySlot[slot] == entry) {
                return slot;
            }
        }
        return -1;
    }

    // Slots are picked by rounding, so the first and last slot only own half a step of the value range.
    private static double slotLowerBound(int slot, int slotCount) {
        return slot == 0 ? 0D : (slot - 0.5D) / (slotCount - 1);
    }

    private static double slotUpperBound(int slot, int slotCount) {
        return slot == slotCount - 1 ? 1D : (slot + 0.5D) / (slotCount - 1);
    }

    record Entry(String block, int weight, boolean found, int color, BlockTexture texture, double weightShare,
                 double actualShare, double lowestNoise, double highestNoise) {
        boolean isFarBelowWeight() {
            return found && actualShare < weightShare / FAR_FROM_WEIGHT_FACTOR;
        }

        boolean isFarAboveWeight() {
            return found && actualShare > weightShare * FAR_FROM_WEIGHT_FACTOR;
        }
    }

    private record SampledBlock(int x, int z, double noise) {
    }

    private record Statistics(long[] histogram, double[] sharesByEntry, double absentShare, double outOfRangeShare) {
    }
}
