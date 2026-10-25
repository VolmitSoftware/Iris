package art.arcane.iris.generation.hydrology.grid;

import org.junit.Test;

import java.util.BitSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class D8RouterTest {
    private static final int SEED = 0;

    @Test
    public void everyCellDrainsToASeed() {
        DemGrid grid = GridFixtures.uniform(8, 8, 5f);
        BitSet seeds = new BitSet();
        seeds.set(grid.index(0, 0));
        float[] filled = PriorityFlood.fill(grid, seeds, 1e-3f);
        int[] receivers = D8Router.receivers(grid, filled);
        int n = receivers.length;
        assertEquals(-1, receivers[SEED]);
        for (int i = 0; i < n; i++) {
            assertTrue(chainReachesSeed(receivers, i, n));
        }
    }

    @Test
    public void receiversPointToStrictlyLowerFilledNeighbour() {
        for (long seed = 0L; seed < 50L; seed++) {
            DemGrid grid = GridFixtures.random(seed, 32, 32);
            BitSet seeds = GridFixtures.edgeSeeds(grid);
            float[] filled = PriorityFlood.fill(grid, seeds, 1e-3f);
            int[] receivers = D8Router.receivers(grid, filled);
            for (int i = 0; i < receivers.length; i++) {
                int r = receivers[i];
                if (r < 0) {
                    assertTrue(seeds.get(i));
                    continue;
                }
                assertTrue(GridFixtures.adjacent(grid, i, r));
                assertTrue(filled[r] < filled[i]);
            }
        }
    }

    @Test
    public void receiverFollowsSteepestDescent() {
        DemGrid grid = GridFixtures.uniform(9, 9, 0f);
        float[] diagonalPlane = new float[81];
        float[] cardinalPlane = new float[81];
        for (int j = 0; j < 9; j++) {
            for (int i = 0; i < 9; i++) {
                diagonalPlane[grid.index(i, j)] = i + 2f * j;
                cardinalPlane[grid.index(i, j)] = i + 3f * j;
            }
        }
        assertEquals(grid.index(3, 3), D8Router.receivers(grid, diagonalPlane)[grid.index(4, 4)]);
        assertEquals(grid.index(4, 3), D8Router.receivers(grid, cardinalPlane)[grid.index(4, 4)]);
    }

    @Test
    public void equalSlopesBreakTowardLowerIndex() {
        DemGrid grid = GridFixtures.uniform(9, 9, 20f);
        float[] filled = grid.values().clone();
        filled[grid.index(4, 4)] = 10f;
        filled[grid.index(5, 4)] = 9f;
        filled[grid.index(4, 5)] = 9f;
        assertEquals(grid.index(5, 4), D8Router.receivers(grid, filled)[grid.index(4, 4)]);
    }

    private static boolean chainReachesSeed(int[] receivers, int start, int n) {
        int current = start;
        for (int step = 0; step <= n; step++) {
            int next = receivers[current];
            if (next < 0) {
                return current == SEED;
            }
            current = next;
        }
        return false;
    }
}
