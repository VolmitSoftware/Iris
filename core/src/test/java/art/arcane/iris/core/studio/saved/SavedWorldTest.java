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

import art.arcane.iris.core.link.CustomBlockLook;
import art.arcane.iris.core.link.Identifier;
import art.arcane.volmlib.util.nbt.io.NBTSerializer;
import art.arcane.volmlib.util.nbt.io.NamedTag;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import art.arcane.volmlib.util.nbt.tag.ListTag;
import art.arcane.volmlib.util.nbt.tag.StringTag;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.IntUnaryOperator;
import java.util.zip.DeflaterOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SavedWorldTest {
    private static final int MIN_SECTION = -4;
    private static final int SECTIONS = 8;
    private static final int MIN_Y = MIN_SECTION * 16;
    private static final List<String> PALETTE = List.of("minecraft:air", "minecraft:stone", "minecraft:oak_log",
            "minecraft:oak_leaves", "minecraft:water", "minecraft:snow", "minecraft:tripwire", "minecraft:tripwire");
    private static final int AIR = 0;
    private static final int ATTACHED_TRIPWIRE = 6;
    private static final int DETACHED_TRIPWIRE = 7;
    private static final int STONE = 1;
    private static final int LOG = 2;
    private static final int LEAVES = 3;
    private static final int WATER = 4;
    private static final int SNOW = 5;
    private static final int PLAIN_GROUND_TOP = 5;
    private static final int TREE_GROUND_TOP = 10;
    private static final int TREE_TOP = 15;
    private static final int SNOWY_TREE_TOP = TREE_TOP + 1;
    private static final int POND_FLOOR = 0;
    private static final int POND_SURFACE = 5;
    private static final SavedGround STONE_IS_GROUND = block -> block.equals("minecraft:stone") || block.equals("minecraft:snow");
    private static final SavedGround ETOPIA_GRASS_IS_GROUND = block -> STONE_IS_GROUND.isGround(block) || block.equals("etopia:grass_block");
    private static final CustomBlocks ETOPIA_GRASS = new CustomBlocks(List.of(
            new CustomBlockLook(new Identifier("etopia", "grass_block"), "minecraft:tripwire[attached=true]")));

    @Rule
    public TemporaryFolder folders = new TemporaryFolder();

    @Test
    public void groundIsTheTopWholeBlockUnderTreesAndWater() throws IOException {
        SavedWorld world = new SavedWorld(regionFolderWithOneChunk(), STONE_IS_GROUND, CustomBlocks.NONE);

        SavedRegion.SavedColumn underTree = world.columnAt(0, 0);
        assertEquals(TREE_GROUND_TOP, underTree.groundY());
        assertEquals(SavedRegion.NO_WATER, underTree.waterSurfaceY());
        assertEquals(new SavedBlock("minecraft:stone", "minecraft:stone"), underTree.block());
        assertEquals("test:meadow", underTree.biome());

        SavedRegion.SavedColumn pond = world.columnAt(1, 0);
        assertEquals(POND_FLOOR, pond.groundY());
        assertEquals(POND_SURFACE, pond.waterSurfaceY());

        assertEquals(PLAIN_GROUND_TOP, world.columnAt(7, 9).groundY());
    }

    @Test
    public void snowCountsAsGroundOnTheGroundButNotOnATree() throws IOException {
        SavedWorld world = new SavedWorld(regionFolderWithOneChunk(), STONE_IS_GROUND, CustomBlocks.NONE);

        assertEquals(TREE_GROUND_TOP, world.columnAt(0, 0).groundY());
        SavedRegion.SavedColumn snowyGround = world.columnAt(2, 0);
        assertEquals(PLAIN_GROUND_TOP + 1, snowyGround.groundY());
        assertEquals("minecraft:snow", snowyGround.block().name());
    }

    @Test
    public void aPluginBlockIsNamedAsItsPluginNamesItWhereItsBorrowedStateIsSaved() throws IOException {
        SavedWorld world = new SavedWorld(regionFolderWithOneChunk(), ETOPIA_GRASS_IS_GROUND, ETOPIA_GRASS);

        SavedRegion.SavedColumn plugin = world.columnAt(3, 0);
        assertEquals(PLAIN_GROUND_TOP + 1, plugin.groundY());
        assertEquals(new SavedBlock("etopia:grass_block", "minecraft:tripwire[attached=true]"), plugin.block());

        SavedRegion.SavedColumn otherState = world.columnAt(5, 0);
        assertEquals(PLAIN_GROUND_TOP, otherState.groundY());
        assertEquals("minecraft:stone", otherState.block().name());
    }

    @Test
    public void withoutPluginBlocksTheBorrowedStateIsOnlyItsVanillaBlock() throws IOException {
        SavedWorld world = new SavedWorld(regionFolderWithOneChunk(), ETOPIA_GRASS_IS_GROUND, CustomBlocks.NONE);

        SavedRegion.SavedColumn borrowed = world.columnAt(3, 0);
        assertEquals(PLAIN_GROUND_TOP, borrowed.groundY());
        assertEquals("minecraft:stone", borrowed.block().name());
    }

    @Test
    public void chunksWithUnfinishedTerrainOrNoFileHaveNoGround() throws IOException {
        SavedWorld world = new SavedWorld(regionFolderWithOneChunk(), STONE_IS_GROUND, CustomBlocks.NONE);

        assertNull(world.columnAt(16, 0));
        assertNull(world.columnAt(-1, -1));
    }

    @Test
    public void theViewStartsInTheMiddleOfTheRegionNearestTheOrigin() throws IOException {
        SavedWorld.Start start = new SavedWorld(regionFolderWithOneChunk(), STONE_IS_GROUND, CustomBlocks.NONE).start().orElseThrow();

        assertEquals(256, start.centerX());
        assertEquals(256, start.centerZ());
        assertEquals(MIN_Y, start.minY());
        assertEquals(POND_FLOOR, start.lowestGround());
        assertEquals(TREE_GROUND_TOP, start.highestGround());
        assertEquals(Integer.valueOf(POND_SURFACE), start.seaLevel());
    }

    @Test
    public void aTileSamplesOneColumnEveryFewBlocksAndNamesEachBlockOnce() throws IOException {
        SavedWorld world = new SavedWorld(regionFolderWithOneChunk(), STONE_IS_GROUND, CustomBlocks.NONE);

        SavedTile tile = SavedTile.sample(world, 0, 0, 2, 16);

        assertEquals(TREE_GROUND_TOP, tile.groundY()[0]);
        assertEquals(PLAIN_GROUND_TOP + 1, tile.groundY()[1]);
        assertEquals(PLAIN_GROUND_TOP, tile.groundY()[2]);
        assertEquals(SavedTile.NO_GROUND, tile.groundY()[8]);
        assertEquals(List.of("minecraft:stone", "minecraft:snow"), tile.savedBlocks().stream().map(SavedBlock::name).toList());
        assertEquals(List.of("test:meadow"), tile.biomeNames());
    }

    @Test
    public void savedWorldsAreTheLevelAndItsDimensionFoldersAndNoOthers() throws IOException {
        Path level = folders.newFolder("level").toPath();
        writeRegion(level.resolve("region"));
        writeRegion(level.resolve("dimensions/iris/demo/region"));
        Files.createDirectories(level.resolve("dimensions/iris/empty/region"));
        SavedWorlds worlds = new SavedWorlds(level, STONE_IS_GROUND, CustomBlocks.NONE);

        assertEquals(List.of(new SavedWorlds.Info(".", "overworld", "minecraft"),
                new SavedWorlds.Info("dimensions/iris/demo", "demo", "iris")), worlds.list());
        assertEquals(TREE_GROUND_TOP, worlds.open("dimensions/iris/demo").columnAt(0, 0).groundY());
        assertThrows(NoSuchElementException.class, () -> worlds.open("../outside"));
    }

    @Test
    public void heightmapIndicesArePackedWithoutSpanningLongs() {
        long[] packed = {(5L) | (7L << 9) | (300L << 18), 9L};

        assertEquals(5L, SavedChunk.PackedIndices.get(packed, 9, 0));
        assertEquals(7L, SavedChunk.PackedIndices.get(packed, 9, 1));
        assertEquals(300L, SavedChunk.PackedIndices.get(packed, 9, 2));
        assertEquals(9L, SavedChunk.PackedIndices.get(packed, 9, 7));
    }

    private Path regionFolderWithOneChunk() throws IOException {
        Path folder = folders.newFolder().toPath();
        writeRegion(folder);
        assertTrue(SavedWorld.holdsRegionFiles(folder));
        return folder;
    }

    private static void writeRegion(Path folder) throws IOException {
        Files.createDirectories(folder);
        byte[] finished = compressed(chunk("minecraft:full"));
        byte[] unfinished = compressed(chunk("minecraft:noise"));
        try (RandomAccessFile region = new RandomAccessFile(folder.resolve("r.0.0.mca").toFile(), "rw")) {
            int finishedSectors = sectorsFor(finished);
            region.seek(0);
            region.writeInt((2 << 8) | finishedSectors);
            region.writeInt(((2 + finishedSectors) << 8) | sectorsFor(unfinished));
            writeChunkAt(region, 2, finished);
            writeChunkAt(region, 2 + finishedSectors, unfinished);
        }
    }

    private static void writeChunkAt(RandomAccessFile region, int sector, byte[] body) throws IOException {
        region.seek(sector * 4_096L);
        region.writeInt(body.length + 1);
        region.writeByte(2);
        region.write(body);
        region.write(new byte[sectorsFor(body) * 4_096 - body.length - 5]);
    }

    private static int sectorsFor(byte[] body) {
        return (body.length + 5 + 4_095) / 4_096;
    }

    private static byte[] compressed(CompoundTag chunk) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DeflaterOutputStream zlib = new DeflaterOutputStream(bytes)) {
            new NBTSerializer(false).toStream(new NamedTag("", chunk), zlib);
        }
        return bytes.toByteArray();
    }

    // Column (0, 0) is a log on stone with snowy leaves on top, (1, 0) a pond on stone, (2, 0) snow on stone, (3, 0) an
    // attached and (5, 0) a detached tripwire on stone, every other column stone to y 5.
    private static CompoundTag chunk(String status) {
        CompoundTag root = new CompoundTag();
        root.putString("Status", status);
        root.putInt("yPos", MIN_SECTION);
        ListTag<CompoundTag> sections = new ListTag<>(CompoundTag.class);
        for (int section = MIN_SECTION; section < MIN_SECTION + SECTIONS; section++) {
            sections.add(section(section));
        }
        root.put("sections", sections);
        CompoundTag heightmaps = new CompoundTag();
        heightmaps.putLongArray("WORLD_SURFACE", packed(8, 256, column -> topOf(column % 16, column / 16) + 1 - MIN_Y));
        root.put("Heightmaps", heightmaps);
        return root;
    }

    private static int topOf(int x, int z) {
        if (x == 0 && z == 0) {
            return SNOWY_TREE_TOP;
        }
        if ((x == 2 || x == 3 || x == 5) && z == 0) {
            return PLAIN_GROUND_TOP + 1;
        }
        return x == 1 && z == 0 ? POND_SURFACE : PLAIN_GROUND_TOP;
    }

    private static int blockAt(int x, int y, int z) {
        if (x == 0 && z == 0) {
            return y <= TREE_GROUND_TOP ? STONE : y < TREE_TOP ? LOG : y == TREE_TOP ? LEAVES : y == SNOWY_TREE_TOP ? SNOW : AIR;
        }
        if (x == 1 && z == 0) {
            return y <= POND_FLOOR ? STONE : y <= POND_SURFACE ? WATER : AIR;
        }
        if (x == 2 && z == 0) {
            return y <= PLAIN_GROUND_TOP ? STONE : y == PLAIN_GROUND_TOP + 1 ? SNOW : AIR;
        }
        if ((x == 3 || x == 5) && z == 0) {
            return y <= PLAIN_GROUND_TOP ? STONE : y == PLAIN_GROUND_TOP + 1 ? (x == 3 ? ATTACHED_TRIPWIRE : DETACHED_TRIPWIRE) : AIR;
        }
        return y <= PLAIN_GROUND_TOP ? STONE : AIR;
    }

    private static CompoundTag section(int sectionY) {
        CompoundTag section = new CompoundTag();
        section.putByte("Y", (byte) sectionY);
        CompoundTag states = new CompoundTag();
        states.put("palette", blockPalette());
        states.putLongArray("data", packed(4, 4_096, index -> blockAt(index % 16, sectionY * 16 + index / 256, (index / 16) % 16)));
        section.put("block_states", states);
        CompoundTag biomes = new CompoundTag();
        ListTag<StringTag> biomePalette = new ListTag<>(StringTag.class);
        biomePalette.addString("test:meadow");
        biomes.put("palette", biomePalette);
        section.put("biomes", biomes);
        return section;
    }

    // Water is written the way 26.1 writes a block without properties among others: its bare name under an empty key.
    private static ListTag<CompoundTag> blockPalette() {
        ListTag<CompoundTag> palette = new ListTag<>(CompoundTag.class);
        for (int index = 0; index < PALETTE.size(); index++) {
            String block = PALETTE.get(index);
            CompoundTag entry = new CompoundTag();
            entry.putString(block.equals("minecraft:water") ? "" : "Name", block);
            if (index == ATTACHED_TRIPWIRE || index == DETACHED_TRIPWIRE) {
                CompoundTag properties = new CompoundTag();
                properties.putString("attached", index == ATTACHED_TRIPWIRE ? "true" : "false");
                entry.put("Properties", properties);
            }
            palette.add(entry);
        }
        return palette;
    }

    private static long[] packed(int bits, int count, IntUnaryOperator valueAt) {
        int perLong = 64 / bits;
        long[] data = new long[(count + perLong - 1) / perLong];
        for (int index = 0; index < count; index++) {
            data[index / perLong] |= ((long) valueAt.applyAsInt(index)) << ((index % perLong) * bits);
        }
        return data;
    }
}
