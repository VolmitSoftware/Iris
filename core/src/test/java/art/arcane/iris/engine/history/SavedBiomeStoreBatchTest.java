package art.arcane.iris.engine.history;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedConstruction;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.withSettings;

public class SavedBiomeStoreBatchTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void queuedClaimsShareOneForceAndPublishAfterItCompletes() throws Exception {
        Path root = temporaryFolder.newFolder().toPath();
        SavedBiomeStore store = SavedBiomeStore.open(root);
        store.claimAndPersist(chunk(0, 3L));
        BatchControl control = new BatchControl();
        ExecutorService workers = Executors.newFixedThreadPool(8);
        try {
            List<Future<ClaimResult>> results = enqueue(store, root, control, workers,
                    List.of(chunk(1, 3L), chunk(2, 3L), chunk(3, 3L), chunk(4, 3L),
                            chunk(5, 3L), chunk(6, 3L), chunk(7, 3L), chunk(8, 3L)));
            assertTrue(control.forcing.await(5, TimeUnit.SECONDS));
            for (int index = 1; index <= 8; index++) {
                assertTrue(store.cached(index, 0).isEmpty());
                assertFalse(results.get(index - 1).isDone());
            }
            control.release.countDown();
            for (Future<ClaimResult> result : results) {
                ClaimResult claim = result.get(5, TimeUnit.SECONDS);
                assertNull(claim.failure);
                assertTrue(claim.persisted);
            }
            assertEquals(1, control.forces.get());
            SavedBiomeStore reopened = SavedBiomeStore.open(root);
            for (int index = 1; index <= 8; index++) {
                assertEquals(chunk(index, 3L), reopened.get(index, 0).orElseThrow());
            }
        } finally {
            control.release.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    public void batchForceFailureRollsBackEveryNewClaimButPreservesExistingDuplicates() throws Exception {
        Path root = temporaryFolder.newFolder().toPath();
        SavedBiomeStore store = SavedBiomeStore.open(root);
        store.claimAndPersist(chunk(0, 3L));
        Path region = root.resolve("iris/generation/biomes/r.0.0.ibio");
        byte[] original = Files.readAllBytes(region);
        BatchControl control = new BatchControl();
        control.failure = new IOException("Batch force failed");
        ExecutorService workers = Executors.newFixedThreadPool(4);
        try {
            List<Future<ClaimResult>> results = enqueue(store, root, control, workers,
                    List.of(chunk(0, 3L), chunk(1, 3L), chunk(1, 3L), chunk(2, 3L)));
            assertTrue(control.forcing.await(5, TimeUnit.SECONDS));
            control.release.countDown();
            ClaimResult existing = results.getFirst().get(5, TimeUnit.SECONDS);
            assertFalse(existing.persisted);
            assertNull(existing.failure);
            for (int index = 1; index < results.size(); index++) {
                ClaimResult result = results.get(index).get(5, TimeUnit.SECONDS);
                assertFalse(result.persisted);
                assertSame(control.failure, result.failure);
            }
            assertEquals(2, control.forces.get());
            assertArrayEquals(original, Files.readAllBytes(region));
            assertTrue(store.cached(1, 0).isEmpty());
            assertTrue(store.claimAndPersist(chunk(1, 3L)));
            assertTrue(SavedBiomeStore.open(root).get(2, 0).isEmpty());
        } finally {
            control.release.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    public void queuedDuplicateAndConflictingClaimsHaveOneWinner() throws Exception {
        Path root = temporaryFolder.newFolder().toPath();
        SavedBiomeStore store = SavedBiomeStore.open(root);
        store.claimAndPersist(chunk(0, 3L));
        BatchControl control = new BatchControl();
        ExecutorService workers = Executors.newFixedThreadPool(3);
        try {
            List<Future<ClaimResult>> results = enqueue(store, root, control, workers,
                    List.of(chunk(1, 3L), chunk(1, 3L), chunk(1, 4L)));
            assertTrue(control.forcing.await(5, TimeUnit.SECONDS));
            control.release.countDown();
            int winners = 0;
            SavedBiomeChunk actual = null;
            for (int index = 0; index < results.size(); index++) {
                ClaimResult result = results.get(index).get(5, TimeUnit.SECONDS);
                if (result.persisted) {
                    winners++;
                    actual = chunk(1, index == 2 ? 4L : 3L);
                }
            }
            assertEquals(1, winners);
            assertEquals(1, control.forces.get());
            assertEquals(actual, SavedBiomeStore.open(root).get(1, 0).orElseThrow());
            for (int index = 0; index < results.size(); index++) {
                ClaimResult result = results.get(index).get(5, TimeUnit.SECONDS);
                assertEquals(index == 2 ? actual.activationId() != 4L : actual.activationId() != 3L,
                        result.failure != null);
            }
        } finally {
            control.release.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    public void uncheckedBatchFailureSettlesClaimsAndRejectsEvictedRetries() throws Exception {
        Path root = temporaryFolder.newFolder().toPath();
        SavedBiomeStore store = SavedBiomeStore.open(root);
        store.claimAndPersist(chunk(0, 3L));
        BatchControl control = new BatchControl();
        control.uncheckedFailure = new IllegalStateException("Batch force failed unexpectedly");
        ExecutorService workers = Executors.newFixedThreadPool(4);
        try {
            List<Future<ClaimResult>> results = enqueue(store, root, control, workers,
                    List.of(chunk(1, 3L), chunk(2, 3L), chunk(3, 3L), chunk(4, 3L)));
            assertTrue(control.forcing.await(5, TimeUnit.SECONDS));
            for (int region = 1; region <= 132; region++) {
                if ((region & 63) != 0) {
                    assertTrue(store.get(region << 5, 0).isEmpty());
                }
            }
            control.release.countDown();
            for (Future<ClaimResult> future : results) {
                ClaimResult result = future.get(5, TimeUnit.SECONDS);
                assertFalse(result.persisted);
                assertSame(control.uncheckedFailure, rootCause(result.failure));
            }
            assertEquals(1, control.forces.get());
            IOException retry = assertThrows(IOException.class, () -> store.claimAndPersist(chunk(1, 3L)));
            assertSame(control.uncheckedFailure, rootCause(retry));
            IOException read = assertThrows(IOException.class, () -> store.get(1, 0));
            assertSame(control.uncheckedFailure, rootCause(read));
        } finally {
            control.release.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private static Throwable rootCause(Throwable failure) {
        if (failure == null) {
            return null;
        }
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static List<Future<ClaimResult>> enqueue(SavedBiomeStore store, Path root, BatchControl control,
                                                    ExecutorService workers, List<SavedBiomeChunk> chunks) throws Exception {
        Field locksField = SavedBiomeStore.class.getDeclaredField("regionLocks");
        locksField.setAccessible(true);
        Object stripe = ((Object[]) locksField.get(store))[0];
        Field writingField = stripe.getClass().getDeclaredField("writing");
        Field pendingField = stripe.getClass().getDeclaredField("pending");
        writingField.setAccessible(true);
        pendingField.setAccessible(true);
        Queue<?> pending = (Queue<?>) pendingField.get(stripe);
        ArrayList<Future<ClaimResult>> results = new ArrayList<>(chunks.size());
        synchronized (stripe) {
            writingField.setBoolean(stripe, true);
            try {
                for (SavedBiomeChunk chunk : chunks) {
                    results.add(workers.submit(() -> claim(store, root, control, chunk)));
                }
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (pending.size() != chunks.size() && System.nanoTime() < deadline) {
                    Thread.sleep(1L);
                }
                assertEquals(chunks.size(), pending.size());
            } finally {
                writingField.setBoolean(stripe, false);
            }
        }
        return results;
    }

    private static ClaimResult claim(SavedBiomeStore store, Path root, BatchControl control,
                                     SavedBiomeChunk chunk) throws Exception {
        try (RandomAccessFile actual = new RandomAccessFile(
                root.resolve("iris/generation/biomes/r.0.0.ibio").toFile(), "rw")) {
            FileChannel channel = mock(FileChannel.class, delegatesTo(actual.getChannel()));
            doAnswer(invocation -> {
                int force = control.forces.incrementAndGet();
                if (force == 1) {
                    control.forcing.countDown();
                    assertTrue(control.release.await(5, TimeUnit.SECONDS));
                    if (control.failure != null) {
                        throw control.failure;
                    }
                    if (control.uncheckedFailure != null) {
                        throw control.uncheckedFailure;
                    }
                }
                actual.getChannel().force(true);
                return null;
            }).when(channel).force(true);
            try (MockedConstruction<RandomAccessFile> ignored = mockConstruction(RandomAccessFile.class,
                    withSettings().defaultAnswer(delegatesTo(actual)),
                    (file, context) -> doReturn(channel).when(file).getChannel())) {
                try {
                    return new ClaimResult(store.claimAndPersist(chunk), null);
                } catch (IOException | RuntimeException failure) {
                    return new ClaimResult(false, failure);
                }
            }
        }
    }

    private static SavedBiomeChunk chunk(int x, long activation) {
        SavedBiomeChunk.Cell cell = new SavedBiomeChunk.Cell(activation, "biome", "region");
        SavedBiomeChunk.Column column = new SavedBiomeChunk.Column(cell, cell,
                List.of(new SavedBiomeChunk.Span(-64, 320, cell)));
        SavedBiomeChunk.Builder builder = SavedBiomeChunk.builder(new SavedBiomeChunk.Header(x, 0, activation, -64, 384));
        for (int z = 0; z < 16; z++) {
            for (int localX = 0; localX < 16; localX++) {
                builder.column(localX, z, column);
            }
        }
        return builder.build();
    }

    private static final class BatchControl {
        private final CountDownLatch forcing = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicInteger forces = new AtomicInteger();
        private IOException failure;
        private RuntimeException uncheckedFailure;
    }

    private record ClaimResult(boolean persisted, Throwable failure) {
    }
}
