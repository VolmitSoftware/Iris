package art.arcane.iris.generation.mantle;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ObjectSourcePlanCacheTest {
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
        ObjectSourcePlanCache cache = new ObjectSourcePlanCache(3L);
        ObjectSourcePlan first = planAt(0);
        ObjectSourcePlan second = planAt(1);

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
