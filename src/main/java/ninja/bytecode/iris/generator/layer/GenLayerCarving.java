package ninja.bytecode.iris.generator.layer;

import java.util.Random;

import org.bukkit.Material;
import org.bukkit.World;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.pack.IrisBiome;
import ninja.bytecode.iris.util.GenLayer;
import ninja.bytecode.iris.util.IrisInterpolation;
import ninja.bytecode.iris.util.MB;
import ninja.bytecode.shuriken.math.CNG;
import ninja.bytecode.shuriken.math.M;
import ninja.bytecode.shuriken.math.RNG;

public class GenLayerCarving extends GenLayer
{
	public CNG carver;
	public CNG clipper;

	public GenLayerCarving(IrisGenerator iris, World world, Random random, RNG rng)
	{
		super(iris, world, random, rng);
		//@builder
		carver = new CNG(rng.nextParallelRNG(116), 1D, 3)
				.scale(0.0285)
				.amp(0.5)
				.freq(1.1)
				.fractureWith(new CNG(rng.nextParallelRNG(18), 1, 3)
					.scale(0.005)
					.child(new CNG(rng.nextParallelRNG(19), 0.745, 2)
						.scale(0.1))
					.fractureWith(new CNG(rng.nextParallelRNG(20), 1, 3)
						.scale(0.08), 12), 33);
		clipper = new CNG(rng.nextParallelRNG(117), 1D, 1)
				.scale(0.0009)
				.amp(0.0)
				.freq(1.1)
				.fractureWith(new CNG(rng.nextParallelRNG(18), 1, 3)
					.scale(0.005)
					.child(new CNG(rng.nextParallelRNG(19), 0.745, 2)
						.scale(0.1))
					.fractureWith(new CNG(rng.nextParallelRNG(20), 1, 3)
						.scale(0.08), 12), 33);
		//@done
	}

	public double getHill(double height)
	{
		double min = Iris.settings.gen.minCarvingHeight;
		double max = Iris.settings.gen.maxCarvingHeight;
		double mid = IrisInterpolation.lerp(min, max, 0.5);

		if(height >= min && height <= mid)
		{
			return IrisInterpolation.lerpBezier(0, 1, M.lerpInverse(min, mid, height));
		}

		else if(height >= mid && height <= max)
		{
			return IrisInterpolation.lerpBezier(1, 0, M.lerpInverse(mid, max, height));
		}

		return 0;
	}

	public void genCarves(double wxx, double wzx, int x, int z, int s, IrisGenerator g, IrisBiome biome)
	{
		if(s < Iris.settings.gen.minCarvingHeight)
		{
			return;
		}

		int hit = 0;
		int carved = 0;

		for(int i = Math.min(Iris.settings.gen.maxCarvingHeight, s); i > Iris.settings.gen.minCarvingHeight; i--)
		{
			if(clipper.noise(wzx, i, wxx) < Iris.settings.gen.carvingChance)
			{
				double hill = getHill(i);

				if(hill < 0.065)
				{
					continue;
				}

				if(carver.noise(wxx, i, wzx) < IrisInterpolation.lerpBezier(0.01, 0.425, hill))
				{
					carved++;
					g.setBlock(x, i, z, Material.AIR);
				}
			}
		}

		if(carved > 4)
		{
			boolean fail = false;

			for(int i = Iris.settings.gen.maxCarvingHeight; i > Iris.settings.gen.minCarvingHeight; i--)
			{
				Material m = g.getType(x, i, z);
				if(!m.equals(Material.AIR))
				{
					hit++;

					if(hit == 1)
					{
						fail = false;

						if(i > 5)
						{
							for(int j = i; j > i - 5; j--)
							{
								if(g.getType(x, j, z).equals(Material.AIR))
								{
									fail = true;
									break;
								}
							}
						}

						if(!fail)
						{
							MB mb = biome.getSurface(wxx, wzx, g.getRTerrain());
							g.setBlock(x, i, z, mb.material, mb.data);
						}

						else
						{
							g.setBlock(x, i, z, Material.AIR);
						}
					}

					else if(hit > 1 && hit < g.getGlBase().scatterInt(x, i, z, 4) + 3)
					{
						if(!fail)
						{
							MB mb = biome.getDirtRNG();
							g.setBlock(x, i, z, mb.material, mb.data);
						}

						else
						{
							g.setBlock(x, i, z, Material.AIR);
						}
					}
				}

				else
				{
					hit = 0;
				}
			}
		}
	}

	@Override
	public double generateLayer(double gnoise, double dx, double dz)
	{
		return gnoise;
	}
}
