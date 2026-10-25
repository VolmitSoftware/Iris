package art.arcane.iris.generation.hydrology.grid;

import org.junit.Test;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BreacherTest {
    @Test
    public void breachedBasinDrainsAfterRefill() {
        DemGrid raw = pittedPlane(10f);
        BitSet seeds = GridFixtures.edgeSeeds(raw);
        float[] filled = PriorityFlood.fill(raw, seeds, 1e-3f);
        List<Basin> before = BasinExtractor.basins(raw, filled, 0.5f, 0f);
        assertEquals(1, before.size());
        assertEquals(40f, before.get(0).maxDepth(), 1e-3f);
        float[] untouched = filled.clone();
        Breacher.breach(raw, filled, before.get(0));
        assertArrayEquals(untouched, filled, 0f);
        assertTrue(BasinExtractor.basins(raw, PriorityFlood.fill(raw, seeds, 1e-3f), 0.5f, 0f).isEmpty());
    }

    @Test
    public void breachLowersDrainWhenFloorIsLowestCell() {
        DemGrid raw = pittedPlane(50f);
        BitSet seeds = GridFixtures.edgeSeeds(raw);
        float[] filled = PriorityFlood.fill(raw, seeds, 1e-3f);
        List<Basin> before = BasinExtractor.basins(raw, filled, 0.5f, 0f);
        assertEquals(1, before.size());
        Breacher.breach(raw, filled, before.get(0));
        float lowestEdge = Float.POSITIVE_INFINITY;
        for (int j = 0; j < raw.depth(); j++) {
            lowestEdge = Math.min(lowestEdge, raw.get(0, j));
        }
        assertTrue(lowestEdge < 30f);
        assertTrue(BasinExtractor.basins(raw, PriorityFlood.fill(raw, seeds, 1e-3f), 0.5f, 0f).isEmpty());
    }

    @Test
    public void longBreachPathLeavesNoBasin() {
        int lake = 90;
        int width = lake + 24;
        DemGrid raw = new DemGrid(0, 0, 8, width, 3);
        for (int j = 0; j < 3; j++) {
            for (int i = 0; i < width; i++) {
                float height = i < 20 ? 29.80f + 0.01f * i : i == 20 ? 31f : i < 21 + lake ? 30.2f : i == 21 + lake ? 30f : 40f;
                raw.set(i, j, height);
            }
        }
        BitSet seeds = westSeeds(raw);
        float[] filled = PriorityFlood.fill(raw, seeds, 1e-3f);
        List<Basin> before = BasinExtractor.basins(raw, filled, 0.5f, 0f);
        assertEquals(1, before.size());
        float[] original = raw.values().clone();
        Breacher.breach(raw, filled, before.get(0));
        int carved = 0;
        for (int cell = 0; cell < original.length; cell++) {
            if (raw.values()[cell] < original[cell]) {
                carved++;
            }
        }
        assertTrue(carved >= 80);
        assertTrue(BasinExtractor.basins(raw, PriorityFlood.fill(raw, seeds, 1e-3f), 0.5f, 0f).isEmpty());
    }

    @Test
    public void breachIntoDownstreamPondNeverCutsBelowTargetLevel() {
        int width = 120;
        DemGrid raw = new DemGrid(0, 0, 8, width, 3);
        for (int j = 0; j < 3; j++) {
            for (int i = 0; i < width; i++) {
                float height;
                if (i <= 40) {
                    height = 70f + 0.25f * i;
                } else if (i == 41) {
                    height = 95f;
                } else if (i < 60) {
                    height = i == 50 ? 60f : 62f;
                } else if (i == 60) {
                    height = 100f;
                } else if (i < 80) {
                    height = i == 70 ? 80f : 84f;
                } else {
                    height = 120f;
                }
                raw.set(i, j, height);
            }
        }
        BitSet seeds = westSeeds(raw);
        float[] filled = PriorityFlood.fill(raw, seeds, 1e-3f);
        List<Basin> before = BasinExtractor.basins(raw, filled, 0.5f, 0f);
        assertEquals(2, before.size());
        Basin upstream = before.get(1);
        assertEquals(70, upstream.floorIndex() % width);
        float targetLevel = raw.get(39, 0);
        float[] original = raw.values().clone();
        Breacher.breach(raw, filled, upstream);
        for (int cell = 0; cell < original.length; cell++) {
            assertTrue(raw.values()[cell] >= Math.min(original[cell], targetLevel));
        }
        for (Basin residual : BasinExtractor.basins(raw, PriorityFlood.fill(raw, seeds, 1e-3f), 0.5f, 0f)) {
            for (int cell : residual.cells()) {
                assertTrue(cell % width < 60);
            }
        }
    }

    @Test
    public void breachDrainsABasinWithAnIslandAtItsSpillHeight() {
        DemGrid raw = islandBasin();
        BitSet seeds = GridFixtures.edgeSeeds(raw);
        float[] filled = PriorityFlood.fill(raw, seeds, 1e-3f);
        List<Basin> before = BasinExtractor.basins(raw, filled, 0.5f, 0f);
        assertEquals(1, before.size());
        Breacher.breach(raw, filled, before.get(0));
        assertTrue(BasinExtractor.basins(raw, PriorityFlood.fill(raw, seeds, 1e-3f), 0.5f, 0f).isEmpty());
    }

    @Test
    public void aBumpInsideTheBasinIsNeverItsSpillWhateverItsHeight() {
        List<String> failures = new ArrayList<>();
        for (int step = 0; step <= 200; step++) {
            float island = 368f + step * 0.00005f;
            DemGrid raw = islandBasin(island);
            BitSet seeds = GridFixtures.edgeSeeds(raw);
            float[] filled = PriorityFlood.fill(raw, seeds, 1e-3f);
            List<Basin> basins = BasinExtractor.basins(raw, filled, 0.5f, 0f);
            if (basins.size() != 1) {
                failures.add(island + ": " + basins.size() + " basins");
                continue;
            }
            if (basins.get(0).spillIndex() == raw.index(7, 2)) {
                failures.add(island + ": spill on the bump");
            }
            try {
                Breacher.breach(raw, filled, basins.get(0));
            } catch (IllegalStateException failure) {
                failures.add(island + ": " + failure.getMessage());
            }
        }
        assertTrue(failures.toString(), failures.isEmpty());
        DemGrid raw = islandBasin(368.0031f);
        float[] filled = PriorityFlood.fill(raw, GridFixtures.edgeSeeds(raw), 1e-3f);
        Basin basin = BasinExtractor.basins(raw, filled, 0.5f, 0f).get(0);
        assertEquals(raw.index(4, 2), basin.spillIndex());
        Breacher.breach(raw, filled, basin);
    }

    static DemGrid islandBasin() {
        return islandBasin(368f);
    }

    static DemGrid islandBasin(float island) {
        DemGrid raw = new DemGrid(0, 0, 8, 11, 5);
        for (int j = 0; j < 5; j++) {
            for (int i = 0; i < 11; i++) {
                float height;
                if (j == 0 || j == 4 || i == 10) {
                    height = 400f;
                } else if (i == 0) {
                    height = 300f;
                } else if (i <= 3) {
                    height = 360f;
                } else if (i == 4) {
                    height = j == 2 ? 368f : 400f;
                } else {
                    height = 340f;
                }
                raw.set(i, j, height);
            }
        }
        raw.set(7, 2, island);
        raw.set(8, 2, 331f);
        return raw;
    }

    private static BitSet westSeeds(DemGrid raw) {
        BitSet seeds = new BitSet();
        for (int j = 0; j < raw.depth(); j++) {
            seeds.set(raw.index(0, j));
        }
        return seeds;
    }

    private static DemGrid pittedPlane(float base) {
        DemGrid raw = new DemGrid(0, 0, 8, 21, 21);
        for (int j = 0; j < 21; j++) {
            for (int i = 0; i < 21; i++) {
                raw.set(i, j, base + 5f * i);
            }
        }
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                raw.set(14 + dx, 10 + dz, 30f + Math.abs(dx) + Math.abs(dz));
            }
        }
        return raw;
    }
}
