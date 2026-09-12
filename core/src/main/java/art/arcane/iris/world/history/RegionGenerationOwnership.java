package art.arcane.iris.world.history;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.zip.CRC32;
import java.util.Set;

final class RegionGenerationOwnership {
    static final int CHUNKS_PER_SIDE = 32;
    static final int CHUNK_COUNT = CHUNKS_PER_SIDE * CHUNKS_PER_SIDE;
    static final String FILE_SUFFIX = ".irow";

    private static final int MAGIC = 0x49524F57;
    private static final int FORMAT_VERSION = 1;
    private static final int FIXED_BODY_BYTES = 32;
    private static final int CHECKSUM_BYTES = Integer.BYTES;
    private static final int MAX_FILE_BYTES = 16_384;
    private static final int MINIMUM_REGION_COORDINATE = Math.floorDiv(Integer.MIN_VALUE, CHUNKS_PER_SIDE);
    private static final int MAXIMUM_REGION_COORDINATE = Math.floorDiv(Integer.MAX_VALUE, CHUNKS_PER_SIDE);

    private final int regionX;
    private final int regionZ;
    private final long[] activations;
    private int assignmentCount;
    private boolean dirty;

    RegionGenerationOwnership(int regionX, int regionZ) {
        this.regionX = regionX;
        this.regionZ = regionZ;
        this.activations = new long[CHUNK_COUNT];
    }

