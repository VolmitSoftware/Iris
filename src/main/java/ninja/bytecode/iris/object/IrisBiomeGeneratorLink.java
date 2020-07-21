package ninja.bytecode.iris.object;

import lombok.Data;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.util.Desc;
import ninja.bytecode.iris.util.IrisInterpolation;

@Desc("This represents a link to a generator for a biome")
@Data
public class IrisBiomeGeneratorLink
{
	@Desc("The generator id")
	private String generator = "default";

	@Desc("The min block value (value + fluidHeight)")
	private int min = 0;

	@Desc("The max block value (value + fluidHeight)")
	private int max = 0;

	private transient IrisGenerator gen;

	public IrisBiomeGeneratorLink()
	{

	}

	public IrisGenerator getCachedGenerator()
	{
		if(gen == null)
		{
			gen = Iris.data.getGeneratorLoader().load(getGenerator());

			if(gen == null)
			{
				gen = new IrisGenerator();
			}
		}

		return gen;
	}

	public double getHeight(double x, double z, long seed)
	{
		double g = getCachedGenerator().getHeight(x, z, seed);
		g = g < 0 ? 0 : g;
		g = g > 1 ? 1 : g;

		return IrisInterpolation.lerp(min, max, g);
	}
}
