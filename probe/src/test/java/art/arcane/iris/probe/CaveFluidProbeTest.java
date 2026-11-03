package art.arcane.iris.probe;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class CaveFluidProbeTest {
    private static final int HEIGHT = 32;

    @Test
    public void bodiesConnectThroughFacesAndMeasureTheirWidestSide() {
        CaveFluidProbe.Grid grid = new CaveFluidProbe.Grid(64, HEIGHT);
        for (int x = 2; x < 42; x++) {
            grid.kinds[grid.index(x, 5, 7)] = 2;
        }
        grid.kinds[grid.index(50, 6, 7)] = 1;
        grid.pools[grid.index(50, 6, 7)] = 1;
        grid.kinds[grid.index(51, 7, 7)] = 1;

        List<CaveFluidProbe.Body> bodies = CaveFluidProbe.bodies(grid);

        assertEquals(3, bodies.size());
        assertEquals(new CaveFluidProbe.Body((byte) 2, false, 5, 40, 40), bodies.get(0));
    }

    @Test
    public void tallySortsBodiesIntoBandsAndSetsFloorLavaApart() {
        CaveFluidProbe.Tally tally = new CaveFluidProbe.Tally();
        CaveFluidProbe.add(tally, List.of(
                new CaveFluidProbe.Body((byte) 2, false, 8, 3, 9),
                new CaveFluidProbe.Body((byte) 2, true, 8, 3, 9),
                new CaveFluidProbe.Body((byte) 2, false, 70, 40, 200),
                new CaveFluidProbe.Body((byte) 1, true, 300, 33, 9)), -256, 8);

        assertEquals(1, tally.floorLavaBodies);
        assertEquals(1, tally.lavaPools[5]);
        assertEquals(1, tally.pools[5]);
        assertEquals(1, tally.lavaBodies[5]);
        assertEquals(1, tally.longLavaBodies[4]);
        assertEquals(1, tally.pools[1]);
        assertEquals(1, tally.longWaterBodies[1]);
        assertEquals(40, tally.longestLava);
    }
}
