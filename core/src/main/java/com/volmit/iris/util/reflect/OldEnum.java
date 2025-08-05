package com.volmit.iris.util.reflect;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Objects;

@Deprecated(since = "3.7.1")
public class OldEnum {

    private static final Class<?> oldEnum;
    private static final MethodHandle name;

    public static boolean exists() {
        return oldEnum != null;
    }

    public static boolean isOldEnum(Class<?> c) {
        return oldEnum != null && oldEnum.isAssignableFrom(c);
    }

    public static <T> T valueOf(Class<? extends T> c, String name) {
        return valueOf(c, name, name.replace(".", "_"));
    }

    public static <T> T valueOf(Class<? extends T> c, String... names) {
        for (final String name : names) {
            try {
                return (T) c.getDeclaredField(name).get(null);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    public static String name(Object o) {
        try {
            return (String) name.invoke(o);
        } catch (Throwable e) {
            return null;
        }
    }

    public static String[] values(Class<?> clazz) {
        if (!isOldEnum(clazz)) return new String[0];
        return Arrays.stream(clazz.getDeclaredFields())
                .filter(f -> Modifier.isStatic(f.getModifiers()))
                .filter(f -> Modifier.isFinal(f.getModifiers()))
                .map(f -> {
                    try {
                        return name(f.get(null));
                    } catch (Throwable ignored) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toArray(String[]::new);
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
        MethodHandle method = null;
        try {
            clazz = Class.forName("org.bukkit.util.OldEnum");
            method = MethodHandles.lookup().findVirtual(clazz, "name", MethodType.methodType(String.class));
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
