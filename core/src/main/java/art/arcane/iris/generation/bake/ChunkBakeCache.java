package art.arcane.iris.generation.bake;

import art.arcane.iris.generation.cache.ConcurrentClockCache;
import art.arcane.iris.generation.stream.ProvisionalSampling;

import java.util.Objects;
import java.util.function.Supplier;

public final class ChunkBakeCache implements BakeCache {
    private static final ThreadLocal<int[]> SPARSE = ThreadLocal.withInitial(() -> new int[1]);

    private final PlaneSource source;
    private final ThreadLocal<Recent> recent = ThreadLocal.withInitial(Recent::new);
    private volatile ConcurrentClockCache<ChunkPlanes> entries;

    public ChunkBakeCache(PlaneSource source, int capacityChunks) {
        this.source = Objects.requireNonNull(source, "Bake plane source");
        this.entries = new ConcurrentClockCache<>(Math.max(16, capacityChunks));
    }

    @Override
    public ColumnBake get(int chunkX, int chunkZ) {
        return planes(chunkX, chunkZ).columnBake(source);
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
        ConcurrentClockCache<ChunkPlanes> current = entries;
        entries = new ConcurrentClockCache<>(current.capacity());
        current.clear();
    }

    public long[] census() {
        long[] counts = new long[BakePlane.PLANES.length + 1];
        entries.forEach(planes -> {
            counts[BakePlane.PLANES.length]++;
            for (BakePlane plane : BakePlane.PLANES) {
                if (planes.planeIfPresent(plane) != null) {
                    counts[plane.ordinal()]++;
                }
            }
        });
        return counts;
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

    public ChunkPlanes.Plane planeIfPresent(BakePlane plane, int chunkX, int chunkZ) {
        long key = key(chunkX, chunkZ);
        Recent last = recent.get();
        ConcurrentClockCache<ChunkPlanes> current = entries;
        ChunkPlanes planes;
        if (last.key == key && last.entries == current) {
            planes = last.planes;
        } else {
            planes = current.get(key);
            if (planes == null) {
                return null;
            }
            last.key = key;
            last.planes = planes;
            last.entries = current;
        }
        return planes.planeIfPresent(plane);
    }

    public ChunkPlanes.Plane plane(BakePlane plane, int chunkX, int chunkZ) {
        return planes(chunkX, chunkZ).plane(plane);
    }

    public Object complete(BakePlane plane, int chunkX, int chunkZ) {
        return remembered(chunkX, chunkZ).complete(plane, source);
    }

    public boolean memoAllowed() {
        return SPARSE.get()[0] == 0 && !source.mainThread();
    }

    public void storeDouble(BakePlane plane, int blockX, int blockZ, double value, long revision) {
        if (revision != ProvisionalSampling.revision()) {
            return;
        }
        remembered(blockX >> 4, blockZ >> 4).plane(plane).storeDouble(((blockZ & 15) << 4) | (blockX & 15), value);
    }

    public void storeObject(BakePlane plane, int blockX, int blockZ, Object value, long revision) {
        if (revision != ProvisionalSampling.revision()) {
            return;
        }
        remembered(blockX >> 4, blockZ >> 4).plane(plane).storeObject(((blockZ & 15) << 4) | (blockX & 15), value);
    }

    private ChunkPlanes remembered(int chunkX, int chunkZ) {
        ConcurrentClockCache<ChunkPlanes> current = entries;
        long key = key(chunkX, chunkZ);
        ChunkPlanes existing = current.get(key);
        ChunkPlanes planes = existing != null ? existing : current.putIfAbsent(key, new ChunkPlanes(chunkX, chunkZ));
        Recent last = recent.get();
        last.key = key;
        last.planes = planes;
        last.entries = current;
        return planes;
    }

    public static <T> T sparse(Supplier<T> action) {
        enterSparse();
        try {
            return action.get();
        } finally {
            exitSparse();
        }
    }

    public static void enterSparse() {
        SPARSE.get()[0]++;
    }

    public static void exitSparse() {
        SPARSE.get()[0]--;
    }

    public static boolean inSparseScope() {
        return SPARSE.get()[0] > 0;
    }

    public static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    private static final class Recent {
        private long key;
        private ChunkPlanes planes;
        private ConcurrentClockCache<ChunkPlanes> entries;
    }
}
