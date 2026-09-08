package art.arcane.iris.engine.history;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrays;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.DigestOutputStream;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.zip.CRC32;

public final class GenerationBoundaryStore {
    public static final String SNAPSHOT_FILE_SUFFIX = ".irgb";

    private static final String MASK_FILE_SUFFIX = ".irbm";
    private static final int MAGIC = 0x49524742;
    private static final int SCHEMA_VERSION = 2;
    private static final int MASK_MAGIC = 0x4952424D;
    private static final int MASK_SCHEMA_VERSION = 1;
    private static final int IDENTITY_BYTES = 32;
    private static final int CATALOG_FIXED_BODY_BYTES = 56;
    private static final int CATALOG_ENTRY_BYTES = 40;
    private static final int CHECKSUM_BYTES = Integer.BYTES;
    private static final int REGION_SIDE = 32;
    private static final int CHUNKS_PER_REGION = REGION_SIDE * REGION_SIDE;
    private static final int MASK_BYTES = CHUNKS_PER_REGION / Byte.SIZE;
    private static final int MASK_BODY_BYTES = 8 + MASK_BYTES;
    private static final int MAXIMUM_CACHED_MASKS = 64;
    private static final int MAX_REGION_COUNT = 8_388_608;
    private static final long MAX_CATALOG_BYTES = CATALOG_FIXED_BODY_BYTES
            + (long) MAX_REGION_COUNT * CATALOG_ENTRY_BYTES
            + CHECKSUM_BYTES;
    private static final int TRANSFER_BYTES = 65_536;
    private static final byte[] IDENTITY_DOMAIN = {
            0x49, 0x72, 0x69, 0x73, 0x42, 0x6F, 0x75, 0x6E,
            0x64, 0x61, 0x72, 0x79, 0x02
    };

    private final Path dimensionRoot;
    private final Path directory;

    public GenerationBoundaryStore(Path dimensionRoot) {
        Path root = Objects.requireNonNull(dimensionRoot, "Dimension root").toAbsolutePath().normalize();
        this.dimensionRoot = root;
        this.directory = root.resolve("iris").resolve("generation").resolve("boundaries").normalize();
        if (!directory.startsWith(root)) {
            throw new IllegalArgumentException("Generation boundary path escapes the dimension root");
        }
    }

    public Path directory() {
        return directory;
    }

    public Path snapshotPath(long activationId) {
        requireActivation(activationId);
        return directory.resolve(fileName(activationId));
    }

    public synchronized GenerationBoundary publish(
            long activationId,
            Collection<GenerationBoundary.ChunkCoordinate> historicalChunks
    ) throws IOException {
        Collection<GenerationBoundary.ChunkCoordinate> requiredChunks = Objects.requireNonNull(
                historicalChunks,
                "Historical chunks"
        );
        MaskCatalogBuilder builder = new MaskCatalogBuilder();
        for (GenerationBoundary.ChunkCoordinate chunk : requiredChunks) {
            GenerationBoundary.ChunkCoordinate requiredChunk = Objects.requireNonNull(
                    chunk,
                    "Historical chunk"
            );
            builder.add(requiredChunk.chunkX(), requiredChunk.chunkZ());
        }
        return publishCatalog(activationId, builder);
    }

    public synchronized GenerationBoundary publishPacked(long activationId, long[] historicalChunks)
            throws IOException {
        long[] requiredChunks = Objects.requireNonNull(historicalChunks, "Historical chunks");
        MaskCatalogBuilder builder = new MaskCatalogBuilder();
        for (long chunk : requiredChunks) {
            builder.add((int) (chunk >> Integer.SIZE), (int) chunk);
        }
        return publishCatalog(activationId, builder);
    }

    synchronized GenerationBoundary publishOwnership(
            long activationId,
            ChunkGenerationOwnership ownership
    ) throws IOException {
        ChunkGenerationOwnership requiredOwnership = Objects.requireNonNull(ownership, "Generation ownership");
        MaskCatalogBuilder builder = new MaskCatalogBuilder();
        requiredOwnership.forEachAssignment((chunkX, chunkZ, owner) -> builder.add(chunkX, chunkZ));
        return publishCatalog(activationId, builder);
    }

