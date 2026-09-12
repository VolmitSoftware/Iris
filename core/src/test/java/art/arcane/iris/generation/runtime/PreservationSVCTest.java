package art.arcane.iris.generation.runtime;

import art.arcane.iris.pack.loading.IrisData;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
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

    private static void awaitStop(CountDownLatch started, CountDownLatch stop) {
        started.countDown();
        try {
            stop.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
