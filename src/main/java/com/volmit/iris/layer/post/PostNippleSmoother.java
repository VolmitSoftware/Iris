package com.volmit.iris.layer.post;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import com.volmit.iris.generator.PostBlockChunkGenerator;
import com.volmit.iris.util.IrisPostBlockFilter;

public class PostNippleSmoother extends IrisPostBlockFilter
{
	public PostNippleSmoother(PostBlockChunkGenerator gen)
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

		if(ha == h - 1 && hb == h - 1 && hc == h - 1 && hd == h - 1)
		{
			BlockData bc = getPostBlock(x, h, z);
			BlockData b = getPostBlock(x, h + 1, z);
			Material m = bc.getMaterial();

			if(m.isSolid())
			{
				setPostBlock(x, h, z, b);
			}
		}
	}
}
