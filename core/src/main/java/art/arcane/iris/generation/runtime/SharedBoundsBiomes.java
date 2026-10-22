package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.biome.IrisBiome;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;

final class SharedBoundsBiomes {
    static final int MINIMUM_CAPACITY = 1 << 16;
    static final int MAXIMUM_CAPACITY = 1 << 21;
    static final long BYTES_PER_ENTRY = 16L;
    static final long HEAP_SHARE = 256L;
    private static final VarHandle INTS = MethodHandles.arrayElementVarHandle(int[].class);
    private static final VarHandle LONGS = MethodHandles.arrayElementVarHandle(long[].class);
    private static final VarHandle OBJECTS = MethodHandles.arrayElementVarHandle(Object[].class);

    private final Object source;
    private final long[] keys;
    private final Object[] values;
    private final int[] stamps;
    private final int mask;

    SharedBoundsBiomes(Object source, int capacity) {
        if (capacity < 2 || Integer.bitCount(capacity) != 1) {
            throw new IllegalArgumentException("capacity must be a power of two of at least 2");
        }
        this.source = Objects.requireNonNull(source, "source");
        this.keys = new long[capacity];
        this.values = new Object[capacity];
        this.stamps = new int[capacity];
        this.mask = capacity - 1;
    }

    static int capacityFor(long maxMemory) {
        long entries = Math.max(0L, maxMemory) / HEAP_SHARE / BYTES_PER_ENTRY;
        long bounded = Math.max(MINIMUM_CAPACITY, Math.min(MAXIMUM_CAPACITY, entries));
        return Integer.highestOneBit((int) bounded);
    }

    Object source() {
        return source;
    }

    int slot(int x, int z) {
        long mixed = key(x, z);
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return (int) ((mixed ^ (mixed >>> 31)) & mask);
    }

    IrisBiome get(int x, int z) {
        long key = key(x, z);
        int slot = slot(x, z);
        int before = (int) INTS.getAcquire(stamps, slot);
        if (before == 0 || (before & 1) != 0) {
            return null;
        }
        long storedKey = (long) LONGS.getAcquire(keys, slot);
        Object value = OBJECTS.getAcquire(values, slot);
        VarHandle.loadLoadFence();
        int after = (int) INTS.getAcquire(stamps, slot);
        if (before != after || storedKey != key) {
            return null;
        }
        return (IrisBiome) value;
    }

    void put(int x, int z, IrisBiome biome) {
        if (biome == null) {
            return;
        }
        int slot = slot(x, z);
        int stamp = (int) INTS.getAcquire(stamps, slot);
        if ((stamp & 1) != 0 || !INTS.compareAndSet(stamps, slot, stamp, stamp + 1)) {
            return;
        }
        LONGS.setRelease(keys, slot, key(x, z));
        OBJECTS.setRelease(values, slot, biome);
        INTS.setRelease(stamps, slot, stamp + 2);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
