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

package com.volmit.iris.util.data;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.StringJoiner;

public class NibbleArray implements Writable {
    private static final int[] MASKS = new int[8];

    static {
        for (int i = 0; i < MASKS.length; i++) {
            MASKS[i] = maskFor(i);
        }
    }

    private final int size;
    private final Object lock = new Object();
    private byte[] data;
    private int depth;
    private byte mask;
    private transient int bitIndex, byteIndex, bitInByte;

    public NibbleArray(int capacity, DataInputStream in) throws IOException {
        size = capacity;
        read(in);
    }

    public NibbleArray(int nibbleDepth, int capacity) {
        if (nibbleDepth > 8 || nibbleDepth < 1) {
            throw new IllegalArgumentException();
        }

        int neededBits = nibbleDepth * capacity;

        size = capacity;
        depth = nibbleDepth;
        data = new byte[(neededBits + neededBits % 8) / 8];
        mask = (byte) maskFor(nibbleDepth);
    }

    public NibbleArray(int nibbleDepth, int capacity, NibbleArray existing) {
        if (nibbleDepth > 8 || nibbleDepth < 1) {
            throw new IllegalArgumentException();
        }

        int neededBits = nibbleDepth * capacity;
        size = capacity;
        depth = nibbleDepth;
        data = new byte[(neededBits + neededBits % 8) / 8];
        mask = (byte) maskFor(nibbleDepth);

        for (int i = 0; i < Math.min(size, existing.size()); i++) {
            set(i, existing.get(i));
        }
    }

    public static int maskFor(int amountOfBits) {
        return powerOfTwo(amountOfBits) - 1;
    }

    public static int powerOfTwo(int power) {
        int result = 1;

        for (int i = 0; i < power; i++) {
            result *= 2;
        }

        return result;
    }

    public static String binaryString(byte b, ByteOrder byteOrder) {
        String str = String.format("%8s", Integer.toBinaryString(b & 0xff)).replace(' ', '0');

        return byteOrder.equals(ByteOrder.BIG_ENDIAN) ? str : reverse(str);
    }

    public static String reverse(String str) {
        return new StringBuilder(str).reverse().toString();
    }

    @Override
    public void write(DataOutputStream o) throws IOException {
        o.writeByte(depth + Byte.MIN_VALUE);
        o.write(data);
    }

    @Override
    public void read(DataInputStream i) throws IOException {
        depth = i.readByte() - Byte.MIN_VALUE;
        int neededBits = depth * size;
        data = new byte[(neededBits + neededBits % 8) / 8];
        mask = (byte) maskFor(depth);
        i.read(data);
    }

    public int size() {
        return size;
    }

    public byte get(int index) {
        synchronized (lock) {
            bitIndex = index * depth;
            byteIndex = bitIndex >> 3;
            bitInByte = bitIndex & 7;
            int value = data[byteIndex] >> bitInByte;

            if (bitInByte + depth > 8) {
                value |= data[byteIndex + 1] << bitInByte;
            }

            return (byte) (value & mask);
        }
    }

    public byte get(int x, int y, int z) {
        return get(index(x, y, z));
    }

    public int index(int x, int y, int z) {
        return y << 8 | z << 4 | x;
    }

    public void set(int x, int y, int z, int nibble) {
        set(index(x, y, z), nibble);
    }

    public void set(int index, int nibble) {
        set(index, (byte) nibble);
    }

    public void set(int index, byte nybble) {
        synchronized (lock) {
            bitIndex = index * depth;
            byteIndex = bitIndex >> 3;
            bitInByte = bitIndex & 7;
            data[byteIndex] = (byte) (((~(data[byteIndex] & (mask << bitInByte)) & data[byteIndex]) | ((nybble & mask) << bitInByte)) & 0xff);

            if (bitInByte + depth > 8) {
                data[byteIndex + 1] = (byte) (((~(data[byteIndex + 1] & MASKS[bitInByte + depth - 8]) & data[byteIndex + 1]) | ((nybble & mask) >> (8 - bitInByte))) & 0xff);
            }
        }
    }

    public String toBitsString() {
        return toBitsString(ByteOrder.BIG_ENDIAN);
    }

    public String toBitsString(ByteOrder byteOrder) {
        StringJoiner joiner = new StringJoiner(" ");

        for (byte datum : data) {
            joiner.add(binaryString(datum, byteOrder));
        }

        return joiner.toString();
    }

    public void clear() {
        Arrays.fill(data, (byte) 0);
    }

    public void setAll(byte nibble) {
        for (int i = 0; i < size; i++) {
            set(i, nibble);
        }
    }

    public void setAll(int nibble) {
        for (int i = 0; i < size; i++) {
            set(i, (byte) nibble);
        }
    }
}