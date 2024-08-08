/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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

package com.volmit.iris.util.nbt.mca.palette;

import org.apache.commons.lang3.Validate;

// todo Cool idea but im way to dumb for this for now
public class MCABitStorageByteArray {
    private final byte[] data;
    private final int bits;
    private final int mask;
    private final int size;
    private final int valuesPerByte;

    private final int divideMul;
    private final int divideAdd;
    private final int divideShift;

    public MCABitStorageByteArray(int bits, int length) {
        this(bits, length, null);
    }

    public MCABitStorageByteArray(int bits, int length, byte[] data) {
        Validate.inclusiveBetween(1L, 8L, bits);  // Ensure bits are between 1 and 8
        this.size = length;
        this.bits = bits;
        this.mask = (1 << bits) - 1;
        this.valuesPerByte = 8 / bits;
        int[] divisionParams = computeDivisionParameters(this.valuesPerByte);
        this.divideMul = divisionParams[0];
        this.divideAdd = divisionParams[1];
        this.divideShift = divisionParams[2];
        int numBytes = (length + this.valuesPerByte - 1) / this.valuesPerByte;
        if (data != null) {
            if (data.length != numBytes)
                throw new IllegalArgumentException("Data array length does not match the required size.");
            this.data = data;
        } else {
            this.data = new byte[numBytes];
        }
    }

    private int[] computeDivisionParameters(int denom) {
        long two32 = 1L << 32;
        long magic = two32 / denom;
        int shift = 0;
        while ((1L << (shift + 32)) < magic * denom) {
            shift++;
        }
        return new int[]{(int) magic, 0, shift};
    }

    private int cellIndex(int index) {
        long indexLong = Integer.toUnsignedLong(this.divideMul);
        long addLong = Integer.toUnsignedLong(this.divideAdd);
        return (int) ((index * indexLong + addLong) >>> 32 >>> this.divideShift);
    }

    public int getAndSet(int index, int newValue) {
        Validate.inclusiveBetween(0L, (this.size - 1), index);
        Validate.inclusiveBetween(0L, this.mask, newValue);
        int byteIndex = cellIndex(index);
        int bitOffset = (index - byteIndex * this.valuesPerByte) * this.bits;
        int currentValue = (this.data[byteIndex] >> bitOffset) & this.mask;
        this.data[byteIndex] = (byte) ((this.data[byteIndex] & ~(this.mask << bitOffset)) | (newValue & this.mask) << bitOffset);
        return currentValue;
    }

    public void set(int index, int value) {
        Validate.inclusiveBetween(0L, (this.size - 1), index);
        Validate.inclusiveBetween(0L, this.mask, value);
        int byteIndex = cellIndex(index);
        int bitOffset = (index - byteIndex * this.valuesPerByte) * this.bits;
        this.data[byteIndex] = (byte) ((this.data[byteIndex] & ~(this.mask << bitOffset)) | (value & this.mask) << bitOffset);
    }

    public int get(int index) {
        Validate.inclusiveBetween(0L, (this.size - 1), index);
        int byteIndex = cellIndex(index);
        int bitOffset = (index - byteIndex * this.valuesPerByte) * this.bits;
        return (this.data[byteIndex] >> bitOffset) & this.mask;
    }

    public byte[] getRaw() {
        return this.data;
    }

    public int getSize() {
        return this.size;
    }

    public int getBits() {
        return this.bits;
    }
}


