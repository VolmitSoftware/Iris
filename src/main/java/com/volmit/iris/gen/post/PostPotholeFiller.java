package com.volmit.iris.gen.post;

import org.bukkit.generator.ChunkGenerator.ChunkData;

import com.volmit.iris.gen.PostBlockTerrainProvider;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.IrisPostBlockFilter;

@Post("pothole-filler")
public class PostPotholeFiller extends IrisPostBlockFilter
{
	@DontObfuscate
	public PostPotholeFiller(PostBlockTerrainProvider gen, int phase)
	{
		super(gen, phase);
	}

	@DontObfuscate
	public PostPotholeFiller(PostBlockTerrainProvider gen)
	{
		this(gen, 0);
	}

	@Override
	public void onPost(int x, int z, int currentPostX, int currentPostZ, ChunkData currentData)
	{
		int g = 0;
		int h = highestTerrainOrCarvingBlock(x, z);
		int ha = highestTerrainOrCarvingBlock(x + 1, z);
		int hb = highestTerrainOrCarvingBlock(x, z + 1);
		int hc = highestTerrainOrCarvingBlock(x - 1, z);
		int hd = highestTerrainOrCarvingBlock(x, z - 1);
		g += ha == h + 1 ? 1 : 0;
		g += hb == h + 1 ? 1 : 0;
		g += hc == h + 1 ? 1 : 0;
		g += hd == h + 1 ? 1 : 0;

		if(g >= 3)
		{
			setPostBlock(x, h + 1, z, getPostBlock(x, h, z, currentPostX, currentPostZ, currentData), currentPostX, currentPostZ, currentData);
			updateHeight(x, z, h + 1);
		}
	}
}
