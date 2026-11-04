package art.arcane.iris.generation.hydrology.underground;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GrottoShapeTest {
    static final int SEA = 100;

    static Grotto winding(long id, float roughness) {
        int points = 50;
        float[] xs = new float[points];
        float[] zs = new float[points];
        float[] halfWidth = new float[points];
        float[] roof = new float[points];
        float[] floor = new float[points];
        float[] lift = new float[points];
        for (int point = 0; point < points; point++) {
            xs[point] = point * 4f;
            zs[point] = (float) (22D * Math.sin(point / 7D));
            halfWidth[point] = point > 30 && point < 38 ? 11f : 6f;
            roof[point] = point > 30 && point < 38 ? 13f : 7f;
            floor[point] = 4f;
        }
        return new Grotto(id, SEA, roughness, 4, xs, zs, halfWidth, roof, floor, lift, new float[0], new float[0], new float[0], new int[0], new long[0]);
    }

    private static int[][] section(GrottoShape shape, Grotto grotto, int point) {
        int x = (int) grotto.xs()[point];
        int[][] spans = new int[121][];
        for (int z = -60; z <= 60; z++) {
            int low = Integer.MAX_VALUE;
            int high = Integer.MIN_VALUE;
            for (int y = shape.minY(); y <= shape.maxY(); y++) {
                if (shape.inside(x, y, z + (int) grotto.zs()[point])) {
                    low = Math.min(low, y);
                    high = Math.max(high, y);
                }
            }
            spans[z + 60] = low == Integer.MAX_VALUE ? null : new int[]{low, high};
        }
        return spans;
    }

    @Test
    public void shapeDependsOnlyOnThePlan() {
        Grotto a = winding(77L, 0.6f);
        Grotto b = winding(77L, 0.6f);
        GrottoShape first = new GrottoShape(a);
        GrottoShape second = new GrottoShape(b);
        GrottoShape scoped = new GrottoShape(a).scoped(0, -40, 80, 40);
        for (int x = 8; x < 72; x += 3) {
            for (int z = -30; z < 30; z += 3) {
                for (int y = first.minY(); y <= first.maxY(); y += 2) {
                    assertEquals(first.inside(x, y, z), second.inside(x, y, z));
                    assertEquals(first.inside(x, y, z), scoped.inside(x, y, z));
                }
            }
        }
    }

    @Test
    public void differentGrottosGetDifferentRock() {
        GrottoShape first = new GrottoShape(winding(1L, 0.6f));
        GrottoShape second = new GrottoShape(winding(2L, 0.6f));
        int differ = 0;
        for (int x = 8; x < 180; x += 2) {
            for (int z = -40; z < 40; z += 2) {
                for (int y = SEA - 8; y <= SEA + 16; y += 2) {
                    differ += first.inside(x, y, z) != second.inside(x, y, z) ? 1 : 0;
                }
            }
        }
        assertTrue("differ " + differ, differ > 200);
    }

    @Test
    public void waterSliceRunsFromMouthToHeadInEveryRoughness() {
        for (float roughness : new float[]{0f, 0.3f, 0.6f, 1f}) {
            for (long id = 1L; id <= 8L; id++) {
                Grotto grotto = winding(id, roughness);
                GrottoShape shape = new GrottoShape(grotto);
                int startX = (int) grotto.xs()[0];
                int startZ = (int) grotto.zs()[0];
                assertTrue(shape.inside(startX, SEA, startZ));
                Set<Long> seen = new HashSet<>();
                ArrayDeque<int[]> frontier = new ArrayDeque<>();
                frontier.add(new int[]{startX, startZ});
                seen.add(key(startX, startZ));
                while (!frontier.isEmpty()) {
                    int[] cell = frontier.poll();
                    for (int[] side : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int nx = cell[0] + side[0];
                        int nz = cell[1] + side[1];
                        if (shape.inside(nx, SEA, nz) && seen.add(key(nx, nz))) {
                            frontier.add(new int[]{nx, nz});
                        }
                    }
                }
                int last = grotto.xs().length - 1;
                assertTrue("roughness " + roughness + " id " + id, seen.contains(key((int) grotto.xs()[last], (int) grotto.zs()[last])) || nearReached(seen, grotto, last));
            }
        }
    }

    private static boolean nearReached(Set<Long> seen, Grotto grotto, int point) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (seen.contains(key((int) grotto.xs()[point] + dx, (int) grotto.zs()[point] + dz))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    @Test
    public void sectionsAreNotBoxes() {
        int flatRuns = 0;
        int sections = 0;
        int widest = 0;
        Grotto grotto = winding(5L, 0.6f);
        GrottoShape shape = new GrottoShape(grotto);
        for (int point = 8; point < 44; point += 3) {
            int[][] spans = section(shape, grotto, point);
            int longest = 0;
            int run = 0;
            int width = 0;
            Integer last = null;
            for (int[] span : spans) {
                if (span == null) {
                    continue;
                }
                width++;
                run = last != null && last == span[1] ? run + 1 : 1;
                longest = Math.max(longest, run);
                last = span[1];
            }
            flatRuns += longest * 2 > width ? 1 : 0;
            sections++;
            widest = Math.max(widest, width);
        }
        assertTrue("sections with a flat roof over half their width " + flatRuns + " of " + sections, flatRuns * 5 <= sections);
        assertTrue("widest " + widest, widest >= 14);
    }

    @Test
    public void someColumnsHoldOverhangs() {
        Grotto grotto = winding(9L, 0.8f);
        GrottoShape shape = new GrottoShape(grotto);
        int columns = 0;
        int overhangs = 0;
        for (int x = 8; x < 190; x++) {
            for (int z = -50; z < 50; z++) {
                boolean carved = false;
                boolean over = false;
                for (int y = SEA + 1; y <= shape.maxY() - 3; y++) {
                    if (!shape.inside(x, y, z)) {
                        continue;
                    }
                    carved = true;
                    for (int up = 1; up <= 3 && !over; up++) {
                        over = !shape.inside(x, y + up, z) && (shape.inside(x + 1, y + up, z) || shape.inside(x - 1, y + up, z)
                                || shape.inside(x, y + up, z + 1) || shape.inside(x, y + up, z - 1));
                    }
                }
                columns += carved ? 1 : 0;
                overhangs += over ? 1 : 0;
            }
        }
        assertTrue("overhangs " + overhangs + " of " + columns, overhangs * 4 > columns);
    }

    @Test
    public void pluggedCellsAreRock() {
        Grotto plain = winding(5L, 0.6f);
        int x = (int) plain.xs()[20];
        int z = (int) plain.zs()[20];
        assertTrue(new GrottoShape(plain).inside(x, SEA, z));
        Grotto plugged = new Grotto(plain.id(), plain.seaLevel(), plain.roughness(), plain.coast(), plain.xs(), plain.zs(), plain.halfWidth(), plain.roof(), plain.floor(), plain.lift(),
                plain.holeX(), plain.holeZ(), plain.holeRadius(), plain.holeTop(), new long[]{Grotto.plug(x, SEA, z)});
        assertFalse(new GrottoShape(plugged).inside(x, SEA, z));
    }

    @Test
    public void skylightOpensAboveTheRoofOnly() {
        Grotto plain = winding(5L, 0.3f);
        float x = plain.xs()[20];
        float z = plain.zs()[20];
        Grotto holed = new Grotto(plain.id(), plain.seaLevel(), plain.roughness(), plain.coast(), plain.xs(), plain.zs(), plain.halfWidth(), plain.roof(), plain.floor(), plain.lift(),
                new float[]{x}, new float[]{z}, new float[]{2.5f}, new int[]{SEA + 30}, new long[0]);
        GrottoShape shape = new GrottoShape(holed);
        assertTrue(shape.maxY() >= SEA + 30);
        assertTrue(shape.inside((int) x, SEA + 25, (int) z));
        assertFalse(shape.inside((int) x, SEA + 40, (int) z));
        assertFalse(new GrottoShape(plain).inside((int) x, SEA + 25, (int) z));
    }
}
