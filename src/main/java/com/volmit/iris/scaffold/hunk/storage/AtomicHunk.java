package com.volmit.iris.scaffold.hunk.storage;

import java.util.concurrent.atomic.AtomicReferenceArray;

import com.volmit.iris.scaffold.hunk.Hunk;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class AtomicHunk<T> extends StorageHunk<T> implements Hunk<T>
{
	private final AtomicReferenceArray<T> data;

	public AtomicHunk(int w, int h, int d)
	{
		super(w, h, d);
		data = new AtomicReferenceArray<T>(w * h * d);
	}
	
	@Override
	public boolean isAtomic()
	{
		return true;
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
