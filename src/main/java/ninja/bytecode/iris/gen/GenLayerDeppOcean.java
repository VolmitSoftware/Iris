package ninja.bytecode.iris.gen;

import java.util.Random;

import org.bukkit.World;

import ninja.bytecode.shuriken.math.CNG;
import ninja.bytecode.shuriken.math.RNG;

public class GenLayerDeppOcean extends GenLayer
{
	private CNG gen;

	public GenLayerDeppOcean(World world, Random random, RNG rng)
	{
		//@builder
		super(world, random, rng);
		gen = new CNG(rng.nextRNG(), 1D, 4)
				.scale(0.012);
		//@done
	}

	@Override
	public double generateLayer(double noise, double dx, double dz)
	{
		double deeper = gen.noise(dx, dz);
	}
}
