package art.arcane.iris.probe;

import art.arcane.iris.generation.chunk.SectionBuffer;
import art.arcane.volmlib.nativelib.terrain.NativeBiome;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.hunk.Hunk;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class ProductionHunkTest {
    private static final int HEIGHT = 24;

    @Test
    public void blockHunkIgnoresNullWritesAndTracksTheHighestStoredCellPerColumn() {
        SectionBuffer blocks = new SectionBuffer(HEIGHT);
        NativeBlockState stone = StubPlatform.blockStateForTest("minecraft:stone");
        NativeBlockState dirt = StubPlatform.blockStateForTest("minecraft:dirt");

        blocks.setRaw(3, 5, 7, stone);
        blocks.setRaw(3, 9, 7, dirt);
        blocks.setRaw(3, 2, 7, stone);
        blocks.setRaw(3, 9, 7, null);

        assertSame(dirt, blocks.getStoredRaw(3, 9, 7));
        assertSame(stone, blocks.getStoredRaw(3, 5, 7));
        assertNull(blocks.getStoredRaw(3, 6, 7));
        assertEquals(9, blocks.highestStoredY(3, 7));
        assertEquals(-1, blocks.highestStoredY(7, 3));
    }

    @Test
    public void dumpCaptureOfTheBlockHunkMatchesAnArrayHunkHoldingTheSameWrites() {
        SectionBuffer production = new SectionBuffer(HEIGHT);
        Hunk<NativeBlockState> array = Hunk.newArrayHunk(16, HEIGHT, 16);
        Hunk<NativeBiome> biomes = Hunk.newArrayHunk(16, HEIGHT, 16);
        NativeBlockState stone = StubPlatform.blockStateForTest("minecraft:stone");
        NativeBlockState water = StubPlatform.blockStateForTest("minecraft:water");
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int top = (x * 7 + z * 3) % HEIGHT;
                for (int y = 0; y <= top; y++) {
                    NativeBlockState state = y == top ? water : stone;
                    production.setRaw(x, y, z, state);
                    array.setRaw(x, y, z, state);
                }
            }
        }

        ChunkDump.ChunkSnapshot fromProduction = ChunkDump.capture(4, -9, production, biomes, HEIGHT);
        ChunkDump.ChunkSnapshot fromArray = ChunkDump.capture(4, -9, array, biomes, HEIGHT);

        assertArrayEquals(fromArray.blockPalette(), fromProduction.blockPalette());
        assertArrayEquals(fromArray.blocks(), fromProduction.blocks());
        assertTrue(List.of(fromProduction.blockPalette()).contains(ChunkDump.NULL_BLOCK_KEY));
    }

    @Test
    public void biomeHunkKeepsWholeColumnWritesPerColumnAndReadsLikeAnArrayHunk() {
        ProductionBiomeHunk production = new ProductionBiomeHunk(HEIGHT, () -> biome("minecraft:plains"));
        Hunk<NativeBiome> array = Hunk.newArrayHunk(16, HEIGHT, 16);
        NativeBiome forest = biome("minecraft:forest");
        NativeBiome desert = biome("minecraft:desert");
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                NativeBiome column = ((x + z) & 1) == 0 ? forest : desert;
                production.set(x, 0, z, x, HEIGHT - 1, z, column);
                array.set(x, 0, z, x, HEIGHT - 1, z, column);
            }
        }

        assertFalse(production.expanded());
        assertSameCells(array, production);

        production.setRaw(2, 4, 9, desert);
        array.setRaw(2, 4, 9, desert);
        production.set(5, 0, 5, 5, HEIGHT - 1, 5, desert);
        array.set(5, 0, 5, 5, HEIGHT - 1, 5, desert);

        assertTrue(production.expanded());
        assertSameCells(array, production);
    }

    @Test
    public void biomeHunkReadsTheFallbackOnlyForUnwrittenCells() {
        NativeBiome plains = biome("minecraft:plains");
        ProductionBiomeHunk production = new ProductionBiomeHunk(HEIGHT, () -> plains);
        NativeBiome forest = biome("minecraft:forest");
        production.set(1, 0, 1, 1, HEIGHT - 1, 1, forest);

        assertSame(forest, production.getRaw(1, HEIGHT - 1, 1));
        assertSame(plains, production.getRaw(0, 0, 0));
    }

    private static void assertSameCells(Hunk<NativeBiome> expected, Hunk<NativeBiome> actual) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < HEIGHT; y++) {
                    assertSame(expected.get(x, y, z), actual.get(x, y, z));
                }
            }
        }
    }

    private static NativeBiome biome(String key) {
        return new TestBiome(key);
    }

    private record TestBiome(String key) implements NativeBiome {
        @Override
        public String namespace() {
            return key.substring(0, key.indexOf(':'));
        }

        @Override
        public Object nativeHandle() {
            return key;
        }
    }
}
