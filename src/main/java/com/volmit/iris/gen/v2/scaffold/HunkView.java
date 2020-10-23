package com.volmit.iris.gen.v2.scaffold;

public class HunkView<T> implements Hunk<T>
{
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
		this.src = src;
		this.w = w;
		this.h = h;
		this.d = d;
	}

	@Override
	public ArrayHunk<T> crop(int x1, int y1, int z1, int x2, int y2, int z2)
	{
		ArrayHunk<T> h = new ArrayHunk<T>(x2 - x1, y2 - y1, z2 - z1);

		for(int i = x1; i < x2; i++)
		{
			for(int j = y1; j < y2; j++)
			{
				for(int k = z1; k < z2; k++)
				{
					h.set(i - x1, j - y1, k - z1, get(i, j, k));
				}
			}
		}

		return h;
	}

	@Override
	public void insert(int offX, int offY, int offZ, ArrayHunk<T> hunk, boolean invertY)
	{
		if(offX + (hunk.getW() - 1) >= w || offY + (hunk.getH() - 1) >= h || offZ + (hunk.getD() - 1) >= d || offX < 0 || offY < 0 || offZ < 0)
		{
			throw new RuntimeException("Cannot insert hunk " + hunk.getW() + "," + hunk.getH() + "," + hunk.getD() + " into Hunk " + w + "," + h + "," + d + " with offset " + offZ + "," + offY + "," + offZ);
		}

		for(int i = offX; i < offX + hunk.getW(); i++)
		{
			for(int j = offY; j < offY + hunk.getH(); j++)
			{
				for(int k = offZ; k < offZ + hunk.getD(); k++)
				{
					set(i, j, k, hunk.get(i - offX, j - offY, k - offZ));
				}
			}
		}
	}

	@Override
	public void set(int x1, int y1, int z1, int x2, int y2, int z2, T t)
	{
		if(x1 >= w || y1 >= h || z1 >= d || x2 >= w || y2 >= h || z2 >= d)
		{
			throw new RuntimeException(x1 + "-" + x2 + " " + y1 + "-" + y2 + " " + z1 + "-" + z2 + " is out of the bounds 0,0,0 - " + (w - 1) + "," + (h - 1) + "," + (d - 1));
		}

		src.set(x1, y1, z1, x2, y2, z2, t);
	}

	@Override
	public void set(int x, int y, int z, T t)
	{
		if(x >= w || y >= h || z >= d)
		{
			throw new RuntimeException(x + " " + y + " " + z + " is out of the bounds 0,0,0 - " + (w - 1) + "," + (h - 1) + "," + (d - 1));
		}

		src.set(x, y, z, t);
	}

	@Override
	public T get(int x, int y, int z)
	{
		if(x >= w || y >= h || z >= d)
		{
			throw new RuntimeException(x + " " + y + " " + z + " is out of the bounds 0,0,0 - " + (w - 1) + "," + (h - 1) + "," + (d - 1));
		}

		return src.get(x, y, z);
	}

	@Override
	public T getClosest(int x, int y, int z)
	{
		return src.get(x >= w ? w - 1 : x, y >= h ? h - 1 : y, z >= d ? d - 1 : z);
	}

	@Override
	public void fill(T t)
	{
		set(0, 0, 0, w - 1, h - 1, d - 1, t);
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

}
