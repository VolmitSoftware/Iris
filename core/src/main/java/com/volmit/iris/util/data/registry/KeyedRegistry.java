package com.volmit.iris.util.data.registry;

import com.volmit.iris.util.collection.KMap;
import lombok.NonNull;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface KeyedRegistry<T> {
    @NonNull Map<NamespacedKey, T> map();

    @Nullable T get(@NonNull NamespacedKey key);
    @Nullable NamespacedKey keyOf(@NonNull T value);

    default boolean isEmpty() {
        return map().isEmpty();
    }

    @NonNull
    default Optional<T> find(@NonNull String @NonNull ... keys) {
        if (keys.length == 0) throw new IllegalArgumentException("Need at least one key");
        for (final String key : keys) {
            final T t = get(NamespacedKey.minecraft(key));
            if (t != null) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }

    @NonNull
    default Optional<T> find(@NonNull NamespacedKey @NonNull ... keys) {
        if (keys.length == 0) throw new IllegalArgumentException("Need at least one key");
        for (final NamespacedKey key : keys) {
            final T t = get(key);
            if (t != null) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }
    
    @Contract(value = "null -> null; !null -> new", pure = true)
    static <T> KeyedRegistry<T> wrapped(Map<NamespacedKey, T> map) {
        if (map == null) return null;
        return new MappedRegistry<>(map);
    }

    @Contract(value = "null -> null; !null -> new", pure = true)
    static <T extends Keyed> KeyedRegistry<T> wrapped(Registry<T> registry) {
        if (registry == null) return null;
        return new BukkitRegistry<>(registry);
    }
    
    @Contract(value = "_ -> new", pure = true)
    static <T> KeyedRegistry<T> wrapped(@NonNull Collection<@NonNull KeyedRegistry<T>> registries) {
        return new CompoundRegistry<>(registries);
    }
    
    
    record MappedRegistry<T>(Map<NamespacedKey, T> map) implements KeyedRegistry<T> {
        @Override
        public @Nullable T get(@NonNull NamespacedKey key) {
            return map.get(key);
        }

        @Override
        public @Nullable NamespacedKey keyOf(@NonNull T value) {
            return map.entrySet().stream()
                    .filter(e -> e.getValue().equals(value))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
        }
    }

    record BukkitRegistry<T extends Keyed>(Registry<T> registry) implements KeyedRegistry<T> {
        @Override
        public @NonNull Map<NamespacedKey, T> map() {
            return registry.stream().collect(Collectors.toMap(Keyed::getKey, Function.identity()));
        }

        @Override
        public @Nullable T get(@NonNull NamespacedKey key) {
            return registry.get(key);
        }

        @Override
        public @NonNull NamespacedKey keyOf(@NonNull T value) {
            return value.getKey();
        }
    }

    record CompoundRegistry<T>(Collection<KeyedRegistry<T>> registries) implements KeyedRegistry<T> {
        @Override
        public @NonNull Map<NamespacedKey, T> map() {
            final KMap<NamespacedKey, T> m = new KMap<>();
            for (final KeyedRegistry<T> registry : registries) {
                m.put(registry.map());
            }
            return m;
        }

        @Override
        public @Nullable T get(@NonNull NamespacedKey key) {
            for (final KeyedRegistry<T> registry : registries) {
                final T t = registry.get(key);
                if (t != null) {
                    return t;
                }
            }
            return null;
        }

        @Override
        public @Nullable NamespacedKey keyOf(@NonNull T value) {
            for (final KeyedRegistry<T> registry : registries) {
                final NamespacedKey key = registry.keyOf(value);
                if (key != null) {
                    return key;
                }
            }
            return null;
        }

        @Override
        public boolean isEmpty() {
            return registries.isEmpty() || registries.stream().allMatch(KeyedRegistry::isEmpty);
        }
    }
}
