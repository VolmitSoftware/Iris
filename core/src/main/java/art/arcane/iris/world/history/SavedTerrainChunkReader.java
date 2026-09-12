package art.arcane.iris.world.history;

import art.arcane.volmlib.util.nbt.io.NBTUtil;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import art.arcane.volmlib.util.nbt.tag.ByteArrayTag;
import art.arcane.volmlib.util.nbt.tag.ListTag;
import art.arcane.volmlib.util.nbt.tag.LongArrayTag;
import art.arcane.volmlib.util.nbt.tag.LongTag;
import art.arcane.volmlib.util.nbt.tag.NumberTag;
import art.arcane.volmlib.util.nbt.tag.StringTag;
import art.arcane.volmlib.util.nbt.tag.Tag;
import net.jpountz.lz4.LZ4BlockInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

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
        Tag<?> receiptTag = root.get(NativeTerrainReceipt.NBT_KEY);
        if (root.get("ChunkBukkitValues") instanceof CompoundTag bukkitValues) {
            receiptTag = bukkitValues.get(NativeTerrainReceipt.NBT_KEY);
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
            if (sections.put(sectionY, decodeSection(section)) != null) {
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
        Tag<?> activation = root.get(NativeTerrainReceipt.STRUCTURE_ACTIVATION_KEY);
        if (root.get("ChunkBukkitValues") instanceof CompoundTag values) {
            activation = values.get(NativeTerrainReceipt.STRUCTURE_ACTIVATION_KEY);
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
        Tag<?> receipt = root.get(NativeTerrainReceipt.NBT_KEY);
        if (root.get("ChunkBukkitValues") instanceof CompoundTag values) {
            receipt = values.get(NativeTerrainReceipt.NBT_KEY);
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
        return string(readRoot(dimensionRoot, chunkX, chunkZ), "Status");
    }

    private static Section requireSection(Map<Integer, Section> sections, int y) throws IOException {
        Section section = sections.get(Math.floorDiv(y, 16));
        if (section == null) {
            throw new IOException("Saved terrain is missing section " + Math.floorDiv(y, 16));
        }
        return section;
    }

    private static Section decodeSection(CompoundTag section) throws IOException {
        CompoundTag blocks = compound(section, "block_states");
        CompoundTag biomes = compound(section, "biomes");
        ListTag<?> blockPalette = list(blocks, "palette");
        List<BoundaryColumnGeometry.Voxel> decodedBlocks = new ArrayList<>(blockPalette.size());
        for (Tag<?> entry : blockPalette) {
            if (!(entry instanceof CompoundTag state)) {
                throw new IOException("Saved block palette entry is not a compound");
            }
            decodedBlocks.add(decodeBlock(state));
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

    private static BoundaryColumnGeometry.Voxel decodeBlock(CompoundTag state) throws IOException {
        String name = string(state, "Name");
        TreeMap<String, String> properties = new TreeMap<>();
        if (state.get("Properties") instanceof CompoundTag values) {
            for (Map.Entry<String, Tag<?>> property : values) {
                if (!(property.getValue() instanceof StringTag value)) {
                    throw new IOException("Saved block property is not a string");
                }
                properties.put(property.getKey(), value.getValue());
            }
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
        Path regionDirectory = dimensionRoot.toAbsolutePath().normalize().resolve("region");
        Path region = regionDirectory.resolve("r." + Math.floorDiv(chunkX, 32) + "."
                + Math.floorDiv(chunkZ, 32) + ".mca");
        if (!Files.isRegularFile(region, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Saved chunk region is unavailable: " + region);
        }
        byte[] compressed;
        int compression;
        try (RandomAccessFile input = new RandomAccessFile(region.toFile(), "r")) {
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
            compression = input.readUnsignedByte();
            if ((compression & 128) != 0) {
                if (length != 1) {
                    throw new IOException("External saved chunk has an invalid allocation length");
                }
                Path external = regionDirectory.resolve("c." + chunkX + "." + chunkZ + ".mcc");
                if (!Files.isRegularFile(external, LinkOption.NOFOLLOW_LINKS)
                        || Files.size(external) > MAX_CHUNK_BYTES) {
                    throw new IOException("External saved chunk is unavailable or too large: " + external);
                }
                compressed = Files.readAllBytes(external);
                compression &= 127;
            } else {
                if (length < 1 || length > MAX_CHUNK_BYTES || (long) length + 4 > (long) sectors * SECTOR_BYTES
                        || offset + 4L + length > input.length()) {
                    throw new IOException("Saved chunk payload is truncated or too large: " + chunkX + "," + chunkZ);
                }
                compressed = new byte[length - 1];
                input.readFully(compressed);
            }
            input.seek((long) slot * 4);
            if (input.readInt() != allocation) {
                throw new IOException("Saved chunk allocation changed during boundary capture: " + chunkX + "," + chunkZ);
            }
        }
        try (InputStream decoded = decompress(compressed, compression)) {
            byte[] bytes = decoded.readNBytes(MAX_CHUNK_BYTES + 1);
            if (bytes.length > MAX_CHUNK_BYTES) {
                throw new IOException("Saved chunk decompressed payload exceeds the size limit");
            }
            Tag<?> tag = NBTUtil.read(new ByteArrayInputStream(bytes), false).getTag();
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

    private static InputStream decompress(byte[] encoded, int compression) throws IOException {
        InputStream input = new ByteArrayInputStream(encoded);
        return switch (compression) {
            case 1 -> new GZIPInputStream(input);
            case 2 -> new InflaterInputStream(input);
            case 3 -> input;
            case 4 -> new LZ4BlockInputStream(input);
            default -> throw new IOException("Unsupported saved chunk compression " + compression);
        };
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
