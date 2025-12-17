package com.volmit.iris.util.cache;

import com.volmit.iris.util.function.Function2;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class ChunkCache2D<T> {
    private static final boolean FAST = Boolean.getBoolean("iris.cache.fast");
    private static final boolean DYNAMIC = Boolean.getBoolean("iris.cache.dynamic");
    private static final VarHandle AA = MethodHandles.arrayElementVarHandle(Entry[].class);

    private final Entry<T>[] cache;

    @SuppressWarnings({"unchecked"})
    public ChunkCache2D() {
        this.cache = new Entry[256];
        if (DYNAMIC) return;
        for (int i = 0; i < cache.length; i++) {
            cache[i] = FAST ? new FastEntry<>() : new Entry<>();
        }
    }

    @SuppressWarnings({"unchecked"})
    public T get(int x, int z, Function2<Integer, Integer, T> resolver) {
        int key = ((z & 15) * 16) + (x & 15);
        var entry = cache[key];
        if (entry == null) {
            entry = FAST ? new FastEntry<>() : new Entry<>();
            if (!AA.compareAndSet(cache, key, null, entry)) {
                entry = (Entry<T>) AA.getVolatile(cache, key);
            }
        }
        return entry.compute(x, z, resolver);
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
