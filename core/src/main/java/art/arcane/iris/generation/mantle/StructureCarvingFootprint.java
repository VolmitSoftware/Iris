package art.arcane.iris.generation.mantle;

import art.arcane.iris.structure.placement.PlacedStructurePiece;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectRotation;
import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.volmlib.util.collection.KList;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;

import java.util.Arrays;

public final class StructureCarvingFootprint {
    static final int DEFAULT_MAX_CELLS = 1_048_576;

    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;
    private final int width;
    private final int depth;
    private final long[] distanceSquared;
    private final int[] nearestSourceIds;
    private final int[] sourceMinY;
    private final int[] sourceMaxY;

    private StructureCarvingFootprint(FootprintData data) {
        minX = data.minX();
        maxX = data.maxX();
        minZ = data.minZ();
        maxZ = data.maxZ();
        width = data.width();
        depth = data.depth();
        distanceSquared = data.distanceSquared();
        nearestSourceIds = data.nearestSourceIds();
        sourceMinY = data.sourceMinY();
        sourceMaxY = data.sourceMaxY();
    }

    static StructureCarvingFootprint from(KList<PlacedStructurePiece> pieces, int padding) {
        return from(pieces, padding, DEFAULT_MAX_CELLS);
    }

    static StructureCarvingFootprint from(KList<PlacedStructurePiece> pieces, int padding, int maxCells) {
        return fromColumns(sink -> emitPieceColumns(pieces, sink), padding, maxCells);
    }

    /**
     * Builds a footprint from any per-column occupancy source. The source reports one contribution per
     * occupied column; overlapping contributions merge into a single column whose vertical extents span
     * every contribution.
     */
    public static StructureCarvingFootprint fromColumns(ColumnSource source, int padding, int maxCells) {
        if (padding < 0) {
            throw new IllegalArgumentException("padding must be non-negative");
        }
        if (maxCells < 1) {
            throw new IllegalArgumentException("maxCells must be positive");
        }
        if (source == null) {
            return null;
        }

        SourceCollection sources = collectSources(source);
        if (sources == null || sources.columns().isEmpty()) {
            return null;
        }

        Bounds bounds = paddedBounds(sources.bounds(), padding, maxCells);
        if (bounds == null) {
            return null;
        }

        int[] nearestSourceIds = new int[bounds.cellCount()];
        Arrays.fill(nearestSourceIds, -1);
        int sourceCount = sources.columns().size();
        int[] sourceX = new int[sourceCount];
        int[] sourceZ = new int[sourceCount];
        int[] sourceMinY = new int[sourceCount];
        int[] sourceMaxY = new int[sourceCount];
        int sourceId = 0;
        for (Long2LongMap.Entry entry : sources.columns().long2LongEntrySet()) {
            int localX = unpackX(entry.getLongKey()) - bounds.minX();
            int localZ = unpackZ(entry.getLongKey()) - bounds.minZ();
            long heights = entry.getLongValue();
            sourceX[sourceId] = localX;
            sourceZ[sourceId] = localZ;
            sourceMinY[sourceId] = unpackMinY(heights);
            sourceMaxY[sourceId] = unpackMaxY(heights);
            nearestSourceIds[localZ * bounds.width() + localX] = sourceId;
            sourceId++;
        }

        transformRows(nearestSourceIds, sourceX, sourceZ, bounds.width(), bounds.depth());
        long[] distanceSquared = transformColumns(
                nearestSourceIds, sourceX, sourceZ, bounds.width(), bounds.depth());
        FootprintData data = new FootprintData(
                bounds.minX(), bounds.maxX(), bounds.minZ(), bounds.maxZ(),
                bounds.width(), bounds.depth(), distanceSquared, nearestSourceIds,
                sourceMinY, sourceMaxY);
        return new StructureCarvingFootprint(data);
    }

    public int minX() {
        return minX;
    }

    public int maxX() {
        return maxX;
    }

    public int minZ() {
        return minZ;
    }

    public int maxZ() {
        return maxZ;
    }

    public int width() {
        return width;
    }

    public int depth() {
        return depth;
    }

    public boolean contains(int worldX, int worldZ) {
        return worldX >= minX && worldX <= maxX && worldZ >= minZ && worldZ <= maxZ;
    }

    public int indexAt(int worldX, int worldZ) {
        if (!contains(worldX, worldZ)) {
            return -1;
        }
        return (worldZ - minZ) * width + worldX - minX;
    }

    public long distanceSquaredAt(int worldX, int worldZ) {
        return distanceSquaredAt(requireIndex(worldX, worldZ));
    }

    public long distanceSquaredAt(int index) {
        return distanceSquared[index];
    }

    public int sourceMinYAt(int worldX, int worldZ) {
        return sourceMinYAt(requireIndex(worldX, worldZ));
    }

    public int sourceMinYAt(int index) {
        return sourceMinY[nearestSourceIds[index]];
    }

    public int sourceMaxYAt(int worldX, int worldZ) {
        return sourceMaxYAt(requireIndex(worldX, worldZ));
    }

    public int sourceMaxYAt(int index) {
        return sourceMaxY[nearestSourceIds[index]];
    }

