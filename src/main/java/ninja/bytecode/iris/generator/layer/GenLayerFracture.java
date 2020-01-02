package ninja.bytecode.iris.generator.layer;

import java.util.Random;

import org.bukkit.World;

import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.util.GenLayer;
import ninja.bytecode.shuriken.math.CNG;
import ninja.bytecode.shuriken.math.M;
import ninja.bytecode.shuriken.math.RNG;

public class GenLayerFracture extends GenLayer
{
	private CNG gen;
	private CNG cond; 
	private double shootHeight = 0.563;
   
	public GenLayerFracture(IrisGenerator iris, World world, Random random, RNG rng)
	{
		//@builder
		super(iris, world, random, rng);
		gen = new CNG(rng.nextParallelRNG(40), 1D, 12)
				.scale(0.023)
				.fractureWith(new CNG(rng.nextParallelRNG(41), 1D, 1)
						.scale(0.05), 333);
		cond = new CNG(rng.nextParallelRNG(42), 1D, 12)
				.scale(0.038)
				.fractureWith(new CNG(rng.nextParallelRNG(43), 1D, 1)
						.scale(0.025), 299);
		//@done
	}

	@Override
	public double generateLayer(double noise, double dx, double dz)
	{
		double shootHeight = this.shootHeight + (cond.noise(dx, dz) * 0.035);
		
		if(noise >= shootHeight)
		{
			double multiplier = M.rangeScale(0, 0.055, this.shootHeight, 1D, cond.noise(-dx, -dz));
			double on = gen.noise(dx, dz) * multiplier;
				
			return noise + on;
		}

		return noise;
	}
}
