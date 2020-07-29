package com.volmit.iris.generator;

import java.util.concurrent.locks.ReentrantLock;

import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import com.volmit.iris.Iris;
import com.volmit.iris.layer.post.PostFloatingNippleDeleter;
import com.volmit.iris.layer.post.PostNippleSmoother;
import com.volmit.iris.layer.post.PostPotholeFiller;
import com.volmit.iris.layer.post.PostSlabber;
import com.volmit.iris.layer.post.PostWallPatcher;
import com.volmit.iris.layer.post.PostWaterlogger;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.util.CaveResult;
import com.volmit.iris.util.IPostBlockAccess;
import com.volmit.iris.util.IrisPostBlockFilter;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.PrecisionStopwatch;
import com.volmit.iris.util.RNG;

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
		filters.add(new PostFloatingNippleDeleter(this));
		filters.add(new PostPotholeFiller(this));
		filters.add(new PostSlabber(this));
		filters.add(new PostWallPatcher(this));
		filters.add(new PostWaterlogger(this));
	}

	@Override
	protected void onGenerate(RNG random, int x, int z, ChunkData data, BiomeGrid grid)
	{
		super.onGenerate(random, x, z, data, grid);

		if(!getDimension().isPostProcess())
		{
			return;
		}

		currentData = data;
		currentPostX = x;
		currentPostZ = z;
		int rx, i, j;

		PrecisionStopwatch p = PrecisionStopwatch.start();

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
						int rxxx = rxx;
						int rzzx = rzz;

						f.onPost(rxxx, rzzx);
					}
				});
			}
		}

		getAccelerant().waitFor(postKey);
		p.end();
		getMetrics().getPost().put(p.getMilliseconds());
	}

	@Override
	public void updateHeight(int x, int z, int h)
	{
		getCacheHeightMap()[(z << 4) | x] = h;
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

	@Override
	public KList<CaveResult> caveFloors(int x, int z)
	{
		return getCaves(x, z);
	}
}
