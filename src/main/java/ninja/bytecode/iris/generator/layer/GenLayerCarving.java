package ninja.bytecode.iris.generator.layer;

import java.util.Random;

import org.bukkit.Material;
import org.bukkit.World;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.pack.IrisBiome;
import ninja.bytecode.iris.util.AtomicChunkData;
import ninja.bytecode.iris.util.GenLayer;
import ninja.bytecode.iris.util.IrisInterpolation;
import ninja.bytecode.iris.util.MB;
import ninja.bytecode.shuriken.math.CNG;
import ninja.bytecode.shuriken.math.M;
import ninja.bytecode.shuriken.math.RNG;

public class GenLayerCarving extends GenLayer
{
	private CNG carver;
	private CNG fract;
	private CNG ruff;

	public GenLayerCarving(IrisGenerator iris, World world, Random random, RNG rng)
	{
		super(iris, world, random, rng);
		//@builder
		carver = new CNG(rng.nextParallelRNG(116), 1D, 7)
				.scale(0.0057)
				.amp(0.5)
				.freq(1.1);
		fract = new CNG(rng.nextParallelRNG(20), 1, 3)
				.scale(0.0302);
		ruff = new CNG(rng.nextParallelRNG(20), 1, 2)
				.scale(0.0702);
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

	public double carve(double x, double y, double z)
	{
		double cx = 77D;
		double cz = 11D;
		double rx = ruff.noise(x, z, y) * cz;
		double ry = ruff.noise(z, y, x) * cz;
		double rz = ruff.noise(y, x, z) * cz;
		double fx = fract.noise(x + rx, z - ry, y + rz) * cx;
		double fy = fract.noise(z - ry, y + rx, x - rz) * cx;
		double fz = fract.noise(y + rz, x - rx, z + ry) * cx;
		return carver.noise(x + fx, y - fy, z + fz);
	}

	public void genCarves(double wxx, double wzx, int x, int z, int s, IrisGenerator g, IrisBiome biome, AtomicChunkData data)
	{
		if(!Iris.settings.gen.genCarving)
		{
			return;
		}
		
		if(s < Iris.settings.gen.minCarvingHeight)
		{
			return;
		}

		double ch = Iris.settings.gen.carvingChance;
		int txy = (int) IrisInterpolation.lerp(Iris.settings.gen.minCarvingHeight, Iris.settings.gen.maxCarvingHeight, 0.5);

		if(carve(wxx, txy, wzx) < ch / 2D)
		{
			return;
		}

		int hit = 0;
		int carved = 0;

		for(int i = Math.min(Iris.settings.gen.maxCarvingHeight, s); i > Iris.settings.gen.minCarvingHeight; i--)
		{
			double hill = getHill(i);

			if(hill < 0.065)
			{
				continue;
			}

			if(carve(wxx, i, wzx) < IrisInterpolation.lerpBezier(0, ch, hill))
			{
				carved++;
				data.setBlock(x, i, z, Material.AIR);
			}
		}

		if(carved > 4)
		{
			boolean fail = false;

			for(int i = Iris.settings.gen.maxCarvingHeight; i > Iris.settings.gen.minCarvingHeight; i--)
			{
				Material m = data.getType(x, i, z);
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
								if(data.getType(x, j, z).equals(Material.AIR))
								{
									fail = true;
									break;
								}
							}
						}

						if(!fail)
						{
							MB mb = biome.getSurface(wxx, wzx, g.getRTerrain());
							data.setBlock(x, i, z, mb.material, mb.data);
						}

						else
						{
							data.setBlock(x, i, z, Material.AIR);
						}
					}

					else if(hit > 1 && hit < g.scatterInt(x, i, z, 4) + 3)
					{
						if(!fail)
						{
							MB mb = biome.getSubSurface(wxx, i, wzx, g.getRTerrain());
							data.setBlock(x, i, z, mb.material, mb.data);
						}

						else
						{
							data.setBlock(x, i, z, Material.AIR);
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
