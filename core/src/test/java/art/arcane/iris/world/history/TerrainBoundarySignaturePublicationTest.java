package art.arcane.iris.world.history;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

public final class TerrainBoundarySignaturePublicationTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void syncsAllShardFilesThenDirectoryBeforePublishingEitherCatalogPath() throws Exception {
        assumeTrue(File.separatorChar != '\\');
        GenerationBoundary boundary = GenerationBoundary.freeze("a".repeat(64), List.of(
                new GenerationBoundary.ChunkCoordinate(0, 0),
                new GenerationBoundary.ChunkCoordinate(4, 0),
                new GenerationBoundary.ChunkCoordinate(8, 0)));
        List<TerrainBoundarySignature> signatures = new ArrayList<>();
        boundary.forEachExposedBlockColumn((x, z) -> signatures.add(signature(x, z)));
        for (boolean streamed : new boolean[]{false, true}) {
            TerrainBoundarySignatureStore store = store();
            List<String> events = new ArrayList<>();
            try (MockedStatic<FileChannel> ignored = channels(path -> {
                if (path.equals(store.directory())) {
                    assertEquals(3, shards(store).size());
                    events.add(Files.exists(store.snapshotPath(1L)) ? "catalog-directory" : "shard-directory");
                } else if (path.getFileName().toString().endsWith(".tmp")) {
                    events.add(fileKind(path));
                }
            })) {
                if (streamed) {
                    store.publish(1L, boundary, TerrainBoundarySignaturePublicationTest::signature);
                } else {
                    store.publish(1L, signatures);
                }
            }
            assertEquals(List.of("shard", "shard", "shard", "shard-directory", "catalog", "catalog-directory"),
                    events);
            assertEquals(signatures.size(), store.load(1L).size());
        }
    }

    @Test
    public void failedShardDirectoryBarrierDoesNotPublishCatalogAndRetrySyncsExistingShards() throws Exception {
        assumeTrue(File.separatorChar != '\\');
        TerrainBoundarySignatureStore store = store();
        List<TerrainBoundarySignature> signatures = List.of(signature(0, 0), signature(64, 0));
        IOException failure = new IOException("Shard directory force failed");
        try (MockedStatic<FileChannel> ignored = channels(path -> {
            if (path.equals(store.directory())) {
                throw failure;
            }
        })) {
            assertSame(failure, assertThrows(IOException.class, () -> store.publish(1L, signatures)));
        }
        assertFalse(Files.exists(store.snapshotPath(1L)));
        assertEquals(2, shards(store).size());
        List<String> events = new ArrayList<>();
        try (MockedStatic<FileChannel> ignored = channels(path -> {
            if (path.equals(store.directory())) {
                events.add(Files.exists(store.snapshotPath(1L)) ? "catalog-directory" : "shard-directory");
            } else if (path.getFileName().toString().endsWith(".tmp")) {
                events.add(fileKind(path));
            }
        })) {
            assertEquals(2, store.publish(1L, signatures).size());
        }
        assertEquals(List.of("shard-directory", "catalog", "catalog-directory"), events);
    }

    @Test
    public void failedCatalogDirectoryBarrierIsRetriedBeforeExistingCatalogReturns() throws Exception {
        assumeTrue(File.separatorChar != '\\');
        TerrainBoundarySignatureStore store = store();
        List<TerrainBoundarySignature> signatures = List.of(signature(0, 0));
        IOException failure = new IOException("Catalog directory force failed");
        try (MockedStatic<FileChannel> ignored = channels(path -> {
            if (path.equals(store.directory()) && Files.exists(store.snapshotPath(1L))) {
                throw failure;
            }
        })) {
            assertSame(failure, assertThrows(IOException.class, () -> store.publish(1L, signatures)));
        }
        assertTrue(Files.exists(store.snapshotPath(1L)));
        AtomicInteger directoryForces = new AtomicInteger();
        try (MockedStatic<FileChannel> ignored = channels(path -> {
            if (path.equals(store.directory())) {
                directoryForces.incrementAndGet();
            }
            assertFalse(path.getFileName().toString().endsWith(".tmp"));
        })) {
            assertEquals(1, store.publish(1L, signatures).size());
        }
        assertEquals(1, directoryForces.get());
    }

    @Test
    public void shardForceFailureCannotPublishShardOrCatalog() throws Exception {
        TerrainBoundarySignatureStore store = store();
        IOException failure = new IOException("Shard file force failed");
        try (MockedStatic<FileChannel> ignored = channels(path -> {
            if (path.getFileName().toString().endsWith(".tmp") && fileKind(path).equals("shard")) {
                throw failure;
            }
        })) {
            assertSame(failure, assertThrows(IOException.class,
                    () -> store.publish(1L, List.of(signature(0, 0)))));
        }
        assertTrue(shards(store).isEmpty());
        assertFalse(Files.exists(store.snapshotPath(1L)));
    }

    @Test
    public void existingCorruptShardCannotReachCatalogPublication() throws Exception {
        TerrainBoundarySignatureStore store = store();
        List<TerrainBoundarySignature> signatures = List.of(signature(0, 0));
        store.publish(1L, signatures);
        Path shard = shards(store).getFirst();
        byte[] bytes = Files.readAllBytes(shard);
        bytes[bytes.length - 1] ^= 1;
        Files.write(shard, bytes);
        assertThrows(IOException.class, () -> store.publish(2L, signatures));
        assertFalse(Files.exists(store.snapshotPath(2L)));
    }

    private TerrainBoundarySignatureStore store() throws IOException {
        TerrainBoundarySignatureStore store = new TerrainBoundarySignatureStore(temporaryFolder.newFolder().toPath());
        Files.createDirectories(store.directory());
        return store;
    }

    private static List<Path> shards(TerrainBoundarySignatureStore store) throws IOException {
        List<Path> shards = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(store.directory(), "*.irtm")) {
            for (Path entry : entries) {
                shards.add(entry);
            }
        }
        return shards;
    }

    private static String fileKind(Path path) throws IOException {
        try (DataInputStream input = new DataInputStream(Files.newInputStream(path))) {
            return switch (input.readInt()) {
                case 0x4952544D -> "shard";
                case 0x49525453 -> "catalog";
                default -> throw new IOException("Unexpected terrain boundary file");
            };
        }
    }

    private static MockedStatic<FileChannel> channels(ForceObserver observer) {
        return mockStatic(FileChannel.class, invocation -> {
            FileChannel source = (FileChannel) invocation.callRealMethod();
            if (invocation.getMethod().getParameterCount() != 2) {
                return source;
            }
            Path path = invocation.getArgument(0);
            FileChannel intercepted = mock(FileChannel.class, delegatesTo(source));
            doAnswer(force -> {
                observer.force(path);
                source.force(true);
                return null;
            }).when(intercepted).force(true);
            return intercepted;
        });
    }

    private static TerrainBoundarySignature signature(int x, int z) {
        return new TerrainBoundarySignature(
                new TerrainBoundarySignature.Column(x, z, 80 + x, 60, OptionalInt.empty(), OptionalInt.empty()),
                new TerrainBoundarySignature.Samples(new TerrainBoundarySignature.VerticalLayout(-64, 32, 1),
                        new TerrainBoundarySignature.BiomeEncoding(List.of("iris:plains"), new short[]{0})),
                BoundaryColumnGeometry.empty());
    }

    @FunctionalInterface
    private interface ForceObserver {
        void force(Path path) throws IOException;
    }
}
