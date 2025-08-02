package com.volmit.iris.util.data.registry;

import com.volmit.iris.core.nms.container.Pair;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.volmit.iris.util.data.registry.KeyedRegistry.wrapped;

@SuppressWarnings("unchecked")
public class RegistryUtil {
    private static final AtomicCache<RegistryLookup> registryLookup = new AtomicCache<>();
    private static final KMap<Class<?>, KeyedRegistry<Object>> CACHE = new KMap<>();

    @NonNull
    public static <T> T find(@NonNull Class<T> typeClass, @NonNull String... keys) {
        return find(typeClass, Arrays.stream(keys).map(NamespacedKey::minecraft).toArray(NamespacedKey[]::new));
    }

    @NonNull
    public static <T> T find(@NonNull Class<T> typeClass, @NonNull NamespacedKey... keys) {
        return lookup(typeClass).find(keys).orElseThrow(() -> new IllegalArgumentException("No element found for keys: " + Arrays.toString(keys)));
    }

    @NonNull
    public static <T> KeyedRegistry<T> lookup(@NonNull Class<T> typeClass) {
        return (KeyedRegistry<T>) CACHE.computeIfAbsent(typeClass, $ -> {
            final var registries = new KList<KeyedRegistry<Object>>();
            if (Keyed.class.isAssignableFrom(typeClass)) {
                var bukkit = wrapped(getRegistry(typeClass.asSubclass(Keyed.class)));
                if (bukkit == null) {
                    bukkit = Arrays.stream(Registry.class.getDeclaredFields())
                            .filter(field -> Modifier.isStatic(field.getModifiers()) && Modifier.isPublic(field.getModifiers()))
                            .filter(field -> Registry.class.isAssignableFrom(field.getType()))
                            .filter(field -> ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0].equals(typeClass))
                            .map(field -> {
                                try {
                                    return (Registry<Keyed>) field.get(null);
                                } catch (IllegalAccessException e) {
                                    return null;
                                }
                            })
                            .filter(Objects::nonNull)
                            .findFirst()
                            .map(KeyedRegistry::wrapped)
                            .orElse(null);
                }

                registries.addNonNull((KeyedRegistry<Object>) (Object) bukkit);
            }
            registries.add(getKeyedValues(typeClass));
            registries.add(getEnumValues(typeClass));

            return wrapped(registries);
        });
    }

    private static KeyedRegistry<Object> getKeyedValues(@NonNull Class<?> typeClass) {
        return wrapped(Arrays.stream(typeClass.getDeclaredFields())
                .filter(field -> Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers()))
                .filter(field -> Keyed.class.isAssignableFrom(field.getType()))
                .map(field -> {
                    try {
                        final var value = (Keyed) field.get(null);
                        return new Pair<>(value.getKey(), value);
                    } catch (Throwable e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Pair::getA, Pair::getB)));
    }

    private static KeyedRegistry<Object> getEnumValues(@NonNull Class<?> typeClass) {
        return wrapped(Arrays.stream(typeClass.getDeclaredFields())
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
                .collect(Collectors.toMap(Pair::getA, Pair::getB)));

    }

    @Nullable
    private static <T extends Keyed> Registry<T> getRegistry(@NotNull Class<T> type) {
        RegistryLookup lookup = registryLookup.aquire(() -> {
            RegistryLookup bukkit;
            try {
                bukkit = Bukkit::getRegistry;
            } catch (Throwable ignored) {
                bukkit = null;
            }
            return new DefaultRegistryLookup(bukkit);
        });
        return lookup.find(type);
    }

    private interface RegistryLookup {
        @Nullable
        <T extends Keyed> Registry<T> find(@NonNull Class<T> type);
    }

    private static class DefaultRegistryLookup implements RegistryLookup {
        private final RegistryLookup bukkit;
        private final Map<Type, Object> registries;

        private DefaultRegistryLookup(RegistryLookup bukkit) {
            this.bukkit = bukkit;
            registries = Arrays.stream(Registry.class.getDeclaredFields())
                    .filter(field -> Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers()))
                    .filter(field -> Registry.class.isAssignableFrom(field.getType()))
                    .map(field -> {
                        var type = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
                        try {
                            return new Pair<>(type, field.get(null));
                        } catch (Throwable e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(Pair::getA, Pair::getB, (a, b) -> a));
        }

        @Nullable
        @Override
        public <T extends Keyed> Registry<T> find(@NonNull Class<T> type) {
            if (bukkit == null) return (Registry<T>) registries.get(type);
            try {
                return bukkit.find(type);
            } catch (Throwable e) {
                return (Registry<T>) registries.get(type);
            }
        }
    }
}
