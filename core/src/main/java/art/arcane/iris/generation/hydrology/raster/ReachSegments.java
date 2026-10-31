package art.arcane.iris.generation.hydrology.raster;

import art.arcane.iris.generation.hydrology.water.Reach;

import java.util.Arrays;

final class ReachSegments {
    private static final int RUN = 16;
    private static final int COVER_SHIFT = 2;
    private static final float COVER_MARGIN = 1f;

    private Reach[] reaches;
    private int[] segments;
    private float[] startX;
    private float[] startZ;
    private float[] endX;
    private float[] endZ;
    private float[] startWidth;
    private float[] endWidth;
    private float[] radius;
    private int[] bands;
    private int size;
    private int[] runEnds = new int[8];
    private float[] runBoxes = new float[32];
    private float[] runRadius = new float[8];
    private int[] runBands = new int[8];
    private int runs;
    private boolean sealed;
    private boolean covered;
    private int coverX;
    private int coverZ;
    private int coverWidth;
    private int coverDepth;
    private int[] coverStarts;
    private int[] coverEntries;

    ReachSegments(int capacity) {
        int initial = Math.max(1, capacity);
        reaches = new Reach[initial];
        segments = new int[initial];
        startX = new float[initial];
        startZ = new float[initial];
        endX = new float[initial];
        endZ = new float[initial];
        startWidth = new float[initial];
        endWidth = new float[initial];
        radius = new float[initial];
        bands = new int[initial];
    }

    void addAll(Reach reach) {
        for (int segment = 0; segment + 1 < reach.xs().length; segment++) {
            add(reach, segment);
        }
    }

    boolean hasOnlyLevel(int head, int seaLevel) {
        for (int index = 0; index < size; index++) {
            int[] grade = reaches[index].grade();
            int start = grade[segments[index]];
            int end = grade[segments[index] + 1];
            if (start - Math.floorMod(start - seaLevel, 2) != head || end - Math.floorMod(end - seaLevel, 2) != head) {
                return false;
            }
        }
        return true;
    }

    void addNear(Reach reach, float minX, float minZ, float maxX, float maxZ, float outward) {
        float[] xs = reach.xs();
        float[] zs = reach.zs();
        float[] halfWidth = reach.halfWidth();
        float reachOut = Math.max(reach.tier().bankBand(), outward);
        for (int segment = 0; segment + 1 < xs.length; segment++) {
            float grown = Math.max(halfWidth[segment], halfWidth[segment + 1]) + reachOut;
            if (Math.min(xs[segment], xs[segment + 1]) - grown <= maxX && Math.max(xs[segment], xs[segment + 1]) + grown >= minX
                    && Math.min(zs[segment], zs[segment + 1]) - grown <= maxZ && Math.max(zs[segment], zs[segment + 1]) + grown >= minZ) {
                add(reach, segment);
            }
        }
    }

    boolean isEmpty() {
        return size == 0;
    }

    void coverChannels(int minX, int minZ, int maxX, int maxZ) {
        covered = true;
        coverX = minX;
        coverZ = minZ;
        coverWidth = ((maxX - minX) >> COVER_SHIFT) + 1;
        coverDepth = ((maxZ - minZ) >> COVER_SHIFT) + 1;
        sealed = false;
    }

    void nearest(double x, double z, Scope scope, ReachCursor cursor) {
        if (!sealed) {
            seal();
        }
        int channelHint = cursor.channel.index;
        int bankHint = cursor.bank.index;
        int edgeHint = cursor.edge.index;
        cursor.reset();
        if (bankHint == channelHint) {
            bankHint = -1;
        }
        if (edgeHint == channelHint || edgeHint == bankHint) {
            edgeHint = -1;
        }
        boolean unbounded = scope == Scope.NEAREST;
        hint(channelHint, x, z, unbounded, cursor);
        hint(bankHint, x, z, unbounded, cursor);
        hint(edgeHint, x, z, unbounded, cursor);
        int cell = scope == Scope.CHANNEL ? coveredCell(x, z) : -1;
        if (cell >= 0) {
            for (int entry = coverStarts[cell]; entry < coverStarts[cell + 1]; entry++) {
                int index = coverEntries[entry];
                if (index != channelHint && index != bankHint && index != edgeHint && !beyondSegment(index, x, z, scope, cursor)) {
                    evaluate(index, x, z, false, cursor);
                }
            }
            return;
        }
        int first = 0;
        for (int run = 0; run < runs; run++) {
            int end = runEnds[run];
            int box = 4 * run;
            if (beyond(x, z, runBoxes[box], runBoxes[box + 1], runBoxes[box + 2], runBoxes[box + 3],
                    limit(runRadius[run], runBands[run], scope, cursor))) {
                first = end;
                continue;
            }
            for (int index = first; index < end; index++) {
                if (!beyondSegment(index, x, z, scope, cursor) && index != channelHint && index != bankHint && index != edgeHint) {
                    evaluate(index, x, z, unbounded, cursor);
                }
            }
            first = end;
        }
    }

