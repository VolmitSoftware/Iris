package com.volmit.iris.generator.atomics;

import org.bouncycastle.util.Arrays;

import com.volmit.iris.util.Function3;
import com.volmit.iris.util.Supplier2;
import com.volmit.iris.util.Supplier3;

import lombok.Data;

@Data
public class Hunk<T>
{
	protected final int w;
	protected final int h;
	protected final int d;
	protected final T[] data;

	@SuppressWarnings("unchecked")
	public Hunk(int w, int h, int d)
	{
		this.w = w;
		this.h = h;
		this.d = d;
		data = (T[]) new Object[w * h * d];
	}

	/**
	 * Create a new hunk from a section of this hunk.
	 * 
	 * 
	 * @param x1
	 *            The min x (inclusive)
	 * @param y1
	 *            The min y (inclusive)
	 * @param z1
	 *            The min z (inclusive)
	 * @param x2
	 *            The max x (exclusive)
	 * @param y2
	 *            The max y (exclusive)
	 * @param z2
	 *            The max z (exclusive)
	 * @return the new hunk (x2-x1, y2-y1, z2-z1)
	 */
	public Hunk<T> crop(int x1, int y1, int z1, int x2, int y2, int z2)
	{
		Hunk<T> h = new Hunk<T>(x2 - x1, y2 - y1, z2 - z1);

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

	/**
	 * Insert a hunk into this one with an offset the inserted hunk
	 * 
	 * @param offX
	 *            the offset from zero for x
	 * @param offY
	 *            the offset from zero for y
	 * @param offZ
	 *            the offset from zero for z
	 * @param hunk
	 *            the hunk to insert
	 */
	public void insert(int offX, int offY, int offZ, Hunk<T> hunk)
	{
		insert(offX, offY, offZ, hunk, false);
	}

	/**
	 * Insert a hunk into this one
	 * 
	 * @param hunk
	 *            the hunk to insert
	 */
	public void insert(Hunk<T> hunk)
	{
		insert(0, 0, 0, hunk, false);
	}

	/**
	 * Insert a hunk into this one
	 * 
	 * @param hunk
	 *            the hunk to insert
	 * @param inverted
	 *            invert the inserted hunk or not
	 */
	public void insert(Hunk<T> hunk, boolean inverted)
	{
		insert(0, 0, 0, hunk, inverted);
	}

	/**
	 * Insert a hunk into this one with an offset and possibly inverting the y of
	 * the inserted hunk
	 * 
	 * @param offX
	 *            the offset from zero for x
	 * @param offY
	 *            the offset from zero for y
	 * @param offZ
	 *            the offset from zero for z
	 * @param hunk
	 *            the hunk to insert
	 * @param invertY
	 *            should the inserted hunk be inverted
	 */
	public void insert(int offX, int offY, int offZ, Hunk<T> hunk, boolean invertY)
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

	public void set(int x, int z, int y1, int y2, T t)
	{
		set(x, x, y1, y2, z, z, t);
	}

	public void set(int x1, int y1, int z1, int x2, int y2, int z2, T t)
	{
		for(int i = x1; i <= x2; i++)
		{
			for(int j = y1; j <= y2; j++)
			{
				for(int k = z1; k <= z2; k++)
				{
					set(i, j, k, t);
				}
			}
		}
	}

	public void set(int x, int y, int z, T t)
	{
		data[index(x, y, z)] = t;
	}

	public T get(int x, int y, int z)
	{
		return data[index(x, y, z)];
	}

	public void setInvertedY(int x, int y, int z, T t)
	{
		data[index(x, h - y, z)] = t;
	}

	public T getInvertedY(int x, int y, int z)
	{
		return data[index(x, h - y, z)];
	}

	protected int index(int x, int y, int z)
	{
		if(x >= w || y >= h || z >= d)
		{
			throw new RuntimeException(x + " " + y + " " + z + " is out of the bounds 0,0,0 - " + (w - 1) + "," + (h - 1) + "," + (d - 1));
		}

		return (z * w * h) + (y * w) + x;
	}

	public void fill(int ox, int oy, int oz, Function3<Integer, Integer, Integer, T> f)
	{
		for(int i = ox; i < ox + getW(); i++)
		{
			for(int j = oy; j < oy + getH(); j++)
			{
				for(int k = oz; k < oz + getD(); k++)
				{
					set(i - ox, j - oy, k - oz, f.apply(i, j, k));
				}
			}
		}
	}

	public void forEach(Supplier3<Integer, Integer, Integer> t)
	{
		for(int i = 0; i < getW(); i++)
		{
			for(int j = 0; j < getH(); j++)
			{
				for(int k = 0; k < getD(); k++)
				{
					t.get(i, j, k);
				}
			}
		}
	}

	public void forEachXZ(Supplier2<Integer, Integer> t)
	{
		for(int i = 0; i < getW(); i++)
		{
			for(int k = 0; k < getD(); k++)
			{
				t.get(i, k);
			}
		}
	}

	public void fill(T t)
	{
		Arrays.fill(data, t);
	}

	@SafeVarargs
	public static <T> Hunk<T> combined(T defaultNode, Hunk<T>... hunks)
	{
		int w = 0;
		int h = 0;
		int d = 0;

		for(Hunk<T> i : hunks)
		{
			w = Math.max(w, i.getW());
			h = Math.max(h, i.getH());
			d = Math.max(d, i.getD());
		}

		Hunk<T> b = new Hunk<T>(w, h, d);
		b.fill(defaultNode);

		for(Hunk<T> i : hunks)
		{
			b.insert(i);
		}

		return b;
	}

	@SafeVarargs
	public static <T> Hunk<T> combined(Hunk<T>... hunks)
	{
		int w = 0;
		int h = 0;
		int d = 0;

		for(Hunk<T> i : hunks)
		{
			w = Math.max(w, i.getW());
			h = Math.max(h, i.getH());
			d = Math.max(d, i.getD());
		}

		Hunk<T> b = new Hunk<T>(w, h, d);

		for(Hunk<T> i : hunks)
		{
			b.insert(i);
		}

		return b;
	}
}
