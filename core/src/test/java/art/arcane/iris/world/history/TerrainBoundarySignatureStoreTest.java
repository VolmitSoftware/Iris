package art.arcane.iris.world.history;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.CRC32;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class TerrainBoundarySignatureStoreTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void preservesPresentAndAbsentUpperCeilingsAcrossShardReloads() throws Exception {
        TerrainBoundarySignatureStore store = new TerrainBoundarySignatureStore(
                temporaryFolder.newFolder("upper-ceiling-shards").toPath());
        TerrainBoundarySignature.Samples samples = new TerrainBoundarySignature.Samples(
                new TerrainBoundarySignature.VerticalLayout(-64, 4, 0),
                new TerrainBoundarySignature.BiomeEncoding(List.of(), new short[0]));
        TerrainBoundarySignature present = new TerrainBoundarySignature(
                new TerrainBoundarySignature.Column(-17, 8, 64, 64, OptionalInt.empty(), OptionalInt.of(80)), samples, BoundaryColumnGeometry.empty());
        TerrainBoundarySignature absent = new TerrainBoundarySignature(
                new TerrainBoundarySignature.Column(257, 8, 64, 64, OptionalInt.empty(), OptionalInt.empty()), samples, BoundaryColumnGeometry.empty());
        store.publish(2L, List.of(present, absent));

        TerrainBoundarySignatureStore.Snapshot reopened = store.load(2L);

        assertEquals(OptionalInt.of(80), reopened.signatureAt(-17, 8).orElseThrow().upperCeilingDepth());
        assertTrue(reopened.signatureAt(257, 8).orElseThrow().upperCeilingDepth().isEmpty());
    }

    @Test
    public void preservesEveryCompactFieldAtNegativeCoordinates() throws Exception {
        Path dimensionRoot = temporaryFolder.newFolder("signature-dimension").toPath();
        TerrainBoundarySignatureStore store = new TerrainBoundarySignatureStore(dimensionRoot);
        TerrainBoundarySignature negative = signature(
                -17,
                Integer.MIN_VALUE,
                93,
                -48,
                OptionalInt.of(62),
                -64,
                32,
                List.of("iris:forest", "iris:cavern", "iris:forest"),
                new short[]{2, 1, 0}
        );
        TerrainBoundarySignature positive = signature(
                Integer.MAX_VALUE,
                4,
                77,
                41,
                OptionalInt.empty(),
                0,
                1,
                List.of(),
                new short[0]
        );

        TerrainBoundarySignatureStore.Snapshot published = store.publish(19L, List.of(positive, negative));
        TerrainBoundarySignatureStore.Snapshot loaded = store.load(19L);

        assertEquals(dimensionRoot.toAbsolutePath().resolve("iris/generation/boundaries"), store.directory());
        assertEquals(19L, published.activationId());
        assertEquals(published.identity(), loaded.identity());
        assertEquals(2, loaded.size());
        assertSignatureEquals(negative, loaded.signatureAt(-17, Integer.MIN_VALUE).orElseThrow());
        assertSignatureEquals(positive, loaded.signatureAt(Integer.MAX_VALUE, 4).orElseThrow());
        assertTrue(loaded.signatureAt(0, 0).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> loaded.signatures().clear());
    }

    @Test
    public void writesDeterministicallyRegardlessOfCollectionOrder() throws Exception {
        Path firstRoot = temporaryFolder.newFolder("deterministic-first").toPath();
        Path secondRoot = temporaryFolder.newFolder("deterministic-second").toPath();
        TerrainBoundarySignatureStore firstStore = new TerrainBoundarySignatureStore(firstRoot);
        TerrainBoundarySignatureStore secondStore = new TerrainBoundarySignatureStore(secondRoot);
        TerrainBoundarySignature first = signature(
                -1,
                9,
                80,
                40,
                OptionalInt.empty(),
                -32,
                16,
                List.of("iris:snowy_森林"),
                new short[]{0, 0}
        );
        TerrainBoundarySignature second = signature(
                12,
                -8,
                70,
                35,
                OptionalInt.of(50),
                5,
                4,
                List.of("iris:desert"),
                new short[]{0}
        );

        TerrainBoundarySignatureStore.Snapshot firstSnapshot = firstStore.publish(8L, List.of(first, second));
        TerrainBoundarySignatureStore.Snapshot secondSnapshot = secondStore.publish(8L, List.of(second, first));

        assertEquals(firstSnapshot.identity(), secondSnapshot.identity());
        assertArrayEquals(
                Files.readAllBytes(firstStore.snapshotPath(8L)),
                Files.readAllBytes(secondStore.snapshotPath(8L))
        );
    }

    @Test
    public void streamedBoundaryMatchesCollectionPublicationAcrossCells() throws Exception {
        TerrainBoundarySignatureStore streamed = new TerrainBoundarySignatureStore(
                temporaryFolder.newFolder("streamed-boundary").toPath()
        );
        TerrainBoundarySignatureStore collected = new TerrainBoundarySignatureStore(
                temporaryFolder.newFolder("collected-boundary").toPath()
        );
        List<GenerationBoundary.ChunkCoordinate> chunks = new ArrayList<>();
        for (int index = -40; index < 40; index++) {
            chunks.add(new GenerationBoundary.ChunkCoordinate(index * 3, index % 5));
        }
        GenerationBoundary boundary = GenerationBoundary.freeze("a".repeat(64), chunks);
        List<TerrainBoundarySignature> signatures = new ArrayList<>();
        boundary.forEachExposedBlockColumn((x, z) -> signatures.add(simpleSignature(x, z, 81)));

        TerrainBoundarySignatureStore.Snapshot first = streamed.publish(
                2L, boundary, (x, z) -> simpleSignature(x, z, 81)
        );
        TerrainBoundarySignatureStore.Snapshot second = collected.publish(2L, signatures);

        assertEquals(boundary.exposedBlockColumnCount(), first.size());
        assertEquals(second.identity(), first.identity());
        assertArrayEquals(Files.readAllBytes(collected.snapshotPath(2L)), Files.readAllBytes(streamed.snapshotPath(2L)));
        boundary.forEachExposedBlockColumn((x, z) -> assertTrue(first.signatureAt(x, z).isPresent()));
        assertTrue(first.cachedShardCount() <= 64);
    }

    @Test
    public void streamedBoundaryDoesNotPublishOnMissingOrMisplacedSamples() throws Exception {
        TerrainBoundarySignatureStore store = new TerrainBoundarySignatureStore(
                temporaryFolder.newFolder("failed-streamed-boundary").toPath()
        );
        GenerationBoundary boundary = GenerationBoundary.freeze(
                "a".repeat(64), List.of(new GenerationBoundary.ChunkCoordinate(0, 0))
        );

        assertThrows(IOException.class, () -> store.publish(2L, boundary, (x, z) -> {
            throw new IOException("sample failed");
        }));
        assertFalse(Files.exists(store.snapshotPath(2L)));
        assertThrows(IOException.class, () -> store.publish(2L, boundary, (x, z) -> simpleSignature(x + 1, z, 81)));
        assertFalse(Files.exists(store.snapshotPath(2L)));
        assertEquals(60, store.publish(2L, boundary, (x, z) -> simpleSignature(x, z, 81)).size());
    }

    @Test
    public void canonicalizesEquivalentBiomePalettesBeforeIdentityAndPublication() throws Exception {
        Path firstRoot = temporaryFolder.newFolder("palette-first").toPath();
        Path secondRoot = temporaryFolder.newFolder("palette-second").toPath();
        TerrainBoundarySignature first = signature(
                5,
                -7,
                80,
                40,
                OptionalInt.empty(),
                -64,
                32,
                List.of("iris:z", "iris:a", "iris:z", "iris:unused"),
                new short[]{0, 1, 2}
        );
        TerrainBoundarySignature equivalent = signature(
                5,
                -7,
                80,
                40,
                OptionalInt.empty(),
                -64,
                32,
                List.of("iris:a", "iris:z"),
                new short[]{1, 0, 1}
        );
        TerrainBoundarySignatureStore firstStore = new TerrainBoundarySignatureStore(firstRoot);
        TerrainBoundarySignatureStore secondStore = new TerrainBoundarySignatureStore(secondRoot);

        TerrainBoundarySignatureStore.Snapshot firstSnapshot = firstStore.publish(13L, List.of(first));
        TerrainBoundarySignatureStore.Snapshot repeated = firstStore.publish(13L, List.of(equivalent));
        TerrainBoundarySignatureStore.Snapshot secondSnapshot = secondStore.publish(13L, List.of(equivalent));

        assertEquals(firstSnapshot.identity(), repeated.identity());
        assertEquals(firstSnapshot.identity(), secondSnapshot.identity());
        assertArrayEquals(
                Files.readAllBytes(firstStore.snapshotPath(13L)),
                Files.readAllBytes(secondStore.snapshotPath(13L))
        );
        TerrainBoundarySignature loaded = firstStore.load(13L).signatureAt(5, -7).orElseThrow();
        assertEquals(List.of("iris:a", "iris:z"), loaded.samples().biomes().palette());
        assertArrayEquals(new short[]{1, 0, 1}, loaded.samples().biomes().paletteIndices());
    }

    @Test
    public void keepsActivationSnapshotImmutableAndRejectsDuplicateCoordinates() throws Exception {
        Path dimensionRoot = temporaryFolder.newFolder("immutable-signature-dimension").toPath();
        TerrainBoundarySignatureStore store = new TerrainBoundarySignatureStore(dimensionRoot);
        TerrainBoundarySignature original = simpleSignature(-5, 6, 81);
        TerrainBoundarySignature changed = simpleSignature(-5, 6, 82);

        TerrainBoundarySignatureStore.Snapshot first = store.publish(2L, List.of(original));
        TerrainBoundarySignatureStore.Snapshot repeated = store.publish(2L, List.of(original));

        assertEquals(first.identity(), repeated.identity());
        assertThrows(FileAlreadyExistsException.class, () -> store.publish(2L, List.of(changed)));
        assertThrows(IllegalArgumentException.class, () -> store.publish(3L, List.of(original, changed)));
        assertEquals(81, store.load(2L).signatureAt(-5, 6).orElseThrow().surfaceHeight());
    }

    @Test
    public void failsClosedForChecksumCorruptionAndTruncation() throws Exception {
        Path dimensionRoot = temporaryFolder.newFolder("corrupt-signature-dimension").toPath();
        TerrainBoundarySignatureStore store = new TerrainBoundarySignatureStore(dimensionRoot);
        store.publish(4L, List.of(simpleSignature(1, -2, 70)));
        Path corrupted = store.snapshotPath(4L);
        byte[] corruptedBytes = Files.readAllBytes(corrupted);
        corruptedBytes[8] ^= 0x01;
        Files.write(corrupted, corruptedBytes);

        IOException checksumFailure = assertThrows(IOException.class, () -> store.load(4L));
        assertTrue(checksumFailure.getMessage().contains("checksum"));

        store.publish(5L, List.of(simpleSignature(-3, 7, 72)));
        Path truncated = store.snapshotPath(5L);
        byte[] complete = Files.readAllBytes(truncated);
        Files.write(truncated, Arrays.copyOf(complete, complete.length - 2));
        assertThrows(IOException.class, () -> store.load(5L));
    }

    @Test
    public void verifiesContentIdentityAndDeclaredBounds() throws Exception {
        Path dimensionRoot = temporaryFolder.newFolder("verified-signature-dimension").toPath();
        TerrainBoundarySignatureStore store = new TerrainBoundarySignatureStore(dimensionRoot);
        store.publish(9L, List.of(simpleSignature(-9, -10, 90)));
        Path identitySnapshot = store.snapshotPath(9L);
        byte[] identityBytes = Files.readAllBytes(identitySnapshot);
        identityBytes[20] ^= 0x01;
        updateChecksum(identityBytes);
        Files.write(identitySnapshot, identityBytes);

        IOException identityFailure = assertThrows(IOException.class, () -> store.load(9L));
        assertTrue(identityFailure.getMessage().contains("identity mismatch"));

        store.publish(10L, List.of());
        Path boundedSnapshot = store.snapshotPath(10L);
        byte[] boundedBytes = Files.readAllBytes(boundedSnapshot);
        ByteBuffer.wrap(boundedBytes).putInt(16, Integer.MAX_VALUE);
        updateChecksum(boundedBytes);
        Files.write(boundedSnapshot, boundedBytes);
        assertThrows(IOException.class, () -> store.load(10L));
    }

    @Test
    public void rejectsInvalidActivationAndMalformedUtf8() throws Exception {
        Path dimensionRoot = temporaryFolder.newFolder("invalid-signature-dimension").toPath();
        TerrainBoundarySignatureStore store = new TerrainBoundarySignatureStore(dimensionRoot);
        assertThrows(IllegalArgumentException.class, () -> store.publish(0L, List.of()));
        String malformed = new String(new char[]{'i', 'r', 'i', 's', ':', '\uD800'});
        TerrainBoundarySignature invalid = signature(
                0,
                0,
                1,
                0,
                OptionalInt.empty(),
                0,
                1,
                List.of(malformed),
                new short[]{0}
        );
        assertThrows(IOException.class, () -> store.publish(1L, List.of(invalid)));
        assertFalse(Files.exists(store.snapshotPath(1L)));
    }

    @Test
    public void rejectsSymbolicLinksAtEveryStorageComponent() throws Exception {
        Path probe = temporaryFolder.newFolder("terrain-symlink-probe").toPath();
        Path probeTarget = temporaryFolder.newFolder("terrain-symlink-probe-target").toPath();
        try {
            Files.createSymbolicLink(probe.resolve("link"), probeTarget);
        } catch (IOException | UnsupportedOperationException exception) {
            Assume.assumeNoException(exception);
        }

        for (String component : new String[]{"dimension", "iris", "generation", "boundaries"}) {
            assertStorageLinkRejected(component);
        }
    }

    @Test
    public void concurrentStoreInstancesCannotClobberTerrainSignatures() throws Exception {
        Path dimensionRoot = temporaryFolder.newFolder("terrain-race").toPath();
        TerrainBoundarySignatureStore firstStore = new TerrainBoundarySignatureStore(dimensionRoot);
        TerrainBoundarySignatureStore secondStore = new TerrainBoundarySignatureStore(dimensionRoot);
        TerrainBoundarySignature firstSignature = simpleSignature(1, 2, 70);
        TerrainBoundarySignature secondSignature = simpleSignature(3, 4, 90);
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(
                    () -> publishAfterBarrier(firstStore, 22L, firstSignature, barrier)
            );
            Future<Boolean> second = executor.submit(
                    () -> publishAfterBarrier(secondStore, 22L, secondSignature, barrier)
            );

            assertTrue(first.get() ^ second.get());
            TerrainBoundarySignatureStore.Snapshot loaded = firstStore.load(22L);
            assertTrue(loaded.signatureAt(1, 2).isPresent() ^ loaded.signatureAt(3, 4).isPresent());
        } finally {
            executor.shutdownNow();
        }
    }

    private void assertStorageLinkRejected(String component) throws Exception {
        Path container = temporaryFolder.newFolder("terrain-linked-" + component).toPath();
        Path dimensionRoot = container.resolve("dimension");
        Path external = temporaryFolder.newFolder("terrain-linked-target-" + component).toPath();
        if (component.equals("dimension")) {
            Files.createSymbolicLink(dimensionRoot, external);
        } else {
            Files.createDirectory(dimensionRoot);
            Path iris = dimensionRoot.resolve("iris");
            if (component.equals("iris")) {
                Files.createSymbolicLink(iris, external);
            } else {
                Files.createDirectory(iris);
                Path generation = iris.resolve("generation");
                if (component.equals("generation")) {
                    Files.createSymbolicLink(generation, external);
                } else {
                    Files.createDirectory(generation);
                    Files.createSymbolicLink(generation.resolve("boundaries"), external);
                }
            }
        }
        TerrainBoundarySignatureStore store = new TerrainBoundarySignatureStore(dimensionRoot);

        assertThrows(IOException.class, () -> store.publish(1L, List.of()));
    }

    private static boolean publishAfterBarrier(
            TerrainBoundarySignatureStore store,
            long activationId,
            TerrainBoundarySignature signature,
            CyclicBarrier barrier
    ) throws Exception {
        barrier.await();
        try {
            store.publish(activationId, List.of(signature));
            return true;
        } catch (IOException expected) {
            return false;
        }
    }

    private static TerrainBoundarySignature simpleSignature(int blockX, int blockZ, int surfaceHeight) {
        return signature(
                blockX,
                blockZ,
                surfaceHeight,
                surfaceHeight - 20,
                OptionalInt.of(surfaceHeight - 10),
                -64,
                32,
                List.of("iris:plains"),
                new short[]{0, 0}
        );
    }

    private static TerrainBoundarySignature signature(
            int blockX,
            int blockZ,
            int surfaceHeight,
            int oceanFloorHeight,
            OptionalInt fluidHeight,
            int minimumY,
            int sampleStep,
            List<String> palette,
            short[] paletteIndices
    ) {
        TerrainBoundarySignature.VerticalLayout layout = new TerrainBoundarySignature.VerticalLayout(
                minimumY,
                sampleStep,
                paletteIndices.length
        );
        TerrainBoundarySignature.BiomeEncoding biomeEncoding =
                new TerrainBoundarySignature.BiomeEncoding(palette, paletteIndices);
        return new TerrainBoundarySignature(
                new TerrainBoundarySignature.Column(
                        blockX,
                        blockZ,
                        surfaceHeight,
                        oceanFloorHeight,
                        fluidHeight,
                        OptionalInt.empty()
                ),
                new TerrainBoundarySignature.Samples(layout, biomeEncoding)
        , BoundaryColumnGeometry.empty());
    }

    private static void assertSignatureEquals(
            TerrainBoundarySignature expected,
            TerrainBoundarySignature actual
    ) {
        assertEquals(expected.column(), actual.column());
        assertEquals(expected.samples().layout(), actual.samples().layout());
        for (int index = 0; index < expected.sampleCount(); index++) {
            assertEquals(expected.biomeAtSample(index), actual.biomeAtSample(index));
        }
    }

    private static void updateChecksum(byte[] encoded) {
        int bodyLength = encoded.length - Integer.BYTES;
        CRC32 checksum = new CRC32();
        checksum.update(encoded, 0, bodyLength);
        ByteBuffer.wrap(encoded, bodyLength, Integer.BYTES).putInt((int) checksum.getValue());
    }
}
