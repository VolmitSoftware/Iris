package com.volmit.iris.util;

public interface Observable<T> {
    T get();

    Observable<T> set(T t);

    boolean has();

    Observable<T> clearObservers();

    Observable<T> observe(Observer<T> t);
}
