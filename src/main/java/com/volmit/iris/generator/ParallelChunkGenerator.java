package com.volmit.iris.generator;

import java.util.concurrent.locks.ReentrantLock;

import org.bukkit.World;

import com.volmit.iris.Iris;
import com.volmit.iris.object.atomics.AtomicSliver;
import com.volmit.iris.object.atomics.AtomicSliverMap;
import com.volmit.iris.util.BiomeMap;
import com.volmit.iris.util.GroupedExecutor;
import com.volmit.iris.util.HeightMap;
import com.volmit.iris.util.PrecisionStopwatch;
import com.volmit.iris.util.RNG;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class ParallelChunkGenerator extends BiomeChunkGenerator
{
	private GroupedExecutor accelerant;
	private int threads;
	protected boolean unsafe;
	protected int cacheX;
	protected int cacheZ;
	private ReentrantLock genlock;
	protected boolean cachingAllowed;

	public ParallelChunkGenerator(String dimensionName, int threads)
	{
		super(dimensionName);
		unsafe = false;
		cacheX = 0;
		cacheZ = 0;
		this.threads = threads;
		genlock = new ReentrantLock();
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

	protected abstract void onGenerateColumn(int cx, int cz, int wx, int wz, int x, int z, AtomicSliver sliver, BiomeMap biomeMap, int onlyY);

	protected void onGenerateColumn(int cx, int cz, int wx, int wz, int x, int z, AtomicSliver sliver, BiomeMap biomeMap)
	{
		onGenerateColumn(cx, cz, wx, wz, x, z, sliver, biomeMap, -1);
	}

	protected abstract int onSampleColumnHeight(int cx, int cz, int wx, int wz, int x, int z);

	protected abstract void onPostGenerate(RNG random, int x, int z, ChunkData data, BiomeGrid grid, HeightMap height, BiomeMap biomeMap);

	protected int sampleHeight(int x, int z)
	{
		return onSampleColumnHeight(x >> 4, z >> 4, x, z, x & 15, z & 15);
	}

	protected void onGenerate(RNG random, int x, int z, ChunkData data, BiomeGrid grid)
	{
		genlock.lock();
		cacheX = x;
		cacheZ = z;
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

		setCachingAllowed(true);
		setUnsafe(true);
		accelerant.waitFor(key);
		setUnsafe(false);
		setCachingAllowed(false);
		map.write(data, grid, height);
		getMetrics().getTerrain().put(p.getMilliseconds());
		p = PrecisionStopwatch.start();
		onPostGenerate(random, x, z, data, grid, height, biomeMap);
		genlock.unlock();
	}

	protected void onClose()
	{
		accelerant.close();
	}

	public void onInit(World world, RNG rng)
	{
		super.onInit(world, rng);
		changeThreadCount(threads);
	}

	public boolean isSafe()
	{
		return !unsafe;
	}

	@Override
	public boolean isParallelCapable()
	{
		return false;
	}
}
