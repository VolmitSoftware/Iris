package ninja.bytecode.iris.generator;

import java.util.concurrent.locks.ReentrantLock;

import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.layer.post.PostNippleSmoother;
import ninja.bytecode.iris.layer.post.PostPotholeFiller;
import ninja.bytecode.iris.object.IrisDimension;
import ninja.bytecode.iris.util.IPostBlockAccess;
import ninja.bytecode.iris.util.IrisPostBlockFilter;
import ninja.bytecode.iris.util.RNG;
import ninja.bytecode.shuriken.collections.KList;

public abstract class PostBlockChunkGenerator extends ParallaxChunkGenerator implements IPostBlockAccess
{
	protected boolean generatingCeiling = false;
	protected boolean ceilingCached = false;
	protected IrisDimension cacheCeiling = null;
	protected IrisDimension cacheFloor = null;
	private int currentPostX;
	private int currentPostZ;
	private ChunkData currentData;
	private KList<IrisPostBlockFilter> filters;
	private String postKey;
	private ReentrantLock lock;

	public PostBlockChunkGenerator(String dimensionName, int threads)
	{
		super(dimensionName, threads);
		filters = new KList<>();
		postKey = "post-" + dimensionName;
		lock = new ReentrantLock();
	}

	public void onInit(World world, RNG rng)
	{
		super.onInit(world, rng);
		filters.add(new PostNippleSmoother(this));
		filters.add(new PostPotholeFiller(this));
	}

	@Override
	protected void onGenerate(RNG random, int x, int z, ChunkData data, BiomeGrid grid)
	{
		super.onGenerate(random, x, z, data, grid);
		currentData = data;
		currentPostX = x;
		currentPostZ = z;
		int rx, i, j;

		for(i = 0; i < 16; i++)
		{
			rx = (x * 16) + i;

			for(j = 0; j < 16; j++)
			{
				int rxx = rx;
				int rzz = (z * 16) + j;

				getAccelerant().queue(postKey, () ->
				{
					for(IrisPostBlockFilter f : filters)
					{
						f.onPost(rxx, rzz);
					}
				});
			}
		}

		getAccelerant().waitFor(postKey);
	}

	@Override
	public BlockData getPostBlock(int x, int y, int z)
	{
		if(x >> 4 == currentPostX && z >> 4 == currentPostZ)
		{
			lock.lock();
			BlockData d = currentData.getBlockData(x & 15, y, z & 15);
			lock.unlock();
			return d == null ? AIR : d;
		}

		return sampleSliver(x, z).get(y);
	}

	@Override
	public void setPostBlock(int x, int y, int z, BlockData d)
	{
		if(x >> 4 == currentPostX && z >> 4 == currentPostZ)
		{
			lock.lock();
			currentData.setBlock(x & 15, y, z & 15, d);
			lock.unlock();
		}

		else
		{
			Iris.warn("Post Block Overdraw: " + currentPostX + "," + currentPostZ + " into " + (x >> 4) + ", " + (z >> 4));
		}
	}

	@Override
	public int highestTerrainOrFluidBlock(int x, int z)
	{
		return getHighest(x, z, false);
	}

	@Override
	public int highestTerrainBlock(int x, int z)
	{
		return getHighest(x, z, true);
	}
}
