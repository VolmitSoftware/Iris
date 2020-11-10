package com.volmit.iris.scaffold.hunk.view;

import com.volmit.iris.scaffold.hunk.Hunk;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.ChunkData;

public class ChunkDataHunkView implements Hunk<BlockData>
{
	private final ChunkData chunk;

	public ChunkDataHunkView(ChunkData chunk)
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
		return chunk.getMaxHeight();
	}

	@Override
	public void set(int x1, int y1, int z1, int x2, int y2, int z2, BlockData t)
	{
		if(t == null)
		{
			return;
		}

		enforceBounds(x1, y1, z1, x2 - x1, y2 - y1, z2 - z1);
		chunk.setRegion(x1, y1, z1, x2, y2, z2, t);
	}

	@Override
	public void setRaw(int x, int y, int z, BlockData t)
	{
		if(t == null)
		{
			return;
		}

		chunk.setBlock(x, y, z, t);
	}

	@Override
	public BlockData getRaw(int x, int y, int z)
	{
		return chunk.getBlockData(x, y, z);
	}
}
