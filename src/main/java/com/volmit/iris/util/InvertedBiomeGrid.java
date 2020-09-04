package com.volmit.iris.util;

import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator.BiomeGrid;

import com.volmit.iris.Iris;

public class InvertedBiomeGrid implements BiomeGrid
{
	private BiomeGrid grid;

	public InvertedBiomeGrid(BiomeGrid real)
	{
		this.grid = real;
	}

	@SuppressWarnings("deprecation")
	@Override
	public Biome getBiome(int arg0, int arg1)
	{
		return grid.getBiome(arg0, arg1);
	}

	@Override
	public Biome getBiome(int arg0, int arg1, int arg2)
	{
		if(!Iris.biome3d)
		{
			return getBiome(arg0, arg2);
		}

		return grid.getBiome(arg0, 255 - arg1, arg2);
	}

	@SuppressWarnings("deprecation")
	@Override
	public void setBiome(int arg0, int arg1, Biome arg2)
	{
		grid.setBiome(arg0, arg1, arg2);
	}

	@Override
	public void setBiome(int arg0, int arg1, int arg2, Biome arg3)
	{
		if(!Iris.biome3d)
		{
			setBiome(arg0, arg2, arg3);
			return;
		}

		grid.setBiome(arg0, 255 - arg1, arg2, arg3);
	}
}