    public GenerationBoundary load(long activationId) throws IOException {
        requireActivation(activationId);
        validateStorageAncestors();
        Path snapshot = snapshotPath(activationId);
        requireRegularFile(snapshot, "Generation boundary snapshot");
        long fileSize = Files.size(snapshot);
        if (fileSize < CATALOG_FIXED_BODY_BYTES + CHECKSUM_BYTES) {
            throw invalid(snapshot, "truncated data");
        }
        if (fileSize > MAX_CATALOG_BYTES) {
            throw invalid(snapshot, "catalog exceeds the maximum size");
        }
        verifyChecksum(snapshot, fileSize, "generation boundary snapshot");

        long bodyBytes = fileSize - CHECKSUM_BYTES;
        try (InputStream fileInput = Files.newInputStream(snapshot);
             LimitedInputStream limitedInput = new LimitedInputStream(fileInput, bodyBytes);
             DataInputStream input = new DataInputStream(limitedInput)) {
            int magic = input.readInt();
            if (magic != MAGIC) {
                throw invalid(snapshot, "invalid magic");
            }
            int version = input.readUnsignedShort();
            if (version != SCHEMA_VERSION) {
                throw invalid(snapshot, "unsupported schema version " + version);
            }
            int flags = input.readUnsignedShort();
            if (flags != 0) {
                throw invalid(snapshot, "unsupported format flags " + flags);
            }
            long storedActivation = input.readLong();
            if (storedActivation != activationId) {
                throw invalid(snapshot, "activation ID does not match the snapshot path");
            }
            int chunkCount = input.readInt();
            if (chunkCount < 0) {
                throw invalid(snapshot, "invalid chunk count " + chunkCount);
            }
            byte[] storedIdentity = input.readNBytes(IDENTITY_BYTES);
            if (storedIdentity.length != IDENTITY_BYTES) {
                throw invalid(snapshot, "truncated snapshot identity");
            }
            int regionCount = input.readInt();
            if (regionCount < 0 || regionCount > MAX_REGION_COUNT) {
                throw invalid(snapshot, "invalid region count " + regionCount);
            }
            long expectedSize = CATALOG_FIXED_BODY_BYTES
                    + (long) regionCount * CATALOG_ENTRY_BYTES
                    + CHECKSUM_BYTES;
            if (fileSize != expectedSize) {
                throw invalid(snapshot, "catalog length does not match the region count");
            }

            long[] regionKeys = new long[regionCount];
            Long2ObjectOpenHashMap<String> shardHashes = new Long2ObjectOpenHashMap<>(regionCount);
            int countedChunks = 0;
            long previousRegionKey = 0L;
            for (int index = 0; index < regionCount; index++) {
                int regionX = input.readInt();
                int regionZ = input.readInt();
                long regionKey = packRegion(regionX, regionZ);
                if (index > 0 && compareRegions(previousRegionKey, regionKey) >= 0) {
                    throw invalid(snapshot, "region entries are not in canonical order");
                }
                byte[] hashBytes = input.readNBytes(IDENTITY_BYTES);
                if (hashBytes.length != IDENTITY_BYTES) {
                    throw invalid(snapshot, "truncated mask hash");
                }
                String hash = HexFormat.of().formatHex(hashBytes);
                byte[] mask = readMask(hash);
                countedChunks = Math.addExact(countedChunks, countBits(mask));
                regionKeys[index] = regionKey;
                shardHashes.put(regionKey, hash);
                previousRegionKey = regionKey;
            }
            if (limitedInput.remaining() != 0L) {
                throw invalid(snapshot, "unexpected trailing catalog data");
            }
            if (countedChunks != chunkCount) {
                throw invalid(snapshot, "chunk count does not match the referenced masks");
            }
            byte[] computedIdentity = computeIdentity(activationId, chunkCount, regionKeys, shardHashes);
            if (!MessageDigest.isEqual(storedIdentity, computedIdentity)) {
                throw invalid(snapshot, "boundary identity mismatch");
            }
            MaskSource source = new MaskSource(directory, regionKeys, shardHashes);
            return GenerationBoundary.backed(toHex(computedIdentity), chunkCount, source);
        } catch (ArithmeticException error) {
            throw invalid(snapshot, "chunk count overflows the supported range", error);
        } catch (EOFException error) {
            throw invalid(snapshot, "truncated data", error);
        }
    }

