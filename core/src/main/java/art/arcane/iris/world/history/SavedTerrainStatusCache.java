package art.arcane.iris.world.history;

import art.arcane.iris.spi.IrisLogging;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Objects;
import java.util.zip.CRC32;

/**
 * Remembers the decoded "has terrain" bit of each saved chunk between startups. A bit is reused only while the
 * slot's location word and timestamp in the region header are unchanged, the timestamp is at least two seconds
 * older than the scan that decoded it, and the chunk is not stored in an external .mcc file. Ownership filtering
 * is never cached; callers apply it on every scan.
 */
final class SavedTerrainStatusCache {
    static final String FILE_NAME = "terrain-status.cache";

    private static final int MAGIC = 0x49545343;
    private static final int VERSION = 1;
    private static final int SLOTS = 1024;
    private static final int HEADER_BYTES = 2 * 4096;
    private static final long MAX_CACHE_BYTES = 1L << 30;

    private final Path file;
    private final Path regionDirectory;
    private final long scanSeconds;
    private final long previousScanSeconds;
    private final Long2ObjectOpenHashMap<Region> previous;
    private final Long2ObjectOpenHashMap<Region> next = new Long2ObjectOpenHashMap<>();
    private LongOpenHashSet externalChunks;
    private boolean externalChunksUnknown;

    private SavedTerrainStatusCache(Path file, Path world, long scanSeconds, Contents contents) {
        this.file = file;
        regionDirectory = world.toAbsolutePath().normalize().resolve(GenerationHistoryPaths.REGION_DIRECTORY_NAME);
        this.scanSeconds = scanSeconds;
        previousScanSeconds = contents.scanSeconds();
        previous = contents.regions();
    }

