package art.arcane.iris.generation.bake;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Objects;

public final class BakeRegistry<T> {
    private final Object lock = new Object();
    private volatile IdentityHashMap<T, Integer> ids = new IdentityHashMap<>();
    private volatile Object[] values = new Object[16];
    private int size;

    public short id(T value) {
        Objects.requireNonNull(value, "Registered value");
        Integer known = ids.get(value);
        if (known != null) {
            return known.shortValue();
        }
        synchronized (lock) {
            Integer existing = ids.get(value);
            if (existing != null) {
                return existing.shortValue();
            }
            if (size == Short.MAX_VALUE) {
                throw new IllegalStateException("Bake registry is full");
            }
            Object[] grown = size == values.length ? Arrays.copyOf(values, size * 2) : values;
            grown[size] = value;
            values = grown;
            IdentityHashMap<T, Integer> next = new IdentityHashMap<>(ids);
            next.put(value, size);
            ids = next;
            return (short) size++;
        }
    }

    @SuppressWarnings("unchecked")
    public T value(int id) {
        return (T) values[id];
    }
}
