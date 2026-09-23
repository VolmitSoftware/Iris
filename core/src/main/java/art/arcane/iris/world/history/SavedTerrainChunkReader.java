package art.arcane.iris.world.history;

import art.arcane.volmlib.nativelib.terrain.NativeTerrainReceiptStorage;

import art.arcane.volmlib.util.nbt.io.NBTUtil;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.nbt.mca.MCABlockStateCodecSupport;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import art.arcane.volmlib.util.nbt.tag.ByteArrayTag;
import art.arcane.volmlib.util.nbt.tag.ListTag;
import art.arcane.volmlib.util.nbt.tag.LongArrayTag;
import art.arcane.volmlib.util.nbt.tag.LongTag;
import art.arcane.volmlib.util.nbt.tag.NumberTag;
import art.arcane.volmlib.util.nbt.tag.StringTag;
import art.arcane.volmlib.util.nbt.tag.Tag;
import net.jpountz.lz4.LZ4BlockInputStream;
import com.fulcrumgenomics.jlibdeflate.LibdeflateDecompressor;
import com.fulcrumgenomics.jlibdeflate.LibdeflateException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.DataFormatException;

final class SavedTerrainChunkReader {
    private static final int SECTOR_BYTES = 4096;
    private static final int MAX_CHUNK_BYTES = 64 * 1024 * 1024;
    private static final BoundaryColumnGeometry.Voxel AIR = new BoundaryColumnGeometry.Voxel(
            "minecraft:air", BoundaryColumnGeometry.Phase.AIR, "", false);

    private SavedTerrainChunkReader() {
    }

    static SavedTerrainChunk read(Path dimensionRoot, int chunkX, int chunkZ, int minimumY, int height)
            throws IOException {
        return readRoot(readRoot(dimensionRoot, chunkX, chunkZ), chunkX, chunkZ, minimumY, height);
    }

    static SavedTerrainChunk readNbt(byte[] bytes, int chunkX, int chunkZ, int minimumY, int height) throws IOException {
        if (bytes.length > MAX_CHUNK_BYTES) {
            throw new IOException("Native chunk exceeds its size limit");
        }
        Tag<?> parsed = NBTUtil.read(new ByteArrayInputStream(bytes), false).getTag();
        if (!(parsed instanceof CompoundTag root) || number(root, "xPos") != chunkX || number(root, "zPos") != chunkZ) {
            throw new IOException("Native chunk identity differs from requested coordinates");
        }
        return readRoot(root, chunkX, chunkZ, minimumY, height);
    }

    private static SavedTerrainChunk readRoot(CompoundTag root, int chunkX, int chunkZ, int minimumY, int height)
            throws IOException {
        String status = string(root, "Status");
        if (!SavedTerrainChunk.hasTerrain(status)) {
            throw new IOException("Saved chunk " + chunkX + "," + chunkZ + " has no terrain at native status " + status);
        }
        Tag<?> receiptTag = root.get(NativeTerrainReceiptStorage.NBT_KEY);
        if (root.get("ChunkBukkitValues") instanceof CompoundTag bukkitValues) {
            receiptTag = bukkitValues.get(NativeTerrainReceiptStorage.NBT_KEY);
        }
        if (receiptTag != null) {
            if (!(receiptTag instanceof ByteArrayTag bytes)) {
                throw new IOException("Native terrain receipt is not a byte array");
            }
            return NativeTerrainReceipt.restore(bytes.getValue(), status, chunkX, chunkZ, minimumY, height);
        }
        Tag<?> sectionTag = root.get("sections");
        if (!(sectionTag instanceof ListTag<?> sectionList)) {
            throw new IOException("Saved chunk has no current-format sections: " + chunkX + "," + chunkZ);
        }
        Map<Integer, Section> sections = new HashMap<>();
        for (Tag<?> entry : sectionList) {
            if (!(entry instanceof CompoundTag section)) {
                throw new IOException("Saved chunk section is not a compound");
            }
            int sectionY = number(section, "Y");
            if (sectionY * 16 < minimumY || sectionY * 16 >= (long) minimumY + height) {
                continue;
            }
            if (sections.put(sectionY, decodeSection(section, root.getInt("DataVersion") >= 5023
                    ? MCABlockStateCodecSupport.Format.LOWERCASE : MCABlockStateCodecSupport.Format.CAPITALIZED)) != null) {
                throw new IOException("Saved chunk contains duplicate section " + sectionY);
            }
        }
        return SavedTerrainChunk.captureBoundary(chunkX, chunkZ, minimumY, height, status, new SavedTerrainChunk.VoxelSource() {
            @Override
            public BoundaryColumnGeometry.Voxel voxel(int x, int y, int z) throws IOException {
                Section section = requireSection(sections, y);
                return section.blocks().get((Math.floorMod(y, 16) << 8) | (z << 4) | x);
            }

            @Override
            public String biome(int x, int y, int z) throws IOException {
                Section section = requireSection(sections, y);
                return section.biomes().get(((Math.floorMod(y, 16) >> 2) << 4) | ((z >> 2) << 2) | (x >> 2));
            }
        });
    }

