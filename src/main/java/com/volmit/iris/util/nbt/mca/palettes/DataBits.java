/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.util.nbt.mca.palettes;

import org.apache.commons.lang3.Validate;

import java.util.function.IntConsumer;

public class DataBits {
    private static final int[] a = new int[]{-1, -1, 0, -2147483648, 0, 0, 1431655765, 1431655765, 0, -2147483648, 0, 1, 858993459, 858993459, 0, 715827882, 715827882, 0, 613566756, 613566756, 0, -2147483648, 0, 2, 477218588, 477218588, 0, 429496729, 429496729, 0, 390451572, 390451572, 0, 357913941, 357913941, 0, 330382099, 330382099, 0, 306783378, 306783378, 0, 286331153, 286331153, 0, -2147483648, 0, 3, 252645135, 252645135, 0, 238609294, 238609294, 0, 226050910, 226050910, 0, 214748364, 214748364, 0, 204522252, 204522252, 0, 195225786, 195225786, 0, 186737708, 186737708, 0, 178956970, 178956970, 0, 171798691, 171798691, 0, 165191049, 165191049, 0, 159072862, 159072862, 0, 153391689, 153391689, 0, 148102320, 148102320, 0, 143165576, 143165576, 0, 138547332, 138547332, 0, -2147483648, 0, 4, 130150524, 130150524, 0, 126322567, 126322567, 0, 122713351, 122713351, 0, 119304647, 119304647, 0, 116080197, 116080197, 0, 113025455, 113025455, 0, 110127366, 110127366, 0, 107374182, 107374182, 0, 104755299, 104755299, 0, 102261126, 102261126, 0, 99882960, 99882960, 0, 97612893, 97612893, 0, 95443717, 95443717, 0, 93368854, 93368854, 0, 91382282, 91382282, 0, 89478485, 89478485, 0, 87652393, 87652393, 0, 85899345, 85899345, 0, 84215045, 84215045, 0, 82595524, 82595524, 0, 81037118, 81037118, 0, 79536431, 79536431, 0, 78090314, 78090314, 0, 76695844, 76695844, 0, 75350303, 75350303, 0, 74051160, 74051160, 0, 72796055, 72796055, 0, 71582788, 71582788, 0, 70409299, 70409299, 0, 69273666, 69273666, 0, 68174084, 68174084, 0, -2147483648, 0, 5};
    private final long[] b;
    private final int c;
    private final long d;
    private final int e;
    private final int f;
    private final int g;
    private final int h;
    private final int i;

    public DataBits(int var0, int var1) {
        this(var0, var1, (long[]) null);
    }

    public DataBits(int var0, int var1, long[] var2) {
        Validate.inclusiveBetween(1L, 32L, (long) var0);
        this.e = var1;
        this.c = var0;
        this.d = (1L << var0) - 1L;
        this.f = (char) (64 / var0);
        int var3 = 3 * (this.f - 1);
        this.g = a[var3 + 0];
        this.h = a[var3 + 1];
        this.i = a[var3 + 2];
        int var4 = (var1 + this.f - 1) / this.f;
        if (var2 != null) {
            if (var2.length != var4) {
                throw new RuntimeException("Invalid length given for storage, got: " + var2.length + " but expected: " + var4);
            }

            this.b = var2;
        } else {
            this.b = new long[var4];
        }

    }

    private int b(int var0) {
        long var1 = Integer.toUnsignedLong(this.g);
        long var3 = Integer.toUnsignedLong(this.h);
        return (int) ((long) var0 * var1 + var3 >> 32 >> this.i);
    }

    public int a(int var0, int var1) {
        Validate.inclusiveBetween(0L, (long) (this.e - 1), (long) var0);
        Validate.inclusiveBetween(0L, this.d, (long) var1);
        int var2 = this.b(var0);
        long var3 = this.b[var2];
        int var5 = (var0 - var2 * this.f) * this.c;
        int var6 = (int) (var3 >> var5 & this.d);
        this.b[var2] = var3 & ~(this.d << var5) | ((long) var1 & this.d) << var5;
        return var6;
    }

    public void b(int var0, int var1) {
        Validate.inclusiveBetween(0L, (long) (this.e - 1), (long) var0);
        Validate.inclusiveBetween(0L, this.d, (long) var1);
        int var2 = this.b(var0);
        long var3 = this.b[var2];
        int var5 = (var0 - var2 * this.f) * this.c;
        this.b[var2] = var3 & ~(this.d << var5) | ((long) var1 & this.d) << var5;
    }

    public int getIndexFromPos(int var0) {
        Validate.inclusiveBetween(0L, (long) (this.e - 1), (long) var0);
        int var1 = this.b(var0);
        long var2 = this.b[var1];
        int var4 = (var0 - var1 * this.f) * this.c;
        return (int) (var2 >> var4 & this.d);
    }

    public long[] getData() {
        return this.b;
    }

    public int b() {
        return this.e;
    }

    public int c() {
        return this.c;
    }

    public void a(IntConsumer var0) {
        int var1 = 0;
        long[] var3 = this.b;
        int var4 = var3.length;
        for (long l : var3) {
            long var5 = l;
            for (int j = 0; j < this.f; ++j) {
                var0.accept((int) (var5 & this.d));
                var5 >>= this.c;
                ++var1;
                if (var1 >= this.e) {
                    return;
                }
            }
        }
    }
}