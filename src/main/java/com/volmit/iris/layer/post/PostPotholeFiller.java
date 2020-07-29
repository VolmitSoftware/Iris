package com.volmit.iris.layer.post;

import com.volmit.iris.generator.PostBlockChunkGenerator;
import com.volmit.iris.util.IrisPostBlockFilter;

public class PostPotholeFiller extends IrisPostBlockFilter
{
	public PostPotholeFiller(PostBlockChunkGenerator gen)
	{
		super(gen);
	}

	@Override
	public void onPost(int x, int z)
	{
		int g = 0;
		int h = highestTerrainBlock(x, z);
		int ha = highestTerrainBlock(x + 1, z);
		int hb = highestTerrainBlock(x, z + 1);
		int hc = highestTerrainBlock(x - 1, z);
		int hd = highestTerrainBlock(x, z - 1);
		g += ha == h + 1 ? 1 : 0;
		g += hb == h + 1 ? 1 : 0;
		g += hc == h + 1 ? 1 : 0;
		g += hd == h + 1 ? 1 : 0;

		if(g >= 3)
		{
			setPostBlock(x, h + 1, z, getPostBlock(x, h, z));
			updateHeight(x, z, h + 1);
		}
	}
}
