package com.volmit.iris.util;

import lombok.Data;

import java.util.Objects;

@Data
public class BlockPosition {
    private int x;
    private int y;
    private int z;

    //Magic numbers
    private static final int m1 = 1 + MathHelper.f(MathHelper.c(30000000));
    private static final int m2 = 64 - (m1 * 2);
    private static final long m3 = m1 + m2;
    private static final long m4 = (1L << m1) - 1L;
    private static final long m5 = (1L << m2) - 1L;
    private static final long m6 = (1L << m1) - 1L;


    public BlockPosition(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }

    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }

        if (o instanceof BlockPosition) {
            BlockPosition ot = (BlockPosition) o;

            return ot.x == x && ot.y == y && ot.z == z;
        }

        return false;
    }

    public int getChunkX() {
        return x >> 4;
    }

    public int getChunkZ() {
        return z >> 4;
    }

    public boolean is(int x, int z) {
        return this.x == x && this.z == z;
    }

    public boolean is(int x, int y, int z) {
        return this.x == x && this.y == y && this.z == z;
    }

    public long asLong() {
        return toLong(getX(), getY(), getZ());
    }

    public static long toLong(int x, int y, int z) {
        long var3 = 0L;
        var3 |= (x & m4) << m3;
        var3 |= (y & m5) << 0L;
        var3 |= (z & m6) << m2;
        return var3;
    }
}
