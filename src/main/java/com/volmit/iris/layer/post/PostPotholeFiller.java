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
		int h = highestTerrainBlock(x, z);
		int ha = highestTerrainBlock(x + 1, z);
		int hb = highestTerrainBlock(x, z + 1);
		int hc = highestTerrainBlock(x - 1, z);
		int hd = highestTerrainBlock(x, z - 1);

		if(ha == h + 1 && hb == h + 1 && hc == h + 1 && hd == h + 1)
		{
			setPostBlock(x, h + 1, z, getPostBlock(x, h, z));
		}
	}
}
