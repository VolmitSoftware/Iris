package com.volmit.iris.v2.scaffold.hunk.storage;

import com.volmit.iris.v2.scaffold.hunk.Hunk;
import com.volmit.iris.util.BlockPosition;
import com.volmit.iris.util.KMap;

import lombok.Data;
import lombok.EqualsAndHashCode;

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
	public T getRaw(int x, int y, int z)
	{
		return data.get(new BlockPosition(x, y, z));
	}
}
