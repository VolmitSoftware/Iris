package com.volmit.iris.gen.post;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.generator.ChunkGenerator.ChunkData;

import com.volmit.iris.gen.PostBlockChunkGenerator;
import com.volmit.iris.util.B;
import com.volmit.iris.util.IrisPostBlockFilter;

@Post("waterlogger")
public class PostWaterlogger extends IrisPostBlockFilter
{
	private static final BlockData WATER = B.getBlockData("WATER");

	public PostWaterlogger(PostBlockChunkGenerator gen, int phase)
	{
		super(gen, phase);
	}

	public PostWaterlogger(PostBlockChunkGenerator gen)
	{
		super(gen);
	}

	@Override
	public void onPost(int x, int z, int currentPostX, int currentPostZ, ChunkData currentData)
	{
		int h = highestTerrainBlock(x, z);
		BlockData b = getPostBlock(x, h, z, currentPostX, currentPostZ, currentData);

		if(b instanceof Waterlogged)
		{
			Waterlogged ww = (Waterlogged) b;

			if(ww.isWaterlogged())
			{
				return;
			}

			if(isWaterOrWaterlogged(x, h + 1, z, currentPostX, currentPostZ, currentData) && !ww.isWaterlogged())
			{
				ww.setWaterlogged(true);
				setPostBlock(x, h, z, ww, currentPostX, currentPostZ, currentData);
			}

			else if(!ww.isWaterlogged() && (isWaterOrWaterlogged(x + 1, h, z, currentPostX, currentPostZ, currentData) || isWaterOrWaterlogged(x - 1, h, z, currentPostX, currentPostZ, currentData) || isWaterOrWaterlogged(x, h, z + 1, currentPostX, currentPostZ, currentData) || isWaterOrWaterlogged(x, h, z - 1, currentPostX, currentPostZ, currentData)))
			{
				ww.setWaterlogged(true);
				setPostBlock(x, h, z, ww, currentPostX, currentPostZ, currentData);
			}
		}

		else if(b.getMaterial().equals(Material.AIR) && h <= gen.getFluidHeight())
		{
			if((isWaterOrWaterlogged(x + 1, h, z, currentPostX, currentPostZ, currentData) || isWaterOrWaterlogged(x - 1, h, z, currentPostX, currentPostZ, currentData) || isWaterOrWaterlogged(x, h, z + 1, currentPostX, currentPostZ, currentData) || isWaterOrWaterlogged(x, h, z - 1, currentPostX, currentPostZ, currentData)))
			{
				setPostBlock(x, h, z, WATER, currentPostX, currentPostZ, currentData);
			}
		}
	}
}
