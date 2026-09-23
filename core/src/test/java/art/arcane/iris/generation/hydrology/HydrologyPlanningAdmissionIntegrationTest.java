package art.arcane.iris.generation.hydrology;

import art.arcane.iris.testsupport.Await;
import org.junit.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HydrologyPlanningAdmissionIntegrationTest {
    @Test(timeout = 15000)
    public void separateCachesShareTheProcessPlanningBudget() throws Exception {
        ArrayList<HydrologyPlanningAdmission.Permit> held = holdRoots(HydrologyPlanningAdmission.maximumRoots() - 1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        ExecutorService callers = Executors.newFixedThreadPool(2);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        HydrologyTile tile = mock(HydrologyTile.class);
        HydrologyPlanner firstPlanner = mock(HydrologyPlanner.class);
        when(firstPlanner.settings()).thenReturn(HydrologyPlannerSettings.defaults());
        HydrologyPlanner secondPlanner = mock(HydrologyPlanner.class);
        when(secondPlanner.settings()).thenReturn(HydrologyPlannerSettings.defaults());
        HydrologyTileKey key = new HydrologyTileKey(0, 0);
        when(firstPlanner.plan(key)).thenAnswer(invocation -> {
            peak.accumulateAndGet(active.incrementAndGet(), Math::max);
            firstStarted.countDown();
            assertTrue(releaseFirst.await(5L, TimeUnit.SECONDS));
            active.decrementAndGet();
            return tile;
        });
        when(secondPlanner.plan(key)).thenAnswer(invocation -> {
            peak.accumulateAndGet(active.incrementAndGet(), Math::max);
            secondStarted.countDown();
            active.decrementAndGet();
            return tile;
        });
        HydrologyTileCache firstCache = new HydrologyTileCache(firstPlanner, 4, workers);
        HydrologyTileCache secondCache = new HydrologyTileCache(secondPlanner, 4, workers);
        try {
            Future<HydrologyTile> first = callers.submit(() -> firstCache.get(key));
            assertTrue(firstStarted.await(5L, TimeUnit.SECONDS));
            Future<HydrologyTile> second = callers.submit(() -> secondCache.get(key));
            awaitLoad(secondCache);
            assertFalse(secondStarted.await(150L, TimeUnit.MILLISECONDS));
            releaseFirst.countDown();
            assertSame(tile, first.get(5L, TimeUnit.SECONDS));
            assertSame(tile, second.get(5L, TimeUnit.SECONDS));
            assertEquals(1, peak.get());
            assertEquals(0, active.get());
        } finally {
            releaseFirst.countDown();
            releaseRoots(held);
            workers.shutdownNow();
            callers.shutdownNow();
            assertTrue(workers.awaitTermination(5L, TimeUnit.SECONDS));
            assertTrue(callers.awaitTermination(5L, TimeUnit.SECONDS));
            firstCache.close();
            secondCache.close();
        }
    }

    @Test(timeout = 15000)
    public void closingAWaitingCacheCancelsAdmissionWithoutPublishingAnEmptyTile() throws Exception {
        ArrayList<HydrologyPlanningAdmission.Permit> held = holdRoots(HydrologyPlanningAdmission.maximumRoots());
        ExecutorService workers = Executors.newSingleThreadExecutor();
        ExecutorService callers = Executors.newFixedThreadPool(2);
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        when(planner.settings()).thenReturn(HydrologyPlannerSettings.defaults());
        HydrologyTileCache cache = new HydrologyTileCache(planner, 4, workers);
        HydrologyTileKey key = new HydrologyTileKey(0, 0);
        try {
            Future<HydrologyTile> pending = callers.submit(() -> cache.get(key));
            awaitLoad(cache);
            callers.submit(cache::close).get(2L, TimeUnit.SECONDS);
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> pending.get(2L, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof CancellationException);
            verify(planner, never()).plan(any(HydrologyTileKey.class));
            verify(planner, never()).emptyTile(any(HydrologyTileKey.class));
            assertEquals(0, cache.size());
            releaseRoots(held);
            HydrologyTile tile = mock(HydrologyTile.class);
            HydrologyPlanner nextPlanner = mock(HydrologyPlanner.class);
        when(nextPlanner.settings()).thenReturn(HydrologyPlannerSettings.defaults());
            when(nextPlanner.plan(key)).thenReturn(tile);
            try (HydrologyTileCache next = new HydrologyTileCache(nextPlanner, 4, workers)) {
                assertSame(tile, callers.submit(() -> next.get(key)).get(5L, TimeUnit.SECONDS));
            }
        } finally {
            releaseRoots(held);
            workers.shutdownNow();
            callers.shutdownNow();
            assertTrue(workers.awaitTermination(5L, TimeUnit.SECONDS));
            assertTrue(callers.awaitTermination(5L, TimeUnit.SECONDS));
            cache.close();
        }
    }

    private static ArrayList<HydrologyPlanningAdmission.Permit> holdRoots(int count) {
        ArrayList<HydrologyPlanningAdmission.Permit> held = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            held.add(HydrologyPlanningAdmission.acquireRoot(() -> false));
        }
        return held;
    }

    private static void releaseRoots(ArrayList<HydrologyPlanningAdmission.Permit> held) {
        for (HydrologyPlanningAdmission.Permit permit : held) {
            permit.close();
        }
        held.clear();
    }

    private static void awaitLoad(HydrologyTileCache cache) throws Exception {
        Field field = HydrologyTileCache.class.getDeclaredField("loading");
        field.setAccessible(true);
        Map<?, ?> loading = (Map<?, ?>) field.get(cache);
        Await.until("the cache load to start", Duration.ofSeconds(5L), () -> !loading.isEmpty());
    }
}