    private int coveredCell(double x, double z) {
        if (coverStarts == null) {
            return -1;
        }
        int cellX = ((int) Math.floor(x) - coverX) >> COVER_SHIFT;
        int cellZ = ((int) Math.floor(z) - coverZ) >> COVER_SHIFT;
        if (cellX < 0 || cellZ < 0 || cellX >= coverWidth || cellZ >= coverDepth) {
            return -1;
        }
        return cellZ * coverWidth + cellX;
    }

    private boolean beyondSegment(int index, double x, double z, Scope scope, ReachCursor cursor) {
        return beyond(x, z, Math.min(startX[index], endX[index]), Math.min(startZ[index], endZ[index]), Math.max(startX[index], endX[index]),
                Math.max(startZ[index], endZ[index]), limit(radius[index], bands[index], scope, cursor));
    }

    private void hint(int index, double x, double z, boolean unbounded, ReachCursor cursor) {
        if (index >= 0 && index < size) {
            evaluate(index, x, z, unbounded, cursor);
        }
    }

    private static double limit(double radius, int band, Scope scope, ReachCursor cursor) {
        if (scope == Scope.CHANNEL || cursor.channel.reach != null) {
            return Math.min(radius, cursor.channel.distance);
        }
        double limit = Math.max(radius, cursor.bank.reach == null ? radius + band : Math.min(radius + band, cursor.bank.distance));
        return switch (scope) {
            case NEAREST -> cursor.bank.reach == null ? Math.max(limit, cursor.nearest.distance) : limit;
            case SHORE -> Math.max(limit, Math.min(ChunkWaterRaster.MAX_SHORE_DISTANCE, cursor.edge.distance) + radius);
            case CHANNEL -> limit;
        };
    }

    private static boolean beyond(double x, double z, double minX, double minZ, double maxX, double maxZ, double limit) {
        if (limit == Double.POSITIVE_INFINITY) {
            return false;
        }
        double dx = Math.max(0D, Math.max(minX - x, x - maxX));
        double dz = Math.max(0D, Math.max(minZ - z, z - maxZ));
        return dx * dx + dz * dz > limit * limit;
    }

    private void evaluate(int index, double x, double z, boolean unbounded, ReachCursor cursor) {
        double ax = startX[index];
        double az = startZ[index];
        double dx = endX[index] - ax;
        double dz = endZ[index] - az;
        double length = dx * dx + dz * dz;
        double t = length == 0D ? 0D : Math.clamp(((x - ax) * dx + (z - az) * dz) / length, 0D, 1D);
        double offsetX = x - (ax + t * dx);
        double offsetZ = z - (az + t * dz);
        double distance = Math.sqrt(offsetX * offsetX + offsetZ * offsetZ);
        double width = startWidth[index] + t * (endWidth[index] - startWidth[index]);
        Reach reach = reaches[index];
        int segment = segments[index];
        if (unbounded) {
            cursor.nearest.offer(reach, segment, index, t, distance);
        }
        if (distance <= width) {
            cursor.channel.offer(reach, segment, index, t, distance);
        } else if (distance <= width + bands[index]) {
            cursor.bank.offer(reach, segment, index, t, distance);
        }
        cursor.edge.offer(reach, segment, index, t, distance - width);
    }

    private void seal() {
        runs = 0;
        int start = 0;
        while (start < size) {
            int end = start + 1;
            while (end < size && end - start < RUN && reaches[end] == reaches[start]) {
                end++;
            }
            addRun(start, end);
            start = end;
        }
        if (covered) {
            cover();
        }
        sealed = true;
    }

