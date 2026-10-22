package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.cache.ConcurrentClockCache;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
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
    private final ConcurrentClockCache<Column> warpColumns;
    private final ConcurrentHashMap<Object, Owner> owners = new ConcurrentHashMap<>();

    CaveDensityGrid(int capacity) {
        columns = new ConcurrentClockCache<>(capacity);
        warpColumns = new ConcurrentClockCache<>(Math.max(1, capacity >> 2));
    }

    Owner owner(Object fieldKey) {
        return owners.computeIfAbsent(fieldKey, ignored -> new Owner(OWNERS.getAndIncrement()));
    }

    Column column(Owner owner, int height, int latticeX, int latticeZ) {
        return column(columns, owner, height, latticeX, latticeZ);
    }

    Column warpColumn(Owner owner, int height, int latticeX, int latticeZ) {
        return column(warpColumns, owner, height, latticeX, latticeZ);
    }

    void clear() {
        columns.clear();
        warpColumns.clear();
        owners.clear();
    }

    private static Column column(ConcurrentClockCache<Column> cache, Owner owner, int height, int latticeX, int latticeZ) {
        long key = (long) (owner.id & 0xFFFF) << 48 | (latticeX & COORDINATE_MASK) << 24 | (latticeZ & COORDINATE_MASK);
        Column cached = cache.get(key);
        if (cached == null) {
            cached = cache.putIfAbsent(key, new Column(owner, latticeX, latticeZ, height));
        }
        return cached.matches(owner, latticeX, latticeZ) ? cached : null;
    }

    static final class Owner {
        private final int id;

        private Owner(int id) {
            this.id = id;
        }
    }

    static final class Column {
        private final Owner owner;
        private final int latticeX;
        private final int latticeZ;
        private final AtomicReferenceArray<double[]> segments;

        private Column(Owner owner, int latticeX, int latticeZ, int height) {
            this.owner = owner;
            this.latticeX = latticeX;
            this.latticeZ = latticeZ;
            segments = new AtomicReferenceArray<>(Math.ceilDiv(Math.max(1, height), SEGMENT_SIZE));
        }

        boolean matches(Owner owner, int latticeX, int latticeZ) {
            return this.owner == owner && this.latticeX == latticeX && this.latticeZ == latticeZ;
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
