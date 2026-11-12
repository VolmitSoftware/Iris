package art.arcane.iris.core.studio.map;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.hunk.Hunk;
import org.junit.Test;

import java.util.function.UnaryOperator;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ColumnRunsTest {
    private static final int HEIGHT = 16;
    private static final int GROUND_Y = 3;
    private static final PlatformBlockState GRASS = block("minecraft:grass_block");
    private static final PlatformBlockState LOG = block("minecraft:oak_log");
    private static final PlatformBlockState LEAVES = block("minecraft:oak_leaves");

    @Test
    public void flatGroundIsOneRunPerArea() {
        ColumnRuns runs = ColumnRuns.of(groundUpTo(GROUND_Y), 1, (x, z) -> GROUND_Y, UnaryOperator.identity());

        assertEquals(64, runs.counts().length);
        assertEquals(1, runs.counts()[0]);
        assertEquals(GROUND_Y, runs.tops()[0]);
        assertEquals("minecraft:grass_block", runs.blocks()[0]);
    }

    @Test
    public void aTreeIsACanopyOnATrunkOnTheGroundNotAPillar() {
        Hunk<PlatformBlockState> chunk = groundUpTo(GROUND_Y);
        for (int y = GROUND_Y + 1; y <= GROUND_Y + 4; y++) {
            chunk.set(0, y, 0, LOG);
        }
        for (int z = 0; z < 2; z++) {
            for (int x = 0; x < 2; x++) {
                chunk.set(x, GROUND_Y + 5, z, LEAVES);
                chunk.set(x, GROUND_Y + 6, z, LEAVES);
            }
        }

        ColumnRuns runs = ColumnRuns.of(chunk, 1, (x, z) -> GROUND_Y, UnaryOperator.identity());

        assertEquals(3, runs.counts()[0]);
        assertArrayEquals(new short[]{GROUND_Y + 6, GROUND_Y + 4, GROUND_Y}, new short[]{runs.tops()[0], runs.tops()[1], runs.tops()[2]});
        assertArrayEquals(new short[]{GROUND_Y + 5, GROUND_Y + 1, 0}, new short[]{runs.bottoms()[0], runs.bottoms()[1], runs.bottoms()[2]});
        assertArrayEquals(new String[]{"minecraft:oak_leaves", "minecraft:oak_log", "minecraft:grass_block"}, new String[]{runs.blocks()[0], runs.blocks()[1], runs.blocks()[2]});
    }

    @Test
    public void aCanopyOverOpenGroundFloatsWithAirUnderIt() {
        Hunk<PlatformBlockState> chunk = groundUpTo(GROUND_Y);
        for (int z = 2; z < 4; z++) {
            for (int x = 2; x < 4; x++) {
                chunk.set(x, GROUND_Y + 5, z, LEAVES);
            }
        }

        ColumnRuns runs = ColumnRuns.of(chunk, 1, (x, z) -> GROUND_Y, UnaryOperator.identity());
        int area = 1 * 8 + 1;
        int first = runs.firstRunOf(area);

        assertEquals(2, runs.counts()[area]);
        assertEquals(GROUND_Y + 5, runs.bottoms()[first]);
        assertEquals(GROUND_Y, runs.tops()[first + 1]);
    }

    private static Hunk<PlatformBlockState> groundUpTo(int topY) {
        Hunk<PlatformBlockState> chunk = Hunk.newArrayHunk(16, HEIGHT, 16);
        for (int y = 0; y <= topY; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    chunk.set(x, y, z, GRASS);
                }
            }
        }
        return chunk;
    }

    private static PlatformBlockState block(String key) {
        PlatformBlockState block = mock(PlatformBlockState.class);
        when(block.key()).thenReturn(key);
        when(block.isSolid()).thenReturn(true);
        return block;
    }
}
