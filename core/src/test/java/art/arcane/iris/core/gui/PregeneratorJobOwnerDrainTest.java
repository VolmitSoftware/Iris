package art.arcane.iris.core.gui;

import art.arcane.iris.core.pregenerator.IrisPregenerator;
import art.arcane.volmlib.util.format.MemoryMonitor;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class PregeneratorJobOwnerDrainTest {
    @Test
    public void ownerPollingCompletesTheRequestThatKeepsTheWorkerAlive() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Thread owner = Thread.currentThread();
            AtomicInteger polls = new AtomicInteger();
            assertTrue(PregeneratorJob.shutdownAndWait(1_000L, () -> {
                assertSame(owner, Thread.currentThread());
                polls.incrementAndGet();
                fixture.completion.complete(null);
            }));
            assertTrue(polls.get() > 0);
            assertFalse(fixture.worker.isAlive());
            assertNull(PregeneratorJob.getInstance());
            verify(fixture.pregenerator).close();
        }
    }

    @Test
    public void timeoutKeepsThePendingJobAndDoesNotCompleteItsRequest() throws Exception {
        try (Fixture fixture = new Fixture()) {
            AtomicInteger polls = new AtomicInteger();
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> PregeneratorJob.shutdownAndWait(20L, polls::incrementAndGet));
            assertEquals("Timed out while stopping the Iris pregenerator after 20ms.", failure.getMessage());
            assertTrue(polls.get() > 0);
            assertTrue(fixture.worker.isAlive());
            assertFalse(fixture.completion.isDone());
            assertSame(fixture.job, PregeneratorJob.getInstance());
        }
    }

    @Test
    public void interruptedOwnerKeepsTheJobAndOriginalInterruption() throws Exception {
        try (Fixture fixture = new Fixture()) {
            try {
                IllegalStateException failure = assertThrows(IllegalStateException.class,
                        () -> PregeneratorJob.shutdownAndWait(1_000L, () -> Thread.currentThread().interrupt()));
                assertTrue(failure.getCause() instanceof InterruptedException);
                assertTrue(Thread.currentThread().isInterrupted());
                assertSame(fixture.job, PregeneratorJob.getInstance());
                assertFalse(fixture.completion.isDone());
            } finally {
                Thread.interrupted();
            }
        }
    }

    @Test
    public void ownerTaskFailureEscapesWithItsCauseAndRetainsTheJob() throws Exception {
        try (Fixture fixture = new Fixture()) {
            IllegalStateException expected = new IllegalStateException("Native task failed", new Exception("Chunk cause"));
            IllegalStateException actual = assertThrows(IllegalStateException.class,
                    () -> PregeneratorJob.shutdownAndWait(1_000L, () -> {
                        throw expected;
                    }));
            assertSame(expected, actual);
            assertSame(expected.getCause(), actual.getCause());
            assertSame(fixture.job, PregeneratorJob.getInstance());
            assertFalse(fixture.completion.isDone());
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final PregeneratorJob job = mock(PregeneratorJob.class, CALLS_REAL_METHODS);
        private final IrisPregenerator pregenerator = mock(IrisPregenerator.class);
        private final CompletableFuture<Void> completion = new CompletableFuture<>();
        private final AtomicReference<PregeneratorJob> instance;
        private final PregeneratorJob previous;
        private final Thread worker;

        private Fixture() throws Exception {
            CountDownLatch started = new CountDownLatch(1);
            worker = new Thread(() -> {
                started.countDown();
                completion.join();
            }, "Pregen owner drain test");
            set("worker", worker);
            set("pregenerator", pregenerator);
            set("monitor", mock(MemoryMonitor.class));
            set("stopRequested", new AtomicBoolean());
            set("bounds", new PregenRenderSnapshot.Bounds(0, 0, 0, 0));
            instance = instance();
            previous = instance.getAndSet(job);
            worker.start();
            assertTrue(started.await(5L, TimeUnit.SECONDS));
        }

        @Override
        public void close() throws Exception {
            completion.complete(null);
            worker.join(5_000L);
            instance.set(previous);
            assertFalse(worker.isAlive());
        }

        private void set(String name, Object value) throws Exception {
            Field field = PregeneratorJob.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(job, value);
        }

        @SuppressWarnings("unchecked")
        private static AtomicReference<PregeneratorJob> instance() throws Exception {
            Field field = PregeneratorJob.class.getDeclaredField("instance");
            field.setAccessible(true);
            return (AtomicReference<PregeneratorJob>) field.get(null);
        }
    }
}
