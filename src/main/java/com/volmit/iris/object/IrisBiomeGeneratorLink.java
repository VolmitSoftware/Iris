package com.volmit.iris.object;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.ContextualChunkGenerator;
import com.volmit.iris.gen.atomics.AtomicCache;
import com.volmit.iris.util.DependsOn;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.IrisInterpolation;
import com.volmit.iris.util.MaxNumber;
import com.volmit.iris.util.MinNumber;
import com.volmit.iris.util.RegistryListGenerator;
import com.volmit.iris.util.Required;

import lombok.Data;

@Desc("This represents a link to a generator for a biome")
@Data
public class IrisBiomeGeneratorLink
{
	@RegistryListGenerator
	@DontObfuscate
	@Desc("The generator id")
	private String generator = "default";

	@DependsOn({"min", "max"})
	@Required
	@MinNumber(-256)
	@MaxNumber(256)
	@DontObfuscate
	@Desc("The min block value (value + fluidHeight)")
	private int min = 0;

	@DependsOn({"min", "max"})
	@Required
	@MinNumber(-256)
	@MaxNumber(256)
	@DontObfuscate
	@Desc("The max block value (value + fluidHeight)")
	private int max = 0;

	private transient AtomicCache<IrisGenerator> gen = new AtomicCache<>();

	public IrisBiomeGeneratorLink()
	{

	}

	public IrisGenerator getCachedGenerator(ContextualChunkGenerator g)
	{
		return gen.aquire(() ->
		{
			IrisGenerator gen = g != null ? g.loadGenerator(getGenerator()) : Iris.globaldata.getGeneratorLoader().load(getGenerator());

			if(gen == null)
			{
				gen = new IrisGenerator();
			}

			return gen;
		});
	}

	public double getHeight(ContextualChunkGenerator xg, double x, double z, long seed)
	{
		double g = getCachedGenerator(xg).getHeight(x, z, seed);
		g = g < 0 ? 0 : g;
		g = g > 1 ? 1 : g;

		return IrisInterpolation.lerp(min, max, g);
	}
}
