package com.volmit.iris.util;

@FunctionalInterface
public interface Observer<T> {
    void onChanged(T from, T to);
}
