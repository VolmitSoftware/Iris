package com.volmit.iris.scaffold.hunk.view;

import com.volmit.iris.scaffold.hunk.Hunk;
import org.bukkit.Chunk;
import org.bukkit.block.data.BlockData;

import com.volmit.iris.Iris;

public class ChunkHunkView implements Hunk<BlockData>
{
	private final Chunk chunk;

	public ChunkHunkView(Chunk chunk)
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
	public void setRaw(int x, int y, int z, BlockData t)
	{
		if(t == null)
		{
			return;
		}

		Iris.edit.set(chunk.getWorld(), x + (chunk.getX() * 16), y, z + (chunk.getZ() * 16), t);
	}

	@Override
	public BlockData getRaw(int x, int y, int z)
	{
		return Iris.edit.get(chunk.getWorld(), x + (chunk.getX() * 16), y, z + (chunk.getZ() * 16));
	}
}
