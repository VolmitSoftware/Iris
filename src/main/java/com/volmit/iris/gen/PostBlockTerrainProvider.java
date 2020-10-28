package com.volmit.iris.gen;

import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.ChunkData;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.post.PostMasterPatcher;
import com.volmit.iris.gen.scaffold.GeneratedChunk;
import com.volmit.iris.gen.scaffold.TerrainChunk;
import com.volmit.iris.gen.scaffold.TerrainTarget;
import com.volmit.iris.util.CaveResult;
import com.volmit.iris.util.IPostBlockAccess;
import com.volmit.iris.util.IrisLock;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.PrecisionStopwatch;
import com.volmit.iris.util.RNG;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class PostBlockTerrainProvider extends ParallaxTerrainProvider implements IPostBlockAccess
{
	private String postKey;
	private IrisLock postLock;
	private PostMasterPatcher patcher;

	public PostBlockTerrainProvider(TerrainTarget t, String dimensionName, int threads)
	{
		super(t, dimensionName, threads);
		setPostKey("post-" + dimensionName);
		setPostLock(new IrisLock("PostChunkGenerator"));
	}

	public void onInit(RNG rng)
	{
		super.onInit(rng);
		patcher = new PostMasterPatcher(this);
	}

	@Override
	protected GeneratedChunk onGenerate(RNG random, int x, int z, TerrainChunk terrain)
	{
		GeneratedChunk map = super.onGenerate(random, x, z, terrain);

		if(!getDimension().isPostProcessing())
		{
			return map;
		}

		int rx, i;
		PrecisionStopwatch p = PrecisionStopwatch.start();
		KList<Runnable> q = new KList<>();
		for(i = 0; i < 16; i++)
		{
			rx = (x << 4) + i;

			int rxx = rx;
			getAccelerant().queue("post", () ->
			{
				for(int j = 0; j < 16; j++)
				{
					patcher.onPost(rxx, (z << 4) + j, x, z, terrain, q);
				}
			});
		}

		getAccelerant().waitFor("post");

		for(Runnable v : q)
		{
			v.run();
		}

		p.end();
		getMetrics().getPost().put(p.getMilliseconds());
		return map;
	}

	@Override
	public void updateHeight(int x, int z, int h)
	{
		getCache().updateHeight(x, z, h);
	}

	@Override
	public BlockData getPostBlock(int x, int y, int z, int currentPostX, int currentPostZ, ChunkData currentData)
	{
		if(y > 255 || y < 0)
		{
			return null;
		}

		if(x >> 4 == currentPostX && z >> 4 == currentPostZ)
		{
			getPostLock().lock();
			BlockData d = currentData.getBlockData(x & 15, y, z & 15);
			getPostLock().unlock();
			return d;
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
