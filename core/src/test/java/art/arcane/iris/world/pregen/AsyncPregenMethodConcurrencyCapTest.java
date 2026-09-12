package art.arcane.iris.world.pregen;

import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class AsyncPregenMethodConcurrencyCapTest {
    @Test
    public void paperLikeRecommendedCapTracksWorkerThreads() {
        assertEquals(16, AsyncPregenMethod.computePaperLikeRecommendedCap(1));
        assertEquals(32, AsyncPregenMethod.computePaperLikeRecommendedCap(4));
        assertEquals(96, AsyncPregenMethod.computePaperLikeRecommendedCap(12));
        assertEquals(256, AsyncPregenMethod.computePaperLikeRecommendedCap(80));
        assertEquals(256, AsyncPregenMethod.computePaperLikeRecommendedCap(128));
    }

    @Test
    public void foliaRecommendedCapTracksWorkerThreads() {
        assertEquals(64, AsyncPregenMethod.computeFoliaRecommendedCap(1));
        assertEquals(96, AsyncPregenMethod.computeFoliaRecommendedCap(12));
        assertEquals(160, AsyncPregenMethod.computeFoliaRecommendedCap(20));
        assertEquals(192, AsyncPregenMethod.computeFoliaRecommendedCap(80));
    }

    @Test
    public void paperLikeConcurrencyUsesProvisionedWorkerPoolCapacity() {
        assertEquals(32, AsyncPregenMethod.resolvePaperLikeConcurrencyWorkerThreads(4, 16, 32));
        assertEquals(16, AsyncPregenMethod.resolvePaperLikeConcurrencyWorkerThreads(4, 16, 16));
        assertEquals(24, AsyncPregenMethod.resolvePaperLikeConcurrencyWorkerThreads(-1, 16, 24));
        assertEquals(16, AsyncPregenMethod.resolvePaperLikeConcurrencyWorkerThreads(-1, 16, 8));
        assertEquals(128, AsyncPregenMethod.computePaperLikeRecommendedCap(
                AsyncPregenMethod.resolvePaperLikeConcurrencyWorkerThreads(4, 16, 16)));
    }

    @Test
    public void foliaConcurrencyStillUsesBroaderRuntimeCapacity() {
        assertEquals(32, AsyncPregenMethod.resolveFoliaConcurrencyWorkerThreads(4, 16, 32));
        assertEquals(16, AsyncPregenMethod.resolveFoliaConcurrencyWorkerThreads(-1, 16, 12));
    }

    @Test
    public void strictSerialOverridesRecommendedConcurrencyCap() {
        assertEquals(1, AsyncPregenMethod.selectConcurrencyCap(128, true));
        assertEquals(128, AsyncPregenMethod.selectConcurrencyCap(128, false));
        assertEquals(1, AsyncPregenMethod.selectConcurrencyCap(0, false));
    }

    @Test
    public void coldStartMatchesAvailableWorkersBeforeAdaptiveGrowth() {
        assertEquals(1, AsyncPregenMethod.computeInitialInFlightLimit(1, 16));
        assertEquals(4, AsyncPregenMethod.computeInitialInFlightLimit(128, 1));
        assertEquals(16, AsyncPregenMethod.computeInitialInFlightLimit(128, 16));
        assertEquals(6, AsyncPregenMethod.computeInitialInFlightLimit(6, 16));
    }

    @Test
    public void adaptiveRecoveryGrowsWithoutSubmittingColdWaves() {
        assertEquals(17, AsyncPregenMethod.nextAdaptiveInFlightLimit(16, 128));
        assertEquals(128, AsyncPregenMethod.nextAdaptiveInFlightLimit(128, 128));
        assertEquals(1, AsyncPregenMethod.nextAdaptiveInFlightLimit(0, 0));
    }

    @Test
    public void slowRequestObservationDoesNotCompleteOrReplacePendingFuture() {
        CompletableFuture<String> request = new CompletableFuture<>();
        AtomicInteger slowRequests = new AtomicInteger();

        CompletableFuture<String> observed = AsyncPregenMethod.observeSlowRequest(
                request,
                Runnable::run,
                slowRequests::incrementAndGet);

        assertSame(request, observed);
        assertFalse(request.isDone());
        assertEquals(1, slowRequests.get());
        request.complete("generated");
        assertEquals("generated", observed.join());
    }

    @Test
    public void slowRequestObservationIgnoresCompletedFuture() {
        CompletableFuture<String> request = CompletableFuture.completedFuture("generated");
        AtomicInteger slowRequests = new AtomicInteger();

        AsyncPregenMethod.observeSlowRequest(request, Runnable::run, slowRequests::incrementAndGet);

        assertEquals(0, slowRequests.get());
        assertEquals("generated", request.join());
    }

    @Test
    public void closeDrainWaitsPastWarningIntervalsUntilEveryPermitReturns() throws Exception {
        PregenAdmissionGate gate = new PregenAdmissionGate(2, 5L, System::currentTimeMillis);
        assertNotNull(gate.admit(() -> false, () -> false));
        assertNotNull(gate.admit(() -> false, () -> false));
        AtomicInteger warnings = new AtomicInteger();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> drain = executor.submit(() -> gate.awaitDrain(
                    10L,
                    TimeUnit.MILLISECONDS,
                    warnings::incrementAndGet
            ));

            while (warnings.get() == 0) {
                Thread.onSpinWait();
            }
            gate.release();
            gate.release();

            assertFalse(drain.get(1L, TimeUnit.SECONDS));
            assertTrue(warnings.get() > 0);
            assertEquals(2, gate.availablePermits());
        } finally {
            executor.shutdownNow();
        }
    }
}