    static void verifyCheckpoint(Path dimensionRoot, int chunkX, int chunkZ, String status, byte[] expectedReceipt, long expectedStructureActivation)
            throws IOException {
        CompoundTag root = readRoot(dimensionRoot, chunkX, chunkZ);
        if (SavedTerrainChunk.statusRank(string(root, "Status")) < SavedTerrainChunk.statusRank(status)) {
            throw new IOException("Native terrain checkpoint did not persist stage " + status + " for " + chunkX + "," + chunkZ);
        }
        if (expectedStructureActivation > 0 && structureActivation(root) != expectedStructureActivation) {
            throw new IOException("Native structure checkpoint differs for " + chunkX + "," + chunkZ);
        }
        if (expectedReceipt != null && !Arrays.equals(expectedReceipt, receipt(root))) {
            throw new IOException("Native terrain receipt checkpoint differs for " + chunkX + "," + chunkZ);
        }
    }

    static byte[] readReceipt(Path dimensionRoot, int chunkX, int chunkZ) throws IOException {
        return receipt(readRoot(dimensionRoot, chunkX, chunkZ));
    }

    private static long structureActivation(CompoundTag root) throws IOException {
        Tag<?> activation = root.get(NativeTerrainReceiptStorage.STRUCTURE_ACTIVATION_KEY);
        if (root.get("ChunkBukkitValues") instanceof CompoundTag values) {
            activation = values.get(NativeTerrainReceiptStorage.STRUCTURE_ACTIVATION_KEY);
        }
        if (activation == null) {
            return 0;
        }
        if (!(activation instanceof LongTag value) || value.getValue() <= 0) {
            throw new IOException("Invalid native structure activation");
        }
        return value.getValue();
    }

    private static byte[] receipt(CompoundTag root) throws IOException {
        Tag<?> receipt = root.get(NativeTerrainReceiptStorage.NBT_KEY);
        if (root.get("ChunkBukkitValues") instanceof CompoundTag values) {
            receipt = values.get(NativeTerrainReceiptStorage.NBT_KEY);
        }
        if (receipt == null) {
            return null;
        }
        if (!(receipt instanceof ByteArrayTag bytes)) {
            throw new IOException("Native terrain receipt is not a byte array");
        }
        return bytes.getValue();
    }

    static String readStatus(Path dimensionRoot, int chunkX, int chunkZ) throws IOException {
        try (StatusReader reader = new StatusReader(dimensionRoot)) {
            return reader.readStatus(chunkX, chunkZ);
        }
    }

    private static Section requireSection(Map<Integer, Section> sections, int y) throws IOException {
        Section section = sections.get(Math.floorDiv(y, 16));
        if (section == null) {
            throw new IOException("Saved terrain is missing section " + Math.floorDiv(y, 16));
        }
        return section;
    }

