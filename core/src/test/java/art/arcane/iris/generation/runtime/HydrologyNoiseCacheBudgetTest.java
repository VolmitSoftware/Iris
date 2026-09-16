package art.arcane.iris.generation.runtime;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HydrologyNoiseCacheBudgetTest {
    @Test
    public void reservationsClampToTheRemainingSharedCapacity() {
        HydrologyNoiseCacheBudget budget = new HydrologyNoiseCacheBudget(4_096L * 15L + 4_095L);
        HydrologyNoiseCacheBudget.Reservation first = budget.reserve(4, 10);
        HydrologyNoiseCacheBudget.Reservation second = budget.reserve(2, 10);
        assertEquals(3, first.additionalChunks());
        assertEquals(1, second.additionalChunks());
        assertEquals(14L * 4_096L, budget.reservedBytes());
        first.close();
        first.close();
        assertEquals(2L * 4_096L, budget.reservedBytes());
        second.close();
        assertEquals(0L, budget.reservedBytes());
    }

    @Test
    public void emptyBudgetAndZeroDemandReserveNothing() {
        HydrologyNoiseCacheBudget budget = new HydrologyNoiseCacheBudget(0L);
        try (HydrologyNoiseCacheBudget.Reservation reservation = budget.reserve(5, 32_768)) {
            assertEquals(0, reservation.additionalChunks());
        }
        HydrologyNoiseCacheBudget available = new HydrologyNoiseCacheBudget(1_000_000L);
        try (HydrologyNoiseCacheBudget.Reservation reservation = available.reserve(5, 0)) {
            assertEquals(0, reservation.additionalChunks());
        }
        assertEquals(0L, budget.reservedBytes());
        assertEquals(0L, available.reservedBytes());
    }

    @Test
    public void requestsCannotExceedTheCapacityLimit() {
        HydrologyNoiseCacheBudget budget = new HydrologyNoiseCacheBudget(Long.MAX_VALUE);
        try (HydrologyNoiseCacheBudget.Reservation reservation = budget.reserve(5, 32_768)) {
            assertEquals(32_768, reservation.additionalChunks());
            assertEquals(5L * 32_768L * 4_096L, budget.reservedBytes());
        }
        assertThrows(IllegalArgumentException.class, () -> budget.reserve(0, 10));
        assertThrows(IllegalArgumentException.class, () -> budget.reserve(5, -1));
        assertThrows(IllegalArgumentException.class, () -> budget.reserve(5, 32_769));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyNoiseCacheBudget(-1L));
    }

    @Test(timeout = 10_000L)
    public void concurrentOwnersNeverOversubscribeAndReleaseIndependently() throws Exception {
        long maximumBytes = 4_096L * 503L;
        HydrologyNoiseCacheBudget budget = new HydrologyNoiseCacheBudget(maximumBytes);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<HydrologyNoiseCacheBudget.Reservation>> pending = new ArrayList<>();
        try (ExecutorService workers = Executors.newFixedThreadPool(16)) {
            for (int index = 0; index < 32; index++) {
                pending.add(workers.submit(() -> {
                    assertTrue(start.await(5L, TimeUnit.SECONDS));
                    return budget.reserve(5, 50);
                }));
            }
            start.countDown();
            List<HydrologyNoiseCacheBudget.Reservation> reservations = new ArrayList<>();
            long expectedBytes = 0L;
            for (Future<HydrologyNoiseCacheBudget.Reservation> future : pending) {
                HydrologyNoiseCacheBudget.Reservation reservation = future.get(5L, TimeUnit.SECONDS);
                reservations.add(reservation);
                expectedBytes += reservation.additionalChunks() * 5L * 4_096L;
            }
            assertEquals(expectedBytes, budget.reservedBytes());
            assertTrue(expectedBytes <= maximumBytes);
            List<Future<?>> releases = new ArrayList<>();
            for (HydrologyNoiseCacheBudget.Reservation reservation : reservations) {
                releases.add(workers.submit(() -> {
                    reservation.close();
                    reservation.close();
                }));
            }
            for (Future<?> release : releases) {
                release.get(5L, TimeUnit.SECONDS);
            }
            assertEquals(0L, budget.reservedBytes());
        }
    }
}
