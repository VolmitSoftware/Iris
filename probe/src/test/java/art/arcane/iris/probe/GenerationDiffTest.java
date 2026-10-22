package art.arcane.iris.probe;

import art.arcane.volmlib.nativelib.terrain.NativeBiome;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.hunk.Hunk;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class GenerationDiffTest {
    private static final int HEIGHT = 8;
    private static final int MIN_Y = -4;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void roundTripsWidePalettesAndChunkCoordinates() throws IOException {
        String[] palette = new String[300];
        for (int index = 0; index < palette.length; index++) {
            palette[index] = "minecraft:block_" + index;
        }
        char[] blocks = new char[256 * HEIGHT];
        for (int index = 0; index < blocks.length; index++) {
            blocks[index] = (char) (index % palette.length);
        }
        ChunkDump.ChunkSnapshot wide = new ChunkDump.ChunkSnapshot(-3, 7, HEIGHT, palette, blocks,
                new String[]{"minecraft:plains"}, new char[256 * HEIGHT]);
        Path file = write("wide.bin", List.of(wide));

        try (ChunkDump.Reader reader = new ChunkDump.Reader(file)) {
            assertEquals(new ChunkDump.Header("overworld", 1337L, MIN_Y, HEIGHT), reader.header());
            ChunkDump.ChunkSnapshot read = reader.next();
            assertEquals(-3, read.chunkX());
            assertEquals(7, read.chunkZ());
            assertArrayEquals(palette, read.blockPalette());
            assertArrayEquals(blocks, read.blocks());
            assertArrayEquals(new String[]{"minecraft:plains"}, read.biomePalette());
            assertNull(reader.next());
        }
    }

    @Test
    public void capturesUnsetHunkEntriesAsAirAndNullBiome() {
        Hunk<NativeBlockState> blocks = Hunk.newArrayHunk(16, HEIGHT, 16);
        Hunk<NativeBiome> biomes = Hunk.newArrayHunk(16, HEIGHT, 16);

        ChunkDump.ChunkSnapshot snapshot = ChunkDump.capture(1, 2, blocks, biomes, HEIGHT);

        assertArrayEquals(new String[]{ChunkDump.NULL_BLOCK_KEY}, snapshot.blockPalette());
        assertArrayEquals(new String[]{ChunkDump.NULL_BIOME_KEY}, snapshot.biomePalette());
        assertEquals(256 * HEIGHT, snapshot.blocks().length);
    }

    @Test
    public void reportsFullIdentityIndependentOfChunkOrderAndNamespaceSpelling() throws IOException {
        Path baseline = write("a.bin", List.of(terrain(0, 0, 3, "minecraft:air"), terrain(1, 0, 5, "minecraft:air")));
        Path candidate = write("b.bin", List.of(terrain(1, 0, 5, "air"), terrain(0, 0, 3, "minecraft:air")));

        GenerationDiff.Totals totals = GenerationDiff.compare(baseline, candidate);

        assertEquals(2L, totals.commonChunks);
        assertEquals(0L, totals.onlyBaseline);
        assertEquals(0L, totals.onlyCandidate);
        assertEquals(totals.positions, totals.identical);
        assertEquals(totals.nonAirPositions, totals.nonAirIdentical);
        assertEquals(totals.columns, totals.surfaceIdentical);
        assertEquals(totals.biomePositions, totals.biomeIdentical);
    }

    @Test
    public void treatsBothAirSpellingsInOnePaletteAsTheSameBlock() throws IOException {
        char[] blocks = new char[256 * HEIGHT];
        Arrays.fill(blocks, 0, HEIGHT, (char) 1);
        ChunkDump.ChunkSnapshot mixed = new ChunkDump.ChunkSnapshot(0, 0, HEIGHT,
                new String[]{"minecraft:air", "air"}, blocks, new String[]{"minecraft:plains"}, new char[256 * HEIGHT]);
        ChunkDump.ChunkSnapshot reordered = new ChunkDump.ChunkSnapshot(0, 0, HEIGHT,
                new String[]{"air", "minecraft:air"}, blocks.clone(), new String[]{"plains"}, new char[256 * HEIGHT]);

        GenerationDiff.Totals totals = GenerationDiff.compare(write("a.bin", List.of(mixed)),
                write("b.bin", List.of(reordered)));

        assertEquals(totals.positions, totals.identical);
        assertEquals(totals.biomePositions, totals.biomeIdentical);
    }

    @Test
    public void attributesSurfaceAndCategoryMismatchesAndUnmatchedChunks() throws IOException {
        Path baseline = write("a.bin", List.of(terrain(0, 0, 5, "minecraft:air"), terrain(4, 4, 5, "minecraft:air")));
        Path candidate = write("b.bin", List.of(terrain(0, 0, 3, "minecraft:air"), terrain(9, 9, 5, "minecraft:air")));

        GenerationDiff.Totals totals = GenerationDiff.compare(baseline, candidate);

        assertEquals(1L, totals.commonChunks);
        assertEquals(1L, totals.onlyBaseline);
        assertEquals(1L, totals.onlyCandidate);
        assertEquals(256L * 2L, totals.matrix[GenerationDiff.Category.TERRAIN.ordinal()][GenerationDiff.Category.AIR.ordinal()]);
        assertEquals(0L, totals.surfaceIdentical);
        assertEquals(256L * 2L, totals.surfaceDeltaSum);
        assertEquals(2, totals.surfaceMaxDelta);
        assertEquals(256L * 5L, totals.nonAirPositions);
        assertEquals(256L * 3L, totals.nonAirIdentical);
    }

    @Test
    public void classifiesBlockFamilies() {
        assertEquals(GenerationDiff.Category.AIR, GenerationDiff.classify("cave_air"));
        assertEquals(GenerationDiff.Category.AIR, GenerationDiff.classify("minecraft:void_air"));
        assertEquals(GenerationDiff.Category.FLUID, GenerationDiff.classify("minecraft:water[level=0]"));
        assertEquals(GenerationDiff.Category.ORE, GenerationDiff.classify("minecraft:deepslate_diamond_ore"));
        assertEquals(GenerationDiff.Category.DECOR, GenerationDiff.classify("minecraft:oak_leaves[distance=1,persistent=false]"));
        assertEquals(GenerationDiff.Category.DECOR, GenerationDiff.classify("minecraft:snow[layers=1]"));
        assertEquals(GenerationDiff.Category.TERRAIN, GenerationDiff.classify("minecraft:snow_block"));
        assertEquals(GenerationDiff.Category.TERRAIN, GenerationDiff.classify("minecraft:grass_block[snowy=false]"));
        assertEquals("minecraft:air", GenerationDiff.canonicalKey("air"));
        assertEquals("custom:thing[a=b:c]", GenerationDiff.canonicalKey("custom:thing[a=b:c]"));
    }

    private ChunkDump.ChunkSnapshot terrain(int chunkX, int chunkZ, int solidHeight, String airKey) {
        char[] blocks = new char[256 * HEIGHT];
        for (int column = 0; column < 256; column++) {
            Arrays.fill(blocks, column * HEIGHT, column * HEIGHT + solidHeight, (char) 1);
        }
        return new ChunkDump.ChunkSnapshot(chunkX, chunkZ, HEIGHT, new String[]{airKey, "minecraft:stone"}, blocks,
                new String[]{"minecraft:plains"}, new char[256 * HEIGHT]);
    }

    private Path write(String name, List<ChunkDump.ChunkSnapshot> snapshots) throws IOException {
        Path file = folder.getRoot().toPath().resolve(name);
        try (ChunkDump.Writer writer = new ChunkDump.Writer(file, new ChunkDump.Header("overworld", 1337L, MIN_Y, HEIGHT))) {
            for (ChunkDump.ChunkSnapshot snapshot : snapshots) {
                writer.write(snapshot);
            }
        }
        return file;
    }
}
