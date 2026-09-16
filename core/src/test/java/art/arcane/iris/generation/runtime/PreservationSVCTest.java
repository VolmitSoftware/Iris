package art.arcane.iris.generation.runtime;

import art.arcane.iris.pack.loading.IrisData;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class PreservationSVCTest {
    @Test
    public void retainsShutdownExecutorsUntilTheyActuallyTerminate() {
        PreservationSVC preservation = new PreservationSVC();
        ExecutorService executor = mock(ExecutorService.class);
        when(executor.isShutdown()).thenReturn(true);
        when(executor.isTerminated()).thenReturn(false);
        preservation.register(executor);

        try (MockedStatic<IrisData> data = mockStatic(IrisData.class)) {
            preservation.dereference();
            assertTrue(preservation.hasActiveResources());

            when(executor.isTerminated()).thenReturn(true);
            preservation.dereference();
            assertFalse(preservation.hasActiveResources());
        }
    }

    @Test
    public void tracksOwnedThreadsUntilTheyExit() throws Exception {
        PreservationSVC preservation = new PreservationSVC();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch stop = new CountDownLatch(1);
        Thread worker = new Thread(() -> awaitStop(started, stop), "preservation-shutdown-test");
        preservation.register(worker);
        worker.start();
        try {
            assertTrue(started.await(5L, TimeUnit.SECONDS));
            assertTrue(preservation.hasActiveResources());
        } finally {
            stop.countDown();
            worker.join(5000L);
        }
        assertFalse(worker.isAlive());
        assertFalse(preservation.hasActiveResources());
    }

    @Test
    public void emptyPreservationDoesNotPreventLoaderRelease() {
        assertFalse(new PreservationSVC().hasActiveResources());
    }

    @Test(timeout = 10_000L)
    public void postShutdownWaitsForInterruptedThreadsAndExecutorsToFinish() throws Exception {
        PreservationSVC preservation = spy(new PreservationSVC());
        AtomicReference<Runnable> postShutdown = new AtomicReference<>();
        doAnswer(invocation -> {
            postShutdown.set(invocation.getArgument(0));
            return null;
        }).when(preservation).postShutdown(any(Runnable.class));
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch interrupted = new CountDownLatch(2);
        CountDownLatch finish = new CountDownLatch(1);
        Thread thread = new Thread(() -> drainAfterInterrupt(started, interrupted, finish), "preserved-worker");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        preservation.register(thread);
        preservation.register(executor);
        thread.start();
        executor.submit(() -> drainAfterInterrupt(started, interrupted, finish));
        try (ExecutorService closer = Executors.newSingleThreadExecutor();
             MockedStatic<IrisData> data = mockStatic(IrisData.class)) {
            assertTrue(started.await(5L, TimeUnit.SECONDS));
            preservation.onDisable();
            assertNotNull(postShutdown.get());
            Future<?> stopped = closer.submit(postShutdown.get());
            try {
                assertTrue(interrupted.await(5L, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> stopped.get(100L, TimeUnit.MILLISECONDS));
                assertTrue(preservation.hasActiveResources());
            } finally {
                finish.countDown();
            }
            stopped.get(5L, TimeUnit.SECONDS);
            assertFalse(preservation.hasActiveResources());
        } finally {
            finish.countDown();
            thread.interrupt();
            thread.join(5_000L);
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5L, TimeUnit.SECONDS));
        }
    }

    @Test(timeout = 10_000L)
    public void timedOutWorkersRemainTrackedWithTheirIdentityInTheFailure() throws Exception {
        PreservationSVC preservation = new PreservationSVC();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        Thread worker = new Thread(() -> drainAfterInterrupt(started, interrupted, finish), "stalled-preserved-worker");
        preservation.register(worker);
        worker.start();
        try {
            assertTrue(started.await(5L, TimeUnit.SECONDS));
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> preservation.shutdownResources(25L));
            assertTrue(failure.getMessage().contains("stalled-preserved-worker"));
            assertTrue(preservation.hasActiveResources());
        } finally {
            finish.countDown();
            worker.interrupt();
            worker.join(5_000L);
        }
        assertFalse(preservation.hasActiveResources());
    }

    @Test(timeout = 5_000L)
    public void shutdownNeverInterruptsOrWaitsForItsCallingThread() {
        PreservationSVC preservation = new PreservationSVC();
        preservation.register(Thread.currentThread());

        preservation.shutdownResources(1_000L);

        assertFalse(Thread.currentThread().isInterrupted());
        assertTrue(preservation.hasActiveResources());
    }

    private static void drainAfterInterrupt(CountDownLatch started, CountDownLatch interrupted, CountDownLatch finish) {
        started.countDown();
        try {
            new CountDownLatch(1).await();
        } catch (InterruptedException expected) {
            interrupted.countDown();
        }
        boolean released = false;
        while (!released) {
            try {
                finish.await();
                released = true;
            } catch (InterruptedException ignored) {
            }
        }
    }

    private static void awaitStop(CountDownLatch started, CountDownLatch stop) {
        started.countDown();
        try {
            stop.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
