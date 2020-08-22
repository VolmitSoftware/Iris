package com.volmit.iris.util;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public abstract class DataPalette<T> implements Writable
{
	private static final int DEFAULT_BITS_PER_BLOCK = 4;
	private static final int CAPACITY = 4096;
	private int bpb;
	private NibbleArray data;
	private KList<T> palette;

	public DataPalette(T defaultValue)
	{
		palette = new KList<>();
		bpb = DEFAULT_BITS_PER_BLOCK;
		data = new NibbleArray(bpb, CAPACITY);
		data.setAll(Byte.MIN_VALUE);
		getPaletteId(defaultValue);
	}

	public abstract T readType(DataInputStream i) throws IOException;

	public abstract void writeType(T t, DataOutputStream o) throws IOException;

	@Override
	public void write(DataOutputStream o) throws IOException
	{
		o.writeByte(bpb + Byte.MIN_VALUE);
		o.writeByte(palette.size() + Byte.MIN_VALUE);

		for(T i : palette)
		{
			writeType(i, o);
		}

		data.write(o);
	}

	@Override
	public void read(DataInputStream i) throws IOException
	{
		bpb = i.readByte() - Byte.MIN_VALUE;
		palette = new KList<>();
		int v = i.readByte() - Byte.MIN_VALUE;

		for(int j = 0; j < v; j++)
		{
			palette.add(readType(i));
		}

		data = new NibbleArray(CAPACITY, i);
	}

	private final void expand()
	{
		if(bpb < 8)
		{
			changeBitsPerBlock(bpb + 1);
		}

		else
		{
			throw new IndexOutOfBoundsException("The Data Palette can only handle at most 256 block types per 16x16x16 region. We cannot use more than 8 bits per block!");
		}
	}

	public final void optimize()
	{
		int targetBits = bpb;
		int needed = palette.size();

		for(int i = 1; i < bpb; i++)
		{
			if(Math.pow(2, i) > needed)
			{
				targetBits = i;
				break;
			}
		}

		changeBitsPerBlock(targetBits);
	}

	private final void changeBitsPerBlock(int bits)
	{
		bpb = bits;
		data = new NibbleArray(bpb, CAPACITY, data);
	}

	public final void set(int x, int y, int z, T d)
	{
		data.set(getCoordinateIndex(x, y, z), getPaletteId(d));
	}

	public final T get(int x, int y, int z)
	{
		return palette.get(data.get(getCoordinateIndex(x, y, z)));
	}

	private final int getPaletteId(T d)
	{
		int index = palette.indexOf(d);

		if(index == -1)
		{
			index = palette.size();
			palette.add(d);

			if(palette.size() > Math.pow(2, bpb))
			{
				expand();
			}
		}

		return index + Byte.MIN_VALUE;
	}

	private final int getCoordinateIndex(int x, int y, int z)
	{
		return y << 8 | z << 4 | x;
	}
}