    private void cover() {
        int cells = coverWidth * coverDepth;
        int[] starts = new int[cells + 1];
        int[] range = new int[4];
        for (int index = 0; index < size; index++) {
            if (coverRange(index, range)) {
                for (int cellZ = range[1]; cellZ <= range[3]; cellZ++) {
                    for (int cellX = range[0]; cellX <= range[2]; cellX++) {
                        starts[cellZ * coverWidth + cellX + 1]++;
                    }
                }
            }
        }
        for (int cell = 0; cell < cells; cell++) {
            starts[cell + 1] += starts[cell];
        }
        int[] entries = new int[starts[cells]];
        int[] fill = Arrays.copyOf(starts, cells);
        for (int index = 0; index < size; index++) {
            if (coverRange(index, range)) {
                for (int cellZ = range[1]; cellZ <= range[3]; cellZ++) {
                    for (int cellX = range[0]; cellX <= range[2]; cellX++) {
                        entries[fill[cellZ * coverWidth + cellX]++] = index;
                    }
                }
            }
        }
        coverStarts = starts;
        coverEntries = entries;
    }

    private boolean coverRange(int index, int[] range) {
        float reach = radius[index] + COVER_MARGIN;
        range[0] = Math.max(0, coverCell(Math.min(startX[index], endX[index]) - reach, coverX));
        range[1] = Math.max(0, coverCell(Math.min(startZ[index], endZ[index]) - reach, coverZ));
        range[2] = Math.min(coverWidth - 1, coverCell(Math.max(startX[index], endX[index]) + reach, coverX));
        range[3] = Math.min(coverDepth - 1, coverCell(Math.max(startZ[index], endZ[index]) + reach, coverZ));
        return range[0] <= range[2] && range[1] <= range[3];
    }

    private static int coverCell(float block, int origin) {
        return ((int) Math.floor(block) - origin) >> COVER_SHIFT;
    }

    private void addRun(int start, int end) {
        if (runs == runEnds.length) {
            runEnds = Arrays.copyOf(runEnds, 2 * runs);
            runBoxes = Arrays.copyOf(runBoxes, 8 * runs);
            runRadius = Arrays.copyOf(runRadius, 2 * runs);
            runBands = Arrays.copyOf(runBands, 2 * runs);
        }
        float minX = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        float widest = 0f;
        int band = 0;
        for (int index = start; index < end; index++) {
            minX = Math.min(minX, Math.min(startX[index], endX[index]));
            minZ = Math.min(minZ, Math.min(startZ[index], endZ[index]));
            maxX = Math.max(maxX, Math.max(startX[index], endX[index]));
            maxZ = Math.max(maxZ, Math.max(startZ[index], endZ[index]));
            widest = Math.max(widest, radius[index]);
            band = Math.max(band, bands[index]);
        }
        runEnds[runs] = end;
        runBoxes[4 * runs] = minX;
        runBoxes[4 * runs + 1] = minZ;
        runBoxes[4 * runs + 2] = maxX;
        runBoxes[4 * runs + 3] = maxZ;
        runRadius[runs] = widest;
        runBands[runs] = band;
        runs++;
    }

    private void add(Reach reach, int segment) {
        if (size == reaches.length) {
            grow();
        }
        reaches[size] = reach;
        segments[size] = segment;
        startX[size] = reach.xs()[segment];
        startZ[size] = reach.zs()[segment];
        endX[size] = reach.xs()[segment + 1];
        endZ[size] = reach.zs()[segment + 1];
        startWidth[size] = reach.halfWidth()[segment];
        endWidth[size] = reach.halfWidth()[segment + 1];
        radius[size] = Math.max(startWidth[size], endWidth[size]);
        bands[size] = reach.tier().bankBand();
        size++;
        sealed = false;
    }

    private void grow() {
        int capacity = 2 * reaches.length;
        reaches = Arrays.copyOf(reaches, capacity);
        segments = Arrays.copyOf(segments, capacity);
        startX = Arrays.copyOf(startX, capacity);
        startZ = Arrays.copyOf(startZ, capacity);
        endX = Arrays.copyOf(endX, capacity);
        endZ = Arrays.copyOf(endZ, capacity);
        startWidth = Arrays.copyOf(startWidth, capacity);
        endWidth = Arrays.copyOf(endWidth, capacity);
        radius = Arrays.copyOf(radius, capacity);
        bands = Arrays.copyOf(bands, capacity);
    }

    enum Scope {
        CHANNEL,
        SHORE,
        NEAREST
    }
}
