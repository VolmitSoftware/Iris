package com.volmit.iris.gen.v2;

import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.v2.scaffold.Hunk;
import com.volmit.iris.gen.v2.scaffold.layer.ProceduralStream;
import com.volmit.iris.gen.v2.scaffold.multicore.MultiBurst;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.util.KMap;
import com.volmit.iris.util.M;
import com.volmit.iris.util.PrecisionStopwatch;
import com.volmit.iris.util.RollingSequence;

public class IrisTerrainGenerator
{
	private long seed;
	private MultiBurst burster;
	private IrisDataManager data;
	private IrisDimension dimension;
	private IrisComplex complex;
	private int parallelism;

	public IrisTerrainGenerator(long seed, IrisDimension dimension, IrisDataManager data)
	{
		this.seed = seed;
		complex = new IrisComplex();
		parallelism = 6;
		burster = new MultiBurst((parallelism * parallelism) * 4);
		this.data = data;
		this.dimension = dimension;

		flash();
	}

	public void flash()
	{
		complex.flash(seed, dimension, data);
	}

	private <V, T> void fill2D(ProceduralStream<T> t, Hunk<V> h, double x, double z, ProceduralStream<V> v)
	{
		if(parallelism <= 1)
		{
			t.fill2D(h, x * 16, z * 16, v);
		}

		else
		{
			t.fill2DParallel(burster.burst(parallelism * parallelism), parallelism, h, x * 16, z * 16, v);
		}
	}

	private <V, T> void fill2DYLock(ProceduralStream<T> t, Hunk<V> h, double x, double z, ProceduralStream<V> v)
	{
		if(parallelism <= 1)
		{
			t.fill2DYLocked(h, x * 16, z * 16, v);
		}

		else
		{
			t.fill2DParallelYLocked(burster.burst(parallelism * parallelism), parallelism, h, x * 16, z * 16, v);
		}
	}

	public void generateTerrain(int x, int z, Hunk<BlockData> blocks)
	{
		PrecisionStopwatch p = PrecisionStopwatch.start();
		fill2D(complex.getHeightFluidStream(), blocks, x, z, complex.getTerrainStream());
		p.end();
	}

	public void generateBiome(int x, int z, Hunk<Biome> blocks)
	{
		PrecisionStopwatch p = PrecisionStopwatch.start();
		fill2DYLock(complex.getMaxHeightStream(), blocks, x, z, complex.getTrueBiomeDerivativeStream());
		p.end();
	}

	public void generate(int x, int z, Hunk<BlockData> blocks, Hunk<Biome> biomes)
	{
		// RNG rng = new RNG((((long) x) << 32) | (z & 0xffffffffL));
		generateTerrain(x, z, blocks);
		generateBiome(x, z, biomes);
	}
}
