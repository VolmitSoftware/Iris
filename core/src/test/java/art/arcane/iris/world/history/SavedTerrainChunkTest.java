package art.arcane.iris.world.history;

import art.arcane.volmlib.nativelib.terrain.NativeTerrainReceiptStorage;

import art.arcane.volmlib.util.nbt.io.NBTUtil;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import art.arcane.volmlib.util.nbt.tag.ListTag;
import art.arcane.volmlib.util.nbt.tag.StringTag;
import net.jpountz.lz4.LZ4BlockOutputStream;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import org.junit.Before;
import org.junit.After;
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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class SavedTerrainChunkTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void bindPlatform() {
        IrisPlatforms.unbind();
        IrisPlatform platform = mock(IrisPlatform.class);
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(platform.registries()).thenReturn(registries);
        for (String state : new String[]{"minecraft:stone", "minecraft:water[level=0]",
                "minecraft:sea_pickle[pickles=1,waterlogged=true]"}) {
            NativeBlockState block = mock(NativeBlockState.class);
            when(block.key()).thenReturn(state);
            when(registries.blockOrNull(state.split("\\[")[0])).thenReturn(block);
        }
        IrisPlatforms.bind(platform);
    }

    @After
    public void unbindPlatform() {
        IrisPlatforms.unbind();
    }

    @Test
    public void reads263LowercasePaletteAtTerrainStatus() throws Exception {
        Path world = temporaryFolder.newFolder("terrain-263").toPath();
        CompoundTag saved = root("minecraft:terrain");
        saved.putInt("DataVersion", 5023);
        CompoundTag section = (CompoundTag) saved.getListTag("sections").get(0);
        ListTag<?> palette = section.getCompoundTag("block_states").getListTag("palette");
        for (int index = 0; index < palette.size(); index++) {
            CompoundTag state = (CompoundTag) palette.get(index);
            state.put("id", state.remove("Name"));
            if (state.containsKey("Properties")) {
                state.put("properties", state.remove("Properties"));
            }
        }
        writeChunk(world, saved, 2, false);
        SavedTerrainChunk chunk = SavedTerrainChunk.read(world, -1, -2, -16, 16);
        assertEquals("minecraft:terrain", chunk.nativeStatus());
        assertEquals("minecraft:water[level=0]", chunk.column(-16, -32).geometry().voxelAt(-15).stateKey());
    }

    @Test
    public void reads263StringAndWrappedPaletteEntries() throws Exception {
        for (String name : new String[]{"minecraft:stone", "minecraft:water", "minecraft:sea_pickle"}) {
            for (boolean wrapped : new boolean[]{false, true}) {
                Path world = temporaryFolder.newFolder("terrain-string-" + name.substring(10) + "-" + wrapped).toPath();
                CompoundTag saved = root("minecraft:full");
                saved.putInt("DataVersion", 5023);
                CompoundTag section = (CompoundTag) saved.getListTag("sections").get(0);
                CompoundTag blocks = section.getCompoundTag("block_states");
                blocks.remove("data");
                if (wrapped) {
                    ListTag<CompoundTag> palette = new ListTag<>(CompoundTag.class);
                    CompoundTag state = new CompoundTag();
                    state.putString("", name);
                    palette.add(state);
                    blocks.put("palette", palette);
                } else {
                    ListTag<StringTag> palette = new ListTag<>(StringTag.class);
                    palette.add(new StringTag(name));
                    blocks.put("palette", palette);
                }
                writeChunk(world, saved, 2, false);
                SavedTerrainChunk chunk = SavedTerrainChunk.read(world, -1, -2, -16, 16);
                BoundaryColumnGeometry.Voxel voxel = chunk.column(-16, -32).geometry().voxelAt(-16);
                assertEquals(IrisPlatforms.get().registries().blockOrNull(name).key(), voxel.stateKey());
                assertEquals(name.equals("minecraft:stone") ? "" : "minecraft:water[level=0]", voxel.fluidStateKey());
            }
        }
    }

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
    public void reusesFullChunkReaderAcrossCodecsAndPreservesValidationAfterFailures() throws Exception {
        Path world = temporaryFolder.newFolder("reused-full-reader").toPath();
        try (SavedTerrainChunkReader.StatusReader reader = new SavedTerrainChunkReader.StatusReader(world)) {
            for (int compression = 1; compression <= 4; compression++) {
                for (boolean external : new boolean[]{false, true}) {
                    CompoundTag saved = root("minecraft:noise");
                    saved.putByteArray("padding", new byte[196_608]);
                    writeChunk(world, saved, compression, external);
                    SavedTerrainChunk captured = reader.readChunk(-1, -2, -16, 16);
                    assertEquals("minecraft:noise", captured.nativeStatus());
                    assertEquals("minecraft:stone", captured.column(-16, -32).geometry().voxelAt(-16).stateKey());
                    assertEquals("minecraft:water[level=0]", captured.column(-16, -32).geometry().voxelAt(-15).stateKey());
                    assertEquals("example:saved_biome", captured.column(-16, -32).biomeAtSample(0));
                    saved.putInt("xPos", 0);
                    writeChunk(world, saved, compression, external);
                    assertThrows(IOException.class, () -> reader.readChunk(-1, -2, -16, 16));
                    writeChunk(world, root("minecraft:empty"), compression, external);
                    assertThrows(IOException.class, () -> reader.readChunk(-1, -2, -16, 16));
                    saved = root("minecraft:full");
                    saved.remove("sections");
                    writeChunk(world, saved, compression, external);
                    assertThrows(IOException.class, () -> reader.readChunk(-1, -2, -16, 16));
                    writeChunk(world, root("minecraft:full"), compression, external);
                    assertEquals("minecraft:full", reader.readChunk(-1, -2, -16, 16).nativeStatus());
                }
            }
        }
    }

    @Test
    public void diskBoundaryCaptureClosesItsReaderAndClearsCachedChunks() throws Exception {
        Path world = temporaryFolder.newFolder("closed-disk-boundary").toPath();
        writeChunk(world, root("minecraft:full"), 2, false);
        DiskBoundaryCapture capture = new DiskBoundaryCapture(world, -16, 16);
        try (capture) {
            assertEquals("minecraft:stone", capture.sample(-16, -32).geometry().voxelAt(-16).stateKey());
            assertEquals("minecraft:water[level=0]", capture.sample(-16, -31).geometry().voxelAt(-15).stateKey());
        }
        capture.close();
        assertThrows(IOException.class, () -> capture.sample(-16, -32));
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
            metadata.putLong(NativeTerrainReceiptStorage.STRUCTURE_ACTIVATION_KEY, 7);
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
        bukkitValues.putByteArray(NativeTerrainReceiptStorage.NBT_KEY, receipt);
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
