package art.arcane.iris.engine.history;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public final class SavedBiomeStore {
    private static final Pattern REGION_FILE = Pattern.compile("r\\.(-?[0-9]+)\\.(-?[0-9]+)\\.ibio");
    private static final int MAGIC = 0x4942494F;
    private static final int FORMAT_VERSION = 1;
    private static final int HEADER_BYTES = 20;
    private static final int MAXIMUM_PALETTE_CELLS = 65_536;
    private static final int MAXIMUM_RECORD_BYTES = 16 * 1024 * 1024;
    private static final int MAXIMUM_DECODED_BYTES = 64 * 1024 * 1024;
    private static final long MAXIMUM_REGION_BYTES = 1024L * 1024L * 1024L;
    private static final int MAXIMUM_CACHED_REGIONS = 64;
    private static final int MAXIMUM_CACHED_CHUNKS = 128;
    private static final long MAXIMUM_CACHED_BYTES = 64L * 1024L * 1024L;
    private static final int MAXIMUM_WRITE_BATCH = 64;

    private final Path dimensionRoot;
    private final Path directory;
    private final WriteStripe[] regionLocks = new WriteStripe[64];
    private final LinkedHashMap<Long, RegionIndex> regions = new LinkedHashMap<>(64, 0.75F, true);
    private final LinkedHashMap<Long, Optional<SavedBiomeChunk>> chunks = new LinkedHashMap<>(128, 0.75F, true);
    private long cachedBytes;
    private volatile IOException writeFailure;

    private SavedBiomeStore(Path dimensionRoot) throws IOException {
        this.dimensionRoot = Objects.requireNonNull(dimensionRoot, "dimensionRoot").toAbsolutePath().normalize();
        directory = this.dimensionRoot.resolve("iris/generation/biomes");
        requireSafeAncestors();
        for (int index = 0; index < regionLocks.length; index++) {
            regionLocks[index] = new WriteStripe();
        }
    }

    public static SavedBiomeStore open(Path dimensionRoot) throws IOException {
        return new SavedBiomeStore(dimensionRoot);
    }

    public Path storageDirectory() {
        return directory;
    }

    public Optional<SavedBiomeChunk> get(int chunkX, int chunkZ) throws IOException {
        long chunkKey = key(chunkX, chunkZ);
        synchronized (chunks) {
            Optional<SavedBiomeChunk> cached = chunks.get(chunkKey);
            if (cached != null) {
                return cached;
            }
        }
        synchronized (regionLock(chunkX, chunkZ)) {
            requireWritable();
            synchronized (chunks) {
                Optional<SavedBiomeChunk> cached = chunks.get(chunkKey);
                if (cached != null) {
                    return cached;
                }
            }
            RegionIndex region = region(chunkX, chunkZ);
            int slot = slot(chunkX, chunkZ);
            Optional<SavedBiomeChunk> result = region.offsets[slot] == 0L
                    ? Optional.empty() : Optional.of(readRecord(region, slot));
            cacheChunk(chunkKey, result);
            return result;
        }
    }

    public Optional<SavedBiomeChunk> cached(int chunkX, int chunkZ) {
        synchronized (chunks) {
            return chunks.getOrDefault(key(chunkX, chunkZ), Optional.empty());
        }
    }

    public boolean claimAndPersist(SavedBiomeChunk chunk) throws IOException {
        SavedBiomeChunk required = Objects.requireNonNull(chunk, "chunk");
        WriteStripe lock = regionLock(required.chunkX(), required.chunkZ());
        if (!lock.writing) {
            synchronized (lock) {
                if (alreadyClaimed(required)) {
                    return false;
                }
            }
        }
        byte[] body = encode(required);
        byte[] record = ByteBuffer.allocate(body.length + 8)
                .putInt(body.length).put(body).putInt(checksum(body)).array();
        PendingWrite pending = new PendingWrite(required, record);
        lock.pending.add(pending);
        synchronized (lock) {
            if (!pending.completed) {
                lock.writing = true;
                try {
                    while (!pending.completed) {
                        persistPending(lock);
                    }
                } finally {
                    lock.writing = false;
                }
            }
            if (pending.failure != null) {
                throw pending.failure;
            }
            return pending.persisted;
        }
    }

    private void persistPending(WriteStripe stripe) {
        Map<Long, List<PendingWrite>> batches = new LinkedHashMap<>();
        PendingWrite grouping = null;
        try {
            for (int count = 0; count < MAXIMUM_WRITE_BATCH; count++) {
                grouping = stripe.pending.poll();
                if (grouping == null) {
                    break;
                }
                long regionKey = key(grouping.chunk.chunkX() >> 5, grouping.chunk.chunkZ() >> 5);
                batches.computeIfAbsent(regionKey, ignored -> new ArrayList<>()).add(grouping);
                grouping = null;
            }
            for (List<PendingWrite> batch : batches.values()) {
                persistBatch(batch);
            }
        } catch (RuntimeException | Error failure) {
            IOException unavailable = new IOException("Saved biome append failed unexpectedly", failure);
            writeFailure = unavailable;
            if (grouping != null) {
                grouping.failure = unavailable;
                grouping.completed = true;
            }
            for (List<PendingWrite> batch : batches.values()) {
                for (PendingWrite pending : batch) {
                    if (!pending.completed) {
                        pending.failure = unavailable;
                        pending.completed = true;
                    }
                }
            }
            throw failure;
        }
    }

    private void persistBatch(List<PendingWrite> batch) {
        Map<Integer, PendingWrite> claimed = new LinkedHashMap<>();
        try {
            for (PendingWrite pending : batch) {
                try {
                    if (alreadyClaimed(pending.chunk)) {
                        pending.completed = true;
                        continue;
                    }
                    int slot = slot(pending.chunk.chunkX(), pending.chunk.chunkZ());
                    PendingWrite previous = claimed.get(slot);
                    if (previous == null) {
                        claimed.put(slot, pending);
                    } else if (!previous.chunk.equals(pending.chunk)) {
                        throw new IOException("Conflicting saved biome claim at chunk "
                                + pending.chunk.chunkX() + ", " + pending.chunk.chunkZ());
                    }
                } catch (IOException failure) {
                    pending.failure = failure;
                    pending.completed = true;
                }
            }
            if (claimed.isEmpty()) {
                return;
            }
            requireWritable();
            SavedBiomeChunk first = batch.getFirst().chunk;
            RegionIndex region = region(first.chunkX(), first.chunkZ());
            ensureRegionFile(region);
            long offset = 0L;
            boolean durable = false;
            try (RandomAccessFile output = new RandomAccessFile(region.path.toFile(), "rw")) {
                offset = output.length();
                long length = 0L;
                for (PendingWrite pending : claimed.values()) {
                    length += pending.record.length;
                }
                if (offset + length > MAXIMUM_REGION_BYTES) {
                    throw new IOException("Saved biome region exceeds its storage limit: " + region.path);
                }
                try {
                    output.seek(offset);
                    for (PendingWrite pending : claimed.values()) {
                        output.write(pending.record);
                    }
                    output.getChannel().force(true);
                    durable = true;
                } catch (IOException failure) {
                    try {
                        output.setLength(offset);
                        output.getChannel().force(true);
                    } catch (IOException rollback) {
                        failure.addSuppressed(rollback);
                        writeFailure = failure;
                    }
                    throw failure;
                }
            } catch (IOException failure) {
                if (durable) {
                    writeFailure = failure;
                }
                throw failure;
            }
            for (PendingWrite pending : claimed.values()) {
                SavedBiomeChunk chunk = pending.chunk;
                int slot = slot(chunk.chunkX(), chunk.chunkZ());
                region.offsets[slot] = offset;
                region.lengths[slot] = pending.record.length - 8;
                cacheChunk(key(chunk.chunkX(), chunk.chunkZ()), Optional.of(chunk));
                pending.persisted = true;
                offset += pending.record.length;
            }
        } catch (IOException failure) {
            for (PendingWrite pending : batch) {
                if (!pending.completed) {
                    pending.failure = failure;
                }
            }
        }
        for (PendingWrite pending : batch) {
            pending.completed = true;
        }
    }

    public int discardUnstoredClaims(WorldChunkInventory inventory, Set<Long> selectedActivations) throws IOException {
        Objects.requireNonNull(inventory, "inventory");
        Set<Long> selected = Set.copyOf(selectedActivations);
        for (long activationId : selected) {
            if (activationId <= 0L) {
                throw new IllegalArgumentException("Saved biome activation must be positive");
            }
        }
        requireSafeAncestors();
        if (selected.isEmpty() || !Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return 0;
        }
        int removed = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "r.*.*.ibio")) {
            for (Path path : files) {
                Matcher matcher = REGION_FILE.matcher(path.getFileName().toString());
                if (!matcher.matches()) {
                    continue;
                }
                int regionX = parseRegionCoordinate(matcher.group(1));
                int regionZ = parseRegionCoordinate(matcher.group(2));
                synchronized (regionLock(regionX << 5, regionZ << 5)) {
                    requireWritable();
                    RegionIndex region = region(regionX << 5, regionZ << 5);
                    boolean[] discarded = new boolean[1024];
                    int count = 0;
                    for (int slot = 0; slot < 1024; slot++) {
                        int chunkX = (regionX << 5) + (slot & 31);
                        int chunkZ = (regionZ << 5) + (slot >> 5);
                        if (region.offsets[slot] != 0L && !inventory.contains(chunkX, chunkZ)
                                && selected.contains(readRecord(region, slot).activationId())) {
                            discarded[slot] = true;
                            count++;
                        }
                    }
                    if (count != 0) {
                        rewriteRegion(region, discarded);
                        removed = Math.addExact(removed, count);
                    }
                }
            }
        }
        return removed;
    }

    int cachedRegionCount() {
        synchronized (regions) {
            return regions.size();
        }
    }

    int cachedChunkCount() {
        synchronized (chunks) {
            return chunks.size();
        }
    }

    long cachedByteCount() {
        synchronized (chunks) {
            return cachedBytes;
        }
    }

    private boolean alreadyClaimed(SavedBiomeChunk chunk) throws IOException {
        requireWritable();
        Optional<SavedBiomeChunk> existing = get(chunk.chunkX(), chunk.chunkZ());
        if (existing.isEmpty()) {
            return false;
        }
        if (!existing.get().equals(chunk)) {
            throw new IOException("Conflicting saved biome claim at chunk " + chunk.chunkX() + ", " + chunk.chunkZ());
        }
        return true;
    }

    private RegionIndex region(int chunkX, int chunkZ) throws IOException {
        int regionX = chunkX >> 5;
        int regionZ = chunkZ >> 5;
        long regionKey = key(regionX, regionZ);
        synchronized (regions) {
            RegionIndex cached = regions.get(regionKey);
            if (cached != null) {
                return cached;
            }
        }
        requireSafeAncestors();
        RegionIndex loaded = new RegionIndex(directory.resolve("r." + regionX + "." + regionZ + ".ibio"), regionX, regionZ);
        if (Files.exists(loaded.path, LinkOption.NOFOLLOW_LINKS)) {
            loadIndex(loaded);
        }
        synchronized (regions) {
            regions.put(regionKey, loaded);
            while (regions.size() > MAXIMUM_CACHED_REGIONS) {
                regions.remove(regions.keySet().iterator().next());
            }
        }
        return loaded;
    }

    private void loadIndex(RegionIndex region) throws IOException {
        if (!Files.isRegularFile(region.path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Saved biome region is not a regular file: " + region.path);
        }
        try (RandomAccessFile input = new RandomAccessFile(region.path.toFile(), "rw")) {
            long fileLength = input.length();
            if (fileLength < HEADER_BYTES || fileLength > MAXIMUM_REGION_BYTES) {
                throw new IOException("Invalid saved biome region size: " + region.path);
            }
            byte[] header = new byte[HEADER_BYTES];
            input.readFully(header);
            ByteBuffer fields = ByteBuffer.wrap(header);
            if (fields.getInt() != MAGIC || fields.getInt() != FORMAT_VERSION
                    || fields.getInt() != region.regionX || fields.getInt() != region.regionZ
                    || fields.getInt() != checksum(header, HEADER_BYTES - 4)) {
                throw new IOException("Invalid saved biome region header: " + region.path);
            }
            byte[] buffer = new byte[8192];
            int records = 0;
            while (input.getFilePointer() < fileLength) {
                long offset = input.getFilePointer();
                if (fileLength - offset < Integer.BYTES) {
                    truncateTail(input, offset);
                    break;
                }
                int length = input.readInt();
                if (length < 12 || length > MAXIMUM_RECORD_BYTES) {
                    throw new IOException("Invalid saved biome record size: " + region.path);
                }
                if (fileLength - input.getFilePointer() < length + 4L) {
                    truncateTail(input, offset);
                    break;
                }
                CRC32 crc = new CRC32();
                input.readFully(buffer, 0, 8);
                crc.update(buffer, 0, 8);
                ByteBuffer coordinates = ByteBuffer.wrap(buffer, 0, 8);
                int chunkX = coordinates.getInt();
                int chunkZ = coordinates.getInt();
                for (int remaining = length - 8; remaining > 0;) {
                    int count = Math.min(remaining, buffer.length);
                    input.readFully(buffer, 0, count);
                    crc.update(buffer, 0, count);
                    remaining -= count;
                }
                if (input.readInt() != (int) crc.getValue() || chunkX >> 5 != region.regionX || chunkZ >> 5 != region.regionZ) {
                    throw new IOException("Invalid saved biome record checksum or coordinates: " + region.path);
                }
                int slot = slot(chunkX, chunkZ);
                if (++records > 1024 || region.offsets[slot] != 0L) {
                    throw new IOException("Duplicate saved biome chunk record: " + region.path);
                }
                region.offsets[slot] = offset;
                region.lengths[slot] = length;
            }
        }
    }

    private SavedBiomeChunk readRecord(RegionIndex region, int slot) throws IOException {
        byte[] encoded = new byte[region.lengths[slot]];
        try (RandomAccessFile input = new RandomAccessFile(region.path.toFile(), "r")) {
            input.seek(region.offsets[slot]);
            if (input.readInt() != encoded.length) {
                throw new IOException("Saved biome record length changed: " + region.path);
            }
            input.readFully(encoded);
            if (input.readInt() != checksum(encoded)) {
                throw new IOException("Saved biome record checksum mismatch: " + region.path);
            }
        }
        SavedBiomeChunk result = decode(encoded);
        if (result.chunkX() >> 5 != region.regionX || result.chunkZ() >> 5 != region.regionZ
                || slot(result.chunkX(), result.chunkZ()) != slot) {
            throw new IOException("Saved biome record coordinates do not match its index: " + region.path);
        }
        return result;
    }

    private void ensureRegionFile(RegionIndex region) throws IOException {
        requireSafeAncestors();
        Files.createDirectories(directory);
        if (Files.exists(region.path, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(region.path, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Saved biome region is not a regular file: " + region.path);
            }
            return;
        }
        ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES);
        header.putInt(MAGIC).putInt(FORMAT_VERSION).putInt(region.regionX).putInt(region.regionZ);
        header.putInt(checksum(header.array(), HEADER_BYTES - 4));
        Path temporary = Files.createTempFile(directory, ".biomes-", ".tmp");
        try {
            try (FileChannel output = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                header.flip();
                while (header.hasRemaining()) {
                    output.write(header);
                }
                output.force(true);
            }
            Files.move(temporary, region.path, StandardCopyOption.ATOMIC_MOVE);
            forceDirectory(directory);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void forceDirectory(Path path) throws IOException {
        if (File.separatorChar == '\\') {
            return;
        }
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (UnsupportedOperationException failure) {
            throw new IOException("Saved biome directory cannot be durability-synced: " + path, failure);
        }
    }

    private void rewriteRegion(RegionIndex region, boolean[] discarded) throws IOException {
        Path temporary = Files.createTempFile(directory, ".biomes-", ".tmp");
        try {
            try (FileChannel input = FileChannel.open(region.path, StandardOpenOption.READ);
                 FileChannel output = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                transfer(input, output, 0L, HEADER_BYTES);
                for (int slot = 0; slot < 1024; slot++) {
                    if (region.offsets[slot] != 0L && !discarded[slot]) {
                        transfer(input, output, region.offsets[slot], region.lengths[slot] + 8L);
                    }
                }
                output.force(true);
            }
            Files.move(temporary, region.path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            synchronized (regions) {
                regions.remove(key(region.regionX, region.regionZ));
            }
            synchronized (chunks) {
                for (int slot = 0; slot < 1024; slot++) {
                    long chunkKey = key((region.regionX << 5) + (slot & 31), (region.regionZ << 5) + (slot >> 5));
                    Optional<SavedBiomeChunk> removed = chunks.remove(chunkKey);
                    if (removed != null && removed.isPresent()) {
                        cachedBytes -= removed.get().estimatedBytes();
                    }
                }
            }
            forceDirectory(directory);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void transfer(FileChannel input, FileChannel output, long offset, long length) throws IOException {
        while (length > 0L) {
            long count = input.transferTo(offset, length, output);
            if (count <= 0L) {
                throw new IOException("Saved biome region ended during rewrite");
            }
            offset += count;
            length -= count;
        }
    }

    private static int parseRegionCoordinate(String coordinate) throws IOException {
        try {
            int parsed = Integer.parseInt(coordinate);
            if (parsed < Integer.MIN_VALUE >> 5 || parsed > Integer.MAX_VALUE >> 5) {
                throw new IOException("Saved biome region coordinate is out of range");
            }
            return parsed;
        } catch (NumberFormatException failure) {
            throw new IOException("Invalid saved biome region coordinate", failure);
        }
    }

    private void requireSafeAncestors() throws IOException {
        for (Path path = directory; path != null && path.startsWith(dimensionRoot); path = path.getParent()) {
            if (Files.isSymbolicLink(path)) {
                throw new IOException("Saved biome storage must not use symbolic links: " + path);
            }
        }
    }

    private void cacheChunk(long key, Optional<SavedBiomeChunk> value) {
        synchronized (chunks) {
            Optional<SavedBiomeChunk> previous = chunks.remove(key);
            if (previous != null && previous.isPresent()) {
                cachedBytes -= previous.get().estimatedBytes();
            }
            long weight = value.map(SavedBiomeChunk::estimatedBytes).orElse(0L);
            if (weight > MAXIMUM_CACHED_BYTES) {
                return;
            }
            chunks.put(key, value);
            cachedBytes += weight;
            Iterator<Map.Entry<Long, Optional<SavedBiomeChunk>>> entries = chunks.entrySet().iterator();
            while (chunks.size() > MAXIMUM_CACHED_CHUNKS || cachedBytes > MAXIMUM_CACHED_BYTES) {
                Optional<SavedBiomeChunk> removed = entries.next().getValue();
                cachedBytes -= removed.map(SavedBiomeChunk::estimatedBytes).orElse(0L);
                entries.remove();
            }
        }
    }

    private WriteStripe regionLock(int chunkX, int chunkZ) {
        long key = key(chunkX >> 5, chunkZ >> 5);
        return regionLocks[(int) (key ^ (key >>> 32)) & (regionLocks.length - 1)];
    }

    private static long key(int x, int z) {
        return (long) x << 32 | z & 0xffffffffL;
    }

    private static int slot(int chunkX, int chunkZ) {
        return (chunkZ & 31) * 32 + (chunkX & 31);
    }

    private static void truncateTail(RandomAccessFile file, long length) throws IOException {
        file.setLength(length);
        file.getChannel().force(true);
    }

    private static int checksum(byte[] bytes) {
        return checksum(bytes, bytes.length);
    }

    private static int checksum(byte[] bytes, int length) {
        CRC32 checksum = new CRC32();
        checksum.update(bytes, 0, length);
        return (int) checksum.getValue();
    }

    private static byte[] encode(SavedBiomeChunk chunk) throws IOException {
        LinkedHashMap<SavedBiomeChunk.Cell, Integer> palette = new LinkedHashMap<>();
        LinkedHashMap<SavedBiomeChunk.Column, Integer> columns = new LinkedHashMap<>();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                SavedBiomeChunk.Column column = chunk.column(x, z);
                columns.computeIfAbsent(column, ignored -> columns.size());
                palette.computeIfAbsent(column.surface(), ignored -> palette.size());
                palette.computeIfAbsent(column.caveBase(), ignored -> palette.size());
                for (SavedBiomeChunk.Span span : column.vertical()) {
                    palette.computeIfAbsent(span.cell(), ignored -> palette.size());
                }
            }
        }
        if (palette.size() > MAXIMUM_PALETTE_CELLS) {
            throw new IOException("Saved biome palette exceeds its cell limit");
        }
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(raw)) {
            output.writeLong(chunk.activationId());
            output.writeInt(chunk.header().minimumY());
            output.writeInt(chunk.header().height());
            output.writeInt(palette.size());
            for (SavedBiomeChunk.Cell cell : palette.keySet()) {
                output.writeLong(cell.activationId());
                output.writeByte(cell.isResolved() ? 0 : 1);
                if (cell.isResolved()) {
                    output.writeUTF(cell.biomeKey());
                    output.writeUTF(cell.regionKey());
                }
            }
            output.writeInt(columns.size());
            for (SavedBiomeChunk.Column column : columns.keySet()) {
                writeUnsigned(output, palette.get(column.surface()));
                writeUnsigned(output, palette.get(column.caveBase()));
                writeUnsigned(output, column.vertical().size());
                for (SavedBiomeChunk.Span span : column.vertical()) {
                    writeUnsigned(output, span.maximumYExclusive() - chunk.header().minimumY());
                    writeUnsigned(output, palette.get(span.cell()));
                }
            }
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    output.writeByte(columns.get(chunk.column(x, z)));
                }
            }
        }
        if (raw.size() > MAXIMUM_DECODED_BYTES) {
            throw new IOException("Saved biome chunk exceeds its decoded size limit");
        }
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(encoded);
        output.writeInt(chunk.chunkX());
        output.writeInt(chunk.chunkZ());
        output.writeInt(raw.size());
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        try (DeflaterOutputStream compressed = new DeflaterOutputStream(output, deflater)) {
            raw.writeTo(compressed);
        } finally {
            deflater.end();
        }
        if (encoded.size() > MAXIMUM_RECORD_BYTES) {
            throw new IOException("Saved biome chunk exceeds its encoded size limit");
        }
        return encoded.toByteArray();
    }

    private static SavedBiomeChunk decode(byte[] encoded) throws IOException {
        try (DataInputStream header = new DataInputStream(new ByteArrayInputStream(encoded))) {
            int chunkX = header.readInt();
            int chunkZ = header.readInt();
            int decodedLength = header.readInt();
            if (decodedLength < 24 || decodedLength > MAXIMUM_DECODED_BYTES) {
                throw new IOException("Invalid saved biome decoded size");
            }
            byte[] raw;
            try (InflaterInputStream compressed = new InflaterInputStream(header)) {
                raw = compressed.readNBytes(decodedLength);
                if (raw.length != decodedLength || compressed.read() != -1) {
                    throw new IOException("Saved biome compressed length mismatch");
                }
            }
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(raw))) {
                SavedBiomeChunk.Header metadata = new SavedBiomeChunk.Header(chunkX, chunkZ, input.readLong(), input.readInt(), input.readInt());
                DecodeBudget budget = new DecodeBudget();
                int paletteSize = input.readInt();
                if (paletteSize <= 0 || paletteSize > MAXIMUM_PALETTE_CELLS || paletteSize > input.available() / 9) {
                    throw new IOException("Invalid saved biome palette size");
                }
                budget.reserve(paletteSize * 64L);
                ArrayList<SavedBiomeChunk.Cell> palette = new ArrayList<>(paletteSize);
                for (int index = 0; index < paletteSize; index++) {
                    long activationId = input.readLong();
                    int resolution = input.readUnsignedByte();
                    SavedBiomeChunk.Cell cell = switch (resolution) {
                        case 0 -> new SavedBiomeChunk.Cell(activationId, readKey(input, budget), readKey(input, budget));
                        case 1 -> SavedBiomeChunk.Cell.unresolved(activationId);
                        default -> throw new IOException("Invalid saved biome resolution state");
                    };
                    palette.add(cell);
                }
                int columnCount = input.readInt();
                if (columnCount <= 0 || columnCount > 256) {
                    throw new IOException("Invalid saved biome column palette size");
                }
                budget.reserve(columnCount * 128L);
                ArrayList<SavedBiomeChunk.Column> columns = new ArrayList<>(columnCount);
                for (int index = 0; index < columnCount; index++) {
                    SavedBiomeChunk.Cell surface = paletteCell(palette, readUnsigned(input));
                    SavedBiomeChunk.Cell caveBase = paletteCell(palette, readUnsigned(input));
                    int count = readUnsigned(input);
                    if (count <= 0 || count > metadata.height() || count > input.available() / 2) {
                        throw new IOException("Invalid saved biome span count");
                    }
                    budget.reserve(count * 80L);
                    ArrayList<SavedBiomeChunk.Span> spans = new ArrayList<>(count);
                    int start = metadata.minimumY();
                    for (int spanIndex = 0; spanIndex < count; spanIndex++) {
                        int end = Math.addExact(metadata.minimumY(), readUnsigned(input));
                        spans.add(new SavedBiomeChunk.Span(start, end, paletteCell(palette, readUnsigned(input))));
                        start = end;
                    }
                    columns.add(new SavedBiomeChunk.Column(surface, caveBase, spans));
                }
                SavedBiomeChunk.Builder builder = SavedBiomeChunk.builder(metadata);
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int column = input.readUnsignedByte();
                        if (column >= columns.size()) {
                            throw new IOException("Invalid saved biome column index");
                        }
                        builder.column(x, z, columns.get(column));
                    }
                }
                if (input.read() != -1) {
                    throw new IOException("Unexpected saved biome payload bytes");
                }
                return builder.build();
            }
        } catch (IllegalArgumentException | ArithmeticException failure) {
            throw new IOException("Invalid saved biome chunk payload", failure);
        }
    }

    private static String readKey(DataInputStream input, DecodeBudget budget) throws IOException {
        int length = input.readUnsignedShort();
        if (length > ChunkGenerationSemantics.MAX_KEY_BYTES * 3 || length > input.available()) {
            throw new IOException("Invalid saved biome resource key size");
        }
        budget.reserve(64L + length * 4L);
        byte[] encoded = new byte[length + 2];
        ByteBuffer.wrap(encoded).putShort((short) length);
        input.readFully(encoded, 2, length);
        try (DataInputStream key = new DataInputStream(new ByteArrayInputStream(encoded))) {
            return key.readUTF();
        }
    }

    private static SavedBiomeChunk.Cell paletteCell(List<SavedBiomeChunk.Cell> palette, int index) throws IOException {
        if (index < 0 || index >= palette.size()) {
            throw new IOException("Invalid saved biome palette index");
        }
        return palette.get(index);
    }

    private static void writeUnsigned(DataOutputStream output, int value) throws IOException {
        while ((value & ~0x7f) != 0) {
            output.writeByte((value & 0x7f) | 0x80);
            value >>>= 7;
        }
        output.writeByte(value);
    }

    private static int readUnsigned(DataInputStream input) throws IOException {
        int result = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int value = input.readUnsignedByte();
            if (shift == 28 && (value & 0xf8) != 0) {
                throw new IOException("Invalid saved biome variable integer");
            }
            result |= (value & 0x7f) << shift;
            if ((value & 0x80) == 0) {
                return result;
            }
        }
        throw new IOException("Invalid saved biome variable integer");
    }

    private void requireWritable() throws IOException {
        if (writeFailure != null) {
            throw new IOException("Saved biome writes are unavailable after an uncertain append", writeFailure);
        }
    }

    private static final class WriteStripe {
        private final ConcurrentLinkedQueue<PendingWrite> pending = new ConcurrentLinkedQueue<>();
        private volatile boolean writing;
    }

    private static final class PendingWrite {
        private final SavedBiomeChunk chunk;
        private final byte[] record;
        private boolean completed;
        private boolean persisted;
        private IOException failure;

        private PendingWrite(SavedBiomeChunk chunk, byte[] record) {
            this.chunk = chunk;
            this.record = record;
        }
    }

    private static final class DecodeBudget {
        private long remaining = SavedBiomeChunk.MAXIMUM_ESTIMATED_BYTES - 8192L;

        private void reserve(long bytes) throws IOException {
            if (bytes < 0L || bytes > remaining) {
                throw new IOException("Saved biome chunk exceeds its decoded footprint limit");
            }
            remaining -= bytes;
        }
    }

    private static final class RegionIndex {
        private final Path path;
        private final int regionX;
        private final int regionZ;
        private final long[] offsets = new long[1024];
        private final int[] lengths = new int[1024];

        private RegionIndex(Path path, int regionX, int regionZ) {
            this.path = path;
            this.regionX = regionX;
            this.regionZ = regionZ;
        }
    }
}
