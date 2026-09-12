package art.arcane.iris.world.pregen;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PregenSerialWorkerTest {
    @Test
    public void submitRunsOffTheCallerThreadAndCloseDrainsTheBacklog() throws Exception {
        Reports reports = new Reports();
        PregenSerialWorker queue = reports.queue();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Thread> runner = new AtomicReference<>();
        AtomicBoolean second = new AtomicBoolean();
        try {
            assertTrue(queue.submit(() -> {
                runner.set(Thread.currentThread());
                entered.countDown();
                await(release);
            }));
            assertTrue(queue.submit(() -> second.set(true)));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertNotSame(Thread.currentThread(), runner.get());
            assertTrue(runner.get().getName().startsWith("Iris Pregen Mantle Cleanup world"));
            assertEquals(2, queue.pending());
            assertFalse(second.get());
            release.countDown();
            assertTrue(queue.close(5, TimeUnit.SECONDS));
            assertTrue(second.get());
            assertEquals(0, queue.pending());
            assertTrue(reports.failures.isEmpty());
            assertTrue(reports.warnings.isEmpty());
        } finally {
            release.countDown();
            queue.close(1, TimeUnit.SECONDS);
        }
    }

    @Test
    public void closeAbandonsABlockedBacklogAfterItsTimeout() throws Exception {
        Reports reports = new Reports();
        PregenSerialWorker queue = reports.queue();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean second = new AtomicBoolean();
        try {
            assertTrue(queue.submit(() -> {
                entered.countDown();
                await(release);
            }));
            assertTrue(queue.submit(() -> second.set(true)));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertFalse(queue.close(50, TimeUnit.MILLISECONDS));
            assertFalse(queue.submit(() -> {}));
            assertEquals(1, reports.warnings.size());
            assertTrue(reports.warnings.get(0).contains("1 queued task(s)"));
        } finally {
            release.countDown();
        }
        assertFalse(second.get());
    }

    @Test
    public void aFailingCleanupIsReportedAndDoesNotStopLaterCleanups() throws Exception {
        Reports reports = new Reports();
        PregenSerialWorker queue = reports.queue();
        AtomicBoolean second = new AtomicBoolean();
        IllegalStateException failure = new IllegalStateException("plate read failed");
        assertTrue(queue.submit(() -> {
            throw failure;
        }));
        assertTrue(queue.submit(() -> second.set(true)));
        assertTrue(queue.close(5, TimeUnit.SECONDS));
        assertTrue(second.get());
        assertEquals(1, reports.failures.size());
        assertSame(failure, reports.failures.get(0));
        assertEquals(0, queue.pending());
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(30, TimeUnit.SECONDS));
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interruption);
        }
    }

    private static final class Reports {
        private final List<Throwable> failures = new CopyOnWriteArrayList<>();
        private final List<String> warnings = new CopyOnWriteArrayList<>();

        private PregenSerialWorker queue() {
            return new PregenSerialWorker("Iris Pregen Mantle Cleanup", "world",
                    (message, failure) -> failures.add(failure),
                    warnings::add);
        }
    }
}
