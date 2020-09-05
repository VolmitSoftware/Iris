package com.volmit.iris.gen.post;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.ChunkData;

import com.volmit.iris.gen.PostBlockChunkGenerator;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.IrisPostBlockFilter;
import com.volmit.iris.util.RNG;

@Post("slabber")
public class PostSlabber extends IrisPostBlockFilter
{
	public static final Material AIR = Material.AIR;
	public static final Material WATER = Material.WATER;
	private RNG rng;

	@DontObfuscate
	public PostSlabber(PostBlockChunkGenerator gen, int phase)
	{
		super(gen, phase);
		rng = gen.getMasterRandom().nextParallelRNG(166456);
	}

	@DontObfuscate
	public PostSlabber(PostBlockChunkGenerator gen)
	{
		this(gen, 0);
	}

	@Override
	public void onPost(int x, int z, int currentPostX, int currentPostZ, ChunkData currentData)
	{
		int h = highestTerrainOrCarvingBlock(x, z);
		int ha = highestTerrainOrCarvingBlock(x + 1, z);
		int hb = highestTerrainOrCarvingBlock(x, z + 1);
		int hc = highestTerrainOrCarvingBlock(x - 1, z);
		int hd = highestTerrainOrCarvingBlock(x, z - 1);

		if((ha == h + 1 && isSolid(x + 1, ha, z, currentPostX, currentPostZ, currentData)) || (hb == h + 1 && isSolid(x, hb, z + 1, currentPostX, currentPostZ, currentData)) || (hc == h + 1 && isSolid(x - 1, hc, z, currentPostX, currentPostZ, currentData)) || (hd == h + 1 && isSolid(x, hd, z - 1, currentPostX, currentPostZ, currentData)))
		{
			BlockData d = gen.sampleTrueBiome(x, z).getSlab().get(rng, x, h, z);

			if(d != null)
			{
				if(d.getMaterial().equals(AIR))
				{
					return;
				}

				if(d.getMaterial().equals(Material.SNOW) && h + 1 <= gen.getFluidHeight())
				{
					return;
				}

				if(isSnowLayer(x, h, z, currentPostX, currentPostZ, currentData))
				{
					return;
				}

				if(isAirOrWater(x, h + 1, z, currentPostX, currentPostZ, currentData))
				{
					setPostBlock(x, h + 1, z, d, currentPostX, currentPostZ, currentData);
					updateHeight(x, z, h + 1);
				}
			}
		}
	}
}
