package art.arcane.iris.world.history;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class SavedTerrainRegionSnapshotTest {
    private static final int SECTOR_BYTES = 4_096;
    private static final int HEADER_BYTES = 2 * SECTOR_BYTES;

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void extractsInternalAndExternalPayloadDescriptors() throws Exception {
        byte[] bytes = new byte[4 * SECTOR_BYTES];
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.putInt(0, 2 << 8 | 1);
        buffer.putInt(1023 * Integer.BYTES, 3 << 8 | 1);
        buffer.putInt(HEADER_BYTES, 4);
        bytes[HEADER_BYTES + 4] = 2;
        bytes[HEADER_BYTES + 5] = 12;
        bytes[HEADER_BYTES + 6] = 34;
        bytes[HEADER_BYTES + 7] = 56;
        buffer.putInt(3 * SECTOR_BYTES, 1);
        bytes[3 * SECTOR_BYTES + 4] = (byte) 132;
        Path file = writeRegion(bytes);

        SavedTerrainRegionSnapshot snapshot = SavedTerrainRegionSnapshot.read(file, bytes.length, new byte[0]).orElseThrow();
        SavedTerrainRegionSnapshot.Payload internal = snapshot.payload(0);
        assertEquals(2, internal.compression());
        assertFalse(internal.external());
        assertEquals(3, internal.length());
        assertArrayEquals(new byte[]{12, 34, 56}, Arrays.copyOfRange(snapshot.bytes(),
                internal.offset(), internal.offset() + internal.length()));
        SavedTerrainRegionSnapshot.Payload external = snapshot.payload(1023);
        assertTrue(external.external());
        assertEquals(4, external.compression());
        assertEquals(0, external.length());
        snapshot.validateUnchanged();
    }

    @Test
    public void skipsOversizedRegionsWithoutReadingTheirPayloads() throws Exception {
        Path file = temporaryFolder.newFile().toPath();
        try (RandomAccessFile output = new RandomAccessFile(file.toFile(), "rw")) {
            output.setLength(128L * 1024 * 1024);
        }
        assertTrue(SavedTerrainRegionSnapshot.read(file, 64 * 1024, new byte[0]).isEmpty());
    }

    @Test
    public void acceptsAnExactUnpaddedPayload() throws Exception {
        byte[] bytes = region(2 << 8 | 1, 3, 3, HEADER_BYTES + 7);
        bytes[HEADER_BYTES + 5] = 10;
        bytes[HEADER_BYTES + 6] = 0;
        Path file = writeRegion(bytes);
        SavedTerrainRegionSnapshot snapshot = SavedTerrainRegionSnapshot.read(file, bytes.length, new byte[0]).orElseThrow();
        assertEquals(2, snapshot.payload(0).length());
        snapshot.validateUnchanged();
    }

    @Test
    public void reusesLargerBuffersWithoutAcceptingStalePayloadBytes() throws Exception {
        byte[] larger = region(2 << 8 | 1, 5, 2, 4 * SECTOR_BYTES);
        ByteBuffer.wrap(larger).putInt(3 * SECTOR_BYTES, 1);
        larger[3 * SECTOR_BYTES + Integer.BYTES] = 2;
        Path largerFile = writeRegion(larger);
        SavedTerrainRegionSnapshot original = SavedTerrainRegionSnapshot.read(
                largerFile, larger.length, new byte[0]).orElseThrow();
        byte[] reusable = original.bytes();
        original.validateUnchanged();

        byte[] smaller = region(2 << 8 | 1, 1, 3, HEADER_BYTES + 5);
        Path smallerFile = writeRegion(smaller);
        SavedTerrainRegionSnapshot snapshot = SavedTerrainRegionSnapshot.read(
                smallerFile, smaller.length, reusable).orElseThrow();
        assertSame(reusable, snapshot.bytes());
        assertEquals(larger.length, snapshot.bytes().length);
        assertEquals(0, snapshot.payload(0).length());
        assertEquals(3, snapshot.payload(0).compression());
        snapshot.validateUnchanged();
        assertTrue(SavedTerrainRegionSnapshot.read(largerFile, smaller.length, reusable).isEmpty());

        for (int[] entry : new int[][]{{3 << 8 | 1, 1}, {2 << 8 | 1, 5}}) {
            Path malformed = writeRegion(region(entry[0], entry[1], 2, smaller.length));
            SavedTerrainRegionSnapshot invalid = SavedTerrainRegionSnapshot.read(
                    malformed, smaller.length, reusable).orElseThrow();
            assertSame(reusable, invalid.bytes());
            assertThrows(IOException.class, () -> invalid.payload(0));
            invalid.validateUnchanged();
        }
    }

    @Test
    public void rejectsAtomicReplacementWithMatchingSizeAndTimestamp() throws Exception {
        byte[] bytes = region(2 << 8 | 1, 1, 2, 3 * SECTOR_BYTES);
        Path file = writeRegion(bytes);
        SavedTerrainRegionSnapshot snapshot = SavedTerrainRegionSnapshot.read(file, bytes.length, new byte[0]).orElseThrow();
        FileTime timestamp = Files.getLastModifiedTime(file);
        Path replacement = writeRegion(bytes);
        Files.setLastModifiedTime(replacement, timestamp);
        Files.move(replacement, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        assertThrows(IOException.class, snapshot::validateUnchanged);
    }

    @Test
    public void rejectsSameFilePayloadModificationAndTruncation() throws Exception {
        byte[] bytes = region(2 << 8 | 1, 2, 2, 3 * SECTOR_BYTES);
        Path file = writeRegion(bytes);
        SavedTerrainRegionSnapshot snapshot = SavedTerrainRegionSnapshot.read(file, bytes.length, new byte[0]).orElseThrow();
        FileTime timestamp = Files.getLastModifiedTime(file);
        try (RandomAccessFile output = new RandomAccessFile(file.toFile(), "rw")) {
            output.seek(HEADER_BYTES + 5);
            output.writeByte(1);
        }
        Files.setLastModifiedTime(file, FileTime.fromMillis(timestamp.toMillis() + 2_000));
        assertThrows(IOException.class, snapshot::validateUnchanged);

        SavedTerrainRegionSnapshot rewritten = SavedTerrainRegionSnapshot.read(file, bytes.length, new byte[0]).orElseThrow();
        try (RandomAccessFile output = new RandomAccessFile(file.toFile(), "rw")) {
            output.setLength(HEADER_BYTES - 1);
        }
        assertThrows(IOException.class, rewritten::validateUnchanged);
    }

    @Test
    public void validatesBothHeaderSectorsEvenWhenTimestampIsRestored() throws Exception {
        for (int offset : new int[]{0, SECTOR_BYTES}) {
            byte[] bytes = region(2 << 8 | 1, 1, 2, 3 * SECTOR_BYTES);
            Path file = writeRegion(bytes);
            SavedTerrainRegionSnapshot snapshot = SavedTerrainRegionSnapshot.read(file, bytes.length, new byte[0]).orElseThrow();
            FileTime timestamp = Files.getLastModifiedTime(file);
            try (RandomAccessFile output = new RandomAccessFile(file.toFile(), "rw")) {
                output.seek(offset);
                output.writeInt(7);
            }
            Files.setLastModifiedTime(file, timestamp);
            assertThrows(IOException.class, snapshot::validateUnchanged);
        }
    }

    @Test
    public void rejectsInvalidAllocationsAndPayloadLengths() throws Exception {
        int[][] cases = {
                {0, 1, 2, 3 * SECTOR_BYTES},
                {1 << 8 | 1, 1, 2, 3 * SECTOR_BYTES},
                {2 << 8, 1, 2, 3 * SECTOR_BYTES},
                {3 << 8 | 1, 1, 2, 3 * SECTOR_BYTES},
                {0xFFFFFF01, 1, 2, 3 * SECTOR_BYTES},
                {2 << 8 | 1, 0, 2, 3 * SECTOR_BYTES},
                {2 << 8 | 1, -1, 2, 3 * SECTOR_BYTES},
                {2 << 8 | 1, Integer.MAX_VALUE, 2, 3 * SECTOR_BYTES},
                {2 << 8 | 1, SECTOR_BYTES, 2, 4 * SECTOR_BYTES},
                {2 << 8 | 1, 2, 130, 3 * SECTOR_BYTES},
                {2 << 8 | 1, 2, 2, HEADER_BYTES + 5}
        };
        for (int[] entry : cases) {
            byte[] bytes = region(entry[0], entry[1], entry[2], entry[3]);
            Path file = writeRegion(bytes);
            SavedTerrainRegionSnapshot snapshot = SavedTerrainRegionSnapshot.read(file, bytes.length, new byte[0]).orElseThrow();
            assertThrows(IOException.class, () -> snapshot.payload(0));
        }
    }

    @Test
    public void rejectsTruncatedRegionsDirectoriesAndSymlinks() throws Exception {
        for (int size : new int[]{0, HEADER_BYTES - 1}) {
            Path file = writeRegion(new byte[size]);
            assertThrows(IOException.class, () -> SavedTerrainRegionSnapshot.read(file, 0, new byte[0]));
        }
        Path directory = temporaryFolder.newFolder().toPath();
        assertThrows(IOException.class, () -> SavedTerrainRegionSnapshot.read(directory, HEADER_BYTES, new byte[0]));
        Path file = writeRegion(new byte[HEADER_BYTES]);
        Path link = directory.resolve("region-link");
        Files.createSymbolicLink(link, file);
        assertThrows(IOException.class, () -> SavedTerrainRegionSnapshot.read(link, HEADER_BYTES, new byte[0]));
    }

    private Path writeRegion(byte[] bytes) throws IOException {
        Path file = temporaryFolder.newFile().toPath();
        Files.write(file, bytes);
        return file;
    }

    private static byte[] region(int allocation, int length, int compression, int size) {
        byte[] bytes = new byte[size];
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.putInt(0, allocation);
        buffer.putInt(HEADER_BYTES, length);
        bytes[HEADER_BYTES + Integer.BYTES] = (byte) compression;
        return bytes;
    }
}
