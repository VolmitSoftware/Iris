package com.volmit.iris.object;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.atomics.AtomicCache;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.IrisInterpolation;

import lombok.Data;

@Desc("This represents a link to a generator for a biome")
@Data
public class IrisBiomeGeneratorLink
{
	@DontObfuscate
	@Desc("The generator id")
	private String generator = "default";

	@DontObfuscate
	@Desc("The min block value (value + fluidHeight)")
	private int min = 0;

	@DontObfuscate
	@Desc("The max block value (value + fluidHeight)")
	private int max = 0;

	private transient AtomicCache<IrisGenerator> gen = new AtomicCache<>();

	public IrisBiomeGeneratorLink()
	{

	}

	public IrisGenerator getCachedGenerator()
	{
		return gen.aquire(() ->
		{
			IrisGenerator gen = Iris.data.getGeneratorLoader().load(getGenerator());

			if(gen == null)
			{
				gen = new IrisGenerator();
			}

			return gen;
		});
	}

	public double getHeight(double x, double z, long seed)
	{
		double g = getCachedGenerator().getHeight(x, z, seed);
		g = g < 0 ? 0 : g;
		g = g > 1 ? 1 : g;

		return IrisInterpolation.lerp(min, max, g);
	}
}
