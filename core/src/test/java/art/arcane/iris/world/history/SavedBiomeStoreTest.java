package art.arcane.iris.world.history;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SavedBiomeStoreTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void preservesEveryColumnHeightRegionAndOwningActivationAcrossReload() throws Exception {
        Path root = temporaryFolder.newFolder().toPath();
        SavedBiomeChunk original = chunk(-33, 31, 8L);
        SavedBiomeStore writer = SavedBiomeStore.open(root);
        assertTrue(writer.claimAndPersist(original));
        assertFalse(writer.claimAndPersist(original));
        SavedBiomeChunk loaded = SavedBiomeStore.open(root).get(-33, 31).orElseThrow();
        assertEquals(original, loaded);
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                assertEquals(original.surfaceAt(x, z), loaded.surfaceAt(x, z));
                assertEquals(original.caveBaseAt(x, z), loaded.caveBaseAt(x, z));
                for (int y = -64; y < 320; y++) {
                    assertEquals(original.biomeAt(x, y, z), loaded.biomeAt(x, y, z));
                }
            }
        }
        assertEquals(7L, loaded.biomeAt(0, -64, 0).activationId());
        assertEquals("cave/base-0", loaded.caveBaseAt(0, 0).biomeKey());
        assertEquals(8L, loaded.caveBaseAt(0, 0).activationId());
        assertFalse(loaded.caveBaseAt(0, 0).equals(loaded.biomeAt(0, -64, 0)));
        assertEquals("region/0", loaded.surfaceAt(0, 0).regionKey());
        assertEquals("region/1", loaded.surfaceAt(0, 1).regionKey());
        assertThrows(IndexOutOfBoundsException.class, () -> loaded.biomeAt(0, -65, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> loaded.biomeAt(0, 320, 0));
    }

    @Test
    public void preservesUnresolvedHistoricalCellsAlongsideKnownBiomeAssignments() throws Exception {
        Path root = temporaryFolder.newFolder().toPath();
        SavedBiomeChunk.Cell unresolved = SavedBiomeChunk.Cell.unresolved(1L);
        SavedBiomeChunk.Cell resolved = new SavedBiomeChunk.Cell(2L, "biome/current", "region/current");
        SavedBiomeChunk.Builder builder = SavedBiomeChunk.builder(new SavedBiomeChunk.Header(-1, -1, 2L, -64, 384));
        List<SavedBiomeChunk.Span> spans = List.of(new SavedBiomeChunk.Span(-64, 16, unresolved),
                new SavedBiomeChunk.Span(16, 320, resolved));
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                builder.column(x, z, new SavedBiomeChunk.Column(x < 8 ? unresolved : resolved, unresolved, spans));
            }
        }
        SavedBiomeChunk original = builder.build();
        SavedBiomeStore.open(root).claimAndPersist(original);
        SavedBiomeChunk loaded = SavedBiomeStore.open(root).get(-1, -1).orElseThrow();
        assertEquals(original, loaded);
        assertFalse(loaded.surfaceAt(0, 0).isResolved());
        assertEquals(unresolved, loaded.caveBaseAt(15, 15));
        assertEquals(1L, loaded.surfaceAt(0, 0).activationId());
        assertEquals(SavedBiomeChunk.Resolution.UNRESOLVED, loaded.biomeAt(15, -64, 15).resolution());
        assertEquals(null, loaded.biomeAt(15, -64, 15).biomeKey());
        assertEquals(null, loaded.biomeAt(15, -64, 15).regionKey());
        assertTrue(loaded.surfaceAt(8, 0).isResolved());
        assertEquals(resolved, loaded.biomeAt(0, 16, 0));
        assertThrows(IllegalArgumentException.class, () -> SavedBiomeChunk.Cell.unresolved(0L));
        assertThrows(IllegalArgumentException.class, () -> new SavedBiomeChunk.Cell(1L, "biome/known", null));
        assertThrows(IllegalArgumentException.class, () -> new SavedBiomeChunk.Cell(1L, null, "region/known"));
        assertThrows(IllegalArgumentException.class, () -> new SavedBiomeChunk.Cell(1L, "biome/known", null,
                SavedBiomeChunk.Resolution.UNRESOLVED));
        assertThrows(IllegalArgumentException.class, () -> new SavedBiomeChunk.Cell(1L, null, "region/known",
                SavedBiomeChunk.Resolution.UNRESOLVED));
        assertThrows(IllegalArgumentException.class, () -> new SavedBiomeChunk.Cell(1L, null, null,
                SavedBiomeChunk.Resolution.RESOLVED));
    }

    @Test
    public void rejectsConflictingClaimsWithoutReplacingSavedContent() throws Exception {
        Path root = temporaryFolder.newFolder().toPath();
        SavedBiomeStore store = SavedBiomeStore.open(root);
        SavedBiomeChunk original = chunk(1, 2, 3L);
        store.claimAndPersist(original);
        byte[] bytes = Files.readAllBytes(regionPath(root, 0, 0));
        assertThrows(IOException.class, () -> store.claimAndPersist(chunk(1, 2, 4L)));
        assertTrue(Arrays.equals(bytes, Files.readAllBytes(regionPath(root, 0, 0))));
        assertEquals(Optional.of(original), SavedBiomeStore.open(root).get(1, 2));
    }

    @Test
    public void missingSnapshotsLeaveExistingHistoryUntouchedAndCachedReadsAvoidDisk() throws Exception {
        Path root = temporaryFolder.newFolder().toPath();
        Files.createDirectories(root.resolve("iris/generation/semantics"));
        Path semantics = root.resolve("iris/generation/semantics/existing.isem");
        Files.writeString(semantics, "unchanged format six data");
        SavedBiomeStore store = SavedBiomeStore.open(root);
        assertTrue(store.get(0, 0).isEmpty());
        assertFalse(Files.exists(store.storageDirectory()));
        SavedBiomeChunk chunk = chunk(0, 0, 3L);
        store.claimAndPersist(chunk);
        Files.delete(regionPath(root, 0, 0));
        assertEquals(Optional.of(chunk), store.get(0, 0));
        assertEquals(Optional.of(chunk), store.cached(0, 0));
        assertEquals("unchanged format six data", Files.readString(semantics));
    }

    @Test
    public void prunesOnlyUnstoredSelectedClaimsAndAllowsReplacementAfterCacheInvalidation() throws Exception {
        Path root = temporaryFolder.newFolder().toPath();
        SavedBiomeStore store = SavedBiomeStore.open(root);
        SavedBiomeChunk stored = chunk(0, 0, 3L);
        SavedBiomeChunk orphan = chunk(1, 0, 3L);
        SavedBiomeChunk previous = chunk(2, 0, 2L);
        SavedBiomeChunk unrelatedRegion = chunk(32, 0, 2L);
        store.claimAndPersist(stored);
        store.claimAndPersist(orphan);
        store.claimAndPersist(previous);
        store.claimAndPersist(unrelatedRegion);
        byte[] untouched = Files.readAllBytes(regionPath(root, 1, 0));
        WorldChunkInventory inventory = WorldChunkInventory.ofPackedChunks(ChunkGenerationOwnership.packChunk(0, 0));

        assertEquals(1, store.discardUnstoredClaims(inventory, Set.of(3L)));

        assertTrue(store.cached(1, 0).isEmpty());
        assertTrue(store.get(1, 0).isEmpty());
        assertEquals(Optional.of(stored), store.get(0, 0));
        assertEquals(Optional.of(previous), store.get(2, 0));
        assertEquals(Optional.of(unrelatedRegion), store.get(32, 0));
        assertTrue(Arrays.equals(untouched, Files.readAllBytes(regionPath(root, 1, 0))));
        SavedBiomeChunk replacement = chunk(1, 0, 4L);
        assertTrue(store.claimAndPersist(replacement));
        assertEquals(0, store.discardUnstoredClaims(inventory, Set.of(3L)));
        SavedBiomeStore loaded = SavedBiomeStore.open(root);
        assertEquals(Optional.of(replacement), loaded.get(1, 0));
        assertEquals(Optional.of(stored), loaded.get(0, 0));
        assertEquals(Optional.of(previous), loaded.get(2, 0));
        assertEquals(1, loaded.discardUnstoredClaims(WorldChunkInventory.empty(), Set.of(4L)));
        assertTrue(SavedBiomeStore.open(root).get(1, 0).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> loaded.discardUnstoredClaims(inventory, Set.of(0L)));
    }

    @Test
    public void tornLastAppendTruncatesOnlyIncompleteClaimAndAllowsNewAppend() throws Exception {
        Path root = temporaryFolder.newFolder().toPath();
        SavedBiomeStore store = SavedBiomeStore.open(root);
        SavedBiomeChunk first = chunk(0, 0, 3L);
        store.claimAndPersist(first);
        Path path = regionPath(root, 0, 0);
        long firstLength = Files.size(path);
        store.claimAndPersist(chunk(1, 0, 3L));
        byte[] bytes = Files.readAllBytes(path);
        Files.write(path, Arrays.copyOf(bytes, bytes.length - 3));
        SavedBiomeStore loaded = SavedBiomeStore.open(root);
        assertEquals(Optional.of(first), loaded.get(0, 0));
        assertEquals(firstLength, Files.size(path));
        assertTrue(loaded.get(1, 0).isEmpty());
        assertTrue(loaded.claimAndPersist(chunk(2, 0, 3L)));
        assertTrue(SavedBiomeStore.open(root).get(2, 0).isPresent());
    }

    @Test
    public void rejectsChecksumCorruptionAndDuplicateCompleteRecords() throws Exception {
        Path root = temporaryFolder.newFolder().toPath();
        SavedBiomeStore store = SavedBiomeStore.open(root);
        store.claimAndPersist(chunk(0, 0, 3L));
        Path path = regionPath(root, 0, 0);
        byte[] original = Files.readAllBytes(path);
        byte[] corrupt = original.clone();
        corrupt[corrupt.length - 1] ^= 1;
        Files.write(path, corrupt);
        assertThrows(IOException.class, () -> SavedBiomeStore.open(root).get(0, 0));
        Files.write(path, original);
        Files.write(path, Arrays.copyOfRange(original, 20, original.length), StandardOpenOption.APPEND);
        assertThrows(IOException.class, () -> SavedBiomeStore.open(root).get(0, 0));
    }

    @Test
    public void rejectsUnknownFormatEvenWithValidHeaderChecksum() throws Exception {
        Path root = temporaryFolder.newFolder().toPath();
        SavedBiomeStore.open(root).claimAndPersist(chunk(0, 0, 3L));
        Path path = regionPath(root, 0, 0);
        byte[] bytes = Files.readAllBytes(path);
        ByteBuffer.wrap(bytes).putInt(4, 2);
        CRC32 crc = new CRC32();
        crc.update(bytes, 0, 16);
        ByteBuffer.wrap(bytes).putInt(16, (int) crc.getValue());
        Files.write(path, bytes);
        assertThrows(IOException.class, () -> SavedBiomeStore.open(root).get(0, 0));
    }

    @Test
    public void rejectsOversizedDecodedHeadersPalettesRunsAndStringsBeforeAllocation() throws Exception {
        Path root = temporaryFolder.newFolder().toPath();
        SavedBiomeStore.open(root).claimAndPersist(chunk(0, 0, 3L));
        Path path = regionPath(root, 0, 0);
        rewritePayload(path, new byte[24], Integer.MAX_VALUE);
        assertThrows(IOException.class, () -> SavedBiomeStore.open(root).get(0, 0));
        for (int mode = 0; mode < 3; mode++) {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(raw)) {
                output.writeLong(3L);
                output.writeInt(-64);
                output.writeInt(384);
                output.writeInt(mode == 0 ? Integer.MAX_VALUE : 1);
                if (mode == 0) {
                    output.writeInt(0);
                } else {
                    output.writeLong(3L);
                    output.writeByte(mode == 1 ? 1 : 0);
                    if (mode == 1) {
                        output.writeInt(1);
                        output.writeByte(0);
                        output.writeByte(0);
                        output.write(new byte[]{(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x07});
                    } else {
                        output.writeShort(65_535);
                    }
                }
            }
            rewritePayload(path, raw.toByteArray(), raw.size());
            assertThrows(IOException.class, () -> SavedBiomeStore.open(root).get(0, 0));
        }
    }

    @Test
    public void modelRejectsDenseColumnsBeyondItsDecodedFootprintLimit() {
        SavedBiomeChunk.Cell first = new SavedBiomeChunk.Cell(3L, "biome/first", "region/main");
        SavedBiomeChunk.Cell second = new SavedBiomeChunk.Cell(3L, "biome/second", "region/main");
        ArrayList<SavedBiomeChunk.Span> spans = new ArrayList<>(4096);
        for (int y = 0; y < 4096; y++) {
            spans.add(new SavedBiomeChunk.Span(y, y + 1, (y & 1) == 0 ? first : second));
        }
        SavedBiomeChunk.Column column = new SavedBiomeChunk.Column(first, second, spans);
        SavedBiomeChunk.Builder builder = SavedBiomeChunk.builder(new SavedBiomeChunk.Header(0, 0, 3L, 0, 4096));
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                builder.column(x, z, column);
            }
        }
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, builder::build);
        assertTrue(failure.getMessage().contains("footprint limit"));
    }

    @Test
    public void boundedCachesReloadEvictedRegionsAndChunks() throws Exception {
        Path root = temporaryFolder.newFolder().toPath();
        SavedBiomeStore store = SavedBiomeStore.open(root);
        for (int index = 0; index < 140; index++) {
            store.claimAndPersist(chunk(index * 32, -32, 3L));
            assertTrue(store.cachedChunkCount() <= 128);
            assertTrue(store.cachedRegionCount() <= 64);
            assertTrue(store.cachedByteCount() <= 64L * 1024L * 1024L);
        }
        assertTrue(store.cached(0, -32).isEmpty());
        assertEquals(Optional.of(chunk(0, -32, 3L)), store.get(0, -32));
        assertEquals(Optional.of(chunk(139 * 32, -32, 3L)), SavedBiomeStore.open(root).get(139 * 32, -32));
    }

    @Test
    public void compressesRepeatedVerticalColumnsAndKeepsDistinctExactSurfaceColumns() throws Exception {
        Path root = temporaryFolder.newFolder().toPath();
        SavedBiomeChunk.Header header = new SavedBiomeChunk.Header(0, 0, 5L, -64, 384);
        SavedBiomeChunk.Cell cave = new SavedBiomeChunk.Cell(5L, "biome/cave", "region/main");
        List<SavedBiomeChunk.Span> vertical = List.of(new SavedBiomeChunk.Span(-64, 320, cave));
        SavedBiomeChunk.Builder builder = SavedBiomeChunk.builder(header);
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                SavedBiomeChunk.Cell surface = new SavedBiomeChunk.Cell(5L, "biome/surface-" + x, "region/" + z);
                builder.column(x, z, new SavedBiomeChunk.Column(surface, cave, vertical));
            }
        }
        SavedBiomeChunk original = builder.build();
        SavedBiomeStore.open(root).claimAndPersist(original);
        assertTrue(Files.size(regionPath(root, 0, 0)) < 4096);
        assertEquals(Optional.of(original), SavedBiomeStore.open(root).get(0, 0));
    }

    @Test
    public void concurrentlyPublishesSeparateRegionsAndSingleImmutableClaims() throws Exception {
        Path root = temporaryFolder.newFolder().toPath();
        SavedBiomeStore store = SavedBiomeStore.open(root);
        try (ExecutorService workers = Executors.newFixedThreadPool(4)) {
            ArrayList<Future<Boolean>> futures = new ArrayList<>();
            for (int index = 0; index < 12; index++) {
                int chunkX = (index % 6) * 32;
                futures.add(workers.submit(() -> store.claimAndPersist(chunk(chunkX, 0, 3L))));
            }
            int added = 0;
            for (Future<Boolean> future : futures) {
                if (future.get()) {
                    added++;
                }
            }
            assertEquals(6, added);
        }
        SavedBiomeStore loaded = SavedBiomeStore.open(root);
        for (int index = 0; index < 6; index++) {
            assertEquals(Optional.of(chunk(index * 32, 0, 3L)), loaded.get(index * 32, 0));
        }
    }

    @Test
    public void modelCopiesInputsCompactsRunsAndRejectsGapsMissingColumnsAndInvalidOwners() {
        SavedBiomeChunk.Cell cell = new SavedBiomeChunk.Cell(3L, "biome/main", "region/main");
        ArrayList<SavedBiomeChunk.Span> spans = new ArrayList<>(List.of(
                new SavedBiomeChunk.Span(-64, 0, cell), new SavedBiomeChunk.Span(0, 320, cell)));
        SavedBiomeChunk.Column column = new SavedBiomeChunk.Column(cell, cell, spans);
        spans.clear();
        assertEquals(List.of(new SavedBiomeChunk.Span(-64, 320, cell)), column.vertical());
        assertThrows(UnsupportedOperationException.class, () -> column.vertical().clear());
        SavedBiomeChunk.Builder builder = SavedBiomeChunk.builder(new SavedBiomeChunk.Header(0, 0, 3L, -64, 384));
        assertThrows(NullPointerException.class, builder::build);
        assertThrows(IllegalArgumentException.class, () -> new SavedBiomeChunk.Cell(0L, "biome/main", "region/main"));
        assertThrows(IllegalArgumentException.class, () -> new SavedBiomeChunk.Header(0, 0, 3L, Integer.MAX_VALUE, 1));
        assertThrows(IllegalArgumentException.class, () -> new SavedBiomeChunk.Column(cell, cell, List.of(
                new SavedBiomeChunk.Span(-64, 0, cell), new SavedBiomeChunk.Span(1, 320, cell))));
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                builder.column(x, z, column);
            }
        }
        SavedBiomeChunk saved = builder.build();
        SavedBiomeChunk.Column shorter = new SavedBiomeChunk.Column(cell, cell, List.of(new SavedBiomeChunk.Span(-64, 319, cell)));
        builder.column(0, 0, shorter);
        assertEquals(column, saved.column(0, 0));
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    private static void rewritePayload(Path path, byte[] raw, int declaredSize) throws IOException {
        byte[] header = Arrays.copyOf(Files.readAllBytes(path), 20);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        DataOutputStream coordinates = new DataOutputStream(body);
        coordinates.writeInt(0);
        coordinates.writeInt(0);
        coordinates.writeInt(declaredSize);
        try (DeflaterOutputStream compressed = new DeflaterOutputStream(coordinates)) {
            compressed.write(raw);
        }
        byte[] encoded = body.toByteArray();
        CRC32 crc = new CRC32();
        crc.update(encoded);
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(file)) {
            output.write(header);
            output.writeInt(encoded.length);
            output.write(encoded);
            output.writeInt((int) crc.getValue());
        }
        Files.write(path, file.toByteArray());
    }

    private static Path regionPath(Path root, int regionX, int regionZ) {
        return root.resolve("iris/generation/biomes/r." + regionX + "." + regionZ + ".ibio");
    }

    private static SavedBiomeChunk chunk(int chunkX, int chunkZ, long activation) {
        SavedBiomeChunk.Builder builder = SavedBiomeChunk.builder(new SavedBiomeChunk.Header(chunkX, chunkZ, activation, -64, 384));
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                SavedBiomeChunk.Cell cave = new SavedBiomeChunk.Cell(activation - 1L, "cave/" + (x / 4), "region/" + (z / 4));
                SavedBiomeChunk.Cell surface = new SavedBiomeChunk.Cell(activation, "surface/" + x, "region/" + z);
                builder.column(x, z, new SavedBiomeChunk.Column(surface,
                        new SavedBiomeChunk.Cell(activation, "cave/base-" + z, "region/" + z), List.of(
                        new SavedBiomeChunk.Span(-64, 16 + z, cave), new SavedBiomeChunk.Span(16 + z, 320, surface))));
            }
        }
        return builder.build();
    }
}
