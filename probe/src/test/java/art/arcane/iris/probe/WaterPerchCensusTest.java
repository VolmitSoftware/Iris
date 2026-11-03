package art.arcane.iris.probe;

import art.arcane.volmlib.util.nbt.io.NBTUtil;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import art.arcane.volmlib.util.nbt.tag.ListTag;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.DeflaterOutputStream;

import static org.junit.Assert.assertEquals;

public class WaterPerchCensusTest {
    private static final int GROUND = 3;
    private static final int STONE = 1;
    private static final int WATER = 2;
    private static final int FLOWING = 3;
    private static final int LAKE_FLOOR = 0;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void countsPerchedSourcesInsideAndOutsideBlends() throws IOException {
        int[][][] blocks = column(heights(GROUND));
        blocks[GROUND + 1][4][4] = WATER;
        blocks[GROUND + 1][4][12] = WATER;
        blocks[GROUND + 1][12][12] = FLOWING;
        Path regions = folder.newFolder("region").toPath();
        writeRegion(regions, 0, 0, section(blocks), startAt(0, 0, 0, 8));

        WaterPerchCensus.Result result = WaterPerchCensus.scan(regions, 0);

        assertEquals(3L, result.waterColumns());
        assertEquals("the source within the piece", 1L, result.insideSources());
        assertEquals("the source away from the piece", 1L, result.outsideSources());
        assertEquals(1L, result.outsideFlowing());
    }

    @Test
    public void waterHeldByEqualOrTallerBanksIsNotPerched() throws IOException {
        int[][][] blocks = column(heights(GROUND));
        for (int z = 3; z <= 5; z++) {
            for (int x = 3; x <= 5; x++) {
                blocks[GROUND + 1][z][x] = STONE;
            }
        }
        blocks[GROUND + 1][4][4] = WATER;
        Path regions = folder.newFolder("region").toPath();
        writeRegion(regions, 0, 0, section(blocks), null);

        WaterPerchCensus.Result result = WaterPerchCensus.scan(regions, 24);

        assertEquals(1L, result.waterColumns());
        assertEquals(0L, result.insideSources());
        assertEquals(0L, result.outsideSources());
    }

    @Test
    public void waterWithoutASolidBedIsNotCounted() throws IOException {
        int[][][] blocks = column(heights(LAKE_FLOOR));
        blocks[GROUND][4][4] = WATER;
        Path regions = folder.newFolder("region").toPath();
        writeRegion(regions, 0, 0, section(blocks), null);

        assertEquals(0L, WaterPerchCensus.scan(regions, 24).outsideSources());
    }

    private static int[][] heights(int height) {
        int[][] heights = new int[16][16];
        for (int[] row : heights) {
            Arrays.fill(row, height);
        }
        return heights;
    }

    private static int[][][] column(int[][] heights) {
        int[][][] blocks = new int[16][16][16];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y <= heights[z][x] && y < 16; y++) {
                    blocks[y][z][x] = STONE;
                }
            }
        }
        return blocks;
    }

    private static CompoundTag section(int[][][] blocks) {
        ListTag<CompoundTag> palette = new ListTag<>(CompoundTag.class);
        palette.add(state("minecraft:air", null));
        palette.add(state("minecraft:stone", null));
        palette.add(state("minecraft:water", "0"));
        palette.add(state("minecraft:water", "1"));
        long[] data = new long[256];
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int index = (y << 8) | (z << 4) | x;
                    data[index / 16] |= (long) blocks[y][z][x] << ((index % 16) * 4);
                }
            }
        }
        CompoundTag states = new CompoundTag();
        states.put("palette", palette);
        states.putLongArray("data", data);
        CompoundTag section = new CompoundTag();
        section.putByte("Y", (byte) 0);
        section.put("block_states", states);
        return section;
    }

    private static CompoundTag state(String name, String level) {
        CompoundTag state = new CompoundTag();
        state.putString("Name", name);
        if (level != null) {
            CompoundTag properties = new CompoundTag();
            properties.putString("level", level);
            state.put("Properties", properties);
        }
        return state;
    }

    private static CompoundTag startAt(int minX, int minZ, int maxX, int maxZ) {
        CompoundTag piece = new CompoundTag();
        piece.putIntArray("BB", new int[]{minX, 0, minZ, 8, 15, maxZ});
        ListTag<CompoundTag> children = new ListTag<>(CompoundTag.class);
        children.add(piece);
        CompoundTag start = new CompoundTag();
        start.put("Children", children);
        return start;
    }

    private static void writeRegion(Path folder, int chunkX, int chunkZ, CompoundTag section, CompoundTag start) throws IOException {
        CompoundTag root = new CompoundTag();
        root.putString("Status", "minecraft:full");
        ListTag<CompoundTag> sections = new ListTag<>(CompoundTag.class);
        sections.add(section);
        root.put("sections", sections);
        if (start != null) {
            CompoundTag starts = new CompoundTag();
            starts.put("minecraft:village_plains", start);
            CompoundTag structures = new CompoundTag();
            structures.put("starts", starts);
            root.put("structures", structures);
        }
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed)) {
            NBTUtil.write(root, deflater, false);
        }
        byte[] payload = compressed.toByteArray();
        ByteBuffer region = ByteBuffer.allocate(4096 * (3 + payload.length / 4096));
        region.putInt((chunkZ << 5 | chunkX) * 4, 2 << 8 | (1 + payload.length / 4096));
        region.putInt(4096 * 2, payload.length + 1);
        region.put(4096 * 2 + 4, (byte) 2);
        region.put(4096 * 2 + 5, payload);
        Files.write(folder.resolve("r.0.0.mca"), region.array());
    }
}
