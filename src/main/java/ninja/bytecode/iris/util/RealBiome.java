package ninja.bytecode.iris.util;

import org.bukkit.Material;
import org.bukkit.block.Biome;

import ninja.bytecode.iris.MB;
import ninja.bytecode.iris.gen.GenLayerBase;
import ninja.bytecode.shuriken.collections.GList;

public class RealBiome
{
	public static final double a = 0;
	public static final double h = 0.5;
	public static final double t = 0.5;
	
	//@builder
	public static final RealBiome[] biomes = {
			new RealBiome(0, 0.5, h, -1).water(), // Ocean
			new RealBiome(1, 0.6, 0.4, 0.125), // Plains 
			new RealBiome(2, 2, 0, 0.125) // Desert 
				.surface(new MB(Material.SAND))
				.dirt(new MB(Material.SAND), new MB(Material.SAND, 1))
				.rock(new MB(Material.SANDSTONE)),
			new RealBiome(3, 0.2, 0.3, 0.56), // Extreme Hills
			new RealBiome(4, 0.5, 0.8, a), // Forest 
			new RealBiome(5, 0.25, 0.8, 0.2), // Taiga 
			new RealBiome(6, 0.8, 0.9, -0.2), // Swampland
			new RealBiome(7, t, h, -0.5).river(), // River
			new RealBiome(8, 2, 0, a).dimensional(), // Hell 
			new RealBiome(9, t, h, a).dimensional(), // The End 
			new RealBiome(10, 0, 0.5, -1).water(), // Frozen Ocean
			new RealBiome(11, 0, 0.5, -0.5).river(), // Frozen River
			new RealBiome(12, 0, 0.5, 0.125).surface(new MB(Material.SNOW_BLOCK)), // Ice Plains
			new RealBiome(13, 0, 0.5, 0.765) // Ice Mountains
				.surface(new MB(Material.SNOW_BLOCK))
				.dirt(new MB(Material.PACKED_ICE)), 
			new RealBiome(14, 0.9, 1, 0.2).modifier() // Mushroom Island
				.surface(new MB(Material.MYCEL)),
			new RealBiome(15, 0, 1, 0).modifier() // Mushroom Island Shore
				.surface(new MB(Material.MYCEL)), 
			new RealBiome(16, 0.8, 0.4, 0).beach(), // Beaches
			new RealBiome(17, 2, 0, 0.75) // Desert Hills
				.surface(new MB(Material.SAND))
				.dirt(new MB(Material.SAND), new MB(Material.SAND, 1))
				.rock(new MB(Material.SANDSTONE)),
			new RealBiome(18, 0.6, 0.8, 0.75), // Forest Hills
			new RealBiome(19, 0.25, 0.8, 0.75), // Taiga Hills
			new RealBiome(20, 0.2, 0.3, 0.8), // Extreme Hills Edge
			new RealBiome(21, 0.95, 0.9, a), // Jungle
			new RealBiome(22, 0.95, 0.9, 0.75), // Jungle
			new RealBiome(23, 0.9, 0.9, 0.15), // Jungle Edge
			new RealBiome(24, t, h, -1.8).water(), // Deep Ocean
			new RealBiome(25, 0.2, 0.3, 0.1).beach(), // Stone Beach
			new RealBiome(26, 0.2, 0.3, 0).beach(), // Cold Beach
			new RealBiome(27, 0.5, 0.5, a), // Birch Forest
			new RealBiome(28, 0.4, 0.4, 0.25), // Birch Forest Hills
			new RealBiome(29, 0.7, 0.8, a), // Roofed Forest
			new RealBiome(30, -0.5, 0.4, 0.2), // Cold Taiga
			new RealBiome(31, -0.5, 0.4, 0.75), // Cold Taiga Hills
			new RealBiome(32, 0.4, 0.8, 0.2), // Redwood Taiga
			new RealBiome(33, 0.3, 0.8, 0.75), // Redwood Taiga Hills
			new RealBiome(34, 0.2, 0.3, 1), // Extra Hills with Trees
			new RealBiome(35, 1.2, 0, 0.125), // Savanna
			new RealBiome(36, 1, 0, 0.28), // Savanna Plateau
			new RealBiome(37, 2, 0, a), // Mesa
			new RealBiome(38, 2, 0, 0.28), // Mesa Plateau F
			new RealBiome(39, 2, 0, 0.31), // Mesa Plateau
	};
	//@done

