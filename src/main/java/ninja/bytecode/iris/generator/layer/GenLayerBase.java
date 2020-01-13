package ninja.bytecode.iris.generator.layer;

import java.util.Random;

import org.bukkit.World;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.util.GenLayer;
import ninja.bytecode.shuriken.math.CNG;
import ninja.bytecode.shuriken.math.M;
import ninja.bytecode.shuriken.math.RNG;

public class GenLayerBase extends GenLayer
{
	private double[][][] scatterCache;
	private CNG gen;
	private CNG fracture;
	private CNG hfracture;
	private CNG height;
	private CNG superheight;

	public GenLayerBase(IrisGenerator iris, World world, Random random, RNG rng)
	{
		//@builder
		super(iris, world, random, rng);
		scatterCache = new double[16][][];
		CNG scatter = new CNG(rng.nextParallelRNG(5), 1, 1)
			.scale(10);
		hfracture = new CNG(rng.nextParallelRNG(6), 1, 2)
			.scale(0.0124);
		gen = new CNG(rng.nextParallelRNG(7), 0.19D, 7)
			.scale(0.012)
			.amp(0.5)
			.freq(1.1)
			.fractureWith(new CNG(rng.nextParallelRNG(8), 1, 6)
				.scale(0.018)
				.injectWith(CNG.MULTIPLY)
				.child(new CNG(rng.nextParallelRNG(9), 0.745, 2)
					.scale(0.1)), 44);
		height = new CNG(rng.nextParallelRNG(10), 1, 8)
			.scale(0.0017601 * Iris.settings.gen.heightScale)
			.fractureWith(new CNG(rng.nextParallelRNG(11), 1, 6)
				.scale(0.0174)
				.fractureWith(new CNG(rng.nextParallelRNG(12), 1, 1)
					.scale(0.0034), 31)
				.scale(0.066), 58);		
		superheight = new CNG(rng.nextParallelRNG(13), 1, 6)
			.scale(0.0025 * Iris.settings.gen.superHeightScale)
			.fractureWith(new CNG(rng.nextParallelRNG(14), 1, 1)
				.scale(0.021), 250);
		fracture = new CNG(rng.nextParallelRNG(15), 0.6D, 4)
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

	public double getHeight(double x, double z)
	{
		return M.clip(Math.pow(height.noise(x + (hfracture.noise(x, z) * Iris.settings.gen.heightFracture), z + (hfracture.noise(z, x) * Iris.settings.gen.heightFracture)), Iris.settings.gen.heightExponentBase + (superheight.noise(x, z) * Iris.settings.gen.heightExponentMultiplier)) * Iris.settings.gen.heightMultiplier, 0D, 1D);
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
	public double generateLayer(double gnoise, double dx, double dz)
	{
		double noise = gnoise + getHeight(dx, dz);
		double fnoise = fracture.noise(dx, dz);
		dx += (fnoise * 44);
		dz -= (fnoise * 44);
		return ((noise * 0.185) + (gen.noise(dx, dz) * (0.15 + (noise * 0.65))));
	}
}
