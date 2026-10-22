package art.arcane.iris.generation.bake;

import art.arcane.iris.generation.cache.ConcurrentClockCache;
import art.arcane.iris.generation.stream.ProvisionalSampling;

import java.util.Objects;

public final class ChunkBakeCache {
    public static final int MINIMUM_CAPACITY = 2_048;
    static final int MAXIMUM_CAPACITY = 32_768;
    static final long BYTES_PER_ENTRY = 13L * 1_024L;
    static final int HEAP_SHARE = 32;
    private static final ThreadLocal<int[]> SPARSE = ThreadLocal.withInitial(() -> new int[1]);

    private final PlaneSource source;
    private final ConcurrentClockCache<ChunkPlanes> entries;
    private final ThreadLocal<Recent> recent = ThreadLocal.withInitial(Recent::new);
    private volatile int generation;

    public ChunkBakeCache(PlaneSource source, int capacityChunks) {
        this.source = Objects.requireNonNull(source, "Bake plane source");
        this.entries = new ConcurrentClockCache<>(Math.max(16, capacityChunks));
    }

    public static int capacityFor(long maxMemory) {
        long entries = Math.max(0L, maxMemory) / HEAP_SHARE / BYTES_PER_ENTRY;
        return (int) Math.max(MINIMUM_CAPACITY, Math.min(MAXIMUM_CAPACITY, entries));
    }

    public int capacity() {
        return entries.capacity();
    }

    public synchronized void clear() {
        generation++;
        entries.forEach(ChunkPlanes::release);
        entries.clear();
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

    public ChunkPlanes.Plane planeIfPresent(BakePlane plane, int chunkX, int chunkZ) {
        long key = key(chunkX, chunkZ);
        Recent last = recent.get();
        int current = generation;
        ChunkPlanes planes;
        if (last.key == key && last.generation == current) {
            planes = last.planes;
        } else {
            planes = entries.get(key);
            if (planes == null) {
                return null;
            }
            last.remember(key, planes, current);
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

    private ChunkPlanes planes(int chunkX, int chunkZ) {
        long key = key(chunkX, chunkZ);
        ChunkPlanes existing = entries.get(key);
        return existing != null ? existing : entries.putIfAbsent(key, new ChunkPlanes(chunkX, chunkZ));
    }

    private ChunkPlanes remembered(int chunkX, int chunkZ) {
        int current = generation;
        ChunkPlanes planes = planes(chunkX, chunkZ);
        recent.get().remember(key(chunkX, chunkZ), planes, current);
        return planes;
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
        private int generation = -1;
        private ChunkPlanes planes;

        private void remember(long key, ChunkPlanes planes, int generation) {
            this.key = key;
            this.planes = planes;
            this.generation = generation;
        }
    }
}
