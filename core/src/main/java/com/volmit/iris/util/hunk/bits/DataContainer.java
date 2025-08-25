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

package com.volmit.iris.util.hunk.bits;

import com.volmit.iris.util.data.Varint;
import lombok.Synchronized;

import java.io.*;

public class DataContainer<T> {
    protected static final int INITIAL_BITS = 3;
    protected static final int LINEAR_BITS_LIMIT = 4;
    protected static final int LINEAR_INITIAL_LENGTH = (int) Math.pow(2, LINEAR_BITS_LIMIT) + 1;
    protected static final int[] BIT = computeBitLimits();
    private volatile Palette<T> palette;
    private volatile DataBits data;
    private final int length;
    private final Writable<T> writer;

    public DataContainer(Writable<T> writer, int length) {
        this.writer = writer;
        this.length = length;
        this.data = new DataBits(INITIAL_BITS, length);
        this.palette = newPalette(INITIAL_BITS);
    }

    public DataContainer(DataInputStream din, Writable<T> writer) throws IOException {
        this.writer = writer;
        this.length = Varint.readUnsignedVarInt(din);
        this.palette = newPalette(din);
        this.data = new DataBits(palette.bits(), length, din);
    }

    private static int[] computeBitLimits() {
        int[] m = new int[16];

        for (int i = 0; i < m.length; i++) {
            m[i] = (int) Math.pow(2, i);
        }

        return m;
    }

    protected static int bits(int size) {
        if (DataContainer.BIT[INITIAL_BITS] >= size) {
            return INITIAL_BITS;
        }

        for (int i = 0; i < DataContainer.BIT.length; i++) {
            if (DataContainer.BIT[i] >= size) {
                return i;
            }
        }

        return DataContainer.BIT.length - 1;
    }

    public String toString() {
        return "DataContainer <" + length + " x " + data.getBits() + " bits> -> Palette<" + palette.getClass().getSimpleName().replaceAll("\\QPalette\\E", "") + ">: " + palette.size() +
                " " + data.toString() + " PalBit: " + palette.bits();
    }

    public byte[] write() throws IOException {
        ByteArrayOutputStream boas = new ByteArrayOutputStream();
        write(boas);
        return boas.toByteArray();
    }

    public void write(OutputStream out) throws IOException {
        writeDos(new DataOutputStream(out));
    }

    @Synchronized
    public void writeDos(DataOutputStream dos) throws IOException {
        Varint.writeUnsignedVarInt(length, dos);
        Varint.writeUnsignedVarInt(palette.size(), dos);
        palette.iterateIO((data, __) -> writer.writeNodeData(dos, data));
        data.write(dos);
        dos.flush();
    }

    private Palette<T> newPalette(DataInputStream din) throws IOException {
        int paletteSize = Varint.readUnsignedVarInt(din);
        Palette<T> d = newPalette(bits(paletteSize + 1));
        d.from(paletteSize, writer, din);
        return d;
    }

    private Palette<T> newPalette(int bits) {
        if (bits <= LINEAR_BITS_LIMIT) {
            return new LinearPalette<>(LINEAR_INITIAL_LENGTH);
        }

        return new HashPalette<>();
    }

    @Synchronized
    public void set(int position, T t) {
        int id = palette.id(t);

        if (id == -1) {
            id = palette.add(t);
            updateBits();
        }

        data.set(position, id);
    }

    @Synchronized
    @SuppressWarnings("NonAtomicOperationOnVolatileField")
    private void updateBits() {
        if (palette.bits() == data.getBits())
            return;

        int bits = palette.bits();
        if (data.getBits() <= LINEAR_BITS_LIMIT != bits <= LINEAR_BITS_LIMIT) {
            palette = newPalette(bits).from(palette);
        }

        data = data.setBits(bits);
    }

    @Synchronized
    public T get(int position) {
        int id = data.get(position);

        if (id <= 0) {
            return null;
        }

        return palette.get(id);
    }

    public int size() {
        return data.getSize();
    }
}
