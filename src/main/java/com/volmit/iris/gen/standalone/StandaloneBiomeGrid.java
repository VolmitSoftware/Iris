package com.volmit.iris.gen.standalone;

import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator.BiomeGrid;

import com.volmit.iris.util.BiomeStorage;

public class StandaloneBiomeGrid implements BiomeGrid
{
	private final BiomeStorage storage;

	public StandaloneBiomeGrid()
	{
		storage = new BiomeStorage();
	}

	@Override
	public Biome getBiome(int x, int z)
	{
		throw new UnsupportedOperationException("Use GetBiome x, y, z");
	}

	@Override
	public Biome getBiome(int x, int y, int z)
	{
		return storage.getBiome(x, y, z);
	}

	@Override
	public void setBiome(int arg0, int arg1, Biome arg2)
	{
		throw new UnsupportedOperationException("Use SetBiome x, y, z, b");
	}

	@Override
	public void setBiome(int x, int y, int z, Biome b)
	{
		storage.setBiome(x, y, z, b);
	}
}
