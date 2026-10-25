package art.arcane.iris.generation.hydrology.grid;

import org.junit.Test;

import java.util.BitSet;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PriorityFloodTest {
    @Test
    public void fillsSinglePitToSpillHeight() {
        DemGrid grid = GridFixtures.uniform(5, 5, 10f);
        grid.set(2, 2, 2f);
        BitSet seeds = GridFixtures.edgeSeeds(grid);
        float[] filled = PriorityFlood.fill(grid, seeds, 1e-3f);
        assertEquals(10f, filled[grid.index(2, 2)], 1e-2f);
        assertTrue(filled[grid.index(2, 2)] > 10f);
        assertEquals(2f, grid.get(2, 2), 0f);
        for (int seed = seeds.nextSetBit(0); seed >= 0; seed = seeds.nextSetBit(seed + 1)) {
            assertEquals(grid.values()[seed], filled[seed], 0f);
        }
    }

    @Test
    public void filledNeverBelowRawAndHasNoInteriorPits() {
        for (long seed = 0L; seed < 200L; seed++) {
            DemGrid grid = GridFixtures.random(seed, 32, 32);
            BitSet seeds = GridFixtures.edgeSeeds(grid);
            float[] filled = PriorityFlood.fill(grid, seeds, 1e-3f);
            float[] raw = grid.values();
            for (int cell = 0; cell < raw.length; cell++) {
                assertTrue(filled[cell] >= raw[cell]);
                if (!seeds.get(cell)) {
                    assertTrue(GridFixtures.hasLowerNeighbour(grid, filled, cell));
                }
            }
        }
    }

    @Test
    public void fillIsDeterministic() {
        DemGrid first = GridFixtures.random(1337L, 48, 40);
        DemGrid second = GridFixtures.random(1337L, 48, 40);
        BitSet firstSeeds = GridFixtures.edgeSeeds(first);
        BitSet secondSeeds = new BitSet();
        for (int cell = second.width() * second.depth() - 1; cell >= 0; cell--) {
            if (firstSeeds.get(cell)) {
                secondSeeds.set(cell);
            }
        }
        float[] a = PriorityFlood.fill(first, firstSeeds, 1e-3f);
        float[] b = PriorityFlood.fill(second, secondSeeds, 1e-3f);
        assertArrayEquals(bits(a), bits(b));
    }

    @Test
    public void fillRejectsEmptySeeds() {
        DemGrid grid = GridFixtures.uniform(4, 4, 1f);
        assertThrows(IllegalArgumentException.class, () -> PriorityFlood.fill(grid, new BitSet(), 1e-3f));
    }

    private static int[] bits(float[] values) {
        int[] bits = new int[values.length];
        for (int index = 0; index < values.length; index++) {
            bits[index] = Float.floatToRawIntBits(values[index]);
        }
        return bits;
    }
}
