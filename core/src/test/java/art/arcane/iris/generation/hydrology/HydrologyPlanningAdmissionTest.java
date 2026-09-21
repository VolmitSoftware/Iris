package art.arcane.iris.generation.hydrology;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HydrologyPlanningAdmissionTest {
    @Test
    public void rootLimitAccountsForHeapAndProcessors() {
        long gibibyte = 1024L * 1024L * 1024L;
        assertEquals(1, HydrologyPlanningAdmission.rootLimit(gibibyte, 32));
        assertEquals(1, HydrologyPlanningAdmission.rootLimit(2L * gibibyte, 32));
        assertEquals(2, HydrologyPlanningAdmission.rootLimit(4L * gibibyte, 32));
        assertEquals(2, HydrologyPlanningAdmission.rootLimit(64L * gibibyte, 2));
        assertEquals(8, HydrologyPlanningAdmission.rootLimit(64L * gibibyte, 32));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlanningAdmission(0));
    }

    @Test
    public void speculativeParallelismUsesBothWorkersAndAvailableProcessors() {
        int[][] cases = {
                {16, 1, 1}, {16, 2, 2}, {16, 4, 4}, {16, 32, 16},
                {1, 16, 1}, {2, 16, 2}, {3, 16, 3}, {4, 16, 4},
                {-1, 16, 1}, {16, 0, 1}
        };
        for (int[] parameters : cases) {
            assertEquals("pool=" + parameters[0] + " processors=" + parameters[1], parameters[2],
                    HydrologyPlanningAdmission.effectiveParallelism(parameters[0], parameters[1]));
        }
        assertEquals(Runtime.getRuntime().availableProcessors(),
                HydrologyPlanningAdmission.effectiveParallelism(Integer.MAX_VALUE));
    }

    @Test
    public void trialLimitSharesWorkingMemoryBetweenAdmittedRoots() {
        long gibibyte = 1024L * 1024L * 1024L;
        assertEquals(1, HydrologyPlanningAdmission.trialLimit(1280L * 1024L * 1024L, 1));
        assertEquals(2, HydrologyPlanningAdmission.trialLimit(2L * gibibyte, 1));
        assertEquals(2, HydrologyPlanningAdmission.trialLimit(4L * gibibyte, 2));
        assertEquals(1, HydrologyPlanningAdmission.trialLimit(gibibyte / 2L, 1));
        assertEquals(8, HydrologyPlanningAdmission.trialLimit(64L * gibibyte, 8));
        assertEquals(16, HydrologyPlanningAdmission.trialLimit(Long.MAX_VALUE, 8));
        assertThrows(IllegalArgumentException.class, () -> HydrologyPlanningAdmission.trialLimit(gibibyte, 0));
    }

    @Test(timeout = 15000)
    public void independentCallersShareTheSameRootBudget() throws Exception {
        HydrologyPlanningAdmission admission = new HydrologyPlanningAdmission(2);
        AtomicInteger running = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        CountDownLatch firstBatch = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService callers = Executors.newFixedThreadPool(8);
        try {
            ArrayList<Future<?>> results = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                results.add(callers.submit(() -> {
                    try (HydrologyPlanningAdmission.Permit ignored = admission.acquire(() -> false)) {
                        peak.accumulateAndGet(running.incrementAndGet(), Math::max);
                        firstBatch.countDown();
                        await(release);
                        running.decrementAndGet();
                    }
                }));
            }
            assertTrue(firstBatch.await(5L, TimeUnit.SECONDS));
            assertEquals(2, running.get());
            release.countDown();
            for (Future<?> result : results) {
                result.get(5L, TimeUnit.SECONDS);
            }
            assertEquals(2, peak.get());
            assertEquals(0, running.get());
        } finally {
            release.countDown();
            callers.shutdownNow();
            assertTrue(callers.awaitTermination(5L, TimeUnit.SECONDS));
        }
    }

    @Test(timeout = 15000)
    public void waitingRootsLeaveWorkersForNestedPlanning() throws Exception {
        HydrologyPlanningAdmission admission = new HydrologyPlanningAdmission(1);
        ForkJoinPool pool = new ForkJoinPool(2);
        CountDownLatch firstRoot = new CountDownLatch(1);
        CountDownLatch secondWaiting = new CountDownLatch(1);
        CountDownLatch childCompleted = new CountDownLatch(1);
        try {
            Future<List<Integer>> first = pool.submit(() -> {
                try (HydrologyPlanningAdmission.Permit ignored = admission.acquire(() -> false)) {
                    firstRoot.countDown();
                    await(secondWaiting);
                    return HydrologyForkJoin.invokeAll(List.of(() -> {
                        ForkJoinPool.managedBlock(new ForkJoinPool.ManagedBlocker() {
                            @Override
                            public boolean isReleasable() {
                                return childCompleted.getCount() == 0L;
                            }

                            @Override
                            public boolean block() {
                                await(childCompleted);
                                return true;
                            }
                        });
                        return 1;
                    }, () -> {
                        childCompleted.countDown();
                        return 2;
                    }), null);
                }
            });
            assertTrue(firstRoot.await(5L, TimeUnit.SECONDS));
            Future<Integer> second = pool.submit(() -> {
                secondWaiting.countDown();
                try (HydrologyPlanningAdmission.Permit ignored = admission.acquire(() -> false)) {
                    return 3;
                }
            });
            assertEquals(List.of(1, 2), first.get(5L, TimeUnit.SECONDS));
            assertEquals(Integer.valueOf(3), second.get(5L, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5L, TimeUnit.SECONDS));
        }
    }

    @Test(timeout = 15000)
    public void interruptedWaiterExitsWithoutLeakingOrReleasingAnotherRoot() throws Exception {
        HydrologyPlanningAdmission admission = new HydrologyPlanningAdmission(1);
        ExecutorService caller = Executors.newSingleThreadExecutor();
        AtomicReference<Thread> waitingThread = new AtomicReference<>();
        CountDownLatch waiting = new CountDownLatch(1);
        try (HydrologyPlanningAdmission.Permit held = admission.acquire(() -> false)) {
            Future<Boolean> interrupted = caller.submit(() -> {
                waitingThread.set(Thread.currentThread());
                waiting.countDown();
                assertThrows(CancellationException.class, () -> admission.acquire(() -> false));
                return Thread.currentThread().isInterrupted();
            });
            assertTrue(waiting.await(5L, TimeUnit.SECONDS));
            waitingThread.get().interrupt();
            assertTrue(interrupted.get(5L, TimeUnit.SECONDS));
            Future<?> next = caller.submit(() -> {
                try (HydrologyPlanningAdmission.Permit ignored = admission.acquire(() -> false)) {
                    return null;
                }
            });
            assertThrows(TimeoutException.class, () -> next.get(150L, TimeUnit.MILLISECONDS));
            held.close();
            next.get(5L, TimeUnit.SECONDS);
        } finally {
            caller.shutdownNow();
            assertTrue(caller.awaitTermination(5L, TimeUnit.SECONDS));
        }
    }

    @Test(timeout = 15000)
    public void closingWhileQueuedCancelsWithoutWaitingForTheActiveRoot() throws Exception {
        HydrologyPlanningAdmission admission = new HydrologyPlanningAdmission(1);
        AtomicBoolean closed = new AtomicBoolean();
        AtomicBoolean entered = new AtomicBoolean();
        CountDownLatch waiting = new CountDownLatch(1);
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try (HydrologyPlanningAdmission.Permit ignored = admission.acquire(() -> false)) {
            Future<?> cancelled = caller.submit(() -> {
                waiting.countDown();
                assertThrows(CancellationException.class, () -> {
                    try (HydrologyPlanningAdmission.Permit unused = admission.acquire(closed::get)) {
                        entered.set(true);
                    }
                });
            });
            assertTrue(waiting.await(5L, TimeUnit.SECONDS));
            closed.set(true);
            cancelled.get(2L, TimeUnit.SECONDS);
            assertFalse(entered.get());
        } finally {
            caller.shutdownNow();
            assertTrue(caller.awaitTermination(5L, TimeUnit.SECONDS));
        }
        try (HydrologyPlanningAdmission.Permit ignored = admission.acquire(() -> false)) {
            assertThrows(CancellationException.class, () -> admission.acquire(() -> true));
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue("Timed out awaiting planning", latch.await(5L, TimeUnit.SECONDS));
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interruption);
        }
    }
}
