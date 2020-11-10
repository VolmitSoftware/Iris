package com.volmit.iris.scaffold.hunk.view;

import com.volmit.iris.scaffold.hunk.Hunk;

public class InvertedHunkView<T> implements Hunk<T>
{
	private final Hunk<T> src;

	public InvertedHunkView(Hunk<T> src)
	{
		this.src = src;
	}

	@Override
	public void setRaw(int x, int y, int z, T t)
	{
		src.setRaw(x, (getHeight() -1) - y, z, t);
	}

	@Override
	public T getRaw(int x, int y, int z)
	{
		return src.getRaw(x, y, z);
	}

	@Override
	public int getWidth()
	{
		return src.getWidth();
	}

	@Override
	public int getDepth()
	{
		return src.getDepth();
	}

	@Override
	public int getHeight()
	{
		return src.getHeight();
	}

	@Override
	public Hunk<T> getSource()
	{
		return src;
	}
}
