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

public class MathHelper {
    public static final float a = 3.1415927F;
    public static final float b = 1.5707964F;
    public static final float c = 6.2831855F;
    public static final float d = 0.017453292F;
    public static final float e = 57.295776F;
    public static final float f = 1.0E-5F;
    public static final float g = c(2.0F);
    private static final int h = 1024;
    private static final float i = 1024.0F;
    private static final long j = 61440L;
    private static final long k = 16384L;
    private static final long l = -4611686018427387904L;
    private static final long m = -9223372036854775808L;
    private static final float n = 10430.378F;
    private static final Random p = new Random();
    private static final int[] q = new int[]{0, 1, 28, 2, 29, 14, 24, 3, 30, 22, 20, 15, 25, 17, 4, 8, 31, 27, 13, 23, 21, 19, 16, 7, 26, 12, 18, 6, 11, 5, 10, 9};
    private static final double r = 0.16666666666666666D;
    private static final int s = 8;
    private static final int t = 257;
    private static final double u = Double.longBitsToDouble(4805340802404319232L);
    private static final double[] v = new double[257];
    private static final double[] w = new double[257];

    static {
        for (int var0 = 0; var0 < 257; ++var0) {
            double var1 = (double) var0 / 256.0D;
            double var3 = Math.asin(var1);
            w[var0] = Math.cos(var3);
            v[var0] = var3;
        }

    }

    public MathHelper() {
    }

    public static float c(float var0) {
        return (float) Math.sqrt(var0);
    }

    public static int d(float var0) {
        int var1 = (int) var0;
        return var0 < (float) var1 ? var1 - 1 : var1;
    }

    public static int a(double var0) {
        return (int) (var0 + 1024.0D) - 1024;
    }

    public static int floor(double var0) {
        int var2 = (int) var0;
        return var0 < (double) var2 ? var2 - 1 : var2;
    }

    public static long c(double var0) {
        long var2 = (long) var0;
        return var0 < (double) var2 ? var2 - 1L : var2;
    }

    public static int d(double var0) {
        return (int) (var0 >= 0.0D ? var0 : -var0 + 1.0D);
    }

    public static float e(float var0) {
        return Math.abs(var0);
    }

    public static int a(int var0) {
        return Math.abs(var0);
    }

    public static int f(float var0) {
        int var1 = (int) var0;
        return var0 > (float) var1 ? var1 + 1 : var1;
    }

    public static int e(double var0) {
        int var2 = (int) var0;
        return var0 > (double) var2 ? var2 + 1 : var2;
    }

    public static byte a(byte var0, byte var1, byte var2) {
        if (var0 < var1) {
            return var1;
        } else {
            return var0 > var2 ? var2 : var0;
        }
    }

    public static int clamp(int var0, int var1, int var2) {
        if (var0 < var1) {
            return var1;
        } else {
            return Math.min(var0, var2);
        }
    }

    public static long a(long var0, long var2, long var4) {
        if (var0 < var2) {
            return var2;
        } else {
            return Math.min(var0, var4);
        }
    }

    public static float a(float var0, float var1, float var2) {
        if (var0 < var1) {
            return var1;
        } else {
            return Math.min(var0, var2);
        }
    }

    public static double a(double var0, double var2, double var4) {
        if (var0 < var2) {
            return var2;
        } else {
            return Math.min(var0, var4);
        }
    }

    public static double b(double var0, double var2, double var4) {
        if (var4 < 0.0D) {
            return var0;
        } else {
            return var4 > 1.0D ? var2 : d(var4, var0, var2);
        }
    }

    public static float b(float var0, float var1, float var2) {
        if (var2 < 0.0F) {
            return var0;
        } else {
            return var2 > 1.0F ? var1 : h(var2, var0, var1);
        }
    }

    public static double a(double var0, double var2) {
        if (var0 < 0.0D) {
            var0 = -var0;
        }

        if (var2 < 0.0D) {
            var2 = -var2;
        }

        return Math.max(var0, var2);
    }

