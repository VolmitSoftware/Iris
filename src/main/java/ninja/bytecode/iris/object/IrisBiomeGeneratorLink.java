package ninja.bytecode.iris.object;

import lombok.Data;
import net.md_5.bungee.api.ChatColor;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.util.IrisInterpolation;

@Data
public class IrisBiomeGeneratorLink
{
	private String generator = "default";
	private int min = 0;
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
			
			Iris.success("Registered Generator " + ChatColor.WHITE + gen.getLoadKey());
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
