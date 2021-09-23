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

import com.volmit.iris.util.data.Varint;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class DataContainer<T> {
    protected static final int INITIAL_BITS = 3;
    protected static final int LINEAR_BITS_LIMIT = 5;
    protected static final int LINEAR_INITIAL_LENGTH = (int) Math.pow(2, LINEAR_BITS_LIMIT) + 1;
    protected static final int[] BIT = computeBitLimits();
    private final AtomicReference<Palette<T>> palette;
    private final AtomicReference<DataBits> data;
    private final AtomicInteger bits;
    private final int length;
    private final Writable<T> writer;

    public DataContainer(Writable<T> writer, int length)
    {
        this.writer = writer;
        this.length = length;
        this.palette = new AtomicReference<>(newPalette(INITIAL_BITS));
        this.data = new AtomicReference<>(new DataBits(INITIAL_BITS, length));
        this.bits = new AtomicInteger(INITIAL_BITS);
    }

    public byte[] write() throws IOException
    {
        ByteArrayOutputStream boas = new ByteArrayOutputStream();
        write(boas);
        return boas.toByteArray();
    }

    public static <T> DataContainer<T> read(InputStream in, Writable<T> writer) throws IOException {
        DataInputStream din = new DataInputStream(in);
        return readDin(din, writer);
    }

    public static <T> DataContainer<T> readDin(DataInputStream in, Writable<T> writer) throws IOException {
        DataInputStream din = new DataInputStream(in);
        DataContainer<T> container = new DataContainer<>(writer, Varint.readUnsignedVarInt(din));
        int paletteSize = Varint.readUnsignedVarInt(din);
        container.palette.set(container.newPalette(BIT[paletteSize]).from(paletteSize, writer, din));
        container.data.set(new DataBits(container.palette.get().bits(), container.length, din));
        return container;
    }

    public void write(OutputStream out) throws IOException
    {
        DataOutputStream dos = new DataOutputStream(out);
        writeDos(dos);
    }

    public void writeDos(DataOutputStream out) throws IOException
    {
        DataOutputStream dos = new DataOutputStream(out);
        Varint.writeUnsignedVarInt(length);
        Varint.writeUnsignedVarInt(palette.get().size());
        palette.get().iterateIO((data, __) -> writer.writeNodeData(dos, data));
        data.get().write(dos);
    }

    private Palette<T> newPalette(int bits)
    {
        if(bits <= LINEAR_BITS_LIMIT)
        {
            return new LinearPalette<>(LINEAR_INITIAL_LENGTH);
        }

        return new HashPalette<>();
    }

    public void set(int position, T t)
    {
        int id = palette.get().id(t);

        if(id == -1)
        {
            id = palette.get().add(t);
        }

        data.get().set(position, id);
    }

    public T get(int position)
    {
        int id = data.get().get(position);

        if(id <= 0)
        {
            return null;
        }

        return palette.get().get(id - 1);
    }

    public void setBits(int bits)
    {
        if(this.bits.get() != bits)
        {
            if(this.bits.get() <= LINEAR_BITS_LIMIT != bits <= LINEAR_BITS_LIMIT)
            {
                palette.set(newPalette(bits).from(palette.get()));
            }

            this.bits.set(bits);
            data.set(data.get().setBits(bits));
        }
    }

    private static int[] computeBitLimits() {
        int[] m = new int[16];

        for(int i = 0; i < m.length; i++)
        {
            m[i] = (int) Math.pow(2, i);
        }

        return m;
    }
}
