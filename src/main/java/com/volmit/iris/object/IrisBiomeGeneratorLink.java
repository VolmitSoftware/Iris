package com.volmit.iris.object;

import com.volmit.iris.scaffold.cache.AtomicCache;
import com.volmit.iris.scaffold.data.DataProvider;
import com.volmit.iris.util.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
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
	@MinNumber(-256) // TODO: WARNING HEIGHT
	@MaxNumber(256) // TODO: WARNING HEIGHT
	@DontObfuscate
	@Desc("The min block value (value + fluidHeight)")
	private int min = 0;

	@DependsOn({"min", "max"})
	@Required
	@MinNumber(-256) // TODO: WARNING HEIGHT
	@MaxNumber(256) // TODO: WARNING HEIGHT
	@DontObfuscate
	@Desc("The max block value (value + fluidHeight)")
	private int max = 0;

	private final transient AtomicCache<IrisGenerator> gen = new AtomicCache<>();

	public IrisGenerator getCachedGenerator(DataProvider g)
	{
		return gen.aquire(() ->
		{
			IrisGenerator gen = g.getData().getGeneratorLoader().load(getGenerator());

			if(gen == null)
			{
				gen = new IrisGenerator();
			}

			return gen;
		});
	}

	public double getHeight(DataProvider xg, double x, double z, long seed)
	{
		double g = getCachedGenerator(xg).getHeight(x, z, seed);
		g = g < 0 ? 0 : g;
		g = g > 1 ? 1 : g;

		return IrisInterpolation.lerp(min, max, g);
	}
}
