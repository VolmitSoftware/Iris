package com.volmit.iris.util.data;

import com.volmit.iris.util.collection.KMap;
import lombok.NonNull;
import org.bukkit.util.BlockVector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class VectorMap<T> implements Iterable<Map.Entry<BlockVector, T>> {
    private final Map<Key, Map<Key, T>> map = new KMap<>();

    public int size() {
        return map.values().stream().mapToInt(Map::size).sum();
    }

    public boolean isEmpty() {
        return map.values().stream().allMatch(Map::isEmpty);
    }

    public boolean containsKey(@NonNull BlockVector vector) {
        var chunk = map.get(chunk(vector));
        return chunk != null && chunk.containsKey(relative(vector));
    }

    public boolean containsValue(@NonNull T value) {
        return map.values().stream().anyMatch(m -> m.containsValue(value));
    }

    public @Nullable T get(@NonNull BlockVector vector) {
        var chunk = map.get(chunk(vector));
        return chunk == null ? null : chunk.get(relative(vector));
    }

    public @Nullable T put(@NonNull BlockVector vector, @NonNull T value) {
        return map.computeIfAbsent(chunk(vector), k -> new KMap<>())
                .put(relative(vector), value);
    }

    public @Nullable T computeIfAbsent(@NonNull BlockVector vector, @NonNull Function<@NonNull BlockVector, @NonNull T> mappingFunction) {
         return map.computeIfAbsent(chunk(vector), k -> new KMap<>())
                 .computeIfAbsent(relative(vector), $ -> mappingFunction.apply(vector));
    }

    public @Nullable T remove(@NonNull BlockVector vector) {
        var chunk = map.get(chunk(vector));
        return chunk == null ? null : chunk.remove(relative(vector));
    }

    public void putAll(@NonNull VectorMap<T> map) {
        map.forEach(this::put);
    }

    public void clear() {
        map.clear();
    }

    public void forEach(@NonNull BiConsumer<@NonNull BlockVector, @NonNull T> consumer) {
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

    private static Key chunk(BlockVector vector) {
        return new Key(vector.getBlockX() >> 10, vector.getBlockY() >> 10, vector.getBlockZ() >> 10);
    }

    private static Key relative(BlockVector vector) {
        return new Key(vector.getBlockX() & 0x3FF, vector.getBlockY() & 0x3FF, vector.getBlockZ() & 0x3FF);
    }

    @Override
    public @NotNull EntryIterator iterator() {
        return new EntryIterator();
    }

    public @NotNull KeyIterator keys() {
        return new KeyIterator();
    }

    public @NotNull ValueIterator values() {
        return new ValueIterator();
    }

    public class EntryIterator implements Iterator<Map.Entry<BlockVector, T>> {
        private final Iterator<Map.Entry<Key, Map<Key, T>>> chunkIterator = map.entrySet().iterator();
        private Iterator<Map.Entry<Key, T>> relativeIterator;
        private int rX, rY, rZ;

        @Override
        public boolean hasNext() {
            return relativeIterator != null && relativeIterator.hasNext() || chunkIterator.hasNext();
        }

        @Override
        public Map.Entry<BlockVector, T> next() {
            if (relativeIterator == null || !relativeIterator.hasNext()) {
                if (!chunkIterator.hasNext()) throw new IllegalStateException("No more elements");
                var chunk = chunkIterator.next();
                rX = chunk.getKey().x << 10;
                rY = chunk.getKey().y << 10;
                rZ = chunk.getKey().z << 10;
                relativeIterator = chunk.getValue().entrySet().iterator();
            }

            var entry = relativeIterator.next();
            return Map.entry(entry.getKey().resolve(rX, rY, rZ), entry.getValue());
        }

        @Override
        public void remove() {
            if (relativeIterator == null) throw new IllegalStateException("No element to remove");
            relativeIterator.remove();
        }
    }

    public class KeyIterator implements Iterator<BlockVector>, Iterable<BlockVector> {
        private final Iterator<Map.Entry<Key, Map<Key, T>>> chunkIterator = map.entrySet().iterator();
        private Iterator<Key> relativeIterator;
        private int rX, rY, rZ;

        @Override
        public boolean hasNext() {
            return relativeIterator != null && relativeIterator.hasNext() || chunkIterator.hasNext();
        }

        @Override
        public BlockVector next() {
            if (relativeIterator == null || !relativeIterator.hasNext()) {
                var chunk = chunkIterator.next();
                rX = chunk.getKey().x << 10;
                rY = chunk.getKey().y << 10;
                rZ = chunk.getKey().z << 10;
                relativeIterator = chunk.getValue().keySet().iterator();
            }

            return relativeIterator.next().resolve(rX, rY, rZ);
        }

        @Override
        public void remove() {
            if (relativeIterator == null) throw new IllegalStateException("No element to remove");
            relativeIterator.remove();
        }

        @Override
        public @NotNull Iterator<BlockVector> iterator() {
            return this;
        }
    }

    public class ValueIterator implements Iterator<T>, Iterable<T> {
        private final Iterator<Map<Key, T>> chunkIterator = map.values().iterator();
        private Iterator<T> relativeIterator;

        @Override
        public boolean hasNext() {
            return relativeIterator != null && relativeIterator.hasNext() || chunkIterator.hasNext();
        }

        @Override
        public T next() {
            if (relativeIterator == null || !relativeIterator.hasNext()) {
                relativeIterator = chunkIterator.next().values().iterator();
            }
            return relativeIterator.next();
        }

        @Override
        public void remove() {
            if (relativeIterator == null) throw new IllegalStateException("No element to remove");
            relativeIterator.remove();
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

        private BlockVector resolve(int rX, int rY, int rZ) {
            return new BlockVector(rX + x, rY + y, rZ + z);
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
