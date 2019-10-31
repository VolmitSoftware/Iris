package ninja.bytecode.iris.gen;

import java.util.Random;

import org.bukkit.World;

import ninja.bytecode.iris.IrisGenerator;
import ninja.bytecode.shuriken.math.CNG;
import ninja.bytecode.shuriken.math.RNG;

public class GenLayerDeepOcean extends GenLayer
{
	private CNG gen;
	private CNG cond;
	private double deepHeight = 0.493;

	public GenLayerDeepOcean(IrisGenerator iris, World world, Random random, RNG rng)
	{ 
		//@builder
		super(iris, world, random, rng);
		gen = new CNG(rng.nextRNG(), 1D, 4)
				.scale(0.023)
				.fractureWith(new CNG(rng.nextRNG(), 1D, 1)
						.scale(0.05), 25);
		cond = new CNG(rng.nextRNG(), 1D, 4)
				.scale(0.018)
				.fractureWith(new CNG(rng.nextRNG(), 1D, 1)
						.scale(0.025), 33);
		//@done
	}

	@Override
	public double generateLayer(double noise, double dx, double dz)
	{
		double deepHeight = this.deepHeight - (cond.noise(dx, dz) * 0.03);
		
		if(noise <= deepHeight)
		{
			double inv = 1D / deepHeight;
			double fract = ((1D - (((noise + (1D - deepHeight)) * inv) * deepHeight))) * inv;
			double on = gen.noise(dx, dz) * 1.93 * fract;
				
			return noise - on;
		}

		return noise;
	}
}
