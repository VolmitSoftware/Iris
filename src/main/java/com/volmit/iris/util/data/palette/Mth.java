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

package com.volmit.iris.util.data.palette;

import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Mth {
    public static final float PI = 3.1415927F;
    public static final float HALF_PI = 1.5707964F;
    public static final float TWO_PI = 6.2831855F;
    public static final float DEG_TO_RAD = 0.017453292F;
    public static final float RAD_TO_DEG = 57.295776F;
    public static final float EPSILON = 1.0E-5F;
    public static final float SQRT_OF_TWO = sqrt(2.0F);
    private static final int BIG_ENOUGH_INT = 1024;
    private static final float BIG_ENOUGH_FLOAT = 1024.0F;
    private static final long UUID_VERSION = 61440L;
    private static final long UUID_VERSION_TYPE_4 = 16384L;
    private static final long UUID_VARIANT = -4611686018427387904L;
    private static final long UUID_VARIANT_2 = -9223372036854775808L;
    private static final float SIN_SCALE = 10430.378F;

    private static final float[] SIN;
    private static final Random RANDOM = new Random();
    private static final int[] MULTIPLY_DE_BRUIJN_BIT_POSITION = new int[]{
            0, 1, 28, 2, 29, 14, 24, 3, 30, 22,
            20, 15, 25, 17, 4, 8, 31, 27, 13, 23,
            21, 19, 16, 7, 26, 12, 18, 6, 11, 5,
            10, 9};
    private static final double ONE_SIXTH = 0.16666666666666666D;
    private static final int FRAC_EXP = 8;
    private static final int LUT_SIZE = 257;
    private static final double FRAC_BIAS = Double.longBitsToDouble(4805340802404319232L);
    private static final double[] ASIN_TAB = new double[257];
    private static final double[] COS_TAB = new double[257];

    static {
        SIN = make(new float[65536], var0 -> {
            for (int var1 = 0; var1 < var0.length; var1++)
                var0[var1] = (float) Math.sin(var1 * Math.PI * 2.0D / 65536.0D);
        });
    }

    static {
        for (int var0 = 0; var0 < 257; var0++) {
            double var1 = var0 / 256.0D;
            double var3 = Math.asin(var1);
            COS_TAB[var0] = Math.cos(var3);
            ASIN_TAB[var0] = var3;
        }
    }

    public static <T> T make(Supplier<T> var0) {
        return var0.get();
    }

    public static <T> T make(T var0, Consumer<T> var1) {
        var1.accept(var0);
        return var0;
    }

    public static float sin(float var0) {
        return SIN[(int) (var0 * 10430.378F) & 0xFFFF];
    }

    public static float cos(float var0) {
        return SIN[(int) (var0 * 10430.378F + 16384.0F) & 0xFFFF];
    }

    public static float sqrt(float var0) {
        return (float) Math.sqrt(var0);
    }

    public static int floor(float var0) {
        int var1 = (int) var0;
        return (var0 < var1) ? (var1 - 1) : var1;
    }

    public static int fastFloor(double var0) {
        return (int) (var0 + 1024.0D) - 1024;
    }

    public static int floor(double var0) {
        int var2 = (int) var0;
        return (var0 < var2) ? (var2 - 1) : var2;
    }

    public static long lfloor(double var0) {
        long var2 = (long) var0;
        return (var0 < var2) ? (var2 - 1L) : var2;
    }

    public static int absFloor(double var0) {
        return (int) ((var0 >= 0.0D) ? var0 : (-var0 + 1.0D));
    }

    public static float abs(float var0) {
        return Math.abs(var0);
    }

    public static int abs(int var0) {
        return Math.abs(var0);
    }

    public static int ceil(float var0) {
        int var1 = (int) var0;
        return (var0 > var1) ? (var1 + 1) : var1;
    }

    public static int ceil(double var0) {
        int var2 = (int) var0;
        return (var0 > var2) ? (var2 + 1) : var2;
    }

    public static byte clamp(byte var0, byte var1, byte var2) {
        if (var0 < var1)
            return var1;
        if (var0 > var2)
            return var2;
        return var0;
    }

    public static int clamp(int var0, int var1, int var2) {
        if (var0 < var1)
            return var1;
        if (var0 > var2)
            return var2;
        return var0;
    }

    public static long clamp(long var0, long var2, long var4) {
        if (var0 < var2)
            return var2;
        if (var0 > var4)
            return var4;
        return var0;
    }

    public static float clamp(float var0, float var1, float var2) {
        if (var0 < var1)
            return var1;
        if (var0 > var2)
            return var2;
        return var0;
    }

    public static double clamp(double var0, double var2, double var4) {
        if (var0 < var2)
            return var2;
        if (var0 > var4)
            return var4;
        return var0;
    }

    public static double clampedLerp(double var0, double var2, double var4) {
        if (var4 < 0.0D)
            return var0;
        if (var4 > 1.0D)
            return var2;
        return lerp(var4, var0, var2);
    }

    public static float clampedLerp(float var0, float var1, float var2) {
        if (var2 < 0.0F)
            return var0;
        if (var2 > 1.0F)
            return var1;
        return lerp(var2, var0, var1);
    }

    public static double absMax(double var0, double var2) {
        if (var0 < 0.0D)
            var0 = -var0;
        if (var2 < 0.0D)
            var2 = -var2;
        return (var0 > var2) ? var0 : var2;
    }

    public static int intFloorDiv(int var0, int var1) {
        return Math.floorDiv(var0, var1);
    }

    public static int nextInt(Random var0, int var1, int var2) {
        if (var1 >= var2)
            return var1;
        return var0.nextInt(var2 - var1 + 1) + var1;
    }

    public static float nextFloat(Random var0, float var1, float var2) {
        if (var1 >= var2)
            return var1;
        return var0.nextFloat() * (var2 - var1) + var1;
    }

    public static double nextDouble(Random var0, double var1, double var3) {
        if (var1 >= var3)
            return var1;
        return var0.nextDouble() * (var3 - var1) + var1;
    }

    public static double average(long[] var0) {
        long var1 = 0L;
        for (long var6 : var0)
            var1 += var6;
        return var1 / var0.length;
    }

    public static boolean equal(float var0, float var1) {
        return (Math.abs(var1 - var0) < 1.0E-5F);
    }

    public static boolean equal(double var0, double var2) {
        return (Math.abs(var2 - var0) < 9.999999747378752E-6D);
    }

    public static int positiveModulo(int var0, int var1) {
        return Math.floorMod(var0, var1);
    }

    public static float positiveModulo(float var0, float var1) {
        return (var0 % var1 + var1) % var1;
    }

    public static double positiveModulo(double var0, double var2) {
        return (var0 % var2 + var2) % var2;
    }

    public static int wrapDegrees(int var0) {
        int var1 = var0 % 360;
        if (var1 >= 180)
            var1 -= 360;
        if (var1 < -180)
            var1 += 360;
        return var1;
    }

    public static float wrapDegrees(float var0) {
        float var1 = var0 % 360.0F;
        if (var1 >= 180.0F)
            var1 -= 360.0F;
        if (var1 < -180.0F)
            var1 += 360.0F;
        return var1;
    }

    public static double wrapDegrees(double var0) {
        double var2 = var0 % 360.0D;
        if (var2 >= 180.0D)
            var2 -= 360.0D;
        if (var2 < -180.0D)
            var2 += 360.0D;
        return var2;
    }

    public static float degreesDifference(float var0, float var1) {
        return wrapDegrees(var1 - var0);
    }

    public static float degreesDifferenceAbs(float var0, float var1) {
        return abs(degreesDifference(var0, var1));
    }

    public static float rotateIfNecessary(float var0, float var1, float var2) {
        float var3 = degreesDifference(var0, var1);
        float var4 = clamp(var3, -var2, var2);
        return var1 - var4;
    }

    public static float approach(float var0, float var1, float var2) {
        var2 = abs(var2);
        if (var0 < var1)
            return clamp(var0 + var2, var0, var1);
        return clamp(var0 - var2, var1, var0);
    }

    public static float approachDegrees(float var0, float var1, float var2) {
        float var3 = degreesDifference(var0, var1);
        return approach(var0, var0 + var3, var2);
    }

    public static int getInt(String var0, int var1) {
        return Integer.valueOf(var0, var1);
    }

    public static int getInt(String var0, int var1, int var2) {
        return Math.max(var2, getInt(var0, var1));
    }

    public static double getDouble(String var0, double var1) {
        try {
            return Double.parseDouble(var0);
        } catch (Throwable var3) {
            return var1;
        }
    }

    public static double getDouble(String var0, double var1, double var3) {
        return Math.max(var3, getDouble(var0, var1));
    }

    public static int smallestEncompassingPowerOfTwo(int var0) {
        int var1 = var0 - 1;
        var1 |= var1 >> 1;
        var1 |= var1 >> 2;
        var1 |= var1 >> 4;
        var1 |= var1 >> 8;
        var1 |= var1 >> 16;
        return var1 + 1;
    }

    public static boolean isPowerOfTwo(int var0) {
        return (var0 != 0 && (var0 & var0 - 1) == 0);
    }

    public static int ceillog2(int var0) {
        var0 = isPowerOfTwo(var0) ? var0 : smallestEncompassingPowerOfTwo(var0);
        return MULTIPLY_DE_BRUIJN_BIT_POSITION[(int) (var0 * 125613361L >> 27L) & 0x1F];
    }

    public static int log2(int var0) {
        return ceillog2(var0) - (isPowerOfTwo(var0) ? 0 : 1);
    }

    public static int color(float var0, float var1, float var2) {
        return color(floor(var0 * 255.0F), floor(var1 * 255.0F), floor(var2 * 255.0F));
    }

    public static int color(int var0, int var1, int var2) {
        int var3 = var0;
        var3 = (var3 << 8) + var1;
        var3 = (var3 << 8) + var2;
        return var3;
    }

    public static int colorMultiply(int var0, int var1) {
        int var2 = (var0 & 0xFF0000) >> 16;
        int var3 = (var1 & 0xFF0000) >> 16;
        int var4 = (var0 & 0xFF00) >> 8;
        int var5 = (var1 & 0xFF00) >> 8;
        int var6 = (var0 & 0xFF);
        int var7 = (var1 & 0xFF);
        int var8 = (int) (var2 * var3 / 255.0F);
        int var9 = (int) (var4 * var5 / 255.0F);
        int var10 = (int) (var6 * var7 / 255.0F);
        return var0 & 0xFF000000 | var8 << 16 | var9 << 8 | var10;
    }

    public static int colorMultiply(int var0, float var1, float var2, float var3) {
        int var4 = (var0 & 0xFF0000) >> 16;
        int var5 = (var0 & 0xFF00) >> 8;
        int var6 = (var0 & 0xFF);
        int var7 = (int) (var4 * var1);
        int var8 = (int) (var5 * var2);
        int var9 = (int) (var6 * var3);
        return var0 & 0xFF000000 | var7 << 16 | var8 << 8 | var9;
    }

    public static float frac(float var0) {
        return var0 - floor(var0);
    }

    public static double frac(double var0) {
        return var0 - lfloor(var0);
    }

    public static long getSeed(int var0, int var1, int var2) {
        long var3 = (var0 * 3129871) ^ var2 * 116129781L ^ var1;
        var3 = var3 * var3 * 42317861L + var3 * 11L;
        return var3 >> 16L;
    }

    public static UUID createInsecureUUID(Random var0) {
        long var1 = var0.nextLong() & 0xFFFFFFFFFFFF0FFFL | 0x4000L;
        long var3 = var0.nextLong() & 0x3FFFFFFFFFFFFFFFL | Long.MIN_VALUE;
        return new UUID(var1, var3);
    }

    public static UUID createInsecureUUID() {
        return createInsecureUUID(RANDOM);
    }

    public static double inverseLerp(double var0, double var2, double var4) {
        return (var0 - var2) / (var4 - var2);
    }

    public static double atan2(double var0, double var2) {
        double var4 = var2 * var2 + var0 * var0;
        if (Double.isNaN(var4))
            return Double.NaN;
        boolean var6 = (var0 < 0.0D);
        if (var6)
            var0 = -var0;
        boolean var7 = (var2 < 0.0D);
        if (var7)
            var2 = -var2;
        boolean var8 = (var0 > var2);
        if (var8) {
            double d = var2;
            var2 = var0;
            var0 = d;
        }
        double var9 = fastInvSqrt(var4);
        var2 *= var9;
        var0 *= var9;
        double var11 = FRAC_BIAS + var0;
        int var13 = (int) Double.doubleToRawLongBits(var11);
        double var14 = ASIN_TAB[var13];
        double var16 = COS_TAB[var13];
        double var18 = var11 - FRAC_BIAS;
        double var20 = var0 * var16 - var2 * var18;
        double var22 = (6.0D + var20 * var20) * var20 * 0.16666666666666666D;
        double var24 = var14 + var22;
        if (var8)
            var24 = 1.5707963267948966D - var24;
        if (var7)
            var24 = Math.PI - var24;
        if (var6)
            var24 = -var24;
        return var24;
    }

    public static float fastInvSqrt(float var0) {
        float var1 = 0.5F * var0;
        int var2 = Float.floatToIntBits(var0);
        var2 = 1597463007 - (var2 >> 1);
        var0 = Float.intBitsToFloat(var2);
        var0 *= 1.5F - var1 * var0 * var0;
        return var0;
    }

    public static double fastInvSqrt(double var0) {
        double var2 = 0.5D * var0;
        long var4 = Double.doubleToRawLongBits(var0);
        var4 = 6910469410427058090L - (var4 >> 1L);
        var0 = Double.longBitsToDouble(var4);
        var0 *= 1.5D - var2 * var0 * var0;
        return var0;
    }

    public static float fastInvCubeRoot(float var0) {
        int var1 = Float.floatToIntBits(var0);
        var1 = 1419967116 - var1 / 3;
        float var2 = Float.intBitsToFloat(var1);
        var2 = 0.6666667F * var2 + 1.0F / 3.0F * var2 * var2 * var0;
        var2 = 0.6666667F * var2 + 1.0F / 3.0F * var2 * var2 * var0;
        return var2;
    }

    public static int hsvToRgb(float var0, float var1, float var2) {
        float var8, var9, var10;
        int var11, var12, var13, var3 = (int) (var0 * 6.0F) % 6;
        float var4 = var0 * 6.0F - var3;
        float var5 = var2 * (1.0F - var1);
        float var6 = var2 * (1.0F - var4 * var1);
        float var7 = var2 * (1.0F - (1.0F - var4) * var1);
        switch (var3) {
            case 0:
                var8 = var2;
                var9 = var7;
                var10 = var5;
                var11 = clamp((int) (var8 * 255.0F), 0, 255);
                var12 = clamp((int) (var9 * 255.0F), 0, 255);
                var13 = clamp((int) (var10 * 255.0F), 0, 255);
                return var11 << 16 | var12 << 8 | var13;
            case 1:
                var8 = var6;
                var9 = var2;
                var10 = var5;
                var11 = clamp((int) (var8 * 255.0F), 0, 255);
                var12 = clamp((int) (var9 * 255.0F), 0, 255);
                var13 = clamp((int) (var10 * 255.0F), 0, 255);
                return var11 << 16 | var12 << 8 | var13;
            case 2:
                var8 = var5;
                var9 = var2;
                var10 = var7;
                var11 = clamp((int) (var8 * 255.0F), 0, 255);
                var12 = clamp((int) (var9 * 255.0F), 0, 255);
                var13 = clamp((int) (var10 * 255.0F), 0, 255);
                return var11 << 16 | var12 << 8 | var13;
            case 3:
                var8 = var5;
                var9 = var6;
                var10 = var2;
                var11 = clamp((int) (var8 * 255.0F), 0, 255);
                var12 = clamp((int) (var9 * 255.0F), 0, 255);
                var13 = clamp((int) (var10 * 255.0F), 0, 255);
                return var11 << 16 | var12 << 8 | var13;
            case 4:
                var8 = var7;
                var9 = var5;
                var10 = var2;
                var11 = clamp((int) (var8 * 255.0F), 0, 255);
                var12 = clamp((int) (var9 * 255.0F), 0, 255);
                var13 = clamp((int) (var10 * 255.0F), 0, 255);
                return var11 << 16 | var12 << 8 | var13;
            case 5:
                var8 = var2;
                var9 = var5;
                var10 = var6;
                var11 = clamp((int) (var8 * 255.0F), 0, 255);
                var12 = clamp((int) (var9 * 255.0F), 0, 255);
                var13 = clamp((int) (var10 * 255.0F), 0, 255);
                return var11 << 16 | var12 << 8 | var13;
        }
        throw new RuntimeException("Something went wrong when converting from HSV to RGB. Input was " + var0 + ", " + var1 + ", " + var2);
    }

    public static int murmurHash3Mixer(int var0) {
        var0 ^= var0 >>> 16;
        var0 *= -2048144789;
        var0 ^= var0 >>> 13;
        var0 *= -1028477387;
        var0 ^= var0 >>> 16;
        return var0;
    }

    public static long murmurHash3Mixer(long var0) {
        var0 ^= var0 >>> 33L;
        var0 *= -49064778989728563L;
        var0 ^= var0 >>> 33L;
        var0 *= -4265267296055464877L;
        var0 ^= var0 >>> 33L;
        return var0;
    }

    public static double[] cumulativeSum(double... var0) {
        float var1 = 0.0F;
        for (double var5 : var0)
            var1 = (float) (var1 + var5);
        int var2;
        for (var2 = 0; var2 < var0.length; var2++)
            var0[var2] = var0[var2] / var1;
        for (var2 = 0; var2 < var0.length; var2++)
            var0[var2] = ((var2 == 0) ? 0.0D : var0[var2 - 1]) + var0[var2];
        return var0;
    }

    public static int getRandomForDistributionIntegral(Random var0, double[] var1) {
        double var2 = var0.nextDouble();
        for (int var4 = 0; var4 < var1.length; var4++) {
            if (var2 < var1[var4])
                return var4;
        }
        return var1.length;
    }

    public static double[] binNormalDistribution(double var0, double var2, double var4, int var6, int var7) {
        double[] var8 = new double[var7 - var6 + 1];
        int var9 = 0;
        for (int var10 = var6; var10 <= var7; var10++) {
            var8[var9] = Math.max(0.0D, var0 *

                    StrictMath.exp(-(var10 - var4) * (var10 - var4) / 2.0D * var2 * var2));
            var9++;
        }
        return var8;
    }

    public static double[] binBiModalNormalDistribution(double var0, double var2, double var4, double var6, double var8, double var10, int var12, int var13) {
        double[] var14 = new double[var13 - var12 + 1];
        int var15 = 0;
        for (int var16 = var12; var16 <= var13; var16++) {
            var14[var15] = Math.max(0.0D, var0 *

                    StrictMath.exp(-(var16 - var4) * (var16 - var4) / 2.0D * var2 * var2) + var6 *
                    StrictMath.exp(-(var16 - var10) * (var16 - var10) / 2.0D * var8 * var8));
            var15++;
        }
        return var14;
    }

    public static double[] binLogDistribution(double var0, double var2, int var4, int var5) {
        double[] var6 = new double[var5 - var4 + 1];
        int var7 = 0;
        for (int var8 = var4; var8 <= var5; var8++) {
            var6[var7] = Math.max(var0 * StrictMath.log(var8) + var2, 0.0D);
            var7++;
        }
        return var6;
    }

    public static float lerp(float var0, float var1, float var2) {
        return var1 + var0 * (var2 - var1);
    }

    public static double lerp(double var0, double var2, double var4) {
        return var2 + var0 * (var4 - var2);
    }

    public static double lerp2(double var0, double var2, double var4, double var6, double var8, double var10) {
        return lerp(var2,

                lerp(var0, var4, var6),
                lerp(var0, var8, var10));
    }

    public static double lerp3(double var0, double var2, double var4, double var6, double var8, double var10, double var12, double var14, double var16, double var18, double var20) {
        return lerp(var4,

                lerp2(var0, var2, var6, var8, var10, var12),
                lerp2(var0, var2, var14, var16, var18, var20));
    }

    public static double smoothstep(double var0) {
        return var0 * var0 * var0 * (var0 * (var0 * 6.0D - 15.0D) + 10.0D);
    }

    public static double smoothstepDerivative(double var0) {
        return 30.0D * var0 * var0 * (var0 - 1.0D) * (var0 - 1.0D);
    }

    public static int sign(double var0) {
        if (var0 == 0.0D)
            return 0;
        return (var0 > 0.0D) ? 1 : -1;
    }

    public static float rotLerp(float var0, float var1, float var2) {
        return var1 + var0 * wrapDegrees(var2 - var1);
    }

    public static float diffuseLight(float var0, float var1, float var2) {
        return Math.min(var0 * var0 * 0.6F + var1 * var1 * (3.0F + var1) / 4.0F + var2 * var2 * 0.8F, 1.0F);
    }

    @Deprecated
    public static float rotlerp(float var0, float var1, float var2) {
        float var3 = var1 - var0;
        while (var3 < -180.0F)
            var3 += 360.0F;
        while (var3 >= 180.0F)
            var3 -= 360.0F;
        return var0 + var2 * var3;
    }

    @Deprecated
    public static float rotWrap(double var0) {
        while (var0 >= 180.0D)
            var0 -= 360.0D;
        while (var0 < -180.0D)
            var0 += 360.0D;
        return (float) var0;
    }

    public static float triangleWave(float var0, float var1) {
        return (Math.abs(var0 % var1 - var1 * 0.5F) - var1 * 0.25F) / var1 * 0.25F;
    }

    public static float square(float var0) {
        return var0 * var0;
    }

    public static double square(double var0) {
        return var0 * var0;
    }

    public static int square(int var0) {
        return var0 * var0;
    }

    public static double clampedMap(double var0, double var2, double var4, double var6, double var8) {
        return clampedLerp(var6, var8, inverseLerp(var0, var2, var4));
    }

    public static double map(double var0, double var2, double var4, double var6, double var8) {
        return lerp(inverseLerp(var0, var2, var4), var6, var8);
    }

    public static double wobble(double var0) {
        return var0 + (2.0D * (new Random(floor(var0 * 3000.0D))).nextDouble() - 1.0D) * 1.0E-7D / 2.0D;
    }

    public static int roundToward(int var0, int var1) {
        return (var0 + var1 - 1) / var1 * var1;
    }

    public static int randomBetweenInclusive(Random var0, int var1, int var2) {
        return var0.nextInt(var2 - var1 + 1) + var1;
    }

    public static float randomBetween(Random var0, float var1, float var2) {
        return var0.nextFloat() * (var2 - var1) + var1;
    }

    public static float normal(Random var0, float var1, float var2) {
        return var1 + (float) var0.nextGaussian() * var2;
    }

    public static double length(int var0, double var1, int var3) {
        return Math.sqrt((var0 * var0) + var1 * var1 + (var3 * var3));
    }
}