package com.volmit.iris.util.misc;

public class E {

    public static <T extends Enum<T>> T getOrDefault(Class<T> enumClass, String name, String fallback) {
        try {
            return Enum.valueOf(enumClass, name);
        } catch (Throwable e) {
            return Enum.valueOf(enumClass, fallback);
        }
    }
}
