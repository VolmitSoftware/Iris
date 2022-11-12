/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.volmit.iris.util.math;

import java.util.Random;
import java.util.UUID;
import java.util.function.IntPredicate;

public class IrisMathHelper {
    public static final float a;
    private static final Random c;
    private static final int[] d;
    private static final double e;
    private static final double[] f;
    private static final double[] g;

    static {
        a = c(2.0f);
        c = new Random();
        d = new int[]{0, 1, 28, 2, 29, 14, 24, 3, 30, 22, 20, 15, 25, 17, 4, 8, 31, 27, 13, 23, 21, 19, 16, 7, 26, 12, 18, 6, 11, 5, 10, 9};
        e = Double.longBitsToDouble(4805340802404319232L);
        f = new double[257];
        g = new double[257];
        for (int var2 = 0; var2 < 257; ++var2) {
            final double var3 = var2 / 256.0;
            final double var4 = Math.asin(var3);
            IrisMathHelper.g[var2] = Math.cos(var4);
            IrisMathHelper.f[var2] = var4;
        }
    }

    public static float c(final float var0) {
        return (float) Math.sqrt(var0);
    }

    public static float sqrt(final double var0) {
        return (float) Math.sqrt(var0);
    }

    public static int d(final float var0) {
        final int var = (int) var0;
        return (var0 < var) ? (var - 1) : var;
    }

    public static int floor(final double var0) {
        final int var = (int) var0;
        return (var0 < var) ? (var - 1) : var;
    }

    public static long d(final double var0) {
        final long var = (long) var0;
        return (var0 < var) ? (var - 1L) : var;
    }

    public static float e(final float var0) {
        return Math.abs(var0);
    }

    public static int a(final int var0) {
        return Math.abs(var0);
    }

    public static int f(final float var0) {
        final int var = (int) var0;
        return (var0 > var) ? (var + 1) : var;
    }

    public static int f(final double var0) {
        final int var = (int) var0;
        return (var0 > var) ? (var + 1) : var;
    }

    public static int clamp(final int var0, final int var1, final int var2) {
        if (var0 < var1) {
            return var1;
        }
        return Math.min(var0, var2);
    }

    public static float a(final float var0, final float var1, final float var2) {
        if (var0 < var1) {
            return var1;
        }
        return Math.min(var0, var2);
    }

    public static double a(final double var0, final double var2, final double var4) {
        if (var0 < var2) {
            return var2;
        }
        return Math.min(var0, var4);
    }

    public static double b(final double var0, final double var2, final double var4) {
        if (var4 < 0.0) {
            return var0;
        }
        if (var4 > 1.0) {
            return var2;
        }
        return d(var4, var0, var2);
    }

    public static double a(double var0, double var2) {
        if (var0 < 0.0) {
            var0 = -var0;
        }
        if (var2 < 0.0) {
            var2 = -var2;
        }
        return Math.max(var0, var2);
    }

    public static int a(final int var0, final int var1) {
        return Math.floorDiv(var0, var1);
    }

    public static int nextInt(final Random var0, final int var1, final int var2) {
        if (var1 >= var2) {
            return var1;
        }
        return var0.nextInt(var2 - var1 + 1) + var1;
    }

    public static float a(final Random var0, final float var1, final float var2) {
        if (var1 >= var2) {
            return var1;
        }
        return var0.nextFloat() * (var2 - var1) + var1;
    }

    public static double a(final Random var0, final double var1, final double var3) {
        if (var1 >= var3) {
            return var1;
        }
        return var0.nextDouble() * (var3 - var1) + var1;
    }

    public static double a(final long[] var0) {
        long var = 0L;
        for (final long var2 : var0) {
            var += var2;
        }
        return var / (double) var0.length;
    }

    public static boolean b(final double var0, final double var2) {
        return Math.abs(var2 - var0) < 9.999999747378752E-6;
    }

    public static int b(final int var0, final int var1) {
        return Math.floorMod(var0, var1);
    }

