package art.arcane.iris.generation.hydrology;

import org.junit.Test;

import java.util.List;
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
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HydrologyTileDemandWindowTest {
    @Test
    public void completedSlotsRefillBeforeTheSlowestTileFinishes() throws Exception {
        assumeTrue(HydrologyPlanningAdmission.maximumRoots() >= 2);
        HydrologyPlanner planner = planner();
        HydrologyTile first = mock(HydrologyTile.class);
        HydrologyTile second = mock(HydrologyTile.class);
        HydrologyTile third = mock(HydrologyTile.class);
        List<HydrologyTile> expected = List.of(first, second, third);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch thirdStarted = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        doAnswer(invocation -> {
            HydrologyTileKey key = invocation.getArgument(0);
            peak.accumulateAndGet(active.incrementAndGet(), Math::max);
            try {
                if (key.tileX() == 0) {
                    firstStarted.countDown();
                    assertTrue(releaseFirst.await(5, TimeUnit.SECONDS));
                } else {
                    assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
                    if (key.tileX() == 2) {
                        thirdStarted.countDown();
                    }
                }
                return expected.get(key.tileX());
            } finally {
                active.decrementAndGet();
            }
        }).when(planner).plan(any(HydrologyTileKey.class));
        ExecutorService workers = Executors.newFixedThreadPool(3);
        ExecutorService caller = Executors.newSingleThreadExecutor();
        HydrologyTileCache cache = new HydrologyTileCache(planner, 4, workers);
        try {
            Future<List<HydrologyTile>> result = caller.submit(() -> cache.tiles(keys()));
            assertTrue("a completed slot must admit the third tile while the first is blocked",
                    thirdStarted.await(5, TimeUnit.SECONDS));
            assertFalse(result.isDone());
            releaseFirst.countDown();
            assertEquals(expected, result.get(5, TimeUnit.SECONDS));
            assertEquals(2, peak.get());
        } finally {
            releaseFirst.countDown();
            caller.shutdownNow();
            workers.shutdownNow();
            assertTrue(caller.awaitTermination(5, TimeUnit.SECONDS));
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
            cache.close();
        }
    }

    @Test
    public void cancellationStopsRefillWithoutCancellingSharedSiblingWork() throws Exception {
        assumeTrue(HydrologyPlanningAdmission.maximumRoots() >= 2);
        HydrologyPlanner planner = planner();
        HydrologyTile tile = mock(HydrologyTile.class);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger planned = new AtomicInteger();
        doAnswer(invocation -> {
            HydrologyTileKey key = invocation.getArgument(0);
            planned.incrementAndGet();
            if (key.tileX() == 0) {
                firstStarted.countDown();
                assertTrue(releaseFirst.await(5, TimeUnit.SECONDS));
                return tile;
            }
            assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
            throw new CancellationException("Cancelled demand tile");
        }).when(planner).plan(any(HydrologyTileKey.class));
        ExecutorService workers = Executors.newFixedThreadPool(3);
        ExecutorService callers = Executors.newFixedThreadPool(2);
        HydrologyTileCache cache = new HydrologyTileCache(planner, 4, workers);
        try {
            Future<List<HydrologyTile>> result = callers.submit(() -> cache.tiles(keys()));
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> result.get(5, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof CancellationException);
            assertEquals(2, planned.get());
            Future<HydrologyTile> shared = callers.submit(() -> cache.get(keys().getFirst()));
            releaseFirst.countDown();
            assertSame(tile, shared.get(5, TimeUnit.SECONDS));
            assertEquals(2, planned.get());
        } finally {
            releaseFirst.countDown();
            callers.shutdownNow();
            workers.shutdownNow();
            assertTrue(callers.awaitTermination(5, TimeUnit.SECONDS));
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
            cache.close();
        }
    }

    @Test
    public void capturedCachedTilesSurviveInvalidationDuringAnotherPlan() {
        HydrologyPlanner planner = planner();
        HydrologyTile cached = mock(HydrologyTile.class);
        HydrologyTile generated = mock(HydrologyTile.class);
        HydrologyTileKey cachedKey = new HydrologyTileKey(2, 0);
        when(planner.plan(cachedKey)).thenReturn(cached);
        HydrologyTileCache cache = new HydrologyTileCache(planner, 4, Runnable::run);
        try {
            assertSame(cached, cache.get(cachedKey));
            doAnswer(invocation -> {
                cache.clear();
                return generated;
            }).when(planner).plan(keys().getFirst());
            when(planner.plan(cachedKey)).thenThrow(new AssertionError("Captured tile was replanned"));
            assertEquals(List.of(generated, cached), cache.tiles(List.of(keys().getFirst(), cachedKey)));
        } finally {
            cache.close();
        }
    }

    private static HydrologyPlanner planner() {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        when(planner.settings()).thenReturn(HydrologyPlannerSettings.defaults());
        return planner;
    }

    private static List<HydrologyTileKey> keys() {
        return List.of(new HydrologyTileKey(0, 0), new HydrologyTileKey(1, 0), new HydrologyTileKey(2, 0));
    }
}
