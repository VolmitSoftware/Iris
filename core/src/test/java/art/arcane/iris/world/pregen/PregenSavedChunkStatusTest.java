package art.arcane.iris.world.pregen;

import art.arcane.volmlib.util.nbt.io.NBTUtil;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PregenSavedChunkStatusTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void savedStatusRequiresFullAndMissingChunksAreUnfinished() throws IOException {
        Path root = temporary.newFolder().toPath();
        assertFalse(PregenSavedChunkStatus.fromWorld(root).isFull(-1, -2));
        for (String stage : new String[]{"minecraft:noise", "minecraft:initialize_light", "minecraft:spawn", "minecraft:full"}) {
            writeChunk(root, stage);
            PregenSavedChunkStatus status = PregenSavedChunkStatus.fromWorld(root);
            assertEquals(stage.equals("minecraft:full"), status.isFull(-1, -2));
            assertFalse(status.isFull(-2, -2));
        }
    }

    @Test
    public void malformedNativeStatusFailsValidation() throws IOException {
        Path root = temporary.newFolder().toPath();
        writeChunk(root, null);
        assertThrows(UncheckedIOException.class, () -> PregenSavedChunkStatus.fromWorld(root).isFull(-1, -2));
    }

    @Test
    public void statusMasksReuseReadsAndEvictAtTheRegionBound() {
        AtomicInteger reads = new AtomicInteger();
        PregenSavedChunkStatus status = new PregenSavedChunkStatus((x, z) -> {
            reads.incrementAndGet();
            return x == -1 && z == -2;
        });
        assertTrue(status.isFull(-1, -2));
        assertTrue(status.isFull(-1, -2));
        assertFalse(status.isFull(-2, -1));
        assertFalse(status.isFull(-2, -1));
        assertEquals(2, reads.get());
        for (int region = 0; region < 256; region++) {
            status.isFull(region << 5, 0);
        }
        assertTrue(status.isFull(-1, -2));
        assertEquals(259, reads.get());
    }

    @Test
    public void invalidSavedDataDoesNotBecomeACompletionHint() {
        PregenSavedChunkStatus status = new PregenSavedChunkStatus((x, z) -> {
            throw new IOException("corrupt native data");
        });
        assertThrows(UncheckedIOException.class, () -> status.isFull(1, 2));
    }

    private static void writeChunk(Path root, String stage) throws IOException {
        CompoundTag data = new CompoundTag();
        data.putInt("xPos", -1);
        data.putInt("zPos", -2);
        if (stage != null) {
            data.putString("Status", stage);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        NBTUtil.write(data, output, false);
        byte[] payload = output.toByteArray();
        Path region = Files.createDirectories(root.resolve("region")).resolve("r.-1.-1.mca");
        try (RandomAccessFile file = new RandomAccessFile(region.toFile(), "rw")) {
            file.setLength(12288);
            file.seek((30L * 32 + 31) * 4);
            file.writeInt((2 << 8) | 1);
            file.seek(8192);
            file.writeInt(payload.length + 1);
            file.writeByte(3);
            file.write(payload);
        }
    }
}