    private GenerationBoundary publishCatalog(long activationId, MaskCatalogBuilder builder) throws IOException {
        requireActivation(activationId);
        ensureDirectory();
        try (GenerationPublicationLock ignored = GenerationPublicationLock.acquire(
                directory,
                ".activation-" + activationId + SNAPSHOT_FILE_SUFFIX + ".lock"
        )) {
            ensureDirectory();
            PublishedCatalog catalog = builder.publish(directory, activationId);
            Path target = snapshotPath(activationId);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                GenerationBoundary existing = load(activationId);
                if (!existing.identity().equals(catalog.identity())) {
                    throw new FileAlreadyExistsException(
                            "Generation boundary activation " + activationId
                                    + " already has a different snapshot: " + target
                    );
                }
                return existing;
            }
            publishAtomic(target, catalog.encoded(), "generation boundary catalog");
            return load(activationId);
        }
    }

    private byte[] readMask(String hash) throws IOException {
        Path file = directory.resolve(maskFileName(hash));
        requireRegularFile(file, "Generation boundary mask");
        return MaskShard.read(file, hash);
    }

    private void ensureDirectory() throws IOException {
        requireSafeDirectory(dimensionRoot);
        Path irisDirectory = ensureChildDirectory(dimensionRoot, "iris");
        Path generationDirectory = ensureChildDirectory(irisDirectory, "generation");
        Path boundaryDirectory = ensureChildDirectory(generationDirectory, "boundaries");
        if (!boundaryDirectory.equals(directory)) {
            throw new IOException("Generation boundary resolved outside its storage path: " + boundaryDirectory);
        }
    }

    private void validateStorageAncestors() throws IOException {
        requireSafeDirectory(dimensionRoot);
        requireSafeDirectory(dimensionRoot.resolve("iris"));
        requireSafeDirectory(dimensionRoot.resolve("iris").resolve("generation"));
        requireSafeDirectory(directory);
    }

    private static Path ensureChildDirectory(Path parent, String name) throws IOException {
        Path child = parent.resolve(name);
        if (Files.exists(child, LinkOption.NOFOLLOW_LINKS)) {
            requireSafeDirectory(child);
            return child;
        }
        try {
            Files.createDirectory(child);
        } catch (FileAlreadyExistsException race) {
            requireSafeDirectory(child);
            return child;
        }
        forceDirectory(parent);
        return child;
    }

    private static void requireSafeDirectory(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new NoSuchFileException(path.toString());
        }
        BasicFileAttributes attributes = Files.readAttributes(
                path,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS
        );
        if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
            throw new IOException("Generation boundary path is not a safe directory: " + path);
        }
    }

    private static void requireRegularFile(Path path, String label) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new NoSuchFileException(path.toString());
        }
        BasicFileAttributes attributes = Files.readAttributes(
                path,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS
        );
        if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
            throw new IOException(label + " is not a safe regular file: " + path);
        }
    }

    private static byte[] computeIdentity(
            long activationId,
            int chunkCount,
            long[] regionKeys,
            Long2ObjectOpenHashMap<String> shardHashes
    ) throws IOException {
        MessageDigest digest = sha256();
        digest.update(IDENTITY_DOMAIN);
        try (DigestOutputStream digestOutput = new DigestOutputStream(OutputStream.nullOutputStream(), digest);
             DataOutputStream output = new DataOutputStream(digestOutput)) {
            output.writeLong(activationId);
            output.writeInt(chunkCount);
            output.writeInt(regionKeys.length);
            for (long regionKey : regionKeys) {
                output.writeInt(regionX(regionKey));
                output.writeInt(regionZ(regionKey));
                output.write(HexFormat.of().parseHex(shardHashes.get(regionKey)));
            }
        }
        return digest.digest();
    }

    private static byte[] encodeCatalog(
            long activationId,
            int chunkCount,
            long[] regionKeys,
            Long2ObjectOpenHashMap<String> shardHashes,
            byte[] identity
    ) throws IOException {
        ByteArrayOutputStream bodyBytes = new ByteArrayOutputStream(
                CATALOG_FIXED_BODY_BYTES + regionKeys.length * CATALOG_ENTRY_BYTES
        );
        try (DataOutputStream output = new DataOutputStream(bodyBytes)) {
            output.writeInt(MAGIC);
            output.writeShort(SCHEMA_VERSION);
            output.writeShort(0);
            output.writeLong(activationId);
            output.writeInt(chunkCount);
            output.write(identity);
            output.writeInt(regionKeys.length);
            for (long regionKey : regionKeys) {
                output.writeInt(regionX(regionKey));
                output.writeInt(regionZ(regionKey));
                output.write(HexFormat.of().parseHex(shardHashes.get(regionKey)));
            }
        }
        return withChecksum(bodyBytes.toByteArray());
    }

    private static void publishAtomic(Path target, byte[] encoded, String kind) throws IOException {
        Path directory = target.getParent();
        Path temporary = Files.createTempFile(directory, ".boundary-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
            )) {
                writeFully(channel, ByteBuffer.wrap(encoded));
                channel.force(true);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException error) {
                throw new IOException("Generation boundary " + kind + " publication requires an atomic move", error);
            }
            forceDirectory(directory);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static byte[] withChecksum(byte[] body) throws IOException {
        CRC32 checksum = new CRC32();
        checksum.update(body);
        ByteArrayOutputStream encoded = new ByteArrayOutputStream(body.length + CHECKSUM_BYTES);
        encoded.write(body);
        try (DataOutputStream output = new DataOutputStream(encoded)) {
            output.writeInt((int) checksum.getValue());
        }
        return encoded.toByteArray();
    }

    private static void verifyChecksum(Path file, long fileSize, String kind) throws IOException {
        long bodyBytes = fileSize - CHECKSUM_BYTES;
        CRC32 checksum = new CRC32();
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(TRANSFER_BYTES);
            long remaining = bodyBytes;
            while (remaining > 0L) {
                buffer.clear();
                buffer.limit((int) Math.min(buffer.capacity(), remaining));
                readFully(channel, buffer);
                buffer.flip();
                checksum.update(buffer);
                remaining -= buffer.limit();
            }
            ByteBuffer stored = ByteBuffer.allocate(CHECKSUM_BYTES);
            readFully(channel, stored);
            stored.flip();
            if (stored.getInt() != (int) checksum.getValue()) {
                throw new IOException("Invalid " + kind + " " + file + ": checksum mismatch");
            }
        }
    }

    private static int countBits(byte[] mask) {
        int count = 0;
        for (byte value : mask) {
            count += Integer.bitCount(value & 0xff);
        }
        return count;
    }

    private static long packRegion(int regionX, int regionZ) {
        return ((long) regionX << Integer.SIZE) | (regionZ & 0xffffffffL);
    }

    private static int regionX(long regionKey) {
        return (int) (regionKey >> Integer.SIZE);
    }

    private static int regionZ(long regionKey) {
        return (int) regionKey;
    }

    private static int compareRegions(long first, long second) {
        int xComparison = Integer.compare(regionX(first), regionX(second));
        return xComparison != 0 ? xComparison : Integer.compare(regionZ(first), regionZ(second));
    }

    private static String fileName(long activationId) {
        return "activation-" + activationId + SNAPSHOT_FILE_SUFFIX;
    }

    private static String maskFileName(String hash) {
        return "chunk-mask-" + hash + MASK_FILE_SUFFIX;
    }

    private static void requireActivation(long activationId) {
        if (activationId <= 0L) {
            throw new IllegalArgumentException("Generation activation IDs must be positive: " + activationId);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static String sha256(byte[] encoded) {
        return HexFormat.of().formatHex(sha256().digest(encoded));
    }

    private static String toHex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    private static void writeFully(FileChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    private static void readFully(FileChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) {
                throw new EOFException("Unexpected end of generation boundary data");
            }
        }
    }

    private static void forceDirectory(Path path) throws IOException {
        if (File.separatorChar == '\\') {
            return;
        }
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (UnsupportedOperationException error) {
            throw new IOException("Generation boundary directory cannot be durability-synced: " + path, error);
        }
    }

    private static IOException invalid(Path path, String reason) {
        return new IOException("Invalid generation boundary snapshot " + path + ": " + reason);
    }

    private static IOException invalid(Path path, String reason, Throwable cause) {
        return new IOException("Invalid generation boundary snapshot " + path + ": " + reason, cause);
    }

    private static final class MaskCatalogBuilder {
        private final Long2ObjectOpenHashMap<byte[]> masks = new Long2ObjectOpenHashMap<>();
        private int chunkCount;

        private void add(int chunkX, int chunkZ) {
            int regionX = chunkX >> 5;
            int regionZ = chunkZ >> 5;
            long regionKey = packRegion(regionX, regionZ);
            byte[] mask = masks.computeIfAbsent(regionKey, ignored -> new byte[MASK_BYTES]);
            int localIndex = (Math.floorMod(chunkZ, REGION_SIDE) << 5)
                    | Math.floorMod(chunkX, REGION_SIDE);
            int byteIndex = localIndex >>> 3;
            int bit = 1 << (localIndex & 7);
            if ((mask[byteIndex] & bit) == 0) {
                mask[byteIndex] = (byte) (mask[byteIndex] | bit);
                chunkCount = Math.addExact(chunkCount, 1);
            }
        }

        private PublishedCatalog publish(Path directory, long activationId) throws IOException {
            if (masks.size() > MAX_REGION_COUNT) {
                throw new IOException("Generation boundary exceeds the maximum region count");
            }
            long[] regionKeys = masks.keySet().toLongArray();
            sortRegionKeys(regionKeys);
            Long2ObjectOpenHashMap<String> shardHashes = new Long2ObjectOpenHashMap<>(regionKeys.length);
            for (long regionKey : regionKeys) {
                String hash = MaskShard.publish(directory, masks.get(regionKey));
                shardHashes.put(regionKey, hash);
            }
            byte[] identity = computeIdentity(activationId, chunkCount, regionKeys, shardHashes);
            byte[] encoded = encodeCatalog(activationId, chunkCount, regionKeys, shardHashes, identity);
            return new PublishedCatalog(toHex(identity), encoded);
        }

        private static void sortRegionKeys(long[] regionKeys) {
            LongArrays.quickSort(regionKeys, GenerationBoundaryStore::compareRegions);
        }
    }

    private record PublishedCatalog(String identity, byte[] encoded) {
    }

    private static final class MaskShard {
        private static String publish(Path directory, byte[] mask) throws IOException {
            byte[] encoded = encode(mask);
            String hash = sha256(encoded);
            Path target = directory.resolve(maskFileName(hash));
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                requireRegularFile(target, "Generation boundary mask");
                read(target, hash);
                return hash;
            }
            try {
                publishAtomic(target, encoded, "mask shard");
            } catch (FileAlreadyExistsException race) {
                requireRegularFile(target, "Generation boundary mask");
                read(target, hash);
            }
            return hash;
        }

        private static byte[] encode(byte[] mask) throws IOException {
            if (mask.length != MASK_BYTES || countBits(mask) == 0) {
                throw new IOException("Generation boundary mask is empty or malformed");
            }
            ByteArrayOutputStream bodyBytes = new ByteArrayOutputStream(MASK_BODY_BYTES);
            try (DataOutputStream output = new DataOutputStream(bodyBytes)) {
                output.writeInt(MASK_MAGIC);
                output.writeShort(MASK_SCHEMA_VERSION);
                output.writeShort(0);
                output.write(mask);
            }
            return withChecksum(bodyBytes.toByteArray());
        }

        private static byte[] read(Path file, String expectedHash) throws IOException {
            byte[] encoded = Files.readAllBytes(file);
            if (encoded.length != MASK_BODY_BYTES + CHECKSUM_BYTES) {
                throw new IOException("Invalid generation boundary mask " + file + ": invalid length");
            }
            if (!sha256(encoded).equals(expectedHash)) {
                throw new IOException("Invalid generation boundary mask " + file + ": content hash mismatch");
            }
            CRC32 checksum = new CRC32();
            checksum.update(encoded, 0, MASK_BODY_BYTES);
            if (ByteBuffer.wrap(encoded, MASK_BODY_BYTES, CHECKSUM_BYTES).getInt()
                    != (int) checksum.getValue()) {
                throw new IOException("Invalid generation boundary mask " + file + ": checksum mismatch");
            }
            try (DataInputStream input = new DataInputStream(
                    new ByteArrayInputStream(encoded, 0, MASK_BODY_BYTES)
            )) {
                if (input.readInt() != MASK_MAGIC) {
                    throw new IOException("Invalid generation boundary mask " + file + ": invalid magic");
                }
                int version = input.readUnsignedShort();
                if (version != MASK_SCHEMA_VERSION) {
                    throw new IOException("Invalid generation boundary mask " + file
                            + ": unsupported schema version " + version);
                }
                if (input.readUnsignedShort() != 0) {
                    throw new IOException("Invalid generation boundary mask " + file + ": unsupported flags");
                }
                byte[] mask = input.readNBytes(MASK_BYTES);
                if (mask.length != MASK_BYTES || countBits(mask) == 0 || input.available() != 0) {
                    throw new IOException("Invalid generation boundary mask " + file + ": malformed mask");
                }
                return mask;
            }
        }
    }

    private static final class MaskSource implements GenerationBoundary.HistoricalChunkSource {
        private final Path directory;
        private final long[] regionKeys;
        private final Long2ObjectOpenHashMap<String> shardHashes;
        private final LinkedHashMap<Long, byte[]> masks;
        private final ThreadLocal<RegionMask> lastRegion = new ThreadLocal<>();

        private MaskSource(
                Path directory,
                long[] regionKeys,
                Long2ObjectOpenHashMap<String> shardHashes
        ) {
            this.directory = directory;
            this.regionKeys = regionKeys;
            this.shardHashes = shardHashes;
            masks = new LinkedHashMap<>(MAXIMUM_CACHED_MASKS, 0.75F, true);
        }

        @Override
        public boolean contains(int chunkX, int chunkZ) throws IOException {
            long regionKey = packRegion(chunkX >> 5, chunkZ >> 5);
            RegionMask region = lastRegion.get();
            if (region == null || region.key() != regionKey) {
                String hash = shardHashes.get(regionKey);
                byte[] loaded = null;
                if (hash != null) {
                    synchronized (this) {
                        loaded = load(regionKey, hash);
                    }
                }
                region = new RegionMask(regionKey, loaded);
                lastRegion.set(region);
            }
            byte[] mask = region.mask();
            if (mask == null) {
                return false;
            }
            int localIndex = (Math.floorMod(chunkZ, REGION_SIDE) << 5)
                    | Math.floorMod(chunkX, REGION_SIDE);
            return (mask[localIndex >>> 3] & (1 << (localIndex & 7))) != 0;
        }

        @Override
        public synchronized void forEach(GenerationBoundary.ChunkConsumer consumer) throws IOException {
            for (long regionKey : regionKeys) {
                byte[] mask = load(regionKey, shardHashes.get(regionKey));
                int minimumChunkX = regionX(regionKey) << 5;
                int minimumChunkZ = regionZ(regionKey) << 5;
                for (int localIndex = 0; localIndex < CHUNKS_PER_REGION; localIndex++) {
                    if ((mask[localIndex >>> 3] & (1 << (localIndex & 7))) == 0) {
                        continue;
                    }
                    consumer.accept(
                            minimumChunkX + (localIndex & 31),
                            minimumChunkZ + (localIndex >>> 5)
                    );
                }
            }
        }

        @Override
        public synchronized int cachedRegionCount() {
            return masks.size();
        }

        private byte[] load(long regionKey, String hash) throws IOException {
            byte[] cached = masks.get(regionKey);
            if (cached != null) {
                return cached;
            }
            Path file = directory.resolve(maskFileName(hash));
            requireRegularFile(file, "Generation boundary mask");
            byte[] loaded = MaskShard.read(file, hash);
            masks.put(regionKey, loaded);
            while (masks.size() > MAXIMUM_CACHED_MASKS) {
                Iterator<Map.Entry<Long, byte[]>> entries = masks.entrySet().iterator();
                entries.next();
                entries.remove();
            }
            return loaded;
        }

        private record RegionMask(long key, byte[] mask) {
        }
    }

    private static final class LimitedInputStream extends InputStream {
        private final InputStream input;
        private long remaining;

        private LimitedInputStream(InputStream input, long remaining) {
            this.input = input;
            this.remaining = remaining;
        }

        @Override
        public int read() throws IOException {
            if (remaining == 0L) {
                return -1;
            }
            int value = input.read();
            if (value < 0) {
                throw new EOFException("Unexpected end of bounded generation boundary input");
            }
            remaining--;
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, buffer.length);
            if (remaining == 0L) {
                return -1;
            }
            int read = input.read(buffer, offset, (int) Math.min(length, remaining));
            if (read < 0) {
                throw new EOFException("Unexpected end of bounded generation boundary input");
            }
            remaining -= read;
            return read;
        }

        @Override
        public void close() throws IOException {
            input.close();
        }

        private long remaining() {
            return remaining;
        }
    }
}
