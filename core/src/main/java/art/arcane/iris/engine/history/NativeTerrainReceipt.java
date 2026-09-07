package art.arcane.iris.engine.history;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class NativeTerrainReceipt {
    public static final String STRUCTURE_ACTIVATION_KEY = "iris:structure_activation";
    public static final String NBT_KEY = "iris:natural_terrain";
    private static final int MAGIC = 0x4952544E;
    private static final int VERSION = 1;
    private static final int MAXIMUM_BYTES = 64 * 1024 * 1024;

    private NativeTerrainReceipt() {
    }

    public static byte[] encode(SavedTerrainChunk chunk, long activationId, String epochId) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        UtfCache strings = new UtfCache();
        try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(bytes)))) {
            output.writeInt(MAGIC);
            output.writeInt(VERSION);
            output.writeInt(chunk.chunkX());
            output.writeInt(chunk.chunkZ());
            output.writeLong(activationId);
            output.writeUTF(epochId);
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    output.writeBoolean(chunk.hasColumn(x, z));
                    if (!chunk.hasColumn(x, z)) {
                        continue;
                    }
                    writeColumn(output, chunk.column((chunk.chunkX() << 4) + x, (chunk.chunkZ() << 4) + z), strings);
                }
            }
        }
        return bytes.toByteArray();
    }

    public static SavedTerrainChunk restore(byte[] bytes, String nativeStatus, int chunkX, int chunkZ,
                                            int minimumY, int height) throws IOException {
        SavedTerrainChunk terrain = decode(bytes, nativeStatus).terrain();
        if (terrain.chunkX() != chunkX || terrain.chunkZ() != chunkZ) {
            throw new IOException("Native terrain receipt coordinates do not match its chunk");
        }
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (!terrain.hasColumn(x, z)) {
                    if (x == 0 || x == 15 || z == 0 || z == 15) {
                        throw new IOException("Native terrain receipt is missing an edge column");
                    }
                    continue;
                }
                BoundaryColumnGeometry geometry = terrain.column((chunkX << 4) + x, (chunkZ << 4) + z).geometry();
                if (geometry.minimumY() != minimumY || geometry.height() != height) {
                    throw new IOException("Native terrain receipt height does not match its dimension");
                }
            }
        }
        return terrain;
    }

    public static Decoded decode(byte[] bytes, String nativeStatus) throws IOException {
        if (bytes.length > MAXIMUM_BYTES) {
            throw new IOException("Native terrain receipt exceeds its size limit");
        }
        byte[] decoded;
        try (GZIPInputStream compressed = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            decoded = compressed.readNBytes(MAXIMUM_BYTES + 1);
        }
        if (decoded.length > MAXIMUM_BYTES) {
            throw new IOException("Native terrain receipt expands beyond its size limit");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(decoded))) {
            if (input.readInt() != MAGIC || input.readInt() != VERSION) {
                throw new IOException("Invalid native terrain receipt format");
            }
            int chunkX = input.readInt();
            int chunkZ = input.readInt();
            long activation = input.readLong();
            String epoch = input.readUTF();
            if (activation < 0 || epoch.isBlank()) {
                throw new IOException("Invalid native terrain receipt identity");
            }
            List<TerrainBoundarySignature> columns = new ArrayList<>(256);
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    if (!input.readBoolean()) {
                        columns.add(null);
                        continue;
                    }
                    columns.add(readColumn(input, Math.addExact(Math.multiplyExact(chunkX, 16), x),
                            Math.addExact(Math.multiplyExact(chunkZ, 16), z)));
                }
            }
            if (input.read() != -1) {
                throw new IOException("Trailing native terrain receipt data");
            }
            return new Decoded(new SavedTerrainChunk(chunkX, chunkZ, nativeStatus, columns), activation, epoch);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new IOException("Invalid native terrain receipt", exception);
        }
    }

    private static void writeColumn(DataOutputStream output, TerrainBoundarySignature signature, UtfCache strings) throws IOException {
        output.writeInt(signature.surfaceHeight());
        output.writeInt(signature.oceanFloorHeight());
        output.writeInt(signature.fluidHeight().orElse(-1));
        output.writeInt(signature.upperCeilingDepth().orElse(-1));
        TerrainBoundarySignature.VerticalLayout layout = signature.samples().layout();
        output.writeInt(layout.minimumY());
        output.writeInt(layout.sampleStep());
        output.writeInt(layout.sampleCount());
        for (int index = 0; index < layout.sampleCount(); index++) {
            strings.write(output, signature.biomeAtSample(index));
        }
        BoundaryColumnGeometry geometry = signature.geometry();
        output.writeInt(geometry.minimumY());
        output.writeInt(geometry.palette().size());
        for (BoundaryColumnGeometry.Voxel voxel : geometry.palette()) {
            strings.write(output, voxel.stateKey());
            output.writeByte(voxel.phase().ordinal());
            strings.write(output, voxel.fluidStateKey());
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

    private static TerrainBoundarySignature readColumn(DataInputStream input, int x, int z) throws IOException {
        int surface = input.readInt();
        int floor = input.readInt();
        int fluid = input.readInt();
        int ceiling = input.readInt();
        int biomeMinimum = input.readInt();
        int step = input.readInt();
        int count = boundedCount(input.readInt());
        List<String> biomes = new ArrayList<>();
        short[] biomeIndices = new short[count];
        for (int index = 0; index < count; index++) {
            String biome = input.readUTF();
            int paletteIndex = biomes.indexOf(biome);
            if (paletteIndex < 0) {
                paletteIndex = biomes.size();
                biomes.add(biome);
            }
            biomeIndices[index] = (short) paletteIndex;
        }
        int minimumY = input.readInt();
        int paletteCount = boundedCount(input.readInt());
        List<BoundaryColumnGeometry.Voxel> palette = new ArrayList<>(paletteCount);
        for (int index = 0; index < paletteCount; index++) {
            String state = input.readUTF();
            int phase = input.readUnsignedByte();
            if (phase >= BoundaryColumnGeometry.Phase.values().length) {
                throw new IOException("Invalid terrain receipt voxel phase");
            }
            palette.add(new BoundaryColumnGeometry.Voxel(state, BoundaryColumnGeometry.Phase.values()[phase],
                    input.readUTF(), input.readBoolean()));
        }
        int runs = boundedCount(input.readInt());
        int[] ends = new int[runs];
        short[] indices = new short[runs];
        for (int index = 0; index < runs; index++) {
            ends[index] = input.readInt();
            indices[index] = input.readShort();
        }
        return new TerrainBoundarySignature(new TerrainBoundarySignature.Column(x, z, surface, floor,
                fluid < 0 ? OptionalInt.empty() : OptionalInt.of(fluid),
                ceiling < 0 ? OptionalInt.empty() : OptionalInt.of(ceiling)),
                new TerrainBoundarySignature.Samples(new TerrainBoundarySignature.VerticalLayout(biomeMinimum, step, count),
                        new TerrainBoundarySignature.BiomeEncoding(biomes, biomeIndices)),
                new BoundaryColumnGeometry(minimumY, palette, ends, indices));
    }

    private static int boundedCount(int count) throws IOException {
        if (count < 0 || count > 4096) {
            throw new IOException("Invalid native terrain receipt collection size");
        }
        return count;
    }

    public record Decoded(SavedTerrainChunk terrain, long activationId, String epochId) {
    }

    private static final class UtfCache {
        private static final int MAXIMUM_ENTRIES = 256;
        private static final int MAXIMUM_ENCODED_BYTES = 64 * 1024;

        private final Map<String, byte[]> encoded = new HashMap<>();
        private final ByteArrayOutputStream scratch = new ByteArrayOutputStream();
        private final DataOutputStream encoder = new DataOutputStream(scratch);
        private int encodedBytes;
        private boolean entryLimitReached;

        private void write(DataOutputStream output, String value) throws IOException {
            if (entryLimitReached) {
                output.writeUTF(value);
                return;
            }
            byte[] cached = encoded.get(value);
            if (cached != null) {
                output.write(cached);
                return;
            }
            if (encoded.size() >= MAXIMUM_ENTRIES) {
                entryLimitReached = true;
                encoded.clear();
                output.writeUTF(value);
                return;
            }
            if (value.length() > MAXIMUM_ENCODED_BYTES - encodedBytes - Short.BYTES) {
                output.writeUTF(value);
                return;
            }
            scratch.reset();
            encoder.writeUTF(value);
            if (scratch.size() <= MAXIMUM_ENCODED_BYTES - encodedBytes) {
                byte[] bytes = scratch.toByteArray();
                encoded.put(value, bytes);
                encodedBytes += bytes.length;
            }
            scratch.writeTo(output);
        }
    }
}
