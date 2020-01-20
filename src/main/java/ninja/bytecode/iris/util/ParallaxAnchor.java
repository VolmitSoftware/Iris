package ninja.bytecode.iris.util;

import ninja.bytecode.iris.pack.IrisBiome;

public class ParallaxAnchor
{
	private final int height;
	private final int water;
	private final IrisBiome biome;
	private final AtomicChunkData data;

	public ParallaxAnchor(int height, int water, IrisBiome biome, AtomicChunkData data)
	{
		this.height = height;
		this.water = water;
		this.biome = biome;
		this.data = data;
	}

	public AtomicChunkData getData()
	{
		return data;
	}

	public int getWater()
	{
		return water;
	}

	public int getHeight()
	{
		return height;
	}

	public int getWaterHeight()
	{
		return water;
	}

	public IrisBiome getBiome()
	{
		return biome;
	}
}
