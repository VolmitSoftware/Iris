package art.arcane.iris.probe.fidelity;

import art.arcane.volmlib.util.nbt.io.NBTDeserializer;
import art.arcane.volmlib.util.nbt.io.NamedTag;
import art.arcane.volmlib.util.nbt.mca.CompressionType;
import art.arcane.volmlib.util.nbt.mca.MCAFileSupport;
import art.arcane.volmlib.util.nbt.mca.MCAUtilSupport;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import art.arcane.volmlib.util.nbt.tag.ListTag;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

public final class RegionFileFidelitySource implements FidelitySource {
    private static final int CHUNK_WIDTH = 16;
    private static final int SECTION_VOLUME = 4096;
    private static final long SECTOR_BYTES = 4096L;
    private static final int EXTERNAL_PAYLOAD = 0x80;
    private static final Set<String> FULL_STATUS = Set.of("minecraft:full", "full");

    private final Path regionDirectory;
    private final int minY;
    private final int maxY;

    public RegionFileFidelitySource(Path regionDirectory, int minY, int maxY) {
        this.regionDirectory = Objects.requireNonNull(regionDirectory, "regionDirectory");
        if (maxY <= minY) {
            throw new IllegalArgumentException("Region height range [" + minY + ", " + maxY + ") is empty");
        }
        this.minY = minY;
        this.maxY = maxY;
    }

    @Override
    public String label() {
        return regionDirectory.toString();
    }

    @Override
    public int minY() {
        return minY;
    }

    @Override
    public int maxY() {
        return maxY;
    }

    @Override
    public FidelityChunk sample(int chunkX, int chunkZ) throws IOException {
        CompoundTag root = readChunk(chunkX, chunkZ);
        String status = root.getString("Status");
        if (!FULL_STATUS.contains(status)) {
            throw new IllegalStateException("Chunk " + chunkX + "," + chunkZ + " in " + regionDirectory
                    + " has status '" + status + "', not full");
        }
        String[] blocks = new String[CHUNK_WIDTH * CHUNK_WIDTH * (maxY - minY)];
        Arrays.fill(blocks, FidelityBlocks.AIR);
        ListTag<?> sections = root.getListTag("sections");
        if (sections != null && sections.size() > 0) {
            for (CompoundTag section : sections.asCompoundTagList()) {
                decodeSection(section, blocks, chunkX, chunkZ);
            }
        }
        return new RegionChunk(blocks);
    }

    @Override
    public void close() {
    }

