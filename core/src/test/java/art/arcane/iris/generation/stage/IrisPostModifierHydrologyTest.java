package art.arcane.iris.generation.stage;

import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.stream.interpolation.Interpolated;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IrisPostModifierHydrologyTest {
    private static final WaterRole[] ROLES = WaterRole.values();

    @Test
    public void columnsTouchingAWaterFeatureSkipThePostPasses() {
        for (int index : new int[] {4, 3, 5, 1, 7}) {
            boolean[] water = new boolean[9];
            water[index] = true;
            assertTrue(IrisPostModifier.skipsHydrologyColumn(water, 4, 3));
        }
    }

    @Test
    public void columnsAwayFromWaterKeepThePostPasses() {
        boolean[] water = new boolean[9];
        water[0] = true;
        water[2] = true;
        water[6] = true;
        water[8] = true;
        assertFalse(IrisPostModifier.skipsHydrologyColumn(water, 4, 3));
    }

    @Test
    public void everyRoleOtherThanNoneSkipsSmoothing() {
        for (WaterRole role : ROLES) {
            ProceduralStream<WaterRole> stream = roles((x, z) -> x == 0 && z == 0 ? role : WaterRole.NONE);
            boolean[] water = IrisPostModifier.waterPlane(stream, 0, 0, 1, 1);
            assertEquals(role.name(), role != WaterRole.NONE, IrisPostModifier.skipsHydrologyColumn(water, 4, 3));
        }
    }

    @Test
    public void paddedPlaneSamplesEachRequiredCoordinateOnce() {
        Map<Long, Integer> samples = new HashMap<>();
        ProceduralStream<WaterRole> stream = roles((x, z) -> {
            long key = ((long) x << 32) | (z & 0xffffffffL);
            samples.merge(key, 1, Integer::sum);
            return role(x, z);
        });
        boolean[] water = IrisPostModifier.waterPlane(stream, -32, 48, 16, 16);
        assertEquals(320, samples.size());
        for (int count : samples.values()) {
            assertEquals(1, count);
        }
        assertPlaneMatchesPointSampling(water, -32, 48, 16, 16);
    }

    @Test
    public void rectangularPlanesKeepTheRoleRule() {
        ProceduralStream<WaterRole> stream = roles(IrisPostModifierHydrologyTest::role);
        for (int width : new int[] {1, 3, 16}) {
            for (int depth : new int[] {1, 5, 16}) {
                boolean[] water = IrisPostModifier.waterPlane(stream, -19, -37, width, depth);
                assertPlaneMatchesPointSampling(water, -19, -37, width, depth);
            }
        }
    }

    private static void assertPlaneMatchesPointSampling(boolean[] water, int x, int z, int width, int depth) {
        int planeWidth = width + 2;
        for (int localX = 0; localX < width; localX++) {
            for (int localZ = 0; localZ < depth; localZ++) {
                int worldX = x + localX;
                int worldZ = z + localZ;
                boolean expected = owned(worldX, worldZ) || owned(worldX + 1, worldZ) || owned(worldX - 1, worldZ)
                        || owned(worldX, worldZ + 1) || owned(worldX, worldZ - 1);
                assertEquals(expected, IrisPostModifier.skipsHydrologyColumn(water,
                        (localZ + 1) * planeWidth + localX + 1, planeWidth));
            }
        }
    }

    private static boolean owned(int x, int z) {
        return role(x, z) != WaterRole.NONE;
    }

    private static WaterRole role(int x, int z) {
        int pick = Math.floorMod(x * 31 + z * 17, ROLES.length * 3);
        return pick < ROLES.length ? ROLES[pick] : WaterRole.NONE;
    }

    private static ProceduralStream<WaterRole> roles(RoleSource source) {
        return ProceduralStream.of((x, z) -> source.role((int) Math.floor(x), (int) Math.floor(z)),
                Interpolated.of(value -> 0D, value -> WaterRole.NONE));
    }

    @FunctionalInterface
    private interface RoleSource {
        WaterRole role(int x, int z);
    }
}
