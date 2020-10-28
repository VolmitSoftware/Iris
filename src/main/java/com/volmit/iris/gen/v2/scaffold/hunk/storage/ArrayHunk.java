package com.volmit.iris.gen.v2.scaffold.hunk.storage;

import com.volmit.iris.gen.v2.scaffold.hunk.Hunk;
import org.bouncycastle.util.Arrays;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class ArrayHunk<T> extends StorageHunk<T> implements Hunk<T>
{
	private final T[] data;

	@SuppressWarnings("unchecked")
	public ArrayHunk(int w, int h, int d)
	{
		super(w, h, d);
		data = (T[]) new Object[w * h * d];
	}

	@Override
	public void setRaw(int x, int y, int z, T t)
	{
		data[index(x, y, z)] = t;
	}

	@Override
	public T getRaw(int x, int y, int z)
	{
		return data[index(x, y, z)];
	}

	private int index(int x, int y, int z)
	{
		return (z * getWidth() * getHeight()) + (y * getWidth()) + x;
	}

	@Override
	public void fill(T t)
	{
		Arrays.fill(data, t);
	}
}
