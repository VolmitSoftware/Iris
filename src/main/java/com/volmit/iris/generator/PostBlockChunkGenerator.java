package com.volmit.iris.generator;

import java.util.concurrent.locks.ReentrantLock;

import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import com.volmit.iris.Iris;
import com.volmit.iris.layer.post.PostFloatingNibDeleter;
import com.volmit.iris.layer.post.PostNibSmoother;
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

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
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
	private int minPhase;
	private int maxPhase;

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
		filters.add(new PostNibSmoother(this));
		filters.add(new PostFloatingNibDeleter(this));
		filters.add(new PostPotholeFiller(this));
		filters.add(new PostWallPatcher(this));
		filters.add(new PostSlabber(this));
		filters.add(new PostWaterlogger(this, 2));

		setMinPhase(0);
		setMaxPhase(0);

		for(IrisPostBlockFilter i : filters)
		{
			setMinPhase(Math.min(getMinPhase(), i.getPhase()));
			setMaxPhase(Math.max(getMaxPhase(), i.getPhase()));
		}

		Iris.info("Post Processing: " + filters.size() + " filters. Phases: " + getMinPhase() + " - " + getMaxPhase());
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

		for(int h = getMinPhase(); h <= getMaxPhase(); h++)
		{
			for(i = 0; i < 16; i++)
			{
				rx = (x << 4) + i;

				for(j = 0; j < 16; j++)
				{
					int rxx = rx;
					int rzz = (z << 4) + j;

					for(IrisPostBlockFilter f : filters)
					{
						if(f.getPhase() == h)
						{
							f.onPost(rxx, rzz);
						}
					}
				}
			}

			for(IrisPostBlockFilter f : filters)
			{
				if(f.getPhase() == h)
				{
					while(f.getQueue().size() > 0)
					{
						f.getQueue().pop().run();
					}
				}
			}
		}

		p.end();
		getMetrics().getPost().put(p.getMilliseconds());
	}

	@Override
	public void updateHeight(int x, int z, int h)
	{
		if(x >> 4 == currentPostX && z >> 4 == currentPostZ)
		{
			getCacheHeightMap()[((z & 15) << 4) | (x & 15)] = h;
		}

		else
		{
			Iris.error("Invalid Heightmap set! Chunk Currently at " + currentPostX + "," + currentPostZ + ". Attempted to place at " + (x >> 4) + " " + (z >> 4) + " which is bad.");
		}
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