    private static Section decodeSection(CompoundTag section, MCABlockStateCodecSupport.Format format) throws IOException {
        CompoundTag blocks = compound(section, "block_states");
        CompoundTag biomes = compound(section, "biomes");
        ListTag<?> blockPalette = list(blocks, "palette");
        List<BoundaryColumnGeometry.Voxel> decodedBlocks = new ArrayList<>(blockPalette.size());
        for (Tag<?> entry : blockPalette) {
            Tag<?> state = entry;
            if (format == MCABlockStateCodecSupport.Format.LOWERCASE
                    && state instanceof CompoundTag wrapped && wrapped.size() == 1 && wrapped.containsKey("")) {
                state = wrapped.get("");
            }
            if (format == MCABlockStateCodecSupport.Format.LOWERCASE && state instanceof StringTag name) {
                NativeBlockState resolved = IrisPlatforms.get().registries().blockOrNull(name.getValue());
                if (resolved == null) {
                    throw new IOException("Saved block state cannot be resolved: " + name.getValue());
                }
                decodedBlocks.add(decodeBlock(MCABlockStateCodecSupport.encodeBlockState(
                        resolved.key(), name.getValue(), format), format));
            } else if (state instanceof CompoundTag compound) {
                decodedBlocks.add(decodeBlock(compound, format));
            } else {
                throw new IOException("Saved block palette entry is not a block state");
            }
        }
        ListTag<?> biomePalette = list(biomes, "palette");
        List<String> decodedBiomes = new ArrayList<>(biomePalette.size());
        for (Tag<?> entry : biomePalette) {
            if (!(entry instanceof StringTag biome) || biome.getValue().isBlank()) {
                throw new IOException("Saved biome palette entry is not a resource key");
            }
            decodedBiomes.add(biome.getValue());
        }
        return new Section(new Palette<>(decodedBlocks, packedData(blocks), 4, 4096),
                new Palette<>(decodedBiomes, packedData(biomes), 1, 64));
    }

    private static BoundaryColumnGeometry.Voxel decodeBlock(CompoundTag state, MCABlockStateCodecSupport.Format format) throws IOException {
        String name = string(state, format.nameKey());
        TreeMap<String, String> properties = new TreeMap<>();
        if (state.get(format.propertiesKey()) instanceof CompoundTag values) {
            for (Map.Entry<String, Tag<?>> property : values) {
                if (!(property.getValue() instanceof StringTag value)) {
                    throw new IOException("Saved block property is not a string");
                }
                properties.put(property.getKey(), value.getValue());
            }
        }
        return decodeBlock(name, properties);
    }

    private static BoundaryColumnGeometry.Voxel decodeBlock(String name, Map<String, String> properties) throws IOException {
        if (name.isBlank()) {
            throw new IOException("Saved block state has no resource key");
        }
        String stateKey = stateKey(name, properties);
        if (name.equals("minecraft:air") || name.equals("minecraft:cave_air") || name.equals("minecraft:void_air")) {
            return name.equals("minecraft:air") ? AIR
                    : new BoundaryColumnGeometry.Voxel(stateKey, BoundaryColumnGeometry.Phase.AIR, "", false);
        }
        boolean liquid = name.equals("minecraft:water") || name.equals("minecraft:lava");
        String fluidKey = liquid ? stateKey : "true".equals(properties.get("waterlogged"))
                ? "minecraft:water[level=0]" : "";
        return new BoundaryColumnGeometry.Voxel(stateKey,
                liquid ? BoundaryColumnGeometry.Phase.FLUID : BoundaryColumnGeometry.Phase.SOLID, fluidKey, false);
    }

    private static String stateKey(String name, Map<String, String> properties) {
        if (properties.isEmpty()) {
            return name;
        }
        StringBuilder encoded = new StringBuilder(name).append('[');
        for (Map.Entry<String, String> property : properties.entrySet()) {
            if (encoded.charAt(encoded.length() - 1) != '[') {
                encoded.append(',');
            }
            encoded.append(property.getKey()).append('=').append(property.getValue());
        }
        return encoded.append(']').toString();
    }

    private static CompoundTag readRoot(Path dimensionRoot, int chunkX, int chunkZ) throws IOException {
        try (StatusReader reader = new StatusReader(dimensionRoot)) {
            return reader.readRoot(chunkX, chunkZ);
        }
    }

