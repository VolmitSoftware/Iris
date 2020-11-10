package com.volmit.iris.scaffold.hunk.view;

import com.volmit.iris.scaffold.hunk.Hunk;

public class HunkView<T> implements Hunk<T>
{
	private final int ox;
	private final int oy;
	private final int oz;
	private final int w;
	private final int h;
	private final int d;
	private final Hunk<T> src;

	public HunkView(Hunk<T> src)
	{
		this(src, src.getWidth(), src.getHeight(), src.getDepth());
	}

	public HunkView(Hunk<T> src, int w, int h, int d)
	{
		this(src, w, h, d, 0, 0, 0);
	}

	public HunkView(Hunk<T> src, int w, int h, int d, int ox, int oy, int oz)
	{
		this.src = src;
		this.w = w;
		this.h = h;
		this.d = d;
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
		return w;
	}

	@Override
	public int getDepth()
	{
		return d;
	}

	@Override
	public int getHeight()
	{
		return h;
	}

	@Override
	public Hunk<T> getSource()
	{
		return src;
	}
}