    static Metadata readMetadata(Path file) throws IOException {
        byte[] encoded = readEncoded(file);
        validateChecksum(file, encoded);
        int bodyLength = encoded.length - CHECKSUM_BYTES;
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded, 0, bodyLength))) {
            int magic = input.readInt();
            if (magic != MAGIC) {
                throw invalid(file, "invalid magic");
            }
            int version = input.readUnsignedShort();
            if (version != FORMAT_VERSION) {
                throw invalid(file, "unsupported format version " + version);
            }
            int flags = input.readUnsignedShort();
            if (flags != 0) {
                throw invalid(file, "unsupported format flags " + flags);
            }
            int regionX = input.readInt();
            int regionZ = input.readInt();
            validateRegionCoordinate(file, regionX);
            validateRegionCoordinate(file, regionZ);
            int assignmentCount = input.readInt();
            int paletteSize = input.readInt();
            int bitsPerEntry = input.readInt();
            int wordCount = input.readInt();
            validateHeader(file, assignmentCount, paletteSize, bitsPerEntry, wordCount);
            long expectedLength = FIXED_BODY_BYTES
                    + (long) paletteSize * Long.BYTES
                    + (long) wordCount * Long.BYTES
                    + CHECKSUM_BYTES;
            if (encoded.length != expectedLength) {
                throw invalid(file, "length does not match the ownership header");
            }
            return new Metadata(regionX, regionZ, assignmentCount);
        } catch (EOFException error) {
            throw invalid(file, "truncated data", error);
        }
    }

    static RegionGenerationOwnership read(Path file) throws IOException {
        byte[] encoded = readEncoded(file);
        validateChecksum(file, encoded);
        int bodyLength = encoded.length - CHECKSUM_BYTES;
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded, 0, bodyLength))) {
            int magic = input.readInt();
            if (magic != MAGIC) {
                throw invalid(file, "invalid magic");
            }
            int version = input.readUnsignedShort();
            if (version != FORMAT_VERSION) {
                throw invalid(file, "unsupported format version " + version);
            }
            int flags = input.readUnsignedShort();
            if (flags != 0) {
                throw invalid(file, "unsupported format flags " + flags);
            }

            int regionX = input.readInt();
            int regionZ = input.readInt();
            validateRegionCoordinate(file, regionX);
            validateRegionCoordinate(file, regionZ);
            int assignmentCount = input.readInt();
            int paletteSize = input.readInt();
            int bitsPerEntry = input.readInt();
            int wordCount = input.readInt();
            validateHeader(file, assignmentCount, paletteSize, bitsPerEntry, wordCount);

            long[] palette = readPalette(file, input, paletteSize);
            long[] packed = readPacked(input, wordCount);
            if (input.available() != 0) {
                throw invalid(file, "unexpected trailing data");
            }
            validateUnusedBits(file, packed, bitsPerEntry);

            RegionGenerationOwnership ownership = new RegionGenerationOwnership(regionX, regionZ);
            int decodedAssignments = ownership.decode(file, palette, packed, bitsPerEntry);
            if (decodedAssignments != assignmentCount) {
                throw invalid(file, "assignment count mismatch");
            }
            ownership.assignmentCount = decodedAssignments;
            return ownership;
        } catch (EOFException error) {
            throw invalid(file, "truncated data", error);
        }
    }

    int regionX() {
        return regionX;
    }

    int regionZ() {
        return regionZ;
    }

    int assignmentCount() {
        synchronized (this) {
            return assignmentCount;
        }
    }

    boolean hasAssignment(int chunkX, int chunkZ) {
        return activations[chunkIndex(chunkX, chunkZ)] != 0L;
    }

    boolean anyMatchingInBounds(int minimumX, int minimumZ, int maximumX, int maximumZ,
                                ChunkGenerationOwnership.AssignmentPredicate predicate) {
        int baseX = regionX << 5;
        int baseZ = regionZ << 5;
        int minimumLocalX = Math.max(minimumX, baseX) & 31;
        int maximumLocalX = Math.min(maximumX, baseX + 31) & 31;
        int minimumLocalZ = Math.max(minimumZ, baseZ) & 31;
        int maximumLocalZ = Math.min(maximumZ, baseZ + 31) & 31;
        for (int z = minimumLocalZ; z <= maximumLocalZ; z++) {
            for (int x = minimumLocalX; x <= maximumLocalX; x++) {
                long activation = activations[chunkIndex(x, z)];
                if (activation != 0L && predicate.test(baseX + x, baseZ + z, activation)) {
                    return true;
                }
            }
        }
        return false;
    }

    long explicitActivation(int chunkX, int chunkZ) {
        long activation = activations[chunkIndex(chunkX, chunkZ)];
        if (activation == 0L) {
            throw new IllegalStateException("Chunk " + chunkX + "," + chunkZ + " has no explicit generation activation");
        }
        return activation;
    }

    long resolve(int chunkX, int chunkZ, long currentActivation) {
        requireActivation(currentActivation);
        long activation = activations[chunkIndex(chunkX, chunkZ)];
        return activation == 0L ? currentActivation : activation;
    }

    synchronized boolean assign(int chunkX, int chunkZ, long activation) {
        requireActivation(activation);
        int index = chunkIndex(chunkX, chunkZ);
        long existing = activations[index];
        if (existing == activation) {
            return false;
        }
        if (existing != 0L) {
            throw new IllegalStateException(
                    "Chunk " + chunkX + "," + chunkZ + " already belongs to activation " + existing
                            + " and cannot be reassigned to " + activation
            );
        }
        activations[index] = activation;
        assignmentCount++;
        dirty = true;
        return true;
    }

    synchronized int discardUnstoredClaims(WorldChunkInventory stored, Set<Long> activationIds) {
        int removed = 0;
        for (int localZ = 0; localZ < CHUNKS_PER_SIDE; localZ++) {
            for (int localX = 0; localX < CHUNKS_PER_SIDE; localX++) {
                int index = chunkIndex(localX, localZ);
                if (activations[index] == 0L || !activationIds.contains(activations[index])) {
                    continue;
                }
                int chunkX = (regionX << 5) + localX;
                int chunkZ = (regionZ << 5) + localZ;
                if (!stored.contains(chunkX, chunkZ)) {
                    activations[index] = 0L;
                    assignmentCount--;
                    removed++;
                }
            }
        }
        dirty |= removed > 0;
        return removed;
    }

    synchronized boolean persist(Path directory) throws IOException {
        if (!dirty) {
            return false;
        }
        ensureDirectory(directory);
        Path target = directory.resolve(fileName(regionX, regionZ));
        Path temporary = Files.createTempFile(directory, "." + target.getFileName() + "-", ".tmp");
        try {
            byte[] encoded = encode();
            writeForced(temporary, encoded);
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException error) {
                throw new IOException("Generation ownership requires atomic shard publication", error);
            }
            forceDirectory(directory);
            dirty = false;
            return true;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    int fillPackedChunkKeys(long[] target, int offset) {
        int baseX = regionX << 5;
        int baseZ = regionZ << 5;
        int cursor = offset;
        for (int localZ = 0; localZ < CHUNKS_PER_SIDE; localZ++) {
            for (int localX = 0; localX < CHUNKS_PER_SIDE; localX++) {
                int index = chunkIndex(localX, localZ);
                if (activations[index] != 0L) {
                    target[cursor++] = ChunkGenerationOwnership.packChunk(baseX + localX, baseZ + localZ);
                }
            }
        }
        return cursor;
    }

    void forEachAssignment(AssignmentConsumer consumer) throws IOException {
        int baseX = regionX << 5;
        int baseZ = regionZ << 5;
        for (int localZ = 0; localZ < CHUNKS_PER_SIDE; localZ++) {
            for (int localX = 0; localX < CHUNKS_PER_SIDE; localX++) {
                long activation = activations[chunkIndex(localX, localZ)];
                if (activation != 0L) {
                    consumer.accept(baseX + localX, baseZ + localZ, activation);
                }
            }
        }
    }

    static String fileName(int regionX, int regionZ) {
        return "r." + regionX + "." + regionZ + FILE_SUFFIX;
    }

    private synchronized byte[] encode() throws IOException {
        long[] activationSnapshot = new long[CHUNK_COUNT];
        Long2IntOpenHashMap paletteIndexes = new Long2IntOpenHashMap();
        paletteIndexes.defaultReturnValue(-1);
        long[] palette = new long[assignmentCount];
        int paletteSize = 0;
        for (int index = 0; index < CHUNK_COUNT; index++) {
            long activation = activations[index];
            activationSnapshot[index] = activation;
            if (activation == 0L || paletteIndexes.get(activation) >= 0) {
                continue;
            }
            palette[paletteSize] = activation;
            paletteIndexes.put(activation, paletteSize + 1);
            paletteSize++;
        }

        int bitsPerEntry = bitsRequired(paletteSize + 1);
        long[] packed = pack(activationSnapshot, paletteIndexes, bitsPerEntry);
        ByteArrayOutputStream bodyBytes = new ByteArrayOutputStream(MAX_FILE_BYTES);
        try (DataOutputStream output = new DataOutputStream(bodyBytes)) {
            output.writeInt(MAGIC);
            output.writeShort(FORMAT_VERSION);
            output.writeShort(0);
            output.writeInt(regionX);
            output.writeInt(regionZ);
            output.writeInt(assignmentCount);
            output.writeInt(paletteSize);
            output.writeInt(bitsPerEntry);
            output.writeInt(packed.length);
            for (int index = 0; index < paletteSize; index++) {
                output.writeLong(palette[index]);
            }
            for (long word : packed) {
                output.writeLong(word);
            }
        }

        byte[] body = bodyBytes.toByteArray();
        CRC32 checksum = new CRC32();
        checksum.update(body);
        ByteArrayOutputStream encodedBytes = new ByteArrayOutputStream(body.length + CHECKSUM_BYTES);
        encodedBytes.write(body);
        try (DataOutputStream output = new DataOutputStream(encodedBytes)) {
            output.writeInt((int) checksum.getValue());
        }
        return encodedBytes.toByteArray();
    }

    private int decode(Path file, long[] palette, long[] packed, int bitsPerEntry) throws IOException {
        int decodedAssignments = 0;
        boolean[] paletteUsed = new boolean[palette.length];
        for (int index = 0; index < CHUNK_COUNT; index++) {
            int paletteIndex = unpack(packed, index, bitsPerEntry);
            if (paletteIndex == 0) {
                continue;
            }
            if (paletteIndex > palette.length) {
                throw invalid(file, "palette index out of range");
            }
            activations[index] = palette[paletteIndex - 1];
            paletteUsed[paletteIndex - 1] = true;
            decodedAssignments++;
        }
        for (boolean used : paletteUsed) {
            if (!used) {
                throw invalid(file, "unused activation palette entry");
            }
        }
        return decodedAssignments;
    }

    private static byte[] readEncoded(Path file) throws IOException {
        long size = Files.size(file);
        if (size < FIXED_BODY_BYTES + CHECKSUM_BYTES) {
            throw invalid(file, "truncated data");
        }
        if (size > MAX_FILE_BYTES) {
            throw invalid(file, "file is too large");
        }
        byte[] encoded = Files.readAllBytes(file);
        if (encoded.length > MAX_FILE_BYTES) {
            throw invalid(file, "file is too large");
        }
        return encoded;
    }

    private static void validateChecksum(Path file, byte[] encoded) throws IOException {
        int bodyLength = encoded.length - CHECKSUM_BYTES;
        int expected = ByteBuffer.wrap(encoded, bodyLength, CHECKSUM_BYTES).getInt();
        CRC32 checksum = new CRC32();
        checksum.update(encoded, 0, bodyLength);
        if ((int) checksum.getValue() != expected) {
            throw invalid(file, "checksum mismatch");
        }
    }

    private static void validateHeader(
            Path file,
            int assignmentCount,
            int paletteSize,
            int bitsPerEntry,
            int wordCount
    ) throws IOException {
        if (assignmentCount < 0 || assignmentCount > CHUNK_COUNT) {
            throw invalid(file, "invalid assignment count " + assignmentCount);
        }
        if (paletteSize < 0 || paletteSize > assignmentCount) {
            throw invalid(file, "invalid palette size " + paletteSize);
        }
        if ((assignmentCount == 0) != (paletteSize == 0)) {
            throw invalid(file, "assignment and palette counts disagree");
        }
        int expectedBits = bitsRequired(paletteSize + 1);
        if (bitsPerEntry != expectedBits) {
            throw invalid(file, "invalid bits per entry " + bitsPerEntry);
        }
        int expectedWords = packedWordCount(bitsPerEntry);
        if (wordCount != expectedWords) {
            throw invalid(file, "invalid packed word count " + wordCount);
        }
    }

    private static long[] readPalette(Path file, DataInputStream input, int paletteSize) throws IOException {
        long[] palette = new long[paletteSize];
        LongOpenHashSet unique = new LongOpenHashSet(paletteSize);
        for (int index = 0; index < paletteSize; index++) {
            long activation = input.readLong();
            if (activation <= 0L) {
                throw invalid(file, "activation IDs must be positive");
            }
            if (!unique.add(activation)) {
                throw invalid(file, "duplicate activation in palette");
            }
            palette[index] = activation;
        }
        return palette;
    }

    private static long[] readPacked(DataInputStream input, int wordCount) throws IOException {
        long[] packed = new long[wordCount];
        for (int index = 0; index < wordCount; index++) {
            packed[index] = input.readLong();
        }
        return packed;
    }

    private static void validateUnusedBits(Path file, long[] packed, int bitsPerEntry) throws IOException {
        if (packed.length == 0) {
            return;
        }
        int usedBits = (CHUNK_COUNT * bitsPerEntry) & 63;
        if (usedBits == 0) {
            return;
        }
        long unusedMask = -1L << usedBits;
        if ((packed[packed.length - 1] & unusedMask) != 0L) {
            throw invalid(file, "nonzero unused packed bits");
        }
    }

    private static void validateRegionCoordinate(Path file, int regionCoordinate) throws IOException {
        if (regionCoordinate < MINIMUM_REGION_COORDINATE || regionCoordinate > MAXIMUM_REGION_COORDINATE) {
            throw invalid(file, "region coordinate is outside the chunk coordinate range");
        }
    }

    private static long[] pack(
            long[] activations,
            Long2IntOpenHashMap paletteIndexes,
            int bitsPerEntry
    ) {
        long[] packed = new long[packedWordCount(bitsPerEntry)];
        if (bitsPerEntry == 0) {
            return packed;
        }
        for (int index = 0; index < CHUNK_COUNT; index++) {
            long activation = activations[index];
            int paletteIndex = activation == 0L ? 0 : paletteIndexes.get(activation);
            int bitIndex = index * bitsPerEntry;
            int wordIndex = bitIndex >>> 6;
            int bitOffset = bitIndex & 63;
            packed[wordIndex] |= (long) paletteIndex << bitOffset;
            if (bitOffset + bitsPerEntry > Long.SIZE) {
                packed[wordIndex + 1] |= (long) paletteIndex >>> (Long.SIZE - bitOffset);
            }
        }
        return packed;
    }

    private static int unpack(long[] packed, int index, int bitsPerEntry) {
        if (bitsPerEntry == 0) {
            return 0;
        }
        int bitIndex = index * bitsPerEntry;
        int wordIndex = bitIndex >>> 6;
        int bitOffset = bitIndex & 63;
        long value = packed[wordIndex] >>> bitOffset;
        if (bitOffset + bitsPerEntry > Long.SIZE) {
            value |= packed[wordIndex + 1] << (Long.SIZE - bitOffset);
        }
        long mask = (1L << bitsPerEntry) - 1L;
        return (int) (value & mask);
    }

    private static int bitsRequired(int values) {
        if (values <= 1) {
            return 0;
        }
        return Integer.SIZE - Integer.numberOfLeadingZeros(values - 1);
    }

    private static int packedWordCount(int bitsPerEntry) {
        return (CHUNK_COUNT * bitsPerEntry + Long.SIZE - 1) / Long.SIZE;
    }

    private static int chunkIndex(int chunkX, int chunkZ) {
        return (chunkX & 31) + ((chunkZ & 31) << 5);
    }

    private static void requireActivation(long activation) {
        if (activation <= 0L) {
            throw new IllegalArgumentException("Generation activation IDs must be positive: " + activation);
        }
    }

    private static void writeForced(Path file, byte[] encoded) throws IOException {
        try (FileChannel channel = FileChannel.open(
                file,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
        )) {
            ByteBuffer buffer = ByteBuffer.wrap(encoded);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    private static void ensureDirectory(Path directory) throws IOException {
        Path parent = directory.getParent();
        if (parent == null || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Generation ownership parent directory is missing or unsafe: " + parent);
        }
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Generation ownership path is not a safe directory: " + directory);
            }
            return;
        }
        try {
            Files.createDirectory(directory);
        } catch (FileAlreadyExistsException race) {
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Generation ownership path is not a safe directory: " + directory, race);
            }
            return;
        }
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Generation ownership path is not a safe directory: " + directory);
        }
    }

    private static void forceDirectory(Path directory) throws IOException {
        if (File.separatorChar == '\\') {
            return;
        }
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (UnsupportedOperationException error) {
            throw new IOException("Generation ownership directory cannot be durability-synced", error);
        }
    }

    private static IOException invalid(Path file, String reason) {
        return new IOException("Invalid generation ownership file " + file + ": " + reason);
    }

    private static IOException invalid(Path file, String reason, Throwable cause) {
        return new IOException("Invalid generation ownership file " + file + ": " + reason, cause);
    }

    record Metadata(int regionX, int regionZ, int assignmentCount) {
    }

    @FunctionalInterface
    interface AssignmentConsumer {
        void accept(int chunkX, int chunkZ, long activationId) throws IOException;
    }
}
