package art.arcane.iris.generation.mantle;

import art.arcane.volmlib.util.cache.CacheKey;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ObjectSourcePlanCacheTest {
    @Test
    public void defaultCapacityRetainsNeighboringSourceWorkingSetWithoutRebuilding() {
        ObjectSourcePlanCache cache = new ObjectSourcePlanCache();
        AtomicInteger builds = new AtomicInteger();
        ObjectDestinationTransaction.DataKey key = new ObjectDestinationTransaction.DataKey(
                0, 4, 0, String.class);
        ObjectDestinationTransaction.Mutation mutation = new ObjectDestinationTransaction.SetMutation(key, "marker");
        ObjectSourcePlan expected = new ObjectSourcePlan(Collections.nCopies(2048, mutation));
        assertTrue(expected.mutationWeight() * 1024L > 1_048_576L);
        for (int sweep = 0; sweep < 3; sweep++) {
            for (int x = -16; x < 16; x++) {
                for (int z = -16; z < 16; z++) {
                    assertSame(expected, cache.get(x, z, () -> {
                        builds.incrementAndGet();
                        return expected;
                    }));
                }
            }
            assertEquals(1024L, cache.estimatedSize());
            assertEquals(1024, builds.get());
        }
        cache.clear();
        assertEquals(0L, cache.estimatedSize());
        assertSame(expected, cache.get(-16, -16, () -> {
            builds.incrementAndGet();
            return expected;
        }));
        assertEquals(1025, builds.get());
    }

    @Test
    public void unrelatedCoordinatesBuildWhileAnotherPlanIsPending() throws Exception {
        ObjectSourcePlanCache cache = new ObjectSourcePlanCache(64L);
        ObjectSourcePlan first = planAt(1);
        ObjectSourcePlan second = planAt(2);
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            assertEquals(Long.hashCode(CacheKey.mix(CacheKey.key(32113, 37))),
                    Long.hashCode(CacheKey.mix(CacheKey.key(32667, 37))));
            Future<ObjectSourcePlan> firstResult = executor.submit(() -> cache.get(32113, 37, () -> {
                firstEntered.countDown();
                try {
                    assertTrue(releaseFirst.await(10L, TimeUnit.SECONDS));
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(failure);
                }
                return first;
            }));
            assertTrue(firstEntered.await(5L, TimeUnit.SECONDS));
            Future<ObjectSourcePlan> secondResult = executor.submit(() -> cache.get(32667, 37, () -> second));

            assertSame(second, secondResult.get(3L, TimeUnit.SECONDS));
            releaseFirst.countDown();
            assertSame(first, firstResult.get(5L, TimeUnit.SECONDS));
            assertSame(first, cache.get(32113, 37, () -> second));
            assertSame(second, cache.get(32667, 37, () -> first));
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void concurrentRequestsBuildOneSourcePlan() throws Exception {
        ObjectSourcePlanCache cache = new ObjectSourcePlanCache(64L);
        ObjectSourcePlan expected = new ObjectSourcePlan(List.of());
        AtomicInteger builds = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<Future<ObjectSourcePlan>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < 8; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5L, TimeUnit.SECONDS));
                    return cache.get(4, -7, () -> {
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
    }

    @Test
    public void cacheEvictsByMutationWeightAndCanBeCleared() {
        ObjectSourcePlan first = planAt(0);
        ObjectSourcePlan second = planAt(1);
        ObjectSourcePlanCache cache = new ObjectSourcePlanCache(first.mutationWeight());

        cache.get(0, 0, () -> first);
        cache.get(1, 0, () -> second);

        assertEquals(1L, cache.estimatedSize());
        cache.clear();
        assertEquals(0L, cache.estimatedSize());
    }

    @Test
    public void cacheRejectsNonPositiveMutationCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new ObjectSourcePlanCache(0L));
    }

    @Test
    public void clearingPendingWorkKeepsNewGenerationIsolated() throws Exception {
        ObjectSourcePlanCache cache = new ObjectSourcePlanCache(64L);
        ObjectSourcePlan old = planAt(1);
        ObjectSourcePlan replacement = planAt(2);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<ObjectSourcePlan> first = executor.submit(() -> cache.get(7, 8, () -> {
                entered.countDown();
                await(release);
                return old;
            }));
            try {
                assertTrue(entered.await(5L, TimeUnit.SECONDS));
                cache.clear();
                assertSame(replacement, cache.get(7, 8, () -> replacement));
            } finally {
                release.countDown();
            }
            assertSame(old, first.get(5L, TimeUnit.SECONDS));
            assertSame(replacement, cache.get(7, 8, () -> old));
        }
    }

    @Test
    public void waitingForkJoinWorkerAllowsQueuedDependencyToRun() throws Exception {
        ObjectSourcePlanCache cache = new ObjectSourcePlanCache(64L);
        ObjectSourcePlan expected = planAt(1);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch waiting = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService owner = Executors.newSingleThreadExecutor();
             ForkJoinPool pool = new ForkJoinPool(1)) {
            Future<ObjectSourcePlan> first = owner.submit(() -> cache.get(7, 8, () -> {
                entered.countDown();
                await(release);
                return expected;
            }));
            try {
                assertTrue(entered.await(5L, TimeUnit.SECONDS));
                Future<ObjectSourcePlan> second = pool.submit(() -> {
                    waiting.countDown();
                    return cache.get(7, 8, () -> { throw new AssertionError("Duplicate build"); });
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
    public void failedAndNullBuildsAreRetryableAndRecursionFailsImmediately() {
        ObjectSourcePlanCache cache = new ObjectSourcePlanCache(64L);
        IllegalArgumentException failure = new IllegalArgumentException("Build failed");
        assertSame(failure, assertThrows(IllegalArgumentException.class,
                () -> cache.get(7, 8, () -> { throw failure; })));
        assertNull(cache.get(7, 8, () -> null));
        assertThrows(IllegalStateException.class, () -> cache.get(7, 8,
                () -> cache.get(7, 8, () -> planAt(1))));
        ObjectSourcePlan expected = planAt(1);
        assertSame(expected, cache.get(7, 8, () -> expected));
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10L, TimeUnit.SECONDS));
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(failure);
        }
    }

    private static ObjectSourcePlan planAt(int x) {
        ObjectDestinationTransaction.DataKey key = new ObjectDestinationTransaction.DataKey(
                x,
                4,
                0,
                String.class
        );
        ObjectDestinationTransaction.Mutation mutation = new ObjectDestinationTransaction.SetMutation(
                key,
                "marker"
        );
        return new ObjectSourcePlan(List.of(mutation));
    }
}
