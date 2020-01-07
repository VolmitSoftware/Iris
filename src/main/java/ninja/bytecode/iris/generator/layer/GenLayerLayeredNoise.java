package ninja.bytecode.iris.generator.layer;

import java.util.Random;

import org.bukkit.World;

import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.util.GenLayer;
import ninja.bytecode.shuriken.math.CNG;
import ninja.bytecode.shuriken.math.RNG;

public class GenLayerLayeredNoise extends GenLayer
{
	private CNG gen;
	private CNG fract;

	public GenLayerLayeredNoise(IrisGenerator iris, World world, Random random, RNG rng)
	{
		//@builder
		super(iris, world, random, rng);
		fract = new CNG(rng.nextParallelRNG(16), 1D, 9).scale(0.0181);
		gen = new CNG(rng.nextParallelRNG(17), 0.19D, 16)
			.scale(0.012)
			.amp(0.5)
			.freq(1.1)
			.fractureWith(new CNG(rng.nextParallelRNG(18), 1, 6)
				.scale(0.018)
				.child(new CNG(rng.nextParallelRNG(19), 0.745, 2)
					.scale(0.1))
				.fractureWith(new CNG(rng.nextParallelRNG(20), 1, 3)
					.scale(0.15), 24), 44);
	}

	public double getHeight(double x, double z)
	{
		return 0.65* gen.noise(x, z);
	}

	@Override
	public double generateLayer(double gnoise, double dx, double dz)
	{
		return 0.65 * gen.noise(gnoise, dx + (fract.noise(gnoise, dx, dz) * 333), dz - (fract.noise(dz, dx, gnoise) * 333));
	}
}
