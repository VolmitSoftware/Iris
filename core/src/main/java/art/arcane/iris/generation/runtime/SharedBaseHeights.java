package art.arcane.iris.generation.runtime;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

final class SharedBaseHeights {
    static final int MINIMUM_CAPACITY = 1 << 16;
    static final int MAXIMUM_CAPACITY = 1 << 21;
    static final long BYTES_PER_ENTRY = 20L;
    static final long HEAP_SHARE = 256L;
    private static final VarHandle INTS = MethodHandles.arrayElementVarHandle(int[].class);
    private static final VarHandle LONGS = MethodHandles.arrayElementVarHandle(long[].class);

    private final long[] keys;
    private final long[] values;
    private final int[] stamps;
    private final int mask;

    SharedBaseHeights(int capacity) {
        if (capacity < 2 || Integer.bitCount(capacity) != 1) {
            throw new IllegalArgumentException("capacity must be a power of two of at least 2");
        }
        this.keys = new long[capacity];
        this.values = new long[capacity];
        this.stamps = new int[capacity];
        this.mask = capacity - 1;
    }

    static int capacityFor(long maxMemory) {
        long entries = Math.max(0L, maxMemory) / HEAP_SHARE / BYTES_PER_ENTRY;
        long bounded = Math.max(MINIMUM_CAPACITY, Math.min(MAXIMUM_CAPACITY, entries));
        return Integer.highestOneBit((int) bounded);
    }

    double get(int x, int z) {
        long key = key(x, z);
        int slot = slot(key);
        int before = (int) INTS.getAcquire(stamps, slot);
        if (before == 0 || (before & 1) != 0) {
            return Double.NaN;
        }
        long storedKey = (long) LONGS.getAcquire(keys, slot);
        long value = (long) LONGS.getAcquire(values, slot);
        VarHandle.loadLoadFence();
        int after = (int) INTS.getAcquire(stamps, slot);
        if (before != after || storedKey != key) {
            return Double.NaN;
        }
        return Double.longBitsToDouble(value);
    }

    void put(int x, int z, double height) {
        if (!Double.isFinite(height)) {
            return;
        }
        long key = key(x, z);
        int slot = slot(key);
        int stamp = (int) INTS.getAcquire(stamps, slot);
        if ((stamp & 1) != 0 || !INTS.compareAndSet(stamps, slot, stamp, stamp + 1)) {
            return;
        }
        LONGS.setRelease(keys, slot, key);
        LONGS.setRelease(values, slot, Double.doubleToRawLongBits(height));
        INTS.setRelease(stamps, slot, stamp + 2);
    }

    private int slot(long key) {
        long mixed = (key ^ (key >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return (int) ((mixed ^ (mixed >>> 31)) & mask);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
