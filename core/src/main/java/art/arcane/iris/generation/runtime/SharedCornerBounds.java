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
    private static final VarHandle STAMPS = MethodHandles.arrayElementVarHandle(int[].class);
    private static final VarHandle LONGS = MethodHandles.arrayElementVarHandle(long[].class);
    private static final long PRESENT = 1L << 63;
    private static final long COORDINATE_MASK = (1L << 21) - 1;

    private final long[] keys;
    private final long[] values;
    private final int[] stamps;
    private final int mask;

    SharedCornerBounds(int capacity) {
        if (capacity < 2 || Integer.bitCount(capacity) != 1) {
            throw new IllegalArgumentException("capacity must be a power of two of at least 2");
        }
        this.keys = new long[capacity];
        this.values = new long[capacity];
        this.stamps = new int[capacity];
        this.mask = capacity - 1;
    }

    /** Never zero, so an untouched slot can never match a real corner. */
    static long key(int cornerX, int cornerZ, int interpolatorIndex) {
        return PRESENT
                | ((cornerX & COORDINATE_MASK) << 42)
                | ((cornerZ & COORDINATE_MASK) << 21)
                | (interpolatorIndex & COORDINATE_MASK);
    }

    int slot(long key) {
        long mixed = (key ^ (key >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return (int) ((mixed ^ (mixed >>> 31)) & mask);
    }

    boolean contains(long key) {
        int slot = slot(key);
        int before = (int) STAMPS.getAcquire(stamps, slot);
        if ((before & 1) != 0) {
            return false;
        }
        long storedKey = (long) LONGS.getAcquire(keys, slot);
        VarHandle.loadLoadFence();
        int after = (int) STAMPS.getAcquire(stamps, slot);
        return before == after && storedKey == key;
    }

    /**
     * The value stored for the key, or {@link Long#MIN_VALUE} when the slot holds another corner
     * or is being written. Callers check {@link #contains(long)} first; a value that legitimately
     * equals the sentinel is recomputed, which is only a wasted starcast.
     */
    long get(long key) {
        int slot = slot(key);
        int before = (int) STAMPS.getAcquire(stamps, slot);
        if ((before & 1) != 0) {
            return Long.MIN_VALUE;
        }
        long storedKey = (long) LONGS.getAcquire(keys, slot);
        long value = (long) LONGS.getAcquire(values, slot);
        // The data loads above must not drift past the second stamp read, or a torn slot could pass.
        VarHandle.loadLoadFence();
        int after = (int) STAMPS.getAcquire(stamps, slot);
        if (before != after || storedKey != key) {
            return Long.MIN_VALUE;
        }
        return value;
    }

    void put(long key, long value) {
        int slot = slot(key);
        int stamp = (int) STAMPS.getAcquire(stamps, slot);
        if ((stamp & 1) != 0) {
            // Another writer owns the slot; the corner is recomputed by whoever misses next.
            return;
        }
        if (!STAMPS.compareAndSet(stamps, slot, stamp, stamp + 1)) {
            return;
        }
        LONGS.setRelease(keys, slot, key);
        LONGS.setRelease(values, slot, value);
        STAMPS.setRelease(stamps, slot, stamp + 2);
    }
}
