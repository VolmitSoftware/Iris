package com.volmit.iris.gen.post;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.ChunkData;

import com.volmit.iris.gen.PostBlockChunkGenerator;
import com.volmit.iris.util.B;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.IrisPostBlockFilter;

@Post("foliage-cleaner")
public class PostFoliageCleaner extends IrisPostBlockFilter
{
	public static final BlockData AIR = B.get("AIR");

	@DontObfuscate
	public PostFoliageCleaner(PostBlockChunkGenerator gen, int phase)
	{
		super(gen, phase);
	}

	@DontObfuscate
	public PostFoliageCleaner(PostBlockChunkGenerator gen)
	{
		this(gen, 0);
	}

	@Override
	public void onPost(int x, int z, int currentPostX, int currentPostZ, ChunkData currentData)
	{
		int h = highestTerrainOrCarvingBlock(x, z);
		BlockData b = getPostBlock(x, h + 1, z, currentPostX, currentPostZ, currentData);

		if(B.isFoliage(b) || b.getMaterial().equals(Material.DEAD_BUSH))
		{
			Material onto = getPostBlock(x, h, z, currentPostX, currentPostZ, currentData).getMaterial();

			if(!B.canPlaceOnto(b.getMaterial(), onto))
			{
				setPostBlock(x, h + 1, z, AIR, currentPostX, currentPostZ, currentData);
			}
		}
	}
}
