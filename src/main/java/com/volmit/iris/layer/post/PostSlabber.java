package com.volmit.iris.layer.post;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import com.volmit.iris.generator.PostBlockChunkGenerator;
import com.volmit.iris.util.IrisPostBlockFilter;
import com.volmit.iris.util.RNG;

@Post("slabber")
public class PostSlabber extends IrisPostBlockFilter
{
	public static final Material AIR = Material.AIR;
	public static final Material WATER = Material.WATER;
	private RNG rng;

	public PostSlabber(PostBlockChunkGenerator gen, int phase)
	{
		super(gen, phase);
		rng = gen.getMasterRandom().nextParallelRNG(166456);
	}

	public PostSlabber(PostBlockChunkGenerator gen)
	{
		this(gen, 0);
	}

	@Override
	public void onPost(int x, int z)
	{
		int h = highestTerrainBlock(x, z);
		int ha = highestTerrainBlock(x + 1, z);
		int hb = highestTerrainBlock(x, z + 1);
		int hc = highestTerrainBlock(x - 1, z);
		int hd = highestTerrainBlock(x, z - 1);

		if((ha == h + 1 && isSolid(x + 1, ha, z)) || (hb == h + 1 && isSolid(x, hb, z + 1)) || (hc == h + 1 && isSolid(x - 1, hc, z)) || (hd == h + 1 && isSolid(x, hd, z - 1)))
		{
			BlockData d = gen.sampleTrueBiome(x, z).getBiome().getSlab().get(rng, x, h, z);

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

				if(isSnowLayer(x, h, z))
				{
					return;
				}

				if(isAirOrWater(x, h + 2, z))
				{
					queue(() ->
					{
						setPostBlock(x, h + 1, z, d);
						updateHeight(x, z, h + 1);
					});
				}
			}
		}
	}
}
