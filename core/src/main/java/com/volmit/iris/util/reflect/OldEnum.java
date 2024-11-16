package com.volmit.iris.util.reflect;

import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Objects;

public class OldEnum {

    private static final Class<?> oldEnum;
    private static final Method name;

    public static boolean exists() {
        return oldEnum != null;
    }

    public static boolean isOldEnum(Class<?> c) {
        return oldEnum != null && oldEnum.isAssignableFrom(c);
    }

    public static <T> T valueOf(Class<? extends T> c, String name) {
        try {
            return (T) c.getDeclaredField(name).get(null);
        } catch (Throwable e) {
            return null;
        }
    }

    public static String name(Object o) {
        try {
            return (String) name.invoke(o);
        } catch (Throwable e) {
            return null;
        }
    }

    public static Object[] values(Class<?> clazz) {
        if (!isOldEnum(clazz)) return new Object[0];
        return Arrays.stream(clazz.getDeclaredFields())
                .filter(f -> Modifier.isStatic(f.getModifiers()))
                .filter(f -> Modifier.isFinal(f.getModifiers()))
                .map(f -> {
                    try {
                        return f.get(null);
                    } catch (Throwable ignored) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toArray();
    }

    public static <T> TypeAdapter<T> create(Class<? extends T> type) {
        if (!isOldEnum(type))
            return null;

        return new TypeAdapter<>() {

            @Override
            public void write(JsonWriter out, T value) throws IOException {
                out.value(name(value));
            }

            @Override
            public T read(JsonReader in) throws IOException {
                return valueOf(type, in.nextString());
            }
        };
    }

    static {
        Class<?> clazz = null;
        Method method = null;
        try {
            clazz = Class.forName("org.bukkit.util.OldEnum");
            method = clazz.getDeclaredMethod("name");
        } catch (Throwable ignored) {}

        if (clazz == null || method == null) {
            oldEnum = null;
            name = null;
        } else {
            oldEnum = clazz;
            name = method;
        }
    }
}
