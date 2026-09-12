package art.arcane.iris.generation.mantle;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CaveDensityTasksTest {
    @Test
    public void saturatedParentsClaimQueuedWorkWithoutHelpingOtherLocks() throws Exception {
        try (ForkJoinPool pool = new ForkJoinPool(2)) {
            CountDownLatch parents = new CountDownLatch(2);
            AtomicInteger executions = new AtomicInteger();
            Runnable parent = () -> {
                synchronized (new Object()) {
                    parents.countDown();
                    await(parents);
                    IrisCaveCarver3D.runDensityTasks(List.of(
                            executions::incrementAndGet, executions::incrementAndGet,
                            executions::incrementAndGet, executions::incrementAndGet), pool);
                }
            };
            Future<?> first = pool.submit(parent);
            Future<?> second = pool.submit(parent);
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertEquals(8, executions.get());
        }
    }

    @Test
    public void failedCallerDrainsStartedWorkerBeforeThrowing() throws Exception {
        try (ForkJoinPool pool = new ForkJoinPool(2)) {
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch failed = new CountDownLatch(1);
            AtomicInteger completed = new AtomicInteger();
            IllegalStateException failure = new IllegalStateException("density failure");
            Future<?> parent = pool.submit(() -> IrisCaveCarver3D.runDensityTasks(List.of(() -> {
                await(started);
                failed.countDown();
                throw failure;
            }, () -> {
                started.countDown();
                await(release);
                completed.incrementAndGet();
            }), pool));
            try {
                assertTrue(failed.await(5, TimeUnit.SECONDS));
                assertFalse(parent.isDone());
            } finally {
                release.countDown();
            }
            try {
                parent.get(5, TimeUnit.SECONDS);
                fail("Expected density failure");
            } catch (ExecutionException expected) {
                Throwable cause = expected.getCause();
                assertTrue(cause == failure || cause.getCause() == failure);
            }
            assertEquals(1, completed.get());
        }
    }

    @Test
    public void interruptionDrainsWorkerAndRestoresInterruptStatus() throws Exception {
        try (ForkJoinPool pool = new ForkJoinPool(2)) {
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch awaiting = new CountDownLatch(1);
            AtomicReference<Thread> parentThread = new AtomicReference<>();
            AtomicInteger completed = new AtomicInteger();
            Future<Boolean> parent = pool.submit(() -> {
                parentThread.set(Thread.currentThread());
                try {
                    IrisCaveCarver3D.runDensityTasks(List.of(() -> {
                        await(started);
                        awaiting.countDown();
                    }, () -> {
                        started.countDown();
                        await(release);
                        completed.incrementAndGet();
                    }), pool);
                    return false;
                } catch (IllegalStateException expected) {
                    return Thread.currentThread().isInterrupted();
                }
            });
            try {
                assertTrue(awaiting.await(5, TimeUnit.SECONDS));
                parentThread.get().interrupt();
                assertFalse(parent.isDone());
            } finally {
                release.countDown();
            }
            assertTrue(parent.get(5, TimeUnit.SECONDS));
            assertEquals(1, completed.get());
        }
    }

    @Test
    public void rejectedSubmissionClaimsAllWorkBeforeFailureReturns() {
        ForkJoinPool pool = new ForkJoinPool(2);
        pool.shutdown();
        AtomicInteger completed = new AtomicInteger();
        try {
            IrisCaveCarver3D.runDensityTasks(List.of(completed::incrementAndGet, completed::incrementAndGet), pool);
            fail("Expected rejected submission");
        } catch (RejectedExecutionException expected) {
            assertEquals(2, completed.get());
        }
    }

    @Test
    public void managedDrainCompensatesWithoutRunningUnrelatedWorkOnParent() throws Exception {
        try (ForkJoinPool pool = new ForkJoinPool(2, ForkJoinPool.defaultForkJoinWorkerThreadFactory,
                null, false, 2, 3, 2, null, 60, TimeUnit.SECONDS)) {
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch awaiting = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicReference<Thread> owner = new AtomicReference<>();
            Object chunkFlag = new Object();
            Future<?> parent = pool.submit(() -> {
                synchronized (chunkFlag) {
                    owner.set(Thread.currentThread());
                    IrisCaveCarver3D.runDensityTasks(List.of(() -> {
                        await(started);
                        awaiting.countDown();
                    }, () -> {
                        started.countDown();
                        await(release);
                    }), pool);
                }
            });
            try {
                assertTrue(awaiting.await(5, TimeUnit.SECONDS));
                Thread other = pool.submit(Thread::currentThread).get(5, TimeUnit.SECONDS);
                assertFalse(other == owner.get());
                assertFalse(parent.isDone());
            } finally {
                release.countDown();
            }
            parent.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    public void unavailableCompensationStillDrainsStartedWork() throws Exception {
        try (ForkJoinPool pool = new ForkJoinPool(2, ForkJoinPool.defaultForkJoinWorkerThreadFactory,
                null, false, 2, 2, 2, null, 60, TimeUnit.SECONDS)) {
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch awaiting = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicInteger completed = new AtomicInteger();
            Future<?> parent = pool.submit(() -> IrisCaveCarver3D.runDensityTasks(List.of(() -> {
                await(started);
                awaiting.countDown();
            }, () -> {
                started.countDown();
                await(release);
                completed.incrementAndGet();
            }), pool));
            try {
                assertTrue(awaiting.await(5, TimeUnit.SECONDS));
                assertFalse(parent.isDone());
            } finally {
                release.countDown();
            }
            parent.get(5, TimeUnit.SECONDS);
            assertEquals(1, completed.get());
        }
    }

    @Test
    public void completedTasksStillPropagateAnExistingInterrupt() {
        try (ForkJoinPool pool = new ForkJoinPool(1)) {
            Thread.currentThread().interrupt();
            try {
                IrisCaveCarver3D.runDensityTasks(List.of(() -> {}), pool);
                fail("Expected interrupted density batch");
            } catch (IllegalStateException expected) {
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for density task");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }
}
