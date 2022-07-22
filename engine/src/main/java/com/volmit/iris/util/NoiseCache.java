package com.volmit.iris.util;

public class NoiseCache<T> {
    private final int width;
    private final int height;
    private final Object[] cache;

    public NoiseCache(int width, int height)
    {
        this.width = width;
        this.height = height;
        cache = new Object[width * height];
    }

    public void set(int x, int y, T v) {
        this.cache[y % this.height * this.width + x % this.width] = v;
    }

    @SuppressWarnings("unchecked")
    public T get(int x, int y) {
        return (T) this.cache[y % this.height * this.width + x % this.width];
    }
}
