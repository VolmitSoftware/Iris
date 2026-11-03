package art.arcane.iris.generation.hydrology.grid;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class D8RouterTest {
    private static final int SEED = 0;
    private static final long WORLD_SEED = 1337L;

    @Test
    public void everyCellDrainsToASeed() {
        DemGrid grid = GridFixtures.uniform(8, 8, 5f);
        BitSet seeds = new BitSet();
        seeds.set(grid.index(0, 0));
        float[] filled = PriorityFlood.fill(grid, seeds, 1e-3f);
        int[] receivers = D8Router.receivers(grid, filled, WORLD_SEED);
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
            int[] receivers = D8Router.receivers(grid, filled, WORLD_SEED);
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
        assertEquals(grid.index(3, 3), D8Router.receivers(grid, diagonalPlane, WORLD_SEED)[grid.index(4, 4)]);
        assertEquals(grid.index(4, 3), D8Router.receivers(grid, cardinalPlane, WORLD_SEED)[grid.index(4, 4)]);
    }

    @Test
    public void equalSlopeTiesFollowAWorldHashNotScanOrder() {
        for (long seed = 1L; seed <= 24L; seed++) {
            DemGrid grid = GridFixtures.uniform(33, 33, 5f);
            float[] filled = PriorityFlood.fill(grid, GridFixtures.edgeSeeds(grid), 1e-3f);
            int[] receivers = D8Router.receivers(grid, filled, seed);
            assertTrue("seed " + seed + ": reruns agree", Arrays.equals(receivers, D8Router.receivers(grid, filled, seed)));
            List<Integer> ties = ties(grid, filled);
            assertTrue("seed " + seed + ": ties on the medial lines " + ties.size(), ties.size() >= 40);
            int firstInScan = 0;
            for (int cell : ties) {
                assertEquals("seed " + seed + ": a tie takes one of its steepest neighbours", bestSlope(grid, filled, cell), slope(grid, filled, cell, receivers[cell]), 0f);
                firstInScan += receivers[cell] == firstSteepest(grid, filled, cell) ? 1 : 0;
            }
            assertTrue("seed " + seed + ": the first neighbour in scan order wins " + firstInScan + " of " + ties.size(), 4 * firstInScan >= ties.size());
            assertTrue("seed " + seed + ": a later neighbour wins " + (ties.size() - firstInScan) + " of " + ties.size(), 4 * (ties.size() - firstInScan) >= ties.size());

            DemGrid shifted = new DemGrid(4096, 0, grid.cell(), 33, 33);
            System.arraycopy(grid.values(), 0, shifted.values(), 0, grid.values().length);
            float[] shiftedFilled = PriorityFlood.fill(shifted, GridFixtures.edgeSeeds(shifted), 1e-3f);
            assertEquals("seed " + seed + ": the same cells tie after a shift", ties, ties(shifted, shiftedFilled));
            int[] shiftedReceivers = D8Router.receivers(shifted, shiftedFilled, seed);
            int moved = 0;
            for (int cell : ties) {
                moved += receivers[cell] == shiftedReceivers[cell] ? 0 : 1;
            }
            assertTrue("seed " + seed + ": ties follow world position, " + moved + " of " + ties.size() + " changed", 4 * moved >= ties.size());
        }
    }

    @Test
    public void tiesDependOnTheSeedAndOnlyOnTiedNeighbours() {
        DemGrid grid = GridFixtures.uniform(9, 9, 20f);
        float[] filled = grid.values().clone();
        filled[grid.index(4, 4)] = 10f;
        filled[grid.index(5, 4)] = 9f;
        filled[grid.index(4, 5)] = 9f;
        filled[grid.index(3, 4)] = 9.5f;
        int east = 0;
        for (long seed = 0L; seed < 64L; seed++) {
            int receiver = D8Router.receivers(grid, filled, seed)[grid.index(4, 4)];
            assertTrue("seed " + seed + " goes to " + receiver, receiver == grid.index(5, 4) || receiver == grid.index(4, 5));
            east += receiver == grid.index(5, 4) ? 1 : 0;
        }
        assertTrue("east wins " + east + " of 64 seeds", east >= 16 && east <= 48);
    }

    private static List<Integer> ties(DemGrid grid, float[] filled) {
        List<Integer> ties = new ArrayList<>();
        for (int j = 1; j < grid.depth() - 1; j++) {
            for (int i = 1; i < grid.width() - 1; i++) {
                int cell = grid.index(i, j);
                float best = bestSlope(grid, filled, cell);
                int sharing = 0;
                for (int neighbour : neighbours(grid, cell)) {
                    sharing += best > 0f && slope(grid, filled, cell, neighbour) == best ? 1 : 0;
                }
                if (sharing >= 2) {
                    ties.add(cell);
                }
            }
        }
        return ties;
    }

    private static int firstSteepest(DemGrid grid, float[] filled, int cell) {
        float best = bestSlope(grid, filled, cell);
        for (int neighbour : neighbours(grid, cell)) {
            if (slope(grid, filled, cell, neighbour) == best) {
                return neighbour;
            }
        }
        return -1;
    }

    private static float bestSlope(DemGrid grid, float[] filled, int cell) {
        float best = 0f;
        for (int neighbour : neighbours(grid, cell)) {
            best = Math.max(best, slope(grid, filled, cell, neighbour));
        }
        return best;
    }

    private static float slope(DemGrid grid, float[] filled, int cell, int neighbour) {
        boolean diagonal = cell % grid.width() != neighbour % grid.width() && cell / grid.width() != neighbour / grid.width();
        float drop = filled[cell] - filled[neighbour];
        return diagonal ? drop * (float) (1.0D / Math.sqrt(2.0D)) : drop;
    }

    private static List<Integer> neighbours(DemGrid grid, int cell) {
        List<Integer> neighbours = new ArrayList<>(8);
        int i = cell % grid.width();
        int j = cell / grid.width();
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if ((dx != 0 || dz != 0) && i + dx >= 0 && i + dx < grid.width() && j + dz >= 0 && j + dz < grid.depth()) {
                    neighbours.add(grid.index(i + dx, j + dz));
                }
            }
        }
        return neighbours;
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
