package art.arcane.iris.engine.history;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedConstruction;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

public class SavedBiomeStoreWriteTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void recordMatchesTheCurrentLengthBodyChecksumFormat() throws Exception {
        Path root = temporaryFolder.newFolder().toPath();
        SavedBiomeStore store = SavedBiomeStore.open(root);
        store.claimAndPersist(chunk(0, 3L));
        assertArrayEquals(expectedRegion(), Files.readAllBytes(regionPath(root)));
    }

    @Test
    public void encodingAllowsOtherReadsInTheSameRegion() throws Exception {
        SavedBiomeStore store = SavedBiomeStore.open(temporaryFolder.newFolder().toPath());
        CountDownLatch encoding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        SavedBiomeChunk chunk = pausedChunk(0, 3L, encoding, release);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> writing = workers.submit(() -> store.claimAndPersist(chunk));
            assertTrue(encoding.await(5, TimeUnit.SECONDS));
            Future<?> reading = workers.submit(() -> {
                assertTrue(store.get(1, 0).isEmpty());
                return null;
            });
            reading.get(1, TimeUnit.SECONDS);
            assertFalse(writing.isDone());
            release.countDown();
            assertTrue(writing.get(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            drain(workers);
        }
    }

    @Test
    public void appendWritesOneFrameAndPublishesOnlyAfterForce() throws Exception {
        Path root = temporaryFolder.newFolder().toPath();
        SavedBiomeStore store = SavedBiomeStore.open(root);
        store.claimAndPersist(chunk(0, 3L));
        CountDownLatch forcing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService workers = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> writing = workers.submit(() -> {
                try (RandomAccessFile actual = new RandomAccessFile(regionPath(root).toFile(), "rw")) {
                    FileChannel channel = mock(FileChannel.class, delegatesTo(actual.getChannel()));
                    doAnswer(invocation -> {
                        forcing.countDown();
                        await(release);
                        actual.getChannel().force(true);
                        return null;
                    }).when(channel).force(true);
                    try (MockedConstruction<RandomAccessFile> files = mockConstruction(RandomAccessFile.class,
                            withSettings().defaultAnswer(delegatesTo(actual)),
                            (file, context) -> doReturn(channel).when(file).getChannel())) {
                        boolean result = store.claimAndPersist(chunk(1, 3L));
                        assertEquals(1, files.constructed().size());
                        verify(files.constructed().getFirst()).write(any(byte[].class));
                        verify(files.constructed().getFirst(), never()).writeInt(anyInt());
                        return result;
                    }
                }
            });
            assertTrue(forcing.await(5, TimeUnit.SECONDS));
            assertTrue(store.cached(1, 0).isEmpty());
            assertFalse(writing.isDone());
            release.countDown();
            assertTrue(writing.get(5, TimeUnit.SECONDS));
            assertEquals(chunk(1, 3L), store.cached(1, 0).orElseThrow());
            assertEquals(chunk(1, 3L), SavedBiomeStore.open(root).get(1, 0).orElseThrow());
        } finally {
            release.countDown();
            drain(workers);
        }
    }

    @Test
    public void failedForceRollsBackWithoutPublishingAndRetainsRollbackFailure() throws Exception {
        for (boolean failRollback : List.of(false, true)) {
            Path root = temporaryFolder.newFolder().toPath();
            SavedBiomeStore store = SavedBiomeStore.open(root);
            store.claimAndPersist(chunk(0, 3L));
            byte[] original = Files.readAllBytes(regionPath(root));
            IOException failure = new IOException("Saved biome force failed");
            IOException rollback = new IOException("Saved biome rollback force failed");
            AtomicBoolean first = new AtomicBoolean(true);
            try (RandomAccessFile actual = new RandomAccessFile(regionPath(root).toFile(), "rw")) {
                FileChannel channel = mock(FileChannel.class, delegatesTo(actual.getChannel()));
                doAnswer(invocation -> {
                    if (first.getAndSet(false)) {
                        throw failure;
                    }
                    if (failRollback) {
                        throw rollback;
                    }
                    actual.getChannel().force(true);
                    return null;
                }).when(channel).force(true);
                try (MockedConstruction<RandomAccessFile> ignored = mockConstruction(RandomAccessFile.class,
                        withSettings().defaultAnswer(delegatesTo(actual)),
                        (file, context) -> doReturn(channel).when(file).getChannel())) {
                    assertSame(failure, assertThrows(IOException.class, () -> store.claimAndPersist(chunk(1, 3L))));
                }
            }
            assertEquals(failRollback ? 1 : 0, failure.getSuppressed().length);
            if (failRollback) {
                assertSame(rollback, failure.getSuppressed()[0]);
            }
            assertArrayEquals(original, Files.readAllBytes(regionPath(root)));
            assertTrue(store.get(1, 0).isEmpty());
            assertTrue(SavedBiomeStore.open(root).get(1, 0).isEmpty());
            if (failRollback) {
                assertSame(failure, assertThrows(IOException.class,
                        () -> store.claimAndPersist(chunk(1, 3L))).getCause());
                assertTrue(SavedBiomeStore.open(root).claimAndPersist(chunk(1, 3L)));
            } else {
                assertTrue(store.claimAndPersist(chunk(1, 3L)));
            }
        }
    }

    @Test
    public void claimsRecheckDuplicatesAndConflictsAfterConcurrentEncoding() throws Exception {
        for (long secondActivation : List.of(3L, 4L)) {
            Path root = temporaryFolder.newFolder().toPath();
            SavedBiomeStore store = SavedBiomeStore.open(root);
            CountDownLatch encoding = new CountDownLatch(2);
            CountDownLatch release = new CountDownLatch(1);
            SavedBiomeChunk first = pausedChunk(0, 3L, encoding, release);
            SavedBiomeChunk second = secondActivation == 3L ? first : pausedChunk(0, secondActivation, encoding, release);
            ExecutorService workers = Executors.newFixedThreadPool(2);
            try {
                Future<ClaimResult> left = workers.submit(() -> claimResult(store, first));
                Future<ClaimResult> right = workers.submit(() -> claimResult(store, second));
                assertTrue(encoding.await(5, TimeUnit.SECONDS));
                release.countDown();
                ClaimResult leftResult = left.get(5, TimeUnit.SECONDS);
                ClaimResult rightResult = right.get(5, TimeUnit.SECONDS);
                assertTrue(leftResult.added() ^ rightResult.added());
                ClaimResult loser = leftResult.added() ? rightResult : leftResult;
                if (secondActivation == 3L) {
                    assertNull(loser.failure());
                } else {
                    assertNotNull(loser.failure());
                }
                long winner = leftResult.added() ? 3L : secondActivation;
                assertEquals(chunk(0, winner), SavedBiomeStore.open(root).get(0, 0).orElseThrow());
            } finally {
                release.countDown();
                drain(workers);
            }
        }
    }

    @Test
    public void appendReloadsRegionOffsetsAfterPruningDuringEncoding() throws Exception {
        Path root = temporaryFolder.newFolder().toPath();
        SavedBiomeStore store = SavedBiomeStore.open(root);
        store.claimAndPersist(chunk(0, 3L));
        store.claimAndPersist(chunk(1, 3L));
        CountDownLatch encoding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        SavedBiomeChunk pending = pausedChunk(2, 3L, encoding, release);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> writing = workers.submit(() -> store.claimAndPersist(pending));
            assertTrue(encoding.await(5, TimeUnit.SECONDS));
            Future<Integer> pruned = workers.submit(() -> store.discardUnstoredClaims(
                    WorldChunkInventory.ofPackedChunks(ChunkGenerationOwnership.packChunk(1, 0)), Set.of(3L)));
            assertEquals(1, (int) pruned.get(1, TimeUnit.SECONDS));
            release.countDown();
            assertTrue(writing.get(5, TimeUnit.SECONDS));
            assertTrue(store.get(0, 0).isEmpty());
            assertEquals(chunk(1, 3L), store.get(1, 0).orElseThrow());
            SavedBiomeStore reopened = SavedBiomeStore.open(root);
            assertTrue(reopened.get(0, 0).isEmpty());
            assertEquals(chunk(1, 3L), reopened.get(1, 0).orElseThrow());
            assertEquals(chunk(2, 3L), reopened.get(2, 0).orElseThrow());
        } finally {
            release.countDown();
            drain(workers);
        }
    }

    private static ClaimResult claimResult(SavedBiomeStore store, SavedBiomeChunk chunk) {
        try {
            return new ClaimResult(store.claimAndPersist(chunk), null);
        } catch (IOException failure) {
            return new ClaimResult(false, failure);
        }
    }

    private static SavedBiomeChunk pausedChunk(int x, long activation, CountDownLatch encoding, CountDownLatch release) {
        SavedBiomeChunk chunk = spy(chunk(x, activation));
        doAnswer(invocation -> {
            if (release.getCount() > 0) {
                encoding.countDown();
                await(release);
            }
            return invocation.callRealMethod();
        }).when(chunk).column(0, 0);
        return chunk;
    }

    private static void await(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IOException("Timed out waiting for saved biome write");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted waiting for saved biome write", failure);
        }
    }

    private static void drain(ExecutorService workers) throws InterruptedException {
        workers.shutdown();
        assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
    }

    private static SavedBiomeChunk chunk(int x, long activation) {
        SavedBiomeChunk.Cell cell = new SavedBiomeChunk.Cell(activation, "biome/main", "region/main");
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

    private static Path regionPath(Path root) {
        return root.resolve("iris/generation/biomes/r.0.0.ibio");
    }

    private static byte[] expectedRegion() throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(raw);
        data.writeLong(3L);
        data.writeInt(-64);
        data.writeInt(384);
        data.writeInt(1);
        data.writeLong(3L);
        data.writeByte(0);
        data.writeUTF("biome/main");
        data.writeUTF("region/main");
        data.writeInt(1);
        data.write(new byte[]{0, 0, 1, (byte) 0x80, 3, 0});
        data.write(new byte[256]);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        DataOutputStream coordinates = new DataOutputStream(body);
        coordinates.writeInt(0);
        coordinates.writeInt(0);
        coordinates.writeInt(raw.size());
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        try (DeflaterOutputStream compressed = new DeflaterOutputStream(coordinates, deflater)) {
            raw.writeTo(compressed);
        } finally {
            deflater.end();
        }
        ByteArrayOutputStream region = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(region);
        output.writeInt(0x4942494F);
        output.writeInt(1);
        output.writeInt(0);
        output.writeInt(0);
        output.writeInt(checksum(region.toByteArray()));
        output.writeInt(body.size());
        output.write(body.toByteArray());
        output.writeInt(checksum(body.toByteArray()));
        return region.toByteArray();
    }

    private static int checksum(byte[] bytes) {
        CRC32 checksum = new CRC32();
        checksum.update(bytes);
        return (int) checksum.getValue();
    }

    private record ClaimResult(boolean added, IOException failure) {
    }
}
