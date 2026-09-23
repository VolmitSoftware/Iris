package art.arcane.iris.world.history;

import art.arcane.volmlib.util.nbt.io.NBTUtil;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import net.jpountz.lz4.LZ4BlockOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class SavedTerrainStatusReaderTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void reusesReaderAcrossCodecsRegionsAndExternalChunks() throws Exception {
        Path world = temporaryFolder.newFolder().toPath();
        try (SavedTerrainChunkReader.StatusReader reader = new SavedTerrainChunkReader.StatusReader(world)) {
            for (int compression = 1; compression <= 4; compression++) {
                for (boolean external : new boolean[]{false, true}) {
                    int x = compression * 32 - 160;
                    writeChunk(world, x, -2, encode(root(x, -2, "minecraft:noise")), compression, external);
                    assertEquals("minecraft:noise", reader.readStatus(x, -2));
                    assertEquals("minecraft:noise", SavedTerrainChunk.readStatus(world, x, -2));
                    writeChunk(world, x, -2, encode(root(x, -2, "minecraft:full")), compression, external);
                    assertEquals("minecraft:full", reader.readStatus(x, -2));
                }
            }
        }
    }

    @Test
    public void observesAtomicReplacementOfAnOpenRegion() throws Exception {
        Path world = temporaryFolder.newFolder().toPath();
        Path replacement = temporaryFolder.newFolder().toPath();
        writeChunk(world, -1, -2, orderedRoot(), 2, false);
        writeChunk(replacement, -1, -2, encode(root(-1, -2, "minecraft:noise")), 2, false);
        try (SavedTerrainChunkReader.StatusReader reader = new SavedTerrainChunkReader.StatusReader(world)) {
            assertEquals("minecraft:full", reader.readStatus(-1, -2));
            Files.move(replacement.resolve("region/r.-1.-1.mca"), world.resolve("region/r.-1.-1.mca"),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            assertEquals("minecraft:noise", reader.readStatus(-1, -2));
        }
    }

    @Test
    public void identifiesTheChunkWhenDecodingFails() throws Exception {
        Path world = temporaryFolder.newFolder().toPath();
        writeChunk(world, -1, -2, new byte[]{10}, 2, false);
        try (SavedTerrainChunkReader.StatusReader reader = new SavedTerrainChunkReader.StatusReader(world)) {
            IOException failure = assertThrows(IOException.class, () -> reader.readStatus(-1, -2));
            assertTrue(failure.getMessage().contains("-1,-2"));
            assertTrue(failure.getCause() instanceof IOException);
        }
    }

    @Test
    public void rejectsReadsAfterCloseAndCanCloseTwice() throws Exception {
        Path world = temporaryFolder.newFolder().toPath();
        writeChunk(world, -1, -2, encode(root(-1, -2, "minecraft:full")), 2, false);
        SavedTerrainChunkReader.StatusReader reader = new SavedTerrainChunkReader.StatusReader(world);
        assertEquals("minecraft:full", reader.readStatus(-1, -2));
        reader.close();
        reader.close();
        assertThrows(IOException.class, () -> reader.readStatus(-1, -2));
    }

    @Test
    public void rejectsCorruptionAfterRequiredFieldsAndRecoversForNextChunk() throws Exception {
        Path world = temporaryFolder.newFolder().toPath();
        byte[] valid = orderedRoot();
        byte[] malformed = Arrays.copyOf(valid, valid.length + 3);
        malformed[valid.length - 1] = 99;
        byte[][] failures = {Arrays.copyOf(valid, valid.length - 1), malformed,
                encode(root(1, -2, "minecraft:full")), encode(root(-1, -2, " "))};
        try (SavedTerrainChunkReader.StatusReader reader = new SavedTerrainChunkReader.StatusReader(world)) {
            for (byte[] failure : failures) {
                writeChunk(world, -1, -2, failure, 2, false);
                assertThrows(IOException.class, () -> reader.readStatus(-1, -2));
                writeChunk(world, -1, -2, valid, 2, false);
                assertEquals("minecraft:full", reader.readStatus(-1, -2));
            }
        }
    }

    @Test
    public void validatesCompressionChecksumsAndTruncationBeforeReturningStatus() throws Exception {
        Path world = temporaryFolder.newFolder().toPath();
        try (SavedTerrainChunkReader.StatusReader reader = new SavedTerrainChunkReader.StatusReader(world)) {
            for (int compression : new int[]{1, 2, 4}) {
                writeChunk(world, -1, -2, orderedRoot(), compression, true);
                Path external = world.resolve("region/c.-1.-2.mcc");
                byte[] encoded = Files.readAllBytes(external);
                Files.write(external, Arrays.copyOf(encoded, encoded.length - 3));
                assertThrows(IOException.class, () -> reader.readStatus(-1, -2));
                writeChunk(world, -1, -2, orderedRoot(), compression, true);
                byte[] corrupted = Files.readAllBytes(external);
                corrupted[compression == 4 ? 17 : corrupted.length - 5] ^= 1;
                Files.write(external, corrupted);
                assertThrows(IOException.class, () -> reader.readStatus(-1, -2));
            }
        }
    }

    @Test
    public void rejectsOversizedInflationAndRetainsOnlyCurrentPayloadLength() throws Exception {
        Path world = temporaryFolder.newFolder().toPath();
        try (SavedTerrainChunkReader.StatusReader reader = new SavedTerrainChunkReader.StatusReader(world)) {
            writeChunk(world, -1, -2, orderedRoot(), 2, true);
            Path external = world.resolve("region/c.-1.-2.mcc");
            try (OutputStream output = new DeflaterOutputStream(Files.newOutputStream(external))) {
                byte[] padding = new byte[65536];
                for (int index = 0; index < 1024; index++) {
                    output.write(padding);
                }
                output.write(0);
            }
            assertThrows(IOException.class, () -> reader.readStatus(-1, -2));
            writeChunk(world, -1, -2, orderedRoot(), 2, false);
            assertEquals("minecraft:full", reader.readStatus(-1, -2));
            byte[] truncated = Arrays.copyOf(orderedRoot(), orderedRoot().length - 1);
            writeChunk(world, -1, -2, truncated, 2, false);
            assertThrows(IOException.class, () -> reader.readStatus(-1, -2));
        }
    }

    @Test
    public void growsBuffersAndReusesReaderAcrossLargeDirectAndSliceReads() throws Exception {
        Path world = temporaryFolder.newFolder().toPath();
        Random random = new Random(729L);
        try (SavedTerrainChunkReader.StatusReader reader = new SavedTerrainChunkReader.StatusReader(world)) {
            for (int size : new int[]{196_608, 64, 131_072, 196_608}) {
                byte[] padding = new byte[size];
                random.nextBytes(padding);
                CompoundTag root = root(-1, -2, "minecraft:full");
                root.putByteArray("padding", padding);
                byte[] nbt = encode(root);
                writeChunk(world, -1, -2, nbt, 2, false);
                assertEquals("minecraft:full", reader.readStatus(-1, -2));
                byte[] compressed = compress(nbt);
                byte[] slice = new byte[compressed.length + 96];
                Arrays.fill(slice, (byte) 127);
                System.arraycopy(compressed, 0, slice, 37, compressed.length);
                assertEquals("minecraft:full", reader.readStatus(slice,
                        new SavedTerrainRegionSnapshot.Payload(37, compressed.length, 2, false), -1, -2));
            }
        }
    }

    @Test
    public void rejectsCorruptAndTruncatedSlicesAndRecoversForValidInput() throws Exception {
        Path world = temporaryFolder.newFolder().toPath();
        byte[] compressed = compress(orderedRoot());
        byte[] slice = new byte[compressed.length + 64];
        System.arraycopy(compressed, 0, slice, 31, compressed.length);
        SavedTerrainRegionSnapshot.Payload stored = new SavedTerrainRegionSnapshot.Payload(
                31, compressed.length, 2, false);
        try (SavedTerrainChunkReader.StatusReader reader = new SavedTerrainChunkReader.StatusReader(world)) {
            assertEquals("minecraft:full", reader.readStatus(slice, stored, -1, -2));
            SavedTerrainRegionSnapshot.Payload truncated = new SavedTerrainRegionSnapshot.Payload(
                    31, compressed.length - 1, 2, false);
            assertThrows(IOException.class, () -> reader.readStatus(slice, truncated, -1, -2));
            assertEquals("minecraft:full", reader.readStatus(slice, stored, -1, -2));
            byte[] corrupt = slice.clone();
            corrupt[31 + compressed.length - 1] ^= 1;
            assertThrows(IOException.class, () -> reader.readStatus(corrupt, stored, -1, -2));
            assertEquals("minecraft:full", reader.readStatus(slice, stored, -1, -2));
        }
    }

    private static byte[] compress(byte[] bytes) throws IOException {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (OutputStream output = new DeflaterOutputStream(compressed)) {
            output.write(bytes);
        }
        return compressed.toByteArray();
    }

    private static CompoundTag root(int x, int z, String status) {
        CompoundTag root = new CompoundTag();
        root.putInt("xPos", x);
        root.putInt("zPos", z);
        root.putString("Status", status);
        return root;
    }

    private static byte[] orderedRoot() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeByte(10);
        output.writeUTF("");
        output.writeByte(3);
        output.writeUTF("xPos");
        output.writeInt(-1);
        output.writeByte(3);
        output.writeUTF("zPos");
        output.writeInt(-2);
        output.writeByte(8);
        output.writeUTF("Status");
        output.writeUTF("minecraft:full");
        output.writeByte(0);
        return bytes.toByteArray();
    }

    private static byte[] encode(CompoundTag root) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NBTUtil.write(root, bytes, false);
        return bytes.toByteArray();
    }

    private static void writeChunk(Path world, int x, int z, byte[] nbt, int compression, boolean external)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (OutputStream output = switch (compression) {
            case 1 -> new GZIPOutputStream(bytes);
            case 2 -> new DeflaterOutputStream(bytes);
            case 4 -> new LZ4BlockOutputStream(bytes);
            default -> bytes;
        }) {
            output.write(nbt);
        }
        byte[] payload = bytes.toByteArray();
        Path directory = Files.createDirectories(world.resolve("region"));
        Path region = directory.resolve("r." + Math.floorDiv(x, 32) + "." + Math.floorDiv(z, 32) + ".mca");
        try (RandomAccessFile file = new RandomAccessFile(region.toFile(), "rw")) {
            int sectors = external ? 1 : Math.ceilDiv(payload.length + 5, 4096);
            file.setLength((2L + sectors) * 4096);
            file.seek((Math.floorMod(z, 32) * 32L + Math.floorMod(x, 32)) * 4);
            file.writeInt((2 << 8) | sectors);
            file.seek(8192);
            file.writeInt(external ? 1 : payload.length + 1);
            file.writeByte(compression | (external ? 128 : 0));
            if (!external) {
                file.write(payload);
            }
        }
        if (external) {
            Files.write(directory.resolve("c." + x + "." + z + ".mcc"), payload);
        }
    }
}
