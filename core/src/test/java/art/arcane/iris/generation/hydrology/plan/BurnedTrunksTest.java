package art.arcane.iris.generation.hydrology.plan;

import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.grid.DemGrid;
import art.arcane.iris.generation.hydrology.water.HydrologyTier;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachStyle;
import org.junit.Test;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BurnedTrunksTest {
    private static final int CELL = 8;
    private static final int SIZE = 32;
    private static final int TRENCH_ROW = 10;
    private static final float SEA = 40f;
    private static final int HIGH_GROUND = 6;
    private static final float DEEP_BURN = 10f;
    private static final int HOLLOW_FROM = 8;
    private static final int HOLLOW_TO = 12;

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
        BurnedTrunks trunks = BurnedTrunks.burn(raw, List.of(feeder, backtrack), 3f, 0.001f, land(raw, new BitSet()));
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

    @Test
    public void trunkStopsAtAHollowBasinAndRestartsBeyondIt() {
        DemGrid raw = new DemGrid(0, 0, CELL, SIZE, SIZE);
        Arrays.fill(raw.values(), 200f);
        BitSet basin = new BitSet(SIZE * SIZE);
        for (int i = 0; i < SIZE; i++) {
            boolean inside = i >= HOLLOW_FROM && i <= HOLLOW_TO;
            raw.set(i, TRENCH_ROW, inside ? 10f : 100f);
            if (inside) {
                basin.set(raw.index(i, TRENCH_ROW));
            }
        }
        HydrologyTier major = HydrologySettings.compile(new IrisHydrology(), 1337L, 40, -64, 320).majorTier();
        BurnedTrunks trunks = BurnedTrunks.burn(raw, List.of(reach(1L, 0L, major, 2, 24)), 3f, 0.001f, land(raw, basin));
        int[] receivers = new int[SIZE * SIZE];
        Arrays.fill(receivers, -1);
        trunks.route(receivers);
        for (int i = 2; i < HOLLOW_FROM - 1; i++) {
            assertEquals("the trunk above the hollow runs on at " + i, raw.index(i + 1, TRENCH_ROW), receivers[raw.index(i, TRENCH_ROW)]);
        }
        assertEquals("the trunk ends at the hollow basin", -1, receivers[raw.index(HOLLOW_FROM - 1, TRENCH_ROW)]);
        for (int i = HOLLOW_FROM; i <= HOLLOW_TO; i++) {
            assertFalse("the hollow basin holds no trunk at " + i, trunks.contains(raw.index(i, TRENCH_ROW)));
            assertEquals("the hollow keeps its own drainage at " + i, -1, receivers[raw.index(i, TRENCH_ROW)]);
            assertEquals("the hollow is not burned at " + i, 10f, raw.values()[raw.index(i, TRENCH_ROW)], 0f);
        }
        for (int i = HOLLOW_TO + 1; i <= 24; i++) {
            float height = raw.values()[raw.index(i, TRENCH_ROW)];
            assertTrue("the trunk beyond the hollow keeps its burned height at " + i + ": " + height, height > 90f);
            if (i < 24) {
                assertEquals("the trunk beyond the hollow runs on at " + i, raw.index(i + 1, TRENCH_ROW), receivers[raw.index(i, TRENCH_ROW)]);
            }
        }
        float[] weights = new float[SIZE * SIZE];
        trunks.inject(weights);
        assertTrue("the trunk beyond the hollow restarts with the river's catchment", weights[raw.index(HOLLOW_TO + 1, TRENCH_ROW)] > 0f);
    }

    @Test
    public void burnedTrunkNeverDigsBelowTheSeaOnDryLand() {
        DemGrid raw = new DemGrid(0, 0, CELL, SIZE, SIZE);
        Arrays.fill(raw.values(), 200f);
        for (int i = 0; i < SIZE; i++) {
            raw.set(i, TRENCH_ROW, i < HOLLOW_FROM ? SEA + HIGH_GROUND + 2f : i <= HOLLOW_TO ? SEA - 10f : 60f);
        }
        HydrologyTier major = HydrologySettings.compile(new IrisHydrology(), 1337L, 40, -64, 320).majorTier();
        BurnedTrunks trunks = BurnedTrunks.burn(raw, List.of(reach(1L, 0L, major, 2, 24)), DEEP_BURN, 0.001f, land(raw, new BitSet()));
        for (int i = 2; i < HOLLOW_FROM; i++) {
            float height = raw.values()[raw.index(i, TRENCH_ROW)];
            assertTrue("a deep burn on high ground stays above the sea at " + i + ": " + height, height > SEA && height <= SEA + 1f);
        }
        int restart = raw.index(HOLLOW_TO + 1, TRENCH_ROW);
        assertEquals("the trunk is not dragged below the sea where it climbs out of low ground", 60f - DEEP_BURN, raw.values()[restart], 0f);
        int[] receivers = new int[SIZE * SIZE];
        Arrays.fill(receivers, -1);
        trunks.route(receivers);
        assertEquals("the trunk link up out of low ground is cut", -1, receivers[raw.index(HOLLOW_TO, TRENCH_ROW)]);
        float[] weights = new float[SIZE * SIZE];
        trunks.inject(weights);
        assertTrue("the trunk restarts with the river's catchment", weights[restart] > 0f);
        for (int i = HOLLOW_TO + 1; i < 24; i++) {
            assertTrue("the trunk beyond descends from its own height at " + i,
                    raw.values()[raw.index(i + 1, TRENCH_ROW)] < raw.values()[raw.index(i, TRENCH_ROW)]);
            assertTrue("the trunk beyond stays above the sea at " + i, raw.values()[raw.index(i, TRENCH_ROW)] > 45f);
        }
    }

    private static BurnedTrunks.Land land(DemGrid raw, BitSet hollows) {
        return new BurnedTrunks.Land(raw.values().clone(), new boolean[raw.values().length], (int) SEA, (int) SEA + HIGH_GROUND, hollows);
    }

    private static Reach reach(long id, long downstreamId, HydrologyTier tier, int fromI, int toI) {
        float[] xs = {fromI * CELL, toI * CELL};
        float[] zs = {TRENCH_ROW * CELL, TRENCH_ROW * CELL};
        return new Reach(id, tier, downstreamId, xs, zs, new int[]{100, 100}, new float[]{16f, 16f}, new float[]{8f, 8f},
                new ReachStyle[]{ReachStyle.RUN, ReachStyle.RUN}, 400000f);
    }
}
