package com.volmit.iris.gen;

import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.ChunkData;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.post.PostFloatingNibDeleter;
import com.volmit.iris.gen.post.PostFoliageCleaner;
import com.volmit.iris.gen.post.PostNibSmoother;
import com.volmit.iris.gen.post.PostPotholeFiller;
import com.volmit.iris.gen.post.PostSlabber;
import com.volmit.iris.gen.post.PostWallPatcher;
import com.volmit.iris.gen.post.PostWaterlogger;
import com.volmit.iris.gen.scaffold.TerrainChunk;
import com.volmit.iris.gen.scaffold.TerrainTarget;
import com.volmit.iris.util.CaveResult;
import com.volmit.iris.util.IPostBlockAccess;
import com.volmit.iris.util.IrisLock;
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
	private String postKey;
	private IrisLock postLock;
	private int minPhase;
	private int maxPhase;

	public PostBlockChunkGenerator(TerrainTarget t, String dimensionName, int threads)
	{
		super(t, dimensionName, threads);
		setPostKey("post-" + dimensionName);
		setPostLock(new IrisLock("PostChunkGenerator"));
	}

	public void onInit(RNG rng)
	{
		super.onInit(rng);
	}

	@Override
	protected void onGenerate(RNG random, int x, int z, TerrainChunk terrain)
	{
		super.onGenerate(random, x, z, terrain);

		if(!getDimension().isPostProcessing())
		{
			return;
		}

		KList<IrisPostBlockFilter> filters = getDimension().getPostBlockProcessors(this);

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
					int hh = h;

					getAccelerant().queue("post", () ->
					{
						for(IrisPostBlockFilter f : filters)
						{
							if(f.getPhase() == hh)
							{
								f.onPost(rxx, rzz, x, z, terrain);
							}
						}
					});
				}
			}

			getAccelerant().waitFor("post");

			for(IrisPostBlockFilter f : filters)
			{
				if(f.getPhase() == h)
				{
					while(f.getQueue().size() > 0)
					{
						try
						{
							f.getQueue().pop().run();
						}

						catch(Throwable e)
						{

						}
					}
				}
			}
		}

		p.end();
		getMetrics().getPost().put(p.getMilliseconds());
	}

	public IrisPostBlockFilter createProcessor(String processor, int phase)
	{
		if(processor.equals("floating-block-remover"))
		{
			return new PostFloatingNibDeleter(this, phase);
		}

		if(processor.equals("foliage-cleaner"))
		{
			return new PostFoliageCleaner(this, phase);
		}

		if(processor.equals("nib-smoother"))
		{
			return new PostNibSmoother(this, phase);
		}

		if(processor.equals("pothole-filler"))
		{
			return new PostPotholeFiller(this, phase);
		}

		if(processor.equals("slabber"))
		{
			return new PostSlabber(this, phase);
		}

		if(processor.equals("wall-painter"))
		{
			return new PostWallPatcher(this, phase);
		}

		if(processor.equals("waterlogger"))
		{
			return new PostWaterlogger(this, phase);
		}

		Iris.error("Failed to find post processor: " + processor);
		fail(new RuntimeException("Failed to find post processor: " + processor));
		return null;
	}

	@Override
	public void updateHeight(int x, int z, int h)
	{
		getCache().updateHeight(x, z, h);
	}

	@Override
	public BlockData getPostBlock(int x, int y, int z, int currentPostX, int currentPostZ, ChunkData currentData)
	{
		if(x >> 4 == currentPostX && z >> 4 == currentPostZ)
		{
			getPostLock().lock();
			BlockData d = currentData.getBlockData(x & 15, y, z & 15);
			getPostLock().unlock();
			return d == null ? AIR : d;
		}

		return sampleSliver(x, z).get(y);
	}

	@Override
	public void setPostBlock(int x, int y, int z, BlockData d, int currentPostX, int currentPostZ, ChunkData currentData)
	{
		if(x >> 4 == currentPostX && z >> 4 == currentPostZ)
		{
			getPostLock().lock();
			currentData.setBlock(x & 15, y, z & 15, d);
			getPostLock().unlock();
		}

		else
		{
			Iris.warn("Post Block Overdraw: " + currentPostX + "," + currentPostZ + " into " + (x >> 4) + ", " + (z >> 4));
		}
	}

	@Override
	public int highestTerrainOrFluidBlock(int x, int z)
	{
		return (int) Math.round(getTerrainWaterHeight(x, z));
	}

	@Override
	public int highestTerrainBlock(int x, int z)
	{
		return (int) Math.round(getTerrainHeight(x, z));
	}

	@Override
	public KList<CaveResult> caveFloors(int x, int z)
	{
		return getCaves(x, z);
	}
}
