package art.arcane.iris.world.pregen;

import art.arcane.iris.testsupport.Await;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PregenMantleBackpressureTest {
    @Test
    public void aDisabledPlateCapNeverAsksForAMantle() {
        AtomicInteger supplierCalls = new AtomicInteger();
        Supplier<Mantle> supplier = () -> {
            supplierCalls.incrementAndGet();
            return null;
        };

        backpressure(supplier, 0, 1_000L, () -> {
        }).enforceMantleBudget();

        assertEquals(0, supplierCalls.get());
    }

    @Test
    public void aFailingMantleProbeDoesNotPropagate() {
        AtomicInteger timeouts = new AtomicInteger();

        backpressure(() -> {
            throw new IllegalStateException("no mantle here");
        }, 4, 1_000L, timeouts::incrementAndGet).enforceMantleBudget();

        assertEquals(0, timeouts.get());
    }

    @Test
    public void residencyAtTwiceThePlateCapIsNotTrimmed() {
        Mantle mantle = mock(Mantle.class);
        when(mantle.getLoadedRegionCount()).thenReturn(8);

        backpressure(() -> mantle, 4, 1_000L, () -> {
        }).enforceMantleBudget();

        verify(mantle, never()).trim(0L, 0);
        verify(mantle, never()).unloadTectonicPlate(0);
    }

    @Test
    public void residencyOverTheHardCapIsTrimmedUntilItFits() {
        Mantle mantle = mock(Mantle.class);
        when(mantle.getLoadedRegionCount()).thenReturn(20, 20, 3);

        backpressure(() -> mantle, 4, 1_000L, () -> {
        }).enforceMantleBudget();

        verify(mantle, times(1)).trim(0L, 0);
        verify(mantle, times(1)).unloadTectonicPlate(0);
    }

    @Test
    public void aTrimFailureEndsTheWaitInsteadOfSpinning() {
        Mantle mantle = mock(Mantle.class);
        when(mantle.getLoadedRegionCount()).thenReturn(20);
        doThrow(new IllegalStateException("mantle is gone")).when(mantle).trim(0L, 0);
        AtomicInteger timeouts = new AtomicInteger();

        backpressure(() -> mantle, 4, 1_000L, timeouts::incrementAndGet).enforceMantleBudget();

        verify(mantle, never()).unloadTectonicPlate(0);
        assertEquals(0, timeouts.get());
    }

    @Test
    public void anExhaustedBudgetReportsTheTimeoutOnceAndProceeds() {
        Mantle mantle = mock(Mantle.class);
        when(mantle.getLoadedRegionCount()).thenReturn(20);
        AtomicInteger timeouts = new AtomicInteger();

        backpressure(() -> mantle, 4, 0L, timeouts::incrementAndGet).enforceMantleBudget();

        assertEquals(1, timeouts.get());
    }

    @Test
    public void cancellationStopsTheWaitWithoutReportingATimeout() {
        Mantle mantle = mock(Mantle.class);
        when(mantle.getLoadedRegionCount()).thenReturn(20);
        AtomicInteger timeouts = new AtomicInteger();
        PregenMantleBackpressure backpressure = new PregenMantleBackpressure(
                () -> mantle, 4, 5, 0L, timeouts::incrementAndGet, () -> "cancelled", () -> true);

        backpressure.enforceMantleBudget();

        verify(mantle, never()).trim(0L, 0);
        verify(mantle, never()).unloadTectonicPlate(0);
        assertEquals(0, timeouts.get());
    }

    @Test
    public void aFailingCancellationProbeIsTreatedAsRunning() {
        Mantle mantle = mock(Mantle.class);
        when(mantle.getLoadedRegionCount()).thenReturn(20);
        AtomicInteger timeouts = new AtomicInteger();
        PregenMantleBackpressure backpressure = new PregenMantleBackpressure(
                () -> mantle, 4, 5, 0L, timeouts::incrementAndGet, () -> "probing", () -> {
            throw new IllegalStateException("cancellation probe exploded");
        });

        backpressure.enforceMantleBudget();

        assertEquals(1, timeouts.get());
    }

    @Test
    public void signalledProgressReleasesTheWaitAheadOfThePollInterval() throws Exception {
        Mantle mantle = mock(Mantle.class);
        when(mantle.getLoadedRegionCount()).thenReturn(20, 20, 20, 3);
        PregenMantleBackpressure backpressure = new PregenMantleBackpressure(
                () -> mantle, 4, 600_000, 600_000L, () -> {
        }, () -> "waiting");
        Thread waiter = new Thread(backpressure::enforceMantleBudget, "pregen-backpressure-waiter");

        waiter.start();
        Await.until("the parked backpressure wait to be released", Duration.ofSeconds(10L), () -> {
            backpressure.signalProgress();
            return !waiter.isAlive();
        });
        waiter.join(1_000L);

        assertFalse(waiter.isAlive());
    }

    private static PregenMantleBackpressure backpressure(
            Supplier<Mantle> mantleSupplier,
            int maxResidentTectonicPlates,
            long timeoutMs,
            Runnable onBudgetTimeout
    ) {
        return new PregenMantleBackpressure(
                mantleSupplier, maxResidentTectonicPlates, 5, timeoutMs, onBudgetTimeout, () -> "test");
    }
}
