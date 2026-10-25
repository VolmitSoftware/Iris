package art.arcane.iris.generation.hydrology.grid;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HeightLatticeTest {
    private static final int WINDOW = 16;
    private static final int AREA = 3 * WINDOW;
    private static final int DISTINCT_ACROSS_NINE = 25 * WINDOW * WINDOW;

    @Test
    public void samplesEachCellOnceAcrossNineOverlappingWindows() {
        CountingSource source = new CountingSource();
        HeightLattice lattice = new HeightLattice(source, 8, false, 1 << 16);
        for (int wz = -1; wz <= 1; wz++) {
            for (int wx = -1; wx <= 1; wx++) {
                fillWindow(lattice, wx, wz, 8, false);
            }
        }
        assertEquals(DISTINCT_ACROSS_NINE, source.distinctPoints());
        assertEquals(DISTINCT_ACROSS_NINE, source.samples());
        assertEquals(9, source.calls());
    }

    @Test
    public void concurrentFillsSampleEachCellOnce() throws Exception {
        CountingSource source = new CountingSource();
        HeightLattice lattice = new HeightLattice(source, 8, false, 1 << 16);
        int threads = 8;
        CyclicBarrier start = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Void>> results = new ArrayList<>();
            for (int thread = 0; thread < threads; thread++) {
                long seed = thread;
                Callable<Void> task = () -> fillAllWindowsShuffled(lattice, start, seed);
                results.add(pool.submit(task));
            }
            for (Future<Void> result : results) {
                result.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(DISTINCT_ACROSS_NINE, source.distinctPoints());
        assertEquals(DISTINCT_ACROSS_NINE, source.samples());
    }

    @Test
    public void returnsBitIdenticalValues() {
        HeightLattice full = new HeightLattice(new CountingSource(), 8, false, 1 << 16);
        HeightLattice base = new HeightLattice(new CountingSource(), 64, true, 1 << 16);
        HeightLattice fine = new HeightLattice(new CountingSource(), 4, false, 1 << 16);
        for (int wz = -1; wz <= 1; wz++) {
            for (int wx = -1; wx <= 1; wx++) {
                fillWindow(full, wx, wz, 8, false);
                fillWindow(base, wx, wz, 64, true);
                fillWindow(fine, wx, wz, 4, false);
            }
        }
        assertFill(full, 8, false, -37, 11, 23, 9);
        assertFill(base, 64, true, -37, 11, 23, 9);
        assertFill(fine, 4, false, -37, 11, 23, 9);
    }

    @Test
    public void evictionKeepsValuesIdenticalAndBoundsTheCache() {
        CountingSource source = new CountingSource();
        int capacity = 64;
        HeightLattice lattice = new HeightLattice(source, 4, true, capacity);
        for (int window = 0; window < 20; window++) {
            assertFill(lattice, 4, true, window * WINDOW, 0, WINDOW, WINDOW);
        }
        int before = source.samples();
        assertFill(lattice, 4, true, 0, 0, WINDOW, WINDOW);
        assertTrue(source.samples() - before >= WINDOW * WINDOW - capacity);
    }

    @Test
    public void failedSamplesAreNotCachedAndWaitersRetry() throws Exception {
        assertWaiterRetriesAfterOwnerFailure(new IllegalStateException("Synthetic sample failure"));
    }

    @Test
    public void cancelledOwnerLetsWaitersRetry() throws Exception {
        assertWaiterRetriesAfterOwnerFailure(new CancellationException("Synthetic owner cancellation"));
    }

    @Test
    public void evictionNeverDropsPendingCells() throws Exception {
        GatedSource source = new GatedSource(null);
        HeightLattice lattice = new HeightLattice(source, 8, false, 16);
        AtomicReference<Throwable> ownerFailure = new AtomicReference<>();
        AtomicReference<Throwable> readerFailure = new AtomicReference<>();
        Thread owner = new Thread(() -> ownerFailure.set(failureOf(() -> assertFill(lattice, 8, false, 0, 0, WINDOW, WINDOW))));
        owner.start();
        assertTrue(source.entered.await(10, TimeUnit.SECONDS));
        assertFill(lattice, 8, false, 100, 0, WINDOW, WINDOW);
        Thread reader = new Thread(() -> readerFailure.set(failureOf(() -> assertFill(lattice, 8, false, 0, 0, WINDOW, WINDOW))));
        reader.start();
        awaitParked(reader);
        source.release.countDown();
        owner.join(10_000L);
        reader.join(10_000L);
        assertFalse(owner.isAlive());
        assertFalse(reader.isAlive());
        assertNull(String.valueOf(ownerFailure.get()), ownerFailure.get());
        assertNull(String.valueOf(readerFailure.get()), readerFailure.get());
        assertEquals(2, source.calls.get());
    }

    @Test
    public void interruptedWaiterCancelsWithoutPoisoningCells() throws Exception {
        GatedSource source = new GatedSource(null);
        HeightLattice lattice = new HeightLattice(source, 8, false, 1 << 16);
        AtomicReference<Throwable> ownerFailure = new AtomicReference<>();
        AtomicReference<Throwable> waiterFailure = new AtomicReference<>();
        AtomicBoolean waiterInterrupted = new AtomicBoolean();
        Thread owner = new Thread(() -> ownerFailure.set(failureOf(() -> assertFill(lattice, 8, false, 0, 0, WINDOW, WINDOW))));
        owner.start();
        assertTrue(source.entered.await(10, TimeUnit.SECONDS));
        Thread waiter = new Thread(() -> {
            waiterFailure.set(failureOf(() -> lattice.fill(0, 0, WINDOW, WINDOW, new HydrologySampleBuffer(WINDOW * WINDOW))));
            waiterInterrupted.set(Thread.currentThread().isInterrupted());
        });
        waiter.start();
        awaitParked(waiter);
        waiter.interrupt();
        waiter.join(10_000L);
        assertFalse(waiter.isAlive());
        assertTrue(String.valueOf(waiterFailure.get()), waiterFailure.get() instanceof CancellationException);
        assertTrue(waiterInterrupted.get());
        source.release.countDown();
        owner.join(10_000L);
        assertFalse(owner.isAlive());
        assertNull(String.valueOf(ownerFailure.get()), ownerFailure.get());
        assertFill(lattice, 8, false, 0, 0, WINDOW, WINDOW);
        assertEquals(1, source.calls.get());
    }

    @Test
    public void rejectsUndersizedBuffer() {
        HeightLattice lattice = new HeightLattice(new CountingSource(), 8, false, 1 << 16);
        assertThrows(IllegalArgumentException.class, () -> lattice.fill(0, 0, 4, 4, new HydrologySampleBuffer(15)));
    }

    private static void assertWaiterRetriesAfterOwnerFailure(RuntimeException ownerSampleFailure) throws Exception {
        GatedSource source = new GatedSource(ownerSampleFailure);
        HeightLattice lattice = new HeightLattice(source, 8, false, 1 << 16);
        AtomicReference<Throwable> ownerFailure = new AtomicReference<>();
        AtomicReference<Throwable> waiterFailure = new AtomicReference<>();
        AtomicBoolean waiterInterrupted = new AtomicBoolean();
        Thread owner = new Thread(() -> ownerFailure.set(failureOf(() -> lattice.fill(0, 0, WINDOW, WINDOW, new HydrologySampleBuffer(WINDOW * WINDOW)))));
        owner.start();
        assertTrue(source.entered.await(10, TimeUnit.SECONDS));
        Thread waiter = new Thread(() -> {
            waiterFailure.set(failureOf(() -> assertFill(lattice, 8, false, 0, 0, WINDOW, WINDOW)));
            waiterInterrupted.set(Thread.currentThread().isInterrupted());
        });
        waiter.start();
        awaitParked(waiter);
        source.release.countDown();
        owner.join(10_000L);
        waiter.join(10_000L);
        assertFalse(owner.isAlive());
        assertFalse(waiter.isAlive());
        assertSame(ownerSampleFailure, ownerFailure.get());
        assertNull(String.valueOf(waiterFailure.get()), waiterFailure.get());
        assertFalse(waiterInterrupted.get());
        assertEquals(2, source.calls.get());
        assertFill(lattice, 8, false, 0, 0, WINDOW, WINDOW);
        assertEquals(2, source.calls.get());
        assertEquals(WINDOW * WINDOW, source.delegate.samples());
    }

    private static Void fillAllWindowsShuffled(HeightLattice lattice, CyclicBarrier start, long seed) throws Exception {
        List<int[]> windows = new ArrayList<>();
        for (int wz = -1; wz <= 1; wz++) {
            for (int wx = -1; wx <= 1; wx++) {
                if (wx != 0 || wz != 0) {
                    windows.add(new int[]{wx, wz});
                }
            }
        }
        Collections.shuffle(windows, new Random(seed));
        start.await(30, TimeUnit.SECONDS);
        fillWindow(lattice, 0, 0, 8, false);
        for (int[] window : windows) {
            fillWindow(lattice, window[0], window[1], 8, false);
        }
        return null;
    }

    @Test
    public void stridedFillReadsTheDenseLatticeNodes() {
        CountingSource source = new CountingSource();
        HeightLattice lattice = new HeightLattice(source, 4, false, 1 << 16);
        HydrologySampleBuffer strided = new HydrologySampleBuffer(5 * 6);
        lattice.fill(-8, 4, 5, 6, 4, strided);
        assertEquals(30, source.samples());
        HydrologySampleBuffer dense = new HydrologySampleBuffer(17 * 21);
        lattice.fill(-8, 4, 17, 21, dense);
        assertEquals(17 * 21, source.samples());
        for (int j = 0; j < 6; j++) {
            for (int i = 0; i < 5; i++) {
                int denseIndex = (j * 4) * 17 + i * 4;
                int stridedIndex = j * 5 + i;
                assertEquals(Float.floatToIntBits(dense.height()[denseIndex]), Float.floatToIntBits(strided.height()[stridedIndex]));
                assertEquals(dense.fluidProfile()[denseIndex], strided.fluidProfile()[stridedIndex]);
            }
        }
    }

    @Test
    public void scatteredFillReadsEachNodeOnce() {
        CountingSource source = new CountingSource();
        HeightLattice lattice = new HeightLattice(source, 4, false, 1 << 16);
        int[] cellXs = {3, -7, 3, 12, -7, 0};
        int[] cellZs = {5, 2, 5, -1, 2, 0};
        HydrologySampleBuffer scattered = new HydrologySampleBuffer(cellXs.length);
        lattice.fill(cellXs, cellZs, scattered);
        assertEquals(4, source.samples());
        assertEquals(1, source.calls());
        for (int index = 0; index < cellXs.length; index++) {
            HydrologySampleBuffer single = new HydrologySampleBuffer(1);
            lattice.fill(cellXs[index], cellZs[index], 1, 1, single);
            assertEquals(Float.floatToIntBits(single.height()[0]), Float.floatToIntBits(scattered.height()[index]));
            assertEquals(single.ocean()[0], scattered.ocean()[index]);
            assertEquals(single.fluidProfile()[0], scattered.fluidProfile()[index]);
        }
        assertEquals(4, source.samples());
        assertThrows(IllegalArgumentException.class, () -> lattice.fill(new int[]{1, 2}, new int[]{1}, new HydrologySampleBuffer(2)));
        assertThrows(IllegalArgumentException.class, () -> lattice.fill(cellXs, cellZs, new HydrologySampleBuffer(5)));
    }

    private static void fillWindow(HeightLattice lattice, int wx, int wz, int cell, boolean baseOnly) {
        assertFill(lattice, cell, baseOnly, (wx - 1) * WINDOW, (wz - 1) * WINDOW, AREA, AREA);
    }

    private static void assertFill(HeightLattice lattice, int cell, boolean baseOnly, int cellX0, int cellZ0, int width, int depth) {
        int count = width * depth;
        HydrologySampleBuffer actual = new HydrologySampleBuffer(count);
        lattice.fill(cellX0, cellZ0, width, depth, actual);
        int[] xs = new int[count];
        int[] zs = new int[count];
        for (int dz = 0; dz < depth; dz++) {
            for (int dx = 0; dx < width; dx++) {
                xs[dz * width + dx] = (cellX0 + dx) * cell;
                zs[dz * width + dx] = (cellZ0 + dz) * cell;
            }
        }
        HydrologySampleBuffer expected = new HydrologySampleBuffer(count);
        new CountingSource().sample(xs, zs, expected, baseOnly);
        for (int index = 0; index < count; index++) {
            assertEquals(Float.floatToIntBits(expected.height()[index]), Float.floatToIntBits(actual.height()[index]));
            assertEquals(Float.floatToIntBits(expected.rainfall()[index]), Float.floatToIntBits(actual.rainfall()[index]));
            assertEquals(Float.floatToIntBits(expected.pondDensity()[index]), Float.floatToIntBits(actual.pondDensity()[index]));
            assertEquals(expected.ocean()[index], actual.ocean()[index]);
            assertEquals(expected.permission()[index], actual.permission()[index]);
            assertEquals(expected.fluidProfile()[index], actual.fluidProfile()[index]);
        }
    }

    private static void awaitParked(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (thread.getState() != Thread.State.WAITING) {
            assertTrue("Thread never parked, state " + thread.getState(), thread.isAlive() && System.nanoTime() < deadline);
            Thread.sleep(10L);
        }
    }

    private static Throwable failureOf(Runnable action) {
        try {
            action.run();
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private static long mix(int x, int z, boolean baseOnly) {
        long hash = ((long) x << 32) ^ (z & 0xFFFFFFFFL) ^ (baseOnly ? 0x9E3779B97F4A7C15L : 0L);
        hash = (hash ^ (hash >>> 30)) * 0xBF58476D1CE4E5B9L;
        hash = (hash ^ (hash >>> 27)) * 0x94D049BB133111EBL;
        return hash ^ (hash >>> 31);
    }

    private static final class CountingSource implements HydrologyTerrainSource {
        private final Set<Long> points = ConcurrentHashMap.newKeySet();
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicInteger samples = new AtomicInteger();

        @Override
        public void sample(int[] xs, int[] zs, HydrologySampleBuffer out, boolean baseOnly) {
            calls.incrementAndGet();
            samples.addAndGet(xs.length);
            for (int index = 0; index < xs.length; index++) {
                points.add(((long) xs[index] << 32) | (zs[index] & 0xFFFFFFFFL));
                long hash = mix(xs[index], zs[index], baseOnly);
                out.height()[index] = Float.intBitsToFloat(((int) hash & 0x807FFFFF) | 0x42000000);
                out.rainfall()[index] = Float.intBitsToFloat(((int) (hash >>> 23) & 0x007FFFFF) | 0x3F000000);
                out.pondDensity()[index] = Float.intBitsToFloat(((int) (hash >>> 37) & 0x007FFFFF) | 0x3F800000);
                out.ocean()[index] = ((hash >>> 62) & 1L) != 0L;
                out.permission()[index] = (byte) ((hash >>> 60) & (HydrologySampleBuffer.ALLOW_STREAMS | HydrologySampleBuffer.ALLOW_PONDS));
                out.fluidProfile()[index] = ((hash >>> 59) & 1L) == 0L ? "water" : "lava";
            }
        }

        @Override
        public int seaLevel() {
            return 63;
        }

        @Override
        public int minY() {
            return -64;
        }

        @Override
        public int maxY() {
            return 320;
        }

        int calls() {
            return calls.get();
        }

        int samples() {
            return samples.get();
        }

        int distinctPoints() {
            return points.size();
        }
    }

    private static final class GatedSource implements HydrologyTerrainSource {
        private final RuntimeException firstCallFailure;
        private final CountingSource delegate = new CountingSource();
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicInteger calls = new AtomicInteger();

        private GatedSource(RuntimeException firstCallFailure) {
            this.firstCallFailure = firstCallFailure;
        }

        @Override
        public void sample(int[] xs, int[] zs, HydrologySampleBuffer out, boolean baseOnly) {
            if (calls.getAndIncrement() == 0) {
                entered.countDown();
                awaitRelease();
                if (firstCallFailure != null) {
                    throw firstCallFailure;
                }
            }
            delegate.sample(xs, zs, out, baseOnly);
        }

        private void awaitRelease() {
            try {
                if (!release.await(30, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Release timed out");
                }
            } catch (InterruptedException interruption) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interruption);
            }
        }

        @Override
        public int seaLevel() {
            return delegate.seaLevel();
        }

        @Override
        public int minY() {
            return delegate.minY();
        }

        @Override
        public int maxY() {
            return delegate.maxY();
        }
    }
}
