package art.arcane.iris.world.history;

import art.arcane.iris.world.storage.Durability;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

public final class GenerationUnpublishedHistoryTest extends GenerationHistorySupport {
    @Test
    public void unpublishedHistoryRequiresNewRootAndReopeningRestoresImmediateBiomeWrites() throws Exception {
        Path root = temporaryFolder.getRoot().toPath().resolve("unpublished");
        Path pack = createPack("unpublished-pack", "alpha");
        GenerationHistory.FreshCreation creation = new GenerationHistory.FreshCreation(root, pack, fingerprint(pack),
                42L, contract(), GenerationRegistryContract.empty());
        GenerationHistory history = GenerationHistory.createUnpublished(creation);
        assertThrows(FileAlreadyExistsException.class, () -> GenerationHistory.createUnpublished(creation));
        history.savedBiomes().claimAndPersist(biomes(0));
        assertBiomeAppendForces(history.savedBiomes(), root, 1, 0);
        GenerationHistory reopened = GenerationHistory.open(root);
        assertEquals(biomes(1), reopened.savedBiomes().get(1, 0).orElseThrow());
        assertBiomeAppendForces(reopened.savedBiomes(), root, 2, 1);
    }

    @Test
    public void semanticAppendDeferralDoesNotChangeReplayOrNormalOpenDurability() throws Exception {
        Path root = temporaryFolder.newFolder("semantics").toPath();
        GenerationSemanticIndex.initialize(root);
        GenerationSemanticIndex staged = GenerationSemanticIndex.loadRequired(root, true);
        staged.claimAndPersist(semantics(0));
        try (MockedStatic<Durability> forces = mockStatic(Durability.class, CALLS_REAL_METHODS)) {
            assertTrue(staged.claimAndPersist(semantics(1)));
            forces.verify(() -> Durability.force(any(FileChannel.class)), times(0));
        }
        GenerationSemanticIndex reopened = GenerationSemanticIndex.loadRequired(root);
        assertEquals(semantics(1), reopened.get(1, 0).orElseThrow());
        try (MockedStatic<Durability> forces = mockStatic(Durability.class, CALLS_REAL_METHODS)) {
            assertTrue(reopened.claimAndPersist(semantics(2)));
            forces.verify(() -> Durability.force(any(FileChannel.class)), times(1));
        }
        reopened.compactJournals();
        assertEquals(3, GenerationSemanticIndex.loadRequired(root).recordCount());
    }

    @Test
    public void unpublishedBiomeWriteFailureStillRollsBackAndDoesNotPublishClaim() throws Exception {
        Path root = temporaryFolder.newFolder("failed-biomes").toPath();
        SavedBiomeStore store = SavedBiomeStore.open(root, true);
        store.claimAndPersist(biomes(0));
        Path region = region(root);
        byte[] before = Files.readAllBytes(region);
        try (RandomAccessFile actual = new RandomAccessFile(region.toFile(), "rw")) {
            FileChannel channel = mock(FileChannel.class, delegatesTo(actual.getChannel()));
            doThrow(new IOException("Expected staged write failure")).when(channel).write(any(ByteBuffer[].class), anyInt(), anyInt());
            try (MockedConstruction<RandomAccessFile> ignored = mockConstruction(RandomAccessFile.class,
                    withSettings().defaultAnswer(delegatesTo(actual)),
                    (file, context) -> doReturn(channel).when(file).getChannel())) {
                assertThrows(IOException.class, () -> store.claimAndPersist(biomes(1)));
            }
            verify(channel, times(0)).force(true);
        }
        assertArrayEquals(before, Files.readAllBytes(region));
        assertTrue(store.get(1, 0).isEmpty());
        assertFalse(SavedBiomeStore.open(root).get(1, 0).isPresent());
        assertTrue(store.claimAndPersist(biomes(1)));
    }

    private static void assertBiomeAppendForces(SavedBiomeStore store, Path root, int x, int count) throws Exception {
        try (RandomAccessFile actual = new RandomAccessFile(region(root).toFile(), "rw")) {
            FileChannel channel = mock(FileChannel.class, delegatesTo(actual.getChannel()));
            try (MockedConstruction<RandomAccessFile> ignored = mockConstruction(RandomAccessFile.class,
                    withSettings().defaultAnswer(delegatesTo(actual)),
                    (file, context) -> doReturn(channel).when(file).getChannel())) {
                assertTrue(store.claimAndPersist(biomes(x)));
            }
            verify(channel, times(count)).force(true);
        }
    }

    private static Path region(Path root) {
        return root.resolve("iris/generation/biomes/r.0.0.ibio");
    }

    private static ChunkGenerationSemantics semantics(int x) {
        return ChunkGenerationSemantics.builder(x, 0, 1L).addSurfaceBiome("biome/main").seal().build();
    }

    private static SavedBiomeChunk biomes(int x) {
        SavedBiomeChunk.Cell cell = new SavedBiomeChunk.Cell(1L, "biome/main", "region/main");
        SavedBiomeChunk.Column column = new SavedBiomeChunk.Column(cell, cell,
                List.of(new SavedBiomeChunk.Span(-64, 320, cell)));
        SavedBiomeChunk.Builder builder = SavedBiomeChunk.builder(new SavedBiomeChunk.Header(x, 0, 1L, -64, 384));
        for (int z = 0; z < 16; z++) {
            for (int localX = 0; localX < 16; localX++) {
                builder.column(localX, z, column);
            }
        }
        return builder.build();
    }
}
