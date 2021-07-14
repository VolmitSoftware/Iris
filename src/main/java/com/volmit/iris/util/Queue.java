package com.volmit.iris.util;

public interface Queue<T> {
    Queue<T> queue(T t);

    Queue<T> queue(KList<T> t);

    boolean hasNext(int amt);

    boolean hasNext();

    T next();

    KList<T> next(int amt);

    Queue<T> clear();

    int size();

    static <T> Queue<T> create(KList<T> t) {
        return new ShurikenQueue<T>().queue(t);
    }

    @SuppressWarnings("unchecked")
    static <T> Queue<T> create(T... t) {
        return new ShurikenQueue<T>().queue(new KList<T>().add(t));
    }

    boolean contains(T p);
}
