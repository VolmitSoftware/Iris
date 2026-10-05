package com.volmit.example.chunk;

/** Per-chunk output: the first air y of each local column. */
public final class SurfaceHeights {
    private final int width;
    private final int[] heights;

    SurfaceHeights(int width, int depth) {
        this.width = width;
        heights = new int[width * depth];
    }

    void set(int x, int z, int height) {
        heights[z * width + x] = height;
    }

    public int at(int x, int z) {
        return heights[z * width + x];
    }
}
