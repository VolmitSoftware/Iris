package com.volmit.iris.gen.post;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.ChunkData;

import com.volmit.iris.gen.PostBlockChunkGenerator;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.IrisPostBlockFilter;
import com.volmit.iris.util.RNG;

@Post("wall-painter")
public class PostWallPatcher extends IrisPostBlockFilter
{
	public static final Material AIR = Material.AIR;
	private RNG rng;

	@DontObfuscate
	public PostWallPatcher(PostBlockChunkGenerator gen, int phase)
	{
		super(gen, phase);
		rng = gen.getMasterRandom().nextParallelRNG(1239456);
	}

	@DontObfuscate
	public PostWallPatcher(PostBlockChunkGenerator gen)
	{
		this(gen, 0);
	}

	@Override
	public void onPost(int x, int z, int currentPostX, int currentPostZ, ChunkData currentData)
	{
		IrisBiome biome = gen.sampleTrueBiome(x, z);
		int h, ha, hb, hc, hd;

		if(!biome.getWall().getPalette().isEmpty())
		{
			h = highestTerrainBlock(x, z);
			ha = highestTerrainBlock(x + 1, z);
			hb = highestTerrainBlock(x, z + 1);
			hc = highestTerrainBlock(x - 1, z);
			hd = highestTerrainBlock(x, z - 1);

			if(ha < h - 2 || hb < h - 2 || hc < h - 2 || hd < h - 2)
			{
				boolean brokeGround = false;
				int max = Math.abs(Math.max(h - ha, Math.max(h - hb, Math.max(h - hc, h - hd))));

				for(int i = h; i > h - max; i--)
				{
					BlockData d = biome.getWall().get(rng, x + i, i + h, z + i);

					if(d != null)
					{
						if(isAirOrWater(x, i, z, currentPostX, currentPostZ, currentData))
						{
							if(brokeGround)
							{
								return;
							}

							continue;
						}

						setPostBlock(x, i, z, d, currentPostX, currentPostZ, currentData);
						brokeGround = true;
					}
				}
			}
		}
	}
}
