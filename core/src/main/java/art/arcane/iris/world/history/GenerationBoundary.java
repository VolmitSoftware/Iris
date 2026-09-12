package art.arcane.iris.world.history;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class GenerationBoundary {
    public static final int CHUNK_SIZE = 16;

    private final String identity;
    private final int historicalChunkCount;
    private final HistoricalChunkSource historicalChunks;

    private GenerationBoundary(
            String identity,
            int historicalChunkCount,
            HistoricalChunkSource historicalChunks
    ) {
        this.identity = requireIdentity(identity);
        if (historicalChunkCount < 0) {
            throw new IllegalArgumentException("Historical chunk count cannot be negative");
        }
        this.historicalChunkCount = historicalChunkCount;
        this.historicalChunks = Objects.requireNonNull(historicalChunks, "Historical chunks");
    }

    public static GenerationBoundary freeze(String identity, Collection<ChunkCoordinate> historicalChunks) {
        Collection<ChunkCoordinate> requiredChunks = Objects.requireNonNull(
                historicalChunks,
                "Historical chunks"
        );
        long[] packedChunks = new long[requiredChunks.size()];
        int index = 0;
        for (ChunkCoordinate chunk : requiredChunks) {
            ChunkCoordinate coordinate = Objects.requireNonNull(chunk, "Historical chunk");
            packedChunks[index++] = packChunk(coordinate.chunkX(), coordinate.chunkZ());
        }
        return freezePacked(identity, packedChunks);
    }

    public static GenerationBoundary freezePacked(String identity, long[] historicalChunks) {
        long[] normalized = normalize(Objects.requireNonNull(historicalChunks, "Historical chunks"));
        return new GenerationBoundary(identity, normalized.length, new ArraySource(normalized));
    }

    static GenerationBoundary backed(
            String identity,
            int historicalChunkCount,
            HistoricalChunkSource historicalChunks
    ) {
        return new GenerationBoundary(identity, historicalChunkCount, historicalChunks);
    }

    public String identity() {
        return identity;
    }

    public int historicalChunkCount() {
        return historicalChunkCount;
    }

    public boolean isHistoricalChunk(int chunkX, int chunkZ) {
        try {
            return historicalChunks.contains(chunkX, chunkZ);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to read the frozen generation boundary", error);
        }
    }

    public boolean isHistoricalBlock(int blockX, int blockZ) {
        return isHistoricalChunk(blockToChunk(blockX), blockToChunk(blockZ));
    }

    public boolean intersectsHistoricalBlocks(
            int minimumX,
            int minimumZ,
            int maximumX,
            int maximumZ
    ) {
        if (minimumX > maximumX || minimumZ > maximumZ) {
            throw new IllegalArgumentException("Generation footprint bounds are inverted");
        }
        if (historicalChunkCount == 0) {
            return false;
        }
        int minimumChunkX = blockToChunk(minimumX);
        int maximumChunkX = blockToChunk(maximumX);
        int minimumChunkZ = blockToChunk(minimumZ);
        int maximumChunkZ = blockToChunk(maximumZ);
        long candidateColumns = (long) maximumChunkX - minimumChunkX + 1L;
        long candidateRows = (long) maximumChunkZ - minimumChunkZ + 1L;
        long candidateCount = multiplySaturated(candidateColumns, candidateRows);
        if (candidateCount <= historicalChunkCount) {
            for (long chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
                for (long chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                    if (isHistoricalChunk((int) chunkX, (int) chunkZ)) {
                        return true;
                    }
                }
            }
            return false;
        }
        boolean[] found = new boolean[1];
        try {
            historicalChunks.forEach((chunkX, chunkZ) -> {
                if (!found[0]
                        && chunkX >= minimumChunkX && chunkX <= maximumChunkX
                        && chunkZ >= minimumChunkZ && chunkZ <= maximumChunkZ) {
                    found[0] = true;
                }
            });
        } catch (IOException error) {
            throw new IllegalStateException("Unable to scan the frozen generation boundary", error);
        }
        return found[0];
    }

    public double distanceToHistoricalChunks(int blockX, int blockZ, int maximumDistanceBlocks) {
        if (maximumDistanceBlocks <= 0) {
            throw new IllegalArgumentException("Maximum distance must be positive");
        }
        if (historicalChunkCount == 0) {
            return maximumDistanceBlocks;
        }
        if (isHistoricalBlock(blockX, blockZ)) {
            return 0D;
        }

        long minimumChunkX = blockToChunk((long) blockX - maximumDistanceBlocks);
        long maximumChunkX = blockToChunk((long) blockX + maximumDistanceBlocks);
        long minimumChunkZ = blockToChunk((long) blockZ - maximumDistanceBlocks);
        long maximumChunkZ = blockToChunk((long) blockZ + maximumDistanceBlocks);
        long candidateColumns = maximumChunkX - minimumChunkX + 1L;
        long candidateRows = maximumChunkZ - minimumChunkZ + 1L;
        long candidateCount = multiplySaturated(candidateColumns, candidateRows);

        double distanceSquared = candidateCount <= historicalChunkCount
                ? nearestByNeighborhood(
                        blockX,
                        blockZ,
                        minimumChunkX,
                        maximumChunkX,
                        minimumChunkZ,
                        maximumChunkZ,
                        maximumDistanceBlocks
                )
                : nearestBySource(blockX, blockZ, maximumDistanceBlocks);
        return Math.sqrt(distanceSquared);
    }

    public long[] packedHistoricalChunks() {
        long[] chunks = new long[historicalChunkCount];
        int[] cursor = new int[1];
        try {
            historicalChunks.forEach((chunkX, chunkZ) -> {
                if (cursor[0] >= chunks.length) {
                    throw new IOException("Frozen boundary contains more chunks than its catalog declares");
                }
                chunks[cursor[0]++] = packChunk(chunkX, chunkZ);
            });
        } catch (IOException error) {
            throw new IllegalStateException("Unable to materialize the frozen generation boundary", error);
        }
        if (cursor[0] != chunks.length) {
            throw new IllegalStateException("Frozen boundary contains fewer chunks than its catalog declares");
        }
        Arrays.sort(chunks);
        return chunks;
    }

    public void forEachHistoricalChunk(ChunkConsumer consumer) throws IOException {
        historicalChunks.forEach(Objects.requireNonNull(consumer, "Historical chunk consumer"));
    }

    int cachedRegionCount() {
        return historicalChunks.cachedRegionCount();
    }

    public void requireCompleteTerrainSignatures(
            Collection<TerrainBoundarySignature> signatures
    ) {
        Collection<TerrainBoundarySignature> requiredSignatures = Objects.requireNonNull(
                signatures,
                "Terrain signatures"
        );
        LongOpenHashSet actual = new LongOpenHashSet(requiredSignatures.size());
        for (TerrainBoundarySignature signature : requiredSignatures) {
            TerrainBoundarySignature requiredSignature = Objects.requireNonNull(
                    signature,
                    "Terrain signature"
            );
            if (!isExposedBlockColumn(requiredSignature.blockX(), requiredSignature.blockZ())) {
                throw new IllegalArgumentException("Terrain signature is not on the frozen generation boundary: "
                        + requiredSignature.blockX() + "," + requiredSignature.blockZ());
            }
            long column = packBlock(requiredSignature.blockX(), requiredSignature.blockZ());
            if (!actual.add(column)) {
                throw new IllegalArgumentException("Duplicate terrain signature column: "
                        + requiredSignature.blockX() + "," + requiredSignature.blockZ());
            }
        }
        long expectedCount = exposedBlockColumnCount();
        if (actual.size() != expectedCount) {
            throw new IllegalArgumentException("Terrain signatures contain " + actual.size()
                    + " columns but the frozen boundary requires " + expectedCount);
        }
    }

    public List<BlockColumn> exposedBlockColumns() {
        ArrayList<BlockColumn> columns = new ArrayList<>();
        try {
            forEachExposedBlockColumn((blockX, blockZ) -> columns.add(new BlockColumn(blockX, blockZ)));
        } catch (IOException error) {
            throw new IllegalStateException("Unable to enumerate frozen boundary columns", error);
        }
        columns.sort(Comparator
                .comparingInt(BlockColumn::blockX)
                .thenComparingInt(BlockColumn::blockZ));
        return List.copyOf(columns);
    }

    public long exposedBlockColumnCount() {
        long[] count = new long[1];
        try {
            forEachExposedBlockColumn((blockX, blockZ) -> count[0]++);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to count frozen boundary columns", error);
        }
        return count[0];
    }

    public void forEachExposedBlockColumn(BlockColumnConsumer consumer) throws IOException {
        BlockColumnConsumer requiredConsumer = Objects.requireNonNull(consumer, "Boundary column consumer");
        historicalChunks.forEach((chunkX, chunkZ) -> emitExposedColumns(chunkX, chunkZ, requiredConsumer));
    }

    public static long packChunk(int chunkX, int chunkZ) {
        return ((long) chunkX << Integer.SIZE) ^ (chunkZ & 0xffffffffL);
    }

    public static ChunkCoordinate unpackChunk(long packedChunk) {
        return new ChunkCoordinate((int) (packedChunk >> Integer.SIZE), (int) packedChunk);
    }

    private void emitExposedColumns(
            int chunkX,
            int chunkZ,
            BlockColumnConsumer consumer
    ) throws IOException {
        int minimumX = Math.multiplyExact(chunkX, CHUNK_SIZE);
        int minimumZ = Math.multiplyExact(chunkZ, CHUNK_SIZE);
        boolean north = chunkZ == Integer.MIN_VALUE || !historicalChunks.contains(chunkX, chunkZ - 1);
        boolean south = chunkZ == Integer.MAX_VALUE || !historicalChunks.contains(chunkX, chunkZ + 1);
        boolean west = chunkX == Integer.MIN_VALUE || !historicalChunks.contains(chunkX - 1, chunkZ);
        boolean east = chunkX == Integer.MAX_VALUE || !historicalChunks.contains(chunkX + 1, chunkZ);
        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                if (localZ == 0 && north
                        || localZ == CHUNK_SIZE - 1 && south
                        || localX == 0 && west
                        || localX == CHUNK_SIZE - 1 && east) {
                    consumer.accept(minimumX + localX, minimumZ + localZ);
                }
            }
        }
    }

    private boolean isExposedBlockColumn(int blockX, int blockZ) {
        int chunkX = blockToChunk(blockX);
        int chunkZ = blockToChunk(blockZ);
        if (!isHistoricalChunk(chunkX, chunkZ)) {
            return false;
        }
        int localX = Math.floorMod(blockX, CHUNK_SIZE);
        int localZ = Math.floorMod(blockZ, CHUNK_SIZE);
        return localZ == 0 && (chunkZ == Integer.MIN_VALUE || !isHistoricalChunk(chunkX, chunkZ - 1))
                || localZ == CHUNK_SIZE - 1
                && (chunkZ == Integer.MAX_VALUE || !isHistoricalChunk(chunkX, chunkZ + 1))
                || localX == 0 && (chunkX == Integer.MIN_VALUE || !isHistoricalChunk(chunkX - 1, chunkZ))
                || localX == CHUNK_SIZE - 1
                && (chunkX == Integer.MAX_VALUE || !isHistoricalChunk(chunkX + 1, chunkZ));
    }

    private double nearestByNeighborhood(
            int blockX,
            int blockZ,
            long minimumChunkX,
            long maximumChunkX,
            long minimumChunkZ,
            long maximumChunkZ,
            int maximumDistanceBlocks
    ) {
        double nearestSquared = square(maximumDistanceBlocks);
        for (long chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (long chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                if (!isHistoricalChunk((int) chunkX, (int) chunkZ)) {
                    continue;
                }
                nearestSquared = Math.min(
                        nearestSquared,
                        distanceSquaredToChunk(blockX, blockZ, (int) chunkX, (int) chunkZ)
                );
            }
        }
        return nearestSquared;
    }

    private double nearestBySource(int blockX, int blockZ, int maximumDistanceBlocks) {
        double[] nearestSquared = {square(maximumDistanceBlocks)};
        try {
            historicalChunks.forEach((chunkX, chunkZ) -> nearestSquared[0] = Math.min(
                    nearestSquared[0],
                    distanceSquaredToChunk(blockX, blockZ, chunkX, chunkZ)
            ));
        } catch (IOException error) {
            throw new IllegalStateException("Unable to scan the frozen generation boundary", error);
        }
        return nearestSquared[0];
    }

    private static double distanceSquaredToChunk(int blockX, int blockZ, int chunkX, int chunkZ) {
        long minimumX = (long) chunkX * CHUNK_SIZE;
        long maximumX = minimumX + CHUNK_SIZE - 1L;
        long minimumZ = (long) chunkZ * CHUNK_SIZE;
        long maximumZ = minimumZ + CHUNK_SIZE - 1L;
        long distanceX = distanceToRange(blockX, minimumX, maximumX);
        long distanceZ = distanceToRange(blockZ, minimumZ, maximumZ);
        return square(distanceX) + square(distanceZ);
    }

    private static long distanceToRange(long value, long minimum, long maximum) {
        if (value < minimum) {
            return minimum - value;
        }
        return value > maximum ? value - maximum : 0L;
    }

    private static double square(long value) {
        return (double) value * value;
    }

    private static long multiplySaturated(long left, long right) {
        if (left == 0L || right == 0L) {
            return 0L;
        }
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    private static int blockToChunk(int blockCoordinate) {
        return Math.floorDiv(blockCoordinate, CHUNK_SIZE);
    }

    private static long blockToChunk(long blockCoordinate) {
        return Math.floorDiv(blockCoordinate, CHUNK_SIZE);
    }

    private static long packBlock(int blockX, int blockZ) {
        return ((long) blockX << Integer.SIZE) ^ (blockZ & 0xffffffffL);
    }

    private static String requireIdentity(String identity) {
        String requiredIdentity = Objects.requireNonNull(identity, "Boundary identity");
        if (requiredIdentity.isBlank()) {
            throw new IllegalArgumentException("Boundary identity cannot be blank");
        }
        return requiredIdentity;
    }

    private static long[] normalize(long[] historicalChunks) {
        long[] normalized = historicalChunks.clone();
        Arrays.sort(normalized);
        if (normalized.length < 2) {
            return normalized;
        }
        int uniqueCount = 1;
        for (int index = 1; index < normalized.length; index++) {
            if (normalized[index] != normalized[uniqueCount - 1]) {
                normalized[uniqueCount++] = normalized[index];
            }
        }
        return Arrays.copyOf(normalized, uniqueCount);
    }

    public record ChunkCoordinate(int chunkX, int chunkZ) {
    }

    public record BlockColumn(int blockX, int blockZ) {
    }

    @FunctionalInterface
    public interface ChunkConsumer {
        void accept(int chunkX, int chunkZ) throws IOException;
    }

    @FunctionalInterface
    public interface BlockColumnConsumer {
        void accept(int blockX, int blockZ) throws IOException;
    }

    interface HistoricalChunkSource {
        boolean contains(int chunkX, int chunkZ) throws IOException;

        void forEach(ChunkConsumer consumer) throws IOException;

        default int cachedRegionCount() {
            return 0;
        }
    }

    private static final class ArraySource implements HistoricalChunkSource {
        private final long[] chunks;

        private ArraySource(long[] chunks) {
            this.chunks = chunks;
        }

        @Override
        public boolean contains(int chunkX, int chunkZ) {
            return Arrays.binarySearch(chunks, packChunk(chunkX, chunkZ)) >= 0;
        }

        @Override
        public void forEach(ChunkConsumer consumer) throws IOException {
            for (long chunk : chunks) {
                consumer.accept((int) (chunk >> Integer.SIZE), (int) chunk);
            }
        }
    }
}
