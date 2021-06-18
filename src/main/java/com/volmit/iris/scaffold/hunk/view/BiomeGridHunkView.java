package com.volmit.iris.scaffold.hunk.view;

import com.volmit.iris.scaffold.hunk.Hunk;
import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator.BiomeGrid;

public class BiomeGridHunkView implements Hunk<Biome>
{
	private final BiomeGrid chunk;

	public BiomeGridHunkView(BiomeGrid chunk)
	{
		this.chunk = chunk;
	}

	@Override
	public int getWidth()
	{
		return 16;
	}

	@Override
	public int getDepth()
	{
		return 16;
	}

	@Override
	public int getHeight()
	{
		// TODO: WARNING HEIGHT
		return 256;
	}

	@Override
	public void setRaw(int x, int y, int z, Biome t)
	{
		chunk.setBiome(x, y, z, t);
	}

	@Override
	public Biome getRaw(int x, int y, int z)
	{
		return chunk.getBiome(x, y, z);
	}
}
