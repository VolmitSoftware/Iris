package com.volmit.iris.util.data.registry;

import com.volmit.iris.core.nms.container.Pair;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;


@SuppressWarnings("unchecked")
public class RegistryUtil {
    private static final Map<Class<?>, Map<NamespacedKey, Keyed>> KEYED_REGISTRY = new HashMap<>();
    private static final Map<Class<?>, Map<NamespacedKey, Object>> ENUM_REGISTRY = new HashMap<>();
    private static final Map<Class<?>, Registry<Keyed>> REGISTRY = new HashMap<>();

    @NonNull
    public static <T> T find(@NonNull Class<T> typeClass, @NonNull String... keys) {
        return find(typeClass, defaultLookup(), keys);
    }

    @NonNull
    public static <T> T find(@NonNull Class<T> typeClass, @Nullable Lookup<T> lookup, @NonNull String... keys) {
        return find(typeClass, lookup, Arrays.stream(keys).map(NamespacedKey::minecraft).toArray(NamespacedKey[]::new));
    }

    public static <T> T find(@NonNull Class<T> typeClass, @NonNull NamespacedKey... keys) {
        return find(typeClass, defaultLookup(), keys);
    }

    @NonNull
    public static <T> T find(@NonNull Class<T> typeClass, @Nullable Lookup<T> lookup, @NonNull NamespacedKey... keys) {
        if (keys.length == 0) throw new IllegalArgumentException("Need at least one key");
        Registry<Keyed> registry = null;
        if (Keyed.class.isAssignableFrom(typeClass)) {
            registry = Bukkit.getRegistry(typeClass.asSubclass(Keyed.class));
        }
        if (registry == null) {
            registry = REGISTRY.computeIfAbsent(typeClass, t -> Arrays.stream(Registry.class.getDeclaredFields())
                    .filter(field -> Modifier.isStatic(field.getModifiers()) && Modifier.isPublic(field.getModifiers()))
                    .filter(field -> Registry.class.isAssignableFrom(field.getType()))
                    .filter(field -> ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0].equals(t))
                    .map(field -> {
                        try {
                            return (Registry<Keyed>) field.get(null);
                        } catch (IllegalAccessException e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null));
        }

        if (registry != null) {
            for (NamespacedKey key : keys) {
                Keyed value = registry.get(key);
                if (value != null)
                    return (T) value;
            }
        }

        if (lookup != null)
            return lookup.find(typeClass, keys);
        throw new IllegalArgumentException("No element found for keys: " + Arrays.toString(keys));
    }

    @NonNull
    public static <T> T findByField(@NonNull Class<T> typeClass, @NonNull NamespacedKey... keys) {
        var values = KEYED_REGISTRY.computeIfAbsent(typeClass, RegistryUtil::getKeyedValues);
        for (NamespacedKey key : keys) {
            var value = values.get(key);
            if (value != null)
                return (T) value;
        }
        throw new IllegalArgumentException("No element found for keys: " + Arrays.toString(keys));
    }

    @NonNull
    public static <T> T findByEnum(@NonNull Class<T> typeClass, @NonNull NamespacedKey... keys) {
        var values = ENUM_REGISTRY.computeIfAbsent(typeClass, RegistryUtil::getEnumValues);
        for (NamespacedKey key : keys) {
            var value = values.get(key);
            if (value != null)
                return (T) value;
        }
        throw new IllegalArgumentException("No element found for keys: " + Arrays.toString(keys));
    }

    @NonNull
    public static <T> Lookup<T> defaultLookup() {
        return Lookup.combine(RegistryUtil::findByField, RegistryUtil::findByEnum);
    }

    private static Map<NamespacedKey, Keyed> getKeyedValues(@NonNull Class<?> typeClass) {
        return Arrays.stream(typeClass.getDeclaredFields())
                .filter(field -> Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers()))
                .filter(field -> Keyed.class.isAssignableFrom(field.getType()))
                .map(field -> {
                    try {
                        return (Keyed) field.get(null);
                    } catch (Throwable e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Keyed::getKey, Function.identity()));
    }

    private static Map<NamespacedKey, Object> getEnumValues(@NonNull Class<?> typeClass) {
        return Arrays.stream(typeClass.getDeclaredFields())
                .filter(field -> Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers()))
                .filter(field -> typeClass.isAssignableFrom(field.getType()))
                .map(field -> {
                    try {
                        return new Pair<>(NamespacedKey.minecraft(field.getName().toLowerCase()), field.get(null));
                    } catch (Throwable e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Pair::getA, Pair::getB));

    }

    @FunctionalInterface
    public interface Lookup<T> {
        @NonNull
        T find(@NonNull Class<T> typeClass, @NonNull NamespacedKey... keys);

        static <T> Lookup<T> combine(@NonNull Lookup<T>... lookups) {
            if (lookups.length == 0) throw new IllegalArgumentException("Need at least one lookup");
            return (typeClass, keys) -> {
                for (Lookup<T> lookup : lookups) {
                    try {
                        return lookup.find(typeClass, keys);
                    } catch (IllegalArgumentException ignored) {}
                }
                throw new IllegalArgumentException("No element found for keys: " + Arrays.toString(keys));
            };
        }
    }
}
