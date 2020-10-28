package com.volmit.iris.gen.v2;

import java.util.function.Predicate;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

import com.volmit.iris.gen.v2.scaffold.hunk.Hunk;
import com.volmit.iris.gen.v2.scaffold.layer.ProceduralStream;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisDecorator;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.util.PrecisionStopwatch;
import com.volmit.iris.util.RNG;

public class IrisTerrainGenerator
{
	private long seed;
	private IrisDataManager data;
	private IrisDimension dimension;
	private IrisComplex complex;
	private RNG rng;
	private int parallelism;
	private static final Predicate<BlockData> PREDICATE_SOLID = (b) -> b != null && !b.getMaterial().isAir() && !b.getMaterial().equals(Material.WATER) && !b.getMaterial().equals(Material.LAVA);

	public IrisTerrainGenerator(long seed, IrisDimension dimension, IrisDataManager data)
	{
		parallelism = 8;
		this.seed = seed;
		this.rng = new RNG(seed);
		complex = new IrisComplex();
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
		t.fill2D(h, x * 16, z * 16, v, parallelism);
	}

	private <V, T> void fill2DYLock(ProceduralStream<T> t, Hunk<V> h, double x, double z, ProceduralStream<V> v)
	{
		t.fill2DYLocked(h, x * 16, z * 16, v, parallelism);
	}

	public void generateDecorations(int x, int z, Hunk<BlockData> blocks)
	{
		int bx = (x * 16);
		int bz = (z * 16);

		blocks.iterateSurfaces2D(parallelism, PREDICATE_SOLID, (ax, az, xx, zz, top, bottom, lastBottom, h) ->
		{
			int rx = bx + xx + ax;
			int rz = bz + zz + az;
			RNG g = rng.nextParallelRNG(rx).nextParallelRNG(rz);
			IrisBiome b = complex.getTrueBiomeStream().get(rx, rz);
			boolean surface = lastBottom == -1;
			int floor = top + 1;
			int ceiling = lastBottom == -1 ? floor < dimension.getFluidHeight() ? dimension.getFluidHeight() : blocks.getHeight() : lastBottom - 1;
			int height = ceiling - floor;

			if(height < 2)
			{
				return;
			}

			IrisDecorator deco = complex.getTerrainSurfaceDecoration().get(rx, rz);

			if(deco != null)
			{
				if(deco.isStacking())
				{
					int stack = Math.min(g.i(deco.getStackMin(), deco.getStackMax()), height);

					for(int i = 0; i < stack; i++)
					{
						h.set(ax, i + floor, az, deco.getBlockData100(b, rng, rx - i, rz + i, data));
					}

					if(deco.getTopPalette().isNotEmpty())
					{
						h.set(ax, stack + floor - 1, az, deco.getBlockDataForTop(b, rng, rx - stack, rz + stack, data));
					}
				}

				else
				{
					h.set(ax, floor, az, deco.getBlockData100(b, rng, rx, rz, data));
				}
			}

			if(!surface)
			{
				IrisDecorator cdeco = complex.getTerrainCeilingDecoration().get(rx, rz);

				if(cdeco != null)
				{
					if(cdeco.isStacking())
					{
						int stack = Math.min(g.i(cdeco.getStackMin(), cdeco.getStackMax()), height);

						for(int i = 0; i < stack; i++)
						{
							h.set(ax, -i + ceiling, az, cdeco.getBlockData100(b, rng, rx - i, rz + i, data));
						}

						if(cdeco.getTopPalette().isNotEmpty())
						{
							h.set(ax, -stack + ceiling - 1, az, cdeco.getBlockDataForTop(b, rng, rx - stack, rz + stack, data));
						}
					}

					else
					{
						h.set(ax, ceiling, az, cdeco.getBlockData100(b, rng, rx, rz, data));
					}
				}
			}
		});
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
		generateTerrain(x, z, blocks);
		generateBiome(x, z, biomes);
		generateDecorations(x, z, blocks);
	}
}