    public static int a(int var0, int var1) {
        return Math.floorDiv(var0, var1);
    }

    public static int nextInt(Random var0, int var1, int var2) {
        return var1 >= var2 ? var1 : var0.nextInt(var2 - var1 + 1) + var1;
    }

    public static float a(Random var0, float var1, float var2) {
        return var1 >= var2 ? var1 : var0.nextFloat() * (var2 - var1) + var1;
    }

    public static double a(Random var0, double var1, double var3) {
        return var1 >= var3 ? var1 : var0.nextDouble() * (var3 - var1) + var1;
    }

    public static double a(long[] var0) {
        long var1 = 0L;
        long[] var3 = var0;
        int var4 = var0.length;

        for (int var5 = 0; var5 < var4; ++var5) {
            long var6 = var3[var5];
            var1 += var6;
        }

        return (double) var1 / (double) var0.length;
    }

    public static boolean a(float var0, float var1) {
        return Math.abs(var1 - var0) < 1.0E-5F;
    }

    public static boolean b(double var0, double var2) {
        return Math.abs(var2 - var0) < 9.999999747378752E-6D;
    }

    public static int b(int var0, int var1) {
        return Math.floorMod(var0, var1);
    }

    public static float b(float var0, float var1) {
        return (var0 % var1 + var1) % var1;
    }

    public static double c(double var0, double var2) {
        return (var0 % var2 + var2) % var2;
    }

    public static int b(int var0) {
        int var1 = var0 % 360;
        if (var1 >= 180) {
            var1 -= 360;
        }

        if (var1 < -180) {
            var1 += 360;
        }

        return var1;
    }

    public static float g(float var0) {
        float var1 = var0 % 360.0F;
        if (var1 >= 180.0F) {
            var1 -= 360.0F;
        }

        if (var1 < -180.0F) {
            var1 += 360.0F;
        }

        return var1;
    }

    public static double f(double var0) {
        double var2 = var0 % 360.0D;
        if (var2 >= 180.0D) {
            var2 -= 360.0D;
        }

        if (var2 < -180.0D) {
            var2 += 360.0D;
        }

        return var2;
    }

    public static float c(float var0, float var1) {
        return g(var1 - var0);
    }

    public static float d(float var0, float var1) {
        return e(c(var0, var1));
    }

    public static float c(float var0, float var1, float var2) {
        float var3 = c(var0, var1);
        float var4 = a(var3, -var2, var2);
        return var1 - var4;
    }

    public static float d(float var0, float var1, float var2) {
        var2 = e(var2);
        return var0 < var1 ? a(var0 + var2, var0, var1) : a(var0 - var2, var1, var0);
    }

    public static float e(float var0, float var1, float var2) {
        float var3 = c(var0, var1);
        return d(var0, var0 + var3, var2);
    }

    public static double a(String var0, double var1) {
        try {
            return Double.parseDouble(var0);
        } catch (Throwable var4) {
            return var1;
        }
    }

    public static double a(String var0, double var1, double var3) {
        return Math.max(var3, a(var0, var1));
    }

    public static int c(int var0) {
        int var1 = var0 - 1;
        var1 |= var1 >> 1;
        var1 |= var1 >> 2;
        var1 |= var1 >> 4;
        var1 |= var1 >> 8;
        var1 |= var1 >> 16;
        return var1 + 1;
    }

    public static boolean d(int var0) {
        return var0 != 0 && (var0 & var0 - 1) == 0;
    }

    public static int e(int var0) {
        var0 = d(var0) ? var0 : c(var0);
        return q[(int) ((long) var0 * 125613361L >> 27) & 31];
    }

    public static int f(int var0) {
        return e(var0) - (d(var0) ? 0 : 1);
    }

    public static int f(float var0, float var1, float var2) {
        return b(d(var0 * 255.0F), d(var1 * 255.0F), d(var2 * 255.0F));
    }

    public static int b(int var0, int var1, int var2) {
        int var3 = (var0 << 8) + var1;
        var3 = (var3 << 8) + var2;
        return var3;
    }

