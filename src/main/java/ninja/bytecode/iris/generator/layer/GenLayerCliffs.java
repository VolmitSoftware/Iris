package ninja.bytecode.iris.generator.layer;

import java.util.Random;

import org.bukkit.World;

import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.util.GenLayer;
import ninja.bytecode.shuriken.math.CNG;
import ninja.bytecode.shuriken.math.RNG;

public class GenLayerCliffs extends GenLayer
{
	private double block;
	private CNG gen;
	private CNG sh;
	private CNG ch;

	public GenLayerCliffs(IrisGenerator iris, World world, Random random, RNG rng)
	{
		//@builder
		super(iris, world, random, rng);
		block = 1D / 255D;
		gen = new CNG(rng.nextParallelRNG(128), 1D, 4).scale(0.02);
		sh = new CNG(rng.nextParallelRNG(127), 1D, 1).scale(0.00367);
		ch = new CNG(rng.nextParallelRNG(127), 1D, 1).scale(0.00413);
		//@done
	}

	@Override
	public double generateLayer(double gnoise, double dx, double dz)
	{
		return generateLayer(gnoise, dx, dz, 1D, 0.37D);
	}

	public double generateLayer(double gnoise, double dx, double dz, double cliffs, double chance)
	{
		if(gnoise < block * 66)
		{
			return gnoise;
		}

		double shift = 10.25 + (sh.noise(dx, dz) * 2.25) * cliffs;
		double hits = 183D / shift;
		double n = gnoise;

		for(int i = (int) hits; i > 0; i--)
		{
			if(ch.noise(dx + (i * -1000), dz + (i * 1000)) >= chance)
			{
				continue;
			}
			
			double var = 12.2 * block;
			double varCombined = 15.45 * block;
			double sep = (shift / 1.8D) * block;
			double height = (67 + (i * shift)) * block;
			double sh = ((gen.noise(dx + dz, dz - dx) - 0.5D) * 2D) * varCombined;
			double shv = ((gen.noise(dz + dx, dx - dz) - 0.5D) * 2D) * varCombined;
			double lo = (gen.noise(dx + (i * -1000), dz + (i * 1000)) * var) + height + sh;
			double hi = (gen.noise(dz + (i * 1000), dx + (i * -1000)) * var) + height + sep + shv;
			n = n > lo && n < hi ? lo : n;
		}

		return n;
	}
}
