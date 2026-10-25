package art.arcane.iris.generation.hydrology.grid;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BasinExtractorTest {
    private static final float FLOOR = 10f;
    private static final float RIM = 30f;
    private static final float RIM_MIN = 24f;

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
