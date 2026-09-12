package art.arcane.iris.generation.hydrology;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HydrologyForkJoinTest {
    @Test
    public void blockedGridBatchNeverHelpsUnrelatedOwnerWork() throws Exception {
        ForkJoinPool pool = new ForkJoinPool(2, ForkJoinPool.defaultForkJoinWorkerThreadFactory,
                null, false, 2, 2, 2, null, 60, TimeUnit.SECONDS);
        CountDownLatch childStarted = new CountDownLatch(1);
        CountDownLatch releaseChild = new CountDownLatch(1);
        CountDownLatch unrelatedRan = new CountDownLatch(1);
        try {
            Future<List<Integer>> result = pool.submit(() -> {
                return HydrologyForkJoin.invokeAll(List.of(() -> {
                    await(childStarted);
                    return 1;
                }, () -> {
                    pool.execute(() -> {
                        unrelatedRan.countDown();
                    });
                    childStarted.countDown();
                    await(releaseChild);
                    return 2;
                }), null);
            });
            assertTrue(childStarted.await(5, TimeUnit.SECONDS));
            assertFalse(unrelatedRan.await(100, TimeUnit.MILLISECONDS));
            releaseChild.countDown();
            assertEquals(List.of(1, 2), result.get(5, TimeUnit.SECONDS));
            assertTrue(unrelatedRan.await(5, TimeUnit.SECONDS));
        } finally {
            releaseChild.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    public void failedCallerDrainsAnAlreadyStartedGridRow() throws Exception {
        ForkJoinPool pool = new ForkJoinPool(2);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch failed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger completed = new AtomicInteger();
        IllegalStateException expected = new IllegalStateException("grid failure");
        try {
            Future<?> result = pool.submit(() -> HydrologyForkJoin.invokeAll(List.of(() -> {
                await(started);
                failed.countDown();
                throw expected;
            }, () -> {
                started.countDown();
                await(release);
                completed.incrementAndGet();
                return 2;
            }), null));
            assertTrue(failed.await(5, TimeUnit.SECONDS));
            assertFalse(result.isDone());
            release.countDown();
            assertThrows(ExecutionException.class, () -> result.get(5, TimeUnit.SECONDS));
            assertEquals(1, completed.get());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    public void rejectedFallbackSchedulingNeverRunsUnsubmittedTasks() {
        AtomicInteger completed = new AtomicInteger();
        RejectedExecutionException expected = new RejectedExecutionException("closed executor");
        RejectedExecutionException actual = assertThrows(RejectedExecutionException.class,
                () -> HydrologyForkJoin.invokeAll(List.of(completed::incrementAndGet, completed::incrementAndGet),
                        runnable -> { throw expected; }));
        assertEquals(expected, actual);
        assertEquals(0, completed.get());
    }

    @Test
    public void partiallyRejectedFallbackDrainsAcceptedWorkOnItsExecutor() throws Exception {
        ExecutorService fallback = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "fallback-executor"));
        ExecutorService caller = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch rejected = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger submissions = new AtomicInteger();
        AtomicInteger unsubmittedCalls = new AtomicInteger();
        AtomicReference<String> callbackThread = new AtomicReference<>();
        RejectedExecutionException expected = new RejectedExecutionException("closed executor");
        try {
            Future<RejectedExecutionException> result = caller.submit(() -> assertThrows(RejectedExecutionException.class,
                    () -> HydrologyForkJoin.invokeAll(List.of(() -> {
                        callbackThread.set(Thread.currentThread().getName());
                        started.countDown();
                        await(release);
                        return 1;
                    }, unsubmittedCalls::incrementAndGet), runnable -> {
                        if (submissions.getAndIncrement() == 0) {
                            fallback.execute(runnable);
                        } else {
                            rejected.countDown();
                            throw expected;
                        }
                    })));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertTrue(rejected.await(5, TimeUnit.SECONDS));
            assertFalse(result.isDone());
            release.countDown();
            assertEquals(expected, result.get(5, TimeUnit.SECONDS));
            assertEquals("fallback-executor", callbackThread.get());
            assertEquals(0, unsubmittedCalls.get());
        } finally {
            release.countDown();
            caller.shutdownNow();
            fallback.shutdownNow();
        }
    }

    @Test
    public void repeatedDemandClaimsAQueuedOwnerOnlyOnceAndRestoresInterrupts() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HydrologyForkJoin.Task<Integer> task = new HydrologyForkJoin.Task<>(calls::incrementAndGet);
        Thread.currentThread().interrupt();
        try {
            assertEquals(Integer.valueOf(1), task.await());
            assertEquals(Integer.valueOf(1), task.await());
            task.run();
            assertEquals(1, calls.get());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void interruptWhileWaitingStillDrainsTheStartedTask() throws Exception {
        ForkJoinPool pool = new ForkJoinPool(2);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch waiting = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Thread> waiter = new AtomicReference<>();
        HydrologyForkJoin.Task<Integer> task = new HydrologyForkJoin.Task<>(() -> {
            started.countDown();
            await(release);
            return 1;
        });
        try {
            pool.execute(task);
            assertTrue(started.await(5, TimeUnit.SECONDS));
            Future<Boolean> result = pool.submit(() -> {
                waiter.set(Thread.currentThread());
                waiting.countDown();
                assertEquals(Integer.valueOf(1), task.await());
                return Thread.currentThread().isInterrupted();
            });
            assertTrue(waiting.await(5, TimeUnit.SECONDS));
            waiter.get().interrupt();
            assertFalse(result.isDone());
            release.countDown();
            assertTrue(result.get(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for planning task");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }

    @Test
    public void nestedFanOutCompletesOnASaturatedForkJoinPool() throws Exception {
        ForkJoinPool pool = new ForkJoinPool(1);
        try {
            List<Integer> results = pool.submit(() -> {
                List<Callable<Integer>> outer = new ArrayList<>();
                for (int index = 0; index < 3; index++) {
                    int value = index;
                    outer.add(() -> {
                        List<Callable<Integer>> inner = List.of(() -> value * 10, () -> value * 10 + 1);
                        List<Integer> nested = HydrologyForkJoin.invokeAll(inner, Runnable::run);
                        return nested.get(0) + nested.get(1);
                    });
                }
                return HydrologyForkJoin.invokeAll(outer, Runnable::run);
            }).get(10, TimeUnit.SECONDS);

            assertEquals(List.of(1, 21, 41), results);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void outsideAPoolTasksRunOnTheFallbackExecutorInOrder() throws Exception {
        List<String> threads = Collections.synchronizedList(new ArrayList<>());
        ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> new Thread(runnable, "fallback-executor"));
        try {
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (int index = 0; index < 4; index++) {
                int value = index;
                tasks.add(() -> {
                    threads.add(Thread.currentThread().getName());
                    return value;
                });
            }

            List<Integer> results = HydrologyForkJoin.invokeAll(tasks, executor);

            assertEquals(List.of(0, 1, 2, 3), results);
            assertEquals(4, threads.size());
            assertTrue(threads.stream().allMatch("fallback-executor"::equals));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void withoutAFallbackTasksRunOnTheCaller() {
        String caller = Thread.currentThread().getName();
        List<Callable<String>> tasks = List.of(
                () -> Thread.currentThread().getName(),
                () -> Thread.currentThread().getName()
        );

        assertEquals(List.of(caller, caller), HydrologyForkJoin.invokeAll(tasks, null));
    }

    @Test
    public void aFailingTaskRethrowsItsExceptionOnEveryPath() throws Exception {
        List<Callable<Integer>> tasks = List.of(() -> 1, () -> {
            throw new IllegalStateException("planning failed");
        });

        IllegalStateException inline = assertThrows(IllegalStateException.class, () -> HydrologyForkJoin.invokeAll(tasks, null));
        assertEquals("planning failed", inline.getMessage());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            IllegalStateException pooled = assertThrows(IllegalStateException.class, () -> HydrologyForkJoin.invokeAll(tasks, executor));
            assertEquals("planning failed", pooled.getMessage());
        } finally {
            executor.shutdownNow();
        }

        ForkJoinPool pool = new ForkJoinPool(1);
        try {
            String message = pool.submit(() -> {
                try {
                    HydrologyForkJoin.invokeAll(tasks, Runnable::run);
                    return "no failure";
                } catch (IllegalStateException failure) {
                    return failure.getMessage();
                }
            }).get(10, TimeUnit.SECONDS);
            assertEquals("planning failed", message);
        } finally {
            pool.shutdownNow();
        }
    }
}