    private static long[] packedData(CompoundTag tag) throws IOException {
        Tag<?> data = tag.get("data");
        if (data == null) {
            return new long[0];
        }
        if (!(data instanceof LongArrayTag longs)) {
            throw new IOException("Saved palette data is not a long array");
        }
        return longs.getValue();
    }

    private static CompoundTag compound(CompoundTag tag, String key) throws IOException {
        if (!(tag.get(key) instanceof CompoundTag value)) {
            throw new IOException("Saved chunk field " + key + " is not a compound");
        }
        return value;
    }

    private static ListTag<?> list(CompoundTag tag, String key) throws IOException {
        if (!(tag.get(key) instanceof ListTag<?> value)) {
            throw new IOException("Saved chunk field " + key + " is not a list");
        }
        return value;
    }

    private static String string(CompoundTag tag, String key) throws IOException {
        if (!(tag.get(key) instanceof StringTag value) || value.getValue().isBlank()) {
            throw new IOException("Saved chunk field " + key + " is not a nonempty string");
        }
        return value.getValue();
    }

    private static int number(CompoundTag tag, String key) throws IOException {
        if (!(tag.get(key) instanceof NumberTag<?> value)) {
            throw new IOException("Saved chunk field " + key + " is not a number");
        }
        return value.asInt();
    }

    static final class StatusReader implements AutoCloseable {
        private static final boolean NATIVE_AVAILABLE = nativeAvailable();

        private final Path regionDirectory;
        private final Inflater inflater = new Inflater();
        private final LibdeflateDecompressor nativeInflater = NATIVE_AVAILABLE ? new LibdeflateDecompressor() : null;
        private byte[] compressed = new byte[65536];
        private byte[] decoded = new byte[65536];
        private Path region;
        private RandomAccessFile input;
        private Object regionFileKey;
        private int regionX;
        private int regionZ;
        private boolean closed;

        StatusReader(Path dimensionRoot) {
            regionDirectory = dimensionRoot.toAbsolutePath().normalize().resolve("region");
        }

        SavedTerrainChunk readChunk(int chunkX, int chunkZ, int minimumY, int height) throws IOException {
            return SavedTerrainChunkReader.readRoot(readRoot(chunkX, chunkZ), chunkX, chunkZ, minimumY, height);
        }

        String readStatus(int chunkX, int chunkZ) throws IOException {
            try {
                Payload payload = readPayload(chunkX, chunkZ);
                return SavedTerrainStatusNbt.read(payload.bytes(), payload.offset(), payload.length(), chunkX, chunkZ);
            } catch (IOException exception) {
                throw new IOException("Cannot read saved chunk status at " + chunkX + "," + chunkZ
                        + " in " + regionDirectory, exception);
            }
        }

        String readStatus(byte[] bytes, SavedTerrainRegionSnapshot.Payload stored, int chunkX, int chunkZ)
                throws IOException {
            if (closed) {
                throw new IOException("Saved chunk status reader is closed");
            }
            if (stored.external()) {
                return readStatus(chunkX, chunkZ);
            }
            try {
                Payload payload = decode(stored.compression(), bytes, stored.offset(), stored.length());
                return SavedTerrainStatusNbt.read(payload.bytes(), payload.offset(), payload.length(), chunkX, chunkZ);
            } catch (IOException exception) {
                throw new IOException("Cannot read saved chunk status at " + chunkX + "," + chunkZ
                        + " in " + regionDirectory, exception);
            }
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            try {
                if (input != null) {
                    input.close();
                }
            } finally {
                inflater.end();
                if (nativeInflater != null) {
                    nativeInflater.close();
                }
            }
        }

        private CompoundTag readRoot(int chunkX, int chunkZ) throws IOException {
            try {
                Payload payload = readPayload(chunkX, chunkZ);
                Tag<?> tag = NBTUtil.read(
                        new ByteArrayInputStream(payload.bytes(), payload.offset(), payload.length()), false).getTag();
                if (!(tag instanceof CompoundTag root)) {
                    throw new IOException("Saved chunk root is not a compound");
                }
                if (number(root, "xPos") != chunkX || number(root, "zPos") != chunkZ) {
                    throw new IOException("Saved chunk coordinates do not match its region allocation");
                }
                return root;
            } catch (IllegalArgumentException | ClassCastException exception) {
                throw new IOException("Invalid saved chunk data at " + chunkX + "," + chunkZ, exception);
            }
        }