	private int biomeId;
	private double temperature;
	private double humidity;
	private double height;
	private boolean modifier;
	private boolean river;
	private boolean water;
	private boolean beach;
	private boolean dimensional;
	private GList<MB> surface;
	private GList<MB> dirt;
	private GList<MB> rock;
	private boolean defs;
	private boolean defd;
	private boolean defr;
	
	public RealBiome(int biomeId, double temperature, double humidity, double height)
	{
		defs = true;
		defd = true;
		defr = true;
		this.biomeId = biomeId;
		this.temperature = temperature;
		this.humidity = humidity;
		this.height = height;
		surface = new GList<>();
		dirt = new GList<>();
		rock = new GList<>();
		surface.add(new MB(Material.GRASS));
		dirt.add(new MB(Material.DIRT), new MB(Material.DIRT, 1));
		rock.add(new MB(Material.STONE), new MB(Material.STONE, 5), new MB(Material.COBBLESTONE));
		temperature = temperature > 1 ? 1 : temperature < 0 ? 0 : temperature;
		humidity = humidity > 1 ? 1 : humidity < 0 ? 0 : humidity;
		height = height > 1 ? 1 : height < 0 ? 0 : height;
	}
	
	public static RealBiome match(double temperature, double humidity, double height, double d)
	{
		GList<RealBiome> b = new GList<>();
		double distance = Double.MAX_VALUE;
		
		for(RealBiome i : biomes)
		{
			if(i.modifier)
			{
				continue;
			}
			
			double dist = i.getDistance(temperature, humidity, height);
			if(dist < distance)
			{
				distance = dist;
				b.add(i);
			}
		}
		
		return b.get((int) (d * Math.min(b.size(), 3)));
	}
	
	public double getDistance(double temperature, double humidity, double height)
	{
		return Math.abs((temperature - this.temperature) * 3.5) + Math.abs((humidity - this.humidity) * 2.5) + Math.abs((height - this.height) * 4.8);
	}
	
	public Biome getBiome()
	{
		return Biome.values()[biomeId];
	}
	
	public RealBiome surface(MB... mb)
	{
		if(defs)
		{
			defs = false;
			surface.clear();
		}
		this.surface.add(mb);
		return this;
	}

	public RealBiome dirt(MB... mb)
	{
		if(defd)
		{
			defd = false;
			dirt.clear();
		}
		
		this.dirt.add(mb);
		return this;
	}
	
	public RealBiome rock(MB... mb)
	{
		if(defr)
		{
			defr = false;
			rock.clear();
		}
		
		this.rock.add(mb);
		return this;
	}
	
	public RealBiome modifier()
	{
		modifier = true;
		return this;
	}
	
	public RealBiome river()
	{
		river = true;
		return this.modifier();
	}
	
	public RealBiome water()
	{
		water = true;
		return this.modifier();
	}
	
	public RealBiome beach()
	{
		beach = true;
		return this.modifier();
	}
	
	public RealBiome dimensional()
	{
		dimensional = true;
		return this.modifier();
	}

	public MB surface(int x, int y, int z, GenLayerBase glBase)
	{
		return surface.get(glBase.scatterInt(x, y, z, surface.size()));
	}
	
	public MB dirt(int x, int y, int z, GenLayerBase glBase)
	{
		return dirt.get(glBase.scatterInt(x, y, z, dirt.size()));
	}
	
	public MB rock(int x, int y, int z, GenLayerBase glBase)
	{
		return rock.get(glBase.scatterInt(x, y, z, rock.size()));
	}

	public static double getA()
	{
		return a;
	}

	public static double getH()
	{
		return h;
	}

	public static double getT()
	{
		return t;
	}

	public static RealBiome[] getBiomes()
	{
		return biomes;
	}

	public int getBiomeId()
	{
		return biomeId;
	}

	public double getTemperature()
	{
		return temperature;
	}

	public double getHumidity()
	{
		return humidity;
	}

	public double getHeight()
	{
		return height;
	}

	public boolean isModifier()
	{
		return modifier;
	}

	public boolean isRiver()
	{
		return river;
	}

	public boolean isWater()
	{
		return water;
	}

	public boolean isBeach()
	{
		return beach;
	}

	public boolean isDimensional()
	{
		return dimensional;
	}

	public GList<MB> getSurface()
	{
		return surface;
	}

	public GList<MB> getDirt()
	{
		return dirt;
	}

	public GList<MB> getRock()
	{
		return rock;
	}

	public boolean isDefs()
	{
		return defs;
	}

	public boolean isDefd()
	{
		return defd;
	}

	public boolean isDefr()
	{
		return defr;
	}

	public MB getSurfaceDecoration()
	{
		// TODO Auto-generated method stub
		return null;
	}
}