package art.arcane.iris.generation.stage;

import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IrisPostModifierHydrologyTest {
    @Test
    public void columnsTouchingARiverFootprintSkipThePostPasses() {
        for (int index : new int[] {4, 3, 5, 1, 7}) {
            boolean[] rivers = new boolean[9];
            rivers[index] = true;
            assertTrue(IrisPostModifier.skipsHydrologyColumn(rivers, 4, 3));
        }
    }

    @Test
    public void columnsAwayFromRiversKeepThePostPasses() {
        boolean[] rivers = new boolean[9];
        rivers[0] = true;
        rivers[2] = true;
        rivers[6] = true;
        rivers[8] = true;
        assertFalse(IrisPostModifier.skipsHydrologyColumn(rivers, 4, 3));
    }

    @Test
    public void paddedPlaneSamplesEachRequiredCoordinateOnce() {
        Map<Long, Integer> samples = new HashMap<>();
        ProceduralStream<Double> stream = ProceduralStream.ofDouble((x, z) -> {
            long key = ((long) (int) x << 32) | ((int) z & 0xffffffffL);
            samples.merge(key, 1, Integer::sum);
            return weight((int) x, (int) z);
        });
        boolean[] rivers = IrisPostModifier.riverPlane(stream, -32, 48, 16, 16);
        assertEquals(320, samples.size());
        for (int count : samples.values()) {
            assertEquals(1, count);
        }
        assertPlaneMatchesPointSampling(rivers, -32, 48, 16, 16);
    }

    @Test
    public void rectangularPlanesPreservePositiveWeightRules() {
        ProceduralStream<Double> stream = ProceduralStream.ofDouble((x, z) -> weight((int) x, (int) z));
        for (int width : new int[] {1, 3, 16}) {
            for (int depth : new int[] {1, 5, 16}) {
                boolean[] rivers = IrisPostModifier.riverPlane(stream, -19, -37, width, depth);
                assertPlaneMatchesPointSampling(rivers, -19, -37, width, depth);
            }
        }
    }

    private static void assertPlaneMatchesPointSampling(boolean[] rivers, int x, int z, int width, int depth) {
        int planeWidth = width + 2;
        for (int localX = 0; localX < width; localX++) {
            for (int localZ = 0; localZ < depth; localZ++) {
                int worldX = x + localX;
                int worldZ = z + localZ;
                boolean expected = weight(worldX, worldZ) > 0D || weight(worldX + 1, worldZ) > 0D
                        || weight(worldX - 1, worldZ) > 0D || weight(worldX, worldZ + 1) > 0D
                        || weight(worldX, worldZ - 1) > 0D;
                assertEquals(expected, IrisPostModifier.skipsHydrologyColumn(rivers,
                        (localZ + 1) * planeWidth + localX + 1, planeWidth));
            }
        }
    }

    private static double weight(int x, int z) {
        return switch (Math.floorMod(x * 31 + z * 17, 8)) {
            case 0 -> Double.NaN;
            case 1 -> -1D;
            case 2 -> 0D;
            case 3 -> -0D;
            case 4 -> Double.MIN_VALUE;
            case 5 -> 1D;
            case 6 -> Double.POSITIVE_INFINITY;
            default -> Double.NEGATIVE_INFINITY;
        };
    }
}