    public static int c(int var0, int var1) {
        int var2 = (var0 & 16711680) >> 16;
        int var3 = (var1 & 16711680) >> 16;
        int var4 = (var0 & '\uff00') >> 8;
        int var5 = (var1 & '\uff00') >> 8;
        int var6 = (var0 & 255);
        int var7 = (var1 & 255);
        int var8 = (int) ((float) var2 * (float) var3 / 255.0F);
        int var9 = (int) ((float) var4 * (float) var5 / 255.0F);
        int var10 = (int) ((float) var6 * (float) var7 / 255.0F);
        return var0 & -16777216 | var8 << 16 | var9 << 8 | var10;
    }

    public static int a(int var0, float var1, float var2, float var3) {
        int var4 = (var0 & 16711680) >> 16;
        int var5 = (var0 & '\uff00') >> 8;
        int var6 = (var0 & 255);
        int var7 = (int) ((float) var4 * var1);
        int var8 = (int) ((float) var5 * var2);
        int var9 = (int) ((float) var6 * var3);
        return var0 & -16777216 | var7 << 16 | var8 << 8 | var9;
    }

    public static float h(float var0) {
        return var0 - (float) d(var0);
    }

    public static double g(double var0) {
        return var0 - (double) c(var0);
    }

    public static long c(int var0, int var1, int var2) {
        long var3 = (long) (var0 * 3129871) ^ (long) var2 * 116129781L ^ (long) var1;
        var3 = var3 * var3 * 42317861L + var3 * 11L;
        return var3 >> 16;
    }

    public static UUID a(Random var0) {
        long var1 = var0.nextLong() & -61441L | 16384L;
        long var3 = var0.nextLong() & 4611686018427387903L | -9223372036854775808L;
        return new UUID(var1, var3);
    }

    public static UUID a() {
        return a(p);
    }

    public static double c(double var0, double var2, double var4) {
        return (var0 - var2) / (var4 - var2);
    }

    public static double d(double var0, double var2) {
        double var4 = var2 * var2 + var0 * var0;
        if (Double.isNaN(var4)) {
            return 0.0D;
        } else {
            boolean var6 = var0 < 0.0D;
            if (var6) {
                var0 = -var0;
            }

            boolean var7 = var2 < 0.0D;
            if (var7) {
                var2 = -var2;
            }

            boolean var8 = var0 > var2;
            double var9;
            if (var8) {
                var9 = var2;
                var2 = var0;
                var0 = var9;
            }

            var9 = h(var4);
            var2 *= var9;
            var0 *= var9;
            double var11 = u + var0;
            int var13 = (int) Double.doubleToRawLongBits(var11);
            double var14 = v[var13];
            double var16 = w[var13];
            double var18 = var11 - u;
            double var20 = var0 * var16 - var2 * var18;
            double var22 = (6.0D + var20 * var20) * var20 * 0.16666666666666666D;
            double var24 = var14 + var22;
            if (var8) {
                var24 = 1.5707963267948966D - var24;
            }

            if (var7) {
                var24 = 3.141592653589793D - var24;
            }

            if (var6) {
                var24 = -var24;
            }

            return var24;
        }
    }

    public static float i(float var0) {
        float var1 = 0.5F * var0;
        int var2 = Float.floatToIntBits(var0);
        var2 = 1597463007 - (var2 >> 1);
        var0 = Float.intBitsToFloat(var2);
        var0 *= 1.5F - var1 * var0 * var0;
        return var0;
    }

    public static double h(double var0) {
        double var2 = 0.5D * var0;
        long var4 = Double.doubleToRawLongBits(var0);
        var4 = 6910469410427058090L - (var4 >> 1);
        var0 = Double.longBitsToDouble(var4);
        var0 *= 1.5D - var2 * var0 * var0;
        return var0;
    }

