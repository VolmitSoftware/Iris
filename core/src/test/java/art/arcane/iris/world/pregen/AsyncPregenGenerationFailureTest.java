package art.arcane.iris.world.pregen;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AsyncPregenGenerationFailureTest {
    @Test
    public void aGenerationFailureFailsEveryOutstandingRequestAndReleasesTheirPermits() throws Exception {
        Harness harness = Harness.withOutstanding(2);
        IllegalStateException cause = new IllegalStateException("Accepted hydrology layer exceeds the mantle bounds");

        harness.method.onChunkGenerationFailed(48, -4, cause);

        assertEquals(2, harness.admission.availablePermits());
        assertEquals(2, harness.failed.get());
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
        Harness harness = Harness.withOutstanding(2);
        harness.method.onChunkGenerationFailed(48, -4, new IllegalStateException("boom"));

        harness.completeChunk(10, 0, null, null);
        harness.completeChunk(11, 0, null, new IllegalStateException("late failure"));

        assertEquals(2, harness.failed.get());
        assertEquals(0, harness.completed.get());
        assertEquals(0, harness.inFlight.get());
        assertEquals(2, harness.admission.availablePermits());
    }

    @Test
    public void closingAbandonsRequestsThatNeverCompleteAndReturnsTheirPermits() throws Exception {
        Harness harness = Harness.withOutstanding(2);

        harness.abandon(new PregenAdmissionGate.Drain(false, false, 2));

        assertEquals(2, harness.admission.availablePermits());
        assertEquals(2, harness.failed.get());
        assertEquals(0, harness.requests.size());
        verify(harness.listener).onChunkFailed(10, 0);
        verify(harness.listener).onChunkFailed(11, 0);
    }

    private static final class Harness {
        private final AsyncPregenMethod method;
        private final PregenAdmissionGate admission;
        private final PregenInFlightRequests requests;
        private final PregenListener listener;
        private final AtomicInteger inFlight;
        private final AtomicLong failed;
        private final AtomicLong completed;

        private Harness(AsyncPregenMethod method, PregenAdmissionGate admission, PregenInFlightRequests requests,
                        PregenListener listener, AtomicInteger inFlight, AtomicLong failed, AtomicLong completed) {
            this.method = method;
            this.admission = admission;
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
            PregenAdmissionGate admission = new PregenAdmissionGate(outstanding, 50L, System::currentTimeMillis);
            PregenInFlightRequests requests = new PregenInFlightRequests();
            PregenListener listener = mock(PregenListener.class);
            AtomicInteger inFlight = new AtomicInteger(outstanding);
            AtomicLong failed = new AtomicLong();
            AtomicLong completed = new AtomicLong();
            for (int index = 0; index < outstanding; index++) {
                assertNotNull(admission.admit(() -> false, () -> false));
                requests.add(PregenInFlightRequests.key(10 + index, 0), listener);
            }
            set(method, "world", world);
            set(method, "admission", admission);
            set(method, "backpressure", mock(PregenMantleBackpressure.class));
            set(method, "mantleCleanup", mock(PregenSerialWorker.class));
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
            set(method, "threads", outstanding);
            set(method, "closing", new AtomicBoolean());
            return new Harness(method, admission, requests, listener, inFlight, failed, completed);
        }

        void completeChunk(int x, int z, Chunk chunk, Throwable throwable) throws Exception {
            Method complete = AsyncPregenMethod.class.getDeclaredMethod(
                    "completeChunk", int.class, int.class, PregenListener.class, Chunk.class, Throwable.class);
            complete.setAccessible(true);
            complete.invoke(method, x, z, listener, chunk, throwable);
        }

        void abandon(PregenAdmissionGate.Drain drain) throws Exception {
            Method abandon = AsyncPregenMethod.class.getDeclaredMethod(
                    "abandonOutstandingRequests", PregenAdmissionGate.Drain.class);
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
