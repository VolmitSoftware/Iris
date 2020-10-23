package com.volmit.iris.gen.v2.scaffold;

import org.bouncycastle.util.Arrays;

import lombok.Data;

@Data
public class ArrayHunk<T> implements Hunk<T>
{
	private final int w;
	private final int h;
	private final int d;
	private final T[] data;

	@SuppressWarnings("unchecked")
	public ArrayHunk(int w, int h, int d)
	{
		if(w * h * d < 0)
		{
			throw new RuntimeException("Unsupported size " + w + " " + h + " " + d);
		}

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
	@Override
	public void insert(int offX, int offY, int offZ, ArrayHunk<T> hunk)
	{
		insert(offX, offY, offZ, hunk, false);
	}

	/**
	 * Insert a hunk into this one
	 * 
	 * @param hunk
	 *            the hunk to insert
	 */
	@Override
	public void insert(ArrayHunk<T> hunk)
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
	@Override
	public void insert(ArrayHunk<T> hunk, boolean inverted)
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

	/**
	 * Set a region
	 * 
	 * @param x1
	 *            inclusive 1st x
	 * @param y1
	 *            inclusive 1st y
	 * @param z1
	 *            inclusive 1st z
	 * @param x2
	 *            inclusive 2nd x
	 * @param y2
	 *            inclusive 2nd y
	 * @param z2
	 *            inclusive 2nd z
	 * @param t
	 *            the value to set
	 */
	@Override
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

	/**
	 * Set a value at the given position
	 * 
	 * @param x
	 *            the x
	 * @param y
	 *            the y
	 * @param z
	 *            the z
	 * @param t
	 *            the value
	 */
	@Override
	public void set(int x, int y, int z, T t)
	{
		data[index(x, y, z)] = t;
	}

	/**
	 * Get a value at the given position
	 * 
	 * @param x
	 *            the x
	 * @param y
	 *            the y
	 * @param z
	 *            the z
	 * @return the value or null
	 */
	@Override
	public T get(int x, int y, int z)
	{
		return data[index(x, y, z)];
	}

	/**
	 * Get the value to the closest valid position
	 * 
	 * @param x
	 *            the x
	 * @param y
	 *            the y
	 * @param z
	 *            the z
	 * @return the value closest to the border of the hunk
	 */
	@Override
	public T getClosest(int x, int y, int z)
	{
		return data[index(x >= w ? w - 1 : x, y >= h ? h - 1 : y, z >= d ? d - 1 : z)];
	}

	protected int index(int x, int y, int z)
	{
		if(x >= w || y >= h || z >= d)
		{
			throw new RuntimeException(x + " " + y + " " + z + " is out of the bounds 0,0,0 - " + (w - 1) + "," + (h - 1) + "," + (d - 1));
		}

		return (z * w * h) + (y * w) + x;
	}

	@Override
	public void fill(T t)
	{
		Arrays.fill(data, t);
	}

	@SafeVarargs
	public static <T> ArrayHunk<T> combined(ArrayHunk<T>... hunks)
	{
		int w = 0;
		int h = 0;
		int d = 0;

		for(ArrayHunk<T> i : hunks)
		{
			w = Math.max(w, i.getW());
			h = Math.max(h, i.getH());
			d = Math.max(d, i.getD());
		}

		ArrayHunk<T> b = new ArrayHunk<T>(w, h, d);

		for(ArrayHunk<T> i : hunks)
		{
			b.insert(i);
		}

		return b;
	}

	@Override
	public int getWidth()
	{
		return w;
	}

	@Override
	public int getDepth()
	{
		return h;
	}

	@Override
	public int getHeight()
	{
		return d;
	}
}
