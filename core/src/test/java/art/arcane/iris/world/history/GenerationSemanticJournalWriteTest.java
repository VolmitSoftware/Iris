package art.arcane.iris.world.history;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

public class GenerationSemanticJournalWriteTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void partialAppendRollsBackBeforeAConcurrentClaimCanPublish() throws Exception {
        Path root = temporaryFolder.newFolder().toPath();
        GenerationSemanticIndex index = GenerationSemanticIndex.initialize(root);
        index.claimAndPersist(claim(0, "first"));
        Path journal = index.storageDirectory().resolve("r.0.0.iswal");
        byte[] original = Files.readAllBytes(journal);
        IOException failure = new IOException("Partial journal write");
        CountDownLatch written = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> rejected = executor.submit(() -> {
                try (MockedStatic<FileChannel> ignored = journalChannels((source, intercepted) ->
                        doAnswer(invocation -> {
                            ByteBuffer bytes = invocation.getArgument(0);
                            int limit = bytes.limit();
                            bytes.limit(bytes.position() + 7);
                            source.write(bytes);
                            bytes.limit(limit);
                            written.countDown();
                            assertTrue(release.await(5L, TimeUnit.SECONDS));
                            throw failure;
                        }).when(intercepted).write(any(ByteBuffer.class)))) {
                    return index.claimAndPersist(claim(1, "failed"));
                }
            });
            assertTrue(written.await(5L, TimeUnit.SECONDS));
            CountDownLatch attempted = new CountDownLatch(1);
            Future<Boolean> accepted = executor.submit(() -> {
                attempted.countDown();
                return index.claimAndPersist(claim(2, "second"));
            });
            assertTrue(attempted.await(5L, TimeUnit.SECONDS));
            release.countDown();
            assertSame(failure, assertThrows(ExecutionException.class,
                    () -> rejected.get(5L, TimeUnit.SECONDS)).getCause());
            assertTrue(accepted.get(5L, TimeUnit.SECONDS));
            assertFalse(index.get(1, 0).isPresent());
            GenerationSemanticIndex replayed = GenerationSemanticIndex.loadRequired(root);
            assertEquals(2, replayed.recordCount());
            assertEquals(Optional.of(claim(0, "first")), replayed.get(0, 0));
            assertEquals(Optional.of(claim(2, "second")), replayed.get(2, 0));
            assertTrue(replayed.get(1, 0).isEmpty());
            assertArrayEquals(original, Arrays.copyOf(Files.readAllBytes(journal), original.length));
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5L, TimeUnit.SECONDS));
        }
    }

    @Test
    public void failedForceRestoresBytesAndLeavesTheClaimRetryable() throws Exception {
        Path root = temporaryFolder.newFolder().toPath();
        GenerationSemanticIndex index = GenerationSemanticIndex.initialize(root);
        index.claimAndPersist(claim(0, "first"));
        Path journal = index.storageDirectory().resolve("r.0.0.iswal");
        byte[] original = Files.readAllBytes(journal);
        IOException failure = new IOException("Journal force failed");
        AtomicInteger forces = new AtomicInteger();
        try (MockedStatic<FileChannel> ignored = journalChannels((source, intercepted) ->
                doAnswer(invocation -> {
                    if (forces.incrementAndGet() == 1) {
                        throw failure;
                    }
                    source.force(true);
                    return null;
                }).when(intercepted).force(true))) {
            assertSame(failure, assertThrows(IOException.class, () -> index.claimAndPersist(claim(1, "second"))));
        }
        assertEquals(2, forces.get());
        assertArrayEquals(original, Files.readAllBytes(journal));
        assertTrue(index.get(1, 0).isEmpty());
        assertTrue(index.claimAndPersist(claim(1, "second")));
        assertEquals(Optional.of(claim(1, "second")), GenerationSemanticIndex.loadRequired(root).get(1, 0));
    }

    @Test
    public void rollbackFailurePreservesCausesAndRejectsFurtherMutation() throws Exception {
        Path root = temporaryFolder.newFolder().toPath();
        GenerationSemanticIndex index = GenerationSemanticIndex.initialize(root);
        index.claimAndPersist(claim(0, "first"));
        IOException failure = new IOException("Journal force failed");
        IOException rollback = new IOException("Rollback force failed");
        AtomicInteger forces = new AtomicInteger();
        IOException reported;
        try (MockedStatic<FileChannel> ignored = journalChannels((source, intercepted) ->
                doAnswer(invocation -> {
                    throw forces.incrementAndGet() == 1 ? failure : rollback;
                }).when(intercepted).force(true))) {
            reported = assertThrows(IOException.class, () -> index.claimAndPersist(claim(1, "failed")));
        }
        assertSame(failure, reported.getCause());
        assertArrayEquals(new Throwable[]{rollback}, failure.getSuppressed());
        Path journal = index.storageDirectory().resolve("r.0.0.iswal");
        byte[] afterFailure = Files.readAllBytes(journal);
        assertThrows(IOException.class, () -> index.claimAndPersist(claim(2, "rejected")));
        assertThrows(IOException.class, () -> index.recordAndPersist(claim(2, "rejected")));
        assertThrows(IOException.class, index::compactJournals);
        assertThrows(IllegalStateException.class, () -> index.get(0, 0));
        assertThrows(IllegalStateException.class, () -> match(index, "first"));
        assertThrows(IllegalStateException.class, () -> index.findNearest(GenerationSemanticIndex.Query.acrossActivations(
                GenerationSemanticIndex.SemanticKind.REGION, "iris:first",
                new ChunkGenerationSemantics.BlockPosition(1_000_000, 64, 1_000_000), 32)));
        assertArrayEquals(afterFailure, Files.readAllBytes(journal));
        assertEquals(1, index.recordCount());
    }

    @Test
    public void closeFailureDoesNotAllowFurtherWritesOverAnUnpublishedClaim() throws Exception {
        Path root = temporaryFolder.newFolder().toPath();
        GenerationSemanticIndex index = GenerationSemanticIndex.initialize(root);
        IOException failure = new IOException("Journal close failed");
        try (MockedStatic<FileChannel> ignored = journalChannels((source, intercepted) ->
                doAnswer(invocation -> {
                    source.close();
                    throw failure;
                }).when(intercepted).close())) {
            IOException reported = assertThrows(IOException.class, () -> index.claimAndPersist(claim(1, "second")));
            assertSame(failure, reported.getCause());
        }
        assertEquals(0, index.recordCount());
        assertThrows(IOException.class, () -> index.claimAndPersist(claim(2, "rejected")));
        assertThrows(IllegalStateException.class, index::recordsSnapshot);
        assertThrows(IOException.class, () -> index.forEachRecord(semantics -> {
            throw new AssertionError("Failed journal reached record consumer");
        }));
        assertThrows(IOException.class, () -> index.forEachSealedClaim(1L, (chunkX, chunkZ) -> {
            throw new AssertionError("Failed journal reached sealed claim consumer");
        }));
        GenerationSemanticIndex replayed = GenerationSemanticIndex.loadRequired(root);
        assertEquals(Optional.of(claim(1, "second")), replayed.get(1, 0));
        assertTrue(replayed.get(2, 0).isEmpty());
    }

    @Test
    public void newJournalDirectoryFailureRollsBackBeforeRetry() throws Exception {
        assumeTrue(File.separatorChar != '\\');
        Path root = temporaryFolder.newFolder().toPath();
        GenerationSemanticIndex index = GenerationSemanticIndex.initialize(root);
        IOException failure = new IOException("Journal directory force failed");
        AtomicInteger directoryForces = new AtomicInteger();
        try (MockedStatic<FileChannel> ignored = mockStatic(FileChannel.class, invocation -> {
            FileChannel source = (FileChannel) invocation.callRealMethod();
            Path path = invocation.getArgument(0);
            if (invocation.getMethod().getParameterCount() != 2 || !path.equals(index.storageDirectory())) {
                return source;
            }
            FileChannel intercepted = mock(FileChannel.class, delegatesTo(source));
            doAnswer(force -> {
                if (directoryForces.incrementAndGet() == 1) {
                    throw failure;
                }
                source.force(true);
                return null;
            }).when(intercepted).force(true);
            return intercepted;
        })) {
            assertSame(failure, assertThrows(IOException.class, () -> index.claimAndPersist(claim(0, "first"))));
        }
        assertEquals(2, directoryForces.get());
        assertEquals(0L, Files.size(index.storageDirectory().resolve("r.0.0.iswal")));
        assertTrue(index.get(0, 0).isEmpty());
        assertTrue(index.claimAndPersist(claim(0, "first")));
        assertEquals(Optional.of(claim(0, "first")), GenerationSemanticIndex.loadRequired(root).get(0, 0));
    }

    @Test
    public void queriedSummariesIncludeNewClaimsAfterMutationAndCompaction() throws Exception {
        Path root = temporaryFolder.newFolder().toPath();
        GenerationSemanticIndex index = GenerationSemanticIndex.initialize(root);
        index.claimAndPersist(claim(0, "first"));
        assertEquals(0, match(index, "first").chunk().chunkX());
        index.claimAndPersist(claim(1, "second"));
        assertEquals(1, match(index, "second").chunk().chunkX());
        assertEquals(0, match(index, "first").chunk().chunkX());
        index.compactJournals();
        GenerationSemanticIndex replayed = GenerationSemanticIndex.loadRequired(root);
        assertEquals(0, match(replayed, "first").chunk().chunkX());
        assertEquals(1, match(replayed, "second").chunk().chunkX());
    }

    @Test
    public void queriedSummariesTrackRecordedUpdatesAndPrunedClaims() throws Exception {
        Path root = temporaryFolder.newFolder().toPath();
        GenerationSemanticIndex index = GenerationSemanticIndex.initialize(root);
        index.recordAndPersist(ChunkGenerationSemantics.builder(0, 0, 1L).addRegion("iris:first").build());
        assertEquals(0, match(index, "first").chunk().chunkX());
        index.recordAndPersist(ChunkGenerationSemantics.builder(0, 0, 1L).addRegion("iris:updated").build());
        assertEquals(0, match(index, "updated").chunk().chunkX());
        index.claimAndPersist(claim(1, "second"));
        assertEquals(1, match(index, "second").chunk().chunkX());

        WorldChunkInventory stored = WorldChunkInventory.ofPackedChunks(ChunkGenerationOwnership.packChunk(0, 0));
        assertEquals(1, index.discardUnstoredClaims(stored, Set.of(1L)));
        assertTrue(index.findNearest(GenerationSemanticIndex.Query.acrossActivations(
                GenerationSemanticIndex.SemanticKind.REGION, "iris:second",
                new ChunkGenerationSemantics.BlockPosition(0, 64, 0), 32)).isEmpty());
        assertEquals(0, match(index, "first").chunk().chunkX());
        assertEquals(0, match(index, "updated").chunk().chunkX());
        GenerationSemanticIndex replayed = GenerationSemanticIndex.loadRequired(root);
        assertEquals(0, match(replayed, "updated").chunk().chunkX());
        assertTrue(replayed.get(1, 0).isEmpty());
    }

    private static GenerationSemanticIndex.Match match(GenerationSemanticIndex index, String region) {
        return index.findNearest(GenerationSemanticIndex.Query.acrossActivations(
                GenerationSemanticIndex.SemanticKind.REGION, "iris:" + region,
                new ChunkGenerationSemantics.BlockPosition(0, 64, 0), 32)).orElseThrow();
    }

    private static ChunkGenerationSemantics claim(int chunkX, String region) {
        return ChunkGenerationSemantics.builder(chunkX, 0, 1L).addRegion("iris:" + region).seal().build();
    }

    private static MockedStatic<FileChannel> journalChannels(ChannelMutation mutation) {
        return mockStatic(FileChannel.class, invocation -> {
            FileChannel source = (FileChannel) invocation.callRealMethod();
            Path path = invocation.getArgument(0);
            if (invocation.getMethod().getParameterCount() != 2
                    || !path.getFileName().toString().endsWith(".iswal")) {
                return source;
            }
            FileChannel intercepted = mock(FileChannel.class, delegatesTo(source));
            mutation.apply(source, intercepted);
            return intercepted;
        });
    }

    @FunctionalInterface
    private interface ChannelMutation {
        void apply(FileChannel source, FileChannel intercepted) throws IOException;
    }
}
