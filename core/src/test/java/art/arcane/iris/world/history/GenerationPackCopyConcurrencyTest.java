package art.arcane.iris.world.history;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

public class GenerationPackCopyConcurrencyTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test(timeout = 15000)
    public void forkJoinShutdownSettlesCancelledQueuedCopyWorkers() throws Exception {
        Path source = createPack("cancelled-queue-source", 2);
        Path target = temporaryFolder.getRoot().toPath().resolve("cancelled-queue-target");
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch queued = new CountDownLatch(2);
        ForkJoinPool workers = new ForkJoinPool(1);
        ExecutorService caller = Executors.newSingleThreadExecutor();
        workers.execute(() -> {
            blocked.countDown();
            awaitUninterruptibly(release);
        });
        Executor execution = command -> {
            workers.execute(command);
            queued.countDown();
        };
        try {
            assertTrue(blocked.await(5, TimeUnit.SECONDS));
            Future<?> copy = caller.submit(() -> {
                GenerationPackRepository.copyPackTree(source, target, execution, 2);
                return null;
            });
            assertTrue(queued.await(5, TimeUnit.SECONDS));

            workers.shutdownNow();

            ExecutionException failure = assertThrows(ExecutionException.class, () -> copy.get(5, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof CancellationException);
            assertFalse(Files.exists(target.resolve("dimensions/0.json")));
            assertFalse(Files.exists(target.resolve("dimensions/1.json")));
        } finally {
            release.countDown();
            caller.shutdownNow();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test(timeout = 15000)
    public void cancelledRunningFlushDrainsFileWorkBeforeReturning() throws Exception {
        Path source = createPack("cancelled-flush-source", 1);
        Path target = temporaryFolder.getRoot().toPath().resolve("cancelled-flush-target");
        CountDownLatch flushing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Future<?>> submitted = new AtomicReference<>();
        AtomicReference<FileChannel> opened = new AtomicReference<>();
        AtomicBoolean flushed = new AtomicBoolean();
        ExecutorService workers = Executors.newSingleThreadExecutor();
        ExecutorService caller = Executors.newSingleThreadExecutor();
        Executor execution = command -> {
            submitted.set((Future<?>) command);
            workers.execute(() -> {
                try (MockedStatic<FileChannel> ignored = mockStatic(FileChannel.class, invocation -> {
                    FileChannel channel = (FileChannel) invocation.callRealMethod();
                    opened.set(channel);
                    FileChannel intercepted = mock(FileChannel.class, delegatesTo(channel));
                    doAnswer(force -> {
                        flushing.countDown();
                        awaitUninterruptibly(release);
                        channel.force(true);
                        flushed.set(true);
                        return null;
                    }).when(intercepted).force(true);
                    return intercepted;
                })) {
                    command.run();
                }
            });
        };
        try {
            Future<?> copy = caller.submit(() -> {
                GenerationPackRepository.copyPackTree(source, target, execution, 1);
                return null;
            });
            assertTrue(flushing.await(5, TimeUnit.SECONDS));

            assertTrue(submitted.get().cancel(true));

            assertThrows(TimeoutException.class, () -> copy.get(100, TimeUnit.MILLISECONDS));
            assertFalse(flushed.get());
            assertTrue(opened.get().isOpen());
            assertTrue(Files.exists(target.resolve("dimensions/0.json")));
            release.countDown();
            ExecutionException failure = assertThrows(ExecutionException.class, () -> copy.get(5, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof CancellationException);
            assertTrue(flushed.get());
            assertFalse(opened.get().isOpen());
        } finally {
            release.countDown();
            caller.shutdownNow();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test(timeout = 15000)
    public void boundedWorkersCopyTheExactFilteredTreeAndAttributes() throws Exception {
        Path source = createPack("source", 48);
        Path target = temporaryFolder.getRoot().toPath().resolve("target");
        Files.createDirectories(source.resolve(".iris"));
        Files.writeString(source.resolve(".iris/cache"), "excluded");
        Files.writeString(source.resolve("source.code-workspace"), "excluded");
        Files.writeString(source.resolve("dimensions/.hidden.json"), "{}");
        FileTime timestamp = FileTime.fromMillis(1_700_000_000_000L);
        Files.setLastModifiedTime(source.resolve("dimensions/0.json"), timestamp);
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger submitted = new AtomicInteger();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        ExecutorService caller = Executors.newSingleThreadExecutor();
        Executor execution = command -> {
            if (submitted.incrementAndGet() == 3) {
                try {
                    assertEquals(fingerprint(source), fingerprint(target));
                } catch (IOException failure) {
                    throw new UncheckedIOException(failure);
                }
            }
            workers.execute(() -> {
                started.countDown();
                await(release);
                command.run();
            });
        };
        try {
            Future<?> copy = caller.submit(() -> {
                GenerationPackRepository.copyPackTree(source, target, execution, 2);
                return null;
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertFalse(copy.isDone());
            release.countDown();
            copy.get(5, TimeUnit.SECONDS);
            assertEquals(4, submitted.get());
            assertEquals(fingerprint(source), fingerprint(target));
            assertEquals(timestamp, Files.getLastModifiedTime(target.resolve("dimensions/0.json")));
            assertFalse(Files.exists(target.resolve(".iris")));
            assertFalse(Files.exists(target.resolve("source.code-workspace")));
            assertTrue(Files.isRegularFile(target.resolve("dimensions/.hidden.json")));
        } finally {
            release.countDown();
            caller.shutdownNow();
            workers.shutdownNow();
        }
    }

    @Test(timeout = 15000)
    public void rejectedSubmissionDrainsAcceptedWorkersBeforeReturning() throws Exception {
        Path source = createPack("rejected-source", 2);
        Path target = temporaryFolder.getRoot().toPath().resolve("rejected-target");
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch rejected = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger submitted = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        RejectedExecutionException expected = new RejectedExecutionException("IO executor closed");
        ExecutorService worker = Executors.newSingleThreadExecutor();
        ExecutorService caller = Executors.newSingleThreadExecutor();
        Executor execution = command -> {
            if (submitted.getAndIncrement() != 0) {
                rejected.countDown();
                throw expected;
            }
            worker.execute(() -> {
                started.countDown();
                await(release);
                command.run();
                completed.incrementAndGet();
            });
        };
        try {
            Future<?> copy = caller.submit(() -> {
                GenerationPackRepository.copyPackTree(source, target, execution, 2);
                return null;
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertTrue(rejected.await(5, TimeUnit.SECONDS));
            assertFalse(copy.isDone());
            release.countDown();
            ExecutionException actual = assertThrows(ExecutionException.class, () -> copy.get(5, TimeUnit.SECONDS));
            assertSame(expected, actual.getCause());
            worker.submit(() -> { }).get(5, TimeUnit.SECONDS);
            assertEquals(1, completed.get());
            assertFalse(Files.exists(target.resolve("dimensions/0.json")));
        } finally {
            release.countDown();
            caller.shutdownNow();
            worker.shutdownNow();
        }
    }

    @Test
    public void fileFailurePropagatesAsIOException() throws Exception {
        Path source = createPack("missing-source", 1);
        Path target = temporaryFolder.getRoot().toPath().resolve("missing-target");
        Executor execution = command -> {
            try {
                Files.delete(source.resolve("dimensions/0.json"));
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
            command.run();
        };

        assertThrows(IOException.class, () -> GenerationPackRepository.copyPackTree(source, target, execution, 1));
    }

    @Test(timeout = 15000)
    public void copyCanJoinWorkSubmittedToItsOwnForkJoinPool() throws Exception {
        Path source = createPack("nested-source", 4);
        Path target = temporaryFolder.getRoot().toPath().resolve("nested-target");
        try (ForkJoinPool pool = new ForkJoinPool(1)) {
            pool.submit(() -> {
                GenerationPackRepository.copyPackTree(source, target, pool, 2);
                return null;
            }).get(5, TimeUnit.SECONDS);
            assertEquals(fingerprint(source), fingerprint(target));
        }
    }

    @Test
    public void emptyPackTreeDoesNotSubmitFileWork() throws Exception {
        Path source = temporaryFolder.newFolder("empty-source").toPath();
        Path target = temporaryFolder.getRoot().toPath().resolve("empty-target");
        Files.createDirectory(source.resolve("empty"));
        GenerationPackRepository.copyPackTree(source, target, command -> {
            throw new AssertionError("Empty packs do not need copy workers");
        }, 2);
        assertTrue(Files.isDirectory(target.resolve("empty")));
    }

    private Path createPack(String name, int files) throws IOException {
        Path root = temporaryFolder.newFolder(name).toPath();
        Files.createDirectory(root.resolve("dimensions"));
        for (int index = 0; index < files; index++) {
            Files.writeString(root.resolve("dimensions/" + index + ".json"), "{\"value\":" + index + "}");
        }
        return root;
    }

    private static String fingerprint(Path root) throws IOException {
        return GenerationPackFingerprint.compute(root, GenerationPackFingerprint.CURRENT_VERSION);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for copy workers");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        try {
            while (true) {
                try {
                    if (!latch.await(Math.max(0L, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) {
                        throw new AssertionError("Timed out waiting for copy worker release");
                    }
                    return;
                } catch (InterruptedException failure) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
