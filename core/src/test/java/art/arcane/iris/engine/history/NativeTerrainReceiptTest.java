package art.arcane.iris.engine.history;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.OptionalInt;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.assertEquals;

public class NativeTerrainReceiptTest {
    @Test
    public void preservesCompleteAndBoundaryReceiptBytes() throws Exception {
        for (boolean boundary : new boolean[]{false, true}) {
            SavedTerrainChunk chunk = chunk(boundary);
            String epoch = "epoch-\u0000-é-水-\ud83c\udf32";
            byte[] encoded = NativeTerrainReceipt.encode(chunk, 7, epoch);
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
