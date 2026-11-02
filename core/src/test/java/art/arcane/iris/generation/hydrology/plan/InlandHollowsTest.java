package art.arcane.iris.generation.hydrology.plan;

import art.arcane.iris.generation.hydrology.grid.DemGrid;
import art.arcane.iris.generation.hydrology.grid.HeightLattice;
import art.arcane.iris.generation.hydrology.grid.HydrologySampleBuffer;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.PondKind;
import art.arcane.iris.generation.hydrology.water.PondMask;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class InlandHollowsTest {
    private static final int SEA_LEVEL = SyntheticTerrain.SEA_LEVEL;
    private static final int CELL = 8;
    private static final int SIZE = 128;
    private static final double PIT_X = 512D;
    private static final double PIT_Z = 512D;
    private static final double PIT_RADIUS = 100D;
    private static final double PIT_DEPTH = 64D;
    private static final int LAKE_STEP = 16;

    @Test
    public void hollowCutByTheGridEdgeIsClassifiedLikeAWholeOne() {
        SyntheticTerrain terrain = SyntheticTerrain.valleySlope().withPit(PIT_X, PIT_Z, PIT_RADIUS, PIT_DEPTH).withLand((x, z) -> true);
        HeightLattice fine = new HeightLattice(terrain, WindowPlanner.FINE_CELL, false, 1 << 20);
        Window whole = new Window(fine, 0, 0);
        Window cut = new Window(fine, (int) PIT_X / CELL, 0);
        InlandHollows.Hollows wholeView = whole.classify();
        InlandHollows.Hollows cutView = cut.classify();
        int inland = 0;
        int basin = 0;
        for (int j = 0; j < SIZE; j++) {
            for (int i = 0; i < SIZE - (int) PIT_X / CELL; i++) {
                int inWhole = j * SIZE + i + (int) PIT_X / CELL;
                int inCut = j * SIZE + i;
                assertEquals("inland at cell " + i + "," + j, wholeView.inland().get(inWhole), cutView.inland().get(inCut));
                assertEquals("hollow basin at cell " + i + "," + j, wholeView.basins().get(inWhole), cutView.basins().get(inCut));
                inland += cutView.inland().get(inCut) ? 1 : 0;
                basin += cutView.basins().get(inCut) ? 1 : 0;
            }
        }
        assertTrue("the grid edge cuts through inland cells " + inland, inland > 20);
        assertTrue("the basin reaches past the below-sea floor up the walls " + basin + " > " + inland, basin > inland + 20);
        int crest = cut.index(PIT_X + PIT_RADIUS + 8D, PIT_Z);
        assertTrue("the rim crest is outside the basin", !cutView.basins().get(crest));
    }

    @Test
    public void lowlandOpenToTheSeaIsNotInland() {
        SyntheticTerrain terrain = SyntheticTerrain.valleySlope().withPit(PIT_X, PIT_Z, PIT_RADIUS, PIT_DEPTH)
                .withLand((x, z) -> Math.hypot(x - PIT_X, z - PIT_Z) > PIT_RADIUS * 0.3D);
        HeightLattice fine = new HeightLattice(terrain, WindowPlanner.FINE_CELL, false, 1 << 20);
        InlandHollows.Hollows hollows = new Window(fine, 0, 0).classify();
        assertEquals("below-sea land around a patch of sea is the sea's shore, not an inland hollow", 0, hollows.inland().cardinality());
        assertEquals(0, hollows.basins().cardinality());
    }

    @Test
    public void hollowHeldByARegionLakeIsLeftToTheLake() {
        SyntheticTerrain terrain = SyntheticTerrain.valleySlope().withPit(PIT_X, PIT_Z, PIT_RADIUS, PIT_DEPTH).withLand((x, z) -> true);
        HeightLattice fine = new HeightLattice(terrain, WindowPlanner.FINE_CELL, false, 1 << 20);
        Window window = new Window(fine, 0, 0);
        assertTrue("the hollow is inland without a lake", window.classify().inland().cardinality() > 20);
        int nodes = (int) (2D * PIT_RADIUS) / LAKE_STEP + 1;
        long[] bits = new long[(nodes * nodes + 63) >>> 6];
        for (int cell = 0; cell < nodes * nodes; cell++) {
            bits[cell >>> 6] |= 1L << cell;
        }
        int origin = (int) (PIT_X - PIT_RADIUS);
        PondMask mask = new PondMask(origin, origin, LAKE_STEP, nodes, nodes, bits);
        Pond lake = new Pond(5L, PondKind.LAKE, 74, 14, origin, (int) PIT_Z, 77f, mask, 0L, 0, "water", (int) PIT_X, (int) PIT_Z);
        InlandHollows.Hollows held = InlandHollows.classify(fine, window.natural, window.ocean, SEA_LEVEL, 0.5f, 0.001f, List.of(lake));
        assertEquals("a region lake holds the hollow, so it is no inland hollow", 0, held.inland().cardinality());
        assertEquals("and its basin keeps the trunk", 0, held.basins().cardinality());
    }

    private static final class Window {
        private final HeightLattice fine;
        private final DemGrid natural;
        private final boolean[] ocean;

        private Window(HeightLattice fine, int cellX, int cellZ) {
            this.fine = fine;
            HydrologySampleBuffer samples = new HydrologySampleBuffer(SIZE * SIZE);
            int stride = CELL / WindowPlanner.FINE_CELL;
            fine.fill(cellX * stride, cellZ * stride, SIZE, SIZE, stride, samples);
            this.natural = new DemGrid(cellX * CELL, cellZ * CELL, CELL, SIZE, SIZE);
            System.arraycopy(samples.height(), 0, natural.values(), 0, SIZE * SIZE);
            this.ocean = samples.ocean().clone();
        }

        private InlandHollows.Hollows classify() {
            return InlandHollows.classify(fine, natural, ocean, SEA_LEVEL, 0.5f, 0.001f, List.of());
        }

        private int index(double x, double z) {
            return (int) Math.floor((z - natural.originZ()) / CELL) * SIZE + (int) Math.floor((x - natural.originX()) / CELL);
        }
    }
}
