package com.volmit.iris.gen.v2.scaffold.hunk;

import java.util.concurrent.atomic.AtomicReferenceArray;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class AtomicHunk<T> extends StorageHunk<T> implements Hunk<T>
{
	private final AtomicReferenceArray<T> data;

	protected AtomicHunk(int w, int h, int d)
	{
		super(w, h, d);
		data = new AtomicReferenceArray<T>(w * h * d);
	}

	@Override
	public void setRaw(int x, int y, int z, T t)
	{
		data.set(index(x, y, z), t);
	}

	@Override
	public T getRaw(int x, int y, int z)
	{
		return data.get(index(x, y, z));
	}

	private int index(int x, int y, int z)
	{
		return (z * getWidth() * getHeight()) + (y * getWidth()) + x;
	}
}
