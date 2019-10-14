package ninja.bytecode.iris.gen;

import java.util.Random;

import org.bukkit.World;

import ninja.bytecode.shuriken.math.CNG;
import ninja.bytecode.shuriken.math.RNG;

public class GenLayerBase extends GenLayer
{
	private double[][][] scatterCache;
	private CNG gen;
	private CNG fracture;

	public GenLayerBase(World world, Random random, RNG rng)
	{
		//@builder
		super(world, random, rng);
		scatterCache = new double[16][][];
		CNG scatter = new CNG(rng.nextRNG(), 1, 1)
				.scale(10);
		gen = new CNG(rng.nextRNG(), 0.19D, 16)
				.scale(0.012)
				.amp(0.5)
				.freq(1.1)
				.fractureWith(new CNG(rng.nextRNG(), 1, 6)
						.scale(0.018)
						.injectWith(CNG.MULTIPLY)
						.child(new CNG(rng.nextRNG(), 0.745, 2)
								.scale(0.1))
						.fractureWith(new CNG(rng.nextRNG(), 1, 3)
								.scale(0.15), 24), 44);
		fracture = new CNG(rng.nextRNG(), 0.6D, 4)
				.scale(0.118);
		//@done

		for(int i = 0; i < 16; i++)
		{
			scatterCache[i] = new double[16][];

			for(int j = 0; j < 16; j++)
			{
				scatterCache[i][j] = new double[16];

				for(int k = 0; k < 16; k++)
				{
					scatterCache[i][j][k] = scatter.noise(i, j, k);
				}
			}
		}
	}

	public int scatterInt(int x, int y, int z, int bound)
	{
		return (int) (scatter(x, y, z) * (double) (bound - 1));
	}

	public double scatter(int x, int y, int z)
	{
		return scatterCache[Math.abs(x) % 16][Math.abs(y) % 16][Math.abs(z) % 16];
	}

	public boolean scatterChance(int x, int y, int z, double chance)
	{
		return scatter(x, y, z) > chance;
	}

	@Override
	public double generateLayer(double noise, double dx, double dz)
	{
		double fnoise = fracture.noise(dx, dz);
		dx += (fnoise * 44);
		dz -= (fnoise * 44);
		return ((noise * 0.5) + (gen.noise(dx, dz) * (0.15 + (noise * 0.65)))) + 0.31;
	}
}
