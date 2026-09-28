package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.cache.ConcurrentClockCache;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Shares the adaptive cave planes' 8-block density lattice between neighbouring chunks. Every lattice column on a
 * chunk edge is sampled by two chunks and every corner by four, with identical coordinates and carver noise, so a
 * value computed once is reused bit for bit. Racing writers store the same value.
 */
final class CaveDensityGrid {
    static final int LATTICE_SHIFT = 3;
    private static final int SEGMENT_SHIFT = 5;
    private static final int SEGMENT_SIZE = 1 << SEGMENT_SHIFT;
    private static final long COORDINATE_MASK = 0xFFFFFFL;
    private static final VarHandle VALUES = MethodHandles.arrayElementVarHandle(double[].class);
    private static final AtomicInteger OWNERS = new AtomicInteger();

    private final ConcurrentClockCache<Column> columns;

    CaveDensityGrid(int capacity) {
        columns = new ConcurrentClockCache<>(capacity);
    }

    static int nextOwner() {
        return OWNERS.getAndIncrement();
    }

    Column column(Object owner, int ownerId, int height, int latticeX, int latticeZ) {
        long key = (long) (ownerId & 0xFFFF) << 48 | (latticeX & COORDINATE_MASK) << 24 | (latticeZ & COORDINATE_MASK);
        Column cached = columns.get(key);
        if (cached == null) {
            cached = columns.putIfAbsent(key, new Column(owner, latticeX, latticeZ, height));
        }
        return cached.owner == owner && cached.latticeX == latticeX && cached.latticeZ == latticeZ ? cached : null;
    }

    void clear() {
        columns.clear();
    }

    static final class Column {
        private final Object owner;
        private final int latticeX;
        private final int latticeZ;
        private final AtomicReferenceArray<double[]> segments;

        private Column(Object owner, int latticeX, int latticeZ, int height) {
            this.owner = owner;
            this.latticeX = latticeX;
            this.latticeZ = latticeZ;
            segments = new AtomicReferenceArray<>(Math.ceilDiv(Math.max(1, height), SEGMENT_SIZE));
        }

        double get(int y) {
            int segment = y >> SEGMENT_SHIFT;
            if (segment >= segments.length()) {
                return Double.NaN;
            }
            double[] values = segments.getAcquire(segment);
            return values == null ? Double.NaN : (double) VALUES.getOpaque(values, y & (SEGMENT_SIZE - 1));
        }

        void set(int y, double value) {
            int segment = y >> SEGMENT_SHIFT;
            if (segment >= segments.length()) {
                return;
            }
            double[] values = segments.getAcquire(segment);
            if (values == null) {
                double[] created = new double[SEGMENT_SIZE];
                Arrays.fill(created, Double.NaN);
                double[] published = segments.compareAndExchange(segment, null, created);
                values = published == null ? created : published;
            }
            VALUES.setOpaque(values, y & (SEGMENT_SIZE - 1), value);
        }
    }
}
