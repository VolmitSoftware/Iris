package art.arcane.iris.generation.bake;

import art.arcane.iris.generation.cache.ConcurrentClockCache;

import java.util.Objects;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

public final class ChunkBakeCache implements BakeCache {
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[2]);
    private static final int BAKING = 0;
    private static final int SPARSE = 1;

    private final PlaneSource source;
    private final PlaneSource tracked;
    private volatile ConcurrentClockCache<ChunkPlanes> entries;

    public ChunkBakeCache(PlaneSource source, int capacityChunks) {
        this.source = Objects.requireNonNull(source, "Bake plane source");
        this.tracked = new TrackedSource(source);
        this.entries = new ConcurrentClockCache<>(Math.max(16, capacityChunks));
    }

    @Override
    public ColumnBake get(int chunkX, int chunkZ) {
        return planes(chunkX, chunkZ).columnBake(tracked);
    }

    @Override
    public ColumnBake getIfPresent(int chunkX, int chunkZ) {
        ChunkPlanes planes = planesIfPresent(chunkX, chunkZ);
        return planes == null ? null : planes.columnBake();
    }

    public int capacity() {
        return entries.capacity();
    }

    public synchronized void ensureCapacity(int chunks) {
        ConcurrentClockCache<ChunkPlanes> current = entries;
        if (current.capacity() >= chunks) {
            return;
        }
        ConcurrentClockCache<ChunkPlanes> resized = new ConcurrentClockCache<>(chunks);
        current.forEach(planes -> resized.putIfAbsent(key(planes.chunkX(), planes.chunkZ()), planes));
        entries = resized;
    }

    public void clear() {
        entries.clear();
    }

    public ChunkPlanes planes(int chunkX, int chunkZ) {
        ConcurrentClockCache<ChunkPlanes> current = entries;
        long key = key(chunkX, chunkZ);
        ChunkPlanes existing = current.get(key);
        return existing != null ? existing : current.putIfAbsent(key, new ChunkPlanes(chunkX, chunkZ));
    }

    public ChunkPlanes planesIfPresent(int chunkX, int chunkZ) {
        return entries.get(key(chunkX, chunkZ));
    }

    public Object bake(BakePlane plane, int chunkX, int chunkZ) {
        return planes(chunkX, chunkZ).plane(plane, tracked);
    }

    public Object read(BakePlane plane, int chunkX, int chunkZ) {
        ChunkPlanes planes = entries.get(key(chunkX, chunkZ));
        if (planes != null) {
            Object published = planes.published(plane);
            if (published != null) {
                return published;
            }
        }
        if (!denseReadsAllowed()) {
            return null;
        }
        return (planes != null ? planes : planes(chunkX, chunkZ)).plane(plane, tracked);
    }

    public boolean denseReadsAllowed() {
        int[] depth = DEPTH.get();
        if (depth[SPARSE] > 0 || source.mainThread()) {
            return false;
        }
        return depth[BAKING] > 0 || source.generationContext();
    }

    public boolean bakingAllowed() {
        return !inSparseScope() && !source.mainThread();
    }

    public static boolean inSparseScope() {
        return DEPTH.get()[SPARSE] > 0;
    }

    public static <T> T sparse(Supplier<T> action) {
        enterSparse();
        try {
            return action.get();
        } finally {
            exitSparse();
        }
    }

    public static double sparseDouble(DoubleSupplier action) {
        enterSparse();
        try {
            return action.getAsDouble();
        } finally {
            exitSparse();
        }
    }

    public static void sparse(Runnable action) {
        enterSparse();
        try {
            action.run();
        } finally {
            exitSparse();
        }
    }

    public static void enterSparse() {
        DEPTH.get()[SPARSE]++;
    }

    public static void exitSparse() {
        DEPTH.get()[SPARSE]--;
    }

    public static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    private static final class TrackedSource implements PlaneSource {
        private final PlaneSource delegate;

        private TrackedSource(PlaneSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object compute(BakePlane plane, int chunkX, int chunkZ) {
            int[] depth = DEPTH.get();
            depth[BAKING]++;
            try {
                return delegate.compute(plane, chunkX, chunkZ);
            } finally {
                depth[BAKING]--;
            }
        }

        @Override
        public ColumnBake assemble(ChunkPlanes planes) {
            int[] depth = DEPTH.get();
            depth[BAKING]++;
            try {
                return delegate.assemble(planes);
            } finally {
                depth[BAKING]--;
            }
        }

        @Override
        public boolean mainThread() {
            return delegate.mainThread();
        }

        @Override
        public boolean generationContext() {
            return delegate.generationContext();
        }
    }
}
