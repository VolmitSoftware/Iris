package art.arcane.iris.engine.history;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

public class GenerationSemanticBatchWriteTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void batchedJournalRetainsSerialBytesAndPerClaimResults() throws Exception {
        Path serialRoot = temporaryFolder.newFolder().toPath();
        Path batchedRoot = temporaryFolder.newFolder().toPath();
        GenerationSemanticIndex serial = GenerationSemanticIndex.initialize(serialRoot);
        GenerationSemanticIndex batched = GenerationSemanticIndex.initialize(batchedRoot);
        List<GenerationSemanticIndex.Claim> claims = List.of(
                pending(1, "first"), pending(2, "second"), pending(1, "first"), pending(1, "conflict"));
        serial.claimAndPersist(claims.get(0).update);
        serial.claimAndPersist(claims.get(1).update);

        batched.claimAndPersistBatch(claims);

        assertTrue(claims.get(0).result());
        assertTrue(claims.get(1).result());
        assertFalse(claims.get(2).result());
        assertThrows(IllegalStateException.class, claims.get(3)::result);
        assertArrayEquals(Files.readAllBytes(serial.storageDirectory().resolve("r.0.0.iswal")),
                Files.readAllBytes(batched.storageDirectory().resolve("r.0.0.iswal")));
        assertEquals(serial.recordsSnapshot(), GenerationSemanticIndex.loadRequired(batchedRoot).recordsSnapshot());
    }

    @Test
    public void partialBatchRollbackFailsDependentDuplicatesAndKeepsDurableDuplicates() throws Exception {
        Path root = temporaryFolder.newFolder().toPath();
        GenerationSemanticIndex index = GenerationSemanticIndex.initialize(root);
        index.claimAndPersist(pending(0, "durable").update);
        Path journal = index.storageDirectory().resolve("r.0.0.iswal");
        byte[] original = Files.readAllBytes(journal);
        List<GenerationSemanticIndex.Claim> claims = List.of(
                pending(1, "first"), pending(1, "first"), pending(2, "second"), pending(0, "durable"));
        IOException failure = new IOException("Second frame write failed");
        AtomicInteger writes = new AtomicInteger();
        try (MockedStatic<FileChannel> ignored = channels((path, source, intercepted) ->
                doAnswer(invocation -> {
                    ByteBuffer bytes = invocation.getArgument(0);
                    if (writes.incrementAndGet() == 2) {
                        int limit = bytes.limit();
                        bytes.limit(bytes.position() + 7);
                        source.write(bytes);
                        bytes.limit(limit);
                        throw failure;
                    }
                    return source.write(bytes);
                }).when(intercepted).write(any(ByteBuffer.class)))) {
            index.claimAndPersistBatch(claims);
        }
        for (int indexInBatch = 0; indexInBatch < 3; indexInBatch++) {
            assertSame(failure, assertThrows(IOException.class, claims.get(indexInBatch)::result));
        }
        assertFalse(claims.get(3).result());
        assertArrayEquals(original, Files.readAllBytes(journal));
        assertEquals(1, GenerationSemanticIndex.loadRequired(root).recordCount());
        List<GenerationSemanticIndex.Claim> retry = List.of(pending(1, "first"), pending(2, "second"));
        index.claimAndPersistBatch(retry);
        assertTrue(retry.get(0).result());
        assertTrue(retry.get(1).result());
        assertEquals(3, GenerationSemanticIndex.loadRequired(root).recordCount());
    }

    @Test
    public void failedRegionDoesNotUndoOtherDurableRegionResults() throws Exception {
        Path root = temporaryFolder.newFolder().toPath();
        GenerationSemanticIndex index = GenerationSemanticIndex.initialize(root);
        List<GenerationSemanticIndex.Claim> claims = List.of(
                pending(0, "first"), pending(32, "failed"), pending(64, "last"));
        IOException failure = new IOException("Middle region force failed");
        AtomicInteger forces = new AtomicInteger();
        try (MockedStatic<FileChannel> ignored = channels((path, source, intercepted) -> {
            if (!path.getFileName().toString().equals("r.1.0.iswal")) {
                return;
            }
            doAnswer(invocation -> {
                if (forces.incrementAndGet() == 1) {
                    throw failure;
                }
                source.force(true);
                return null;
            }).when(intercepted).force(true);
        })) {
            index.claimAndPersistBatch(claims);
        }
        assertTrue(claims.get(0).result());
        assertSame(failure, assertThrows(IOException.class, claims.get(1)::result));
        assertTrue(claims.get(2).result());
        GenerationSemanticIndex loaded = GenerationSemanticIndex.loadRequired(root);
        assertEquals(2, loaded.recordCount());
        assertTrue(loaded.get(32, 0).isEmpty());
    }

    @Test
    public void unexpectedBatchFailureCompletesEveryClaimAndPoisonsFurtherReads() throws Exception {
        GenerationSemanticIndex index = GenerationSemanticIndex.initialize(temporaryFolder.newFolder().toPath());
        List<GenerationSemanticIndex.Claim> claims = List.of(
                pending(0, "first"), pending(0, "first"), pending(32, "other-region"));
        IllegalStateException failure = new IllegalStateException("Unexpected channel failure");
        try (MockedStatic<FileChannel> ignored = channels((path, source, intercepted) ->
                doAnswer(invocation -> {
                    throw failure;
                }).when(intercepted).write(any(ByteBuffer.class)))) {
            assertSame(failure, assertThrows(IllegalStateException.class, () -> index.claimAndPersistBatch(claims)));
        }
        for (GenerationSemanticIndex.Claim claim : claims) {
            assertTrue(claim.completed);
            assertSame(failure, assertThrows(IllegalStateException.class, claim::result));
        }
        assertThrows(IllegalStateException.class, () -> index.get(0, 0));
        assertThrows(IOException.class, () -> index.claimAndPersist(pending(1, "later").update));
    }

    private static GenerationSemanticIndex.Claim pending(int chunkX, String biome) {
        return new GenerationSemanticIndex.Claim(ChunkGenerationSemantics.builder(chunkX, 0, 1L)
                .addSurfaceBiome("iris:" + biome).seal().build());
    }

    private static MockedStatic<FileChannel> channels(ChannelMutation mutation) {
        return mockStatic(FileChannel.class, invocation -> {
            FileChannel source = (FileChannel) invocation.callRealMethod();
            Path path = invocation.getArgument(0);
            if (invocation.getMethod().getParameterCount() != 2
                    || !path.getFileName().toString().endsWith(".iswal")) {
                return source;
            }
            FileChannel intercepted = mock(FileChannel.class, delegatesTo(source));
            mutation.apply(path, source, intercepted);
            return intercepted;
        });
    }

    private interface ChannelMutation {
        void apply(Path path, FileChannel source, FileChannel intercepted) throws IOException;
    }
}