        private Payload readPayload(int chunkX, int chunkZ) throws IOException {
            openRegion(chunkX, chunkZ);
            int slot = Math.floorMod(chunkZ, 32) * 32 + Math.floorMod(chunkX, 32);
            input.seek((long) slot * 4);
            int allocation = input.readInt();
            long offset = (long) (allocation >>> 8) * SECTOR_BYTES;
            int sectors = allocation & 255;
            if (offset < 2L * SECTOR_BYTES || sectors == 0 || offset + 5 > input.length()) {
                throw new IOException("Saved chunk allocation is unavailable: " + chunkX + "," + chunkZ);
            }
            input.seek(offset);
            int length = input.readInt();
            int compression = input.readUnsignedByte();
            int compressedLength;
            if ((compression & 128) != 0) {
                if (length != 1) {
                    throw new IOException("External saved chunk has an invalid allocation length");
                }
                compressedLength = readExternal(chunkX, chunkZ);
                compression &= 127;
            } else {
                if (length < 1 || length > MAX_CHUNK_BYTES || (long) length + 4 > (long) sectors * SECTOR_BYTES
                        || offset + 4L + length > input.length()) {
                    throw new IOException("Saved chunk payload is truncated or too large: " + chunkX + "," + chunkZ);
                }
                compressedLength = length - 1;
                ensureCompressedCapacity(compressedLength);
                input.readFully(compressed, 0, compressedLength);
            }
            input.seek((long) slot * 4);
            if (input.readInt() != allocation) {
                throw new IOException("Saved chunk allocation changed during boundary capture: " + chunkX + "," + chunkZ);
            }
            return decode(compression, compressed, 0, compressedLength);
        }

        private void openRegion(int chunkX, int chunkZ) throws IOException {
            if (closed) {
                throw new IOException("Saved chunk status reader is closed");
            }
            int nextRegionX = Math.floorDiv(chunkX, 32);
            int nextRegionZ = Math.floorDiv(chunkZ, 32);
            boolean sameRegion = input != null && regionX == nextRegionX && regionZ == nextRegionZ;
            Path nextRegion = sameRegion ? region
                    : regionDirectory.resolve("r." + nextRegionX + "." + nextRegionZ + ".mca");
            BasicFileAttributes attributes = Files.readAttributes(nextRegion, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile()) {
                throw new IOException("Saved chunk region is unavailable: " + nextRegion);
            }
            Object nextFileKey = attributes.fileKey();
            if (sameRegion && nextFileKey != null && nextFileKey.equals(regionFileKey)) {
                return;
            }
            if (input != null) {
                input.close();
                input = null;
            }
            input = new RandomAccessFile(nextRegion.toFile(), "r");
            region = nextRegion;
            regionFileKey = nextFileKey;
            regionX = nextRegionX;
            regionZ = nextRegionZ;
        }

