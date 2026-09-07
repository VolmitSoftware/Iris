package art.arcane.iris.engine.history;

import org.junit.Test;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UTFDataFormatException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.OptionalInt;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class NativeTerrainReceiptTest {
    @Test
    public void preservesCompleteAndBoundaryReceiptBytes() throws Exception {
        for (boolean boundary : new boolean[]{false, true}) {
            SavedTerrainChunk chunk = chunk(boundary);
            String epoch = "epoch-\u0000-é-水-\ud83c\udf32";
            byte[] encoded = NativeTerrainReceipt.encode(chunk, 7, epoch);
            assertArrayEquals(legacyEncode(chunk, 7, epoch), encoded);
            byte[] payload;
            try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(encoded))) {
                payload = input.readAllBytes();
            }
            assertEquals(boundary
                            ? "42317aaeb436fa2ab174eaae05777dc46f4a3d9253fd9374680ab49fb4182225"
                            : "95879743b4b720c20598560159cffaed4dc4d3926369cac381ba8a0dad14e7b8",
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload)));
            NativeTerrainReceipt.Decoded decoded = NativeTerrainReceipt.decode(encoded, "minecraft:noise");
            assertEquals(7, decoded.activationId());
            assertEquals(epoch, decoded.epochId());
            assertEquals(-2, decoded.terrain().chunkX());
            assertEquals(3, decoded.terrain().chunkZ());
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    assertEquals(chunk.hasColumn(x, z), decoded.terrain().hasColumn(x, z));
                    if (!chunk.hasColumn(x, z)) {
                        continue;
                    }
                    TerrainBoundarySignature expected = chunk.column(-32 + x, 48 + z);
                    TerrainBoundarySignature actual = decoded.terrain().column(-32 + x, 48 + z);
                    assertEquals(expected.column(), actual.column());
                    assertEquals(expected.geometry(), actual.geometry());
                    assertEquals(expected.samples().layout(), actual.samples().layout());
                    for (int index = 0; index < expected.sampleCount(); index++) {
                        assertEquals(expected.biomeAtSample(index), actual.biomeAtSample(index));
                    }
                }
            }
        }
    }

    @Test
    public void saturatedStringCachesPreserveModifiedUtfBytes() throws Exception {
        for (int padding : new int[]{0, 300}) {
            List<String> keys = new ArrayList<>(300);
            for (int index = 0; index < 300; index++) {
                keys.add("iris:" + index + "-é-水-\u0000-\ud83c\udf32-\ud800-\udc00-" + "x".repeat(padding));
            }
            for (int index = 0; index < 300; index++) {
                keys.add(keys.getFirst());
            }
            SavedTerrainChunk chunk = biomeChunk(keys);
            byte[] encoded = NativeTerrainReceipt.encode(chunk, 9, "epoch");
            assertArrayEquals(legacyEncode(chunk, 9, "epoch"), encoded);
            TerrainBoundarySignature restored = NativeTerrainReceipt.decode(encoded, "minecraft:noise")
                    .terrain().column(0, 0);
            for (int index = 0; index < keys.size() * 2; index++) {
                assertEquals(keys.get(index % keys.size()), restored.biomeAtSample(index));
            }
        }
    }

    @Test
    public void maximumModifiedUtfLengthAndOversizedFailuresRemainUnchanged() throws Exception {
        for (String key : List.of("a".repeat(65_535), "é".repeat(32_767) + "a")) {
            SavedTerrainChunk chunk = biomeChunk(List.of(key));
            byte[] encoded = NativeTerrainReceipt.encode(chunk, 9, "epoch");
            assertArrayEquals(legacyEncode(chunk, 9, "epoch"), encoded);
            assertEquals(key, NativeTerrainReceipt.decode(encoded, "minecraft:noise")
                    .terrain().column(0, 0).biomeAtSample(0));
        }
        for (String key : List.of("a".repeat(65_536), "é".repeat(32_768), "\u0000".repeat(32_768))) {
            SavedTerrainChunk chunk = biomeChunk(List.of(key));
            UTFDataFormatException expected = assertThrows(UTFDataFormatException.class,
                    () -> legacyEncode(chunk, 9, "epoch"));
            UTFDataFormatException actual = assertThrows(UTFDataFormatException.class,
                    () -> NativeTerrainReceipt.encode(chunk, 9, "epoch"));
            assertEquals(expected.getMessage(), actual.getMessage());
        }
    }

    private static SavedTerrainChunk biomeChunk(List<String> keys) {
        List<TerrainBoundarySignature> columns = new ArrayList<>(256);
        short[] samples = new short[keys.size() * 2];
        for (int index = 0; index < samples.length; index++) {
            samples[index] = (short) (index % keys.size());
        }
        BoundaryColumnGeometry geometry = new BoundaryColumnGeometry(0,
                List.of(new BoundaryColumnGeometry.Voxel("minecraft:stone", BoundaryColumnGeometry.Phase.SOLID, "", false)),
                new int[]{samples.length * 4}, new short[]{0});
        columns.add(new TerrainBoundarySignature(new TerrainBoundarySignature.Column(0, 0, 1, 1,
                OptionalInt.empty(), OptionalInt.empty()),
                new TerrainBoundarySignature.Samples(new TerrainBoundarySignature.VerticalLayout(0, 4, samples.length),
                        new TerrainBoundarySignature.BiomeEncoding(keys, samples)), geometry));
        for (int index = 1; index < 256; index++) {
            columns.add(null);
        }
        return new SavedTerrainChunk(0, 0, "minecraft:noise", columns);
    }

    private static byte[] legacyEncode(SavedTerrainChunk chunk, long activation, String epoch) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(bytes)))) {
            output.writeInt(0x4952544E);
            output.writeInt(1);
            output.writeInt(chunk.chunkX());
            output.writeInt(chunk.chunkZ());
            output.writeLong(activation);
            output.writeUTF(epoch);
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    output.writeBoolean(chunk.hasColumn(x, z));
                    if (!chunk.hasColumn(x, z)) {
                        continue;
                    }
                    TerrainBoundarySignature signature = chunk.column((chunk.chunkX() << 4) + x, (chunk.chunkZ() << 4) + z);
                    output.writeInt(signature.surfaceHeight());
                    output.writeInt(signature.oceanFloorHeight());
                    output.writeInt(signature.fluidHeight().orElse(-1));
                    output.writeInt(signature.upperCeilingDepth().orElse(-1));
                    TerrainBoundarySignature.VerticalLayout layout = signature.samples().layout();
                    output.writeInt(layout.minimumY());
                    output.writeInt(layout.sampleStep());
                    output.writeInt(layout.sampleCount());
                    for (int index = 0; index < layout.sampleCount(); index++) {
                        output.writeUTF(signature.biomeAtSample(index));
                    }
                    BoundaryColumnGeometry geometry = signature.geometry();
                    output.writeInt(geometry.minimumY());
                    output.writeInt(geometry.palette().size());
                    for (BoundaryColumnGeometry.Voxel voxel : geometry.palette()) {
                        output.writeUTF(voxel.stateKey());
                        output.writeByte(voxel.phase().ordinal());
                        output.writeUTF(voxel.fluidStateKey());
                        output.writeBoolean(voxel.protectedContent());
                    }
                    int[] ends = geometry.runEnds();
                    short[] indices = geometry.paletteIndices();
                    output.writeInt(ends.length);
                    for (int index = 0; index < ends.length; index++) {
                        output.writeInt(ends[index]);
                        output.writeShort(indices[index]);
                    }
                }
            }
        }
        return bytes.toByteArray();
    }

    private static SavedTerrainChunk chunk(boolean boundary) {
        List<TerrainBoundarySignature> columns = new ArrayList<>(256);
        List<BoundaryColumnGeometry.Voxel> palette = List.of(
                new BoundaryColumnGeometry.Voxel("minecraft:stone", BoundaryColumnGeometry.Phase.SOLID, "", false),
                new BoundaryColumnGeometry.Voxel("minecraft:water[level=0]", BoundaryColumnGeometry.Phase.FLUID,
                        "minecraft:water[level=0]", false),
                new BoundaryColumnGeometry.Voxel("minecraft:air", BoundaryColumnGeometry.Phase.AIR, "", false),
                new BoundaryColumnGeometry.Voxel("minecraft:oak_log[axis=y]", BoundaryColumnGeometry.Phase.SOLID, "", true));
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (boundary && x != 0 && x != 15 && z != 0 && z != 15) {
                    columns.add(null);
                    continue;
                }
                short[] biomes = new short[96];
                for (int index = 0; index < biomes.length; index++) {
                    biomes[index] = (short) ((index / 8 + x + z) % 3);
                }
                BoundaryColumnGeometry geometry = new BoundaryColumnGeometry(-64, palette,
                        new int[]{128 + z, 176 + x, 260 + z, 384}, new short[]{0, 1, 3, 2});
                columns.add(new TerrainBoundarySignature(new TerrainBoundarySignature.Column(-32 + x, 48 + z,
                        259 + z, 259 + z, OptionalInt.empty(), OptionalInt.of(4)),
                        new TerrainBoundarySignature.Samples(new TerrainBoundarySignature.VerticalLayout(-64, 4, 96),
                                new TerrainBoundarySignature.BiomeEncoding(
                                        List.of("minecraft:plains", "iris:水-é-\u0000", "iris:cave-\ud83c\udf32"), biomes)),
                        geometry));
            }
        }
        return new SavedTerrainChunk(-2, 3, "minecraft:noise", columns);
    }
}
