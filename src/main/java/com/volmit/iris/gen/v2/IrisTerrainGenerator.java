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
import com.volmit.iris.util.IO;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.PrecisionStopwatch;
import com.volmit.iris.util.RNG;

public class IrisTerrainGenerator
{
	public static void main(String[] a)
	{
		Hunk<Double> v = Hunk.newArrayHunk(7, 7, 7);
		v.fill(0D);
		KList<Double> vx = new KList<>();
		v.compute3D((x, y, z, h) ->
		{
			h.iterate(0, (xx, yy, zz) ->
			{
				double vv = 0;
				synchronized(vx)
				{
					vv = (double) vx.indexOfAddIfNeeded((double) (IO.hash(x + " " + y + " " + z).hashCode()));

					h.set(xx, yy, zz, vv);
				}
			});
		});

		System.out.println("=================== X Z =====================");
		for(int i = 0; i < v.getWidth(); i++)
		{
			for(int j = 0; j < v.getDepth(); j++)
			{
				System.out.print(((int) v.get(i, 0, j).doubleValue()) + " ");
			}

			System.out.println();
		}

		System.out.println("=================== X Y =====================");
		for(int i = 0; i < v.getHeight(); i++)
		{
			for(int j = 0; j < v.getWidth(); j++)
			{
				System.out.print(((int) v.get(j, i, 0).doubleValue()) + " ");
			}

			System.out.println();
		}

		System.out.println("=================== Z Y =====================");
		for(int i = 0; i < v.getHeight(); i++)
		{
			for(int j = 0; j < v.getDepth(); j++)
			{
				System.out.print(((int) v.get(0, i, j).doubleValue()) + " ");
			}

			System.out.println();
		}
	}

	private long seed;
	private IrisDataManager data;
	private IrisDimension dimension;
	private IrisComplex complex;
	private static final Predicate<BlockData> PREDICATE_SOLID = (b) -> b != null && !b.getMaterial().isAir() && !b.getMaterial().equals(Material.WATER) && !b.getMaterial().equals(Material.LAVA);

	public IrisTerrainGenerator(long seed, IrisDimension dimension, IrisDataManager data)
	{
		this.seed = seed;
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
		t.fill2D(h, x * 16, z * 16, v, 8);
	}

	private <V, T> void fill2DYLock(ProceduralStream<T> t, Hunk<V> h, double x, double z, ProceduralStream<V> v)
	{
		t.fill2DYLocked(h, x * 16, z * 16, v, 8);
	}

	public void generateDecorations(int x, int z, Hunk<BlockData> blocks)
	{
		RNG rng = complex.getRngStream().get(x, z);
		int bx = (x * 16);
		int bz = (z * 16);

		blocks.iterateSurfaces2D(PREDICATE_SOLID, (xx, zz, top, bottom, lastBottom, h) ->
		{
			int rx = bx + xx;
			int rz = bz + zz;
			RNG g = rng.nextParallelRNG(rx).nextParallelRNG(rz);
			IrisBiome b = complex.getTrueBiomeStream().get(rx, rz);
			boolean surface = lastBottom == -1;
			int floor = top + 1;
			int ceiling = lastBottom == -1 ? blocks.getHeight() : lastBottom - 1;
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
						h.set(xx, i + floor, zz, deco.getBlockData100(b, rng, rx - i, rz + i, data).getBlockData());
					}

					if(deco.getTopPalette().isNotEmpty())
					{
						h.set(xx, stack + floor - 1, zz, deco.getBlockDataForTop(b, rng, rx - stack, rz + stack, data).getBlockData());
					}
				}

				else
				{
					h.set(xx, floor, zz, deco.getBlockData100(b, rng, rx, rz, data).getBlockData());
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
							h.set(xx, -i + ceiling, zz, cdeco.getBlockData100(b, rng, rx - i, rz + i, data).getBlockData());
						}

						if(cdeco.getTopPalette().isNotEmpty())
						{
							h.set(xx, -stack + ceiling - 1, zz, cdeco.getBlockDataForTop(b, rng, rx - stack, rz + stack, data).getBlockData());
						}
					}

					else
					{
						h.set(xx, ceiling, zz, cdeco.getBlockData100(b, rng, rx, rz, data).getBlockData());
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