    private CompoundTag readChunk(int chunkX, int chunkZ) throws IOException {
        Path file = regionDirectory.resolve(MCAUtilSupport.createNameFromChunkLocation(chunkX, chunkZ));
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException("Region file " + file + " for chunk " + chunkX + "," + chunkZ + " does not exist");
        }
        byte[] payload;
        int compression;
        try (RandomAccessFile region = new RandomAccessFile(file.toFile(), "r")) {
            region.seek(MCAFileSupport.getChunkIndex(chunkX, chunkZ) * 4L);
            int location = region.readInt();
            int sector = location >>> 8;
            if (sector == 0 || (location & 0xFF) == 0) {
                throw new IllegalStateException("Chunk " + chunkX + "," + chunkZ + " is not stored in " + file);
            }
            region.seek(sector * SECTOR_BYTES);
            int length = region.readInt();
            compression = region.readUnsignedByte();
            if (length < 1) {
                throw new IOException("Chunk " + chunkX + "," + chunkZ + " in " + file + " has invalid length " + length);
            }
            payload = new byte[length - 1];
            region.readFully(payload);
        }
        if ((compression & EXTERNAL_PAYLOAD) != 0) {
            payload = Files.readAllBytes(regionDirectory.resolve("c." + chunkX + "." + chunkZ + ".mcc"));
            compression &= ~EXTERNAL_PAYLOAD;
        }
        return parse(payload, compression, chunkX, chunkZ);
    }

    private CompoundTag parse(byte[] payload, int compression, int chunkX, int chunkZ) throws IOException {
        CompressionType type = CompressionType.getFromID((byte) compression);
        if (type == null) {
            throw new IOException("Chunk " + chunkX + "," + chunkZ + " in " + regionDirectory
                    + " uses unsupported compression type " + compression);
        }
        try (InputStream input = new BufferedInputStream(type.decompress(new ByteArrayInputStream(payload)))) {
            NamedTag tag = new NBTDeserializer(false).fromStream(input);
            if (tag == null || !(tag.getTag() instanceof CompoundTag root)) {
                throw new IOException("Chunk " + chunkX + "," + chunkZ + " in " + regionDirectory + " has no compound root tag");
            }
            return root;
        }
    }

    private void decodeSection(CompoundTag section, String[] blocks, int chunkX, int chunkZ) {
        int baseY = section.getByte("Y") * CHUNK_WIDTH;
        CompoundTag states = section.getCompoundTag("block_states");
        if (baseY + CHUNK_WIDTH <= minY || baseY >= maxY || states == null) {
            return;
        }
        ListTag<?> paletteTag = states.getListTag("palette");
        if (paletteTag == null || paletteTag.size() == 0) {
            return;
        }
        String[] palette = new String[paletteTag.size()];
        int entry = 0;
        for (CompoundTag state : paletteTag.asCompoundTagList()) {
            palette[entry++] = state.getString("Name");
        }
        long[] data = states.getLongArray("data");
        int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(palette.length - 1));
        int perLong = 64 / bits;
        long mask = (1L << bits) - 1L;
        if (palette.length > 1 && data.length < (SECTION_VOLUME + perLong - 1) / perLong) {
            throw new IllegalStateException("Section " + section.getByte("Y") + " of chunk " + chunkX + "," + chunkZ
                    + " in " + regionDirectory + " has " + data.length + " block-state longs for a palette of " + palette.length);
        }
        for (int index = 0; index < SECTION_VOLUME; index++) {
            int y = baseY + (index >> 8);
            if (y < minY || y >= maxY) {
                continue;
            }
            int paletteIndex = palette.length == 1 ? 0 : (int) ((data[index / perLong] >>> ((index % perLong) * bits)) & mask);
            if (paletteIndex >= palette.length) {
                throw new IllegalStateException("Section " + section.getByte("Y") + " of chunk " + chunkX + "," + chunkZ
                        + " in " + regionDirectory + " references palette entry " + paletteIndex + " of " + palette.length);
            }
            blocks[offset(index & 15, y, (index >> 4) & 15)] = palette[paletteIndex];
        }
    }

    private int offset(int localX, int y, int localZ) {
        if (y < minY || y >= maxY) {
            throw new IllegalArgumentException("Y " + y + " is outside [" + minY + ", " + maxY + ")");
        }
        return ((y - minY) * CHUNK_WIDTH + localZ) * CHUNK_WIDTH + localX;
    }

    private final class RegionChunk implements FidelityChunk {
        private final String[] blocks;

        private RegionChunk(String[] blocks) {
            this.blocks = blocks;
        }

        @Override
        public int surfaceY(int localX, int localZ) {
            for (int y = maxY - 1; y >= minY; y--) {
                String id = block(localX, y, localZ);
                if (!FidelityBlocks.open(id) && !FidelityBlocks.fluid(id)) {
                    return y;
                }
            }
            return minY - 1;
        }

        @Override
        public String surfaceBiome(int localX, int localZ) {
            return null;
        }

        @Override
        public boolean open(int localX, int y, int localZ) {
            return FidelityBlocks.open(block(localX, y, localZ));
        }

        @Override
        public boolean fluid(int localX, int y, int localZ) {
            return FidelityBlocks.fluid(block(localX, y, localZ));
        }

        @Override
        public String block(int localX, int y, int localZ) {
            return blocks[offset(localX, y, localZ)];
        }
    }
}