        private int readExternal(int chunkX, int chunkZ) throws IOException {
            Path external = regionDirectory.resolve("c." + chunkX + "." + chunkZ + ".mcc");
            if (!Files.isRegularFile(external, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("External saved chunk is unavailable: " + external);
            }
            long length = Files.size(external);
            if (length > MAX_CHUNK_BYTES) {
                throw new IOException("External saved chunk is too large: " + external);
            }
            int size = (int) length;
            ensureCompressedCapacity(size);
            try (InputStream externalInput = Files.newInputStream(external)) {
                if (externalInput.readNBytes(compressed, 0, size) != size || externalInput.read() != -1) {
                    throw new IOException("External saved chunk length changed during capture: " + external);
                }
            }
            return size;
        }

        private void ensureCompressedCapacity(int size) {
            if (size > compressed.length) {
                compressed = new byte[Math.max(size, Math.min(MAX_CHUNK_BYTES, compressed.length * 2))];
            }
        }

        private Payload decode(int compression, byte[] bytes, int offset, int length) throws IOException {
            if (compression == 2) {
                int size = inflate(bytes, offset, length);
                return new Payload(decoded, 0, size);
            }
            if (compression == 3) {
                return new Payload(bytes, offset, length);
            }
            InputStream encoded = new ByteArrayInputStream(bytes, offset, length);
            try (InputStream stream = switch (compression) {
                case 1 -> new GZIPInputStream(encoded);
                case 4 -> new LZ4BlockInputStream(encoded);
                default -> throw new IOException("Unsupported saved chunk compression " + compression);
            }) {
                int size = 0;
                int count;
                do {
                    ensureDecodedCapacity(size);
                    count = stream.read(decoded, size, decoded.length - size);
                    if (count > 0) {
                        size += count;
                        checkDecodedSize(size);
                    }
                } while (count != -1);
                return new Payload(decoded, 0, size);
            }
        }

        private int inflate(byte[] bytes, int offset, int length) throws IOException {
            if (nativeInflater != null) {
                try {
                    int size = nativeInflater.zlibDecompressEx(bytes, offset, length, decoded, 0, decoded.length)
                            .outputBytesProduced();
                    checkDecodedSize(size);
                    return size;
                } catch (LibdeflateException exception) {
                    return inflateJava(bytes, offset, length);
                }
            }
            return inflateJava(bytes, offset, length);
        }

        private static boolean nativeAvailable() {
            try (LibdeflateDecompressor ignored = new LibdeflateDecompressor()) {
                return true;
            } catch (LinkageError | SecurityException exception) {
                return false;
            }
        }

        private int inflateJava(byte[] bytes, int offset, int length) throws IOException {
            inflater.reset();
            inflater.setInput(bytes, offset, length);
            int size = 0;
            try {
                while (!inflater.finished()) {
                    ensureDecodedCapacity(size);
                    int count = inflater.inflate(decoded, size, decoded.length - size);
                    size += count;
                    checkDecodedSize(size);
                    if (count == 0 && !inflater.finished()) {
                        throw new IOException("Saved chunk zlib payload is truncated or requires a dictionary");
                    }
                }
            } catch (DataFormatException exception) {
                throw new IOException("Invalid saved chunk zlib payload", exception);
            }
            return size;
        }

        private void ensureDecodedCapacity(int size) throws IOException {
            checkDecodedSize(size);
            if (size == decoded.length) {
                decoded = Arrays.copyOf(decoded, Math.min(MAX_CHUNK_BYTES + 1, decoded.length * 2));
            }
        }

        private void checkDecodedSize(int size) throws IOException {
            if (size > MAX_CHUNK_BYTES) {
                throw new IOException("Saved chunk decompressed payload exceeds the size limit");
            }
        }
    }

    private record Payload(byte[] bytes, int offset, int length) {
    }

    private record Section(Palette<BoundaryColumnGeometry.Voxel> blocks, Palette<String> biomes) {
    }

    private static final class Palette<T> {
        private final List<T> values;
        private final long[] data;
        private final int bits;
        private final int valuesPerLong;
        private final long mask;

        private Palette(List<T> values, long[] data, int minimumBits, int count) throws IOException {
            if (values.isEmpty() || values.size() > count) {
                throw new IOException("Saved palette size is invalid");
            }
            this.values = List.copyOf(values);
            this.data = data;
            bits = Math.max(minimumBits, 32 - Integer.numberOfLeadingZeros(values.size() - 1));
            valuesPerLong = 64 / bits;
            mask = (1L << bits) - 1;
            int expected = values.size() == 1 ? 0 : Math.ceilDiv(count, valuesPerLong);
            if (data.length != expected) {
                throw new IOException("Saved palette data length " + data.length + " differs from " + expected);
            }
        }

        private T get(int index) throws IOException {
            int paletteIndex = values.size() == 1 ? 0
                    : (int) ((data[index / valuesPerLong] >>> ((index % valuesPerLong) * bits)) & mask);
            if (paletteIndex >= values.size()) {
                throw new IOException("Saved block or biome palette index is outside its palette");
            }
            return values.get(paletteIndex);
        }
    }
}
