package com.volmit.iris.util;

import java.util.Arrays;

public class HeightMap
{
	private final byte[] height;

	public HeightMap()
	{
		height = new byte[256];
		Arrays.fill(height, Byte.MIN_VALUE);
	}

	public void setHeight(int x, int z, int h)
	{
		height[x * 16 + z] = (byte) (h + Byte.MIN_VALUE);
	}

	public int getHeight(int x, int z)
	{
		return height[x * 16 + z] - Byte.MIN_VALUE;
	}
}
