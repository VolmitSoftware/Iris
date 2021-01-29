package com.volmit.iris.scaffold.hunk.storage;

import com.volmit.iris.scaffold.hunk.Hunk;
import com.volmit.iris.util.Consumer4;
import com.volmit.iris.util.KMap;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = false)
public class MappedHunk<T> extends StorageHunk<T> implements Hunk<T>
{
	private final Map<Integer, T> data;

	public MappedHunk(int w, int h, int d)
	{
		super(w, h, d);
		data = new KMap<>();
	}

	@Override
	public void setRaw(int x, int y, int z, T t)
	{
		if(t == null)
		{
			data.remove(index(x,y,z));
			return;
		}

		data.put(index(x, y, z), t);
	}

	private Integer index(int x, int y, int z)
	{
		return (z * getWidth() * getHeight()) + (y * getWidth()) + x;
	}

	@Override
	public synchronized Hunk<T> iterateSync(Consumer4<Integer, Integer, Integer, T> c)
	{
		int idx, z;

		for(Map.Entry<Integer, T> g : data.entrySet())
		{
			idx = g.getKey();
			z = idx / (getWidth() * getHeight());
			idx -= (z * getWidth() * getHeight());
			c.accept(idx % getWidth(), idx / getWidth(), z, g.getValue());
		}

		return this;
	}

	@Override
	public void empty(T b)
	{
		data.clear();
	}

	@Override
	public T getRaw(int x, int y, int z)
	{
		return data.get(index(x, y, z));
	}
}
