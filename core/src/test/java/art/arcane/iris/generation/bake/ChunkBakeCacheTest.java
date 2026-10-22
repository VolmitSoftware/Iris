package art.arcane.iris.generation.bake;

import art.arcane.iris.generation.stream.ProvisionalSampling;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
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

    @Test
    public void rememberedPlanesAnswerOnlyForTheirOwnChunkAndOnlyUntilClear() {
        ChunkBakeCache cache = new ChunkBakeCache(new ConstantSource(), 256);
        int[][] chunks = {{0, 0}, {1, 0}, {0, 1}, {4, 0}, {0, 4}, {-4, -4}, {3, 3}, {7, 7}};
        for (int[] chunk : chunks) {
            cache.complete(BakePlane.BASE_HEIGHT, chunk[0], chunk[1]);
        }
        for (int round = 0; round < 2; round++) {
            for (int[] chunk : chunks) {
                ChunkPlanes.Plane plane = cache.planeIfPresent(BakePlane.BASE_HEIGHT, chunk[0], chunk[1]);
                assertEquals(chunk[0] * 1_000D + chunk[1] + 5D, plane.doubleAt(5), 0D);
            }
        }
        assertNull(cache.planeIfPresent(BakePlane.BASE_HEIGHT, 8, 8));

        cache.clear();

        for (int[] chunk : chunks) {
            assertNull(cache.planeIfPresent(BakePlane.BASE_HEIGHT, chunk[0], chunk[1]));
        }
    }

    @Test
    public void fillColumnsComputesOnlyTheMissingRequestedColumnsAndPublishesThem() {
        RecordingSource source = new RecordingSource();
        ChunkBakeCache cache = new ChunkBakeCache(source, 64);
        long[] strip = columns(13, 0, 3, 16);
        double[] target = new double[256];

        cache.fillColumns(BakePlane.NATURAL_HEIGHT, 2, -1, strip, target);

        assertEquals(1, source.calls);
        assertArrayEquals(strip, source.lastMissing);
        ChunkPlanes.Plane plane = cache.planeIfPresent(BakePlane.NATURAL_HEIGHT, 2, -1);
        for (int index = 0; index < 256; index++) {
            boolean requested = (strip[index >>> 6] & (1L << (index & 63))) != 0L;
            assertEquals(requested, plane.ready(index));
            if (requested) {
                assertEquals(RecordingSource.value(2, -1, index), target[index], 0D);
            }
        }

        long[] corner = columns(13, 13, 3, 3);
        long[] wider = columns(10, 13, 6, 3);
        double[] second = new double[256];
        cache.fillColumns(BakePlane.NATURAL_HEIGHT, 2, -1, wider, second);

        assertEquals(2, source.calls);
        long[] expected = new long[4];
        for (int word = 0; word < 4; word++) {
            expected[word] = wider[word] & ~corner[word];
        }
        assertArrayEquals(expected, source.lastMissing);
        for (int index = 0; index < 256; index++) {
            if ((wider[index >>> 6] & (1L << (index & 63))) != 0L) {
                assertEquals(RecordingSource.value(2, -1, index), second[index], 0D);
            }
        }

        cache.fillColumns(BakePlane.NATURAL_HEIGHT, 2, -1, corner, new double[256]);
        assertEquals(2, source.calls);
        assertArrayEquals(columns(13, 0, 3, 16), strip);
        assertArrayEquals(columns(10, 13, 6, 3), wider);
    }

    @Test
    public void fillColumnsReadsCompletedPlanesWithoutRecomputing() {
        RecordingSource source = new RecordingSource();
        ChunkBakeCache cache = new ChunkBakeCache(source, 64);
        cache.complete(BakePlane.REGION, -4, 7);
        Object[] target = new Object[256];

        cache.fillColumns(BakePlane.REGION, -4, 7, columns(0, 0, 16, 3), target);

        assertEquals(1, source.calls);
        assertEquals(RecordingSource.label(-4, 7, 37), target[37]);
        assertNull(target[3 * 16]);
    }

    @Test
    public void fillColumnsHandsBackProvisionalValuesWithoutPublishingThem() {
        RecordingSource source = new RecordingSource();
        source.provisional = true;
        ChunkBakeCache cache = new ChunkBakeCache(source, 64);
        long[] block = columns(4, 4, 2, 2);
        double[] target = new double[256];

        cache.fillColumns(BakePlane.NATURAL_HEIGHT, 0, 0, block, target);

        int index = (4 << 4) | 4;
        assertEquals(RecordingSource.value(0, 0, index), target[index], 0D);
        assertTrue(cache.planeIfPresent(BakePlane.NATURAL_HEIGHT, 0, 0) == null
                || !cache.planeIfPresent(BakePlane.NATURAL_HEIGHT, 0, 0).ready(index));

        source.provisional = false;
        cache.fillColumns(BakePlane.NATURAL_HEIGHT, 0, 0, block, new double[256]);
        assertEquals(2, source.calls);
        assertTrue(cache.planeIfPresent(BakePlane.NATURAL_HEIGHT, 0, 0).ready(index));
    }

    private static long[] columns(int x0, int z0, int width, int depth) {
        long[] mask = new long[4];
        for (int x = x0; x < x0 + width; x++) {
            for (int z = z0; z < z0 + depth; z++) {
                int index = (z << 4) | x;
                mask[index >>> 6] |= 1L << (index & 63);
            }
        }
        return mask;
    }

    private static final class RecordingSource implements PlaneSource {
        private int calls;
        private long[] lastMissing;
        private boolean provisional;

        private static double value(int chunkX, int chunkZ, int index) {
            return chunkX * 10_000D + chunkZ * 1_000D + index + 0.25D;
        }

        private static String label(int chunkX, int chunkZ, int index) {
            return chunkX + ":" + chunkZ + ":" + index;
        }

        @Override
        public void fill(BakePlane plane, int chunkX, int chunkZ, long[] missing, Object values) {
            calls++;
            lastMissing = missing.clone();
            if (provisional) {
                ProvisionalSampling.mark();
            }
            for (int index = 0; index < 256; index++) {
                if ((missing[index >>> 6] & (1L << (index & 63))) == 0L) {
                    continue;
                }
                if (values instanceof double[] doubles) {
                    doubles[index] = value(chunkX, chunkZ, index);
                } else {
                    ((Object[]) values)[index] = label(chunkX, chunkZ, index);
                }
            }
        }

        @Override
        public boolean mainThread() {
            return false;
        }
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
