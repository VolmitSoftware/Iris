/*
 * Iris is a World Generator for Minecraft Servers
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

package art.arcane.iris.core.studio.saved;

import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import art.arcane.volmlib.util.nbt.tag.ListTag;
import art.arcane.volmlib.util.nbt.tag.StringTag;
import art.arcane.volmlib.util.nbt.tag.Tag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * One saved chunk as Minecraft 1.18 and later store it: block states and biomes by section, palette plus packed
 * indices, and the heightmaps.
 */
final class SavedChunk {
    static final int BLOCKS_ACROSS = 16;
    static final String AIR = "minecraft:air";
    static final String UNKNOWN = "minecraft:unknown";
    private static final int COLUMNS = BLOCKS_ACROSS * BLOCKS_ACROSS;
    private static final int BIOME_CELLS_ACROSS = 4;
    private static final int SMALLEST_BLOCK_INDEX_BITS = 4;
    private static final String SURFACE_HEIGHTMAP = "WORLD_SURFACE";
    private static final List<String> PROPERTY_KEYS = List.of("Properties", "properties");
    // Terrain and heightmaps are final from the features stage on; pregenerated chunks no player visited stop short of full.
    private static final Set<String> FINISHED_TERRAIN_STATUSES = Stream.of("features", "initialize_light", "light", "spawn",
                    "heightmaps", "full")
            .flatMap(status -> Stream.of(status, "minecraft:" + status))
            .collect(Collectors.toUnmodifiableSet());

    private final int minY;
    private final long[] surfaceHeights;
    private final int surfaceBits;
    private final Map<Integer, CompoundTag> sectionsByY;
    private final CustomBlocks customBlocks;

    private SavedChunk(int minY, long[] surfaceHeights, int surfaceBits, Map<Integer, CompoundTag> sectionsByY, CustomBlocks customBlocks) {
        this.minY = minY;
        this.surfaceHeights = surfaceHeights;
        this.surfaceBits = surfaceBits;
        this.sectionsByY = sectionsByY;
        this.customBlocks = customBlocks;
    }

    /** The chunk, or empty where its terrain is not finished yet or it has no surface heightmap. */
    static Optional<SavedChunk> of(CompoundTag root, CustomBlocks customBlocks) {
        if (!FINISHED_TERRAIN_STATUSES.contains(root.getString("Status"))) {
            return Optional.empty();
        }
        long[] surface = root.containsKey("Heightmaps") ? root.getCompoundTag("Heightmaps").getLongArray(SURFACE_HEIGHTMAP) : new long[0];
        if (surface.length == 0) {
            return Optional.empty();
        }
        Map<Integer, CompoundTag> sections = sectionsByY(root);
        int lowestSection = root.containsKey("yPos") ? root.getInt("yPos") : lowestKey(sections);
        int minY = lowestSection * BLOCKS_ACROSS;
        int worldHeight = (highestKey(sections) - lowestSection + 1) * BLOCKS_ACROSS;
        return Optional.of(new SavedChunk(minY, surface, heightmapBits(worldHeight, surface.length), sections, customBlocks));
    }

    int minY() {
        return minY;
    }

    /** The top block of the column, plants and trees included, or {@code minY - 1} where it holds none. */
    int surfaceY(int x, int z) {
        return minY + (int) PackedIndices.get(surfaceHeights, surfaceBits, z * BLOCKS_ACROSS + x) - 1;
    }

    /** The block's name, such as {@code minecraft:stone}, without its properties; a plugin's block is named as its plugin does. */
    String blockAt(int x, int y, int z) {
        CompoundTag section = sectionsByY.get(Math.floorDiv(y, BLOCKS_ACROSS));
        if (section == null || !section.containsKey("block_states")) {
            return AIR;
        }
        CompoundTag states = section.getCompoundTag("block_states");
        ListTag<?> palette = states.getListTag("palette");
        int index = (Math.floorMod(y, BLOCKS_ACROSS) * BLOCKS_ACROSS + z) * BLOCKS_ACROSS + x;
        return nameOf(paletteEntry(palette, states.getLongArray("data"), Math.max(SMALLEST_BLOCK_INDEX_BITS, bitsFor(palette.size())), index));
    }

