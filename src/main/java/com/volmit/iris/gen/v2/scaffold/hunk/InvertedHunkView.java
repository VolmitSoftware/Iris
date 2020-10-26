package com.volmit.iris.gen.v2.scaffold.hunk;

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
		src.setRaw(x, getHeight() - y, z, t);
	}

	@Override
	public T getRaw(int x, int y, int z)
	{
		return src.getRaw(x, getHeight() - y, z);
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
