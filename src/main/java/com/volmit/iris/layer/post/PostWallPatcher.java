package ninja.bytecode.iris.layer.post;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import ninja.bytecode.iris.generator.PostBlockChunkGenerator;
import ninja.bytecode.iris.object.IrisBiome;
import ninja.bytecode.iris.util.IrisPostBlockFilter;
import ninja.bytecode.iris.util.RNG;

public class PostWallPatcher extends IrisPostBlockFilter
{
	public static final Material AIR = Material.AIR;
	private RNG rng;

	public PostWallPatcher(PostBlockChunkGenerator gen)
	{
		super(gen);
		rng = gen.getMasterRandom().nextParallelRNG(1239456);
	}

	@Override
	public void onPost(int x, int z)
	{
		IrisBiome biome = gen.sampleTrueBiome(x, z).getBiome();

		if(!biome.getWall().getPalette().isEmpty())
		{
			int h = highestTerrainBlock(x, z);
			int ha = highestTerrainBlock(x + 1, z);
			int hb = highestTerrainBlock(x, z + 1);
			int hc = highestTerrainBlock(x - 1, z);
			int hd = highestTerrainBlock(x, z - 1);

			if(ha < h - 2 || hb < h - 2 || hc < h - 2 || hd < h - 2)
			{
				int max = Math.abs(Math.max(h - ha, Math.max(h - hb, Math.max(h - hc, h - hd))));

				for(int i = h; i > h - max; i--)
				{
					BlockData d = biome.getWall().get(rng, x + i, i + h, z + i);

					if(d != null)
					{
						if(d.getMaterial().equals(AIR))
						{
							continue;
						}

						setPostBlock(x, i, z, d);
					}
				}
			}
		}
	}
}
