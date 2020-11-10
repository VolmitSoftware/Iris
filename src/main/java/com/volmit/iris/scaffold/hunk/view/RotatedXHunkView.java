package com.volmit.iris.scaffold.hunk.view;

import com.volmit.iris.scaffold.hunk.Hunk;

public class RotatedXHunkView<T> implements Hunk<T>
{
	private final Hunk<T> src;
	private final double sin;
	private final double cos;

	public RotatedXHunkView(Hunk<T> src, double deg)
	{
		this.src = src;
		this.sin = Math.sin(Math.toRadians(deg));
		this.cos = Math.cos(Math.toRadians(deg));
	}

	@Override
	public void setRaw(int x, int y, int z, T t)
	{
		int yc = (int) Math.round(cos * (getHeight() / 2) - sin * (getDepth() / 2));
		int zc = (int) Math.round(sin * (getHeight() / 2) + cos * (getDepth() / 2));
		src.setIfExists(x, 
				(int) Math.round(cos * (y-yc) - sin * (z-zc))-yc,
				(int) Math.round(sin * y-yc + cos * (z-zc))-zc,
				t);
	}

	@Override
	public T getRaw(int x, int y, int z)
	{
		int yc = (int) Math.round(cos * (getHeight() / 2) - sin * (getDepth() / 2));
		int zc = (int) Math.round(sin * (getHeight() / 2) + cos * (getDepth() / 2));
		return src.getIfExists(x,
				(int) Math.round(cos * (y-yc) - sin * (z-zc))-yc,
				(int) Math.round(sin * y-yc + cos * (z-zc))-zc
				);
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
