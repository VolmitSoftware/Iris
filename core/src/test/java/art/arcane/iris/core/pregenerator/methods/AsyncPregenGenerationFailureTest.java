package art.arcane.iris.core.pregenerator.methods;

import art.arcane.iris.core.pregenerator.PregenListener;
import art.arcane.iris.core.pregenerator.PregenMantleBackpressure;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AsyncPregenGenerationFailureTest {
    private static final int OUTSTANDING_REQUESTS = 2;

    @Test
    public void aGenerationFailureFailsEveryOutstandingRequestAndReleasesTheirPermits() throws Exception {
        Harness harness = Harness.withOutstanding(OUTSTANDING_REQUESTS);
        IllegalStateException cause = new IllegalStateException("Accepted hydrology layer exceeds the mantle bounds");

        harness.method.onChunkGenerationFailed(48, -4, cause);

        assertEquals(OUTSTANDING_REQUESTS, harness.semaphore.availablePermits());
        assertEquals(OUTSTANDING_REQUESTS, harness.failed.get());
        assertEquals(0, harness.inFlight.get());
        assertEquals(0, harness.requests.size());
        verify(harness.listener).onChunkFailed(10, 0);
        verify(harness.listener).onChunkFailed(11, 0);

        IllegalStateException abort = assertThrows(IllegalStateException.class,
                () -> harness.method.generateChunk(12, 0, harness.listener));
        assertSame(cause, abort.getCause());
        verify(harness.listener, never()).onChunkGenerating(12, 0);
    }

    @Test
    public void aLateCompletionForASettledRequestIsIgnored() throws Exception {
        Harness harness = Harness.withOutstanding(OUTSTANDING_REQUESTS);
        harness.method.onChunkGenerationFailed(48, -4, new IllegalStateException("boom"));

        harness.completeChunk(10, 0, null, null);
        harness.completeChunk(11, 0, null, new IllegalStateException("late failure"));

        assertEquals(OUTSTANDING_REQUESTS, harness.failed.get());
        assertEquals(0, harness.completed.get());
        assertEquals(0, harness.inFlight.get());
        assertEquals(OUTSTANDING_REQUESTS, harness.semaphore.availablePermits());
    }

    @Test
    public void closingAbandonsRequestsThatNeverCompleteAndReturnsTheirPermits() throws Exception {
        Harness harness = Harness.withOutstanding(OUTSTANDING_REQUESTS);

        harness.abandon(new AsyncPregenMethod.Drain(false, false, OUTSTANDING_REQUESTS));

        assertEquals(OUTSTANDING_REQUESTS, harness.semaphore.availablePermits());
        assertEquals(OUTSTANDING_REQUESTS, harness.failed.get());
        assertEquals(0, harness.requests.size());
        verify(harness.listener).onChunkFailed(10, 0);
        verify(harness.listener).onChunkFailed(11, 0);
    }

    private static final class Harness {
        private final AsyncPregenMethod method;
        private final Semaphore semaphore;
        private final PregenInFlightRequests requests;
        private final PregenListener listener;
        private final AtomicInteger inFlight;
        private final AtomicLong failed;
        private final AtomicLong completed;

        private Harness(AsyncPregenMethod method, Semaphore semaphore, PregenInFlightRequests requests,
                        PregenListener listener, AtomicInteger inFlight, AtomicLong failed, AtomicLong completed) {
            this.method = method;
            this.semaphore = semaphore;
            this.requests = requests;
            this.listener = listener;
            this.inFlight = inFlight;
            this.failed = failed;
            this.completed = completed;
        }

        static Harness withOutstanding(int outstanding) throws Exception {
            AsyncPregenMethod method = mock(AsyncPregenMethod.class, CALLS_REAL_METHODS);
            World world = mock(World.class);
            when(world.getName()).thenReturn("qa");
            Semaphore semaphore = new Semaphore(outstanding, true);
            semaphore.acquire(outstanding);
            PregenInFlightRequests requests = new PregenInFlightRequests();
            PregenListener listener = mock(PregenListener.class);
            AtomicInteger inFlight = new AtomicInteger(outstanding);
            AtomicLong failed = new AtomicLong();
            AtomicLong completed = new AtomicLong();
            for (int index = 0; index < outstanding; index++) {
                requests.add(PregenInFlightRequests.key(10 + index, 0), listener);
            }
            set(method, "world", world);
            set(method, "semaphore", semaphore);
            set(method, "backpressure", mock(PregenMantleBackpressure.class));
            set(method, "inFlightRequests", requests);
            set(method, "generationFailure", new AtomicReference<>());
            set(method, "regionPending", new ConcurrentHashMap<>());
            set(method, "lastFailedReleaseLogAt", new AtomicLong());
            set(method, "slowRequestWarnIntervalMs", 15_000);
            set(method, "inFlight", inFlight);
            set(method, "submitted", new AtomicLong(outstanding));
            set(method, "completed", completed);
            set(method, "failed", failed);
            set(method, "lastProgressAt", new AtomicLong());
            set(method, "adaptiveInFlightLimit", new AtomicInteger(outstanding));
            set(method, "permitMonitor", new Object());
            set(method, "threads", outstanding);
            set(method, "closing", new AtomicBoolean());
            return new Harness(method, semaphore, requests, listener, inFlight, failed, completed);
        }

        void completeChunk(int x, int z, Chunk chunk, Throwable throwable) throws Exception {
            Method complete = AsyncPregenMethod.class.getDeclaredMethod(
                    "completeChunk", int.class, int.class, PregenListener.class, Chunk.class, Throwable.class);
            complete.setAccessible(true);
            complete.invoke(method, x, z, listener, chunk, throwable);
        }

        void abandon(AsyncPregenMethod.Drain drain) throws Exception {
            Method abandon = AsyncPregenMethod.class.getDeclaredMethod(
                    "abandonOutstandingRequests", AsyncPregenMethod.Drain.class);
            abandon.setAccessible(true);
            abandon.invoke(method, drain);
        }

        private static void set(AsyncPregenMethod method, String name, Object value) throws Exception {
            Field field = AsyncPregenMethod.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(method, value);
        }
    }
}
