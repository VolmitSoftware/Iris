package art.arcane.iris.world.history;

import art.arcane.iris.world.storage.region.MCAFile;
import art.arcane.iris.world.storage.region.MCAUtil;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WorldChunkInventory {
    private static final int REGION_SIDE = 32;
    private static final int SECTOR_BYTES = 4_096;
    private static final int HEADER_SECTORS = 2;
    private static final int HEADER_BYTES = SECTOR_BYTES * HEADER_SECTORS;
    private static final Pattern REGION_FILE = Pattern.compile("^r\\.(-?\\d+)\\.(-?\\d+)\\.mca$");

    private final Long2ObjectOpenHashMap<BitSet> regionMasks;
    private final long[] regionKeys;
    private final int chunkCount;

    private WorldChunkInventory(Long2ObjectOpenHashMap<BitSet> regionMasks) {
        this.regionMasks = regionMasks;
        this.regionKeys = regionMasks.keySet().toLongArray();
        Arrays.sort(regionKeys);
        int count = 0;
        for (BitSet mask : regionMasks.values()) {
            count = Math.addExact(count, mask.cardinality());
        }
        this.chunkCount = count;
    }

    public static WorldChunkInventory empty() {
        return new WorldChunkInventory(new Long2ObjectOpenHashMap<>());
    }

    public static WorldChunkInventory ofPackedChunks(long... packedChunks) {
        Long2ObjectOpenHashMap<BitSet> masks = new Long2ObjectOpenHashMap<>();
        for (long packed : packedChunks) {
            int chunkX = ChunkGenerationOwnership.chunkX(packed);
            int chunkZ = ChunkGenerationOwnership.chunkZ(packed);
            long regionKey = ChunkGenerationOwnership.packChunk(chunkX >> 5, chunkZ >> 5);
            masks.computeIfAbsent(regionKey, ignored -> new BitSet(REGION_SIDE * REGION_SIDE))
                    .set(MCAFile.getChunkIndex(chunkX & 31, chunkZ & 31));
        }
        return new WorldChunkInventory(masks);
    }

    public static WorldChunkInventory scan(Path worldDirectory) throws IOException {
        return scanRegionDirectory(worldDirectory.resolve("region"));
    }

    public WorldChunkInventory filter(ChunkPredicate predicate) throws IOException {
        Objects.requireNonNull(predicate, "chunk predicate");
        Long2ObjectOpenHashMap<BitSet> selected = new Long2ObjectOpenHashMap<>(regionMasks.size());
        for (long regionKey : regionKeys) {
            int baseX = MCAUtil.regionToChunk(ChunkGenerationOwnership.chunkX(regionKey));
            int baseZ = MCAUtil.regionToChunk(ChunkGenerationOwnership.chunkZ(regionKey));
            BitSet source = regionMasks.get(regionKey);
            BitSet accepted = new BitSet(REGION_SIDE * REGION_SIDE);
            for (int index = source.nextSetBit(0); index >= 0; index = source.nextSetBit(index + 1)) {
                if (predicate.test(baseX + (index & 31), baseZ + (index >> 5))) {
                    accepted.set(index);
                }
            }
            if (!accepted.isEmpty()) {
                selected.put(regionKey, accepted);
            }
        }
        return new WorldChunkInventory(selected);
    }

    @FunctionalInterface
    public interface ChunkPredicate {
        boolean test(int chunkX, int chunkZ) throws IOException;
    }

    public static boolean isDurablyAllocated(Path worldDirectory, int chunkX, int chunkZ) throws IOException {
        Path regionDirectory = Objects.requireNonNull(worldDirectory, "worldDirectory")
                .toAbsolutePath()
                .normalize()
                .resolve("region");
        if (!Files.exists(regionDirectory, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        if (!Files.isDirectory(regionDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("World region path is not a directory: " + regionDirectory);
        }
        int regionX = Math.floorDiv(chunkX, REGION_SIDE);
        int regionZ = Math.floorDiv(chunkZ, REGION_SIDE);
        Path regionFile = regionDirectory.resolve(MCAUtil.createNameFromRegionLocation(regionX, regionZ));
        if (!Files.exists(regionFile, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        if (!Files.isRegularFile(regionFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("World region entry is not a regular file: " + regionFile);
        }
        BitSet mask = scanAllocationTable(regionFile);
        return mask.get(MCAFile.getChunkIndex(chunkX & 31, chunkZ & 31));
    }

    public static WorldChunkInventory scanRegionDirectory(Path regionDirectory) throws IOException {
        Path normalized = regionDirectory.toAbsolutePath().normalize();
        if (!Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)) {
            return empty();
        }
        if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("World region path is not a directory: " + normalized);
        }

        List<Path> regionFiles = collectRegionFiles(normalized);
        Long2ObjectOpenHashMap<BitSet> masks = new Long2ObjectOpenHashMap<>(regionFiles.size());
        for (Path regionFile : regionFiles) {
            RegionPosition position = parseRegionPosition(regionFile);
            long regionKey = ChunkGenerationOwnership.packChunk(position.x(), position.z());
            if (masks.containsKey(regionKey)) {
                throw new IOException(
                        "Duplicate world region file for " + position.x() + "," + position.z()
                );
            }
            masks.put(regionKey, scanAllocationTable(regionFile));
        }
        return new WorldChunkInventory(masks);
    }

    public int size() {
        return chunkCount;
    }

    public boolean isEmpty() {
        return chunkCount == 0;
    }

    public boolean contains(int chunkX, int chunkZ) {
        BitSet mask = regionMasks.get(ChunkGenerationOwnership.packChunk(chunkX >> 5, chunkZ >> 5));
        return mask != null && mask.get(MCAFile.getChunkIndex(chunkX & 31, chunkZ & 31));
    }

    public void forEach(ChunkConsumer consumer) throws IOException {
        Objects.requireNonNull(consumer, "consumer");
        for (long regionKey : regionKeys) {
            int baseX = MCAUtil.regionToChunk(ChunkGenerationOwnership.chunkX(regionKey));
            int baseZ = MCAUtil.regionToChunk(ChunkGenerationOwnership.chunkZ(regionKey));
            BitSet mask = regionMasks.get(regionKey);
            for (int index = mask.nextSetBit(0); index >= 0; index = mask.nextSetBit(index + 1)) {
                consumer.accept(baseX + (index & 31), baseZ + (index >> 5));
            }
        }
    }

    int retainedAllocationBytes() {
        return Math.multiplyExact(regionKeys.length, REGION_SIDE * REGION_SIDE / Byte.SIZE);
    }

    private static List<Path> collectRegionFiles(Path directory) throws IOException {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.mca")) {
            for (Path file : stream) {
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("World region entry is not a regular file: " + file);
                }
                files.add(file);
            }
        }
        files.sort(Comparator.comparing(path -> path.getFileName().toString()));
        return files;
    }

    private static RegionPosition parseRegionPosition(Path file) throws IOException {
        String name = file.getFileName().toString();
        Matcher matcher = REGION_FILE.matcher(name);
        if (!matcher.matches()) {
            throw new IOException("Invalid world region filename: " + file);
        }
        try {
            int regionX = Integer.parseInt(matcher.group(1));
            int regionZ = Integer.parseInt(matcher.group(2));
            if (!MCAUtil.createNameFromRegionLocation(regionX, regionZ).equals(name)) {
                throw new IOException("Noncanonical world region filename: " + file);
            }
            validateRegionCoordinate(file, regionX);
            validateRegionCoordinate(file, regionZ);
            return new RegionPosition(regionX, regionZ);
        } catch (NumberFormatException error) {
            throw new IOException("World region coordinate is outside the supported range: " + file, error);
        }
    }

    private static BitSet scanAllocationTable(Path file) throws IOException {
        try (RandomAccessFile input = new RandomAccessFile(file.toFile(), "r")) {
            long length = input.length();
            if (length == 0L) {
                return new BitSet(REGION_SIDE * REGION_SIDE);
            }
            if (length < HEADER_BYTES) {
                throw new IOException("Truncated world region file: " + file);
            }
            byte[] header = new byte[SECTOR_BYTES];
            input.readFully(header);
            ByteBuffer locations = ByteBuffer.wrap(header);
            length = input.length();
            if (length < HEADER_BYTES) {
                throw new IOException("Truncated world region file: " + file);
            }
            long sectorCapacity = length / SECTOR_BYTES + (length % SECTOR_BYTES == 0L ? 0L : 1L);
            BitSet claimedSectors = new BitSet(Math.toIntExact(sectorCapacity));
            claimedSectors.set(0, HEADER_SECTORS);
            BitSet mask = new BitSet(REGION_SIDE * REGION_SIDE);
            for (int localZ = 0; localZ < REGION_SIDE; localZ++) {
                for (int localX = 0; localX < REGION_SIDE; localX++) {
                    int index = MCAFile.getChunkIndex(localX, localZ);
                    int location = locations.getInt(index * Integer.BYTES);
                    if (location == 0) {
                        continue;
                    }
                    int sectorOffset = location >>> Byte.SIZE;
                    int sectorCount = location & 0xFF;
                    validateAllocation(file, sectorOffset, sectorCount, sectorCapacity, claimedSectors);
                    if (((long) sectorOffset + sectorCount) * SECTOR_BYTES > length) {
                        validateUnpaddedAllocation(file, input, sectorOffset, sectorCount, length);
                    }
                    mask.set(index);
                }
            }
            return mask;
        } catch (ArithmeticException error) {
            throw new IOException("World region file is too large to scan safely: " + file, error);
        }
    }

    private static void validateUnpaddedAllocation(
            Path file,
            RandomAccessFile input,
            int sectorOffset,
            int sectorCount,
            long fileLength
    ) throws IOException {
        long chunkStart = (long) sectorOffset * SECTOR_BYTES;
        if (chunkStart + Integer.BYTES >= fileLength) {
            throw new IOException("Truncated chunk payload in world region file: " + file);
        }
        input.seek(chunkStart);
        int payloadLength = input.readInt();
        long storedLength = Integer.BYTES + (long) payloadLength;
        if (payloadLength < 1 || storedLength > (long) sectorCount * SECTOR_BYTES
                || chunkStart + storedLength > fileLength) {
            throw new IOException("Truncated chunk payload in world region file: " + file);
        }
    }

    private static void validateAllocation(
            Path file,
            int sectorOffset,
            int sectorCount,
            long sectorCapacity,
            BitSet claimedSectors
    ) throws IOException {
        long allocationEnd = (long) sectorOffset + sectorCount;
        if (sectorOffset < HEADER_SECTORS || sectorCount == 0 || allocationEnd > sectorCapacity) {
            throw new IOException("Invalid chunk allocation in world region file: " + file);
        }
        int overlap = claimedSectors.nextSetBit(sectorOffset);
        if (overlap >= 0 && overlap < allocationEnd) {
            throw new IOException("Overlapping chunk allocations in world region file: " + file);
        }
        claimedSectors.set(sectorOffset, Math.toIntExact(allocationEnd));
    }

    private static void validateRegionCoordinate(Path file, int regionCoordinate) throws IOException {
        int minimum = Math.floorDiv(Integer.MIN_VALUE, REGION_SIDE);
        int maximum = Math.floorDiv(Integer.MAX_VALUE, REGION_SIDE);
        if (regionCoordinate < minimum || regionCoordinate > maximum) {
            throw new IOException("World region coordinate is outside the chunk coordinate range: " + file);
        }
    }

    @FunctionalInterface
    public interface ChunkConsumer {
        void accept(int chunkX, int chunkZ) throws IOException;
    }

    private record RegionPosition(int x, int z) {
    }
}
