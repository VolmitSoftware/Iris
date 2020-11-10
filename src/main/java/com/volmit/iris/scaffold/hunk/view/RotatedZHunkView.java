package com.volmit.iris.scaffold.hunk.view;

import com.volmit.iris.scaffold.hunk.Hunk;

public class RotatedZHunkView<T> implements Hunk<T>
{
	private final Hunk<T> src;
	private final double sin;
	private final double cos;

	public RotatedZHunkView(Hunk<T> src, double deg)
	{
		this.src = src;
		this.sin = Math.sin(Math.toRadians(deg));
		this.cos = Math.cos(Math.toRadians(deg));
	}

	@Override
	public void setRaw(int x, int y, int z, T t)
	{
		int xc = (int) Math.round(cos * (getWidth() / 2) - sin * (getHeight() / 2));
		int yc = (int) Math.round(sin * (getWidth() / 2) + cos * (getHeight() / 2));
		src.setIfExists((int) Math.round(cos * (x - xc) - sin * (y - yc)) - xc, (int) Math.round(sin * (x - xc) + cos * (y - yc)) - yc, z, t);
	}

	@Override
	public T getRaw(int x, int y, int z)
	{
		int xc = (int) Math.round(cos * (getWidth() / 2) - sin * (getHeight() / 2));
		int yc = (int) Math.round(sin * (getWidth() / 2) + cos * (getHeight() / 2));
		return src.getIfExists((int) Math.round(cos * (x - xc) - sin * (y - yc)) - xc, 
				(int) Math.round(sin * (x - xc) + cos * (y - yc)) - yc
				, z);
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
