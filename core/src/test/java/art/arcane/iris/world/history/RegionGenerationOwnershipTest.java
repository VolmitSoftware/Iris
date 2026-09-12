package art.arcane.iris.world.history;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class RegionGenerationOwnershipTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void roundTripPreservesAssignmentsAcrossNegativeRegionCoordinates() throws Exception {
        Path directory = temporaryFolder.newFolder("ownership").toPath();
        RegionGenerationOwnership ownership = new RegionGenerationOwnership(-1, -1);

        assertTrue(ownership.assign(-32, -32, 7L));
        assertTrue(ownership.assign(-1, -1, 7L));
        assertTrue(ownership.assign(-17, -9, 19L));
        assertTrue(ownership.persist(directory));

        Path file = directory.resolve(RegionGenerationOwnership.fileName(-1, -1));
        assertTrue(Files.size(file) < 512L);
        RegionGenerationOwnership loaded = RegionGenerationOwnership.read(file);

        assertEquals(3, loaded.assignmentCount());
        assertEquals(7L, loaded.resolve(-32, -32, 31L));
        assertEquals(7L, loaded.resolve(-1, -1, 31L));
        assertEquals(19L, loaded.resolve(-17, -9, 31L));
        assertEquals(31L, loaded.resolve(-16, -8, 31L));
        assertFalse(loaded.persist(directory));
    }

    @Test
    public void everyPaletteWidthRoundTripsWithoutBitBoundaryLoss() throws Exception {
        Path directory = temporaryFolder.newFolder("wide-palette").toPath();
        RegionGenerationOwnership ownership = new RegionGenerationOwnership(0, 0);
        for (int chunkZ = 0; chunkZ < 32; chunkZ++) {
            for (int chunkX = 0; chunkX < 32; chunkX++) {
                int index = chunkX + chunkZ * 32;
                ownership.assign(chunkX, chunkZ, index + 1L);
            }
        }

        ownership.persist(directory);
        Path file = directory.resolve(RegionGenerationOwnership.fileName(0, 0));
        RegionGenerationOwnership loaded = RegionGenerationOwnership.read(file);

        assertEquals(1_024, loaded.assignmentCount());
        assertTrue(Files.size(file) < 10_000L);
        for (int chunkZ = 0; chunkZ < 32; chunkZ++) {
            for (int chunkX = 0; chunkX < 32; chunkX++) {
                int index = chunkX + chunkZ * 32;
                assertEquals(index + 1L, loaded.explicitActivation(chunkX, chunkZ));
            }
        }
    }

    @Test
    public void assignmentsAreIdempotentButCannotChangeOwner() {
        RegionGenerationOwnership ownership = new RegionGenerationOwnership(0, 0);

        assertTrue(ownership.assign(3, 5, 12L));
        assertFalse(ownership.assign(3, 5, 12L));
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ownership.assign(3, 5, 13L)
        );

        assertTrue(error.getMessage().contains("already belongs to activation 12"));
        assertEquals(12L, ownership.explicitActivation(3, 5));
    }

    @Test
    public void checksumCorruptionFailsClosed() throws Exception {
        Path file = persistedSingleAssignment("checksum");
        byte[] encoded = Files.readAllBytes(file);
        encoded[20] ^= 0x10;
        Files.write(file, encoded);

        IOException error = assertThrows(IOException.class, () -> RegionGenerationOwnership.read(file));

        assertTrue(error.getMessage().contains("checksum mismatch"));
    }

    @Test
    public void truncatedDataFailsClosed() throws Exception {
        Path file = persistedSingleAssignment("truncated");
        byte[] encoded = Files.readAllBytes(file);
        Files.write(file, java.util.Arrays.copyOf(encoded, encoded.length - 3));

        IOException error = assertThrows(IOException.class, () -> RegionGenerationOwnership.read(file));

        assertTrue(error.getMessage().contains("Invalid generation ownership file"));
    }

    @Test
    public void unsupportedVersionFailsClosedEvenWithAValidChecksum() throws Exception {
        Path file = persistedSingleAssignment("version");
        byte[] encoded = Files.readAllBytes(file);
        encoded[4] = 0;
        encoded[5] = 2;
        rewriteChecksum(encoded);
        Files.write(file, encoded);

        IOException error = assertThrows(IOException.class, () -> RegionGenerationOwnership.read(file));

        assertTrue(error.getMessage().contains("unsupported format version 2"));
    }

    @Test
    public void regionCoordinatesOutsideTheChunkRangeFailClosed() throws Exception {
        Path file = persistedSingleAssignment("coordinate-range");
        byte[] encoded = Files.readAllBytes(file);
        ByteBuffer.wrap(encoded, 8, Integer.BYTES).putInt(Integer.MAX_VALUE);
        rewriteChecksum(encoded);
        Files.write(file, encoded);

        IOException error = assertThrows(IOException.class, () -> RegionGenerationOwnership.read(file));

        assertTrue(error.getMessage().contains("region coordinate is outside"));
    }

    @Test
    public void activationIdsMustBePositive() {
        RegionGenerationOwnership ownership = new RegionGenerationOwnership(0, 0);

        assertThrows(IllegalArgumentException.class, () -> ownership.assign(0, 0, 0L));
        assertThrows(IllegalArgumentException.class, () -> ownership.assign(0, 0, -1L));
        assertThrows(IllegalArgumentException.class, () -> ownership.resolve(0, 0, 0L));
    }

    private Path persistedSingleAssignment(String directoryName) throws Exception {
        Path directory = temporaryFolder.newFolder(directoryName).toPath();
        RegionGenerationOwnership ownership = new RegionGenerationOwnership(0, 0);
        ownership.assign(0, 0, 1L);
        ownership.persist(directory);
        return directory.resolve(RegionGenerationOwnership.fileName(0, 0));
    }

    private static void rewriteChecksum(byte[] encoded) {
        int bodyLength = encoded.length - Integer.BYTES;
        CRC32 checksum = new CRC32();
        checksum.update(encoded, 0, bodyLength);
        ByteBuffer.wrap(encoded, bodyLength, Integer.BYTES).putInt((int) checksum.getValue());
    }
}
