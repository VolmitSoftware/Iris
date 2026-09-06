package art.arcane.iris.engine.hydrology;

import org.junit.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class HydrologyRouteLengthsTest {
    @Test
    public void branchingRoutesMatchIndependentDownstreamWalks() {
        Random random = new Random(912541L);
        for (int width : new int[]{2, 3, 9, 17, 33, 65}) {
            for (int spacing : new int[]{4, 16, 64}) {
                for (int maximumLength : new int[]{63, 128, 257, 4096}) {
                    int[] parent = forest(width, random);
                    HydrologyPlannerSettings.Routing routing = routing(spacing, maximumLength);
                    assertArrayEquals(walks(parent, width, routing),
                            HydrologyPlanner.routeLengths(parent, width, routing));
                    reverse(parent);
                    assertArrayEquals(walks(parent, width, routing),
                            HydrologyPlanner.routeLengths(parent, width, routing));
                }
            }
        }
    }

    @Test
    public void exactLengthLimitAndMissingOutletsKeepTheirBounds() {
        int[] parent = {1, 2, 3, 4, -1, -1, -1, -1, -1};
        assertArrayEquals(new int[]{Integer.MAX_VALUE, 12, 8, 4, 0, 0, 0, 0, 0},
                HydrologyPlanner.routeLengths(parent, 9, routing(4, 12)));
    }

    @Test
    public void longRoutesDoNotUseTheCallStack() {
        int width = 257;
        int[] parent = new int[width * width];
        for (int row = 0; row < width; row++) {
            for (int column = 0; column < width; column++) {
                int index = row * width + column;
                int direction = row % 2 == 0 ? 1 : -1;
                int nextColumn = column + direction;
                parent[index] = nextColumn >= 0 && nextColumn < width ? index + direction : index + width;
            }
        }
        parent[parent.length - 1] = -1;
        int[] lengths = HydrologyPlanner.routeLengths(parent, width, routing(4, Integer.MAX_VALUE));
        assertEquals((parent.length - 1) * 4, lengths[0]);
        assertEquals(0, lengths[lengths.length - 1]);
        reverse(parent);
        lengths = HydrologyPlanner.routeLengths(parent, width, routing(4, Integer.MAX_VALUE));
        assertEquals((parent.length - 1) * 4, lengths[lengths.length - 1]);
        assertEquals(0, lengths[0]);
    }

    @Test
    public void cyclesFailBeforeTheyCanStallPlanning() {
        assertThrows(IllegalStateException.class,
                () -> HydrologyPlanner.routeLengths(new int[]{1, 0}, 2, routing(4, 128)));
    }

    private static int[] forest(int width, Random random) {
        int[] parent = new int[width * width];
        Arrays.fill(parent, -1);
        int[] candidates = new int[4];
        for (int index = 1; index < parent.length; index++) {
            int x = index % width;
            int z = index / width;
            int count = 0;
            if (x > 0) {
                candidates[count++] = index - 1;
            }
            if (z > 0) {
                candidates[count++] = index - width;
                if (x > 0) {
                    candidates[count++] = index - width - 1;
                }
                if (x + 1 < width) {
                    candidates[count++] = index - width + 1;
                }
            }
            if (random.nextInt(16) != 0) {
                parent[index] = candidates[random.nextInt(count)];
            }
        }
        return parent;
    }

    private static void reverse(int[] parent) {
        int[] original = parent.clone();
        for (int index = 0; index < parent.length; index++) {
            int next = original[parent.length - 1 - index];
            parent[index] = next < 0 ? -1 : parent.length - 1 - next;
        }
    }

    private static int[] walks(int[] parent, int width, HydrologyPlannerSettings.Routing routing) {
        int[] lengths = new int[parent.length];
        for (int source = 0; source < parent.length; source++) {
            double distance = 0D;
            int steps = 0;
            int current = source;
            while (parent[current] >= 0) {
                int next = parent[current];
                distance += StrictMath.hypot((current % width - next % width) * routing.sampleSpacing(),
                        (current / width - next / width) * routing.sampleSpacing());
                if (distance > routing.maximumRouteLength() || ++steps > routing.maximumRouteNodes()) {
                    distance = Integer.MAX_VALUE;
                    break;
                }
                current = next;
            }
            lengths[source] = (int) StrictMath.ceil(distance);
        }
        return lengths;
    }

    private static HydrologyPlannerSettings.Routing routing(int spacing, int maximumLength) {
        return new HydrologyPlannerSettings.Routing(spacing * 8, spacing, 1_000_000, maximumLength,
                0, 0, 1D, 1D, 1D, 1D, 1D, 0);
    }
}
