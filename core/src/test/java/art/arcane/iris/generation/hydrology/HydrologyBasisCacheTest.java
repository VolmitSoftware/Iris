package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.stream.ProvisionalSampling;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HydrologyBasisCacheTest {
    @Test
    public void batchLookupPreservesNullDuplicatesAndEviction() {
        CountingSampler sampler = new CountingSampler();
        HydrologyBasisCache cache = new HydrologyBasisCache(1);
        long key = RiverFootprint.pack(Integer.MIN_VALUE, Integer.MAX_VALUE);
        assertArrayEquals(new HydrologyTerrainSample[2],
                cache.sampleWithoutSlopeBatch(new long[]{key, key}, 2, sampler));
        assertNull(cache.sampleWithoutSlope(Integer.MIN_VALUE, Integer.MAX_VALUE, sampler));
        assertEquals(1, sampler.slopeFreeCalls.get());
        long later = RiverFootprint.pack(73, -17);
        assertEquals(87, cache.sampleWithoutSlopeBatch(new long[]{later}, 1, sampler)[0].naturalHeight());
        assertNull(cache.sampleWithoutSlopeBatch(new long[]{key}, 1, sampler)[0]);
        assertEquals(3, sampler.slopeFreeCalls.get());
    }

    @Test
    public void batchOwnershipKeepsProviderOrderAcrossStripes() {
        HydrologyBasisCache cache = new HydrologyBasisCache();
        CountingSampler sampler = new CountingSampler() {
            @Override
            public HydrologyTerrainSample[] sampleBasisWithoutSlopeBatch(long[] coordinates, int count) {
                assertArrayEquals(new long[]{RiverFootprint.pack(7, 0), RiverFootprint.pack(-9, 0),
                        RiverFootprint.pack(-17, 0)}, Arrays.copyOf(coordinates, count));
                return super.sampleBasisWithoutSlopeBatch(coordinates, count);
            }

            @Override
            public HydrologyTerrainSample sampleBasisWithoutSlope(int x, int z) {
                return HydrologyTerrainSample.openLand(63 + x, 0D, "land");
            }
        };
        HydrologyTerrainSample seeded = cache.sampleWithoutSlope(4, 0, sampler);
        HydrologyTerrainSample[] samples = cache.sampleWithoutSlopeBatch(new long[]{
                RiverFootprint.pack(7, 0), RiverFootprint.pack(-9, 0), RiverFootprint.pack(7, 0),
                RiverFootprint.pack(4, 0), RiverFootprint.pack(-17, 0), RiverFootprint.pack(4, 0)}, 6, sampler);
        assertEquals(70, samples[0].naturalHeight());
        assertEquals(54, samples[1].naturalHeight());
        assertSame(samples[0], samples[2]);
        assertSame(seeded, samples[3]);
        assertEquals(46, samples[4].naturalHeight());
        assertSame(seeded, samples[5]);
    }

    @Test(timeout = 10_000)
    public void overlappingBatchesDispatchOnlyTheirOwnedCoordinates() throws Exception {
        HydrologyBasisCache cache = new HydrologyBasisCache();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger batchCalls = new AtomicInteger();
        CountingSampler sampler = new CountingSampler() {
            @Override
            public HydrologyTerrainSample[] sampleBasisWithoutSlopeBatch(long[] coordinates, int count) {
                if (batchCalls.getAndIncrement() == 0) {
                    assertEquals(2, count);
                    firstStarted.countDown();
                } else {
                    assertEquals(1, count);
                    assertEquals(RiverFootprint.pack(3, 0), coordinates[0]);
                    secondStarted.countDown();
                }
                await(release);
                return super.sampleBasisWithoutSlopeBatch(coordinates, count);
            }
        };
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<HydrologyTerrainSample[]> first = workers.submit(() -> cache.sampleWithoutSlopeBatch(
                    new long[]{RiverFootprint.pack(1, 0), RiverFootprint.pack(2, 0)}, 2, sampler));
            assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
            Future<HydrologyTerrainSample[]> second = workers.submit(() -> cache.sampleWithoutSlopeBatch(
                    new long[]{RiverFootprint.pack(2, 0), RiverFootprint.pack(3, 0)}, 2, sampler));
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
            release.countDown();
            assertSame(first.get(5, TimeUnit.SECONDS)[1], second.get(5, TimeUnit.SECONDS)[0]);
            assertEquals(3, sampler.slopeFreeCalls.get());
            assertEquals(2, batchCalls.get());
        } finally {
            release.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test(timeout = 10_000)
    public void pointAndBatchSharePendingSamplesWithoutHoldingStripeLocks() throws Exception {
        HydrologyBasisCache cache = new HydrologyBasisCache(1);
        CountDownLatch pointStarted = new CountDownLatch(1);
        CountDownLatch batchStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountingSampler sampler = new CountingSampler() {
            @Override
            public HydrologyTerrainSample sampleBasisWithoutSlope(int x, int z) {
                if (x == 1) {
                    pointStarted.countDown();
                    await(release);
                }
                return super.sampleBasisWithoutSlope(x, z);
            }

            @Override
            public HydrologyTerrainSample[] sampleBasisWithoutSlopeBatch(long[] coordinates, int count) {
                assertEquals(1, count);
                assertEquals(RiverFootprint.pack(2, 0), coordinates[0]);
                batchStarted.countDown();
                return super.sampleBasisWithoutSlopeBatch(coordinates, count);
            }
        };
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<HydrologyTerrainSample> point = workers.submit(() -> cache.sampleWithoutSlope(1, 0, sampler));
            assertTrue(pointStarted.await(5, TimeUnit.SECONDS));
            Future<HydrologyTerrainSample[]> batch = workers.submit(() -> cache.sampleWithoutSlopeBatch(
                    new long[]{RiverFootprint.pack(1, 0), RiverFootprint.pack(2, 0)}, 2, sampler));
            assertTrue(batchStarted.await(5, TimeUnit.SECONDS));
            release.countDown();
            assertSame(point.get(5, TimeUnit.SECONDS), batch.get(5, TimeUnit.SECONDS)[0]);
            assertEquals(2, sampler.slopeFreeCalls.get());
        } finally {
            release.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test(timeout = 10_000)
    public void nestedBatchesFinishOnSingleWorkerPool() throws Exception {
        HydrologyBasisCache cache = new HydrologyBasisCache();
        ForkJoinPool workers = new ForkJoinPool(1);
        CountingSampler sampler = new CountingSampler() {
            @Override
            public HydrologyTerrainSample[] sampleBasisWithoutSlopeBatch(long[] coordinates, int count) {
                List<HydrologyTerrainSample> values = HydrologyForkJoin.invokeAll(List.of(
                        () -> sampleBasisWithoutSlope(RiverFootprint.unpackX(coordinates[0]), 0),
                        () -> sampleBasisWithoutSlope(RiverFootprint.unpackX(coordinates[1]), 0)), workers);
                return values.toArray(HydrologyTerrainSample[]::new);
            }
        };
        try {
            workers.submit(() -> HydrologyForkJoin.invokeAll(List.of(
                    () -> cache.sampleWithoutSlopeBatch(new long[]{RiverFootprint.pack(1, 0), RiverFootprint.pack(2, 0)}, 2, sampler),
                    () -> cache.sampleWithoutSlopeBatch(new long[]{RiverFootprint.pack(2, 0), RiverFootprint.pack(1, 0)}, 2, sampler)), workers))
                    .get(5, TimeUnit.SECONDS);
            assertEquals(2, sampler.slopeFreeCalls.get());
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void failedMalformedAndRecursiveBatchesReleaseEveryReservation() {
        HydrologyBasisCache cache = new HydrologyBasisCache();
        long[] coordinates = {RiverFootprint.pack(1, 0), RiverFootprint.pack(2, 0)};
        CountingSampler failed = new CountingSampler() {
            @Override
            public HydrologyTerrainSample[] sampleBasisWithoutSlopeBatch(long[] keys, int count) {
                throw new IllegalArgumentException("failure");
            }
        };
        assertThrows(IllegalArgumentException.class, () -> cache.sampleWithoutSlopeBatch(coordinates, 2, failed));
        CountingSampler malformed = new CountingSampler() {
            @Override
            public HydrologyTerrainSample[] sampleBasisWithoutSlopeBatch(long[] keys, int count) {
                return new HydrologyTerrainSample[1];
            }
        };
        assertThrows(IllegalStateException.class, () -> cache.sampleWithoutSlopeBatch(coordinates, 2, malformed));
        CountingSampler absent = new CountingSampler() {
            @Override
            public HydrologyTerrainSample[] sampleBasisWithoutSlopeBatch(long[] keys, int count) {
                return null;
            }
        };
        assertThrows(NullPointerException.class, () -> cache.sampleWithoutSlopeBatch(coordinates, 2, absent));
        CountingSampler recursive = new CountingSampler() {
            @Override
            public HydrologyTerrainSample[] sampleBasisWithoutSlopeBatch(long[] keys, int count) {
                cache.sampleWithoutSlope(2, 0, this);
                throw new AssertionError("Recursive sampling must not wait on itself");
            }
        };
        assertThrows(IllegalStateException.class, () -> cache.sampleWithoutSlopeBatch(coordinates, 2, recursive));
        CountingSampler successful = new CountingSampler();
        assertEquals(2, cache.sampleWithoutSlopeBatch(coordinates, 2, successful).length);
        assertEquals(2, successful.slopeFreeCalls.get());
    }

    @Test(timeout = 10_000)
    public void interruptedPointWaiterReceivesBatchResultAndRestoresInterrupt() throws Exception {
        HydrologyBasisCache cache = new HydrologyBasisCache();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Thread> waiting = new AtomicReference<>();
        CountingSampler sampler = new CountingSampler() {
            @Override
            public HydrologyTerrainSample[] sampleBasisWithoutSlopeBatch(long[] coordinates, int count) {
                started.countDown();
                await(release);
                return super.sampleBasisWithoutSlopeBatch(coordinates, count);
            }
        };
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<HydrologyTerrainSample[]> batch = workers.submit(() -> cache.sampleWithoutSlopeBatch(
                    new long[]{RiverFootprint.pack(1, 0)}, 1, sampler));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            Future<HydrologyTerrainSample> point = workers.submit(() -> {
                waiting.set(Thread.currentThread());
                HydrologyTerrainSample sample = cache.sampleWithoutSlope(1, 0, sampler);
                assertTrue(Thread.interrupted());
                return sample;
            });
            awaitWaiting(waiting);
            waiting.get().interrupt();
            release.countDown();
            assertSame(batch.get(5, TimeUnit.SECONDS)[0], point.get(5, TimeUnit.SECONDS));
            assertEquals(1, sampler.slopeFreeCalls.get());
        } finally {
            release.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test(timeout = 10_000)
    public void batchFailureReachesPointWaiterAndAllowsAllCoordinatesToRetry() throws Exception {
        HydrologyBasisCache cache = new HydrologyBasisCache();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Thread> waiting = new AtomicReference<>();
        IllegalArgumentException failure = new IllegalArgumentException("retry");
        CountingSampler sampler = new CountingSampler() {
            @Override
            public HydrologyTerrainSample[] sampleBasisWithoutSlopeBatch(long[] coordinates, int count) {
                started.countDown();
                await(release);
                throw failure;
            }
        };
        long[] coordinates = {RiverFootprint.pack(1, 0), RiverFootprint.pack(2, 0)};
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<IllegalArgumentException> batch = workers.submit(() -> assertThrows(IllegalArgumentException.class,
                    () -> cache.sampleWithoutSlopeBatch(coordinates, 2, sampler)));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            Future<IllegalArgumentException> point = workers.submit(() -> {
                waiting.set(Thread.currentThread());
                return assertThrows(IllegalArgumentException.class, () -> cache.sampleWithoutSlope(2, 0, sampler));
            });
            awaitWaiting(waiting);
            release.countDown();
            assertSame(failure, batch.get(5, TimeUnit.SECONDS));
            assertSame(failure, point.get(5, TimeUnit.SECONDS));
            CountingSampler successful = new CountingSampler();
            assertEquals(2, cache.sampleWithoutSlopeBatch(coordinates, 2, successful).length);
            assertEquals(2, successful.slopeFreeCalls.get());
        } finally {
            release.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void provisionalResultsAreNotRetained() {
        HydrologyBasisCache cache = new HydrologyBasisCache();
        CountingSampler sampler = new CountingSampler() {
            @Override
            public HydrologyTerrainSample sampleBasisWithoutSlope(int x, int z) {
                ProvisionalSampling.mark();
                return super.sampleBasisWithoutSlope(x, z);
            }
        };
        long[] coordinates = {RiverFootprint.pack(1, 0), RiverFootprint.pack(2, 0)};
        cache.sampleWithoutSlopeBatch(coordinates, 2, sampler);
        cache.sampleWithoutSlopeBatch(coordinates, 2, sampler);
        cache.sampleWithoutSlope(1, 0, sampler);
        cache.sampleWithoutSlope(1, 0, sampler);
        assertEquals(6, sampler.slopeFreeCalls.get());
    }

    @Test(timeout = 10_000)
    public void provisionalWorkerResultMarksSharedCacheWaitersAndIsNotRetained() throws Exception {
        HydrologyBasisCache cache = new HydrologyBasisCache();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Thread> waiting = new AtomicReference<>();
        ExecutorService providerWorkers = Executors.newFixedThreadPool(2);
        CountingSampler sampler = new CountingSampler() {
            @Override
            public HydrologyTerrainSample[] sampleBasisWithoutSlopeBatch(long[] coordinates, int count) {
                List<HydrologyTerrainSample> values = HydrologyForkJoin.invokeAll(List.of(() -> {
                    started.countDown();
                    await(release);
                    ProvisionalSampling.mark();
                    return sampleBasisWithoutSlope(RiverFootprint.unpackX(coordinates[0]), 0);
                }, () -> sampleBasisWithoutSlope(RiverFootprint.unpackX(coordinates[1]), 0)), providerWorkers);
                return values.toArray(HydrologyTerrainSample[]::new);
            }
        };
        ExecutorService callers = Executors.newFixedThreadPool(2);
        long[] coordinates = {RiverFootprint.pack(1, 0), RiverFootprint.pack(2, 0)};
        try {
            Future<HydrologyTerrainSample[]> batch = callers.submit(() -> cache.sampleWithoutSlopeBatch(coordinates, 2, sampler));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            Future<ProvisionalSampling.Unmemoizable> point = callers.submit(() -> {
                waiting.set(Thread.currentThread());
                return assertThrows(ProvisionalSampling.Unmemoizable.class,
                        () -> ProvisionalSampling.memoizable(() -> cache.sampleWithoutSlope(1, 0, sampler)));
            });
            awaitWaiting(waiting);
            release.countDown();
            assertSame(batch.get(5, TimeUnit.SECONDS)[0], point.get(5, TimeUnit.SECONDS).value());
            cache.sampleWithoutSlopeBatch(coordinates, 2, sampler);
            assertEquals(4, sampler.slopeFreeCalls.get());
        } finally {
            release.countDown();
            callers.shutdownNow();
            providerWorkers.shutdownNow();
            assertTrue(callers.awaitTermination(5, TimeUnit.SECONDS));
            assertTrue(providerWorkers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test(timeout = 10_000)
    public void seedWinsPendingSample() throws Exception {
        HydrologyBasisCache cache = new HydrologyBasisCache();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountingSampler sampler = new CountingSampler() {
            @Override
            public HydrologyTerrainSample sampleBasisWithoutSlope(int x, int z) {
                started.countDown();
                await(release);
                return super.sampleBasisWithoutSlope(x, z);
            }
        };
        ExecutorService workers = Executors.newSingleThreadExecutor();
        try {
            Future<HydrologyTerrainSample[]> result = workers.submit(() -> cache.sampleWithoutSlopeBatch(
                    new long[]{RiverFootprint.pack(1, 0)}, 1, sampler));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            HydrologyTerrainSample seeded = HydrologyTerrainSample.openLand(73, 0D, "seed");
            Long2ObjectOpenHashMap<HydrologyTerrainSample> seeds = new Long2ObjectOpenHashMap<>();
            seeds.put(RiverFootprint.pack(1, 0), seeded);
            cache.seedWithoutSlope(seeds);
            release.countDown();
            assertSame(seeded, result.get(5, TimeUnit.SECONDS)[0]);
            assertSame(seeded, cache.sampleWithoutSlope(1, 0, sampler));
        } finally {
            release.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void channelsNullsFailuresAndEvictionPreserveSamples() {
        CountingSampler sampler = new CountingSampler();
        HydrologyBasisCache cache = new HydrologyBasisCache(1);
        assertEquals(3D, cache.sample(1, -2, sampler).slope(), 0D);
        assertEquals(0D, cache.sampleWithoutSlope(1, -2, sampler).slope(), 0D);
        assertEquals(3D, cache.sample(1, -2, sampler).slope(), 0D);
        assertEquals(0D, cache.sampleWithoutSlope(1, -2, sampler).slope(), 0D);
        assertEquals(1, sampler.fullCalls.get());
        assertEquals(1, sampler.slopeFreeCalls.get());
        assertNull(cache.sampleWithoutSlope(Integer.MIN_VALUE, Integer.MAX_VALUE, sampler));
        assertNull(cache.sampleWithoutSlope(Integer.MIN_VALUE, Integer.MAX_VALUE, sampler));
        assertEquals(2, sampler.slopeFreeCalls.get());
        assertThrows(IllegalStateException.class, () -> cache.sampleWithoutSlope(99, 0, sampler));
        assertEquals(87, cache.sampleWithoutSlope(99, 0, sampler).naturalHeight());
        assertEquals(87, cache.sampleWithoutSlope(99, 0, sampler).naturalHeight());
        assertEquals(4, sampler.slopeFreeCalls.get());
        assertEquals(87, cache.sampleWithoutSlope(1, -2, sampler).naturalHeight());
        assertEquals(5, sampler.slopeFreeCalls.get());
    }

    @Test
    public void plannerTrialsPreserveLocalSlopePrecedenceAndOwnerIsolation() {
        CountingSampler natural = new CountingSampler();
        HydrologyTerrainSampler detailed = (x, z) -> {
            throw new AssertionError("Basis caching must not sample caves");
        };
        HydrologyPlannerSettings settings = HydrologyPlannerSettings.defaults();
        HydrologyPlanner planner = new HydrologyPlanner(23L, settings, detailed, natural,
                HydrologyGeometrySampler.deterministic(detailed), -4096,
                footprint -> new HydrologyTerrainCaveVoxelView(detailed, settings.seaLevel(), -4096, 4096));
        planner.planningSamples.set(new HydrologyPlanner.PlanningSamples());
        try {
            assertEquals(3D, planner.sampleBasis(1, 1).slope(), 0D);
            assertEquals(0D, planner.sampleBasisWithoutSlope(2, 1).slope(), 0D);
            HydrologyBasisCache shared = planner.planningSamples.get()
                    .fallbackBasis(new Long2ObjectOpenHashMap<>());
            for (int trial = 0; trial < 4; trial++) {
                planner.planningSamples.set(new HydrologyPlanner.PlanningSamples(null, shared));
                assertEquals(0D, planner.sampleBasisWithoutSlope(1, 1).slope(), 0D);
                assertEquals(3D, planner.sampleBasis(1, 1).slope(), 0D);
                assertEquals(3D, planner.sampleBasisWithoutSlope(1, 1).slope(), 0D);
                assertEquals(0D, planner.sampleBasisWithoutSlope(2, 1).slope(), 0D);
            }
            assertEquals(1, natural.fullCalls.get());
            assertEquals(2, natural.slopeFreeCalls.get());
            planner.planningSamples.set(new HydrologyPlanner.PlanningSamples());
            assertEquals(0D, planner.sampleBasisWithoutSlope(1, 1).slope(), 0D);
            assertEquals(3, natural.slopeFreeCalls.get());
        } finally {
            planner.planningSamples.remove();
        }
    }

    @Test
    public void parallelTrialsComputeEachCoordinateOnce() throws Exception {
        CountingSampler sampler = new CountingSampler();
        HydrologyBasisCache cache = new HydrologyBasisCache();
        CountDownLatch ready = new CountDownLatch(4);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(4);
        try {
            List<Future<List<HydrologyTerrainSample>>> results = new ArrayList<>();
            for (int trial = 0; trial < 4; trial++) {
                results.add(workers.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    List<HydrologyTerrainSample> values = new ArrayList<>();
                    for (int x = -128; x < 0; x++) {
                        values.add(cache.sampleWithoutSlope(x, -17, sampler));
                    }
                    return values;
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            List<HydrologyTerrainSample> expected = results.getFirst().get(5, TimeUnit.SECONDS);
            for (Future<List<HydrologyTerrainSample>> result : results) {
                assertEquals(expected, result.get(5, TimeUnit.SECONDS));
            }
            assertEquals(128, sampler.slopeFreeCalls.get());
        } finally {
            start.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static void awaitWaiting(AtomicReference<Thread> thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Thread current = thread.get();
            if (current != null && current.getState() == Thread.State.WAITING) {
                return;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
        throw new AssertionError("Cache waiter did not wait for its owner");
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interruption);
        }
    }

    private static class CountingSampler implements HydrologyNaturalTerrainSampler {
        private final AtomicInteger fullCalls = new AtomicInteger();
        private final AtomicInteger slopeFreeCalls = new AtomicInteger();
        private final AtomicInteger failures = new AtomicInteger();

        @Override
        public HydrologyTerrainSample sampleBasis(int x, int z) {
            fullCalls.incrementAndGet();
            return HydrologyTerrainSample.openLand(87, 3D, "land");
        }

        @Override
        public HydrologyTerrainSample sampleBasisWithoutSlope(int x, int z) {
            slopeFreeCalls.incrementAndGet();
            if (x == Integer.MIN_VALUE) {
                return null;
            }
            if (x == 99 && failures.getAndIncrement() == 0) {
                throw new IllegalStateException("retry");
            }
            return HydrologyTerrainSample.openLand(87, 0D, "land");
        }

        @Override
        public HydrologyTerrainSample[] sampleGrid(GridRequest request) {
            throw new AssertionError("Basis caching must not sample grids");
        }

        @Override
        public NaturalClassification classifyNatural(int x, int z) {
            throw new AssertionError("Basis caching must not classify separately");
        }
    }
}
