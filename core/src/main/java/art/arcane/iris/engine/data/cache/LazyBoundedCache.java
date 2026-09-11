package art.arcane.iris.engine.data.cache;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.function.Function;

public final class LazyBoundedCache<K, V> {
    private final int maximumSize;
    private transient LinkedHashMap<K, V> entries;

    public LazyBoundedCache(int maximumSize) {
        if (maximumSize <= 0) {
            throw new IllegalArgumentException("Maximum cache size must be positive");
        }
        this.maximumSize = maximumSize;
    }

    public synchronized V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        K requiredKey = Objects.requireNonNull(key, "Cache key");
        Function<? super K, ? extends V> requiredMapping = Objects.requireNonNull(mappingFunction, "Cache mapping function");
        if (entries != null) {
            V cached = entries.get(requiredKey);
            if (cached != null) {
                return cached;
            }
        }

        V computed = requiredMapping.apply(requiredKey);
        if (computed == null) {
            return null;
        }
        if (entries == null) {
            entries = new LinkedHashMap<>(maximumSize, 1F, true);
        }
        entries.put(requiredKey, computed);
        if (entries.size() > maximumSize) {
            entries.sequencedKeySet().removeFirst();
        }
        return computed;
    }

    synchronized boolean isInitialized() {
        return entries != null;
    }

    synchronized int size() {
        return entries == null ? 0 : entries.size();
    }
}
