package com.volmit.iris.gen;

import org.bukkit.World;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.atomics.AtomicSliver;
import com.volmit.iris.gen.atomics.AtomicSliverMap;
import com.volmit.iris.util.BiomeMap;
import com.volmit.iris.util.GroupedExecutor;
import com.volmit.iris.util.HeightMap;
import com.volmit.iris.util.PrecisionStopwatch;
import com.volmit.iris.util.RNG;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class ParallelChunkGenerator extends DimensionChunkGenerator
{
	private GroupedExecutor accelerant;
	private int threads;
	protected int cacheX;
	protected int cacheZ;
	protected boolean cachingAllowed;

	public ParallelChunkGenerator(String dimensionName, int threads)
	{
		super(dimensionName);
		cacheX = 0;
		cacheZ = 0;
		this.threads = threads;
		cachingAllowed = false;
	}

	public void changeThreadCount(int tc)
	{
		threads = tc;
		GroupedExecutor e = accelerant;
		accelerant = new GroupedExecutor(threads, Thread.NORM_PRIORITY, "Iris Generator - " + world.getName());
		Iris.executors.add(accelerant);

		if(e != null)
		{
			e.close();
		}
	}

	protected abstract void onGenerateColumn(int cx, int cz, int wx, int wz, int x, int z, AtomicSliver sliver, BiomeMap biomeMap, boolean sampled);

	protected void onGenerateColumn(int cx, int cz, int wx, int wz, int x, int z, AtomicSliver sliver, BiomeMap biomeMap)
	{
		onGenerateColumn(cx, cz, wx, wz, x, z, sliver, biomeMap, false);
	}

	protected abstract int onSampleColumnHeight(int cx, int cz, int wx, int wz, int x, int z);

	protected abstract void onPostGenerate(RNG random, int x, int z, ChunkData data, BiomeGrid grid, HeightMap height, BiomeMap biomeMap, AtomicSliverMap map);

	protected int sampleHeight(int x, int z)
	{
		return onSampleColumnHeight(x >> 4, z >> 4, x, z, x & 15, z & 15);
	}

	protected void onGenerate(RNG random, int x, int z, ChunkData data, BiomeGrid grid)
	{
		cacheX = x;
		cacheZ = z;
		getCache().targetChunk(cacheX, cacheZ);
		PrecisionStopwatch p = PrecisionStopwatch.start();
		AtomicSliverMap map = new AtomicSliverMap();
		HeightMap height = new HeightMap();
		String key = "c" + x + "," + z;
		BiomeMap biomeMap = new BiomeMap();
		int ii, jj;

		for(ii = 0; ii < 16; ii++)
		{
			int i = ii;
			int wx = (x * 16) + i;

			for(jj = 0; jj < 16; jj++)
			{
				int j = jj;
				int wz = (z * 16) + j;
				AtomicSliver sliver = map.getSliver(i, j);

				accelerant.queue(key, () ->
				{
					onGenerateColumn(x, z, wx, wz, i, j, sliver, biomeMap);
				});
			}
		}

		accelerant.waitFor(key);
		map.write(data, grid, height);
		getMetrics().getTerrain().put(p.getMilliseconds());
		p = PrecisionStopwatch.start();
		onPostGenerate(random, x, z, data, grid, height, biomeMap, map);
	}

	protected void onClose()
	{
		accelerant.close();
		Iris.executors.remove(accelerant);
	}

	public void onInit(World world, RNG rng)
	{
		super.onInit(world, rng);
		changeThreadCount(threads);
	}

	@Override
	public boolean isParallelCapable()
	{
		return false;
	}
}
