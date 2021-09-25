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

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public class DataContainer<T> {
    protected static final int INITIAL_BITS = 2;
    protected static final int LINEAR_BITS_LIMIT = 5;
    protected static final int LINEAR_INITIAL_LENGTH = (int) Math.pow(2, LINEAR_BITS_LIMIT) + 1;
    protected static final int[] BIT = computeBitLimits();
    private final AtomicReference<Palette<T>> palette;
    private final AtomicReference<DataBits> data;
    private final AtomicInteger bits;
    private final int length;
    private final Writable<T> writer;

    public DataContainer(Writable<T> writer, int length) {
        this.writer = writer;
        this.length = length;
        this.bits = new AtomicInteger(INITIAL_BITS);
        this.data = new AtomicReference<>(new DataBits(INITIAL_BITS, length));
        this.palette = new AtomicReference<>(newPalette(INITIAL_BITS));
    }

    public DataContainer(DataInputStream din, Writable<T> writer) throws IOException {
        this.writer = writer;
        this.length = din.readInt();
        this.palette = new AtomicReference<>(newPalette(din));
        this.data = new AtomicReference<>(new DataBits(palette.get().bits(), length, din));
        this.bits = new AtomicInteger(palette.get().bits());
    }

    public DataBits getData()
    {
        return data.get();
    }

    public Palette<T> getPalette()
    {
        return palette.get();
    }

    public String toString() {
        return "DataContainer <" + length + " x " + bits + " bits> -> Palette<" + palette.get().getClass().getSimpleName().replaceAll("\\QPalette\\E", "") + ">: " + palette.get().size() +
                " " + data.get().toString() + " PalBit: " + palette.get().bits();
    }

    public byte[] write() throws IOException {
        ByteArrayOutputStream boas = new ByteArrayOutputStream();
        write(boas);
        return boas.toByteArray();
    }

    public void write(OutputStream out) throws IOException {
        writeDos(new DataOutputStream(out));
    }

    public void writeDos(DataOutputStream dos) throws IOException {
        dos.writeInt(length);
        dos.writeInt(palette.get().size());
        palette.get().iterateIO((data, __) -> writer.writeNodeData(dos, data));
        data.get().write(dos);
        dos.flush();
    }

    private Palette<T> newPalette(DataInputStream din) throws IOException {
        int paletteSize = din.readInt();
        Palette<T> d = newPalette(bits(paletteSize+1));
        d.from(paletteSize, writer, din);
        return d;
    }

    private Palette<T> newPalette(int bits) {
        if (bits <= LINEAR_BITS_LIMIT) {
            return new LinearPalette<>(LINEAR_INITIAL_LENGTH);
        }

        return new HashPalette<>();
    }

    private void checkBits() {
        if (palette.get().size() >= BIT[bits.get()]) {
            setBits(bits.get() + 1);
        }
    }

    public void ensurePaletted(T t) {
        if (palette.get().id(t) == -1) {
            checkBits();
        }
    }

    public void set(int position, T t) {
        synchronized (this)
        {
            int id = palette.get().id(t);

            if (id == -1) {
                expandOne();
                id = palette.get().add(t);
            }

            data.get().set(position, id);
        }
    }

    private void expandOne() {
        if (palette.get().size()+1 >= BIT[bits.get()]) {
            setBits(bits.get() + 1);
        }
    }

    public T get(int position) {
        synchronized (this)
        {
            int id = data.get().get(position) + 1;

            if (id <= 0) {
                return null;
            }

            return palette.get().get(id - 1);
        }
    }

    public void setBits(int bits) {
        if (this.bits.get() != bits) {
            if (this.bits.get() <= LINEAR_BITS_LIMIT != bits <= LINEAR_BITS_LIMIT) {
                palette.set(newPalette(bits).from(palette.get()));
            }

            this.bits.set(bits);
            data.set(data.get().setBits(bits));
        }
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

    public int size() {
        return getData().getSize();
    }
}
