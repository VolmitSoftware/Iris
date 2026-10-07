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

    private final AtomicReferenceArray<CacheSet<V>> slots;
    private final long sets;
    private final int capacity;

    public ConcurrentClockCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Cache capacity must be positive");
        }
        int setCount = Math.ceilDiv(capacity, WAYS);
        this.capacity = Math.multiplyExact(setCount, WAYS);
        sets = setCount;
        slots = new AtomicReferenceArray<>(setCount);
    }

    public int capacity() {
        return capacity;
    }

    public V get(long key) {
        CacheSet<V> snapshot = slots.getAcquire(index(CacheKey.mix(key)));
        if (snapshot == null) {
            return null;
        }
        for (int way = 0; way < WAYS; way++) {
            Entry<V> entry = snapshot.get(way);
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
        int index = index(hash);
        int hand = (int) (hash >>> 60) & (WAYS - 1);
        Entry<V> created = null;
        while (true) {
            CacheSet<V> snapshot = slots.getAcquire(index);
            if (snapshot == null) {
                if (created == null) {
                    created = new Entry<>(key, value);
                }
                if (slots.compareAndSet(index, null, new CacheSet<>(created, null, null, null))) {
                    return value;
                }
                continue;
            }
            int empty = -1;
            for (int way = 0; way < WAYS; way++) {
                Entry<V> entry = snapshot.get(way);
                if (entry == null) {
                    if (empty < 0) {
                        empty = way;
                    }
                } else if (entry.key == key) {
                    entry.touch();
                    return entry.value;
                }
            }
            int victim = empty;
            for (int step = 0; step < WAYS * 2 && victim < 0; step++) {
                int way = (hand + step) & (WAYS - 1);
                Entry<V> entry = snapshot.get(way);
                if (!entry.referenced) {
                    victim = way;
                } else {
                    entry.referenced = false;
                }
            }
            if (victim < 0) {
                victim = hand;
            }
            if (created == null) {
                created = new Entry<>(key, value);
            }
            if (slots.compareAndSet(index, snapshot, snapshot.with(victim, created))) {
                return value;
            }
        }
    }

    public void forEach(Consumer<? super V> action) {
        for (int index = 0; index < slots.length(); index++) {
            CacheSet<V> snapshot = slots.getAcquire(index);
            if (snapshot == null) {
                continue;
            }
            for (int way = 0; way < WAYS; way++) {
                Entry<V> entry = snapshot.get(way);
                if (entry != null) {
                    action.accept(entry.value);
                }
            }
        }
    }

    public void clear() {
        for (int index = 0; index < slots.length(); index++) {
            slots.setRelease(index, null);
        }
    }

    private int index(long hash) {
        return (int) (((hash & 0xffffffffL) * sets) >>> 32);
    }

    private record CacheSet<V>(Entry<V> first, Entry<V> second, Entry<V> third, Entry<V> fourth) {
        private Entry<V> get(int way) {
            return switch (way) {
                case 0 -> first;
                case 1 -> second;
                case 2 -> third;
                case 3 -> fourth;
                default -> throw new IndexOutOfBoundsException(way);
            };
        }

        private CacheSet<V> with(int way, Entry<V> entry) {
            return switch (way) {
                case 0 -> new CacheSet<>(entry, second, third, fourth);
                case 1 -> new CacheSet<>(first, entry, third, fourth);
                case 2 -> new CacheSet<>(first, second, entry, fourth);
                case 3 -> new CacheSet<>(first, second, third, entry);
                default -> throw new IndexOutOfBoundsException(way);
            };
        }
    }

    private static final class Entry<V> {
        private final long key;
        private final V value;
        private volatile boolean referenced;

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
