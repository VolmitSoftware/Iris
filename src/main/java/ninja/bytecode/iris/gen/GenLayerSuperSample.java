package ninja.bytecode.iris.gen;

import java.util.Random;

import org.bukkit.World;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.IrisGenerator;
import ninja.bytecode.shuriken.math.CNG;
import ninja.bytecode.shuriken.math.M;
import ninja.bytecode.shuriken.math.RNG;

public class GenLayerSuperSample extends GenLayer
{
	private CNG gen;
	private CNG radius;

	public GenLayerSuperSample(IrisGenerator iris, World world, Random random, RNG rng)
	{
		//@builder
		super(iris, world, random, rng);
		gen = new CNG(rng.nextRNG(), 1D, 4)
				.scale(0.02 * Iris.settings.gen.superSamplerMultiplier);
		radius = new CNG(rng.nextRNG(), 1D, 2)
				.scale(0.01);
		//@done
	}
	
	public double getSuperSampledHeight(double dx, double dz)
	{
		double ssf = 0;
		double height = iris.getRawHeight(dx, dz);

		if(Iris.settings.gen.superSamplerIterations == 0)
		{
			return height;
		}

		double t = 0;
		double sig = Iris.settings.gen.superSampleOpacity * radius.noise(dx, dz);

		for(int i = 0; i < Iris.settings.gen.superSamplerIterations; i++)
		{
			//@builder
			double ss = 0;
			double mul = Iris.settings.gen.superSamplerRadius;
			double[] ssv = new double[] {
				getRawHeight(dx, dz, Math.toRadians(getAngle(dx, dz)), mul / (double)(i + 1), true),
				getRawHeight(dx, dz, Math.toRadians(getAngle(dx, dz)), mul / (double)(i + 1), false)
			};
			//@done
			for(double j : ssv)
			{
				ss += j;
			}

			t += (double) (1D / (i + 1));
			ssf += (ss / 2D) / (double) (i + 1);
		}

		return (height * (1D - sig)) + ((ssf / t) * sig);
	}

	public double getRawHeight(double dx, double dz, double rad, double mult, boolean a)
	{
		double dax = dx + ((Math.sin(rad) * mult) * (a ? 1 : 1));
		double daz = dz + ((Math.cos(rad) * mult) * (a ? -1 : -1));
		return iris.getRawHeight(dax, daz);
	}
	
	public double getAngle(double x, double z)
	{
		return M.percentRange(gen.noise(x, z), 0, 365);
	}

	@Override
	public double generateLayer(double noise, double dx, double dz)
	{
		return noise;
	}
}