    static SavedTerrainStatusCache load(Path file, Path world) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(world, "world");
        long now = System.currentTimeMillis() / 1000L;
        return new SavedTerrainStatusCache(file, world, now, readContents(file));
    }

    /**
     * Starts the cache update for one region and moves every pending slot whose cached bit is still valid out of
     * {@code pending}, setting it in {@code accepted} when the chunk has terrain.
     */
    Update begin(int regionX, int regionZ, Header header, BitSet pending, BitSet accepted) {
        Update update = new Update(regionX, regionZ, header);
        Region cached = previous.get(ChunkGenerationOwnership.packChunk(regionX, regionZ));
        if (cached == null || !cached.matches(header)) {
            return update;
        }
        for (int entry = 0; entry < cached.slots().length; entry++) {
            int slot = cached.slots()[entry];
            if (header.location(slot) != cached.locations()[entry]
                    || header.timestamp(slot) != cached.timestamps()[entry]
                    || !reusable(cached.locations()[entry], cached.timestamps()[entry], previousScanSeconds)
                    || isExternal(regionX * 32 + (slot & 31), regionZ * 32 + (slot >> 5))) {
                continue;
            }
            boolean terrain = cached.terrain().get(entry);
            update.keep(slot, cached.locations()[entry], cached.timestamps()[entry], terrain);
            if (pending.get(slot)) {
                pending.clear(slot);
                if (terrain) {
                    accepted.set(slot);
                }
            }
        }
        return update;
    }

    /**
     * Keeps a region's cached entries as they are, for a region the scan did not need to read.
     */
    void carry(int regionX, int regionZ) {
        long key = ChunkGenerationOwnership.packChunk(regionX, regionZ);
        Region cached = previous.get(key);
        if (cached != null) {
            next.put(key, cached);
        }
    }

    /**
     * True when the chunk has an external .mcc payload, or when the region directory could not be listed.
     */
    boolean isExternal(int chunkX, int chunkZ) {
        if (externalChunks == null && !externalChunksUnknown) {
            try {
                externalChunks = listExternalChunks(regionDirectory);
            } catch (IOException | RuntimeException failure) {
                externalChunksUnknown = true;
                IrisLogging.warnOnce("saved-terrain-cache-external", () -> "Not using the saved terrain status cache"
                        + " because " + regionDirectory + " could not be listed: " + failure);
            }
        }
        return externalChunksUnknown
                || externalChunks.contains(ChunkGenerationOwnership.packChunk(chunkX, chunkZ));
    }

    /**
     * Writes the cache with the regions visited by this scan. Regions that were not visited are dropped. Failures
     * are logged and never reach the caller.
     */
    void save() {
        try {
            byte[] encoded = encode(scanSeconds, next);
            Path directory = file.toAbsolutePath().getParent();
            Files.createDirectories(directory);
            Path temporary = Files.createTempFile(directory, "." + file.getFileName() + "-", ".tmp");
            try {
                Files.write(temporary, encoded);
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException | RuntimeException failure) {
            IrisLogging.warnOnce("saved-terrain-cache-write", () -> "Could not write the saved terrain status cache "
                    + file + ": " + failure);
        }
    }

    private boolean reusable(int location, int timestamp, long decodedAt) {
        return location != 0 && Integer.toUnsignedLong(timestamp) < decodedAt - 1L;
    }

    private static LongOpenHashSet listExternalChunks(Path regionDirectory) throws IOException {
        LongOpenHashSet chunks = new LongOpenHashSet();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(regionDirectory, "c.*.*.mcc")) {
            for (Path entry : entries) {
                String[] parts = entry.getFileName().toString().split("\\.");
                if (parts.length != 4) {
                    continue;
                }
                try {
                    chunks.add(ChunkGenerationOwnership.packChunk(Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[2])));
                } catch (NumberFormatException ignored) {
                    // Not a chunk payload name; the region reader ignores it too.
                }
            }
        } catch (NoSuchFileException ignored) {
            return chunks;
        }
        return chunks;
    }

    private static Contents readContents(Path file) {
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                return Contents.EMPTY;
            }
            if (Files.size(file) > MAX_CACHE_BYTES) {
                throw new IOException("cache file is too large");
            }
            return decode(Files.readAllBytes(file));
        } catch (IOException | RuntimeException failure) {
            IrisLogging.warnOnce("saved-terrain-cache-read", () -> "Ignoring the saved terrain status cache "
                    + file + ": " + failure.getMessage());
            return Contents.EMPTY;
        }
    }

    private static byte[] encode(long scanSeconds, Long2ObjectOpenHashMap<Region> regions) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(buffer)) {
            output.writeInt(MAGIC);
            output.writeInt(VERSION);
            output.writeLong(scanSeconds);
            output.writeInt(regions.size());
            for (Region region : regions.values()) {
                output.writeInt(region.x());
                output.writeInt(region.z());
                output.writeLong(region.size());
                output.writeUTF(region.fileKey());
                output.writeShort(region.slots().length);
                for (int entry = 0; entry < region.slots().length; entry++) {
                    output.writeShort(region.slots()[entry]);
                    output.writeInt(region.locations()[entry]);
                    output.writeInt(region.timestamps()[entry]);
                    output.writeBoolean(region.terrain().get(entry));
                }
            }
            CRC32 checksum = new CRC32();
            checksum.update(buffer.toByteArray());
            output.writeLong(checksum.getValue());
        }
        return buffer.toByteArray();
    }

    private static Contents decode(byte[] bytes) throws IOException {
        if (bytes.length < Long.BYTES) {
            throw new IOException("cache file is truncated");
        }
        CRC32 checksum = new CRC32();
        checksum.update(bytes, 0, bytes.length - Long.BYTES);
        if (ByteBuffer.wrap(bytes, bytes.length - Long.BYTES, Long.BYTES).getLong() != checksum.getValue()) {
            throw new IOException("cache checksum does not match");
        }
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes, 0, bytes.length - Long.BYTES));
        if (input.readInt() != MAGIC) {
            throw new IOException("not a saved terrain status cache");
        }
        int version = input.readInt();
        if (version != VERSION) {
            throw new IOException("unsupported cache version " + version);
        }
        long scanSeconds = input.readLong();
        int regionCount = input.readInt();
        if (regionCount < 0) {
            throw new IOException("invalid region count");
        }
        Long2ObjectOpenHashMap<Region> regions = new Long2ObjectOpenHashMap<>(Math.min(regionCount, 65536));
        for (int index = 0; index < regionCount; index++) {
            int x = input.readInt();
            int z = input.readInt();
            long size = input.readLong();
            String fileKey = input.readUTF();
            int count = input.readUnsignedShort();
            if (size < HEADER_BYTES || count > SLOTS) {
                throw new IOException("invalid region entry");
            }
            int[] slots = new int[count];
            int[] locations = new int[count];
            int[] timestamps = new int[count];
            BitSet terrain = new BitSet(count);
            int previousSlot = -1;
            for (int entry = 0; entry < count; entry++) {
                int slot = input.readUnsignedShort();
                if (slot >= SLOTS || slot <= previousSlot) {
                    throw new IOException("invalid chunk slot");
                }
                previousSlot = slot;
                slots[entry] = slot;
                locations[entry] = input.readInt();
                timestamps[entry] = input.readInt();
                terrain.set(entry, input.readBoolean());
            }
            if (regions.put(ChunkGenerationOwnership.packChunk(x, z),
                    new Region(x, z, size, fileKey, slots, locations, timestamps, terrain)) != null) {
                throw new IOException("duplicate region entry");
            }
        }
        if (input.available() != 0) {
            throw new IOException("unexpected trailing data");
        }
        return new Contents(scanSeconds, regions);
    }

    final class Update {
        private final int regionX;
        private final int regionZ;
        private final Header header;
        private final int[] locations = new int[SLOTS];
        private final int[] timestamps = new int[SLOTS];
        private final BitSet known = new BitSet(SLOTS);
        private final BitSet terrain = new BitSet(SLOTS);

        private Update(int regionX, int regionZ, Header header) {
            this.regionX = regionX;
            this.regionZ = regionZ;
            this.header = header;
        }

        /**
         * Records a freshly decoded bit. Call only after the region was confirmed unchanged during decoding.
         */
        void record(int slot, int location, int timestamp, boolean hasTerrain) {
            if (reusable(location, timestamp, scanSeconds)) {
                keep(slot, location, timestamp, hasTerrain);
            } else {
                known.clear(slot);
            }
        }

        void commit() {
            int count = known.cardinality();
            int[] slots = new int[count];
            int[] storedLocations = new int[count];
            int[] storedTimestamps = new int[count];
            BitSet storedTerrain = new BitSet(count);
            int entry = 0;
            for (int slot = known.nextSetBit(0); slot >= 0; slot = known.nextSetBit(slot + 1)) {
                slots[entry] = slot;
                storedLocations[entry] = locations[slot];
                storedTimestamps[entry] = timestamps[slot];
                storedTerrain.set(entry, terrain.get(slot));
                entry++;
            }
            next.put(ChunkGenerationOwnership.packChunk(regionX, regionZ), new Region(regionX, regionZ,
                    header.size(), header.fileKey(), slots, storedLocations, storedTimestamps, storedTerrain));
        }

        private void keep(int slot, int location, int timestamp, boolean hasTerrain) {
            locations[slot] = location;
            timestamps[slot] = timestamp;
            known.set(slot);
            terrain.set(slot, hasTerrain);
        }
    }

    /**
     * The 8 KiB header of a region file together with the identity of the file it came from.
     */
    record Header(String fileKey, long size, byte[] bytes) {
        static final int BYTES = HEADER_BYTES;

        static Header read(Path file) throws IOException {
            Path normalized = file.toAbsolutePath().normalize();
            BasicFileAttributes attributes = Files.readAttributes(normalized, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile() || attributes.size() < HEADER_BYTES) {
                throw new IOException("Saved chunk region header is unavailable: " + normalized);
            }
            byte[] bytes = new byte[HEADER_BYTES];
            try (FileChannel input = FileChannel.open(normalized, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    if (input.read(buffer) < 0) {
                        throw new IOException("Truncated saved chunk region: " + normalized);
                    }
                }
            }
            Object key = attributes.fileKey();
            return new Header(key == null ? "" : key.toString(), attributes.size(), bytes);
        }

        int location(int slot) {
            return word(slot * Integer.BYTES);
        }

        int timestamp(int slot) {
            return word(4096 + slot * Integer.BYTES);
        }

        boolean sameHeader(Header other) {
            return fileKey.equals(other.fileKey) && Arrays.equals(bytes, other.bytes);
        }

        private int word(int offset) {
            return (bytes[offset] & 255) << 24 | (bytes[offset + 1] & 255) << 16
                    | (bytes[offset + 2] & 255) << 8 | bytes[offset + 3] & 255;
        }
    }

    record Region(int x, int z, long size, String fileKey, int[] slots, int[] locations, int[] timestamps,
            BitSet terrain) {
        boolean matches(Header header) {
            return fileKey.equals(header.fileKey()) && header.size() >= size;
        }
    }

    private record Contents(long scanSeconds, Long2ObjectOpenHashMap<Region> regions) {
        static final Contents EMPTY = new Contents(0L, new Long2ObjectOpenHashMap<>());
    }
}
