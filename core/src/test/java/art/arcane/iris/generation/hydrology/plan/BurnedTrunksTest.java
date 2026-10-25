package art.arcane.iris.generation.hydrology.plan;

import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.grid.DemGrid;
import art.arcane.iris.generation.hydrology.water.HydrologyTier;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachStyle;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BurnedTrunksTest {
    private static final int CELL = 8;
    private static final int SIZE = 32;
    private static final int TRENCH_ROW = 10;

    @Test
    public void reachRoutedBackOverItsFeederKeepsTheFeederLinked() {
        DemGrid raw = new DemGrid(0, 0, CELL, SIZE, SIZE);
        Arrays.fill(raw.values(), 200f);
        for (int i = 0; i < SIZE; i++) {
            raw.set(i, TRENCH_ROW, 100f);
        }
        HydrologyTier major = HydrologySettings.compile(new IrisHydrology(), 1337L, 40, -64, 320).majorTier();
        Reach feeder = reach(1L, 2L, major, 2, 20);
        Reach backtrack = reach(2L, 0L, major, 20, 12);
        BurnedTrunks trunks = BurnedTrunks.burn(raw, List.of(feeder, backtrack), 3f, 0.001f);
        int[] receivers = new int[SIZE * SIZE];
        Arrays.fill(receivers, -1);
        trunks.route(receivers);
        int cell = raw.index(2, TRENCH_ROW);
        int end = raw.index(20, TRENCH_ROW);
        for (int step = 0; step < 18; step++) {
            int next = receivers[cell];
            assertTrue("feeder breaks at " + raw.blockX(cell), next >= 0);
            assertTrue(raw.values()[next] < raw.values()[cell]);
            cell = next;
        }
        assertEquals(end, cell);
        assertEquals(-1, receivers[end]);
    }

    private static Reach reach(long id, long downstreamId, HydrologyTier tier, int fromI, int toI) {
        float[] xs = {fromI * CELL, toI * CELL};
        float[] zs = {TRENCH_ROW * CELL, TRENCH_ROW * CELL};
        return new Reach(id, tier, downstreamId, xs, zs, new int[]{100, 100}, new float[]{16f, 16f}, new float[]{8f, 8f},
                new ReachStyle[]{ReachStyle.RUN, ReachStyle.RUN}, 400000f);
    }
}