    public static float g(final float var0) {
        float var = var0 % 360.0f;
        if (var >= 180.0f) {
            var -= 360.0f;
        }
        if (var < -180.0f) {
            var += 360.0f;
        }
        return var;
    }

    public static double g(final double var0) {
        double var = var0 % 360.0;
        if (var >= 180.0) {
            var -= 360.0;
        }
        if (var < -180.0) {
            var += 360.0;
        }
        return var;
    }

    public static float c(final float var0, final float var1) {
        return g(var1 - var0);
    }

    public static float d(final float var0, final float var1) {
        return e(c(var0, var1));
    }

    public static float b(final float var0, final float var1, final float var2) {
        final float var3 = c(var0, var1);
        final float var4 = a(var3, -var2, var2);
        return var1 - var4;
    }

    public static float c(final float var0, final float var1, float var2) {
        var2 = e(var2);
        if (var0 < var1) {
            return a(var0 + var2, var0, var1);
        }
        return a(var0 - var2, var1, var0);
    }

    public static float d(final float var0, final float var1, final float var2) {
        final float var3 = c(var0, var1);
        return c(var0, var0 + var3, var2);
    }

    public static int c(final int var0) {
        int var = var0 - 1;
        var |= var >> 1;
        var |= var >> 2;
        var |= var >> 4;
        var |= var >> 8;
        var |= var >> 16;
        return var + 1;
    }

    public static boolean d(final int var0) {
        return var0 != 0 && (var0 & var0 - 1) == 0x0;
    }

    public static int e(int var0) {
        var0 = (d(var0) ? var0 : c(var0));
        return IrisMathHelper.d[(int) (var0 * 125613361L >> 27) & 0x1F];
    }

    public static int f(final int var0) {
        return e(var0) - (d(var0) ? 0 : 1);
    }

    public static int c(final int var0, int var1) {
        if (var1 == 0) {
            return 0;
        }
        if (var0 == 0) {
            return var1;
        }
        if (var0 < 0) {
            var1 *= -1;
        }
        final int var2 = var0 % var1;
        if (var2 == 0) {
            return var0;
        }
        return var0 + var1 - var2;
    }

    public static float h(final float var0) {
        return var0 - d(var0);
    }

    public static double h(final double var0) {
        return var0 - d(var0);
    }

    public static long c(final int var0, final int var1, final int var2) {
        long var3 = (var0 * 3129871L) ^ var2 * 116129781L ^ (long) var1;
        var3 = var3 * var3 * 42317861L + var3 * 11L;
        return var3 >> 16;
    }

    public static UUID a(final Random var0) {
        final long var = (var0.nextLong() & 0xFFFFFFFFFFFF0FFFL) | 0x4000L;
        final long var2 = (var0.nextLong() & 0x3FFFFFFFFFFFFFFFL) | Long.MIN_VALUE;
        return new UUID(var, var2);
    }

    public static UUID a() {
        return a(IrisMathHelper.c);
    }

    public static double c(final double var0, final double var2, final double var4) {
        return (var0 - var2) / (var4 - var2);
    }

    public static double d(double var0, double var2) {
        final double var3 = var2 * var2 + var0 * var0;
        if (Double.isNaN(var3)) {
            return Double.NaN;
        }
        final boolean var4 = var0 < 0.0;
        if (var4) {
            var0 = -var0;
        }
        final boolean var5 = var2 < 0.0;
        if (var5) {
            var2 = -var2;
        }
        final boolean var6 = var0 > var2;
        if (var6) {
            final double var7 = var2;
            var2 = var0;
            var0 = var7;
        }
        final double var7 = i(var3);
        var2 *= var7;
        var0 *= var7;
        final double var8 = IrisMathHelper.e + var0;
        final int var9 = (int) Double.doubleToRawLongBits(var8);
        final double var10 = IrisMathHelper.f[var9];
        final double var11 = IrisMathHelper.g[var9];
        final double var12 = var8 - IrisMathHelper.e;
        final double var13 = var0 * var11 - var2 * var12;
        final double var14 = (6.0 + var13 * var13) * var13 * 0.16666666666666666;
        double var15 = var10 + var14;
        if (var6) {
            var15 = 1.5707963267948966 - var15;
        }
        if (var5) {
            var15 = 3.141592653589793 - var15;
        }
        if (var4) {
            var15 = -var15;
        }
        return var15;
    }

