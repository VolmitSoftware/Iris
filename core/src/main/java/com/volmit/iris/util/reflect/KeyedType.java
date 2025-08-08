package com.volmit.iris.util.reflect;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.volmit.iris.util.data.registry.RegistryTypeAdapter;
import com.volmit.iris.util.data.registry.RegistryUtil;
import org.bukkit.Keyed;

public class KeyedType {
    private static final boolean KEYED_ENABLED = Boolean.getBoolean("iris.keyed-types");
    private static final boolean KEYED_LENIENT = Boolean.getBoolean("iris.keyed-lenient");

    public static String[] values(Class<?> type) {
        if (!isKeyed(type)) return new String[0];
        if (!KEYED_ENABLED) return OldEnum.values(type);
        return RegistryUtil.lookup(type)
                        .map()
                        .keySet()
                        .stream()
                        .map(Object::toString)
                        .toArray(String[]::new);
    }

    public static boolean isKeyed(Class<?> type) {
        if (KEYED_ENABLED) {
            if (KEYED_LENIENT) return !RegistryUtil.lookup(type).isEmpty();
            else return Keyed.class.isAssignableFrom(type);
        } else return OldEnum.isOldEnum(type);
    }

    @SuppressWarnings("unchecked")
    public static <T> TypeAdapter<T> createTypeAdapter(Gson gson, TypeToken<T> type) {
        if (!isKeyed(type.getRawType())) return null;
        return (TypeAdapter<T>) (KEYED_ENABLED ? RegistryTypeAdapter.of(type.getRawType()) : OldEnum.create(type.getRawType()));
    }
}
