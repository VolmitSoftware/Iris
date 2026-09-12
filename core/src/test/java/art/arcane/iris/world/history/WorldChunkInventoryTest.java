package art.arcane.iris.world.history;

import art.arcane.iris.testsupport.DurabilityMode;
import art.arcane.iris.world.storage.region.MCAFile;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.zip.DeflaterOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class WorldChunkInventoryTest {
    @ClassRule
    public static final DurabilityMode DURABILITY = DurabilityMode.relaxed();

    private static final int SECTOR_BYTES = 4_096;

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void scanReadsAllocationTablesAcrossNegativeRegionBoundaries() throws Exception {
        Path world = temporaryFolder.newFolder("world").toPath();
        Path regionDirectory = Files.createDirectories(world.resolve("region"));
        writeRegion(regionDirectory.resolve("r.-1.-1.mca"), new int[][]{{0, 0}, {31, 31}});
        writeRegion(regionDirectory.resolve("r.0.0.mca"), new int[][]{{0, 0}, {31, 31}});
        writeRegion(regionDirectory.resolve("r.1.-2.mca"), new int[][]{{0, 31}});

        WorldChunkInventory inventory = WorldChunkInventory.scan(world);

        assertEquals(5, inventory.size());
        assertTrue(inventory.contains(-32, -32));
        assertTrue(inventory.contains(-1, -1));
        assertTrue(inventory.contains(0, 0));
        assertTrue(inventory.contains(31, 31));
        assertTrue(inventory.contains(32, -33));
        assertFalse(inventory.contains(32, -32));
    }

    @Test
    public void scanNeverReadsOrDecompressesAllocatedChunkPayloads() throws Exception {
        Path regionDirectory = temporaryFolder.newFolder("region-payload").toPath();
        Path region = regionDirectory.resolve("r.0.0.mca");
        writeRegion(region, new int[][]{{7, 11}});
        try (RandomAccessFile file = new RandomAccessFile(region.toFile(), "rw")) {
            file.seek(SECTOR_BYTES * 2L);
            file.writeInt(Integer.MAX_VALUE);
            file.writeByte(127);
        }

        WorldChunkInventory inventory = WorldChunkInventory.scanRegionDirectory(regionDirectory);

        assertEquals(1, inventory.size());
        assertTrue(inventory.contains(7, 11));
    }

    @Test
    public void liveRegionMayEndImmediatelyAfterACompressedChunk() throws Exception {
        Path world = temporaryFolder.newFolder("world-unpadded").toPath();
        Path regionDirectory = Files.createDirectory(world.resolve("region"));
        Path region = regionDirectory.resolve("r.0.0.mca");
        byte[] payload = compressedChunk(10_000);
        writeUnpaddedRegion(region, payload, 2);

        assertTrue(Files.size(region) % SECTOR_BYTES != 0L);
        WorldChunkInventory inventory = WorldChunkInventory.scan(world);
        assertEquals(1, inventory.size());
        assertTrue(inventory.contains(7, 11));
        assertTrue(WorldChunkInventory.isDurablyAllocated(world, 7, 11));
        assertFalse(WorldChunkInventory.isDurablyAllocated(world, 8, 11));
    }

    @Test
    public void unpaddedExternalChunkStubStillOwnsItsChunk() throws Exception {
        Path regionDirectory = temporaryFolder.newFolder("region-external").toPath();
        Path region = regionDirectory.resolve("r.0.0.mca");
        writeUnpaddedRegion(region, new byte[0], 0x82);

        WorldChunkInventory inventory = WorldChunkInventory.scanRegionDirectory(regionDirectory);

        assertEquals(1, inventory.size());
        assertTrue(inventory.contains(7, 11));
    }

    @Test
    public void missingPayloadBytesInTheFinalSectorFailClosed() throws Exception {
        Path regionDirectory = temporaryFolder.newFolder("region-partial-payload").toPath();
        Path region = regionDirectory.resolve("r.0.0.mca");
        writeUnpaddedRegion(region, compressedChunk(32), 2);
        try (RandomAccessFile output = new RandomAccessFile(region.toFile(), "rw")) {
            output.setLength(output.length() - 1L);
        }

        IOException error = assertThrows(IOException.class,
                () -> WorldChunkInventory.scanRegionDirectory(regionDirectory));
        assertTrue(error.getMessage().contains("Truncated chunk payload"));
    }

    @Test
    public void invalidFinalSectorPayloadLengthsFailClosed() throws Exception {
        Path regionDirectory = temporaryFolder.newFolder("region-invalid-payload").toPath();
        Path region = regionDirectory.resolve("r.0.0.mca");
        writeUnpaddedRegion(region, compressedChunk(32), 2);
        for (int invalidLength : new int[]{-1, 0, Integer.MAX_VALUE, SECTOR_BYTES}) {
            try (RandomAccessFile output = new RandomAccessFile(region.toFile(), "rw")) {
                output.seek(2L * SECTOR_BYTES);
                output.writeInt(invalidLength);
            }
            assertThrows(IOException.class,
                    () -> WorldChunkInventory.scanRegionDirectory(regionDirectory));
        }
    }

    @Test
    public void incompleteFinalSectorLengthPrefixFailsClosed() throws Exception {
        Path regionDirectory = temporaryFolder.newFolder("region-incomplete-length").toPath();
        Path region = regionDirectory.resolve("r.0.0.mca");
        try (RandomAccessFile output = new RandomAccessFile(region.toFile(), "rw")) {
            output.setLength(SECTOR_BYTES * 2L + Integer.BYTES);
            writeLocation(output, 7, 11, 2, 1);
        }

        assertThrows(IOException.class,
                () -> WorldChunkInventory.scanRegionDirectory(regionDirectory));
    }

    @Test
    public void newlyOpenedEmptyRegionHasNoDurableAllocations() throws Exception {
        Path world = temporaryFolder.newFolder("world-empty-region").toPath();
        Path regionDirectory = Files.createDirectory(world.resolve("region"));
        Files.createFile(regionDirectory.resolve("r.0.0.mca"));

        assertTrue(WorldChunkInventory.scan(world).isEmpty());
        assertFalse(WorldChunkInventory.isDurablyAllocated(world, 0, 0));
    }

    @Test
    public void inventoryRemainsUsableAfterTheRegionFilesDisappear() throws Exception {
        Path regionDirectory = temporaryFolder.newFolder("region-memory").toPath();
        Path region = regionDirectory.resolve("r.0.0.mca");
        writeRegion(region, new int[][]{{1, 2}});
        WorldChunkInventory inventory = WorldChunkInventory.scanRegionDirectory(regionDirectory);
        Files.delete(region);
        Files.delete(regionDirectory);

        assertTrue(inventory.contains(1, 2));
        assertEquals(1, inventory.size());
    }

    @Test
    public void truncatedRegionFileFailsClosed() throws Exception {
        Path regionDirectory = temporaryFolder.newFolder("region-truncated").toPath();
        Path region = regionDirectory.resolve("r.0.0.mca");
        try (RandomAccessFile file = new RandomAccessFile(region.toFile(), "rw")) {
            file.setLength(SECTOR_BYTES);
        }

        IOException error = assertThrows(
                IOException.class,
                () -> WorldChunkInventory.scanRegionDirectory(regionDirectory)
        );

        assertTrue(error.getMessage().contains("Truncated world region file"));
    }

    @Test
    public void allocationOutsideTheFileFailsClosed() throws Exception {
        Path regionDirectory = temporaryFolder.newFolder("region-outside").toPath();
        Path region = regionDirectory.resolve("r.0.0.mca");
        try (RandomAccessFile file = new RandomAccessFile(region.toFile(), "rw")) {
            file.setLength(SECTOR_BYTES * 2L);
            writeLocation(file, 0, 0, 2, 1);
        }

        IOException error = assertThrows(
                IOException.class,
                () -> WorldChunkInventory.scanRegionDirectory(regionDirectory)
        );

        assertTrue(error.getMessage().contains("Invalid chunk allocation"));
    }

    @Test
    public void overlappingAllocationsFailClosed() throws Exception {
        Path regionDirectory = temporaryFolder.newFolder("region-overlap").toPath();
        Path region = regionDirectory.resolve("r.0.0.mca");
        try (RandomAccessFile file = new RandomAccessFile(region.toFile(), "rw")) {
            file.setLength(SECTOR_BYTES * 3L);
            writeLocation(file, 0, 0, 2, 1);
            writeLocation(file, 1, 0, 2, 1);
        }

        IOException error = assertThrows(
                IOException.class,
                () -> WorldChunkInventory.scanRegionDirectory(regionDirectory)
        );

        assertTrue(error.getMessage().contains("Overlapping chunk allocations"));
    }

    @Test
    public void malformedRegionFilenameFailsClosed() throws Exception {
        Path regionDirectory = temporaryFolder.newFolder("region-name").toPath();
        Path region = regionDirectory.resolve("not-a-region.mca");
        try (RandomAccessFile file = new RandomAccessFile(region.toFile(), "rw")) {
            file.setLength(SECTOR_BYTES * 2L);
        }

        IOException error = assertThrows(
                IOException.class,
                () -> WorldChunkInventory.scanRegionDirectory(regionDirectory)
        );

        assertTrue(error.getMessage().contains("Invalid world region filename"));
    }

    @Test
    public void packedInventoryIsDeduplicatedAndDefensivelyCopied() throws Exception {
        long first = ChunkGenerationOwnership.packChunk(-1, 2);
        long second = ChunkGenerationOwnership.packChunk(3, -4);
        long[] packed = new long[]{first, second, first};
        WorldChunkInventory inventory = WorldChunkInventory.ofPackedChunks(packed);
        packed[0] = 0L;
        List<Long> visited = new ArrayList<>();

        inventory.forEach((chunkX, chunkZ) -> visited.add(ChunkGenerationOwnership.packChunk(chunkX, chunkZ)));

        assertEquals(2, inventory.size());
        assertTrue(inventory.contains(-1, 2));
        assertTrue(inventory.contains(3, -4));
        assertEquals(List.of(first, second), visited);
    }

    @Test
    public void largeInventoryRetainsOnlyRegionMasksAndStreamsOwnership() throws Exception {
        Path world = temporaryFolder.newFolder("many-regions").toPath();
        Path regions = Files.createDirectory(world.resolve("region"));
        int[][] chunks = new int[1024][2];
        for (int index = 0; index < chunks.length; index++) {
            chunks[index][0] = index & 31;
            chunks[index][1] = index >> 5;
        }
        for (int regionX = -48; regionX < 48; regionX++) {
            writeRegion(regions.resolve("r." + regionX + ".-2.mca"), chunks);
        }

        WorldChunkInventory inventory = WorldChunkInventory.scan(world);
        ChunkGenerationOwnership ownership = ChunkGenerationOwnership.load(world.resolve("ownership"));

        assertEquals(96 * 1024, inventory.size());
        assertEquals(96 * 128, inventory.retainedAllocationBytes());
        assertEquals(inventory.size(), ownership.assignUnassigned(inventory, 1L));
        assertEquals(0, ownership.assignUnassigned(inventory, 2L));
        inventory.forEach((chunkX, chunkZ) -> assertEquals(1L, ownership.resolve(chunkX, chunkZ, 2L)));
        assertEquals(inventory.size(), ownership.explicitChunkCount());
    }

    @Test
    public void missingRegionDirectoryProducesAnEmptyInventory() throws Exception {
        Path missing = temporaryFolder.getRoot().toPath().resolve("missing");

        WorldChunkInventory inventory = WorldChunkInventory.scanRegionDirectory(missing);

        assertTrue(inventory.isEmpty());
    }

    private static void writeRegion(Path file, int[][] chunks) throws IOException {
        try (RandomAccessFile output = new RandomAccessFile(file.toFile(), "rw")) {
            output.setLength((long) (2 + chunks.length) * SECTOR_BYTES);
            for (int index = 0; index < chunks.length; index++) {
                writeLocation(output, chunks[index][0], chunks[index][1], 2 + index, 1);
            }
        }
    }

    private static void writeUnpaddedRegion(Path path, byte[] payload, int compression) throws IOException {
        try (RandomAccessFile output = new RandomAccessFile(path.toFile(), "rw")) {
            output.setLength(2L * SECTOR_BYTES);
            int sectors = (payload.length + Integer.BYTES + 1 + SECTOR_BYTES - 1) / SECTOR_BYTES;
            output.seek(2L * SECTOR_BYTES);
            output.writeInt(payload.length + 1);
            output.writeByte(compression);
            output.write(payload);
            writeLocation(output, 7, 11, 2, sectors);
        }
    }

    private static byte[] compressedChunk(int dataLength) throws IOException {
        byte[] values = new byte[dataLength];
        new Random(19L).nextBytes(values);
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DataOutputStream nbt = new DataOutputStream(new DeflaterOutputStream(compressed))) {
            nbt.writeByte(10);
            nbt.writeUTF("");
            nbt.writeByte(7);
            nbt.writeUTF("data");
            nbt.writeInt(values.length);
            nbt.write(values);
            nbt.writeByte(0);
        }
        return compressed.toByteArray();
    }

    private static void writeLocation(
            RandomAccessFile file,
            int localChunkX,
            int localChunkZ,
            int sectorOffset,
            int sectorCount
    ) throws IOException {
        int chunkIndex = MCAFile.getChunkIndex(localChunkX, localChunkZ);
        file.seek((long) chunkIndex * Integer.BYTES);
        file.writeInt(sectorOffset << Byte.SIZE | sectorCount);
    }
}
