package com.volmit.iris.v2.scaffold.hunk.storage;

import com.volmit.iris.util.Consumer4;
import com.volmit.iris.v2.scaffold.hunk.Hunk;
import com.volmit.iris.util.BlockPosition;
import com.volmit.iris.util.KMap;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = false)
public class MappedHunk<T> extends StorageHunk<T> implements Hunk<T>
{
	private final KMap<BlockPosition, T> data;

	public MappedHunk(int w, int h, int d)
	{
		super(w, h, d);
		data = new KMap<>();
	}

	@Override
	public void setRaw(int x, int y, int z, T t)
	{
		data.put(new BlockPosition(x, y, z), t);
	}

	@Override
	public Hunk<T> iterateSync(Consumer4<Integer, Integer, Integer, T> c)
	{
		for(Map.Entry<BlockPosition, T> g : data.entrySet())
		{
			c.accept( g.getKey().getX(),  g.getKey().getY(), g.getKey().getZ(), g.getValue());
		}

		return this;
	}

	@Override
	public T getRaw(int x, int y, int z)
	{
		return data.get(new BlockPosition(x, y, z));
	}
}
