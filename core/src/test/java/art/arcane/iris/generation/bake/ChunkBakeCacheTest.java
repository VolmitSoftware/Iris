package art.arcane.iris.generation.bake;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ChunkBakeCacheTest {
    private static final long MEGABYTE = 1L << 20;
    private static final long GIGABYTE = 1L << 30;

    @Test
    public void capacityChargesTheGeneratedChunkFootprintAgainstAHeapShare() {
        assertEquals(5_041, ChunkBakeCache.capacityFor(2L * GIGABYTE));
        assertEquals(10_082, ChunkBakeCache.capacityFor(4L * GIGABYTE));
        assertEquals(15_123, ChunkBakeCache.capacityFor(6L * GIGABYTE));
        for (long heap = 512L * MEGABYTE; heap <= 64L * GIGABYTE; heap += 256L * MEGABYTE) {
            int capacity = ChunkBakeCache.capacityFor(heap);
            if (capacity > ChunkBakeCache.MINIMUM_CAPACITY) {
                assertTrue("heap " + heap, capacity * ChunkBakeCache.BYTES_PER_ENTRY <= heap / ChunkBakeCache.HEAP_SHARE);
            }
        }
    }

    @Test
    public void capacityStaysBetweenTheFloorAndTheCeilingAndGrowsWithTheHeap() {
        assertEquals(ChunkBakeCache.MINIMUM_CAPACITY, ChunkBakeCache.capacityFor(0L));
        assertEquals(ChunkBakeCache.MINIMUM_CAPACITY, ChunkBakeCache.capacityFor(512L * MEGABYTE));
        assertEquals(ChunkBakeCache.MAXIMUM_CAPACITY, ChunkBakeCache.capacityFor(64L * GIGABYTE));
        assertEquals(ChunkBakeCache.MAXIMUM_CAPACITY, ChunkBakeCache.capacityFor(Long.MAX_VALUE));
        int previous = 0;
        for (long heap = 0L; heap <= 32L * GIGABYTE; heap += 128L * MEGABYTE) {
            int capacity = ChunkBakeCache.capacityFor(heap);
            assertTrue(capacity >= previous);
            previous = capacity;
        }
    }

    @Test
    public void clearRetiresPlanesThatThreadsStillRemember() {
        ChunkBakeCache cache = new ChunkBakeCache(new ConstantSource(), 64);
        double[] before = (double[]) cache.complete(BakePlane.BASE_HEIGHT, 3, -5);
        ChunkPlanes.Plane remembered = cache.planeIfPresent(BakePlane.BASE_HEIGHT, 3, -5);
        assertNotNull(remembered);
        assertSame(before, cache.complete(BakePlane.BASE_HEIGHT, 3, -5));

        cache.clear();

        assertNull(cache.planeIfPresent(BakePlane.BASE_HEIGHT, 3, -5));
        double[] after = (double[]) cache.complete(BakePlane.BASE_HEIGHT, 3, -5);
        assertNotSame(before, after);
        assertEquals(before[17], after[17], 0D);
        assertNotSame(remembered, cache.planeIfPresent(BakePlane.BASE_HEIGHT, 3, -5));
    }

    @Test
    public void releasedEntriesHoldNoPlanes() {
        ChunkPlanes planes = new ChunkPlanes(1, 1);
        ConstantSource source = new ConstantSource();
        for (BakePlane kind : BakePlane.PLANES) {
            planes.complete(kind, source);
            assertNotNull(planes.planeIfPresent(kind));
        }

        planes.release();

        for (BakePlane kind : BakePlane.PLANES) {
            assertNull(planes.planeIfPresent(kind));
        }
    }

    @Test
    public void clearLeavesAnEmptyCacheOfTheSameCapacity() {
        ChunkBakeCache cache = new ChunkBakeCache(new ConstantSource(), 64);
        for (int chunk = 0; chunk < 8; chunk++) {
            cache.complete(BakePlane.HEIGHT, chunk, -chunk);
        }
        assertEquals(8L, cache.census()[BakePlane.PLANES.length]);

        cache.clear();

        assertEquals(0L, cache.census()[BakePlane.PLANES.length]);
        assertEquals(64, cache.capacity());
    }

    private static final class ConstantSource implements PlaneSource {
        @Override
        public void fill(BakePlane plane, int chunkX, int chunkZ, long[] missing, Object values) {
            if (values instanceof double[] doubles) {
                for (int index = 0; index < 256; index++) {
                    doubles[index] = chunkX * 1_000D + chunkZ + index;
                }
            }
        }

        @Override
        public boolean mainThread() {
            return false;
        }
    }
}
