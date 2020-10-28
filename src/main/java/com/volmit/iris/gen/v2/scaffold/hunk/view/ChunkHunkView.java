package com.volmit.iris.gen.v2.scaffold.hunk.view;

import com.volmit.iris.gen.v2.scaffold.hunk.Hunk;
import org.bukkit.Chunk;
import org.bukkit.block.data.BlockData;

import com.volmit.iris.Iris;
import com.volmit.iris.util.FastBlockData;

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
		Iris.edit.set(chunk.getWorld(), x + (chunk.getX() * 16), y, z + (chunk.getZ() * 16), FastBlockData.of(t));
	}

	@Override
	public BlockData getRaw(int x, int y, int z)
	{
		return Iris.edit.get(chunk.getWorld(), x + (chunk.getX() * 16), y, z + (chunk.getZ() * 16)).getBlockData();
	}
}
