package art.arcane.iris.world.history;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class GenerationPackDirectorySyncTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test(timeout = 15000)
    public void syncsEveryChildBeforeItsParentWithBoundedSiblingConcurrency() throws Exception {
        Path root = temporaryFolder.newFolder("tree").toPath();
        for (int index = 0; index < 12; index++) {
            Files.createDirectories(root.resolve("branch-" + (index / 3)).resolve("leaf-" + index));
        }
        Set<Path> completed = ConcurrentHashMap.newKeySet();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        CountDownLatch siblings = new CountDownLatch(4);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(8);
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            Future<?> sync = caller.submit(() -> {
                GenerationPackRepository.forceDirectoryTree(root, workers, 4, directory -> {
                    int running = active.incrementAndGet();
                    maximum.accumulateAndGet(running, Math::max);
                    try {
                        if (directory.getFileName().toString().startsWith("leaf-")) {
                            siblings.countDown();
                            await(release);
                        }
                        try (Stream<Path> children = Files.list(directory)) {
                            assertTrue(children.filter(Files::isDirectory).allMatch(completed::contains));
                        }
                        assertTrue(completed.add(directory));
                    } finally {
                        active.decrementAndGet();
                    }
                });
                return null;
            });
            assertTrue(siblings.await(5, TimeUnit.SECONDS));
            assertFalse(sync.isDone());
            assertFalse(completed.contains(root));
            release.countDown();
            sync.get(5, TimeUnit.SECONDS);
            assertEquals(4, maximum.get());
            assertEquals(17, completed.size());
        } finally {
            release.countDown();
            caller.shutdownNow();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test(timeout = 15000)
    public void failedSiblingDrainsStartedSyncsAndNeverSyncsTheParent() throws Exception {
        Path root = temporaryFolder.newFolder("failed-tree").toPath();
        Files.createDirectory(root.resolve("first"));
        Files.createDirectory(root.resolve("second"));
        AtomicInteger next = new AtomicInteger();
        Set<Path> completed = ConcurrentHashMap.newKeySet();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch failed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        IOException expected = new IOException("directory sync failed");
        ExecutorService workers = Executors.newFixedThreadPool(2);
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            Future<?> sync = caller.submit(() -> {
                GenerationPackRepository.forceDirectoryTree(root, workers, 2, directory -> {
                    if (next.getAndIncrement() == 0) {
                        started.countDown();
                        await(release);
                        completed.add(directory);
                    } else {
                        await(started);
                        failed.countDown();
                        throw expected;
                    }
                });
                return null;
            });
            assertTrue(failed.await(5, TimeUnit.SECONDS));
            assertFalse(sync.isDone());
            release.countDown();
            ExecutionException failure = assertThrows(ExecutionException.class, () -> sync.get(5, TimeUnit.SECONDS));
            assertSame(expected, failure.getCause());
            assertEquals(1, completed.size());
            assertFalse(completed.contains(root));
        } finally {
            release.countDown();
            caller.shutdownNow();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test(timeout = 15000)
    public void syncCanJoinItsOwnForkJoinPool() throws Exception {
        Path root = temporaryFolder.newFolder("nested-tree").toPath();
        Files.createDirectory(root.resolve("first"));
        Files.createDirectory(root.resolve("second"));
        AtomicInteger completed = new AtomicInteger();
        try (ForkJoinPool pool = new ForkJoinPool(1)) {
            pool.submit(() -> {
                GenerationPackRepository.forceDirectoryTree(root, pool, 2, directory -> completed.incrementAndGet());
                return null;
            }).get(5, TimeUnit.SECONDS);
        }
        assertEquals(3, completed.get());
    }

    private static void await(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IOException("Directory sync wait timed out");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IOException("Directory sync wait was interrupted", failure);
        }
    }
}
