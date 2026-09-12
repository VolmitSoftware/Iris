package art.arcane.iris.world.history;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
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
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.PriorityQueue;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.LongAdder;
import java.util.zip.CRC32;

public final class TerrainBoundarySignatureStore {
    public static final String SNAPSHOT_FILE_SUFFIX = ".irts";

    private static final String SHARD_FILE_SUFFIX = ".irtm";
    private static final int MAGIC = 0x49525453;
    private static final int SCHEMA_VERSION = 4;
    private static final int SHARD_MAGIC = 0x4952544D;
    private static final int SHARD_SCHEMA_VERSION = 3;
    private static final int IDENTITY_BYTES = 32;
    private static final int CATALOG_FIXED_BODY_BYTES = 56;
    private static final int CATALOG_ENTRY_BYTES = 44;
    private static final int SHARD_FIXED_BODY_BYTES = 12;
    private static final int CHECKSUM_BYTES = Integer.BYTES;
    private static final int CELL_SIZE = 32;
    private static final int SUPER_CELL_SIZE = 32;
    private static final int MAXIMUM_CACHED_SHARDS = 64;
    private static final int MAX_CELL_ENTRIES = CELL_SIZE * CELL_SIZE;
    private static final int MAX_CELL_COUNT = 8_388_608;
    private static final int MAX_SAMPLE_COUNT = 65_536;
    private static final int MAX_PALETTE_COUNT = Short.MAX_VALUE + 1;
    private static final int MAX_BIOME_BYTES = 65_536;
    private static final int MAX_SHARD_BYTES = 64 * 1_024 * 1_024;
    private static final int TRANSFER_BYTES = 65_536;
    private static final long MAX_CATALOG_BYTES = CATALOG_FIXED_BODY_BYTES
            + (long) MAX_CELL_COUNT * CATALOG_ENTRY_BYTES
            + CHECKSUM_BYTES;
    private static final byte[] IDENTITY_DOMAIN = {
            0x49, 0x72, 0x69, 0x73, 0x54, 0x65, 0x72, 0x72,
            0x61, 0x69, 0x6E, 0x53, 0x69, 0x67, 0x04
    };

    private final Path dimensionRoot;
    private final Path directory;

    public TerrainBoundarySignatureStore(Path dimensionRoot) {
        Path root = Objects.requireNonNull(dimensionRoot, "Dimension root").toAbsolutePath().normalize();
        this.dimensionRoot = root;
        this.directory = root.resolve("iris").resolve("generation").resolve("boundaries").normalize();
        if (!directory.startsWith(root)) {
            throw new IllegalArgumentException("Terrain boundary path escapes the dimension root");
        }
    }

    public Path directory() {
        return directory;
    }

    public Path snapshotPath(long activationId) {
        requireActivation(activationId);
        return directory.resolve(fileName(activationId));
    }

    public synchronized Snapshot publish(
            long activationId,
            Collection<TerrainBoundarySignature> signatures
    ) throws IOException {
        requireActivation(activationId);
        List<TerrainBoundarySignature> normalized = normalize(signatures);
        ensureDirectory();
        try (GenerationPublicationLock ignored = GenerationPublicationLock.acquire(
                directory,
                ".activation-" + activationId + SNAPSHOT_FILE_SUFFIX + ".lock"
        )) {
            ensureDirectory();
            PublishedCatalog catalog = publishShards(activationId, normalized);
            return publishCatalog(activationId, catalog);
        }
    }

    public synchronized Snapshot publish(
            long activationId,
            GenerationBoundary boundary,
            SignatureSampler sampler
    ) throws IOException {
        requireActivation(activationId);
        Objects.requireNonNull(boundary, "boundary");
        Objects.requireNonNull(sampler, "sampler");
        ensureDirectory();
        try (GenerationPublicationLock ignored = GenerationPublicationLock.acquire(
                directory,
                ".activation-" + activationId + SNAPSHOT_FILE_SUFFIX + ".lock"
        )) {
            ensureDirectory();
            Long2ObjectOpenHashMap<BitSet> columns = new Long2ObjectOpenHashMap<>();
            boundary.forEachExposedBlockColumn((blockX, blockZ) -> {
                long cellKey = pack(Math.floorDiv(blockX, CELL_SIZE), Math.floorDiv(blockZ, CELL_SIZE));
                columns.computeIfAbsent(cellKey, unused -> new BitSet(MAX_CELL_ENTRIES))
                        .set(Math.floorMod(blockX, CELL_SIZE) * CELL_SIZE + Math.floorMod(blockZ, CELL_SIZE));
                if (columns.size() > MAX_CELL_COUNT) {
                    throw new IOException("Terrain boundary exceeds the maximum cell count");
                }
            });
            long[] cellKeys = columns.keySet().toLongArray();
            LongArrays.quickSort(cellKeys, TerrainBoundarySignatureStore::compareCoordinates);
            Long2ObjectOpenHashMap<CellReference> cells = new Long2ObjectOpenHashMap<>(cellKeys.length);
            int count = 0;
            for (long cellKey : cellKeys) {
                BitSet mask = columns.remove(cellKey);
                List<TerrainBoundarySignature> signatures = new ArrayList<>(mask.cardinality());
                for (int index = mask.nextSetBit(0); index >= 0; index = mask.nextSetBit(index + 1)) {
                    int blockX = Math.addExact(Math.multiplyExact(cellX(cellKey), CELL_SIZE), index / CELL_SIZE);
                    int blockZ = Math.addExact(Math.multiplyExact(cellZ(cellKey), CELL_SIZE), index % CELL_SIZE);
                    TerrainBoundarySignature signature = Objects.requireNonNull(
                            sampler.sample(blockX, blockZ), "boundary signature"
                    );
                    if (signature.blockX() != blockX || signature.blockZ() != blockZ) {
                        throw new IOException("Boundary signature coordinates changed while sampling "
                                + blockX + "," + blockZ + ".");
                    }
                    signatures.add(canonicalizeSignature(signature));
                }
                String hash = CellShard.publish(directory, signatures, cellX(cellKey), cellZ(cellKey));
                cells.put(cellKey, new CellReference(hash, signatures.size()));
                count = Math.addExact(count, signatures.size());
            }
            byte[] identity = computeIdentity(activationId, count, cellKeys, cells);
            return publishCatalog(activationId, new PublishedCatalog(
                    toHex(identity), encodeCatalog(activationId, count, cellKeys, cells, identity)
            ));
        }
    }

