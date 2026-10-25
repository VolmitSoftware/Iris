package art.arcane.iris.generation.hydrology.grid;

import org.junit.Test;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FlowAccumulatorTest {
    @Test
    public void outletTotalsEqualTotalWeight() {
        DemGrid grid = GridFixtures.random(7L, 40, 36);
        float[] filled = PriorityFlood.fill(grid, GridFixtures.edgeSeeds(grid), 1e-3f);
        int[] receivers = D8Router.receivers(grid, filled);
        Random random = new Random(11L);
        float[] weights = new float[receivers.length];
        float sumWeights = 0f;
        for (int cell = 0; cell < weights.length; cell++) {
            weights[cell] = 1 + random.nextInt(4);
            sumWeights += weights[cell];
        }
        float[] accumulation = FlowAccumulator.accumulate(receivers, filled, weights, Float.POSITIVE_INFINITY);
        float sumAtOutlets = 0f;
        float[] inflow = new float[receivers.length];
        for (int cell = 0; cell < receivers.length; cell++) {
            if (receivers[cell] < 0) {
                sumAtOutlets += accumulation[cell];
            } else {
                inflow[receivers[cell]] += accumulation[cell];
            }
        }
        assertEquals(sumWeights, sumAtOutlets, 1e-3f);
        for (int cell = 0; cell < receivers.length; cell++) {
            assertEquals(weights[cell] + inflow[cell], accumulation[cell], 0f);
        }
    }

    @Test
    public void accumulationSaturatesAtCap() {
        DemGrid grid = new DemGrid(0, 0, 8, 16, 16);
        for (int j = 0; j < 16; j++) {
            for (int i = 0; i < 16; i++) {
                grid.set(i, j, i + j);
            }
        }
        BitSet seeds = new BitSet();
        seeds.set(grid.index(0, 0));
        float[] filled = PriorityFlood.fill(grid, seeds, 1e-3f);
        int[] receivers = D8Router.receivers(grid, filled);
        float[] weights = new float[receivers.length];
        Arrays.fill(weights, 1f);
        assertEquals(256f, FlowAccumulator.accumulate(receivers, filled, weights, Float.POSITIVE_INFINITY)[grid.index(0, 0)], 0f);
        float[] accumulation = FlowAccumulator.accumulate(receivers, filled, weights, 100f);
        float maxAccumulation = 0f;
        for (float value : accumulation) {
            assertTrue(value <= 100f);
            maxAccumulation = Math.max(maxAccumulation, value);
        }
        assertEquals(100f, maxAccumulation, 0f);
    }
}
