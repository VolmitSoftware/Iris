package com.volmit.iris.scaffold.hunk.view;

import com.volmit.iris.scaffold.hunk.Hunk;
import org.bukkit.Chunk;
import org.bukkit.block.Biome;

import com.volmit.iris.Iris;

public class ChunkBiomeHunkView implements Hunk<Biome>
{
	private final Chunk chunk;

	public ChunkBiomeHunkView(Chunk chunk)
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
		return chunk.getWorld().getMaxHeight();
	}

	@Override
	public void setRaw(int x, int y, int z, Biome t)
	{
		if(t == null)
		{
			return;
		}

		Iris.edit.setBiome(chunk.getWorld(), x + (chunk.getX() * 16), y, z + (chunk.getZ() * 16), t);
	}

	@Override
	public Biome getRaw(int x, int y, int z)
	{
		return Iris.edit.getBiome(chunk.getWorld(), x + (chunk.getX() * 16), y, z + (chunk.getZ() * 16));
	}
}
