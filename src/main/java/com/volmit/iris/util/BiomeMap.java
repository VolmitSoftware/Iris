package com.volmit.iris.util;

import com.volmit.iris.object.IrisBiome;

public class BiomeMap
{
	private final IrisBiome[] height;

	public BiomeMap()
	{
		height = new IrisBiome[256];
	}

	public void setBiome(int x, int z, IrisBiome h)
	{
		height[x * 16 + z] = h;
	}

	public IrisBiome getBiome(int x, int z)
	{
		return height[x * 16 + z];
	}
}
