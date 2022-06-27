package com.volmit.iris.util;

public class FloatNoiseCache {
    private final int width;
    private final int height;
    private final float[] cache;

    public FloatNoiseCache(int width, int height)
    {
        this.width = width;
        this.height = height;
        cache = new float[width * height];
    }

    public void set(int x, int y, float v) {
        this.cache[y % this.height * this.width + x % this.width] = v;
    }

    public float get(int x, int y) {
        return this.cache[y % this.height * this.width + x % this.width];
    }
}
