package ninja.bytecode.iris.object;

import org.bukkit.World.Environment;

import lombok.Data;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.util.KList;

@Data
public class IrisDimension
{
	private String name = "A Dimension";
	private InterpolationMethod interpolationFunction = InterpolationMethod.BILINEAR;
	private double interpolationScale = 5.6;
	private Environment environment = Environment.NORMAL;
	private KList<String> biomes = new KList<>();
	private int fluidHeight = 127;
	private double biomeZoom = 5D;
	private double terrainZoom = 2D;
	private double roughnessZoom = 2D;
	private int roughnessHeight = 3;
	private transient KList<IrisBiome> biomeCache;

	public KList<IrisBiome> buildBiomeList()
	{
		if(biomeCache == null)
		{
			synchronized(this)
			{
				biomeCache = new KList<>();

				synchronized(biomeCache)
				{
					for(String i : biomes)
					{
						IrisBiome biome = Iris.data.getBiomeLoader().load(i);
						if(biome != null)
						{
							biomeCache.add(biome);
						}
					}
				}
			}
		}

		return biomeCache;
	}
}