    public static float j(float var0) {
        int var1 = Float.floatToIntBits(var0);
        var1 = 1419967116 - var1 / 3;
        float var2 = Float.intBitsToFloat(var1);
        var2 = 0.6666667F * var2 + 1.0F / (3.0F * var2 * var2 * var0);
        var2 = 0.6666667F * var2 + 1.0F / (3.0F * var2 * var2 * var0);
        return var2;
    }

    public static int g(float var0, float var1, float var2) {
        int var3 = (int) (var0 * 6.0F) % 6;
        float var4 = var0 * 6.0F - (float) var3;
        float var5 = var2 * (1.0F - var1);
        float var6 = var2 * (1.0F - var4 * var1);
        float var7 = var2 * (1.0F - (1.0F - var4) * var1);
        float var8;
        float var9;
        float var10;
        switch (var3) {
            case 0:
                var8 = var2;
                var9 = var7;
                var10 = var5;
                break;
            case 1:
                var8 = var6;
                var9 = var2;
                var10 = var5;
                break;
            case 2:
                var8 = var5;
                var9 = var2;
                var10 = var7;
                break;
            case 3:
                var8 = var5;
                var9 = var6;
                var10 = var2;
                break;
            case 4:
                var8 = var7;
                var9 = var5;
                var10 = var2;
                break;
            case 5:
                var8 = var2;
                var9 = var5;
                var10 = var6;
                break;
            default:
                throw new RuntimeException("Something went wrong when converting from HSV to RGB. Input was " + var0 + ", " + var1 + ", " + var2);
        }

        int var11 = clamp((int) (var8 * 255.0F), 0, 255);
        int var12 = clamp((int) (var9 * 255.0F), 0, 255);
        int var13 = clamp((int) (var10 * 255.0F), 0, 255);
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

    public static long a(long var0) {
        var0 ^= var0 >>> 33;
        var0 *= -49064778989728563L;
        var0 ^= var0 >>> 33;
        var0 *= -4265267296055464877L;
        var0 ^= var0 >>> 33;
        return var0;
    }

    public static double[] a(double... var0) {
        float var1 = 0.0F;
        double[] var2f = var0;
        int var3 = var0.length;

        for (int var4 = 0; var4 < var3; ++var4) {
            double var5 = var2f[var4];
            var1 = (float) ((double) var1 + var5);
        }

        int var2;
        for (var2 = 0; var2 < var0.length; ++var2) {
            var0[var2] /= var1;
        }

        for (var2 = 0; var2 < var0.length; ++var2) {
            var0[var2] += var2 == 0 ? 0.0D : var0[var2 - 1];
        }

        return var0;
    }

    public static int a(Random var0, double[] var1) {
        double var2 = var0.nextDouble();

        for (int var4 = 0; var4 < var1.length; ++var4) {
            if (var2 < var1[var4]) {
                return var4;
            }
        }

        return var1.length;
    }

    public static double[] a(double var0, double var2, double var4, int var6, int var7) {
        double[] var8 = new double[var7 - var6 + 1];
        int var9 = 0;

        for (int var10 = var6; var10 <= var7; ++var10) {
            var8[var9] = Math.max(0.0D, var0 * StrictMath.exp(-((double) var10 - var4) * ((double) var10 - var4) / (2.0D * var2 * var2)));
            ++var9;
        }

        return var8;
    }

    public static double[] a(double var0, double var2, double var4, double var6, double var8, double var10, int var12, int var13) {
        double[] var14 = new double[var13 - var12 + 1];
        int var15 = 0;

        for (int var16 = var12; var16 <= var13; ++var16) {
            var14[var15] = Math.max(0.0D, var0 * StrictMath.exp(-((double) var16 - var4) * ((double) var16 - var4) / (2.0D * var2 * var2)) + var6 * StrictMath.exp(-((double) var16 - var10) * ((double) var16 - var10) / (2.0D * var8 * var8)));
            ++var15;
        }

        return var14;
    }

    public static double[] a(double var0, double var2, int var4, int var5) {
        double[] var6 = new double[var5 - var4 + 1];
        int var7 = 0;

        for (int var8 = var4; var8 <= var5; ++var8) {
            var6[var7] = Math.max(var0 * StrictMath.log(var8) + var2, 0.0D);
            ++var7;
        }

        return var6;
    }

    public static int a(int var0, int var1, IntPredicate var2) {
        int var3 = var1 - var0;

        while (var3 > 0) {
            int var4 = var3 / 2;
            int var5 = var0 + var4;
            if (var2.test(var5)) {
                var3 = var4;
            } else {
                var0 = var5 + 1;
                var3 -= var4 + 1;
            }
        }

        return var0;
    }

    public static float h(float var0, float var1, float var2) {
        return var1 + var0 * (var2 - var1);
    }

    public static double d(double var0, double var2, double var4) {
        return var2 + var0 * (var4 - var2);
    }

    public static double a(double var0, double var2, double var4, double var6, double var8, double var10) {
        return d(var2, d(var0, var4, var6), d(var0, var8, var10));
    }

    public static double a(double var0, double var2, double var4, double var6, double var8, double var10, double var12, double var14, double var16, double var18, double var20) {
        return d(var4, a(var0, var2, var6, var8, var10, var12), a(var0, var2, var14, var16, var18, var20));
    }

    public static double i(double var0) {
        return var0 * var0 * var0 * (var0 * (var0 * 6.0D - 15.0D) + 10.0D);
    }

    public static double j(double var0) {
        return 30.0D * var0 * var0 * (var0 - 1.0D) * (var0 - 1.0D);
    }

    public static int k(double var0) {
        if (var0 == 0.0D) {
            return 0;
        } else {
            return var0 > 0.0D ? 1 : -1;
        }
    }

    public static float i(float var0, float var1, float var2) {
        return var1 + var0 * g(var2 - var1);
    }

    public static float j(float var0, float var1, float var2) {
        return Math.min(var0 * var0 * 0.6F + var1 * var1 * ((3.0F + var1) / 4.0F) + var2 * var2 * 0.8F, 1.0F);
    }

    @Deprecated
    public static float k(float var0, float var1, float var2) {
        float var3;
        for (var3 = var1 - var0; var3 < -180.0F; var3 += 360.0F) {
        }

        while (var3 >= 180.0F) {
            var3 -= 360.0F;
        }

        return var0 + var2 * var3;
    }

    @Deprecated
    public static float l(double var0) {
        while (var0 >= 180.0D) {
            var0 -= 360.0D;
        }

        while (var0 < -180.0D) {
            var0 += 360.0D;
        }

        return (float) var0;
    }

    public static float e(float var0, float var1) {
        return (Math.abs(var0 % var1 - var1 * 0.5F) - var1 * 0.25F) / (var1 * 0.25F);
    }

    public static float k(float var0) {
        return var0 * var0;
    }

    public static double m(double var0) {
        return var0 * var0;
    }

    public static int h(int var0) {
        return var0 * var0;
    }

    public static double a(double var0, double var2, double var4, double var6, double var8) {
        return b(var6, var8, c(var0, var2, var4));
    }

    public static double b(double var0, double var2, double var4, double var6, double var8) {
        return d(c(var0, var2, var4), var6, var8);
    }

    public static double n(double var0) {
        return var0 + (2.0D * (new Random(floor(var0 * 3000.0D))).nextDouble() - 1.0D) * 1.0E-7D / 2.0D;
    }

    public static int d(int var0, int var1) {
        return (var0 + var1 - 1) / var1 * var1;
    }

    public static int b(Random var0, int var1, int var2) {
        return var0.nextInt(var2 - var1 + 1) + var1;
    }

    public static float b(Random var0, float var1, float var2) {
        return var0.nextFloat() * (var2 - var1) + var1;
    }

    public static float c(Random var0, float var1, float var2) {
        return var1 + (float) var0.nextGaussian() * var2;
    }

    public static double a(int var0, double var1, int var3) {
        return Math.sqrt((double) (var0 * var0) + var1 * var1 + (double) (var3 * var3));
    }
}