package com.volmit.iris.layer.post;

import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;

import com.volmit.iris.Iris;
import com.volmit.iris.generator.PostBlockChunkGenerator;
import com.volmit.iris.util.IrisPostBlockFilter;

public class PostWaterlogger extends IrisPostBlockFilter
{
	public PostWaterlogger(PostBlockChunkGenerator gen)
	{
		super(gen);
	}

	@Override
	public void onPost(int x, int z)
	{
		int h = highestTerrainBlock(x, z);
		BlockData b = getPostBlock(x, h, z);

		if(b instanceof Waterlogged)
		{
			Waterlogged ww = (Waterlogged) b;
			boolean w = ww.isWaterlogged();

			if(isWater(x, h + 1, z))
			{
				ww.setWaterlogged(true);
			}

			else if(h < 98)
			{
				Iris.info("Data is " + getPostBlock(x, h + 1, z).getAsString());
			}

			else if(isWater(x + 1, h, z) || isWater(x - 1, h, z) || isWater(x, h, z + 1) || isWater(x, h, z - 1))
			{
				ww.setWaterlogged(true);
			}

			else
			{
				ww.setWaterlogged(false);
			}

			if(ww.isWaterlogged() != w)
			{
				setPostBlock(x, h, z, ww);
			}
		}
	}
}
