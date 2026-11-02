package art.arcane.iris.generation.hydrology.grid;

import org.junit.Test;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BasinExtractorTest {
    private static final float FLOOR = 10f;
    private static final float RIM = 30f;
    private static final float RIM_MIN = 24f;
    private static final float SEA = 40f;

    @Test
    public void bowlYieldsOneBasinWithSpillAndDepth() {
        DemGrid grid = bowl();
        float rimMin = RIM_MIN;
        float floor = FLOOR;
        float[] filled = PriorityFlood.fill(grid, GridFixtures.edgeSeeds(grid), 1e-3f);
        List<Basin> basins = BasinExtractor.basins(grid, filled, 0.5f, 0f);
        assertEquals(1, basins.size());
        Basin b = basins.get(0);
        assertEquals(rimMin, b.spillHeight(), 1e-3f);
        assertEquals(rimMin - floor, b.maxDepth(), 1e-3f);
        assertEquals(grid.index(8, 8), b.floorIndex());
        assertEquals(grid.index(8, 4), b.spillIndex());
        assertEquals(49, b.cells().length);
        assertEquals(0, b.id());
    }

    @Test
    public void spillIsThePourPointNotAnIslandAtTheSameHeight() {
        DemGrid raw = BreacherTest.islandBasin();
        float[] filled = PriorityFlood.fill(raw, GridFixtures.edgeSeeds(raw), 1e-3f);
        List<Basin> basins = BasinExtractor.basins(raw, filled, 0.5f, 0f);
        assertEquals(1, basins.size());
        Basin basin = basins.get(0);
        assertEquals(raw.index(8, 2), basin.floorIndex());
        assertEquals(raw.index(4, 2), basin.spillIndex());
        assertEquals(368f, basin.spillHeight(), 0f);
        assertEquals(37f, basin.maxDepth(), 0f);
    }

    @Test
    public void basinsBelowSeaLevelOrMinimumDepthAreSkipped() {
        DemGrid grid = bowl();
        float[] filled = PriorityFlood.fill(grid, GridFixtures.edgeSeeds(grid), 1e-3f);
        assertTrue(BasinExtractor.basins(grid, filled, 0.5f, RIM_MIN + 1f).isEmpty());
        assertTrue(BasinExtractor.basins(grid, filled, RIM_MIN - FLOOR + 1f, 0f).isEmpty());
    }

    @Test
    public void inlandHollowsAreBelowTheSeaAndRingedByLand() {
        DemGrid natural = new DemGrid(0, 0, 8, 17, 17);
        Arrays.fill(natural.values(), 60f);
        boolean[] ocean = new boolean[natural.values().length];
        for (int j = 6; j <= 8; j++) {
            for (int i = 6; i <= 8; i++) {
                natural.set(i, j, i == 7 && j == 7 ? 20f : 30f);
            }
        }
        natural.set(0, 12, 30f);
        natural.set(1, 12, 30f);
        natural.set(12, 12, 30f);
        natural.set(13, 12, 10f);
        ocean[natural.index(13, 12)] = true;
        natural.set(12, 2, SEA + 0.4f);
        BitSet inland = BasinExtractor.inland(natural, ocean, (int) SEA);
        assertEquals("only the ringed hollow is inland " + inland, 9, inland.cardinality());
        assertTrue(inland.get(natural.index(7, 7)));
        assertFalse("a lowland open to the window edge is not inland", inland.get(natural.index(1, 12)));
        assertFalse("a lowland beside the sea is not inland", inland.get(natural.index(12, 12)));
        assertFalse("ground that rounds to the sea surface is not below it", inland.get(natural.index(12, 2)));
        assertEquals(inland, BasinExtractor.hollow(natural, inland, natural.index(6, 8)));
        assertTrue(BasinExtractor.hollow(natural, inland, natural.index(3, 3)).isEmpty());
        float[] filled = natural.values().clone();
        List<Basin> hollows = BasinExtractor.hollows(natural, inland, filled, (int) SEA, 5);
        assertEquals(1, hollows.size());
        Basin hollow = hollows.get(0);
        assertTrue(hollow.belowSea());
        assertEquals(5, hollow.id());
        assertEquals(natural.index(7, 7), hollow.floorIndex());
        assertEquals(SEA, hollow.spillHeight(), 0f);
        assertEquals(SEA - 20f, hollow.maxDepth(), 0f);
        assertEquals(9, hollow.cells().length);
        filled[natural.index(6, 6)] = SEA + 1f;
        assertTrue("a hollow that still fills above the sea is an ordinary basin",
                BasinExtractor.hollows(natural, inland, filled, (int) SEA, 0).isEmpty());
    }

    private static DemGrid bowl() {
        DemGrid grid = new DemGrid(0, 0, 8, 17, 17);
        for (int j = 0; j < 17; j++) {
            for (int i = 0; i < 17; i++) {
                int ring = Math.max(Math.abs(i - 8), Math.abs(j - 8));
                float height = ring < 4 ? FLOOR + 2f * ring : ring == 4 ? RIM : RIM_MIN - 3f * (ring - 4);
                grid.set(i, j, height);
            }
        }
        grid.set(8, 4, RIM_MIN);
        return grid;
    }
}
