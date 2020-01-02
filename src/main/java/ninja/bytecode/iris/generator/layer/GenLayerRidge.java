package ninja.bytecode.iris.generator.layer;

import java.util.Random;

import org.bukkit.World;

import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.util.GenLayer;
import ninja.bytecode.shuriken.math.CNG;
import ninja.bytecode.shuriken.math.RNG;

public class GenLayerRidge extends GenLayer
{
	private CNG gen;
	private CNG fract;
	private CNG g;
	private CNG q;

	public GenLayerRidge(IrisGenerator iris, World world, Random random, RNG rng)
	{
		//@builder
		super(iris, world, random, rng);
		q = new CNG(rng.nextParallelRNG(21), 1D, 2).scale(0.0211);
		g = new CNG(rng.nextParallelRNG(22), 1D, 2).scale(0.0011);
		fract = new CNG(rng.nextParallelRNG(23), 1D, 5).scale(0.0011);
		gen = new CNG(rng.nextParallelRNG(24), 0.19D, 16)
			.scale(0.012)
			.injectWith(CNG.MAX)
			.amp(0.5)
			.freq(1.1)
			.fractureWith(new CNG(rng.nextParallelRNG(25), 1, 6)
				.scale(0.018)
				.child(new CNG(rng.nextParallelRNG(26), 0.745, 2)
					.scale(0.1))
				.fractureWith(new CNG(rng.nextParallelRNG(27), 1, 3)
					.scale(0.15), 24), 44);
	}

	public double getHeight(double x, double z)
	{
		return gen.noise(x, z);
	}

	@Override
	public double generateLayer(double gnoise, double dx, double dz)
	{
		double d = gen.noise(gnoise, dx + (fract.noise(gnoise, dx, dz) * 1555), dz - (fract.noise(dz, dx, gnoise) * 1555));
		
		if(d > g.noise(dx, dz) / 8D)
		{
			return q.noise(dx, dz, d) * (d / (7D * (g.noise(dz, dx, gnoise) + 0.1))) * (Math.PI / 2.78);
		}
		
		return 0;
	}
}
