package art.arcane.iris.generation.cache;

import art.arcane.volmlib.util.cache.CacheKey;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Consumer;

/**
 * A bounded, lock-free, lossy long-keyed cache for memoized values. Keys hash into 4-way sets and a full set
 * evicts with CLOCK (second chance), so reads never write shared state except to set a reference bit once.
 * Concurrent inserts of the same key may both compute; the first published value wins.
 */
public final class ConcurrentClockCache<V> {
    private static final int WAYS = 4;

    private final AtomicReferenceArray<Entry<V>> slots;
    private final long sets;

    public ConcurrentClockCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Cache capacity must be positive");
        }
        int setCount = Math.ceilDiv(capacity, WAYS);
        sets = setCount;
        slots = new AtomicReferenceArray<>(Math.multiplyExact(setCount, WAYS));
    }

    public int capacity() {
        return slots.length();
    }

    public V get(long key) {
        long hash = CacheKey.mix(key);
        int base = base(hash);
        for (int way = 0; way < WAYS; way++) {
            Entry<V> entry = slots.getAcquire(base + way);
            if (entry != null && entry.key == key) {
                entry.touch();
                return entry.value;
            }
        }
        return null;
    }

    public V putIfAbsent(long key, V value) {
        Objects.requireNonNull(value, "Cached value");
        long hash = CacheKey.mix(key);
        int base = base(hash);
        int hand = (int) (hash >>> 60) & (WAYS - 1);
        while (true) {
            int empty = -1;
            for (int way = 0; way < WAYS; way++) {
                Entry<V> entry = slots.getAcquire(base + way);
                if (entry == null) {
                    if (empty < 0) {
                        empty = way;
                    }
                } else if (entry.key == key) {
                    entry.touch();
                    return entry.value;
                }
            }
            Entry<V> created = new Entry<>(key, value);
            if (empty >= 0) {
                if (slots.compareAndSet(base + empty, null, created)) {
                    return value;
                }
                continue;
            }
            int victim = -1;
            Entry<V> victimEntry = null;
            for (int step = 0; step < WAYS * 2 && victim < 0; step++) {
                int way = (hand + step) & (WAYS - 1);
                Entry<V> entry = slots.getAcquire(base + way);
                if (entry == null || !entry.referenced) {
                    victim = way;
                    victimEntry = entry;
                } else {
                    entry.referenced = false;
                }
            }
            if (victim < 0) {
                victim = hand;
                victimEntry = slots.getAcquire(base + victim);
            }
            if (slots.compareAndSet(base + victim, victimEntry, created)) {
                return value;
            }
        }
    }

    public void forEach(Consumer<? super V> action) {
        for (int index = 0; index < slots.length(); index++) {
            Entry<V> entry = slots.getAcquire(index);
            if (entry != null) {
                action.accept(entry.value);
            }
        }
    }

    public void clear() {
        for (int index = 0; index < slots.length(); index++) {
            slots.setRelease(index, null);
        }
    }

    private int base(long hash) {
        return (int) (((hash & 0xffffffffL) * sets) >>> 32) * WAYS;
    }

    private static final class Entry<V> {
        private final long key;
        private final V value;
        private boolean referenced;

        private Entry(long key, V value) {
            this.key = key;
            this.value = value;
        }

        private void touch() {
            if (!referenced) {
                referenced = true;
            }
        }
    }
}
