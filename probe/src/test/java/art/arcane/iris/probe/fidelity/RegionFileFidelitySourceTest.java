package art.arcane.iris.probe.fidelity;

import art.arcane.volmlib.util.nbt.io.NBTSerializer;
import art.arcane.volmlib.util.nbt.io.NamedTag;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import art.arcane.volmlib.util.nbt.tag.ListTag;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.DeflaterOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class RegionFileFidelitySourceTest {
    private static final int CHUNK_X = 2;
    private static final int CHUNK_Z = -1;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void decodesPackedSectionsFromRegionFiles() throws Exception {
        Path regions = temporaryFolder.newFolder("region").toPath();
        writeRegion(regions.resolve("r.0.-1.mca"), chunk("minecraft:full"));

        try (RegionFileFidelitySource source = new RegionFileFidelitySource(regions, -64, 320)) {
            FidelityChunk chunk = source.sample(CHUNK_X, CHUNK_Z);

            assertEquals(-40, chunk.surfaceY(0, 0));
            assertEquals(-40, chunk.surfaceY(9, 3));
            assertTrue(chunk.fluid(0, -39, 0));
            assertFalse(chunk.fluid(9, -39, 0));
            assertTrue(chunk.open(9, -39, 0));
            assertTrue(chunk.open(0, -60, 0));
            assertFalse(chunk.open(1, -60, 0));
            assertEquals("minecraft:stone", chunk.block(15, -64, 15));
            assertEquals("minecraft:air", chunk.block(4, 100, 4));
            assertNull(chunk.surfaceBiome(0, 0));

            FidelityStats stats = new FidelityStats(-64, 320);
            stats.accept(chunk, 1);
            assertEquals(128L, stats.fluidColumns());
            assertEquals(16L, stats.caveVoxels()[0]);
        }
    }

    @Test
    public void missingChunkFails() throws Exception {
        Path regions = temporaryFolder.newFolder("region").toPath();
        writeRegion(regions.resolve("r.0.-1.mca"), chunk("minecraft:full"));

        try (RegionFileFidelitySource source = new RegionFileFidelitySource(regions, -64, 320)) {
            assertThrows(IllegalStateException.class, () -> source.sample(CHUNK_X + 1, CHUNK_Z));
            assertThrows(IllegalStateException.class, () -> source.sample(40, 40));
        }
    }

    @Test
    public void partiallyGeneratedChunkFails() throws Exception {
        Path regions = temporaryFolder.newFolder("region").toPath();
        writeRegion(regions.resolve("r.0.-1.mca"), chunk("minecraft:features"));

        try (RegionFileFidelitySource source = new RegionFileFidelitySource(regions, -64, 320)) {
            assertThrows(IllegalStateException.class, () -> source.sample(CHUNK_X, CHUNK_Z));
        }
    }

    private static CompoundTag chunk(String status) {
        CompoundTag root = new CompoundTag();
        root.putInt("DataVersion", 5023);
        root.putInt("xPos", CHUNK_X);
        root.putInt("zPos", CHUNK_Z);
        root.putInt("yPos", -4);
        root.putString("Status", status);
        ListTag<CompoundTag> sections = new ListTag<>(CompoundTag.class);
        sections.add(section(-4, List.of("minecraft:stone", "minecraft:air"), (int x, int localY, int z) -> localY == 4 && x == 0 ? 1 : 0));
        sections.add(section(-3, List.of("minecraft:stone", "minecraft:water", "minecraft:air"),
                (int x, int localY, int z) -> localY <= 8 ? 0 : x < 8 ? 1 : 2));
        sections.add(section(-2, List.of("minecraft:air"), (int x, int localY, int z) -> 0));
        root.put("sections", sections);
        return root;
    }

    private static CompoundTag section(int sectionY, List<String> palette, VoxelPalette voxels) {
        CompoundTag section = new CompoundTag();
        section.putByte("Y", (byte) sectionY);
        CompoundTag blockStates = new CompoundTag();
        ListTag<CompoundTag> paletteTag = new ListTag<>(CompoundTag.class);
        for (String name : palette) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Name", name);
            paletteTag.add(entry);
        }
        blockStates.put("palette", paletteTag);
        if (palette.size() > 1) {
            long[] data = new long[256];
            for (int index = 0; index < 4096; index++) {
                long value = voxels.index(index & 15, index >> 8, (index >> 4) & 15);
                data[index / 16] |= value << ((index % 16) * 4);
            }
            blockStates.putLongArray("data", data);
        }
        section.put("block_states", blockStates);
        return section;
    }

    private static void writeRegion(Path file, CompoundTag chunk) throws IOException {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream out = new DeflaterOutputStream(compressed)) {
            new NBTSerializer(false).toStream(new NamedTag("", chunk), out);
        }
        byte[] payload = compressed.toByteArray();
        int sectors = (payload.length + 5 + 4095) / 4096;
        int index = (CHUNK_X & 31) + (CHUNK_Z & 31) * 32;
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
            raf.setLength(8192L + sectors * 4096L);
            raf.seek(index * 4L);
            raf.writeInt((2 << 8) | sectors);
            raf.seek(8192L);
            raf.writeInt(payload.length + 1);
            raf.writeByte(2);
            raf.write(payload);
        }
    }

    @FunctionalInterface
    private interface VoxelPalette {
        int index(int x, int localY, int z);
    }
}
