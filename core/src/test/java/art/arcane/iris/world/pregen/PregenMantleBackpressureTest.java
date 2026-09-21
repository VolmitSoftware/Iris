package art.arcane.iris.world.pregen;

import art.arcane.iris.testsupport.Await;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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

        verify(mantle, never()).trim(0L);
        verify(mantle, never()).unloadTectonicPlate(0);
    }

    @Test
    public void residencyOverTheHardCapIsTrimmedUntilItFits() {
        Mantle mantle = mock(Mantle.class);
        when(mantle.getLoadedRegionCount()).thenReturn(20, 20, 3);

        backpressure(() -> mantle, 4, 1_000L, () -> {
        }).enforceMantleBudget();

        verify(mantle, times(1)).trim(0L);
        verify(mantle, times(1)).unloadTectonicPlate(0);
    }

    @Test
    public void aTrimFailureEndsTheWaitInsteadOfSpinning() {
        Mantle mantle = mock(Mantle.class);
        when(mantle.getLoadedRegionCount()).thenReturn(20);
        doThrow(new IllegalStateException("mantle is gone")).when(mantle).trim(0L);
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

        verify(mantle, never()).trim(0L);
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

    @Test
    public void heapPressurePersistsIdlePlatesBeforeGcEvenBelowOrWithoutACountCap() {
        for (int cap : new int[]{0, 9, 16, 96}) {
            Mantle mantle = mock(Mantle.class);
            when(mantle.getLoadedRegionCount()).thenReturn(9);
            List<String> actions = new ArrayList<>();
            AtomicBoolean pressured = new AtomicBoolean(true);
            AtomicInteger timeouts = new AtomicInteger();
            when(mantle.saveOldestIdleTectonicPlate()).thenAnswer(invocation -> {
                actions.add("persist-and-unload");
                return true;
            });

            backpressure(() -> mantle, cap, 1_000L, timeouts::incrementAndGet)
                    .awaitHeapHeadroom(pressured::get, () -> {
                        actions.add("gc");
                        pressured.set(false);
                    });

            assertEquals(List.of("persist-and-unload", "gc"), actions);
            assertEquals(0, timeouts.get());
            verify(mantle, never()).trim(0L, 0);
            verify(mantle, never()).trim(0L);
            verify(mantle, never()).unloadTectonicPlate(0);
        }
    }

    @Test
    public void heapHeadroomDoesNotEvictRecentlyUsedPlatesWithoutPressure() {
        Mantle mantle = mock(Mantle.class);
        AtomicInteger collections = new AtomicInteger();

        backpressure(() -> mantle, 16, 1_000L, () -> {
        }).awaitHeapHeadroom(() -> false, collections::incrementAndGet);

        verify(mantle, never()).trim(0L);
        verify(mantle, never()).unloadTectonicPlate(0);
        verify(mantle, never()).saveOldestIdleTectonicPlate();
        assertEquals(0, collections.get());
    }

    @Test
    public void pinnedPlatesStillRespectThePressureWaitDeadline() {
        Mantle mantle = mock(Mantle.class);
        when(mantle.getLoadedRegionCount()).thenReturn(9);
        when(mantle.saveOldestIdleTectonicPlate()).thenReturn(false);
        AtomicInteger collections = new AtomicInteger();
        AtomicInteger timeouts = new AtomicInteger();

        backpressure(() -> mantle, 16, 0L, timeouts::incrementAndGet)
                .awaitHeapHeadroom(() -> true, collections::incrementAndGet);

        verify(mantle, times(1)).saveOldestIdleTectonicPlate();
        verify(mantle, never()).unloadTectonicPlate(0);
        assertEquals(1, collections.get());
        assertEquals(1, timeouts.get());
    }

    @Test
    public void failedPressureEvictionStillAttemptsGcAndRespectsTheDeadline() {
        Mantle mantle = mock(Mantle.class);
        doThrow(new IllegalStateException("plate write failed")).when(mantle).saveOldestIdleTectonicPlate();
        AtomicInteger collections = new AtomicInteger();
        AtomicInteger timeouts = new AtomicInteger();

        backpressure(() -> mantle, 16, 0L, timeouts::incrementAndGet)
                .awaitHeapHeadroom(() -> true, collections::incrementAndGet);

        assertEquals(1, collections.get());
        assertEquals(1, timeouts.get());
    }

    @Test
    public void cancelledHeapPressureDoesNotEvictOrCollect() {
        Mantle mantle = mock(Mantle.class);
        AtomicInteger collections = new AtomicInteger();
        AtomicInteger timeouts = new AtomicInteger();
        PregenMantleBackpressure backpressure = new PregenMantleBackpressure(
                () -> mantle, 16, 5, 0L, timeouts::incrementAndGet, () -> "cancelled", () -> true);

        backpressure.awaitHeapHeadroom(() -> true, collections::incrementAndGet);

        verify(mantle, never()).trim(0L);
        verify(mantle, never()).unloadTectonicPlate(0);
        verify(mantle, never()).saveOldestIdleTectonicPlate();
        assertEquals(0, collections.get());
        assertEquals(0, timeouts.get());
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
