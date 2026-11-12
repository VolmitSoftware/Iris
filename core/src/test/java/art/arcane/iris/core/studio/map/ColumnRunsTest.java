package art.arcane.iris.core.studio.map;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.hunk.Hunk;
import it.unimi.dsi.fastutil.ints.Int2ByteMaps;
import org.junit.Test;

import java.util.function.UnaryOperator;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ColumnRunsTest {
    private static final int HEIGHT = 16;
    private static final int GROUND_Y = 3;
    private static final int NO_FLUID = -1;
    private static final PlatformBlockState GRASS = block("minecraft:grass_block", true);
    private static final PlatformBlockState LOG = block("minecraft:oak_log", true);
    private static final PlatformBlockState LEAVES = block("minecraft:oak_leaves", false);
    private static final PlatformBlockState GRASS_PLANT = plant("minecraft:short_grass");

    @Test
    public void flatGroundIsOneRunPerArea() {
        ColumnRuns runs = runsOf(groundUpTo(GROUND_Y));

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

        ColumnRuns runs = runsOf(chunk);

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

        ColumnRuns runs = runsOf(chunk);
        int area = 1 * 8 + 1;
        int first = runs.firstRunOf(area);

        assertEquals(2, runs.counts()[area]);
        assertEquals(GROUND_Y + 5, runs.bottoms()[first]);
        assertEquals(GROUND_Y, runs.tops()[first + 1]);
    }

    @Test
    public void aRunKnowsThePlantsStandingOnItsTop() {
        Hunk<PlatformBlockState> chunk = groundUpTo(GROUND_Y);
        chunk.set(0, GROUND_Y + 1, 0, GRASS_PLANT);

        ColumnRuns runs = runsOf(chunk);

        assertEquals("minecraft:short_grass", runs.covers()[0]);
        assertEquals(ColumnRuns.WHOLE_SHARE / 4, runs.coverShares()[0] & 0xFF);
        assertEquals(null, runs.covers()[1]);
    }

    @Test
    public void openGroundIsLitFullyAndGroundUnderACanopyIsDarker() {
        Hunk<PlatformBlockState> chunk = groundUpTo(GROUND_Y);
        for (int z = 0; z < 8; z++) {
            for (int x = 0; x < 8; x++) {
                chunk.set(x, GROUND_Y + 3, z, LEAVES);
                chunk.set(x, GROUND_Y + 4, z, LEAVES);
            }
        }

        ColumnRuns runs = runsOf(chunk);
        int shadedArea = 1 * 8 + 1;
        int openArea = 7 * 8 + 7;
        int ground = runs.firstRunOf(shadedArea) + runs.counts()[shadedArea] - 1;

        assertEquals(ChunkCells.FULL_LIGHT * ColumnRuns.LIGHT_STEPS, runs.lights()[runs.firstRunOf(openArea)] & 0xFF);
        assertEquals(0, runs.occlusions()[runs.firstRunOf(openArea)]);
        assertTrue((runs.lights()[ground] & 0xFF) < ChunkCells.FULL_LIGHT * ColumnRuns.LIGHT_STEPS);
    }

    @Test
    public void groundAtTheFootOfAWallHasItsCornersDarkened() {
        Hunk<PlatformBlockState> chunk = groundUpTo(GROUND_Y);
        for (int z = 0; z < 16; z++) {
            chunk.set(4, GROUND_Y + 1, z, LOG);
        }

        ColumnRuns runs = runsOf(chunk);
        int besideWall = 4 * 8 + 1;

        assertTrue(runs.occlusions()[runs.firstRunOf(besideWall) + runs.counts()[besideWall] - 1] > 0);
    }

    private static ColumnRuns runsOf(Hunk<PlatformBlockState> chunk) {
        ChunkCells cells = ChunkCells.of(chunk, (x, z) -> GROUND_Y, NO_FLUID, UnaryOperator.identity(), Int2ByteMaps.EMPTY_MAP);
        return ColumnRuns.of(chunk, 1, (x, z) -> GROUND_Y, UnaryOperator.identity(), cells);
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

    private static PlatformBlockState block(String key, boolean occluding) {
        PlatformBlockState block = mock(PlatformBlockState.class);
        when(block.key()).thenReturn(key);
        when(block.isSolid()).thenReturn(true);
        when(block.isOccluding()).thenReturn(occluding);
        return block;
    }

    private static PlatformBlockState plant(String key) {
        PlatformBlockState block = mock(PlatformBlockState.class);
        when(block.key()).thenReturn(key);
        return block;
    }
}
