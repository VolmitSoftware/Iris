package com.volmit.iris.gen.atomics;

public class HeightHunk extends Hunk<Byte>
{
	public HeightHunk(int w, int d)
	{
		super(w, 1, d);
	}

	public void setHeight(int x, int y, int z)
	{
		set(x, 0, z, (byte) (y + Byte.MIN_VALUE));
	}

	public int getHeight(int x, int z)
	{
		return get(x, 0, z) - Byte.MIN_VALUE;
	}

	@SafeVarargs
	public static HeightHunk combined(Byte defaultNode, HeightHunk... hunks)
	{
		int w = 0;
		int d = 0;

		for(HeightHunk i : hunks)
		{
			w = Math.max(w, i.getW());
			d = Math.max(d, i.getD());
		}

		HeightHunk b = new HeightHunk(w, d);
		b.fill((byte) (defaultNode + Byte.MIN_VALUE));

		for(HeightHunk i : hunks)
		{
			b.insert(i);
		}

		return b;
	}

	@SafeVarargs
	public static HeightHunk combined(HeightHunk... hunks)
	{
		int w = 0;
		int d = 0;

		for(HeightHunk i : hunks)
		{
			w = Math.max(w, i.getW());
			d = Math.max(d, i.getD());
		}

		HeightHunk b = new HeightHunk(w, d);

		for(HeightHunk i : hunks)
		{
			b.insert(i);
		}

		return b;
	}
}
