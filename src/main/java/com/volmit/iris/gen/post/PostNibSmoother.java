package com.volmit.iris.layer.post;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import com.volmit.iris.generator.PostBlockChunkGenerator;
import com.volmit.iris.util.IrisPostBlockFilter;

@Post("nib-smoother")
public class PostNibSmoother extends IrisPostBlockFilter
{
	public PostNibSmoother(PostBlockChunkGenerator gen, int phase)
	{
		super(gen, phase);
	}

	public PostNibSmoother(PostBlockChunkGenerator gen)
	{
		this(gen, 0);
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
		g += ha == h - 1 ? 1 : 0;
		g += hb == h - 1 ? 1 : 0;
		g += hc == h - 1 ? 1 : 0;
		g += hd == h - 1 ? 1 : 0;

		if(g >= 3)
		{
			BlockData bc = getPostBlock(x, h, z);
			BlockData b = getPostBlock(x, h + 1, z);
			Material m = bc.getMaterial();

			if(m.isSolid())
			{
				setPostBlock(x, h, z, b);
				updateHeight(x, z, h - 1);
			}
		}
	}
}