    public static double i(double var0) {
        final double var = 0.5 * var0;
        long var2 = Double.doubleToRawLongBits(var0);
        var2 = 6910469410427058090L - (var2 >> 1);
        var0 = Double.longBitsToDouble(var2);
        var0 *= 1.5 - var * var0 * var0;
        return var0;
    }

    public static int f(final float var0, final float var1, final float var2) {
        final int var3 = (int) (var0 * 6.0f) % 6;
        final float var4 = var0 * 6.0f - var3;
        final float var5 = var2 * (1.0f - var1);
        final float var6 = var2 * (1.0f - var4 * var1);
        final float var7 = var2 * (1.0f - (1.0f - var4) * var1);
        float var8 = 0.0f;
        float var9 = 0.0f;
        float var10 = 0.0f;
        switch (var3) {
            case 0 -> {
                var8 = var2;
                var9 = var7;
                var10 = var5;
            }
            case 1 -> {
                var8 = var6;
                var9 = var2;
                var10 = var5;
            }
            case 2 -> {
                var8 = var5;
                var9 = var2;
                var10 = var7;
            }
            case 3 -> {
                var8 = var5;
                var9 = var6;
                var10 = var2;
            }
            case 4 -> {
                var8 = var7;
                var9 = var5;
                var10 = var2;
            }
            case 5 -> {
                var8 = var2;
                var9 = var5;
                var10 = var6;
            }
            default ->
                    throw new RuntimeException("Something went wrong when converting from HSV to RGB. Input was " + var0 + ", " + var1 + ", " + var2);
        }
        final int var11 = clamp((int) (var8 * 255.0f), 0, 255);
        final int var12 = clamp((int) (var9 * 255.0f), 0, 255);
        final int var13 = clamp((int) (var10 * 255.0f), 0, 255);
        return var11 << 16 | var12 << 8 | var13;
    }

    public static int g(int var0) {
        var0 ^= var0 >>> 16;
        var0 *= -2048144789;
        var0 ^= var0 >>> 13;
        var0 *= -1028477387;
        var0 ^= var0 >>> 16;
        return var0;
    }

    public static int a(int var0, final int var1, final IntPredicate var2) {
        int var3 = var1 - var0;
        while (var3 > 0) {
            final int var4 = var3 / 2;
            final int var5 = var0 + var4;
            if (var2.test(var5)) {
                var3 = var4;
            } else {
                var0 = var5 + 1;
                var3 -= var4 + 1;
            }
        }
        return var0;
    }

    public static float g(final float var0, final float var1, final float var2) {
        return var1 + var0 * (var2 - var1);
    }

    public static double d(final double var0, final double var2, final double var4) {
        return var2 + var0 * (var4 - var2);
    }

    public static double a(final double var0, final double var2, final double var4, final double var6, final double var8, final double var10) {
        return d(var2, d(var0, var4, var6), d(var0, var8, var10));
    }

    public static double a(final double var0, final double var2, final double var4, final double var6, final double var8, final double var10, final double var12, final double var14, final double var16, final double var18, final double var20) {
        return d(var4, a(var0, var2, var6, var8, var10, var12), a(var0, var2, var14, var16, var18, var20));
    }

    public static double j(final double var0) {
        return var0 * var0 * var0 * (var0 * (var0 * 6.0 - 15.0) + 10.0);
    }

    public static int k(final double var0) {
        if (var0 == 0.0) {
            return 0;
        }
        return (var0 > 0.0) ? 1 : -1;
    }

    @Deprecated
    public static float j(final float var0, final float var1, final float var2) {
        float var3;
        for (var3 = var1 - var0; var3 < -180.0f; var3 += 360.0f) {
        }
        while (var3 >= 180.0f) {
            var3 -= 360.0f;
        }
        return var0 + var2 * var3;
    }

    public static float k(final float var0) {
        return var0 * var0;
    }
}