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

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.util.collection.KList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The blocks every biome palette places, as the studio's pattern view lists and lays them out: one entry per palette,
 * and for a square of columns the block each column gets at a depth.
 */
public final class PalettePatterns {
    public static final short NO_BLOCK = -1;

    private PalettePatterns() {
    }

    /**
     * @param key    names the palette in {@link #pattern}
     * @param group  the biome the palette belongs to
     * @param depths how many blocks deep it can stack, at least 1
     * @param facts  short lines about it, such as its blocks
     */
    public record Entry(String key, String name, String group, int depths, List<String> facts) {
    }

    /**
     * @param blocks the block states the picks index
     * @param picks  each column's block, row by row from the north west, or {@link #NO_BLOCK}
     */
    public record Pattern(int columns, List<String> blocks, short[] picks) {
    }

    public static List<Entry> entries(Engine engine) {
        IrisData data = engine.getData();
        String[] biomeKeys = data.getBiomeLoader().getPossibleKeys();
        if (biomeKeys == null) {
            return List.of();
        }
        Arrays.sort(biomeKeys);
        List<Entry> entries = new ArrayList<>();
        for (String biomeKey : biomeKeys) {
            IrisBiome biome = data.getBiomeLoader().load(biomeKey);
            if (biome != null) {
                PaletteCatalog.summarizePalettes(biomeKey, biome).forEach(summary -> entries.add(entryOf(biome, summary)));
            }
        }
        return entries;
    }

    /** Empty when the palette no longer exists, such as after a hotload removed it. */
    public static Optional<Pattern> pattern(Engine engine, String key, int depth, int originX, int originZ, int columns, int blocksPerColumn) {
        BiomePalette palette = BiomePalette.fromId(key);
        IrisData data = engine.getData();
        IrisBiome biome = palette == null ? null : data.getBiomeLoader().load(palette.biomeKey());
        KList<IrisBlockData> blocks = biome == null ? null : palette.blocksIn(biome);
        if (blocks == null) {
            return Optional.empty();
        }
        BiomePalettePlacement placement = BiomePalettePlacement.of(engine, biome, palette);
        int[] entryBySlot = PaletteSlots.entryPerSlot(blocks, data);
        short[] picks = new short[columns * columns];
        for (int row = 0; row < columns; row++) {
            for (int column = 0; column < columns; column++) {
                picks[row * columns + column] = pickAt(placement, entryBySlot, depth,
                        originX + column * blocksPerColumn, originZ + row * blocksPerColumn);
            }
        }
        return Optional.of(new Pattern(columns, blocks.stream().map(IrisBlockData::stateKey).toList(), picks));
    }

    private static short pickAt(BiomePalettePlacement placement, int[] entryBySlot, int depth, int blockX, int blockZ) {
        if (entryBySlot.length == 0 || placement.thickness(blockX, blockZ) <= depth) {
            return NO_BLOCK;
        }
        double noise = placement.noise(blockX, blockZ, depth);
        return (short) entryBySlot[PaletteSlots.slotForNoise(noise, entryBySlot.length)];
    }

    private static Entry entryOf(IrisBiome biome, PaletteCatalog.PaletteSummary summary) {
        BiomePalette palette = summary.palette();
        int depths = palette.kind().isStacked() ? Math.max(1, palette.layerIn(biome).getMaxHeight()) : 1;
        List<String> facts = new ArrayList<>();
        facts.add(summary.blocks());
        if (summary.snippet() != null) {
            facts.add("From the " + summary.snippet() + " snippet");
        }
        return new Entry(palette.id(), palette.label(), palette.biomeKey(), depths, List.copyOf(facts));
    }
}
