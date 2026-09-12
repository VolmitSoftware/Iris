package art.arcane.iris.world.history;

import art.arcane.volmlib.util.nbt.io.NBTUtil;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import art.arcane.volmlib.util.nbt.tag.ListTag;
import art.arcane.volmlib.util.nbt.tag.StringTag;
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

public final class SavedTerrainChunkTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void readsModernPaletteAtNegativeCoordinatesWithEveryCompression() throws Exception {
        for (int compression = 1; compression <= 4; compression++) {
            Path world = temporaryFolder.newFolder("compression-" + compression).toPath();
            writeChunk(world, root("minecraft:full"), compression, false);
            SavedTerrainChunk chunk = SavedTerrainChunk.read(world, -1, -2, -16, 16);
            assertEquals("minecraft:full", chunk.nativeStatus());
            TerrainBoundarySignature column = chunk.column(-16, -32);
            assertEquals("minecraft:stone", column.geometry().voxelAt(-16).stateKey());
            assertEquals("minecraft:water[level=0]", column.geometry().voxelAt(-15).stateKey());
            assertEquals(BoundaryColumnGeometry.Phase.FLUID, column.geometry().voxelAt(-15).phase());
            assertEquals("example:saved_biome", column.biomeAtSample(0));
            assertFalse(chunk.hasColumn(1, 1));
        }
    }

    @Test
    public void externalChunkAndPartialNativeStagePreserveAvailableTerrain() throws Exception {
        Path world = temporaryFolder.newFolder("external").toPath();
        writeChunk(world, root("minecraft:noise"), 2, true);
        assertEquals("minecraft:noise", SavedTerrainChunk.readStatus(world, -1, -2));
        assertTrue(SavedTerrainChunk.hasTerrain("minecraft:noise"));
        assertFalse(SavedTerrainChunk.isComplete("minecraft:noise"));
        assertFalse(SavedTerrainChunk.hasTerrain("minecraft:biomes"));
        assertEquals("minecraft:noise", SavedTerrainChunk.read(world, -1, -2, -16, 16).nativeStatus());
    }

    @Test
    public void verifiesPreTerrainStructureStampInNativePayload() throws Exception {
        for (boolean bukkit : new boolean[]{false, true}) {
            Path world = temporaryFolder.newFolder("structures-" + bukkit).toPath();
            CompoundTag saved = root("minecraft:structure_starts");
            CompoundTag metadata = saved;
            if (bukkit) {
                metadata = new CompoundTag();
                saved.put("ChunkBukkitValues", metadata);
            }
            metadata.putLong(NativeTerrainReceipt.STRUCTURE_ACTIVATION_KEY, 7);
            writeChunk(world, saved, 2, false);
            SavedTerrainChunk.verifyCheckpoint(world, -1, -2, "minecraft:structure_starts", null, 7);
            assertThrows(IOException.class, () -> SavedTerrainChunk.verifyCheckpoint(
                    world, -1, -2, "minecraft:structure_starts", null, 8));
            assertFalse(SavedTerrainChunk.hasTerrain(SavedTerrainChunk.readStatus(world, -1, -2)));
        }
    }

    @Test
    public void receiptsPreserveNaturalBoundaryAfterSavedBlocksChange() throws Exception {
        Path world = temporaryFolder.newFolder("receipt").toPath();
        CompoundTag initial = root("minecraft:full");
        writeChunk(world, initial, 2, false);
        SavedTerrainChunk natural = SavedTerrainChunk.read(world, -1, -2, -16, 16);
        byte[] receipt = NativeTerrainReceipt.encode(natural, 3, "test-epoch");
        NativeTerrainReceipt.Decoded decoded = NativeTerrainReceipt.decode(receipt, "minecraft:full");
        assertEquals(3, decoded.activationId());
        assertEquals("test-epoch", decoded.epochId());
        assertFalse(decoded.terrain().hasColumn(3, 3));
        CompoundTag bukkitValues = new CompoundTag();
        bukkitValues.putByteArray(NativeTerrainReceipt.NBT_KEY, receipt);
        initial.put("ChunkBukkitValues", bukkitValues);
        initial.remove("sections");
        writeChunk(world, initial, 2, false);
        SavedTerrainChunk restored = SavedTerrainChunk.read(world, -1, -2, -16, 16);
        assertEquals(natural.column(-16, -32).geometry(), restored.column(-16, -32).geometry());
        assertThrows(IOException.class, () -> NativeTerrainReceipt.restore(receipt, "minecraft:full", 1, 2, -16, 16));
        assertThrows(IOException.class, () -> NativeTerrainReceipt.restore(receipt, "minecraft:full", -1, -2, 0, 16));
        receipt[receipt.length - 5] ^= 1;
        assertThrows(IOException.class, () -> NativeTerrainReceipt.decode(receipt, "minecraft:full"));
    }

    @Test
    public void rejectsMissingBiomesAndInvalidPaletteIndices() throws Exception {
        Path world = temporaryFolder.newFolder("invalid").toPath();
        CompoundTag root = root("minecraft:full");
        CompoundTag section = (CompoundTag) root.getListTag("sections").get(0);
        section.remove("biomes");
        writeChunk(world, root, 2, false);
        assertThrows(IOException.class, () -> SavedTerrainChunk.read(world, -1, -2, -16, 16));
        root = root("minecraft:full");
        section = (CompoundTag) root.getListTag("sections").get(0);
        long[] data = section.getCompoundTag("block_states").getLongArray("data");
        data[0] = 15;
        writeChunk(world, root, 2, false);
        assertThrows(IOException.class, () -> SavedTerrainChunk.read(world, -1, -2, -16, 16));
    }

    private static CompoundTag root(String status) {
        CompoundTag root = new CompoundTag();
        root.putInt("xPos", -1);
        root.putInt("zPos", -2);
        root.putString("Status", status);
        CompoundTag section = new CompoundTag();
        section.putByte("Y", (byte) -1);
        CompoundTag blocks = new CompoundTag();
        ListTag<CompoundTag> palette = new ListTag<>(CompoundTag.class);
        CompoundTag stone = new CompoundTag();
        stone.putString("Name", "minecraft:stone");
        palette.add(stone);
        CompoundTag water = new CompoundTag();
        water.putString("Name", "minecraft:water");
        CompoundTag properties = new CompoundTag();
        properties.putString("level", "0");
        water.put("Properties", properties);
        palette.add(water);
        blocks.put("palette", palette);
        long[] data = new long[256];
        for (int index = 16; index < 32; index++) {
            data[index] = 0x1111111111111111L;
        }
        blocks.putLongArray("data", data);
        section.put("block_states", blocks);
        CompoundTag biomes = new CompoundTag();
        ListTag<StringTag> biomePalette = new ListTag<>(StringTag.class);
        biomePalette.add(new StringTag("example:saved_biome"));
        biomes.put("palette", biomePalette);
        section.put("biomes", biomes);
        ListTag<CompoundTag> sections = new ListTag<>(CompoundTag.class);
        sections.add(section);
        root.put("sections", sections);
        return root;
    }

    private static void writeChunk(Path world, CompoundTag root, int compression, boolean external) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (OutputStream output = switch (compression) {
            case 1 -> new GZIPOutputStream(bytes);
            case 2 -> new DeflaterOutputStream(bytes);
            case 4 -> new LZ4BlockOutputStream(bytes);
            default -> bytes;
        }) {
            NBTUtil.write(root, output, false);
        }
        byte[] payload = bytes.toByteArray();
        Path region = Files.createDirectories(world.resolve("region"));
        try (RandomAccessFile file = new RandomAccessFile(region.resolve("r.-1.-1.mca").toFile(), "rw")) {
            int sectors = external ? 1 : Math.ceilDiv(payload.length + 5, 4096);
            file.setLength((2L + sectors) * 4096);
            file.seek((30L * 32 + 31) * 4);
            file.writeInt((2 << 8) | sectors);
            file.seek(8192);
            file.writeInt(external ? 1 : payload.length + 1);
            file.writeByte(compression | (external ? 128 : 0));
            if (!external) {
                file.write(payload);
            }
        }
        if (external) {
            Files.write(region.resolve("c.-1.-2.mcc"), payload);
        }
    }
}
