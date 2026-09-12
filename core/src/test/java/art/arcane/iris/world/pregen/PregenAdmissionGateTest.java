package art.arcane.iris.world.pregen;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PregenAdmissionGateTest {
    private static final long GENEROUS_BOUND_MS = 60_000L;

    @Test
    public void admissionConsumesOnePermitAndReturnsItOnRelease() throws Exception {
        PregenAdmissionGate gate = new PregenAdmissionGate(2, GENEROUS_BOUND_MS, System::currentTimeMillis);

        assertEquals(2, gate.availablePermits());
        assertNotNull(gate.admit(() -> false, () -> false));
        assertEquals(1, gate.availablePermits());
        gate.release();
        assertEquals(2, gate.availablePermits());
    }

    @Test
    public void releaseNeverPushesThePermitCountAboveTheConfiguredCeiling() throws Exception {
        PregenAdmissionGate gate = new PregenAdmissionGate(1, GENEROUS_BOUND_MS, System::currentTimeMillis);

        gate.release();
        gate.release();

        assertEquals(1, gate.availablePermits());
        assertNotNull(gate.admit(() -> false, () -> false));
        assertEquals(0, gate.availablePermits());
    }

    @Test
    public void aBlockedAdmissionWakesOnTheReleaseSignalRatherThanTheTimeout() throws Exception {
        PregenAdmissionGate gate = new PregenAdmissionGate(1, GENEROUS_BOUND_MS, System::currentTimeMillis);
        assertNotNull(gate.admit(() -> false, () -> false));
        CountDownLatch started = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<PregenAdmissionGate.Wait> blocked = executor.submit(() -> {
                started.countDown();
                return gate.admit(() -> false, () -> false);
            });

            assertTrue(started.await(5, TimeUnit.SECONDS));
            while (gate.availablePermits() != 0) {
                Thread.onSpinWait();
            }
            gate.release();

            assertNotNull(blocked.get(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void aBlockedAdmissionWakesOnTheAdaptiveLimitSignalWithoutWaitingOutTheBound() throws Exception {
        PregenAdmissionGate gate = new PregenAdmissionGate(4, GENEROUS_BOUND_MS, System::currentTimeMillis);
        AtomicBoolean overLimit = new AtomicBoolean(true);
        CountDownLatch started = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<PregenAdmissionGate.Wait> blocked = executor.submit(() -> {
                started.countDown();
                return gate.admit(overLimit::get, () -> false);
            });

            assertTrue(started.await(5, TimeUnit.SECONDS));
            Thread.yield();
            overLimit.set(false);
            gate.wake();

            assertNotNull(blocked.get(5, TimeUnit.SECONDS));
            assertEquals(3, gate.availablePermits());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void aCancelledAdmissionReturnsNothingAndConsumesNoPermit() throws Exception {
        PregenAdmissionGate gate = new PregenAdmissionGate(1, GENEROUS_BOUND_MS, System::currentTimeMillis);

        assertNull(gate.admit(() -> true, () -> true));
        assertEquals(1, gate.availablePermits());
    }

    @Test
    public void aCancellationRaisedWhileBlockedEndsTheWait() throws Exception {
        PregenAdmissionGate gate = new PregenAdmissionGate(1, GENEROUS_BOUND_MS, System::currentTimeMillis);
        assertNotNull(gate.admit(() -> false, () -> false));
        AtomicBoolean cancelled = new AtomicBoolean();
        CountDownLatch started = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<PregenAdmissionGate.Wait> blocked = executor.submit(() -> {
                started.countDown();
                return gate.admit(() -> false, cancelled::get);
            });

            assertTrue(started.await(5, TimeUnit.SECONDS));
            Thread.yield();
            cancelled.set(true);
            gate.wake();

            assertNull(blocked.get(5, TimeUnit.SECONDS));
            assertEquals(0, gate.availablePermits());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void theAdaptiveAndPermitWaitsAreAttributedSeparately() throws Exception {
        AtomicLong clock = new AtomicLong();
        AtomicBoolean overLimit = new AtomicBoolean(true);
        PregenAdmissionGate gate = new PregenAdmissionGate(1, 5L, () -> clock.addAndGet(10L));
        assertNotNull(gate.admit(() -> false, () -> false));

        AtomicInteger passes = new AtomicInteger();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<PregenAdmissionGate.Wait> blocked = executor.submit(() -> gate.admit(() -> {
                if (passes.incrementAndGet() > 2) {
                    overLimit.set(false);
                }
                return overLimit.get();
            }, () -> false));

            while (passes.get() <= 3) {
                Thread.onSpinWait();
            }
            gate.release();

            PregenAdmissionGate.Wait wait = blocked.get(5, TimeUnit.SECONDS);
            assertNotNull(wait);
            assertTrue("adaptive wait was not attributed: " + wait, wait.adaptiveMs() > 0L);
            assertTrue("permit wait was not attributed: " + wait, wait.permitMs() > 0L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void drainReturnsOnlyAfterEveryPermitIsBack() throws Exception {
        PregenAdmissionGate gate = new PregenAdmissionGate(2, GENEROUS_BOUND_MS, System::currentTimeMillis);
        assertNotNull(gate.admit(() -> false, () -> false));
        assertNotNull(gate.admit(() -> false, () -> false));
        AtomicInteger warnings = new AtomicInteger();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> drain = executor.submit(() -> gate.awaitDrain(5L, TimeUnit.MILLISECONDS, warnings::incrementAndGet));

            while (warnings.get() < 2) {
                Thread.onSpinWait();
            }
            gate.release();
            gate.release();

            assertEquals(Boolean.FALSE, drain.get(5, TimeUnit.SECONDS));
            assertEquals(2, gate.availablePermits());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void aGateWithoutPermitsIsRejected() {
        new PregenAdmissionGate(0, GENEROUS_BOUND_MS, System::currentTimeMillis);
    }

    @Test(expected = IllegalArgumentException.class)
    public void aGateWithoutAWaitBoundIsRejected() {
        new PregenAdmissionGate(1, 0L, System::currentTimeMillis);
    }
}