    Column sample(int worldX, int worldZ) {
        int index = indexAt(worldX, worldZ);
        if (index < 0) {
            return null;
        }
        return new Column(distanceSquaredAt(index), sourceMinYAt(index), sourceMaxYAt(index));
    }

    private static SourceCollection collectSources(ColumnSource source) {
        Long2LongOpenHashMap columns = new Long2LongOpenHashMap();
        MutableBounds bounds = new MutableBounds();
        boolean usable = source.emit((worldX, worldZ, minY, maxY) -> {
            mergeColumn(columns, worldX, worldZ, minY, maxY);
            bounds.include(worldX, worldZ);
        });
        if (!usable) {
            return null;
        }
        return columns.isEmpty() ? null : new SourceCollection(columns, bounds.freeze());
    }

    private static boolean emitPieceColumns(KList<PlacedStructurePiece> pieces, ColumnSink sink) {
        if (pieces == null) {
            return true;
        }
        for (PlacedStructurePiece piece : pieces) {
            if (piece == null) {
                continue;
            }
            IrisObject object = piece.getObject();
            IrisObjectRotation rotation = piece.getRotation();
            if (object == null || rotation == null || object.getBlocks() == null) {
                continue;
            }
            for (IrisBlockVector local : object.getBlocks().keys()) {
                if (local == null || B.isAir(object.getBlocks().get(local))) {
                    continue;
                }
                IrisBlockVector rotated = rotation.rotate(local.clone());
                long worldX = (long) piece.getX() + rotated.getBlockX();
                long worldY = (long) piece.getY() + rotated.getBlockY();
                long worldZ = (long) piece.getZ() + rotated.getBlockZ();
                if (!fitsInteger(worldX) || !fitsInteger(worldY) || !fitsInteger(worldZ)) {
                    return false;
                }
                sink.column((int) worldX, (int) worldZ, (int) worldY, (int) worldY);
            }
        }
        return true;
    }

    private static Bounds paddedBounds(MutableBoundsSnapshot sourceBounds, int padding, int maxCells) {
        long paddedMinX = (long) sourceBounds.minX() - padding;
        long paddedMaxX = (long) sourceBounds.maxX() + padding;
        long paddedMinZ = (long) sourceBounds.minZ() - padding;
        long paddedMaxZ = (long) sourceBounds.maxZ() + padding;
        if (!fitsInteger(paddedMinX) || !fitsInteger(paddedMaxX)
                || !fitsInteger(paddedMinZ) || !fitsInteger(paddedMaxZ)) {
            return null;
        }
        long width = paddedMaxX - paddedMinX + 1L;
        long depth = paddedMaxZ - paddedMinZ + 1L;
        if (width < 1L || depth < 1L || width > Integer.MAX_VALUE || depth > Integer.MAX_VALUE
                || width > maxCells / depth) {
            return null;
        }
        int cellCount = (int) (width * depth);
        return new Bounds(
                (int) paddedMinX, (int) paddedMaxX, (int) paddedMinZ, (int) paddedMaxZ,
                (int) width, (int) depth, cellCount);
    }

    private static void mergeColumn(Long2LongOpenHashMap columns, int x, int z, int minY, int maxY) {
        long key = pack(x, z);
        if (!columns.containsKey(key)) {
            columns.put(key, packHeights(minY, maxY));
            return;
        }
        long existing = columns.get(key);
        columns.put(key, packHeights(
                Math.min(minY, unpackMinY(existing)), Math.max(maxY, unpackMaxY(existing))));
    }

    private static void transformRows(int[] nearestSourceIds, int[] sourceX, int[] sourceZ,
                                      int width, int depth) {
        for (int z = 0; z < depth; z++) {
            int rowStart = z * width;
            int previousSource = -1;
            for (int x = 0; x < width; x++) {
                int index = rowStart + x;
                int sourceId = nearestSourceIds[index];
                if (isExplicitSource(sourceId, x, z, sourceX, sourceZ)) {
                    previousSource = sourceId;
                } else {
                    nearestSourceIds[index] = previousSource;
                }
            }
            int nextSource = -1;
            for (int x = width - 1; x >= 0; x--) {
                int index = rowStart + x;
                int currentSource = nearestSourceIds[index];
                if (isExplicitSource(currentSource, x, z, sourceX, sourceZ)) {
                    nextSource = currentSource;
                }
                if (isBetterHorizontalSource(nextSource, currentSource, x, sourceX)) {
                    nearestSourceIds[index] = nextSource;
                }
            }
        }
    }

