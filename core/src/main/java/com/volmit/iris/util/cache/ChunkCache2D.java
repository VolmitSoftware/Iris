package com.volmit.iris.util.cache;

import com.volmit.iris.util.function.Function2;

public class ChunkCache2D<T> {
    private static final boolean FAST = Boolean.getBoolean("iris.cache.fast");

    private final Entry<T>[] cache;

    @SuppressWarnings({"unchecked"})
    public ChunkCache2D() {
        this.cache = new Entry[256];
        for (int i = 0; i < cache.length; i++) {
            cache[i] = FAST ? new FastEntry<>() : new Entry<>();
        }
    }

    public T get(int x, int z, Function2<Integer, Integer, T> resolver) {
        int key = ((z & 15) * 16) + (x & 15);
        return cache[key].compute(x, z, resolver);
    }

    private static class Entry<T> {
        protected volatile T t;

        protected T compute(int x, int z, Function2<Integer, Integer, T> resolver) {
            if (t != null) return t;
            synchronized (this) {
                if (t == null) t = resolver.apply(x, z);
                return t;
            }
        }
    }

    private static class FastEntry<T> extends Entry<T> {
        @Override
        protected T compute(int x, int z, Function2<Integer, Integer, T> resolver) {
            if (t != null) return t;
            return t = resolver.apply(x, z);
        }
    }
}
