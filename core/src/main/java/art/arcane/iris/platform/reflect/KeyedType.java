package art.arcane.iris.platform.reflect;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import art.arcane.iris.platform.bukkit.registry.RegistryTypeAdapter;
import art.arcane.iris.platform.bukkit.registry.RegistryUtil;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

public class KeyedType {
    private static final boolean KEYED_LENIENT = Boolean.getBoolean("iris.keyed-lenient");
    private static final boolean KEYED_PRESENT = detectKeyed();

    private static boolean detectKeyed() {
        try {
            Class.forName("org.bukkit.Keyed", false, KeyedType.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static String[] values(Class<?> type) {
        if (!isKeyed(type)) return new String[0];
        return RegistryUtil.lookup(type)
                        .map()
                        .keySet()
                        .stream()
                        .map(Object::toString)
                        .toArray(String[]::new);
    }

    public static boolean isKeyed(Class<?> type) {
        if (!KEYED_PRESENT) return false;
        if (KEYED_LENIENT) return !RegistryUtil.lookup(type).isEmpty();
        return Keyed.class.isAssignableFrom(type);
    }

    @SuppressWarnings("unchecked")
    public static <T> TypeAdapter<T> createTypeAdapter(Gson gson, TypeToken<T> type) {
        if (!isKeyed(type.getRawType())) return null;
        return (TypeAdapter<T>) RegistryTypeAdapter.of(type.getRawType());
    }

    @Nullable
    public static NamespacedKey getKey(Object value) {
        // KEYED_PRESENT first: without it the instanceof below resolves org.bukkit.Keyed and
        // NoClassDefFoundErrors on the modded loaders instead of degrading to null.
        if (!KEYED_PRESENT || value == null) {
            return null;
        }

        if (value instanceof Keyed keyed) {
            NamespacedKey key = keyed.getKey();
            if (key != null) {
                return key;
            }
        }

        NamespacedKey keyOrThrow = invokeKey(value, "getKeyOrThrow");
        if (keyOrThrow != null) {
            return keyOrThrow;
        }

        return invokeKey(value, "getKey");
    }

    @Nullable
    private static NamespacedKey invokeKey(Object value, String methodName) {
        try {
            Method method = value.getClass().getMethod(methodName);
            Object result = method.invoke(value);
            if (result instanceof NamespacedKey namespacedKey) {
                return namespacedKey;
            }
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
