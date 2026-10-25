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

import art.arcane.iris.studio.map.BlockTextures.BlockTexture;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class PaletteLayerPreview {
    static final int HISTOGRAM_BINS = 50;
    private static final int STATISTICS_GRID_SIZE = 64;
    private static final double STATISTICS_SPACING_BLOCKS = 37D;
    private static final int MISSING_BLOCK_COLOR = 0x5A5F6B;
    private static final double FAR_FROM_WEIGHT_FACTOR = 2D;
    private static final int BLOCK_CACHE_SIZE = 1 << 16;
    private static final int NO_OVERHANG = -1;

    private final BiomePalette palette;
    private final KList<IrisBlockData> blocks;
    private final BiomePalettePlacement placement;
    private final PaletteLayerPreview ground;
    private final boolean[] showsGroundByEntry;
    private final int depth;
    private final IrisData data;
    private final int[] entryBySlot;
    private final BiomeTintField biomeTints;
    private final BlockTexture[] textureByEntry;
    private final BlockTexture[] upperHalfByEntry;
    private final boolean hasTallPlants;
    private final BlockTexture[] legendTextureByEntry;
    private final int[] legendColorByEntry;
    private final List<Entry> entries;
    private final SampledBlock[] blockCache = new SampledBlock[BLOCK_CACHE_SIZE];
    private final long[] histogram;

    private PaletteLayerPreview(BiomePalette palette, BiomePalettePlacement placement, PaletteLayerPreview ground, int depth,
                                IrisData data, BlockTextures textures, BiomeTintField biomeTints) {
        this.palette = palette;
        this.blocks = placement.blocks();
        this.placement = placement;
        this.ground = ground;
        this.depth = depth;
        this.data = data;
        this.entryBySlot = PaletteSlots.entryPerSlot(blocks, data);
        if (entryBySlot.length == 0) {
            throw new IllegalStateException("No block of " + palette.biomeKey() + " " + palette.lowercaseLabel() + " could be found");
        }
        this.biomeTints = biomeTints;
        this.showsGroundByEntry = resolveGroundShowingEntries();
        this.textureByEntry = resolveEntryTextures(textures);
        this.upperHalfByEntry = resolveUpperHalves(textures);
        this.hasTallPlants = Arrays.stream(upperHalfByEntry).anyMatch(Objects::nonNull);
        this.legendTextureByEntry = tintForLegend(legendTextures());
        this.legendColorByEntry = resolveLegendColors();

        Statistics statistics = measure();
        this.histogram = statistics.histogram();
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
        PaletteLayerPreview ground = palette.kind() == BiomePalette.Kind.DECORATOR
                ? groundUnder(engine, palette.biomeKey(), biome, data, textures, biomeTints)
                : null;
        return new PaletteLayerPreview(palette, placement, ground, depth, data, textures, biomeTints);
    }

    // Decorations stand on the biome's top surface layer, so that is what shows around and between them.
    private static PaletteLayerPreview groundUnder(Engine engine, String biomeKey, IrisBiome biome, IrisData data,
                                                   BlockTextures textures, BiomeTintField biomeTints) {
        BiomePalette topLayer = new BiomePalette(biomeKey, BiomePalette.Kind.LAYER, 0);
        KList<IrisBlockData> blocks = topLayer.blocksIn(biome);
        if (blocks == null || blocks.isEmpty()) {
            return null;
        }
        return new PaletteLayerPreview(topLayer, BiomePalettePlacement.of(engine, biome, topLayer), null, 0, data, textures,
                biomeTints);
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
        int overhang = showTexturePixels ? overhangColorAt(worldX, worldZ) : NO_OVERHANG;
        if (overhang != NO_OVERHANG) {
            return overhang;
        }
        int entry = entryIndexForNoise(noise);
        if (showsGroundByEntry[entry]) {
            return bareColorAt(worldX, worldZ, showTexturePixels);
        }
        BlockTexture texture = textureByEntry[entry];
        if (texture == null) {
            return legendColorByEntry[entry];
        }
        if (ground != null && showTexturePixels && !texture.isOpaqueAt(worldX, worldZ)) {
            return groundColorAt(worldX, worldZ, true);
        }
        int untintedColor = showTexturePixels ? texture.texelAt(worldX, worldZ) : texture.averageColor();
        return tinted(texture, untintedColor, worldX, worldZ);
    }

    /**
     * The colour where the palette places nothing: the ground under a decorator, or plain dark otherwise.
     */
    int bareColorAt(double worldX, double worldZ, boolean showTexturePixels) {
        if (ground == null) {
            return PaletteLayerScale.ABSENT_COLOR;
        }
        int overhang = showTexturePixels ? overhangColorAt(worldX, worldZ) : NO_OVERHANG;
        return overhang != NO_OVERHANG ? overhang : groundColorAt(worldX, worldZ, showTexturePixels);
    }

    // A two block tall plant stands up on the map: its top half covers the block above it, which is one block north.
    private int overhangColorAt(double worldX, double worldZ) {
        if (!hasTallPlants) {
            return NO_OVERHANG;
        }
        double plantZ = worldZ + 1D;
        double noise = sample(worldX, plantZ);
        if (Double.isNaN(noise)) {
            return NO_OVERHANG;
        }
        BlockTexture upperHalf = upperHalfByEntry[entryIndexForNoise(noise)];
        if (upperHalf == null || !upperHalf.isOpaqueAt(worldX, worldZ)) {
            return NO_OVERHANG;
        }
        return tinted(upperHalf, upperHalf.texelAt(worldX, worldZ), worldX, plantZ);
    }

    private int tinted(BlockTexture texture, int untintedColor, double worldX, double worldZ) {
        if (texture.biomeTint() == null) {
            return untintedColor;
        }
        int tint = biomeTints.colorAt(texture.biomeTint(), (int) Math.floor(worldX), (int) Math.floor(worldZ));
        return BlockTexture.multiply(untintedColor, tint);
    }

    private int groundColorAt(double worldX, double worldZ, boolean showTexturePixels) {
        double noise = ground.sample(worldX, worldZ);
        return Double.isNaN(noise) ? PaletteLayerScale.ABSENT_COLOR : ground.blockColorAt(noise, worldX, worldZ, showTexturePixels);
    }

    // A decorator places air to leave gaps, which looks the same as placing nothing.
    private boolean[] resolveGroundShowingEntries() {
        boolean[] showsGround = new boolean[blocks.size()];
        for (int entry = 0; entry < blocks.size(); entry++) {
            NativeBlockState block = blocks.get(entry).getBlockData(data);
            showsGround[entry] = ground != null && block != null && block.isAir();
        }
        return showsGround;
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

    List<Entry> entries() {
        return entries;
    }

    long[] histogram() {
        return histogram.clone();
    }

    private int entryIndexForNoise(double noise) {
        return entryBySlot[PaletteSlots.slotForNoise(noise, entryBySlot.length)];
    }

    private BlockTexture[] resolveEntryTextures(BlockTextures textures) {
        BlockTexture[] resolved = new BlockTexture[blocks.size()];
        if (textures == null) {
            return resolved;
        }
        for (int entry = 0; entry < blocks.size(); entry++) {
            NativeBlockState block = blocks.get(entry).getBlockData(data);
            resolved[entry] = block == null ? null : textureOf(block, textures).orElse(null);
        }
        return resolved;
    }

    private BlockTexture[] resolveUpperHalves(BlockTextures textures) {
        BlockTexture[] resolved = new BlockTexture[blocks.size()];
        if (textures == null || palette.kind() != BiomePalette.Kind.DECORATOR) {
            return resolved;
        }
        for (int entry = 0; entry < blocks.size(); entry++) {
            NativeBlockState block = blocks.get(entry).getBlockData(data);
            resolved[entry] = block == null ? null : textures.upperHalfIcon(block).orElse(null);
        }
        return resolved;
    }

    // Tall plants are known by their top half, the way Minecraft draws them in the inventory.
    private BlockTexture[] legendTextures() {
        BlockTexture[] legend = textureByEntry.clone();
        for (int entry = 0; entry < legend.length; entry++) {
            if (upperHalfByEntry[entry] != null) {
                legend[entry] = upperHalfByEntry[entry];
            }
        }
        return legend;
    }

    private Optional<BlockTexture> textureOf(NativeBlockState block, BlockTextures textures) {
        return palette.kind() == BiomePalette.Kind.DECORATOR ? textures.iconTexture(block) : textures.topTexture(block);
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

    // Blocks without a texture, like custom blocks or everything before the textures load, fall back to plain colours.
    private int[] resolveLegendColors() {
        List<NativeBlockState> untexturedBlocks = new ArrayList<>();
        for (int entry = 0; entry < blocks.size(); entry++) {
            NativeBlockState block = blocks.get(entry).getBlockData(data);
            if (block != null && textureByEntry[entry] == null) {
                untexturedBlocks.add(block);
            }
        }
        Map<String, Integer> mapColorsByBlock = BlockPreviewColors.colorsByBlock(untexturedBlocks);
        int[] colors = new int[blocks.size()];
        for (int entry = 0; entry < blocks.size(); entry++) {
            NativeBlockState block = blocks.get(entry).getBlockData(data);
            if (block == null) {
                colors[entry] = MISSING_BLOCK_COLOR;
            } else if (legendTextureByEntry[entry] != null) {
                colors[entry] = legendTextureByEntry[entry].averageColor();
            } else {
                colors[entry] = mapColorsByBlock.get(block.key());
            }
        }
        return colors;
    }

    private Statistics measure() {
        long[] values = new long[HISTOGRAM_BINS];
        long[] samplesByEntry = new long[blocks.size()];
        long present = 0;
        double origin = -STATISTICS_GRID_SIZE * STATISTICS_SPACING_BLOCKS / 2D;
        for (int gridX = 0; gridX < STATISTICS_GRID_SIZE; gridX++) {
            for (int gridZ = 0; gridZ < STATISTICS_GRID_SIZE; gridZ++) {
                double noise = sample(origin + gridX * STATISTICS_SPACING_BLOCKS, origin + gridZ * STATISTICS_SPACING_BLOCKS);
                if (Double.isNaN(noise)) {
                    continue;
                }
                present++;
                values[Math.clamp((int) (noise * HISTOGRAM_BINS), 0, HISTOGRAM_BINS - 1)]++;
                samplesByEntry[entryIndexForNoise(noise)]++;
            }
        }
        double[] sharesByEntry = new double[samplesByEntry.length];
        for (int entry = 0; entry < samplesByEntry.length; entry++) {
            sharesByEntry[entry] = present == 0 ? 0D : samplesByEntry[entry] / (double) present;
        }
        return new Statistics(values, sharesByEntry);
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

    private record Statistics(long[] histogram, double[] sharesByEntry) {
    }
}
