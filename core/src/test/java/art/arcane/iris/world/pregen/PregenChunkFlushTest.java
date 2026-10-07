package art.arcane.iris.world.pregen;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

public class PregenChunkFlushTest {
    @Test
    public void queuedRequestsShareOneFlushAndCancellationIsIndependent() {
        Queue<Runnable> tasks = new ArrayDeque<>();
        AtomicInteger calls = new AtomicInteger();
        PregenChunkFlush flush = new PregenChunkFlush(tasks::add, calls::incrementAndGet);
        CompletableFuture<Void> first = flush.request();
        CompletableFuture<Void> second = flush.request();
        List<CompletableFuture<Void>> burst = new ArrayList<>();
        for (int index = 0; index < 1024; index++) {
            burst.add(flush.request());
        }
        CompletableFuture<Void> cancelled = flush.request();
        cancelled.cancel(false);
        assertEquals(1, tasks.size());
        assertFalse(first.isDone());
        assertFalse(second.isDone());
        tasks.remove().run();
        first.join();
        second.join();
        for (CompletableFuture<Void> request : burst) {
            request.join();
        }
        assertEquals(1, calls.get());
        flush.request();
        assertEquals(1, tasks.size());
    }

    @Test
    public void requestDuringFlushRequiresAnotherFlushAndFailuresDoNotDropNextBatch() {
        Queue<Runnable> tasks = new ArrayDeque<>();
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<PregenChunkFlush> owner = new AtomicReference<>();
        AtomicReference<CompletableFuture<Void>> next = new AtomicReference<>();
        PregenChunkFlush flush = new PregenChunkFlush(tasks::add, () -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                next.set(owner.get().request());
                assertFalse(next.get().isDone());
                throw new IllegalStateException("flush failed");
            }
            assertFalse(next.get().isDone());
        });
        owner.set(flush);
        CompletableFuture<Void> first = flush.request();
        CompletableFuture<Void> peer = flush.request();
        tasks.remove().run();
        assertThrows(CompletionException.class, first::join);
        assertThrows(CompletionException.class, peer::join);
        next.get().join();
        assertEquals(2, calls.get());
        assertTrue(tasks.isEmpty());
    }

    @Test
    public void completedBatchDoesNotWaitForRequestAdmittedDuringItsFlush() {
        Queue<Runnable> tasks = new ArrayDeque<>();
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<PregenChunkFlush> owner = new AtomicReference<>();
        AtomicReference<CompletableFuture<Void>> first = new AtomicReference<>();
        AtomicReference<CompletableFuture<Void>> second = new AtomicReference<>();
        PregenChunkFlush flush = new PregenChunkFlush(tasks::add, () -> {
            if (calls.incrementAndGet() == 1) {
                second.set(owner.get().request());
                assertFalse(first.get().isDone());
                assertFalse(second.get().isDone());
            } else {
                assertTrue(first.get().isDone());
                assertFalse(second.get().isDone());
            }
        });
        owner.set(flush);
        first.set(flush.request());
        tasks.remove().run();
        first.get().join();
        second.get().join();
        assertEquals(2, calls.get());
    }

    @Test
    public void schedulingFailuresCompleteRequestsAndAllowRetry() {
        for (Throwable failure : new Throwable[]{new RejectedExecutionException("closed"),
                new IllegalStateException("thread creation failed"), new OutOfMemoryError("thread unavailable")}) {
            Queue<Runnable> tasks = new ArrayDeque<>();
            AtomicInteger submissions = new AtomicInteger();
            Executor executor = task -> {
                if (submissions.getAndIncrement() == 0) {
                    if (failure instanceof Error error) {
                        throw error;
                    }
                    throw (RuntimeException) failure;
                }
                tasks.add(task);
            };
            AtomicInteger calls = new AtomicInteger();
            PregenChunkFlush flush = new PregenChunkFlush(executor, calls::incrementAndGet);
            CompletableFuture<Void> rejected = flush.request();
            CompletionException completed = assertThrows(CompletionException.class, rejected::join);
            assertEquals(failure, completed.getCause());
            CompletableFuture<Void> retry = flush.request();
            tasks.remove().run();
            retry.join();
            assertEquals(1, calls.get());
        }
    }

    @Test
    public void runningFlushDoesNotBlockRequestAndWorkerCloseDrainsBothBatches() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        PregenSerialWorker worker = new PregenSerialWorker("flush test", "synthetic",
                (message, failure) -> { throw new AssertionError(message, failure); },
                message -> { throw new AssertionError(message); });
        try {
            PregenChunkFlush flush = new PregenChunkFlush(worker.executor(), () -> {
                if (calls.incrementAndGet() == 1) {
                    started.countDown();
                    try {
                        assertTrue(finish.await(5, TimeUnit.SECONDS));
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(failure);
                    }
                }
            });
            CompletableFuture<Void> first = flush.request();
            assertTrue(started.await(5, TimeUnit.SECONDS));
            CompletableFuture<Void> second = flush.request();
            assertFalse(first.isDone());
            assertFalse(second.isDone());
            finish.countDown();
            assertTrue(worker.close(5, TimeUnit.SECONDS));
            first.join();
            second.join();
            assertEquals(2, calls.get());
        } finally {
            finish.countDown();
            worker.close(5, TimeUnit.SECONDS);
        }
    }
}