    private Snapshot publishCatalog(long activationId, PublishedCatalog catalog) throws IOException {
        Path target = snapshotPath(activationId);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            Snapshot existing = load(activationId);
            if (!existing.identity().equals(catalog.identity())) {
                throw new FileAlreadyExistsException(
                        "Terrain boundary activation " + activationId
                                + " already has a different snapshot: " + target
                );
            }
            return existing;
        }
        publishAtomic(target, catalog.encoded(), "terrain signature catalog");
        return load(activationId);
    }

    public Snapshot load(long activationId) throws IOException {
        requireActivation(activationId);
        validateStorageAncestors();
        Path snapshot = snapshotPath(activationId);
        requireRegularFile(snapshot, "Terrain boundary catalog");
        long fileSize = Files.size(snapshot);
        if (fileSize < CATALOG_FIXED_BODY_BYTES + CHECKSUM_BYTES) {
            throw invalid(snapshot, "truncated data");
        }
        if (fileSize > MAX_CATALOG_BYTES) {
            throw invalid(snapshot, "catalog exceeds the maximum size");
        }
        verifyChecksum(snapshot, fileSize, "terrain boundary catalog");
        long bodyBytes = fileSize - CHECKSUM_BYTES;
        try (InputStream fileInput = Files.newInputStream(snapshot);
             LimitedInputStream limitedInput = new LimitedInputStream(fileInput, bodyBytes);
             DataInputStream input = new DataInputStream(limitedInput)) {
            if (input.readInt() != MAGIC) {
                throw invalid(snapshot, "invalid magic");
            }
            int version = input.readUnsignedShort();
            if (version != SCHEMA_VERSION) {
                throw invalid(snapshot, "unsupported schema version " + version);
            }
            if (input.readUnsignedShort() != 0) {
                throw invalid(snapshot, "unsupported format flags");
            }
            long storedActivation = input.readLong();
            if (storedActivation != activationId) {
                throw invalid(snapshot, "activation ID does not match the snapshot path");
            }
            int entryCount = input.readInt();
            if (entryCount < 0) {
                throw invalid(snapshot, "invalid signature count " + entryCount);
            }
            byte[] storedIdentity = input.readNBytes(IDENTITY_BYTES);
            if (storedIdentity.length != IDENTITY_BYTES) {
                throw invalid(snapshot, "truncated snapshot identity");
            }
            int cellCount = input.readInt();
            if (cellCount < 0 || cellCount > MAX_CELL_COUNT) {
                throw invalid(snapshot, "invalid cell count " + cellCount);
            }
            long expectedSize = CATALOG_FIXED_BODY_BYTES
                    + (long) cellCount * CATALOG_ENTRY_BYTES
                    + CHECKSUM_BYTES;
            if (fileSize != expectedSize) {
                throw invalid(snapshot, "catalog length does not match the cell count");
            }

            long[] cellKeys = new long[cellCount];
            Long2ObjectOpenHashMap<CellReference> cells = new Long2ObjectOpenHashMap<>(cellCount);
            LinkedHashMap<String, List<TerrainBoundarySignature>> validated = new LinkedHashMap<>(
                    MAXIMUM_CACHED_SHARDS,
                    0.75F,
                    true
            );
            int countedEntries = 0;
            long previousCellKey = 0L;
            for (int index = 0; index < cellCount; index++) {
                int cellX = input.readInt();
                int cellZ = input.readInt();
                int count = input.readInt();
                if (count <= 0 || count > MAX_CELL_ENTRIES) {
                    throw invalid(snapshot, "invalid terrain signature cell count " + count);
                }
                long cellKey = pack(cellX, cellZ);
                if (index > 0 && compareCoordinates(previousCellKey, cellKey) >= 0) {
                    throw invalid(snapshot, "cell entries are not in canonical order");
                }
                byte[] hashBytes = input.readNBytes(IDENTITY_BYTES);
                if (hashBytes.length != IDENTITY_BYTES) {
                    throw invalid(snapshot, "truncated shard hash");
                }
                String hash = HexFormat.of().formatHex(hashBytes);
                List<TerrainBoundarySignature> signatures = validated.get(hash);
                if (signatures == null) {
                    Path shard = directory.resolve(shardFileName(hash));
                    requireRegularFile(shard, "Terrain boundary shard");
                    signatures = CellShard.read(shard, hash, cellX, cellZ);
                    validated.put(hash, signatures);
                    trim(validated);
                } else {
                    signatures = relocate(signatures, cellX, cellZ);
                }
                if (signatures.size() != count) {
                    throw invalid(snapshot, "terrain signature shard count does not match its catalog entry");
                }
                countedEntries = Math.addExact(countedEntries, count);
                cellKeys[index] = cellKey;
                cells.put(cellKey, new CellReference(hash, count));
                previousCellKey = cellKey;
            }
            if (limitedInput.remaining() != 0L) {
                throw invalid(snapshot, "unexpected trailing catalog data");
            }
            if (countedEntries != entryCount) {
                throw invalid(snapshot, "signature count does not match the referenced shards");
            }
            byte[] computedIdentity = computeIdentity(activationId, entryCount, cellKeys, cells);
            if (!MessageDigest.isEqual(storedIdentity, computedIdentity)) {
                throw invalid(snapshot, "terrain signature identity mismatch");
            }
            CellSource source = new CellSource(directory, cellKeys, cells);
            return new Snapshot(activationId, toHex(computedIdentity), entryCount, source);
        } catch (ArithmeticException error) {
            throw invalid(snapshot, "signature count overflows the supported range", error);
        } catch (EOFException error) {
            throw invalid(snapshot, "truncated data", error);
        }
    }

    private PublishedCatalog publishShards(
            long activationId,
            List<TerrainBoundarySignature> signatures
    ) throws IOException {
        Long2ObjectOpenHashMap<ArrayList<TerrainBoundarySignature>> grouped = new Long2ObjectOpenHashMap<>();
        for (TerrainBoundarySignature signature : signatures) {
            int cellX = Math.floorDiv(signature.blockX(), CELL_SIZE);
            int cellZ = Math.floorDiv(signature.blockZ(), CELL_SIZE);
            grouped.computeIfAbsent(pack(cellX, cellZ), ignored -> new ArrayList<>()).add(signature);
        }
        if (grouped.size() > MAX_CELL_COUNT) {
            throw new IOException("Terrain boundary exceeds the maximum cell count");
        }
        long[] cellKeys = grouped.keySet().toLongArray();
        LongArrays.quickSort(cellKeys, TerrainBoundarySignatureStore::compareCoordinates);
        Long2ObjectOpenHashMap<CellReference> cells = new Long2ObjectOpenHashMap<>(cellKeys.length);
        for (long cellKey : cellKeys) {
            List<TerrainBoundarySignature> cell = List.copyOf(grouped.get(cellKey));
            String hash = CellShard.publish(directory, cell, cellX(cellKey), cellZ(cellKey));
            cells.put(cellKey, new CellReference(hash, cell.size()));
        }
        byte[] identity = computeIdentity(activationId, signatures.size(), cellKeys, cells);
        byte[] encoded = encodeCatalog(activationId, signatures.size(), cellKeys, cells, identity);
        return new PublishedCatalog(toHex(identity), encoded);
    }

    private static List<TerrainBoundarySignature> normalize(
            Collection<TerrainBoundarySignature> signatures
    ) throws IOException {
        Collection<TerrainBoundarySignature> requiredSignatures = Objects.requireNonNull(
                signatures,
                "Terrain boundary signatures"
        );
        ArrayList<TerrainBoundarySignature> normalized = new ArrayList<>(requiredSignatures.size());
        for (TerrainBoundarySignature signature : requiredSignatures) {
            normalized.add(canonicalizeSignature(Objects.requireNonNull(
                    signature,
                    "Terrain boundary signature"
            )));
        }
        normalized.sort(Comparator
                .comparingInt(TerrainBoundarySignature::blockX)
                .thenComparingInt(TerrainBoundarySignature::blockZ));
        for (int index = 1; index < normalized.size(); index++) {
            TerrainBoundarySignature previous = normalized.get(index - 1);
            TerrainBoundarySignature current = normalized.get(index);
            if (previous.blockX() == current.blockX() && previous.blockZ() == current.blockZ()) {
                throw new IllegalArgumentException(
                        "Duplicate terrain boundary signature at " + current.blockX() + "," + current.blockZ()
                );
            }
        }
        return List.copyOf(normalized);
    }

    private static TerrainBoundarySignature canonicalizeSignature(TerrainBoundarySignature signature)
            throws IOException {
        TerrainBoundarySignature.BiomeEncoding biomes = signature.samples().biomes();
        List<String> sourcePalette = biomes.palette();
        short[] sourceIndices = biomes.paletteIndices();
        TreeSet<String> usedBiomes = new TreeSet<>();
        for (short sourceIndex : sourceIndices) {
            usedBiomes.add(sourcePalette.get(sourceIndex));
        }
        List<String> palette = List.copyOf(usedBiomes);
        HashMap<String, Integer> paletteIndexes = new HashMap<>(palette.size());
        for (int index = 0; index < palette.size(); index++) {
            paletteIndexes.put(palette.get(index), index);
        }
        short[] paletteIndices = new short[sourceIndices.length];
        for (int index = 0; index < sourceIndices.length; index++) {
            paletteIndices[index] = paletteIndexes.get(sourcePalette.get(sourceIndices[index])).shortValue();
        }
        TerrainBoundarySignature canonical = new TerrainBoundarySignature(
                signature.column(),
                new TerrainBoundarySignature.Samples(
                        signature.samples().layout(),
                        new TerrainBoundarySignature.BiomeEncoding(palette, paletteIndices)
                ),
                BoundaryColumnGeometry.fromVoxels(signature.geometry().minimumY(), signature.geometry().voxels())
        );
        validateSignature(canonical);
        return canonical;
    }

    private static void validateSignature(TerrainBoundarySignature signature) throws IOException {
        if (signature.sampleCount() < 0 || signature.sampleCount() > MAX_SAMPLE_COUNT) {
            throw new IllegalArgumentException("Invalid terrain boundary sample count: " + signature.sampleCount());
        }
        if (signature.samples().biomes().palette().size() > MAX_PALETTE_COUNT) {
            throw new IllegalArgumentException("Terrain boundary biome palette exceeds compact index capacity");
        }
        for (BoundaryColumnGeometry.Voxel voxel : signature.geometry().palette()) {
            encodeBiome(voxel.stateKey());
            encodeBiome(voxel.fluidStateKey());
        }
        for (String biome : signature.samples().biomes().palette()) {
            encodeBiome(Objects.requireNonNull(biome, "Terrain boundary biome"));
        }
    }

    private static byte[] computeIdentity(
            long activationId,
            int entryCount,
            long[] cellKeys,
            Long2ObjectOpenHashMap<CellReference> cells
    ) throws IOException {
        MessageDigest digest = sha256();
        digest.update(IDENTITY_DOMAIN);
        try (DigestOutputStream digestOutput = new DigestOutputStream(OutputStream.nullOutputStream(), digest);
             DataOutputStream output = new DataOutputStream(digestOutput)) {
            output.writeLong(activationId);
            output.writeInt(entryCount);
            output.writeInt(cellKeys.length);
            for (long cellKey : cellKeys) {
                CellReference cell = cells.get(cellKey);
                output.writeInt(cellX(cellKey));
                output.writeInt(cellZ(cellKey));
                output.writeInt(cell.count());
                output.write(HexFormat.of().parseHex(cell.hash()));
            }
        }
        return digest.digest();
    }

    private static byte[] encodeCatalog(
            long activationId,
            int entryCount,
            long[] cellKeys,
            Long2ObjectOpenHashMap<CellReference> cells,
            byte[] identity
    ) throws IOException {
        ByteArrayOutputStream bodyBytes = new ByteArrayOutputStream(
                CATALOG_FIXED_BODY_BYTES + cellKeys.length * CATALOG_ENTRY_BYTES
        );
        try (DataOutputStream output = new DataOutputStream(bodyBytes)) {
            output.writeInt(MAGIC);
            output.writeShort(SCHEMA_VERSION);
            output.writeShort(0);
            output.writeLong(activationId);
            output.writeInt(entryCount);
            output.write(identity);
            output.writeInt(cellKeys.length);
            for (long cellKey : cellKeys) {
                CellReference cell = cells.get(cellKey);
                output.writeInt(cellX(cellKey));
                output.writeInt(cellZ(cellKey));
                output.writeInt(cell.count());
                output.write(HexFormat.of().parseHex(cell.hash()));
            }
        }
        return withChecksum(bodyBytes.toByteArray());
    }

    private void ensureDirectory() throws IOException {
        requireSafeDirectory(dimensionRoot);
        Path irisDirectory = ensureChildDirectory(dimensionRoot, "iris");
        Path generationDirectory = ensureChildDirectory(irisDirectory, "generation");
        Path boundaryDirectory = ensureChildDirectory(generationDirectory, "boundaries");
        if (!boundaryDirectory.equals(directory)) {
            throw new IOException("Terrain boundary resolved outside its storage path: " + boundaryDirectory);
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
            throw new IOException("Terrain boundary path is not a safe directory: " + path);
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

    private static void publishAtomic(Path target, byte[] encoded, String kind) throws IOException {
        Path directory = target.getParent();
        Path temporary = Files.createTempFile(directory, ".terrain-boundary-", ".tmp");
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
                throw new IOException("Terrain boundary " + kind + " publication requires an atomic move", error);
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

    private static void verifyChecksum(Path file, long fileSize, String label) throws IOException {
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
                throw new IOException("Invalid " + label + " " + file + ": checksum mismatch");
            }
        }
    }

    private static byte[] encodeBiome(String biome) throws IOException {
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(biome));
            if (encoded.remaining() > MAX_BIOME_BYTES) {
                throw new IllegalArgumentException("Terrain boundary biome key exceeds maximum encoded length");
            }
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes;
        } catch (CharacterCodingException error) {
            throw new IOException("Terrain boundary biome key is not valid UTF-8", error);
        }
    }

    private static String decodeBiome(Path source, byte[] encoded) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(encoded))
                    .toString();
        } catch (CharacterCodingException error) {
            throw invalid(source, "biome key is not valid UTF-8", error);
        }
    }

    private static List<TerrainBoundarySignature> relocate(
            List<TerrainBoundarySignature> source,
            int cellX,
            int cellZ
    ) {
        int minimumX = Math.multiplyExact(cellX, CELL_SIZE);
        int minimumZ = Math.multiplyExact(cellZ, CELL_SIZE);
        ArrayList<TerrainBoundarySignature> relocated = new ArrayList<>(source.size());
        for (TerrainBoundarySignature signature : source) {
            int localX = Math.floorMod(signature.blockX(), CELL_SIZE);
            int localZ = Math.floorMod(signature.blockZ(), CELL_SIZE);
            relocated.add(new TerrainBoundarySignature(
                    new TerrainBoundarySignature.Column(
                            minimumX + localX,
                            minimumZ + localZ,
                            signature.surfaceHeight(),
                            signature.oceanFloorHeight(),
                            signature.fluidHeight(),
                            signature.upperCeilingDepth()
                    ),
                    signature.samples(),
                    signature.geometry()
            ));
        }
        return List.copyOf(relocated);
    }

    private static void trim(LinkedHashMap<?, ?> cache) {
        while (cache.size() > MAXIMUM_CACHED_SHARDS) {
            Iterator<?> entries = cache.entrySet().iterator();
            entries.next();
            entries.remove();
        }
    }

    private static long pack(int x, int z) {
        return ((long) x << Integer.SIZE) | (z & 0xffffffffL);
    }

    private static int cellX(long key) {
        return (int) (key >> Integer.SIZE);
    }

    private static int cellZ(long key) {
        return (int) key;
    }

    private static int compareCoordinates(long first, long second) {
        int xComparison = Integer.compare(cellX(first), cellX(second));
        return xComparison != 0 ? xComparison : Integer.compare(cellZ(first), cellZ(second));
    }

    private static String fileName(long activationId) {
        return "activation-" + activationId + SNAPSHOT_FILE_SUFFIX;
    }

    private static String shardFileName(String hash) {
        return "terrain-cell-" + hash + SHARD_FILE_SUFFIX;
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

    private static String toHex(byte[] encoded) {
        return HexFormat.of().formatHex(encoded);
    }

    private static void writeFully(FileChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    private static void readFully(FileChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) {
                throw new EOFException("Unexpected end of terrain boundary data");
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
            throw new IOException("Terrain boundary directory cannot be durability-synced: " + path, error);
        }
    }

    private static IOException invalid(Path path, String reason) {
        return new IOException("Invalid terrain boundary snapshot " + path + ": " + reason);
    }

    private static IOException invalid(Path path, String reason, Throwable cause) {
        return new IOException("Invalid terrain boundary snapshot " + path + ": " + reason, cause);
    }

    @FunctionalInterface
    public interface SignatureSampler extends AutoCloseable {
        TerrainBoundarySignature sample(int blockX, int blockZ) throws IOException;

        @Override
        default void close() throws IOException {
        }
    }

    public static final class Snapshot {
        private final long activationId;
        private final String identity;
        private final int size;
        private final CellSource source;

        private Snapshot(long activationId, String identity, int size, CellSource source) {
            this.activationId = activationId;
            this.identity = Objects.requireNonNull(identity, "Terrain boundary identity");
            this.size = size;
            this.source = Objects.requireNonNull(source, "Terrain boundary source");
        }

        public long activationId() {
            return activationId;
        }

        public String identity() {
            return identity;
        }

        public int size() {
            return size;
        }

        public Optional<TerrainBoundarySignature> signatureAt(int blockX, int blockZ) {
            try {
                return Optional.ofNullable(source.signatureAt(blockX, blockZ));
            } catch (IOException error) {
                throw new IllegalStateException("Unable to load terrain boundary signature", error);
            }
        }

        public List<TerrainBoundarySignature> signatures() {
            try {
                return source.all();
            } catch (IOException error) {
                throw new IllegalStateException("Unable to materialize terrain boundary signatures", error);
            }
        }

        List<TerrainBoundarySignature> nearestCandidatesForChunk(int chunkX, int chunkZ, int searchWidth) {
            try {
                return source.nearestCandidatesForChunk(chunkX, chunkZ, searchWidth);
            } catch (IOException error) {
                throw new IllegalStateException("Unable to query terrain boundary signatures", error);
            }
        }

        boolean intersectsTerrainBand(BlockBounds footprint, int width) {
            try {
                return source.intersectsTerrainBand(footprint, width);
            } catch (IOException error) {
                throw new IllegalStateException("Unable to query terrain boundary signatures", error);
            }
        }

        long catalogProbeCount() {
            return source.catalogProbeCount();
        }

        long shardLoadCount() {
            return source.shardLoadCount();
        }

        int cachedShardCount() {
            return source.cachedShardCount();
        }
    }

    private record CellReference(String hash, int count) {
    }

    private record PublishedCatalog(String identity, byte[] encoded) {
    }

    record BlockBounds(long minimumX, long minimumZ, long maximumX, long maximumZ) {
        private static BlockBounds cell(long key, int size) {
            long minimumX = (long) cellX(key) * size;
            long minimumZ = (long) cellZ(key) * size;
            return new BlockBounds(minimumX, minimumZ, minimumX + size - 1L, minimumZ + size - 1L);
        }

        private BlockBounds expanded(long width) {
            return new BlockBounds(minimumX - width, minimumZ - width, maximumX + width, maximumZ + width);
        }

        private double distanceSquared(BlockBounds other) {
            long x = Math.max(0L, Math.max(minimumX - other.maximumX, other.minimumX - maximumX));
            long z = Math.max(0L, Math.max(minimumZ - other.maximumZ, other.minimumZ - maximumZ));
            return (double) x * x + (double) z * z;
        }

        private double distanceSquared(long blockX, long blockZ) {
            long x = Math.max(0L, Math.max(minimumX - blockX, blockX - maximumX));
            long z = Math.max(0L, Math.max(minimumZ - blockZ, blockZ - maximumZ));
            return (double) x * x + (double) z * z;
        }
    }

    private record CellCandidate(long key, boolean superCell, double distanceSquared) {
    }

    @FunctionalInterface
    private interface SuperCellVisitor {
        boolean visit(long key) throws IOException;
    }

    private static final class ChunkNearest {
        private static final int NEIGHBOURS = 4;
        private static final int COLUMNS = GenerationBoundary.CHUNK_SIZE * GenerationBoundary.CHUNK_SIZE;

        private final BlockBounds chunk;
        private final TerrainBoundarySignature[][] signatures = new TerrainBoundarySignature[COLUMNS][NEIGHBOURS];
        private final double[][] distancesSquared = new double[COLUMNS][NEIGHBOURS];
        private final int[] counts = new int[COLUMNS];
        private final double maximumDistanceSquared;
        private double limitSquared;

        private ChunkNearest(BlockBounds chunk, int searchWidth) {
            this.chunk = chunk;
            this.maximumDistanceSquared = (double) searchWidth * searchWidth;
            this.limitSquared = maximumDistanceSquared;
        }

        private double limitSquared() {
            return limitSquared;
        }

        private double limitSquared(int column) {
            return counts[column] < NEIGHBOURS ? maximumDistanceSquared : distancesSquared[column][NEIGHBOURS - 1];
        }

        private boolean canImprove(BlockBounds bounds) {
            for (int column = 0; column < COLUMNS; column++) {
                if (bounds.distanceSquared(blockX(column), blockZ(column)) <= limitSquared(column)) {
                    return true;
                }
            }
            return false;
        }

        private void offer(BlockBounds bounds, List<TerrainBoundarySignature> candidates) {
            limitSquared = 0D;
            for (int column = 0; column < COLUMNS; column++) {
                long blockX = blockX(column);
                long blockZ = blockZ(column);
                if (bounds.distanceSquared(blockX, blockZ) <= limitSquared(column)) {
                    for (TerrainBoundarySignature candidate : candidates) {
                        long x = candidate.blockX() - blockX;
                        long z = candidate.blockZ() - blockZ;
                        offer(column, candidate, (double) x * x + (double) z * z);
                    }
                }
                limitSquared = Math.max(limitSquared, limitSquared(column));
            }
        }

        private void offer(int column, TerrainBoundarySignature candidate, double distanceSquared) {
            if (distanceSquared > limitSquared(column)) {
                return;
            }
            int count = counts[column];
            int insertion = 0;
            while (insertion < count) {
                int comparison = Double.compare(distancesSquared[column][insertion], distanceSquared);
                TerrainBoundarySignature existing = signatures[column][insertion];
                if (comparison > 0 || comparison == 0 && compareCoordinates(
                        pack(existing.blockX(), existing.blockZ()), pack(candidate.blockX(), candidate.blockZ())
                ) > 0) {
                    break;
                }
                insertion++;
            }
            if (insertion >= NEIGHBOURS) {
                return;
            }
            int copyLength = Math.min(count, NEIGHBOURS - 1) - insertion;
            if (copyLength > 0) {
                System.arraycopy(signatures[column], insertion, signatures[column], insertion + 1, copyLength);
                System.arraycopy(distancesSquared[column], insertion, distancesSquared[column], insertion + 1, copyLength);
            }
            signatures[column][insertion] = candidate;
            distancesSquared[column][insertion] = distanceSquared;
            counts[column] = Math.min(NEIGHBOURS, count + 1);
        }

        private List<TerrainBoundarySignature> candidates() {
            Long2ObjectOpenHashMap<TerrainBoundarySignature> candidates = new Long2ObjectOpenHashMap<>();
            for (int column = 0; column < COLUMNS; column++) {
                for (int index = 0; index < counts[column]; index++) {
                    TerrainBoundarySignature signature = signatures[column][index];
                    candidates.put(pack(signature.blockX(), signature.blockZ()), signature);
                }
            }
            return List.copyOf(candidates.values());
        }

        private long blockX(int column) {
            return chunk.minimumX() + column / GenerationBoundary.CHUNK_SIZE;
        }

        private long blockZ(int column) {
            return chunk.minimumZ() + column % GenerationBoundary.CHUNK_SIZE;
        }
    }

    private static final class CellSource {
        private final Path directory;
        private final long[] cellKeys;
        private final Long2ObjectOpenHashMap<CellReference> cells;
        private final Long2ObjectOpenHashMap<long[]> cellsBySuperCell;
        private final LinkedHashMap<Long, List<TerrainBoundarySignature>> cache;
        private final Map<Long, CompletableFuture<List<TerrainBoundarySignature>>> pendingLoads = new HashMap<>();
        private final LongAdder catalogProbes = new LongAdder();
        private final LongAdder shardLoads = new LongAdder();

        private CellSource(
                Path directory,
                long[] cellKeys,
                Long2ObjectOpenHashMap<CellReference> cells
        ) {
            this.directory = directory;
            this.cellKeys = cellKeys;
            this.cells = cells;
            this.cellsBySuperCell = indexSuperCells(cellKeys);
            this.cache = new LinkedHashMap<>(MAXIMUM_CACHED_SHARDS, 0.75F, true);
        }

        private TerrainBoundarySignature signatureAt(int blockX, int blockZ) throws IOException {
            int cellX = Math.floorDiv(blockX, CELL_SIZE);
            int cellZ = Math.floorDiv(blockZ, CELL_SIZE);
            long cellKey = pack(cellX, cellZ);
            CellReference reference = cells.get(cellKey);
            if (reference == null) {
                return null;
            }
            for (TerrainBoundarySignature signature : load(cellKey, reference)) {
                if (signature.blockX() == blockX && signature.blockZ() == blockZ) {
                    return signature;
                }
            }
            return null;
        }

        private List<TerrainBoundarySignature> all() throws IOException {
            ArrayList<TerrainBoundarySignature> signatures = new ArrayList<>();
            for (long cellKey : cellKeys) {
                signatures.addAll(load(cellKey, cells.get(cellKey)));
            }
            signatures.sort(Comparator
                    .comparingInt(TerrainBoundarySignature::blockX)
                    .thenComparingInt(TerrainBoundarySignature::blockZ));
            return List.copyOf(signatures);
        }

        private List<TerrainBoundarySignature> nearestCandidatesForChunk(
                int chunkX,
                int chunkZ,
                int searchWidth
        ) throws IOException {
            BlockBounds chunk = BlockBounds.cell(pack(chunkX, chunkZ), GenerationBoundary.CHUNK_SIZE);
            ChunkNearest nearest = new ChunkNearest(chunk, searchWidth);
            PriorityQueue<CellCandidate> candidates = new PriorityQueue<>(Comparator
                    .comparingDouble(CellCandidate::distanceSquared)
                    .thenComparingLong(CellCandidate::key)
                    .thenComparing(CellCandidate::superCell));
            visitSuperCells(chunk.expanded(searchWidth), key -> {
                BlockBounds bounds = BlockBounds.cell(key, CELL_SIZE * SUPER_CELL_SIZE);
                double distanceSquared = chunk.distanceSquared(bounds);
                if (distanceSquared <= nearest.limitSquared()) {
                    candidates.add(new CellCandidate(key, true, distanceSquared));
                }
                return false;
            });
            while (!candidates.isEmpty()) {
                CellCandidate candidate = candidates.remove();
                if (candidate.distanceSquared() > nearest.limitSquared()) {
                    break;
                }
                if (candidate.superCell()) {
                    for (long key : cellsBySuperCell.get(candidate.key())) {
                        double distanceSquared = chunk.distanceSquared(BlockBounds.cell(key, CELL_SIZE));
                        if (distanceSquared <= nearest.limitSquared()) {
                            candidates.add(new CellCandidate(key, false, distanceSquared));
                        }
                    }
                    continue;
                }
                BlockBounds bounds = BlockBounds.cell(candidate.key(), CELL_SIZE);
                if (nearest.canImprove(bounds)) {
                    nearest.offer(bounds, load(candidate.key(), cells.get(candidate.key())));
                }
            }
            return nearest.candidates();
        }

        private boolean intersectsTerrainBand(BlockBounds footprint, int width) throws IOException {
            double distanceSquared = (double) width * width;
            return visitSuperCells(footprint.expanded(width - 1L), superKey -> {
                if (footprint.distanceSquared(BlockBounds.cell(superKey, CELL_SIZE * SUPER_CELL_SIZE))
                        >= distanceSquared) {
                    return false;
                }
                for (long cellKey : cellsBySuperCell.get(superKey)) {
                    if (footprint.distanceSquared(BlockBounds.cell(cellKey, CELL_SIZE)) >= distanceSquared) {
                        continue;
                    }
                    for (TerrainBoundarySignature signature : load(cellKey, cells.get(cellKey))) {
                        if (footprint.distanceSquared(signature.blockX(), signature.blockZ()) < distanceSquared) {
                            return true;
                        }
                    }
                }
                return false;
            });
        }

        private boolean visitSuperCells(BlockBounds bounds, SuperCellVisitor visitor) throws IOException {
            int size = CELL_SIZE * SUPER_CELL_SIZE;
            int minimumX = saturatedFloorDiv(bounds.minimumX(), size);
            int minimumZ = saturatedFloorDiv(bounds.minimumZ(), size);
            int maximumX = saturatedFloorDiv(bounds.maximumX(), size);
            int maximumZ = saturatedFloorDiv(bounds.maximumZ(), size);
            long width = (long) maximumX - minimumX + 1L;
            long height = (long) maximumZ - minimumZ + 1L;
            if (width > cellsBySuperCell.size() / height) {
                for (long key : cellsBySuperCell.keySet()) {
                    catalogProbes.increment();
                    if (cellX(key) >= minimumX && cellX(key) <= maximumX
                            && cellZ(key) >= minimumZ && cellZ(key) <= maximumZ
                            && visitor.visit(key)) {
                        return true;
                    }
                }
                return false;
            }
            for (long x = minimumX; x <= maximumX; x++) {
                for (long z = minimumZ; z <= maximumZ; z++) {
                    catalogProbes.increment();
                    long key = pack((int) x, (int) z);
                    if (cellsBySuperCell.containsKey(key) && visitor.visit(key)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private List<TerrainBoundarySignature> load(long cellKey, CellReference reference) throws IOException {
            CompletableFuture<List<TerrainBoundarySignature>> pending;
            boolean loader;
            synchronized (this) {
                List<TerrainBoundarySignature> cached = cache.get(cellKey);
                if (cached != null) {
                    return cached;
                }
                pending = pendingLoads.get(cellKey);
                loader = pending == null;
                if (loader) {
                    pending = new CompletableFuture<>();
                    pendingLoads.put(cellKey, pending);
                }
            }
            if (!loader) {
                try {
                    return pending.join();
                } catch (CompletionException failure) {
                    if (failure.getCause() instanceof IOException io) {
                        throw io;
                    }
                    throw failure;
                }
            }
            try {
                Path shard = directory.resolve(shardFileName(reference.hash()));
                requireRegularFile(shard, "Terrain boundary shard");
                List<TerrainBoundarySignature> loaded = CellShard.read(
                        shard, reference.hash(), cellX(cellKey), cellZ(cellKey));
                if (loaded.size() != reference.count()) {
                    throw new IOException("Terrain boundary shard count does not match its catalog entry");
                }
                synchronized (this) {
                    shardLoads.increment();
                    cache.put(cellKey, loaded);
                    trim(cache);
                }
                pending.complete(loaded);
                return loaded;
            } catch (IOException | RuntimeException | Error failure) {
                pending.completeExceptionally(failure);
                throw failure;
            } finally {
                synchronized (this) {
                    pendingLoads.remove(cellKey, pending);
                }
            }
        }

        private long catalogProbeCount() {
            return catalogProbes.sum();
        }

        private long shardLoadCount() {
            return shardLoads.sum();
        }

        private synchronized int cachedShardCount() {
            return cache.size();
        }

        private static Long2ObjectOpenHashMap<long[]> indexSuperCells(long[] cellKeys) {
            Long2ObjectOpenHashMap<LongArrayList> mutable = new Long2ObjectOpenHashMap<>();
            for (long cellKey : cellKeys) {
                int superX = Math.floorDiv(cellX(cellKey), SUPER_CELL_SIZE);
                int superZ = Math.floorDiv(cellZ(cellKey), SUPER_CELL_SIZE);
                mutable.computeIfAbsent(pack(superX, superZ), ignored -> new LongArrayList()).add(cellKey);
            }
            Long2ObjectOpenHashMap<long[]> indexed = new Long2ObjectOpenHashMap<>(mutable.size());
            for (Map.Entry<Long, LongArrayList> entry : mutable.long2ObjectEntrySet()) {
                indexed.put(entry.getKey(), entry.getValue().toLongArray());
            }
            return indexed;
        }

        private static int saturatedFloorDiv(long value, int divisor) {
            long divided = Math.floorDiv(value, divisor);
            if (divided < Integer.MIN_VALUE) {
                return Integer.MIN_VALUE;
            }
            return divided > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) divided;
        }
    }

    private static final class CellShard {
        private static String publish(
                Path directory,
                List<TerrainBoundarySignature> signatures,
                int cellX,
                int cellZ
        ) throws IOException {
            byte[] encoded = encode(signatures, cellX, cellZ);
            String hash = sha256(encoded);
            Path target = directory.resolve(shardFileName(hash));
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                requireRegularFile(target, "Terrain boundary shard");
                read(target, hash, cellX, cellZ);
                return hash;
            }
            try {
                publishAtomic(target, encoded, "terrain signature shard");
            } catch (FileAlreadyExistsException race) {
                requireRegularFile(target, "Terrain boundary shard");
                read(target, hash, cellX, cellZ);
            }
            return hash;
        }

        private static byte[] encode(
                List<TerrainBoundarySignature> signatures,
                int cellX,
                int cellZ
        ) throws IOException {
            if (signatures.isEmpty() || signatures.size() > MAX_CELL_ENTRIES) {
                throw new IOException("Terrain boundary cell is empty or exceeds its entry limit");
            }
            ByteArrayOutputStream bodyBytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bodyBytes)) {
                output.writeInt(SHARD_MAGIC);
                output.writeShort(SHARD_SCHEMA_VERSION);
                output.writeShort(0);
                output.writeInt(signatures.size());
                for (TerrainBoundarySignature signature : signatures) {
                    int localX = Math.subtractExact(signature.blockX(), Math.multiplyExact(cellX, CELL_SIZE));
                    int localZ = Math.subtractExact(signature.blockZ(), Math.multiplyExact(cellZ, CELL_SIZE));
                    if (localX < 0 || localX >= CELL_SIZE || localZ < 0 || localZ >= CELL_SIZE) {
                        throw new IOException("Terrain signature does not belong to its cell");
                    }
                    output.writeByte(localX);
                    output.writeByte(localZ);
                    writeSignature(output, signature);
                    if (bodyBytes.size() > MAX_SHARD_BYTES - CHECKSUM_BYTES) {
                        throw new IOException("Terrain boundary shard exceeds its size limit");
                    }
                }
            }
            return withChecksum(bodyBytes.toByteArray());
        }

        private static List<TerrainBoundarySignature> read(
                Path file,
                String expectedHash,
                int cellX,
                int cellZ
        ) throws IOException {
            long size = Files.size(file);
            if (size < SHARD_FIXED_BODY_BYTES + CHECKSUM_BYTES || size > MAX_SHARD_BYTES) {
                throw invalid(file, "terrain signature shard has an invalid length");
            }
            byte[] encoded = Files.readAllBytes(file);
            if (!sha256(encoded).equals(expectedHash)) {
                throw invalid(file, "terrain signature shard content hash mismatch");
            }
            int bodyLength = encoded.length - CHECKSUM_BYTES;
            CRC32 checksum = new CRC32();
            checksum.update(encoded, 0, bodyLength);
            if (ByteBuffer.wrap(encoded, bodyLength, CHECKSUM_BYTES).getInt()
                    != (int) checksum.getValue()) {
                throw invalid(file, "terrain signature shard checksum mismatch");
            }
            try (DataInputStream input = new DataInputStream(
                    new ByteArrayInputStream(encoded, 0, bodyLength)
            )) {
                if (input.readInt() != SHARD_MAGIC) {
                    throw invalid(file, "terrain signature shard has invalid magic");
                }
                int version = input.readUnsignedShort();
                if (version != SHARD_SCHEMA_VERSION) {
                    throw invalid(file, "terrain signature shard has unsupported schema version " + version);
                }
                if (input.readUnsignedShort() != 0) {
                    throw invalid(file, "terrain signature shard has unsupported flags");
                }
                int count = input.readInt();
                if (count <= 0 || count > MAX_CELL_ENTRIES) {
                    throw invalid(file, "terrain signature shard has invalid entry count " + count);
                }
                ArrayList<TerrainBoundarySignature> signatures = new ArrayList<>(count);
                int previousLocalX = -1;
                int previousLocalZ = -1;
                for (int index = 0; index < count; index++) {
                    int localX = input.readUnsignedByte();
                    int localZ = input.readUnsignedByte();
                    if (localX >= CELL_SIZE || localZ >= CELL_SIZE
                            || index > 0 && compareLocal(previousLocalX, previousLocalZ, localX, localZ) >= 0) {
                        throw invalid(file, "terrain signature shard coordinates are not canonical");
                    }
                    int blockX = Math.addExact(Math.multiplyExact(cellX, CELL_SIZE), localX);
                    int blockZ = Math.addExact(Math.multiplyExact(cellZ, CELL_SIZE), localZ);
                    signatures.add(readSignature(file, input, blockX, blockZ));
                    previousLocalX = localX;
                    previousLocalZ = localZ;
                }
                if (input.available() != 0) {
                    throw invalid(file, "unexpected trailing terrain signature shard data");
                }
                return List.copyOf(signatures);
            } catch (IllegalArgumentException | ArithmeticException error) {
                throw invalid(file, "invalid terrain signature content", error);
            }
        }

        private static int compareLocal(int firstX, int firstZ, int secondX, int secondZ) {
            int xComparison = Integer.compare(firstX, secondX);
            return xComparison != 0 ? xComparison : Integer.compare(firstZ, secondZ);
        }

        private static void writeSignature(
                DataOutputStream output,
                TerrainBoundarySignature signature
        ) throws IOException {
            output.writeInt(signature.surfaceHeight());
            output.writeInt(signature.oceanFloorHeight());
            OptionalInt fluidHeight = signature.fluidHeight();
            output.writeByte(fluidHeight.isPresent() ? 1 : 0);
            if (fluidHeight.isPresent()) {
                output.writeInt(fluidHeight.getAsInt());
            }
            OptionalInt upperCeilingDepth = signature.upperCeilingDepth();
            output.writeByte(upperCeilingDepth.isPresent() ? 1 : 0);
            if (upperCeilingDepth.isPresent()) {
                output.writeInt(upperCeilingDepth.getAsInt());
            }
            TerrainBoundarySignature.VerticalLayout layout = signature.samples().layout();
            output.writeInt(layout.minimumY());
            output.writeInt(layout.sampleStep());
            output.writeInt(layout.sampleCount());
            TerrainBoundarySignature.BiomeEncoding biomes = signature.samples().biomes();
            output.writeInt(biomes.palette().size());
            for (String biome : biomes.palette()) {
                byte[] encodedBiome = encodeBiome(biome);
                output.writeInt(encodedBiome.length);
                output.write(encodedBiome);
            }
            for (short paletteIndex : biomes.paletteIndices()) {
                output.writeShort(paletteIndex);
            }
            writeGeometry(output, signature.geometry());
        }

        private static void writeGeometry(DataOutputStream output, BoundaryColumnGeometry geometry)
                throws IOException {
            output.writeInt(geometry.minimumY());
            output.writeInt(geometry.palette().size());
            for (BoundaryColumnGeometry.Voxel voxel : geometry.palette()) {
                writeGeometryKey(output, voxel.stateKey());
                output.writeByte(voxel.phase().ordinal());
                writeGeometryKey(output, voxel.fluidStateKey());
                output.writeBoolean(voxel.protectedContent());
            }
            int[] runEnds = geometry.runEnds();
            short[] indices = geometry.paletteIndices();
            output.writeInt(runEnds.length);
            for (int run = 0; run < runEnds.length; run++) {
                output.writeInt(runEnds[run]);
                output.writeShort(indices[run]);
            }
        }

        private static BoundaryColumnGeometry readGeometry(Path source, DataInputStream input)
                throws IOException {
            int minimumY = input.readInt();
            int paletteCount = input.readInt();
            if (paletteCount < 0 || paletteCount > MAX_PALETTE_COUNT) {
                throw invalid(source, "invalid geometry palette count " + paletteCount);
            }
            ArrayList<BoundaryColumnGeometry.Voxel> palette = new ArrayList<>(paletteCount);
            BoundaryColumnGeometry.Phase[] phases = BoundaryColumnGeometry.Phase.values();
            for (int index = 0; index < paletteCount; index++) {
                String stateKey = readGeometryKey(source, input);
                int phase = input.readUnsignedByte();
                if (phase >= phases.length) {
                    throw invalid(source, "invalid geometry phase " + phase);
                }
                String fluidKey = readGeometryKey(source, input);
                int protectedFlag = input.readUnsignedByte();
                if (protectedFlag > 1) {
                    throw invalid(source, "invalid geometry protection flag " + protectedFlag);
                }
                palette.add(new BoundaryColumnGeometry.Voxel(stateKey, phases[phase], fluidKey,
                        protectedFlag == 1));
            }
            int runCount = input.readInt();
            if (runCount < 0 || runCount > BoundaryColumnGeometry.MAXIMUM_HEIGHT
                    || (long) runCount * (Integer.BYTES + Short.BYTES) > input.available()) {
                throw invalid(source, "invalid geometry run count " + runCount);
            }
            int[] runEnds = new int[runCount];
            short[] indices = new short[runCount];
            for (int run = 0; run < runCount; run++) {
                runEnds[run] = input.readInt();
                indices[run] = input.readShort();
            }
            return new BoundaryColumnGeometry(minimumY, palette, runEnds, indices);
        }

        private static void writeGeometryKey(DataOutputStream output, String key) throws IOException {
            byte[] encoded = encodeBiome(key);
            output.writeInt(encoded.length);
            output.write(encoded);
        }

        private static String readGeometryKey(Path source, DataInputStream input) throws IOException {
            int encodedLength = input.readInt();
            if (encodedLength < 0 || encodedLength > MAX_BIOME_BYTES || encodedLength > input.available()) {
                throw invalid(source, "invalid geometry state key length " + encodedLength);
            }
            return decodeBiome(source, input.readNBytes(encodedLength));
        }

        private static TerrainBoundarySignature readSignature(
                Path source,
                DataInputStream input,
                int blockX,
                int blockZ
        ) throws IOException {
            int surfaceHeight = input.readInt();
            int oceanFloorHeight = input.readInt();
            int fluidFlag = input.readUnsignedByte();
            OptionalInt fluidHeight;
            if (fluidFlag == 0) {
                fluidHeight = OptionalInt.empty();
            } else if (fluidFlag == 1) {
                fluidHeight = OptionalInt.of(input.readInt());
            } else {
                throw invalid(source, "invalid fluid-height flag " + fluidFlag);
            }
            int upperCeilingFlag = input.readUnsignedByte();
            OptionalInt upperCeilingDepth;
            if (upperCeilingFlag == 0) {
                upperCeilingDepth = OptionalInt.empty();
            } else if (upperCeilingFlag == 1) {
                upperCeilingDepth = OptionalInt.of(input.readInt());
            } else {
                throw invalid(source, "invalid upper-ceiling flag " + upperCeilingFlag);
            }
            int minimumY = input.readInt();
            int sampleStep = input.readInt();
            int sampleCount = input.readInt();
            if (sampleCount < 0 || sampleCount > MAX_SAMPLE_COUNT) {
                throw invalid(source, "invalid sample count " + sampleCount);
            }
            int paletteCount = input.readInt();
            if (paletteCount < 0 || paletteCount > MAX_PALETTE_COUNT) {
                throw invalid(source, "invalid biome palette count " + paletteCount);
            }
            ArrayList<String> palette = new ArrayList<>(paletteCount);
            for (int index = 0; index < paletteCount; index++) {
                int encodedLength = input.readInt();
                if (encodedLength < 0 || encodedLength > MAX_BIOME_BYTES || encodedLength > input.available()) {
                    throw invalid(source, "invalid biome key length " + encodedLength);
                }
                byte[] encoded = input.readNBytes(encodedLength);
                if (encoded.length != encodedLength) {
                    throw invalid(source, "truncated biome key");
                }
                palette.add(decodeBiome(source, encoded));
            }
            if ((long) sampleCount * Short.BYTES > input.available()) {
                throw invalid(source, "truncated compact sample data");
            }
            short[] paletteIndices = new short[sampleCount];
            for (int index = 0; index < sampleCount; index++) {
                paletteIndices[index] = input.readShort();
            }
            return new TerrainBoundarySignature(
                    new TerrainBoundarySignature.Column(
                            blockX,
                            blockZ,
                            surfaceHeight,
                            oceanFloorHeight,
                            fluidHeight,
                            upperCeilingDepth
                    ),
                    new TerrainBoundarySignature.Samples(
                            new TerrainBoundarySignature.VerticalLayout(minimumY, sampleStep, sampleCount),
                            new TerrainBoundarySignature.BiomeEncoding(palette, paletteIndices)
                    ),
                    readGeometry(source, input)
            );
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
                throw new EOFException("Unexpected end of bounded terrain boundary input");
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
                throw new EOFException("Unexpected end of bounded terrain boundary input");
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
