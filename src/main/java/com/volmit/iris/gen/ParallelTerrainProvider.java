package com.volmit.iris.gen;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.atomics.AtomicSliver;
import com.volmit.iris.gen.atomics.AtomicSliverMap;
import com.volmit.iris.gen.scaffold.TerrainChunk;
import com.volmit.iris.gen.scaffold.TerrainTarget;
import com.volmit.iris.util.BiomeMap;
import com.volmit.iris.util.GroupedExecutor;
import com.volmit.iris.util.HeightMap;
import com.volmit.iris.util.PrecisionStopwatch;
import com.volmit.iris.util.RNG;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class ParallelTerrainProvider extends DimensionalTerrainProvider
{
	private GroupedExecutor accelerant;
	private int threads;
	private boolean cachingAllowed;

	public ParallelTerrainProvider(TerrainTarget t, String dimensionName, int threads)
	{
		super(t, dimensionName);
		setThreads(threads);
		setCachingAllowed(false);
	}

	public void changeThreadCount(int tc)
	{
		setThreads(tc);
		GroupedExecutor e = getAccelerant();
		setAccelerant(new GroupedExecutor(threads, Thread.MAX_PRIORITY, "Iris Generator - " + getTarget().getName()));
		;
		Iris.executors.add(getAccelerant());

		if(e != null)
		{
			e.close();
		}

		Iris.info("Thread Count changed to " + getThreads());
	}

	protected abstract void onGenerateColumn(int cx, int cz, int wx, int wz, int x, int z, AtomicSliver sliver, BiomeMap biomeMap, boolean sampled);

	protected void onGenerateColumn(int cx, int cz, int wx, int wz, int x, int z, AtomicSliver sliver, BiomeMap biomeMap)
	{
		onGenerateColumn(cx, cz, wx, wz, x, z, sliver, biomeMap, false);
	}

	protected abstract int onSampleColumnHeight(int cx, int cz, int wx, int wz, int x, int z);

	protected abstract void onPostGenerate(RNG random, int x, int z, TerrainChunk terrain, HeightMap height, BiomeMap biomeMap, AtomicSliverMap map);

	protected abstract void onPreGenerate(RNG random, int x, int z, TerrainChunk terrain, HeightMap height, BiomeMap biomeMap, AtomicSliverMap map);

	protected int sampleHeight(int x, int z)
	{
		return onSampleColumnHeight(x >> 4, z >> 4, x, z, x & 15, z & 15);
	}

	protected void onGenerate(RNG random, int x, int z, TerrainChunk terrain)
	{
		getCache().targetChunk(x, z);
		PrecisionStopwatch p = PrecisionStopwatch.start();
		AtomicSliverMap map = new AtomicSliverMap();
		HeightMap height = new HeightMap();
		String key = "c" + x + "," + z;
		BiomeMap biomeMap = new BiomeMap();
		int ii, jj;

		onPreGenerate(random, x, z, terrain, height, biomeMap, map);

		for(ii = 0; ii < 16; ii++)
		{
			int i = ii;
			int wx = (x * 16) + i;

			for(jj = 0; jj < 16; jj++)
			{
				int j = jj;
				int wz = (z * 16) + j;
				AtomicSliver sliver = map.getSliver(i, j);

				getAccelerant().queue(key, () ->
				{
					try
					{
						onGenerateColumn(x, z, wx, wz, i, j, sliver, biomeMap);
					}

					catch(Throwable e)
					{
						fail(e);
					}
				});
			}
		}

		accelerant.waitFor(key);

		map.write(terrain, terrain, height);
		getMetrics().getTerrain().put(p.getMilliseconds());
		p = PrecisionStopwatch.start();
		onPostGenerate(random, x, z, terrain, height, biomeMap, map);
	}

	protected void onClose()
	{
		getAccelerant().close();
		Iris.executors.remove(accelerant);
	}

	public void onInit(RNG rng)
	{
		super.onInit(rng);
		changeThreadCount(getThreads());
	}

	@Override
	public boolean isParallelCapable()
	{
		return false;
	}
}
