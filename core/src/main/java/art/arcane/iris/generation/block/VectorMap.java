package art.arcane.iris.generation.block;

import art.arcane.volmlib.util.collection.KMap;
import lombok.NonNull;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class VectorMap<T> implements Iterable<Map.Entry<IrisBlockVector, T>> {
    private final Map<Key, Map<Key, T>> map = new KMap<>();
    private transient volatile long modificationRevision;

    public long modificationRevision() {
        return modificationRevision;
    }

    public int size() {
        return map.values().stream().mapToInt(Map::size).sum();
    }

    public boolean isEmpty() {
        return map.values().stream().allMatch(Map::isEmpty);
    }

    public boolean containsKey(@NonNull IrisBlockVector vector) {
        if (map.isEmpty()) return false;
        var chunk = map.get(chunk(vector));
        return chunk != null && chunk.containsKey(relative(vector));
    }

    public boolean containsValue(@NonNull T value) {
        return map.values().stream().anyMatch(m -> m.containsValue(value));
    }

    public @Nullable T get(@NonNull IrisBlockVector vector) {
        if (map.isEmpty()) return null;
        var chunk = map.get(chunk(vector));
        return chunk == null ? null : chunk.get(relative(vector));
    }

    public @Nullable T put(@NonNull IrisBlockVector vector, @NonNull T value) {
        T previous = map.computeIfAbsent(chunk(vector), k -> new KMap<>())
                .put(relative(vector), value);
        modificationRevision++;
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
            modificationRevision++;
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
            modificationRevision++;
        }

        return (T) removed[0];
    }

    public void putAll(@NonNull VectorMap<T> map) {
        map.forEach(this::put);
    }

    public void clear() {
        if (!map.isEmpty()) {
            modificationRevision++;
        }
        map.clear();
    }

    public void forEach(@NonNull BiConsumer<@NonNull IrisBlockVector, @NonNull T> consumer) {
        map.forEach((chunk, values) -> {
            int rX = chunk.x << 10;
            int rY = chunk.y << 10;
            int rZ = chunk.z << 10;

            values.forEach((relative, value) -> consumer.accept(
                    relative.resolve(rX, rY, rZ),
                    value
            ));
        });
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
        private final Iterator<Map.Entry<Key, Map<Key, T>>> chunkIterator = map.entrySet().iterator();
        private final IrisBlockVector position = new IrisBlockVector(0, 0, 0);
        private Iterator<Map.Entry<Key, T>> relativeIterator;
        private int rX, rY, rZ;
        private T value;

        public boolean next() {
            while (relativeIterator == null || !relativeIterator.hasNext()) {
                if (!chunkIterator.hasNext()) {
                    value = null;
                    return false;
                }

                Map.Entry<Key, Map<Key, T>> chunk = chunkIterator.next();
                rX = chunk.getKey().x << 10;
                rY = chunk.getKey().y << 10;
                rZ = chunk.getKey().z << 10;
                relativeIterator = chunk.getValue().entrySet().iterator();
            }

            Map.Entry<Key, T> entry = relativeIterator.next();
            Key relative = entry.getKey();
            position.setX(rX + relative.x);
            position.setY(rY + relative.y);
            position.setZ(rZ + relative.z);
            value = entry.getValue();
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
        private final Iterator<Map.Entry<Key, Map<Key, T>>> chunkIterator = map.entrySet().iterator();
        private Iterator<Map.Entry<Key, T>> relativeIterator;
        private int rX, rY, rZ;

        @Override
        public boolean hasNext() {
            return advance();
        }

        @Override
        public Map.Entry<IrisBlockVector, T> next() {
            if (!advance()) throw new NoSuchElementException();

            var entry = relativeIterator.next();
            return Map.entry(entry.getKey().resolve(rX, rY, rZ), entry.getValue());
        }

        private boolean advance() {
            while (relativeIterator == null || !relativeIterator.hasNext()) {
                if (!chunkIterator.hasNext()) return false;
                var chunk = chunkIterator.next();
                rX = chunk.getKey().x << 10;
                rY = chunk.getKey().y << 10;
                rZ = chunk.getKey().z << 10;
                relativeIterator = chunk.getValue().entrySet().iterator();
            }

            return true;
        }

        @Override
        public void remove() {
            if (relativeIterator == null) throw new IllegalStateException("No element to remove");
            relativeIterator.remove();
            modificationRevision++;
        }
    }

    public class KeyIterator implements Iterator<IrisBlockVector>, Iterable<IrisBlockVector> {
        private final Iterator<Map.Entry<Key, Map<Key, T>>> chunkIterator = map.entrySet().iterator();
        private Iterator<Key> relativeIterator;
        private int rX, rY, rZ;

        @Override
        public boolean hasNext() {
            return advance();
        }

        @Override
        public IrisBlockVector next() {
            if (!advance()) throw new NoSuchElementException();

            return relativeIterator.next().resolve(rX, rY, rZ);
        }

        private boolean advance() {
            while (relativeIterator == null || !relativeIterator.hasNext()) {
                if (!chunkIterator.hasNext()) return false;
                var chunk = chunkIterator.next();
                rX = chunk.getKey().x << 10;
                rY = chunk.getKey().y << 10;
                rZ = chunk.getKey().z << 10;
                relativeIterator = chunk.getValue().keySet().iterator();
            }

            return true;
        }

        @Override
        public void remove() {
            if (relativeIterator == null) throw new IllegalStateException("No element to remove");
            relativeIterator.remove();
            modificationRevision++;
        }

        @Override
        public @NotNull Iterator<IrisBlockVector> iterator() {
            return this;
        }
    }

    public class ValueIterator implements Iterator<T>, Iterable<T> {
        private final Iterator<Map<Key, T>> chunkIterator = map.values().iterator();
        private Iterator<T> relativeIterator;

        @Override
        public boolean hasNext() {
            return advance();
        }

        @Override
        public T next() {
            if (!advance()) throw new NoSuchElementException();

            return relativeIterator.next();
        }

        private boolean advance() {
            while (relativeIterator == null || !relativeIterator.hasNext()) {
                if (!chunkIterator.hasNext()) return false;
                relativeIterator = chunkIterator.next().values().iterator();
            }

            return true;
        }

        @Override
        public void remove() {
            if (relativeIterator == null) throw new IllegalStateException("No element to remove");
            relativeIterator.remove();
            modificationRevision++;
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
