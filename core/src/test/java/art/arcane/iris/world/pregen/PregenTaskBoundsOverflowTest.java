package art.arcane.iris.world.pregen;

import art.arcane.volmlib.util.math.Position2;
import org.junit.Test;

import java.util.LinkedHashSet;
import java.util.concurrent.atomic.AtomicLong;

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
        assertEquals(58593, bounds[2]);
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
    public void thousandBlockRadiusVisitsOnlyTheSixteenOccupiedRegions() {
        PregenTask task = PregenTask.builder().radiusX(1000).radiusZ(1000).build();

        assertArrayEqualsMessage(new int[]{-63, -63, 63, 63}, task.chunkBounds());
        assertArrayEqualsMessage(new int[]{-2, -2, 1, 1}, task.regionBounds());
        assertEquals(16_129L, task.chunkCount());
        assertRegionTraversal(task);
    }

    @Test
    public void asymmetricRegionTraversalRetainsEveryChunkAndStartsAtTheCenter() {
        int[][] areas = {
                {0, 0, 1, 1},
                {497, -497, 1, 17},
                {-513, 511, 32, 17},
                {511, -513, 1000, 32},
                {512, -512, 512, 256},
                {0, 0, 1024, 512},
                {PregenTask.MAX_WORLD_BLOCK - 32, 0, 32, 1},
                {0, -PregenTask.MAX_WORLD_BLOCK + 32, 1, 32}
        };
        for (int[] area : areas) {
            PregenTask task = PregenTask.builder()
                    .center(new Position2(area[0], area[1]))
                    .radiusX(area[2])
                    .radiusZ(area[3])
                    .build();
            assertRegionTraversal(task);
        }
    }

    @Test
    public void clampSaturatesInsteadOfWrapping() {
        assertEquals(Integer.MAX_VALUE, PregenTask.clampBlock((long) Integer.MAX_VALUE + 1L));
        assertEquals(Integer.MIN_VALUE, PregenTask.clampBlock((long) Integer.MIN_VALUE - 1L));
        assertEquals(0, PregenTask.clampBlock(0L));
        assertEquals(-7, PregenTask.clampBlock(-7L));
    }

    private static void assertRegionTraversal(PregenTask task) {
        int[] chunks = task.chunkBounds();
        LinkedHashSet<Position2> expected = new LinkedHashSet<>();
        for (int regionX = chunks[0] >> 5; regionX <= chunks[2] >> 5; regionX++) {
            for (int regionZ = chunks[1] >> 5; regionZ <= chunks[3] >> 5; regionZ++) {
                expected.add(new Position2(regionX, regionZ));
            }
        }
        LinkedHashSet<Position2> actual = new LinkedHashSet<>();
        AtomicLong visitedChunks = new AtomicLong();
        task.iterateRegions((regionX, regionZ) -> {
            assertTrue(actual.add(new Position2(regionX, regionZ)));
            long before = visitedChunks.get();
            task.iterateChunks(regionX, regionZ, (chunkX, chunkZ) -> visitedChunks.incrementAndGet());
            assertTrue(visitedChunks.get() > before);
        });

        assertEquals(expected, actual);
        assertEquals(new Position2(task.getCenter().getX() >> 9, task.getCenter().getZ() >> 9), actual.getFirst());
        assertEquals(task.chunkCount(), visitedChunks.get());
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
