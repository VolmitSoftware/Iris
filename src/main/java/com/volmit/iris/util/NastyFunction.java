package com.volmit.iris.util;

public interface NastyFunction<T, R> {
    R run(T t) throws Throwable;
}
