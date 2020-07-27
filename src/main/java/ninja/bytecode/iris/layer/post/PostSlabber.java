package ninja.bytecode.iris.layer.post;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;

import ninja.bytecode.iris.generator.PostBlockChunkGenerator;
import ninja.bytecode.iris.util.IrisPostBlockFilter;
import ninja.bytecode.iris.util.RNG;

public class PostSlabber extends IrisPostBlockFilter
{
	public static final Material AIR = Material.AIR;
	public static final Material WATER = Material.WATER;
	private RNG rng;

	public PostSlabber(PostBlockChunkGenerator gen)
	{
		super(gen);
		rng = gen.getMasterRandom().nextParallelRNG(1239456);
	}

	@Override
	public void onPost(int x, int z)
	{
		int h = highestTerrainBlock(x, z);

		if(highestTerrainBlock(x + 1, z) == h + 1 || highestTerrainBlock(x, z + 1) == h + 1 || highestTerrainBlock(x - 1, z) == h + 1 || highestTerrainBlock(x, z - 1) == h + 1)
		{
			BlockData d = gen.sampleTrueBiome(x, z).getBiome().getSlab().get(rng, x, h, z);
			if(d != null)
			{
				if(d.getMaterial().equals(AIR))
				{
					return;
				}

				if(d instanceof Waterlogged)
				{
					((Waterlogged) d).setWaterlogged(getPostBlock(x, h + 1, z).getMaterial().equals(Material.WATER));
				}

				if(getPostBlock(x, h + 2, z).getMaterial().equals(AIR) || getPostBlock(x, h + 2, z).getMaterial().equals(WATER))
				{
					setPostBlock(x, h + 1, z, d);
				}
			}
		}
	}
}
