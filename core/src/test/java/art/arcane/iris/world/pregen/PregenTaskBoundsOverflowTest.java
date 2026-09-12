package art.arcane.iris.world.pregen;

import art.arcane.volmlib.util.math.Position2;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PregenTaskBoundsOverflowTest {
    @Test
    public void farPositiveCenterIsRejectedBeforeTraversal() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> PregenTask.builder()
                        .center(new Position2(Integer.MAX_VALUE - 16, Integer.MAX_VALUE - 16))
                        .radiusX(4096)
                        .radiusZ(4096)
                        .build());

        assertTrue(failure.getMessage().contains("coordinate limit"));
    }

    @Test
    public void farNegativeCenterIsRejectedBeforeTraversal() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> PregenTask.builder()
                        .center(new Position2(Integer.MIN_VALUE + 16, Integer.MIN_VALUE + 16))
                        .radiusX(4096)
                        .radiusZ(4096)
                        .build());

        assertTrue(failure.getMessage().contains("coordinate limit"));
    }

    @Test
    public void hugeRadiusAroundOriginIsRejected() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> PregenTask.builder()
                        .center(new Position2(0, 0))
                        .radiusX(Integer.MAX_VALUE)
                        .radiusZ(Integer.MAX_VALUE)
                        .build());

        assertTrue(failure.getMessage().contains("radius 2147483647x2147483647"));
    }

    @Test
    public void exactWorldLimitRadiusIsAccepted() {
        PregenTask task = PregenTask.builder()
                .center(new Position2(0, 0))
                .radiusX(PregenTask.MAX_WORLD_BLOCK)
                .radiusZ(PregenTask.MAX_WORLD_BLOCK)
                .build();

        int[] bounds = task.regionBounds();

        assertEquals(-58594, bounds[0]);
        assertEquals(58594, bounds[2]);
    }

    @Test
    public void oneBlockPastWorldLimitIsRejectedOnEveryEdge() {
        int limit = PregenTask.MAX_WORLD_BLOCK;

        assertWorldLimitFailure(limit, 0, 1, 1);
        assertWorldLimitFailure(-limit, 0, 1, 1);
        assertWorldLimitFailure(0, limit, 1, 1);
        assertWorldLimitFailure(0, -limit, 1, 1);
    }

    @Test
    public void offsetAreaEndingExactlyAtWorldLimitIsAccepted() {
        int radius = 1000;
        PregenTask task = PregenTask.builder()
                .center(new Position2(PregenTask.MAX_WORLD_BLOCK - radius, -PregenTask.MAX_WORLD_BLOCK + radius))
                .radiusX(radius)
                .radiusZ(radius)
                .build();

        int[] bounds = task.regionBounds();
        assertTrue(bounds[0] <= bounds[2]);
        assertTrue(bounds[1] <= bounds[3]);
    }

    @Test
    public void ordinaryBoundsAreUnchanged() {
        PregenTask task = PregenTask.builder()
                .center(new Position2(0, 0))
                .radiusX(1024)
                .radiusZ(512)
                .build();

        assertArrayEqualsMessage(new int[]{-2, -1, 2, 1}, task.regionBounds());
    }

    @Test
    public void clampSaturatesInsteadOfWrapping() {
        assertEquals(Integer.MAX_VALUE, PregenTask.clampBlock((long) Integer.MAX_VALUE + 1L));
        assertEquals(Integer.MIN_VALUE, PregenTask.clampBlock((long) Integer.MIN_VALUE - 1L));
        assertEquals(0, PregenTask.clampBlock(0L));
        assertEquals(-7, PregenTask.clampBlock(-7L));
    }

    private static void assertArrayEqualsMessage(int[] expected, int[] actual) {
        assertEquals(expected.length, actual.length);
        for (int index = 0; index < expected.length; index++) {
            assertEquals("bounds[" + index + "]", expected[index], actual[index]);
        }
    }

    private static void assertWorldLimitFailure(int centerX, int centerZ, int radiusX, int radiusZ) {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> PregenTask.builder()
                        .center(new Position2(centerX, centerZ))
                        .radiusX(radiusX)
                        .radiusZ(radiusZ)
                        .build());
        assertTrue(failure.getMessage().contains("coordinate limit"));
    }
}
