package art.arcane.iris.generation.mantle;

import art.arcane.volmlib.util.cache.CacheKey;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ObjectSourcePlanCacheTest {
    @Test
    public void untouchedDestinationsReuseTheSourceIndexWithoutRebuilding() {
        ObjectSourcePlanCache cache = new ObjectSourcePlanCache(1024L * 1024L);
        AtomicInteger builds = new AtomicInteger();
        ObjectSourcePlan plan = planTouching(new int[]{0, 0}, new int[]{1, 0});
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                if ((x == 0 || x == 1) && z == 0) {
                    continue;
                }
                assertNull(cache.acquire(0, 0, x, z, 7, () -> {
                    builds.incrementAndGet();
                    return plan;
                }));
            }
        }
        assertEquals(1, builds.get());
        assertSame(plan, cache.acquire(0, 0, 1, 0, 7, () -> {
            builds.incrementAndGet();
            return plan;
        }));
        assertEquals(1, builds.get());
        ObjectSourcePlanCache.Stats stats = cache.stats();
        assertEquals(1L, stats.builds());
        assertEquals(224L, stats.lookups());
        assertEquals(0L, stats.drained());
    }

    @Test
    public void everyDestinationConsumesARetainedPlanOnceAndRepeatsRebuild() {
        ObjectSourcePlanCache cache = new ObjectSourcePlanCache(1024L * 1024L);
        AtomicInteger builds = new AtomicInteger();
        ObjectSourcePlan plan = planTouching(new int[]{0, 0}, new int[]{1, 0});
        ObjectSourcePlan rebuilt = planTouching(new int[]{0, 0}, new int[]{1, 0});
        assertSame(plan, cache.acquire(0, 0, 0, 0, 7, () -> {
            builds.incrementAndGet();
            return plan;
        }));
        long retainedBeforeDrain = cache.stats().retainedBytes();
        assertSame(plan, cache.acquire(0, 0, 1, 0, 7, () -> {
            builds.incrementAndGet();
            return rebuilt;
        }));
        assertEquals(1, builds.get());
        assertEquals(1L, cache.stats().drained());
        assertTrue(cache.stats().retainedBytes() < retainedBeforeDrain);

        assertNull(cache.acquire(0, 0, 2, 0, 7, () -> {
            builds.incrementAndGet();
            return rebuilt;
        }));
        assertSame(rebuilt, cache.acquire(0, 0, 0, 0, 7, () -> {
            builds.incrementAndGet();
            return rebuilt;
        }));
        assertEquals(2, builds.get());
        assertEquals(1L, cache.stats().replays());
        assertSame(rebuilt, cache.acquire(0, 0, 1, 0, 7, () -> {
            builds.incrementAndGet();
            return plan;
        }));
        assertEquals(2, builds.get());
        assertEquals(2L, cache.stats().drained());
    }

    @Test
    public void sourcesRetireOnceEveryDestinationInReachAskedForThem() {
        ObjectSourcePlanCache cache = new ObjectSourcePlanCache(1024L * 1024L);
        AtomicInteger builds = new AtomicInteger();
        ObjectSourcePlan plan = planTouching(new int[]{0, 0}, new int[]{1, 0});
        int returned = 0;
        int visits = 0;
        for (int x = 7; x >= -7; x--) {
            for (int z = -7; z <= 7; z++) {
                if (cache.acquire(0, 0, x, z, 7, () -> {
                    builds.incrementAndGet();
                    return plan;
                }) != null) {
                    returned++;
                }
                if (++visits < 225) {
                    assertEquals(1L, cache.estimatedSize());
                }
            }
        }

        assertEquals(2, returned);
        assertEquals(1, builds.get());
        assertEquals(0L, cache.estimatedSize());
        assertEquals(1L, cache.stats().retired());
        assertSame(plan, cache.acquire(0, 0, 1, 0, 7, () -> {
            builds.incrementAndGet();
            return plan;
        }));
        assertEquals(2, builds.get());
    }

    @Test
    public void emptyPlansAreRetainedAsIndexesAndNeverRebuilt() {
        ObjectSourcePlanCache cache = new ObjectSourcePlanCache(1024L * 1024L);
        AtomicInteger builds = new AtomicInteger();
        for (int sweep = 0; sweep < 3; sweep++) {
            for (int chunk = 0; chunk < 1000; chunk++) {
                assertNull(cache.acquire(chunk, 0, chunk, 0, 7, () -> {
                    builds.incrementAndGet();
                    return new ObjectSourcePlan(List.of());
                }));
            }
        }
        assertEquals(1000, builds.get());
        assertEquals(1000L, cache.estimatedSize());
        cache.clear();
        assertEquals(0L, cache.estimatedSize());
    }

    @Test
    public void unrelatedCoordinatesBuildWhileAnotherPlanIsPending() throws Exception {
        ObjectSourcePlanCache cache = new ObjectSourcePlanCache(4096L);
        ObjectSourcePlan first = planTouching(new int[]{32113, 37});
        ObjectSourcePlan second = planTouching(new int[]{32667, 37});
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            assertEquals(Long.hashCode(CacheKey.mix(CacheKey.key(32113, 37))),
                    Long.hashCode(CacheKey.mix(CacheKey.key(32667, 37))));
            Future<ObjectSourcePlan> firstResult = executor.submit(() -> cache.acquire(32113, 37, 32113, 37, 7, () -> {
                firstEntered.countDown();
                await(releaseFirst);
                return first;
            }));
            assertTrue(firstEntered.await(5L, TimeUnit.SECONDS));
            Future<ObjectSourcePlan> secondResult = executor.submit(() -> cache.acquire(32667, 37, 32667, 37, 7, () -> second));

            assertSame(second, secondResult.get(3L, TimeUnit.SECONDS));
            releaseFirst.countDown();
            assertSame(first, firstResult.get(5L, TimeUnit.SECONDS));
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void concurrentDestinationsShareOneSourceBuild() throws Exception {
        ObjectSourcePlanCache cache = new ObjectSourcePlanCache(1024L * 1024L);
        int[][] touched = new int[8][];
        for (int index = 0; index < touched.length; index++) {
            touched[index] = new int[]{index - 4, -7};
        }
        ObjectSourcePlan expected = planTouching(touched);
        AtomicInteger builds = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<Future<ObjectSourcePlan>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < 8; index++) {
                int destinationX = index - 4;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5L, TimeUnit.SECONDS));
                    return cache.acquire(4, -7, destinationX, -7, 7, () -> {
                        builds.incrementAndGet();
                        return expected;
                    });
                }));
            }
            assertTrue(ready.await(5L, TimeUnit.SECONDS));
            start.countDown();
            for (Future<ObjectSourcePlan> future : futures) {
                assertSame(expected, future.get(5L, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, builds.get());
        assertEquals(1L, cache.stats().drained());
    }

    @Test
    public void oversizedPlansAreServedWithoutRetention() {
        ObjectSourcePlan large = planTouching(new int[]{0, 0}, new int[]{1, 0});
        ObjectSourcePlanCache cache = new ObjectSourcePlanCache(large.estimatedRetainedBytes());
        AtomicInteger builds = new AtomicInteger();

        assertSame(large, cache.acquire(0, 0, 0, 0, 7, () -> {
            builds.incrementAndGet();
            return large;
        }));
        assertEquals(0L, cache.estimatedSize());
        assertSame(large, cache.acquire(0, 0, 1, 0, 7, () -> {
            builds.incrementAndGet();
            return large;
        }));
        assertEquals(2, builds.get());
    }

    @Test
    public void cacheEvictsByRetainedBytesAndCanBeCleared() {
        ObjectSourcePlan first = planTouching(new int[]{0, 0}, new int[]{9, 9});
        ObjectSourcePlan second = planTouching(new int[]{1, 0}, new int[]{9, 9});
        ObjectSourcePlanCache cache = new ObjectSourcePlanCache(first.estimatedRetainedBytes() + 256L);

        cache.acquire(0, 0, 0, 0, 7, () -> first);
        cache.acquire(1, 0, 1, 0, 7, () -> second);

        assertEquals(1L, cache.estimatedSize());
        cache.clear();
        assertEquals(0L, cache.estimatedSize());
    }

    @Test
    public void cacheRejectsNonPositiveByteCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new ObjectSourcePlanCache(0L));
    }

    @Test
    public void clearingPendingWorkKeepsNewGenerationIsolated() throws Exception {
        ObjectSourcePlanCache cache = new ObjectSourcePlanCache(4096L);
        ObjectSourcePlan old = planTouching(new int[]{7, 8});
        ObjectSourcePlan replacement = planTouching(new int[]{7, 8});
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<ObjectSourcePlan> first = executor.submit(() -> cache.acquire(7, 8, 7, 8, 7, () -> {
                entered.countDown();
                await(release);
                return old;
            }));
            try {
                assertTrue(entered.await(5L, TimeUnit.SECONDS));
                cache.clear();
                assertSame(replacement, cache.acquire(7, 8, 7, 8, 7, () -> replacement));
            } finally {
                release.countDown();
            }
            assertSame(old, first.get(5L, TimeUnit.SECONDS));
            assertSame(old, cache.acquire(7, 8, 7, 8, 7, () -> old));
        }
    }

    @Test
    public void waitingForkJoinWorkerAllowsQueuedDependencyToRun() throws Exception {
        ObjectSourcePlanCache cache = new ObjectSourcePlanCache(4096L);
        ObjectSourcePlan expected = planTouching(new int[]{7, 8}, new int[]{8, 8});
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch waiting = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService owner = Executors.newSingleThreadExecutor();
             ForkJoinPool pool = new ForkJoinPool(1)) {
            Future<ObjectSourcePlan> first = owner.submit(() -> cache.acquire(7, 8, 7, 8, 7, () -> {
                entered.countDown();
                await(release);
                return expected;
            }));
            try {
                assertTrue(entered.await(5L, TimeUnit.SECONDS));
                Future<ObjectSourcePlan> second = pool.submit(() -> {
                    waiting.countDown();
                    return cache.acquire(7, 8, 8, 8, 7, () -> { throw new AssertionError("Duplicate build"); });
                });
                assertTrue(waiting.await(5L, TimeUnit.SECONDS));
                pool.submit(release::countDown).get(5L, TimeUnit.SECONDS);
                assertSame(expected, first.get(5L, TimeUnit.SECONDS));
                assertSame(expected, second.get(5L, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    public void failedAndMissingBuildsAreRetryableAndRecursionFailsImmediately() {
        ObjectSourcePlanCache cache = new ObjectSourcePlanCache(4096L);
        IllegalArgumentException failure = new IllegalArgumentException("Build failed");
        assertSame(failure, assertThrows(IllegalArgumentException.class,
                () -> cache.acquire(7, 8, 7, 8, 7, () -> { throw failure; })));
        assertThrows(NullPointerException.class, () -> cache.acquire(7, 8, 7, 8, 7, () -> null));
        assertThrows(IllegalStateException.class, () -> cache.acquire(7, 8, 7, 8, 7,
                () -> cache.acquire(7, 8, 7, 8, 7, () -> planTouching(new int[]{7, 8}))));
        ObjectSourcePlan expected = planTouching(new int[]{7, 8});
        assertSame(expected, cache.acquire(7, 8, 7, 8, 7, () -> expected));
    }

    @Test
    public void byteBudgetScalesWithHeapAndHasAnUpperBound() {
        assertEquals(128L * 1024L * 1024L, ObjectSourcePlanCache.retainedByteBudget(4L * 1024L * 1024L * 1024L));
        assertEquals(32L * 1024L * 1024L, ObjectSourcePlanCache.retainedByteBudget(1024L * 1024L * 1024L));
        assertEquals(512L * 1024L * 1024L, ObjectSourcePlanCache.retainedByteBudget(Long.MAX_VALUE));
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10L, TimeUnit.SECONDS));
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(failure);
        }
    }

    private static ObjectSourcePlan planTouching(int[]... chunks) {
        List<ObjectDestinationTransaction.Mutation> mutations = new ArrayList<>();
        for (int[] chunk : chunks) {
            mutations.add(new ObjectDestinationTransaction.SetMutation(
                    new ObjectDestinationTransaction.DataKey(chunk[0] << 4, 4, chunk[1] << 4, String.class), "marker"));
        }
        return new ObjectSourcePlan(mutations);
    }
}
