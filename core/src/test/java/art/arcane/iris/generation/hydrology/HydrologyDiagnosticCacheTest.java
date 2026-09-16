package art.arcane.iris.generation.hydrology;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HydrologyDiagnosticCacheTest {
    @Test
    public void terrainLoadsLeaveDiagnosticsLazyAndQueriesRetainImmutableResults() {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        HydrologyTileKey key = new HydrologyTileKey(0, 0);
        HydrologyDiagnosticCandidate candidate = mock(HydrologyDiagnosticCandidate.class);
        ArrayList<HydrologyDiagnosticCandidate> supplied = new ArrayList<>(List.of(candidate));
        when(planner.plan(key)).thenReturn(tile);
        when(planner.diagnosticCandidates(tile)).thenReturn(supplied);
        try (HydrologyTileCache cache = new HydrologyTileCache(planner, 4)) {
            assertSame(tile, cache.get(key));
            verify(planner, never()).diagnosticCandidates(tile);
            AtomicInteger preparations = new AtomicInteger();
            cache.setTerrainPreparation(preparations::incrementAndGet);
            List<HydrologyDiagnosticCandidate> result = cache.diagnosticCandidates(key);
            supplied.clear();
            assertEquals(List.of(candidate), result);
            assertThrows(UnsupportedOperationException.class, () -> result.add(candidate));
            assertSame(result, cache.diagnosticCandidates(key));
            verify(planner, times(1)).plan(key);
            verify(planner, times(1)).diagnosticCandidates(tile);
            assertEquals(1, preparations.get());
        }
    }

    @Test
    public void forbiddenThreadsCanReadOnlyCompleteCachedDiagnostics() {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        HydrologyTileKey key = new HydrologyTileKey(0, 0);
        AtomicBoolean forbidden = new AtomicBoolean(true);
        when(planner.plan(key)).thenReturn(tile);
        when(planner.diagnosticCandidates(tile)).thenReturn(List.of());
        try (HydrologyTileCache cache = new HydrologyTileCache(planner, 4, null, forbidden::get)) {
            assertThrows(IllegalStateException.class, () -> cache.diagnosticCandidates(key));
            verify(planner, never()).plan(key);
            forbidden.set(false);
            List<HydrologyDiagnosticCandidate> complete = cache.diagnosticCandidates(key);
            forbidden.set(true);
            assertSame(complete, cache.diagnosticCandidates(key));
        }
    }

    @Test
    public void interruptedWaiterLeavesTheSharedDiagnosticLoadRunning() throws Exception {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        HydrologyTileKey key = new HydrologyTileKey(0, 0);
        List<HydrologyDiagnosticCandidate> expected = List.of(mock(HydrologyDiagnosticCandidate.class));
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch exited = new CountDownLatch(1);
        AtomicReference<Thread> waiterThread = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        when(planner.plan(key)).thenReturn(tile);
        when(planner.diagnosticCandidates(tile)).thenAnswer(invocation -> {
            started.countDown();
            assertTrue(release.await(5L, TimeUnit.SECONDS));
            return expected;
        });
        ExecutorService workers = Executors.newSingleThreadExecutor();
        ExecutorService callers = Executors.newFixedThreadPool(2);
        HydrologyTileCache cache = new HydrologyTileCache(planner, 4, workers);
        try {
            Future<?> waiter = callers.submit(() -> {
                waiterThread.set(Thread.currentThread());
                try {
                    assertThrows(CancellationException.class, () -> cache.diagnosticCandidates(key));
                    interrupted.set(Thread.currentThread().isInterrupted());
                } finally {
                    exited.countDown();
                }
            });
            assertTrue(started.await(5L, TimeUnit.SECONDS));
            Future<List<HydrologyDiagnosticCandidate>> second = callers.submit(() -> cache.diagnosticCandidates(key));
            waiterThread.get().interrupt();
            assertTrue(exited.await(2L, TimeUnit.SECONDS));
            waiter.get(2L, TimeUnit.SECONDS);
            assertTrue(interrupted.get());
            assertFalse(second.isDone());
            release.countDown();
            assertEquals(expected, second.get(5L, TimeUnit.SECONDS));
            verify(planner, times(1)).diagnosticCandidates(tile);
        } finally {
            release.countDown();
            workers.shutdownNow();
            callers.shutdownNow();
            assertTrue(workers.awaitTermination(5L, TimeUnit.SECONDS));
            assertTrue(callers.awaitTermination(5L, TimeUnit.SECONDS));
            cache.close();
        }
    }

    @Test
    public void clearDoesNotPublishAnOlderDiagnosticResultIntoTheNewEpoch() throws Exception {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        HydrologyTileKey key = new HydrologyTileKey(0, 0);
        List<HydrologyDiagnosticCandidate> old = List.of(mock(HydrologyDiagnosticCandidate.class));
        List<HydrologyDiagnosticCandidate> fresh = List.of(mock(HydrologyDiagnosticCandidate.class));
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger();
        when(planner.plan(key)).thenReturn(tile);
        when(planner.diagnosticCandidates(tile)).thenAnswer(invocation -> {
            if (attempts.getAndIncrement() == 0) {
                started.countDown();
                assertTrue(release.await(5L, TimeUnit.SECONDS));
                return old;
            }
            return fresh;
        });
        ExecutorService caller = Executors.newSingleThreadExecutor();
        HydrologyTileCache cache = new HydrologyTileCache(planner, 4);
        try {
            Future<List<HydrologyDiagnosticCandidate>> first = caller.submit(() -> cache.diagnosticCandidates(key));
            assertTrue(started.await(5L, TimeUnit.SECONDS));
            cache.clear();
            assertEquals(fresh, cache.diagnosticCandidates(key));
            release.countDown();
            assertEquals(old, first.get(5L, TimeUnit.SECONDS));
            assertEquals(fresh, cache.diagnosticCandidates(key));
            assertEquals(2, attempts.get());
        } finally {
            release.countDown();
            caller.shutdownNow();
            assertTrue(caller.awaitTermination(5L, TimeUnit.SECONDS));
            cache.close();
        }
    }

    @Test
    public void closeCancelsQueuedDiagnosticsAndDrainsActiveDiagnostics() throws Exception {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        HydrologyTileKey firstKey = new HydrologyTileKey(0, 0);
        HydrologyTileKey secondKey = new HydrologyTileKey(1, 0);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch queued = new CountDownLatch(1);
        AtomicInteger submissions = new AtomicInteger();
        when(planner.plan(any(HydrologyTileKey.class))).thenReturn(tile);
        when(planner.diagnosticCandidates(tile)).thenAnswer(invocation -> {
            started.countDown();
            assertTrue(release.await(5L, TimeUnit.SECONDS));
            return List.of();
        });
        ExecutorService workers = Executors.newSingleThreadExecutor();
        ExecutorService callers = Executors.newFixedThreadPool(3);
        HydrologyTileCache cache = new HydrologyTileCache(planner, 4, task -> {
            workers.execute(task);
            if (submissions.incrementAndGet() == 2) {
                queued.countDown();
            }
        });
        try {
            Future<List<HydrologyDiagnosticCandidate>> first = callers.submit(() -> cache.diagnosticCandidates(firstKey));
            assertTrue(started.await(5L, TimeUnit.SECONDS));
            Future<?> second = callers.submit(() -> cache.diagnosticCandidates(secondKey));
            assertTrue(queued.await(5L, TimeUnit.SECONDS));
            Future<?> closing = callers.submit(cache::close);
            ExecutionException cancelled = assertThrows(ExecutionException.class, () -> second.get(5L, TimeUnit.SECONDS));
            assertTrue(cancelled.getCause() instanceof CancellationException);
            assertThrows(TimeoutException.class, () -> closing.get(100L, TimeUnit.MILLISECONDS));
            assertThrows(CancellationException.class, () -> cache.diagnosticCandidates(firstKey));
            release.countDown();
            assertEquals(List.of(), first.get(5L, TimeUnit.SECONDS));
            closing.get(5L, TimeUnit.SECONDS);
            verify(planner, never()).plan(secondKey);
        } finally {
            release.countDown();
            workers.shutdownNow();
            callers.shutdownNow();
            assertTrue(workers.awaitTermination(5L, TimeUnit.SECONDS));
            assertTrue(callers.awaitTermination(5L, TimeUnit.SECONDS));
        }
    }

    @Test
    public void aDiagnosticLoadCannotCloseItsOwnCache() {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        HydrologyTileKey key = new HydrologyTileKey(0, 0);
        when(planner.plan(key)).thenReturn(tile);
        HydrologyTileCache cache = new HydrologyTileCache(planner, 4);
        when(planner.diagnosticCandidates(tile)).thenAnswer(invocation -> {
            assertThrows(IllegalStateException.class, cache::close);
            return List.of();
        });
        assertEquals(List.of(), cache.diagnosticCandidates(key));
        assertSame(tile, cache.get(key));
        cache.close();
    }

    @Test
    public void diagnosticFailureIsRetriedAndCompletedListsRemainBounded() throws Exception {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        when(planner.plan(any(HydrologyTileKey.class))).thenReturn(tile);
        when(planner.diagnosticCandidates(tile)).thenThrow(new IllegalStateException("expected")).thenReturn(List.of());
        try (HydrologyTileCache cache = new HydrologyTileCache(planner, 2)) {
            HydrologyTileKey key = new HydrologyTileKey(0, 0);
            assertThrows(IllegalStateException.class, () -> cache.diagnosticCandidates(key));
            assertEquals(List.of(), cache.diagnosticCandidates(key));
            for (int index = 1; index < 24; index++) {
                assertEquals(List.of(), cache.diagnosticCandidates(new HydrologyTileKey(index, 0)));
            }
            Field field = HydrologyTileCache.class.getDeclaredField("diagnostics");
            field.setAccessible(true);
            Cache<?, ?> completed = (Cache<?, ?>) field.get(cache);
            completed.cleanUp();
            assertTrue(completed.estimatedSize() <= 2L);
        }
    }
}
