package art.arcane.iris.core.nms.v26_2_R1;

import org.junit.Test;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class IrisChunkGeneratorFailureContractTest {
    @Test
    public void releasedNoiseAdmissionLetsSynchronousDependentWaitForQueuedExclusive() throws Exception {
        Semaphore admission = new Semaphore(1, true);
        admission.acquire();
        CountDownLatch exclusiveEntered = new CountDownLatch(1);
        CountDownLatch releaseExclusive = new CountDownLatch(1);
        AtomicBoolean dependentFinished = new AtomicBoolean(false);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        boolean stageReleased = false;

        try {
            Future<?> exclusive = executor.submit(() -> {
                boolean acquired = false;
                try {
                    admission.acquire();
                    acquired = true;
                    exclusiveEntered.countDown();
                    assertTrue(releaseExclusive.await(2, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                } finally {
                    if (acquired) {
                        admission.release();
                    }
                }
            });
            awaitQueueLength(admission, 1);
            CompletableFuture<Void> outward = new CompletableFuture<>();
            outward.thenRun(() -> {
                try {
                    assertTrue(exclusiveEntered.await(2, TimeUnit.SECONDS));
                    dependentFinished.set(true);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            });

            admission.release();
            stageReleased = true;
            assertTrue(outward.complete(null));
            assertTrue(dependentFinished.get());
            releaseExclusive.countDown();
            exclusive.get(2, TimeUnit.SECONDS);
            assertEquals(1, admission.availablePermits());
        } finally {
            if (!stageReleased) {
                admission.release();
            }
            releaseExclusive.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void transformedDelegateCancellationIsDetectedThroughItsCompletionFailure() {
        CompletableFuture<Void> delegate = new CompletableFuture<>();
        CompletableFuture<Void> transformed = delegate.thenApply(value -> value);

        assertTrue(delegate.cancel(false));
        assertFalse(transformed.isCancelled());
        CompletionException failure = assertThrows(CompletionException.class, transformed::join);
        assertTrue(failure.getCause() instanceof CancellationException);
    }

    private static void awaitQueueLength(Semaphore semaphore, int expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (semaphore.getQueueLength() < expected && System.nanoTime() < deadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
        assertTrue("Expected at least " + expected + " queued semaphore threads",
                semaphore.getQueueLength() >= expected);
    }
}
