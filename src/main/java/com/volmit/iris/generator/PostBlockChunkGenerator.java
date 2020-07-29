package com.volmit.iris.generator;

import java.util.concurrent.locks.ReentrantLock;

import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import com.volmit.iris.Iris;
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
	private KList<IrisPostBlockFilter> availableFilters;
	private String postKey;
	private ReentrantLock lock;
	private int minPhase;
	private int maxPhase;

	public PostBlockChunkGenerator(String dimensionName, int threads)
	{
		super(dimensionName, threads);
		availableFilters = new KList<>();
		postKey = "post-" + dimensionName;
		lock = new ReentrantLock();
	}

	public void onInit(World world, RNG rng)
	{
		super.onInit(world, rng);

		for(Class<? extends IrisPostBlockFilter> i : Iris.postProcessors)
		{
			try
			{
				availableFilters.add(i.getConstructor(PostBlockChunkGenerator.class).newInstance(this));
			}

			catch(Throwable e)
			{
				Iris.error("Failed to initialize post processor: " + i.getCanonicalName());
				fail(e);
			}
		}
	}

	@Override
	protected void onGenerate(RNG random, int x, int z, ChunkData data, BiomeGrid grid)
	{
		super.onGenerate(random, x, z, data, grid);

		if(!getDimension().isPostProcessing())
		{
			return;
		}

		KList<IrisPostBlockFilter> filters = getDimension().getPostBlockProcessors(this);
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

	public IrisPostBlockFilter createProcessor(String processor, int phase)
	{
		for(IrisPostBlockFilter i : availableFilters)
		{
			if(i.getKey().equals(processor))
			{
				try
				{
					return i.getClass().getConstructor(PostBlockChunkGenerator.class, int.class).newInstance(this, phase);
				}

				catch(Throwable e)
				{
					Iris.error("Failed initialize find post processor: " + processor);
					fail(e);
				}
			}
		}

		Iris.error("Failed to find post processor: " + processor);
		fail(new RuntimeException("Failed to find post processor: " + processor));
		return null;
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