    /** The biome's key, such as {@code minecraft:plains}; biomes are stored once per 4 blocks across. */
    String biomeAt(int x, int y, int z) {
        CompoundTag section = sectionsByY.get(Math.floorDiv(y, BLOCKS_ACROSS));
        if (section == null || !section.containsKey("biomes")) {
            return UNKNOWN;
        }
        CompoundTag biomes = section.getCompoundTag("biomes");
        ListTag<?> palette = biomes.getListTag("palette");
        int cellY = Math.floorMod(y, BLOCKS_ACROSS) / BIOME_CELLS_ACROSS;
        int index = (cellY * BIOME_CELLS_ACROSS + z / BIOME_CELLS_ACROSS) * BIOME_CELLS_ACROSS + x / BIOME_CELLS_ACROSS;
        Tag<?> biome = paletteEntry(palette, biomes.getLongArray("data"), bitsFor(palette.size()), index);
        return biome instanceof StringTag key ? key.getValue() : UNKNOWN;
    }

    private static Tag<?> paletteEntry(ListTag<?> palette, long[] data, int bits, int index) {
        if (palette == null || palette.size() == 0) {
            return null;
        }
        if (palette.size() == 1 || data.length == 0) {
            return palette.get(0);
        }
        int entry = (int) PackedIndices.get(data, bits, index);
        return entry < palette.size() ? palette.get(entry) : null;
    }

    // A block with properties is a compound naming it under Name; since 26.1 a block without them may be its bare name.
    private String nameOf(Tag<?> block) {
        String name = switch (block) {
            case StringTag text -> text.getValue();
            case CompoundTag compound when !compound.getString("Name").isEmpty() -> compound.getString("Name");
            case CompoundTag compound when !compound.getString("").isEmpty() -> compound.getString("");
            case null, default -> UNKNOWN;
        };
        return customBlocks.nameSavedAs(name, () -> propertiesOf(block));
    }

    private static Map<String, String> propertiesOf(Tag<?> block) {
        Map<String, String> properties = new HashMap<>();
        if (!(block instanceof CompoundTag compound)) {
            return properties;
        }
        for (String key : PROPERTY_KEYS) {
            if (compound.containsKey(key)) {
                compound.getCompoundTag(key).forEach((name, value) -> {
                    if (value instanceof StringTag text) {
                        properties.put(name, text.getValue());
                    }
                });
            }
        }
        return properties;
    }

    private static Map<Integer, CompoundTag> sectionsByY(CompoundTag root) {
        Map<Integer, CompoundTag> sections = new HashMap<>();
        ListTag<?> saved = root.getListTag("sections");
        if (saved == null) {
            return sections;
        }
        for (Tag<?> section : saved) {
            if (section instanceof CompoundTag compound && compound.containsKey("block_states")) {
                sections.put((int) compound.getByte("Y"), compound);
            }
        }
        return sections;
    }

    private static int lowestKey(Map<Integer, CompoundTag> sections) {
        return sections.keySet().stream().mapToInt(Integer::intValue).min().orElse(0);
    }

    private static int highestKey(Map<Integer, CompoundTag> sections) {
        return sections.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    // Heights run from 0 to the world's height; where the sections leave that unsure, the array's length decides.
    private static int heightmapBits(int worldHeight, int longs) {
        int fromHeight = bitsFor(worldHeight + 1);
        if (longsFor(fromHeight) == longs) {
            return fromHeight;
        }
        int perLong = (COLUMNS + longs - 1) / longs;
        return Long.SIZE / perLong;
    }

    private static int longsFor(int bits) {
        int perLong = Long.SIZE / bits;
        return (COLUMNS + perLong - 1) / perLong;
    }

    private static int bitsFor(int values) {
        return values <= 1 ? 0 : Integer.SIZE - Integer.numberOfLeadingZeros(values - 1);
    }

    /** Values packed into longs low bits first, none spanning two longs, as Minecraft packs them since 1.16. */
    static final class PackedIndices {
        private PackedIndices() {
        }

        static long get(long[] data, int bits, int index) {
            int perLong = Long.SIZE / bits;
            long word = data[index / perLong];
            int shift = (index % perLong) * bits;
            return (word >>> shift) & ((1L << bits) - 1);
        }
    }
}
