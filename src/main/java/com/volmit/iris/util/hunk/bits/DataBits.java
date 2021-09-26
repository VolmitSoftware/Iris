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

package com.volmit.iris.util.hunk.bits;

import com.volmit.iris.Iris;
import com.volmit.iris.util.data.Varint;
import org.apache.commons.lang3.Validate;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.function.IntConsumer;

public class DataBits {
    private static final int[] MAGIC = new int[]{
            -1, -1, 0, Integer.MIN_VALUE, 0, 0, 1431655765, 1431655765, 0, Integer.MIN_VALUE,
            0, 1, 858993459, 858993459, 0, 715827882, 715827882, 0, 613566756, 613566756,
            0, Integer.MIN_VALUE, 0, 2, 477218588, 477218588, 0, 429496729, 429496729, 0,
            390451572, 390451572, 0, 357913941, 357913941, 0, 330382099, 330382099, 0, 306783378,
            306783378, 0, 286331153, 286331153, 0, Integer.MIN_VALUE, 0, 3, 252645135, 252645135,
            0, 238609294, 238609294, 0, 226050910, 226050910, 0, 214748364, 214748364, 0,
            204522252, 204522252, 0, 195225786, 195225786, 0, 186737708, 186737708, 0, 178956970,
            178956970, 0, 171798691, 171798691, 0, 165191049, 165191049, 0, 159072862, 159072862,
            0, 153391689, 153391689, 0, 148102320, 148102320, 0, 143165576, 143165576, 0,
            138547332, 138547332, 0, Integer.MIN_VALUE, 0, 4, 130150524, 130150524, 0, 126322567,
            126322567, 0, 122713351, 122713351, 0, 119304647, 119304647, 0, 116080197, 116080197,
            0, 113025455, 113025455, 0, 110127366, 110127366, 0, 107374182, 107374182, 0,
            104755299, 104755299, 0, 102261126, 102261126, 0, 99882960, 99882960, 0, 97612893,
            97612893, 0, 95443717, 95443717, 0, 93368854, 93368854, 0, 91382282, 91382282,
            0, 89478485, 89478485, 0, 87652393, 87652393, 0, 85899345, 85899345, 0,
            84215045, 84215045, 0, 82595524, 82595524, 0, 81037118, 81037118, 0, 79536431,
            79536431, 0, 78090314, 78090314, 0, 76695844, 76695844, 0, 75350303, 75350303,
            0, 74051160, 74051160, 0, 72796055, 72796055, 0, 71582788, 71582788, 0,
            70409299, 70409299, 0, 69273666, 69273666, 0, 68174084, 68174084, 0, Integer.MIN_VALUE,
            0, 5};

    private final AtomicLongArray data;
    private final int bits;
    private final long mask;
    private final int size;
    private final int valuesPerLong;
    private final int divideMul;
    private final int divideAdd;
    private final int divideShift;

    public DataBits(int bits, int length) {
        this(bits, length, (AtomicLongArray) null);
    }

    public DataBits(int bits, int length, DataInputStream din) throws IOException {
        this(bits, length, longs(din, dataLength(bits, length)));
    }

    public DataBits(int bits, int length, AtomicLongArray data) {
        Validate.inclusiveBetween(1L, 32L, bits);
        this.size = length;
        this.bits = bits;
        this.mask = (1L << bits) - 1L;
        this.valuesPerLong = (char) (64 / bits);
        int var3 = 3 * (valuesPerLong - 1);
        this.divideMul = MAGIC[var3];
        this.divideAdd = MAGIC[var3 + 1];
        this.divideShift = MAGIC[var3 + 2];
        int var4 = (length + valuesPerLong - 1) / valuesPerLong;

        if (data != null) {
            if (data.length() != var4) {
                throw new RuntimeException("NO! Trying to load " + data.length() + " into actual size of " + var4 + " because length: " + length + " (bits: " + bits + ")");
            }
            this.data = data;
        } else {
            this.data = new AtomicLongArray(var4);
        }
    }

    public String toString() {
        return "DBits: " + size + "/" + bits + "[" + data.length() + "]";
    }

    private static int dataLength(int bits, int length) {
        return (length + ((char) (64 / bits)) - 1) / ((char) (64 / bits));
    }

    private static AtomicLongArray longs(DataInputStream din, int longSize) throws IOException {
        AtomicLongArray a = new AtomicLongArray(longSize);

        for (int i = 0; i < a.length(); i++) {
            a.set(i, Varint.readUnsignedVarLong(din));
        }

        return a;
    }

    public DataBits setBits(int newBits) {
        if (bits != newBits) {
            DataBits newData = new DataBits(newBits, size);
            AtomicInteger c = new AtomicInteger(0);

            for (int i = 0; i < size; i++) {
                newData.set(i, get(i));
            }

            return newData;
        }

        return this;
    }

    private int cellIndex(int var0) {
        long var1 = Integer.toUnsignedLong(this.divideMul);
        long var3 = Integer.toUnsignedLong(this.divideAdd);
        return (int) (var0 * var1 + var3 >> 32L >> this.divideShift);
    }

    @SuppressWarnings("PointlessBitwiseExpression")
    public int getAndSet(int var0, int var1) {
        Validate.inclusiveBetween(0L, (this.size - 1), var0);
        Validate.inclusiveBetween(0L, this.mask, var1);
        int var2 = cellIndex(var0);
        long var3 = this.data.get(var2);
        int var5 = (var0 - var2 * this.valuesPerLong) * this.bits;
        int var6 = (int) (var3 >> var5 & this.mask);
        this.data.set(var2, var3 & (this.mask << var5 ^ 0xFFFFFFFFFFFFFFFFL) | (var1 & this.mask) << var5);
        return var6;
    }

    @SuppressWarnings("PointlessBitwiseExpression")
    public void set(int var0, int var1) {
        Validate.inclusiveBetween(0L, (this.size - 1), var0);
        Validate.inclusiveBetween(0L, this.mask, var1);
        int var2 = cellIndex(var0);
        long var3 = this.data.get(var2);
        int var5 = (var0 - var2 * this.valuesPerLong) * this.bits;

        this.data.set(var2, var3 & (this.mask << var5 ^ 0xFFFFFFFFFFFFFFFFL) | (var1 & this.mask) << var5);
    }

    public int get(int var0) {
        Validate.inclusiveBetween(0L, (size - 1), var0);
        int var1 = cellIndex(var0);
        long var2 = this.data.get(var1);
        int var4 = (var0 - var1 * valuesPerLong) * this.bits;
        return (int) (var2 >> var4 & mask);
    }

    public AtomicLongArray getRaw() {
        return data;
    }

    public int getSize() {
        return size;
    }

    public int getBits() {
        return bits;
    }

    public void getAll(IntConsumer var0) {
        int var1 = 0;
        for (int i = 0; i < data.length(); i++) {
            long var5 = data.get(i);
            for (int var7 = 0; var7 < valuesPerLong; var7++) {
                var0.accept((int) (var5 & mask));
                var5 >>= bits;
                if (++var1 >= size) {
                    return;
                }
            }
        }
    }

    public void write(DataOutputStream dos) throws IOException {
        for (int i = 0; i < data.length(); i++) {
            Varint.writeUnsignedVarLong(data.get(i), dos);
        }
    }
}
