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
import java.nio.file.Files;
import java.nio.file.Path;
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
