package art.arcane.iris.generation.block;

import art.arcane.volmlib.util.collection.KMap;
import lombok.NonNull;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class VectorMap<T> implements Iterable<Map.Entry<IrisBlockVector, T>> {
    private final Map<Key, Map<Key, T>> map = new KMap<>();
    private final AtomicLong modificationRevision = new AtomicLong();
    private transient volatile OrderedSnapshot<T> orderedSnapshot;

    public long modificationRevision() {
        return modificationRevision.get();
    }

    private void changed() {
        modificationRevision.incrementAndGet();
        orderedSnapshot = null;
    }

    public int size() {
        return map.values().stream().mapToInt(Map::size).sum();
    }

    public boolean isEmpty() {
        return map.values().stream().allMatch(Map::isEmpty);
    }

    public boolean containsKey(@NonNull IrisBlockVector vector) {
        if (map.isEmpty()) return false;
        Map<Key, T> chunk = map.get(chunk(vector));
        return chunk != null && chunk.containsKey(relative(vector));
    }

    public boolean containsValue(@NonNull T value) {
        return map.values().stream().anyMatch(m -> m.containsValue(value));
    }

    public @Nullable T get(@NonNull IrisBlockVector vector) {
        if (map.isEmpty()) return null;
        Map<Key, T> chunk = map.get(chunk(vector));
        return chunk == null ? null : chunk.get(relative(vector));
    }

    public @Nullable T put(@NonNull IrisBlockVector vector, @NonNull T value) {
        T previous = map.computeIfAbsent(chunk(vector), k -> new KMap<>())
                .put(relative(vector), value);
        changed();
        return previous;
    }

    public @Nullable T computeIfAbsent(@NonNull IrisBlockVector vector, @NonNull Function<@NonNull IrisBlockVector, @NonNull T> mappingFunction) {
        boolean[] inserted = new boolean[1];
        T value = map.computeIfAbsent(chunk(vector), key -> new KMap<>())
                .computeIfAbsent(relative(vector), key -> {
                    inserted[0] = true;
                    return mappingFunction.apply(vector);
                });
        if (inserted[0]) {
            changed();
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public @Nullable T remove(@NonNull IrisBlockVector vector) {
        if (map.isEmpty()) return null;
        Key relative = relative(vector);
        Object[] removed = new Object[1];

        // computeIfPresent so the emptied bucket is pruned atomically against a concurrent put.
        map.computeIfPresent(chunk(vector), (key, chunk) -> {
            removed[0] = chunk.remove(relative);
            return chunk.isEmpty() ? null : chunk;
        });

        if (removed[0] != null) {
            changed();
        }

        return (T) removed[0];
    }

    public void putAll(@NonNull VectorMap<T> map) {
        map.forEach(this::put);
    }

    public void clear() {
        if (!map.isEmpty()) {
            map.clear();
            changed();
        }
    }

    public void forEach(@NonNull BiConsumer<@NonNull IrisBlockVector, @NonNull T> consumer) {
        Cursor cursor = cursor();
        while (cursor.next()) {
            consumer.accept(cursor.key().clone(), cursor.value());
        }
    }

    private List<OrderedEntry<T>> orderedEntries() {
        long revision = modificationRevision.get();
        OrderedSnapshot<T> cached = orderedSnapshot;
        if (cached != null && cached.revision() == revision) {
            return cached.entries();
        }
        synchronized (this) {
            revision = modificationRevision.get();
            cached = orderedSnapshot;
            if (cached != null && cached.revision() == revision) {
                return cached.entries();
            }
            List<OrderedEntry<T>> entries = new ArrayList<>();
            map.forEach((chunk, values) -> values.forEach((relative, value) -> entries.add(
                    new OrderedEntry<>((chunk.x << 10) + relative.x, (chunk.y << 10) + relative.y,
                            (chunk.z << 10) + relative.z, value))));
            entries.sort(Comparator.comparingInt((OrderedEntry<T> entry) -> entry.x())
                    .thenComparingInt(OrderedEntry::y).thenComparingInt(OrderedEntry::z));
            if (modificationRevision.get() == revision) {
                orderedSnapshot = new OrderedSnapshot<>(revision, entries);
            }
            return entries;
        }
    }

    private record OrderedEntry<T>(int x, int y, int z, T value) {
    }

    private record OrderedSnapshot<T>(long revision, List<OrderedEntry<T>> entries) {
    }

    private static Key chunk(IrisBlockVector vector) {
        return new Key(vector.getBlockX() >> 10, vector.getBlockY() >> 10, vector.getBlockZ() >> 10);
    }

    private static Key relative(IrisBlockVector vector) {
        return new Key(vector.getBlockX() & 0x3FF, vector.getBlockY() & 0x3FF, vector.getBlockZ() & 0x3FF);
    }

    @Override
    public @NotNull EntryIterator iterator() {
        return new EntryIterator();
    }

    /**
     * Allocation-free entry walk. {@link EntryIterator} allocates a resolved vector plus a Map.Entry per element;
     * the cursor resolves into one reused vector instead. Only valid for callers that do not retain the vector
     * returned by {@link Cursor#key()} beyond the current step - clone it if it must outlive the next
     * {@link Cursor#next()}.
     */
    public @NotNull Cursor cursor() {
        return new Cursor();
    }

    public @NotNull KeyIterator keys() {
        return new KeyIterator();
    }

    public @NotNull ValueIterator values() {
        return new ValueIterator();
    }

    public final class Cursor {
        private final List<OrderedEntry<T>> entries = orderedEntries();
        private final IrisBlockVector position = new IrisBlockVector(0, 0, 0);
        private int index;
        private T value;

        public boolean next() {
            if (index >= entries.size()) {
                value = null;
                return false;
            }
            OrderedEntry<T> entry = entries.get(index++);
            position.setX(entry.x());
            position.setY(entry.y());
            position.setZ(entry.z());
            value = entry.value();
            return true;
        }

        /**
         * The position of the current element. The same instance is returned every step.
         */
        public @NotNull IrisBlockVector key() {
            return position;
        }

        public T value() {
            return value;
        }
    }

    public class EntryIterator implements Iterator<Map.Entry<IrisBlockVector, T>> {
        private final List<OrderedEntry<T>> entries = orderedEntries();
        private int index;
        private OrderedEntry<T> last;

        @Override
        public boolean hasNext() {
            return index < entries.size();
        }

        @Override
        public Map.Entry<IrisBlockVector, T> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            OrderedEntry<T> entry = entries.get(index++);
            last = entry;
            return Map.entry(new IrisBlockVector(entry.x(), entry.y(), entry.z()), entry.value());
        }

        @Override
        public void remove() {
            if (last == null) {
                throw new IllegalStateException("No element to remove");
            }
            VectorMap.this.remove(new IrisBlockVector(last.x(), last.y(), last.z()));
            last = null;
        }
    }

    public class KeyIterator implements Iterator<IrisBlockVector>, Iterable<IrisBlockVector> {
        private final List<OrderedEntry<T>> entries = orderedEntries();
        private int index;
        private OrderedEntry<T> last;

        @Override
        public boolean hasNext() {
            return index < entries.size();
        }

        @Override
        public IrisBlockVector next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            OrderedEntry<T> entry = entries.get(index++);
            last = entry;
            return new IrisBlockVector(entry.x(), entry.y(), entry.z());
        }

        @Override
        public void remove() {
            if (last == null) {
                throw new IllegalStateException("No element to remove");
            }
            VectorMap.this.remove(new IrisBlockVector(last.x(), last.y(), last.z()));
            last = null;
        }

        @Override
        public @NotNull Iterator<IrisBlockVector> iterator() {
            return this;
        }
    }

    public class ValueIterator implements Iterator<T>, Iterable<T> {
        private final List<OrderedEntry<T>> entries = orderedEntries();
        private int index;
        private OrderedEntry<T> last;

        @Override
        public boolean hasNext() {
            return index < entries.size();
        }

        @Override
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            last = entries.get(index++);
            return last.value();
        }

        @Override
        public void remove() {
            if (last == null) {
                throw new IllegalStateException("No element to remove");
            }
            VectorMap.this.remove(new IrisBlockVector(last.x(), last.y(), last.z()));
            last = null;
        }

        @Override
        public @NotNull Iterator<T> iterator() {
            return this;
        }
    }

    private static final class Key {
        private final int x;
        private final int y;
        private final int z;
        private final int hashCode;

        private Key(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.hashCode = (x << 20) | (y << 10) | z;
        }

        private IrisBlockVector resolve(int rX, int rY, int rZ) {
            return new IrisBlockVector(rX + x, rY + y, rZ + z);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Key key)) return false;
            return x == key.x && y == key.y && z == key.z;
        }
    }
}