    private static long[] transformColumns(int[] nearestSourceIds, int[] sourceX, int[] sourceZ,
                                           int width, int depth) {
        long[] distanceSquared = new long[nearestSourceIds.length];
        int[] siteRows = new int[depth];
        int[] siteSourceIds = new int[depth];
        int[] starts = new int[depth];
        long[] siteCosts = new long[depth];
        for (int x = 0; x < width; x++) {
            int siteCount = buildEnvelope(
                    nearestSourceIds, sourceX, width, depth, x,
                    siteRows, siteSourceIds, starts, siteCosts);
            int siteIndex = 0;
            for (int z = 0; z < depth; z++) {
                while (siteIndex + 1 < siteCount && starts[siteIndex + 1] <= z) {
                    siteIndex++;
                }
                int sourceId = siteSourceIds[siteIndex];
                int index = z * width + x;
                long deltaX = (long) x - sourceX[sourceId];
                long deltaZ = (long) z - sourceZ[sourceId];
                nearestSourceIds[index] = sourceId;
                distanceSquared[index] = deltaX * deltaX + deltaZ * deltaZ;
            }
        }
        return distanceSquared;
    }

    private static int buildEnvelope(int[] nearestSourceIds, int[] sourceX, int width, int depth,
                                     int x, int[] siteRows, int[] siteSourceIds,
                                     int[] starts, long[] siteCosts) {
        int siteCount = 0;
        for (int z = 0; z < depth; z++) {
            int sourceId = nearestSourceIds[z * width + x];
            if (sourceId < 0) {
                continue;
            }
            long deltaX = (long) x - sourceX[sourceId];
            long cost = deltaX * deltaX;
            int start = Integer.MIN_VALUE;
            while (siteCount > 0) {
                start = firstStrictlyBetterAt(
                        siteRows[siteCount - 1], siteCosts[siteCount - 1], z, cost);
                if (start > starts[siteCount - 1]) {
                    break;
                }
                siteCount--;
            }
            if (siteCount == 0) {
                start = Integer.MIN_VALUE;
            }
            siteRows[siteCount] = z;
            siteSourceIds[siteCount] = sourceId;
            siteCosts[siteCount] = cost;
            starts[siteCount] = start;
            siteCount++;
        }
        return siteCount;
    }

    private static int firstStrictlyBetterAt(int existingRow, long existingCost,
                                             int candidateRow, long candidateCost) {
        long numerator = candidateCost + (long) candidateRow * candidateRow
                - existingCost - (long) existingRow * existingRow;
        long denominator = 2L * (candidateRow - existingRow);
        long start = Math.floorDiv(numerator, denominator) + 1L;
        if (start < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        if (start > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) start;
    }

    private static boolean isExplicitSource(int sourceId, int x, int z,
                                            int[] sourceX, int[] sourceZ) {
        return sourceId >= 0 && sourceX[sourceId] == x && sourceZ[sourceId] == z;
    }

    private static boolean isBetterHorizontalSource(int candidateId, int currentId,
                                                    int x, int[] sourceX) {
        if (candidateId < 0) {
            return false;
        }
        if (currentId < 0) {
            return true;
        }
        long candidateDelta = (long) x - sourceX[candidateId];
        long currentDelta = (long) x - sourceX[currentId];
        long candidateDistance = candidateDelta * candidateDelta;
        long currentDistance = currentDelta * currentDelta;
        return candidateDistance < currentDistance
                || candidateDistance == currentDistance && sourceX[candidateId] < sourceX[currentId];
    }

    private int requireIndex(int worldX, int worldZ) {
        int index = indexAt(worldX, worldZ);
        if (index < 0) {
            throw new IndexOutOfBoundsException(
                    "coordinate outside carving footprint: " + worldX + "," + worldZ);
        }
        return index;
    }

    private static boolean fitsInteger(long value) {
        return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE;
    }

    private static long pack(int x, int z) {
        return (long) x << 32 | z & 0xffffffffL;
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackZ(long packed) {
        return (int) packed;
    }

    private static long packHeights(int minY, int maxY) {
        return (long) minY << 32 | maxY & 0xffffffffL;
    }

    private static int unpackMinY(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackMaxY(long packed) {
        return (int) packed;
    }

    @FunctionalInterface
    public interface ColumnSink {
        void column(int worldX, int worldZ, int minY, int maxY);
    }

    @FunctionalInterface
    public interface ColumnSource {
        /**
         * Reports every occupied column to the sink. Returning false abandons the footprint, which is how
         * a source rejects occupancy it cannot address (for example coordinates outside the integer range).
         */
        boolean emit(ColumnSink sink);
    }

    record Column(long distanceSquared, int sourceMinY, int sourceMaxY) {
    }

    private record Bounds(int minX, int maxX, int minZ, int maxZ,
                          int width, int depth, int cellCount) {
    }

    private record FootprintData(int minX, int maxX, int minZ, int maxZ,
                                 int width, int depth, long[] distanceSquared,
                                 int[] nearestSourceIds, int[] sourceMinY, int[] sourceMaxY) {
    }

    private record SourceCollection(Long2LongOpenHashMap columns, MutableBoundsSnapshot bounds) {
    }

    private record MutableBoundsSnapshot(int minX, int maxX, int minZ, int maxZ) {
    }

    private static final class MutableBounds {
        private int minX = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE;
        private int minZ = Integer.MAX_VALUE;
        private int maxZ = Integer.MIN_VALUE;

        private void include(int x, int z) {
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }

        private MutableBoundsSnapshot freeze() {
            return new MutableBoundsSnapshot(minX, maxX, minZ, maxZ);
        }
    }
}
