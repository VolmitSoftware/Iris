package com.volmit.iris.gen.v2;

import com.volmit.iris.gen.v2.scaffold.ArrayHunk;
import com.volmit.iris.gen.v2.scaffold.Hunk;
import com.volmit.iris.gen.v2.scaffold.HunkFace;

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
	public Hunk<T> croppedView(int x1, int y1, int z1, int x2, int y2, int z2)
	{
		return new HunkView<T>(this, x2 - x1, y2 - y1, z2 - z1, x1 + ox, y1 + oy, z1 + oz);
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
	public void insert(int offX, int offY, int offZ, Hunk<T> hunk, boolean invertY)
	{
		if(offX + (hunk.getWidth() - 1) >= w || offY + (hunk.getHeight() - 1) >= h || offZ + (hunk.getDepth() - 1) >= d || offX < 0 || offY < 0 || offZ < 0)
		{
			throw new RuntimeException("Cannot insert hunk " + hunk.getWidth() + "," + hunk.getHeight() + "," + hunk.getDepth() + " into Hunk " + w + "," + h + "," + d + " with offset " + offZ + "," + offY + "," + offZ);
		}

		for(int i = offX; i < offX + hunk.getWidth(); i++)
		{
			for(int j = offY; j < offY + hunk.getHeight(); j++)
			{
				for(int k = offZ; k < offZ + hunk.getDepth(); k++)
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

		src.set(x + ox, y + oy, z + oz, t);
	}

	@Override
	public T get(int x, int y, int z)
	{
		if(x >= w || y >= h || z >= d)
		{
			throw new RuntimeException(x + " " + y + " " + z + " is out of the bounds 0,0,0 - " + (w - 1) + "," + (h - 1) + "," + (d - 1));
		}

		return src.get(x + ox, y + oy, z + oz);
	}

	@Override
	public T getClosest(int x, int y, int z)
	{
		return src.get(x >= w ? w + ox - 1 : x + ox, y >= h ? h + oy - 1 : y + oy, z >= d ? d + oz - 1 : z + oz);
	}

	@Override
	public void fill(T t)
	{
		set(0 + ox, 0 + oy, 0 + oz, w - 1 + ox, h - 1 + oy, d - 1 + oz, t);
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
	public Hunk<T> getFace(HunkFace f)
	{
		switch(f)
		{
			case BOTTOM:
				return croppedView(0, 0, 0, getWidth() - 1, 0, getDepth() - 1);
			case EAST:
				return croppedView(getWidth() - 1, 0, 0, getWidth() - 1, getHeight() - 1, getDepth() - 1);
			case NORTH:
				return croppedView(0, 0, 0, getWidth() - 1, getHeight() - 1, 0);
			case SOUTH:
				return croppedView(0, 0, 0, 0, getHeight() - 1, getDepth() - 1);
			case TOP:
				return croppedView(0, getHeight() - 1, 0, getWidth() - 1, getHeight() - 1, getDepth() - 1);
			case WEST:
				return croppedView(0, 0, getDepth() - 1, getWidth() - 1, getHeight() - 1, getDepth() - 1);
			default:
				break;
		}

		return null;
	}

	@Override
	public Hunk<T> getSource()
	{
		return src;
	}
}
