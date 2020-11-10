package com.volmit.iris.scaffold.hunk.view;

import com.volmit.iris.scaffold.hunk.Hunk;

public class DriftHunkView<T> implements Hunk<T>
{
	private final int ox;
	private final int oy;
	private final int oz;
	private final Hunk<T> src;

	public DriftHunkView(Hunk<T> src, int ox, int oy, int oz)
	{
		this.src = src;
		this.ox = ox;
		this.oy = oy;
		this.oz = oz;
	}

	@Override
	public void setRaw(int x, int y, int z, T t)
	{
		src.setRaw(x + ox, y + oy, z + oz, t);
	}

	@Override
	public T getRaw(int x, int y, int z)
	{
		return src.getRaw(x + ox, y + oy, z + oz);
	}

	@Override
	public int getWidth()
	{
		return src.getWidth();
	}

	@Override
	public int getHeight()
	{
		return src.getHeight();
	}

	@Override
	public int getDepth()
	{
		return src.getDepth();
	}

	@Override
	public Hunk<T> getSource()
	{
		return src;
	}
}
