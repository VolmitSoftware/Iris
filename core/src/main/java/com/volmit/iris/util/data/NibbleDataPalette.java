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

import com.volmit.iris.util.collection.KList;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public abstract class NibbleDataPalette<T> implements Writable {
    private static final int DEFAULT_BITS_PER_BLOCK = 4;
    private static final int CAPACITY = 4096;
    private int bpb;
    private NibbleArray data;
    private KList<T> palette;

    public NibbleDataPalette(T defaultValue) {
        palette = new KList<>();
        bpb = DEFAULT_BITS_PER_BLOCK;
        data = new NibbleArray(bpb, CAPACITY);
        data.setAll(Byte.MIN_VALUE);
        getPaletteId(defaultValue);
    }

    public abstract T readType(DataInputStream i) throws IOException;

    public abstract void writeType(T t, DataOutputStream o) throws IOException;

    @Override
    public void write(DataOutputStream o) throws IOException {
        o.writeByte(bpb + Byte.MIN_VALUE);
        o.writeByte(palette.size() + Byte.MIN_VALUE);

        for (T i : palette) {
            writeType(i, o);
        }

        data.write(o);
    }

    @Override
    public void read(DataInputStream i) throws IOException {
        bpb = i.readByte() - Byte.MIN_VALUE;
        palette = new KList<>();
        int v = i.readByte() - Byte.MIN_VALUE;

        for (int j = 0; j < v; j++) {
            palette.add(readType(i));
        }

        data = new NibbleArray(CAPACITY, i);
    }

    private void expand() {
        if (bpb < 8) {
            changeBitsPerBlock(bpb + 1);
        } else {
            throw new IndexOutOfBoundsException("The Data Palette can only handle at most 256 block types per 16x16x16 region. We cannot use more than 8 bits per block!");
        }
    }

    public final void optimize() {
        int targetBits = bpb;
        int needed = palette.size();

        for (int i = 1; i < bpb; i++) {
            if (Math.pow(2, i) > needed) {
                targetBits = i;
                break;
            }
        }

        changeBitsPerBlock(targetBits);
    }

    private void changeBitsPerBlock(int bits) {
        bpb = bits;
        data = new NibbleArray(bpb, CAPACITY, data);
    }

    public final void set(int x, int y, int z, T d) {
        data.set(getCoordinateIndex(x, y, z), getPaletteId(d));
    }

    public final T get(int x, int y, int z) {
        return palette.get(data.get(getCoordinateIndex(x, y, z)));
    }

    private int getPaletteId(T d) {
        int index = palette.indexOf(d);

        if (index == -1) {
            index = palette.size();
            palette.add(d);

            if (palette.size() > Math.pow(2, bpb)) {
                expand();
            }
        }

        return index + Byte.MIN_VALUE;
    }

    private int getCoordinateIndex(int x, int y, int z) {
        return y << 8 | z << 4 | x;
    }
}
