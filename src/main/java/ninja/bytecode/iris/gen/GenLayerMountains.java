package ninja.bytecode.iris.gen;

import java.util.Random;

import org.bukkit.World;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.IrisGenerator;
import ninja.bytecode.shuriken.math.CNG;
import ninja.bytecode.shuriken.math.RNG;

public class GenLayerMountains extends GenLayer
{
	private CNG gen;

	public GenLayerMountains(IrisGenerator iris, World world, Random random, RNG rng)
	{
		//@builder
		super(iris, world, random, rng);
		gen = new CNG(rng.nextRNG(), 1D, 2)
				.scale(0.0011 * Iris.settings.gen.mountainHorizontalZoom)
				.child(new CNG(rng.nextRNG(), 1D, 3).scale(0.00012 * Iris.settings.gen.mountainHorizontalZoom))
				.child(new CNG(rng.nextRNG(), 1D, 4).scale(0.00014 * Iris.settings.gen.mountainHorizontalZoom))
				.child(new CNG(rng.nextRNG(), 1D, 5).scale(0.00015 * Iris.settings.gen.mountainHorizontalZoom))
				.injectWith(CNG.MULTIPLY)
				.fractureWith(new CNG(rng.nextRNG(), 1D, 1)
						.scale(0.05), 25);
		//@done
	}

	@Override
	public double generateLayer(double noise, double dx, double dz)
	{
		return noise + (gen.noise(dx, dz) - Iris.settings.gen.mountainSink) * Iris.settings.gen.mountainMultiplier;
	}
}
