package art.arcane.iris.engine.platform;

import art.arcane.iris.spi.IrisLogging;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

public class BukkitChunkGeneratorGenerationStageGateTest {
    @Test
    public void activeStageDelaysExclusiveControl() throws Exception {
        AtomicBoolean closing = new AtomicBoolean(false);
        BukkitChunkGenerator.GenerationStageGate gate =
                new BukkitChunkGenerator.GenerationStageGate(2, closing::get);
        BukkitChunkGenerator.GenerationStagePermit stage = gate.acquireStage("active");
        CountDownLatch exclusiveEntered = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<?> exclusive = executor.submit(() -> {
                try {
                    gate.acquireExclusive();
                    exclusiveEntered.countDown();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                } finally {
                    if (exclusiveEntered.getCount() == 0L) {
                        gate.releaseExclusive();
                    }
                }
            });

            awaitQueueLength(gate, 1);
            assertEquals(1L, exclusiveEntered.getCount());

            stage.close();
            exclusive.get(2, TimeUnit.SECONDS);

            assertEquals(0L, exclusiveEntered.getCount());
            assertEquals(2, gate.availablePermits());
        } finally {
            stage.close();
            executor.shutdownNow();
        }
    }

    @Test
    public void exclusiveWaiterRunsBeforeLaterGenerationStage() throws Exception {
        AtomicBoolean closing = new AtomicBoolean(false);
        BukkitChunkGenerator.GenerationStageGate gate =
                new BukkitChunkGenerator.GenerationStageGate(2, closing::get);
        BukkitChunkGenerator.GenerationStagePermit activeStage = gate.acquireStage("active");
        CountDownLatch exclusiveEntered = new CountDownLatch(1);
        CountDownLatch releaseExclusive = new CountDownLatch(1);
        CountDownLatch laterStageAttempting = new CountDownLatch(1);
        CountDownLatch laterStageEntered = new CountDownLatch(1);
        List<String> order = new CopyOnWriteArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> exclusive = executor.submit(() -> {
                try {
                    gate.acquireExclusive();
                    order.add("exclusive");
                    exclusiveEntered.countDown();
                    assertTrue(releaseExclusive.await(2, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                } finally {
                    if (exclusiveEntered.getCount() == 0L) {
                        gate.releaseExclusive();
                    }
                }
            });
            awaitQueueLength(gate, 1);

            Future<?> laterStage = executor.submit(() -> {
                laterStageAttempting.countDown();
                try (BukkitChunkGenerator.GenerationStagePermit ignored = gate.acquireStage("later")) {
                    order.add("stage");
                    laterStageEntered.countDown();
                }
            });
            assertTrue(laterStageAttempting.await(2, TimeUnit.SECONDS));
            awaitQueueLength(gate, 2);

            activeStage.close();
            assertTrue(exclusiveEntered.await(2, TimeUnit.SECONDS));
            assertEquals(1L, laterStageEntered.getCount());

            releaseExclusive.countDown();
            exclusive.get(2, TimeUnit.SECONDS);
            laterStage.get(2, TimeUnit.SECONDS);

            assertEquals(List.of("exclusive", "stage"), order);
            assertEquals(2, gate.availablePermits());
        } finally {
            releaseExclusive.countDown();
            activeStage.close();
            executor.shutdownNow();
        }
    }

    @Test
    public void generationStageCanBeReleasedTwiceFromAnotherThread() throws Exception {
        AtomicBoolean closing = new AtomicBoolean(false);
        BukkitChunkGenerator.GenerationStageGate gate =
                new BukkitChunkGenerator.GenerationStageGate(1, closing::get);
        BukkitChunkGenerator.GenerationStagePermit stage = gate.acquireStage("async");
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<?> release = executor.submit(() -> {
                stage.close();
                stage.close();
            });
            release.get(2, TimeUnit.SECONDS);

            assertEquals(1, gate.availablePermits());
            gate.acquireExclusive();
            assertEquals(0, gate.availablePermits());
            gate.releaseExclusive();
            assertEquals(1, gate.availablePermits());
        } finally {
            stage.close();
            executor.shutdownNow();
        }
    }

    @Test
    public void exclusiveControlSuccessReleasesGateBeforeSynchronousCompletionDependent() throws Exception {
        AtomicBoolean closing = new AtomicBoolean(false);
        BukkitChunkGenerator.GenerationStageGate gate =
                new BukkitChunkGenerator.GenerationStageGate(1, closing::get);
        CompletableFuture<Void> outward = new CompletableFuture<>();
        CountDownLatch dependentEntered = new CountDownLatch(1);
        outward.thenRun(() -> {
            try (BukkitChunkGenerator.GenerationStagePermit ignored = gate.acquireStage("success-dependent")) {
                dependentEntered.countDown();
            }
        });
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<?> operation = executor.submit(() ->
                    BukkitChunkGenerator.completeExclusiveControlFuture(gate, () -> {
                    }, outward));
            operation.get(2, TimeUnit.SECONDS);

            assertTrue(outward.isDone());
            assertEquals(0L, dependentEntered.getCount());
            assertEquals(1, gate.availablePermits());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void exclusiveControlFailureReleasesGateBeforeSynchronousCompletionDependent() throws Exception {
        AtomicBoolean closing = new AtomicBoolean(false);
        BukkitChunkGenerator.GenerationStageGate gate =
                new BukkitChunkGenerator.GenerationStageGate(1, closing::get);
        CompletableFuture<Void> outward = new CompletableFuture<>();
        IllegalStateException expected = new IllegalStateException("exclusive failure");
        AtomicReference<Throwable> observed = new AtomicReference<>();
        CountDownLatch dependentEntered = new CountDownLatch(1);
        outward.whenComplete((ignored, failure) -> {
            try (BukkitChunkGenerator.GenerationStagePermit stage = gate.acquireStage("failure-dependent")) {
                observed.set(failure);
                dependentEntered.countDown();
            }
        });
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<?> operation = executor.submit(() ->
                    BukkitChunkGenerator.completeExclusiveControlFuture(gate, () -> {
                        throw expected;
                    }, outward));
            operation.get(2, TimeUnit.SECONDS);

            CompletionException completion = assertThrows(CompletionException.class, outward::join);
            assertSame(expected, completion.getCause());
            assertSame(expected, observed.get());
            assertEquals(0L, dependentEntered.getCount());
            assertEquals(1, gate.availablePermits());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void timedExclusiveRetainsPriorityDuringStaggeredGenerationDrain() throws Exception {
        BukkitChunkGenerator.GenerationStageGate gate =
                new BukkitChunkGenerator.GenerationStageGate(3, () -> false);
        BukkitChunkGenerator.GenerationStagePermit first = gate.acquireStage("first-active");
        BukkitChunkGenerator.GenerationStagePermit last = gate.acquireStage("last-active");
        CompletableFuture<Void> outward = new CompletableFuture<>();
        CountDownLatch exclusiveEntered = new CountDownLatch(1);
        CountDownLatch releaseExclusive = new CountDownLatch(1);
        CountDownLatch readerEntered = new CountDownLatch(1);
        List<String> order = new CopyOnWriteArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(3);

        try {
            Future<?> exclusive = executor.submit(() -> BukkitChunkGenerator.completeExclusiveControlFuture(
                    gate,
                    () -> {
                        order.add("exclusive");
                        exclusiveEntered.countDown();
                        try {
                            assertTrue(releaseExclusive.await(2L, TimeUnit.SECONDS));
                        } catch (InterruptedException failure) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(failure);
                        }
                    },
                    outward,
                    2L,
                    TimeUnit.SECONDS));
            awaitQueueLength(gate, 1);
            Runnable reader = () -> {
                try (BukkitChunkGenerator.GenerationStagePermit ignored = gate.acquireStage("later-reader")) {
                    order.add("reader");
                    readerEntered.countDown();
                }
            };
            Future<?> firstReader = executor.submit(reader);
            Future<?> secondReader = executor.submit(reader);
            awaitQueueLength(gate, 3);

            assertFalse(readerEntered.await(150L, TimeUnit.MILLISECONDS));
            first.close();
            assertFalse(readerEntered.await(150L, TimeUnit.MILLISECONDS));
            last.close();
            assertTrue(exclusiveEntered.await(2L, TimeUnit.SECONDS));
            assertEquals(1L, readerEntered.getCount());
            releaseExclusive.countDown();
            exclusive.get(2L, TimeUnit.SECONDS);
            outward.get(2L, TimeUnit.SECONDS);
            firstReader.get(2L, TimeUnit.SECONDS);
            secondReader.get(2L, TimeUnit.SECONDS);
            assertEquals(List.of("exclusive", "reader", "reader"), order);
            assertEquals(3, gate.availablePermits());
        } finally {
            first.close();
            last.close();
            releaseExclusive.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(2L, TimeUnit.SECONDS));
        }
    }

    @Test
    public void timedExclusiveControlDoesNotReleaseAnUnacquiredPermit() throws Exception {
        AtomicBoolean closing = new AtomicBoolean(false);
        BukkitChunkGenerator.GenerationStageGate gate =
                new BukkitChunkGenerator.GenerationStageGate(1, closing::get);
        BukkitChunkGenerator.GenerationStagePermit stage = gate.acquireStage("timeout-holder");
        CompletableFuture<Void> outward = new CompletableFuture<>();
        AtomicBoolean operationRan = new AtomicBoolean(false);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<?> operation = executor.submit(() ->
                    BukkitChunkGenerator.completeExclusiveControlFuture(
                            gate,
                            () -> operationRan.set(true),
                            outward,
                            50L,
                            TimeUnit.MILLISECONDS));

            ExecutionException failure = assertThrows(
                    ExecutionException.class,
                    () -> outward.get(2L, TimeUnit.SECONDS));
            operation.get(2L, TimeUnit.SECONDS);

            assertTrue(failure.getCause() instanceof TimeoutException);
            assertFalse(operationRan.get());
            assertEquals(0, gate.availablePermits());
        } finally {
            stage.close();
            assertEquals(1, gate.availablePermits());
            executor.shutdownNow();
        }
    }

    @Test
    public void untimedExclusiveCancellationRemovesWaiterWithoutLeakingInterruptOrPermits() throws Exception {
        BukkitChunkGenerator.GenerationStageGate gate =
                new BukkitChunkGenerator.GenerationStageGate(1, () -> false);
        BukkitChunkGenerator.GenerationStagePermit active = gate.acquireStage("active");
        CompletableFuture<Void> pending = new CompletableFuture<>();
        AtomicBoolean operationRan = new AtomicBoolean();
        AtomicBoolean interrupted = new AtomicBoolean();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> worker = executor.submit(() -> {
                BukkitChunkGenerator.completeExclusiveControlFuture(gate, () -> operationRan.set(true), pending);
                interrupted.set(Thread.currentThread().isInterrupted());
            });
            awaitQueueLength(gate, 1);

            assertTrue(pending.cancel(true));
            worker.get(2L, TimeUnit.SECONDS);

            assertFalse(operationRan.get());
            assertFalse(interrupted.get());
            assertEquals(0, gate.queueLength());
            assertEquals(0, gate.availablePermits());
            active.close();
            assertEquals(1, gate.availablePermits());
        } finally {
            active.close();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(2L, TimeUnit.SECONDS));
        }
    }

    @Test
    public void untimedCancellationAfterAdmissionDoesNotInterruptCutover() throws Exception {
        BukkitChunkGenerator.GenerationStageGate gate =
                new BukkitChunkGenerator.GenerationStageGate(1, () -> false);
        CompletableFuture<Void> pending = new CompletableFuture<>();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> worker = executor.submit(() -> BukkitChunkGenerator.completeExclusiveControlFuture(gate, () -> {
                entered.countDown();
                try {
                    assertTrue(release.await(2L, TimeUnit.SECONDS));
                } catch (InterruptedException failure) {
                    interrupted.set(true);
                    Thread.currentThread().interrupt();
                }
            }, pending));
            assertTrue(entered.await(2L, TimeUnit.SECONDS));

            assertTrue(pending.cancel(true));
            assertEquals(0, gate.availablePermits());
            release.countDown();
            worker.get(2L, TimeUnit.SECONDS);

            assertFalse(interrupted.get());
            assertEquals(1, gate.availablePermits());
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(2L, TimeUnit.SECONDS));
        }
    }

    @Test
    public void preCancelledUntimedControlDoesNotQueueOrRun() {
        BukkitChunkGenerator.GenerationStageGate gate =
                new BukkitChunkGenerator.GenerationStageGate(1, () -> false);
        CompletableFuture<Void> pending = new CompletableFuture<>();
        AtomicBoolean operationRan = new AtomicBoolean();
        pending.cancel(true);

        BukkitChunkGenerator.completeExclusiveControlFuture(gate, () -> operationRan.set(true), pending);

        assertFalse(operationRan.get());
        assertEquals(0, gate.queueLength());
        assertEquals(1, gate.availablePermits());
    }

    @Test
    public void cancelledUntimedControlReportsAnAdmittedCutoverFailure() {
        BukkitChunkGenerator.GenerationStageGate gate =
                new BukkitChunkGenerator.GenerationStageGate(1, () -> false);
        CompletableFuture<Void> pending = new CompletableFuture<>();
        IllegalStateException failure = new IllegalStateException("Admitted cutover failed");

        try (MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            BukkitChunkGenerator.completeExclusiveControlFuture(gate, () -> {
                pending.cancel(true);
                throw failure;
            }, pending);

            logging.verify(() -> IrisLogging.reportError(failure));
        }
        assertTrue(pending.isCancelled());
        assertFalse(Thread.currentThread().isInterrupted());
        assertEquals(1, gate.availablePermits());
    }

    @Test
    public void cancelledExclusiveControlStopsWaitingWithoutReleasingAStagePermit() throws Exception {
        AtomicBoolean closing = new AtomicBoolean(false);
        BukkitChunkGenerator.GenerationStageGate gate =
                new BukkitChunkGenerator.GenerationStageGate(1, closing::get);
        BukkitChunkGenerator.GenerationStagePermit stage = gate.acquireStage("cancellation-holder");
        CompletableFuture<Void> outward = new CompletableFuture<>();
        AtomicBoolean operationRan = new AtomicBoolean(false);
        AtomicBoolean interruptedAfterCancellation = new AtomicBoolean(true);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<?> operation = executor.submit(() -> {
                BukkitChunkGenerator.completeExclusiveControlFuture(
                        gate,
                        () -> operationRan.set(true),
                        outward,
                        30L,
                        TimeUnit.SECONDS);
                interruptedAfterCancellation.set(Thread.currentThread().isInterrupted());
            });
            awaitQueueLength(gate, 1);

            assertTrue(outward.cancel(true));
            operation.get(2L, TimeUnit.SECONDS);

            assertTrue(outward.isCancelled());
            assertFalse(operationRan.get());
            assertFalse(interruptedAfterCancellation.get());
            assertEquals(0, gate.availablePermits());
        } finally {
            stage.close();
            assertEquals(1, gate.availablePermits());
            executor.shutdownNow();
        }
    }

    @Test
    public void externalInterruptStillRestoresTheAcquisitionWorkerFlag() throws Exception {
        BukkitChunkGenerator.GenerationStageGate gate =
                new BukkitChunkGenerator.GenerationStageGate(1, () -> false);
        BukkitChunkGenerator.GenerationStagePermit stage = gate.acquireStage("interrupt-holder");
        CompletableFuture<Void> outward = new CompletableFuture<>();
        AtomicReference<Thread> worker = new AtomicReference<>();
        AtomicBoolean interruptedAfterAcquisition = new AtomicBoolean(false);
        AtomicBoolean operationRan = new AtomicBoolean(false);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<?> operation = executor.submit(() -> {
                worker.set(Thread.currentThread());
                BukkitChunkGenerator.completeExclusiveControlFuture(gate, () -> operationRan.set(true),
                        outward, 30L, TimeUnit.SECONDS);
                interruptedAfterAcquisition.set(Thread.currentThread().isInterrupted());
            });
            awaitQueueLength(gate, 1);
            worker.get().interrupt();
            operation.get(2L, TimeUnit.SECONDS);

            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> outward.get(2L, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof InterruptedException);
            assertTrue(interruptedAfterAcquisition.get());
            assertFalse(operationRan.get());
            assertEquals(0, gate.availablePermits());
        } finally {
            stage.close();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(2L, TimeUnit.SECONDS));
        }
    }

    @Test
    public void cancellationAfterAcquisitionDoesNotInterruptTheRunningOperation() throws Exception {
        BukkitChunkGenerator.GenerationStageGate gate =
                new BukkitChunkGenerator.GenerationStageGate(1, () -> false);
        CompletableFuture<Void> outward = new CompletableFuture<>();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean(false);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<?> operation = executor.submit(() -> {
                BukkitChunkGenerator.completeExclusiveControlFuture(gate, () -> {
                    entered.countDown();
                    try {
                        assertTrue(release.await(2L, TimeUnit.SECONDS));
                    } catch (InterruptedException failure) {
                        interrupted.set(true);
                        Thread.currentThread().interrupt();
                    }
                }, outward, 30L, TimeUnit.SECONDS);
                interrupted.compareAndSet(false, Thread.currentThread().isInterrupted());
            });
            assertTrue(entered.await(2L, TimeUnit.SECONDS));
            assertTrue(outward.cancel(true));
            assertEquals(0, gate.availablePermits());
            release.countDown();
            operation.get(2L, TimeUnit.SECONDS);

            assertFalse(interrupted.get());
            assertTrue(outward.isCancelled());
            assertEquals(1, gate.availablePermits());
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(2L, TimeUnit.SECONDS));
        }
    }

    @Test
    public void queuedStageRemainsAdmittedWhileShutdownIsOnlyQuiesced() throws Exception {
        AtomicBoolean closing = new AtomicBoolean(false);
        BukkitChunkGenerator.GenerationStageGate gate =
                new BukkitChunkGenerator.GenerationStageGate(1, closing::get);
        gate.acquireExclusive();
        boolean exclusiveHeld = true;
        BukkitChunkGenerator.GenerationStagePermit admitted = null;
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<BukkitChunkGenerator.GenerationStagePermit> stage =
                    executor.submit(() -> gate.acquireStage("paper-queued-before-shutdown-boundary"));
            awaitQueueLength(gate, 1);

            assertFalse(closing.get());
            gate.releaseExclusive();
            exclusiveHeld = false;

            admitted = stage.get(2, TimeUnit.SECONDS);
            assertEquals(0, gate.availablePermits());
            admitted.close();
            assertEquals(1, gate.availablePermits());
        } finally {
            if (admitted != null) {
                admitted.close();
            }
            if (exclusiveHeld) {
                gate.releaseExclusive();
            }
            executor.shutdownNow();
        }
    }

    @Test
    public void queuedStageIsRejectedAfterCloseBegins() throws Exception {
        AtomicBoolean closing = new AtomicBoolean(false);
        BukkitChunkGenerator.GenerationStageGate gate =
                new BukkitChunkGenerator.GenerationStageGate(1, closing::get);
        gate.acquireExclusive();
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<BukkitChunkGenerator.GenerationStagePermit> stage =
                    executor.submit(() -> gate.acquireStage("queued"));
            awaitQueueLength(gate, 1);

            closing.set(true);
            gate.releaseExclusive();

            ExecutionException failure = assertThrows(
                    ExecutionException.class,
                    () -> stage.get(2, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof IllegalStateException);
            assertTrue(failure.getCause().getMessage().contains("rejected while the generator is closing"));
            assertEquals(1, gate.availablePermits());
        } finally {
            if (gate.availablePermits() == 0) {
                gate.releaseExclusive();
            }
            executor.shutdownNow();
        }
    }

    @Test
    public void generationStageGateRequiresAtLeastOnePermit() {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> new BukkitChunkGenerator.GenerationStageGate(0, () -> false));

        assertTrue(failure.getMessage().contains("must be positive"));
    }

    private static void awaitQueueLength(
            BukkitChunkGenerator.GenerationStageGate gate,
            int expected
    ) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (gate.queueLength() < expected && System.nanoTime() < deadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
        assertTrue("Expected at least " + expected + " queued gate threads", gate.queueLength() >= expected);
    }

}
