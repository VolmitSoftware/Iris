package com.volmit.iris.util.cache;

import com.volmit.iris.util.function.Function2;

import java.util.concurrent.atomic.AtomicReferenceArray;

public class ChunkCache2D<T> {
    private final AtomicReferenceArray<T> cache;

    public ChunkCache2D() {
        this.cache = new AtomicReferenceArray<>(256);
    }

    public T get(int x, int z, Function2<Integer, Integer, T> resolver) {
        int key = ((z & 15) * 16) + (x & 15);
        T t = cache.get(key);

        if (t == null) {
            t = resolver.apply(x, z);
            cache.set(key, t);
        }

        return t;
    }
}
