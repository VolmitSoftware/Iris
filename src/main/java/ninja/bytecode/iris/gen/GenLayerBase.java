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
	private CNG basegen;

	public GenLayerBase(World world, Random random, RNG rng)
	{
		//@builder
		super(world, random, rng);
		scatterCache = new double[16][][];
		CNG scatter = new CNG(rng.nextRNG(), 1, 1)
				.scale(10);
		basegen = new CNG(rng.nextRNG(), 0.46, 3)
				.scale(0.00295)
				.fractureWith(new CNG(rng.nextRNG(), 1, 2)
						.scale(0.025), 70);
		gen = new CNG(rng.nextRNG(), 0.19D, 4)
				.scale(0.012)
				.amp(0.5)
				.freq(1.1)
				.fractureWith(new CNG(rng.nextRNG(), 1, 2)
						.scale(0.018)
						.fractureWith(new CNG(rng.nextRNG(), 1, 1)
								.scale(0.15), 24), 44);
		fracture = new CNG(rng.nextRNG(), 0.6D, 4)
				.scale(0.118);
//		faultline = new CNG(rng.nextRNG(), 1D, 1)
//				.scale(0.005)
//				.child(new CNG(rng.nextRNG(), 1D, 1)
//						.scale(0.012))
//				.injectWith(CNG.MULTIPLY)
//				.fractureWith(new CNG(rng.nextRNG(), 1, 1)
//						.scale(0.07), 200);
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

	@Override
	public double generateLayer(double noise, double dx, double dz)
	{
		super.generateLayer(noise, dx, dz);
		double fnoise = fracture.noise(dx, dz);
		double fx = dx + (fnoise * 12);
		double fz = dz - (fnoise * 12);
		return basegen.noise(fx, fz) + gen.noise(fx, fz);
	}
}
