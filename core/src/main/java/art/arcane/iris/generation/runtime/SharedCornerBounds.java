package art.arcane.iris.generation.runtime;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Direct-mapped, lock-free cache of packed height bounds at grid corners, shared by every thread
 * that samples a complex. A corner's bounds depend only on its coordinates and interpolator, so
 * one thread's starcast serves all others and survives from hydrology planning into chunk
 * generation. Each slot is guarded by a sequence stamp: writers bump it to odd, store, and bump
 * it to even; readers reject a slot whose stamp changed or was odd while they read it.
 */
final class SharedCornerBounds {
    private static final VarHandle INTS = MethodHandles.arrayElementVarHandle(int[].class);
    private static final VarHandle LONGS = MethodHandles.arrayElementVarHandle(long[].class);

    private final long[] keys;
    private final long[] values;
    private final int[] interpolators;
    private final int[] stamps;
    private final int mask;

    SharedCornerBounds(int capacity) {
        if (capacity < 2 || Integer.bitCount(capacity) != 1) {
            throw new IllegalArgumentException("capacity must be a power of two of at least 2");
        }
        this.keys = new long[capacity];
        this.values = new long[capacity];
        this.interpolators = new int[capacity];
        this.stamps = new int[capacity];
        this.mask = capacity - 1;
    }

    static long key(int cornerX, int cornerZ) {
        return ((long) cornerX << 32) | (cornerZ & 0xFFFFFFFFL);
    }

    int slot(long key, int interpolatorIndex) {
        long mixed = key ^ (0x9E3779B97F4A7C15L * interpolatorIndex);
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return (int) ((mixed ^ (mixed >>> 31)) & mask);
    }

    /**
     * The value stored for the key, or {@link Long#MIN_VALUE} when the slot holds another corner
     * or is being written. A value that legitimately equals the sentinel is recomputed, which is
     * only a wasted starcast.
     */
    long get(long key, int interpolatorIndex) {
        int slot = slot(key, interpolatorIndex);
        int before = (int) INTS.getAcquire(stamps, slot);
        if (before == 0 || (before & 1) != 0) {
            return Long.MIN_VALUE;
        }
        long storedKey = (long) LONGS.getAcquire(keys, slot);
        int storedInterpolator = (int) INTS.getAcquire(interpolators, slot);
        long value = (long) LONGS.getAcquire(values, slot);
        // The data loads above must not drift past the second stamp read, or a torn slot could pass.
        VarHandle.loadLoadFence();
        int after = (int) INTS.getAcquire(stamps, slot);
        if (before != after || storedKey != key || storedInterpolator != interpolatorIndex) {
            return Long.MIN_VALUE;
        }
        return value;
    }

    void put(long key, int interpolatorIndex, long value) {
        int slot = slot(key, interpolatorIndex);
        int stamp = (int) INTS.getAcquire(stamps, slot);
        if ((stamp & 1) != 0) {
            // Another writer owns the slot; the corner is recomputed by whoever misses next.
            return;
        }
        if (!INTS.compareAndSet(stamps, slot, stamp, stamp + 1)) {
            return;
        }
        LONGS.setRelease(keys, slot, key);
        INTS.setRelease(interpolators, slot, interpolatorIndex);
        LONGS.setRelease(values, slot, value);
        INTS.setRelease(stamps, slot, stamp + 2);
    }
}
