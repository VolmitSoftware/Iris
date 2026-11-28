package art.arcane.iris.world.history;

import art.arcane.volmlib.util.nbt.io.NBTUtil;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import net.jpountz.lz4.LZ4BlockOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class SavedTerrainRecoveryScanTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void bufferedAndDirectScansMatchAcrossCodecsCoordinatesAndExternalChunks() throws Exception {
        Path world = temporaryFolder.newFolder().toPath();
        for (int index = 191; index >= 0; index--) {
            int x = index % 32 - 32;
            int z = index / 32 - 32;
            write(world, new Chunk(x, z, index % 4 + 1, index % 17 == 0,
                    encode(x, z, index % 3 == 0 ? "minecraft:biomes" : "minecraft:noise")));
        }
        write(world, new Chunk(33, 3, 3, false, encode(33, 3, "minecraft:full")));
        WorldChunkInventory inventory = WorldChunkInventory.scan(world);
        WorldChunkInventory expected;
        try (SavedTerrainChunkReader.StatusReader reader = new SavedTerrainChunkReader.StatusReader(world)) {
            expected = inventory.filter((x, z) -> SavedTerrainChunk.hasTerrain(reader.readStatus(x, z)));
        }
        for (int workers : new int[]{1, 2, 4}) {
            for (int regionBytes : new int[]{8192, 4 * 1024 * 1024}) {
                try (SavedTerrainRecoveryScan scan = new SavedTerrainRecoveryScan(world,
                        new SavedTerrainRecoveryScan.Limits(workers, regionBytes))) {
                    WorldChunkInventory actual = scan.filter(inventory, (x, z) -> false);
                    assertEquals(expected.size(), actual.size());
                    inventory.forEach((x, z) -> assertEquals(expected.contains(x, z), actual.contains(x, z)));
                }
            }
        }
    }

    @Test
    public void doesNotDecodeRetainedHistoricalChunks() throws Exception {
        Path world = temporaryFolder.newFolder().toPath();
        write(world, new Chunk(0, 0, 2, false, new byte[]{10}));
        write(world, new Chunk(1, 0, 2, false, encode(1, 0, "minecraft:full")));
        try (SavedTerrainRecoveryScan scan = new SavedTerrainRecoveryScan(world,
                new SavedTerrainRecoveryScan.Limits(2, 1024 * 1024))) {
            WorldChunkInventory actual = scan.filter(WorldChunkInventory.scan(world), (x, z) -> x == 0);
            assertEquals(2, actual.size());
        }
    }

    @Test
    public void propagatesWorkerCorruptionAndCanScanAgain() throws Exception {
        Path world = temporaryFolder.newFolder().toPath();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                write(world, new Chunk(x, z, 2, false, encode(x, z, "minecraft:full")));
            }
        }
        write(world, new Chunk(1, 1, 2, false, new byte[]{10}));
        try (SavedTerrainRecoveryScan scan = new SavedTerrainRecoveryScan(world,
                new SavedTerrainRecoveryScan.Limits(2, 4 * 1024 * 1024))) {
            IOException failure = assertThrows(IOException.class,
                    () -> scan.filter(WorldChunkInventory.scan(world), (x, z) -> false));
            assertTrue(failure.getMessage().contains("1,1"));
            write(world, new Chunk(1, 1, 2, false, encode(1, 1, "minecraft:full")));
            assertEquals(256, scan.filter(WorldChunkInventory.scan(world), (x, z) -> false).size());
        }
        assertEquals(256, SavedTerrainRecoveryScan.scan(world, WorldChunkInventory.scan(world), (x, z) -> false).size());
    }

    @Test
    public void retainsInterruptStatusWhenRecoveryIsInterrupted() throws Exception {
        Path world = temporaryFolder.newFolder().toPath();
        write(world, new Chunk(0, 0, 2, false, encode(0, 0, "minecraft:full")));
        WorldChunkInventory inventory = WorldChunkInventory.scan(world);
        try (SavedTerrainRecoveryScan scan = new SavedTerrainRecoveryScan(world,
                new SavedTerrainRecoveryScan.Limits(1, 1024 * 1024))) {
            Thread.currentThread().interrupt();
            try {
                assertThrows(IOException.class, () -> scan.filter(inventory, (x, z) -> false));
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
        }
    }

    @Test
    public void capsWorkersAndRegionMemoryForTheAvailableResources() {
        assertEquals(1, SavedTerrainRecoveryScan.Limits.forRuntime(1, 8L << 30).workers());
        assertEquals(1, SavedTerrainRecoveryScan.Limits.forRuntime(64, 512L << 20).workers());
        assertEquals(2, SavedTerrainRecoveryScan.Limits.forRuntime(64, 2L << 30).workers());
        assertEquals(4, SavedTerrainRecoveryScan.Limits.forRuntime(64, 8L << 30).workers());
        assertEquals(32 << 20, SavedTerrainRecoveryScan.Limits.forRuntime(64, 512L << 20).regionBytes());
        assertEquals(64 << 20, SavedTerrainRecoveryScan.Limits.forRuntime(64, 8L << 30).regionBytes());
    }

    @Test
    public void emptyInventoriesDoNotNeedARegionDirectory() throws Exception {
        Path world = temporaryFolder.newFolder().toPath();
        assertTrue(SavedTerrainRecoveryScan.scan(world, WorldChunkInventory.empty(), (x, z) -> false).isEmpty());
        assertFalse(Files.exists(world.resolve("region")));
    }

    @Test
    public void warmCacheReusesStatusWithoutDecodingUnchangedChunks() throws Exception {
        for (int regionBytes : new int[]{8192, 4 * 1024 * 1024}) {
            Path world = temporaryFolder.newFolder().toPath();
            Path cacheFile = world.resolve("iris").resolve(SavedTerrainStatusCache.FILE_NAME);
            writeChunkGrid(world);
            WorldChunkInventory inventory = WorldChunkInventory.scan(world);
            WorldChunkInventory expected = uncachedScan(world, inventory);
            assertSameChunks(inventory, expected, cachedScan(world, cacheFile, regionBytes, (x, z) -> false));
            assertTrue(Files.isRegularFile(cacheFile));
            corruptAll(world);
            assertThrows(IOException.class, () -> uncachedScan(world, inventory));
            assertSameChunks(inventory, expected, cachedScan(world, cacheFile, regionBytes, (x, z) -> false));
            assertSameChunks(inventory, expected, cachedScan(world, cacheFile, regionBytes, (x, z) -> false));
        }
    }

    @Test
    public void ownershipIsAppliedOnEveryScanInsteadOfCached() throws Exception {
        Path world = temporaryFolder.newFolder().toPath();
        Path cacheFile = world.resolve("iris").resolve(SavedTerrainStatusCache.FILE_NAME);
        write(world, new Chunk(0, 0, 2, false, encode(0, 0, "minecraft:biomes")), OLD);
        write(world, new Chunk(1, 0, 2, false, encode(1, 0, "minecraft:full")), OLD);
        WorldChunkInventory inventory = WorldChunkInventory.scan(world);
        WorldChunkInventory first = cachedScan(world, cacheFile, 4 * 1024 * 1024, (x, z) -> x == 0);
        assertEquals(2, first.size());
        WorldChunkInventory second = cachedScan(world, cacheFile, 4 * 1024 * 1024, (x, z) -> false);
        assertEquals(1, second.size());
        assertTrue(second.contains(1, 0));
        corruptAll(world);
        assertEquals(2, cachedScan(world, cacheFile, 4 * 1024 * 1024, (x, z) -> x == 0).size());
        WorldChunkInventory third = cachedScan(world, cacheFile, 4 * 1024 * 1024, (x, z) -> false);
        assertEquals(1, third.size());
        assertTrue(third.contains(1, 0));
        assertSameChunks(inventory, second, third);
    }

    @Test
    public void changedHeaderWordsAreDecodedAgain() throws Exception {
        Path world = temporaryFolder.newFolder().toPath();
        Path cacheFile = world.resolve("iris").resolve(SavedTerrainStatusCache.FILE_NAME);
        writeChunkGrid(world);
        cachedScan(world, cacheFile, 4 * 1024 * 1024, (x, z) -> false);
        write(world, new Chunk(2, 0, 2, false, encode(2, 0, "minecraft:biomes")), OLD);
        WorldChunkInventory inventory = WorldChunkInventory.scan(world);
        WorldChunkInventory rewritten = cachedScan(world, cacheFile, 4 * 1024 * 1024, (x, z) -> false);
        assertSameChunks(inventory, uncachedScan(world, inventory), rewritten);
        assertFalse(rewritten.contains(2, 0));

        cachedScan(world, cacheFile, 4 * 1024 * 1024, (x, z) -> false);
        setTimestamp(world, 3, 0, OLD + 1);
        corrupt(world, 3, 0);
        IOException failure = assertThrows(IOException.class,
                () -> cachedScan(world, cacheFile, 4 * 1024 * 1024, (x, z) -> false));
        assertTrue(failure.getMessage().contains("3,0"));
    }

    @Test
    public void chunksSavedNearTheScanTimeAreDecodedAgain() throws Exception {
        Path world = temporaryFolder.newFolder().toPath();
        Path cacheFile = world.resolve("iris").resolve(SavedTerrainStatusCache.FILE_NAME);
        writeChunkGrid(world);
        long now = System.currentTimeMillis() / 1000L;
        setTimestamp(world, 4, 1, now);
        setTimestamp(world, 5, 1, now + 30);
        cachedScan(world, cacheFile, 4 * 1024 * 1024, (x, z) -> false);
        corrupt(world, 4, 1);
        assertTrue(assertThrows(IOException.class,
                () -> cachedScan(world, cacheFile, 4 * 1024 * 1024, (x, z) -> false)).getMessage().contains("4,1"));
        write(world, new Chunk(4, 1, 2, false, encode(4, 1, "minecraft:full")), OLD);
        cachedScan(world, cacheFile, 4 * 1024 * 1024, (x, z) -> false);
        corrupt(world, 5, 1);
        assertTrue(assertThrows(IOException.class,
                () -> cachedScan(world, cacheFile, 4 * 1024 * 1024, (x, z) -> false)).getMessage().contains("5,1"));
    }

    @Test
    public void externalPayloadsAreDecodedOnEveryScan() throws Exception {
        Path world = temporaryFolder.newFolder().toPath();
        Path cacheFile = world.resolve("iris").resolve(SavedTerrainStatusCache.FILE_NAME);
        writeChunkGrid(world);
        write(world, new Chunk(6, 2, 2, false, encode(6, 2, "minecraft:full")), OLD);
        write(world, new Chunk(7, 2, 2, true, encode(7, 2, "minecraft:full")), OLD);
        for (int regionBytes : new int[]{8192, 4 * 1024 * 1024}) {
            cachedScan(world, cacheFile, regionBytes, (x, z) -> false);
            Path external = world.resolve("region").resolve("c.7.2.mcc");
            byte[] payload = Files.readAllBytes(external);
            Files.write(external, new byte[]{1, 2, 3});
            assertTrue(assertThrows(IOException.class,
                    () -> cachedScan(world, cacheFile, regionBytes, (x, z) -> false)).getMessage().contains("7,2"));
            Files.write(external, payload);
        }
    }

    @Test
    public void unreadableCacheFallsBackToAFullScan() throws Exception {
        Path world = temporaryFolder.newFolder().toPath();
        Path cacheFile = world.resolve("iris").resolve(SavedTerrainStatusCache.FILE_NAME);
        writeChunkGrid(world);
        WorldChunkInventory inventory = WorldChunkInventory.scan(world);
        WorldChunkInventory expected = uncachedScan(world, inventory);
        cachedScan(world, cacheFile, 4 * 1024 * 1024, (x, z) -> false);
        byte[] valid = Files.readAllBytes(cacheFile);

        byte[] flipped = valid.clone();
        flipped[flipped.length / 2] ^= 1;
        byte[] wrongVersion = valid.clone();
        wrongVersion[7] = 99;
        CRC32 checksum = new CRC32();
        checksum.update(wrongVersion, 0, wrongVersion.length - Long.BYTES);
        ByteBuffer.wrap(wrongVersion, wrongVersion.length - Long.BYTES, Long.BYTES).putLong(checksum.getValue());
        for (byte[] broken : new byte[][]{flipped, wrongVersion, new byte[]{1, 2, 3}, new byte[0]}) {
            Files.write(cacheFile, broken);
            assertSameChunks(inventory, expected, cachedScan(world, cacheFile, 4 * 1024 * 1024, (x, z) -> false));
        }

        Files.write(cacheFile, flipped);
        corrupt(world, 1, 1);
        assertThrows(IOException.class, () -> cachedScan(world, cacheFile, 4 * 1024 * 1024, (x, z) -> false));
    }

    @Test
    public void staticStartupScanWritesTheCacheOnlyAfterSuccess() throws Exception {
        Path world = temporaryFolder.newFolder().toPath();
        Path cacheFile = world.resolve("iris").resolve(SavedTerrainStatusCache.FILE_NAME);
        writeChunkGrid(world);
        corrupt(world, 1, 1);
        assertThrows(IOException.class, () -> SavedTerrainRecoveryScan.scan(world, WorldChunkInventory.scan(world),
                (x, z) -> false, cacheFile));
        assertFalse(Files.exists(cacheFile));
        write(world, new Chunk(1, 1, 2, false, encode(1, 1, "minecraft:full")), OLD);
        WorldChunkInventory inventory = WorldChunkInventory.scan(world);
        WorldChunkInventory expected = uncachedScan(world, inventory);
        assertSameChunks(inventory, expected, SavedTerrainRecoveryScan.scan(world, inventory, (x, z) -> false, cacheFile));
        corruptAll(world);
        assertSameChunks(inventory, expected, SavedTerrainRecoveryScan.scan(world, inventory, (x, z) -> false, cacheFile));
    }

    private static final long OLD = 1_700_000_000L;

    private static void writeChunkGrid(Path world) throws IOException {
        for (int x = 0; x < 12; x++) {
            for (int z = 0; z < 12; z++) {
                write(world, new Chunk(x, z, 2, false,
                        encode(x, z, (x + z) % 3 == 0 ? "minecraft:biomes" : "minecraft:full")), OLD);
            }
        }
        write(world, new Chunk(40, 3, 2, false, encode(40, 3, "minecraft:noise")), OLD);
    }

    private static WorldChunkInventory uncachedScan(Path world, WorldChunkInventory inventory) throws IOException {
        try (SavedTerrainChunkReader.StatusReader reader = new SavedTerrainChunkReader.StatusReader(world)) {
            return inventory.filter((x, z) -> SavedTerrainChunk.hasTerrain(reader.readStatus(x, z)));
        }
    }

    private static WorldChunkInventory cachedScan(Path world, Path cacheFile, int regionBytes,
            WorldChunkInventory.ChunkPredicate retained) throws IOException {
        SavedTerrainStatusCache cache = SavedTerrainStatusCache.load(cacheFile, world);
        WorldChunkInventory result;
        try (SavedTerrainRecoveryScan scan = new SavedTerrainRecoveryScan(world,
                new SavedTerrainRecoveryScan.Limits(2, regionBytes), cache)) {
            result = scan.filter(WorldChunkInventory.scan(world), retained);
        }
        cache.save();
        return result;
    }

    private static void assertSameChunks(WorldChunkInventory inventory, WorldChunkInventory expected,
            WorldChunkInventory actual) throws IOException {
        assertEquals(expected.size(), actual.size());
        inventory.forEach((x, z) -> assertEquals(expected.contains(x, z), actual.contains(x, z)));
    }

    private static void corruptAll(Path world) throws IOException {
        WorldChunkInventory.scan(world).forEach((x, z) -> corrupt(world, x, z));
    }

    private static void corrupt(Path world, int x, int z) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(regionFile(world, x, z).toFile(), "rw")) {
            file.seek(slot(x, z) * 4L);
            int sector = file.readInt() >>> 8;
            file.seek(sector * 4096L + 4);
            int compression = file.readUnsignedByte();
            file.seek(sector * 4096L + 4);
            file.writeByte((compression & 128) | 99);
        }
    }

    private static void setTimestamp(Path world, int x, int z, long seconds) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(regionFile(world, x, z).toFile(), "rw")) {
            file.seek(4096L + slot(x, z) * 4L);
            file.writeInt((int) seconds);
        }
    }

    private static Path regionFile(Path world, int x, int z) {
        return world.resolve("region").resolve("r." + Math.floorDiv(x, 32) + "." + Math.floorDiv(z, 32) + ".mca");
    }

    private static int slot(int x, int z) {
        return Math.floorMod(z, 32) * 32 + Math.floorMod(x, 32);
    }

    private static void write(Path world, Chunk chunk, long timestamp) throws IOException {
        write(world, chunk);
        setTimestamp(world, chunk.x(), chunk.z(), timestamp);
    }

    private static byte[] encode(int x, int z, String status) throws IOException {
        CompoundTag root = new CompoundTag();
        root.putInt("xPos", x);
        root.putInt("zPos", z);
        root.putString("Status", status);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NBTUtil.write(root, bytes, false);
        return bytes.toByteArray();
    }

    private static void write(Path world, Chunk chunk) throws IOException {
        Path directory = Files.createDirectories(world.resolve("region"));
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        try (OutputStream stream = switch (chunk.compression()) {
            case 1 -> new GZIPOutputStream(encoded);
            case 2 -> new DeflaterOutputStream(encoded);
            case 4 -> new LZ4BlockOutputStream(encoded);
            default -> encoded;
        }) {
            stream.write(chunk.nbt());
        }
        byte[] payload = encoded.toByteArray();
        Path path = directory.resolve("r." + Math.floorDiv(chunk.x(), 32) + "." + Math.floorDiv(chunk.z(), 32) + ".mca");
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw")) {
            int sector = Math.max(2, Math.toIntExact(Math.ceilDiv(file.length(), 4096)));
            int sectors = chunk.external() ? 1 : Math.ceilDiv(payload.length + 5, 4096);
            file.setLength((long) (sector + sectors) * 4096);
            file.seek((Math.floorMod(chunk.z(), 32) * 32L + Math.floorMod(chunk.x(), 32)) * 4);
            file.writeInt(sector << 8 | sectors);
            file.seek((long) sector * 4096);
            file.writeInt(chunk.external() ? 1 : payload.length + 1);
            file.writeByte(chunk.compression() | (chunk.external() ? 128 : 0));
            if (!chunk.external()) {
                file.write(payload);
            }
        }
        if (chunk.external()) {
            Files.write(directory.resolve("c." + chunk.x() + "." + chunk.z() + ".mcc"), payload);
        }
    }

    private record Chunk(int x, int z, int compression, boolean external, byte[] nbt) {
    }
}
