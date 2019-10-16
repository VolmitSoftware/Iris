package ninja.bytecode.iris.util;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

import ninja.bytecode.iris.gen.GenLayerBase;
import ninja.bytecode.shuriken.collections.GList;
import ninja.bytecode.shuriken.collections.GMap;
import ninja.bytecode.shuriken.math.M;

public class RealBiome
{
	public static final double a = 0;
	public static final double h = 0.5;
	public static final double t = 0.5;

	//@builder
	public static final RealBiome[] biomes = {
			new RealBiome(0, 0.5, h, -1)
				.water()
				.surface(Material.SAND.createBlockData())
				.surface(Material.SEAGRASS.createBlockData(), 0.02),
			new RealBiome(1, 0.6, 0.4, 0.125)// Plains 
				.surface(Material.GRASS.createBlockData(), 0.58)
				.surface(Material.TALL_GRASS.createBlockData(), 0.18)
				.surface(Material.CORNFLOWER.createBlockData(), 0.002)
				.surface(Material.SUNFLOWER.createBlockData(), 0.002)
				.surface(Material.ROSE_BUSH.createBlockData(), 0.002)
				.surface(Material.DANDELION.createBlockData(), 0.002)
				.surface(Material.ORANGE_TULIP.createBlockData(), 0.001)
				.surface(Material.PINK_TULIP.createBlockData(), 0.001)
				.surface(Material.RED_TULIP.createBlockData(), 0.001)
				.surface(Material.WHITE_TULIP.createBlockData(), 0.001)
				.surface(Material.OXEYE_DAISY.createBlockData(), 0.001),
			new RealBiome(2, 2, 0, 0.125) // Desert 
				.surface(Material.SAND.createBlockData())
				.dirt(Material.SAND.createBlockData())
				.rock(Material.SANDSTONE.createBlockData()),
			new RealBiome(3, 0.2, 0.3, 0.56)
				.surface(Material.GRASS.createBlockData(), 0.18), // Extreme Hills
			new RealBiome(4, 0.5, 0.8, a).surface(Material.TALL_GRASS.createBlockData(), 0.48), // Forest 
			new RealBiome(5, 0.25, 0.8, 0.2).surface(Material.TALL_GRASS.createBlockData(), 0.18), // Taiga 
			new RealBiome(6, 0.8, 0.9, -0.2).surface(Material.TALL_GRASS.createBlockData(), 0.38), // Swampland
			new RealBiome(7, t, h, -0.5).river(), // River
			new RealBiome(8, 2, 0, a).dimensional(), // Hell 
			new RealBiome(9, t, h, a).dimensional(), // The End 
			new RealBiome(10, 0, 0.5, -1)
				.water()
				.surface(Material.SAND.createBlockData()),
			new RealBiome(11, 0, 0.5, -0.5).river(), // Frozen River
			new RealBiome(12, 0, 0.5, 0.125)
				.surface(Material.SNOW_BLOCK.createBlockData()), // Ice Plains
			new RealBiome(13, 0, 0.5, 0.765) // Ice Mountains
				.surface(Material.SNOW_BLOCK.createBlockData())
				.dirt(Material.PACKED_ICE.createBlockData()), 
			new RealBiome(14, 0.9, 1, 0.2).modifier() // Mushroom Island
				.surface(Material.MYCELIUM.createBlockData()),
			new RealBiome(15, 0, 1, 0).modifier() // Mushroom Island Shore
				.surface(Material.MYCELIUM.createBlockData()), 
			new RealBiome(16, 0.8, 0.4, 0).beach()
				.surface(Material.SAND.createBlockData()), // Beaches
			new RealBiome(17, 2, 0, 0.75) // Desert Hills
				.surface(Material.SAND.createBlockData())
				.dirt(Material.SAND.createBlockData())
				.rock(Material.SANDSTONE.createBlockData()),
			new RealBiome(18, 0.6, 0.8, 0.75).surface(Material.GRASS.createBlockData(), 0.38).surface(Material.TALL_GRASS.createBlockData(), 0.08), // Forest Hills
			new RealBiome(19, 0.25, 0.8, 0.75).surface(Material.GRASS.createBlockData(), 0.28).surface(Material.TALL_GRASS.createBlockData(), 0.08), // Taiga Hills
			new RealBiome(20, 0.2, 0.3, 0.8).surface(Material.GRASS.createBlockData(), 0.18).surface(Material.TALL_GRASS.createBlockData(), 0.08), // Extreme Hills Edge
			new RealBiome(21, 0.95, 0.9, a).surface(Material.GRASS.createBlockData(), 0.68).surface(Material.TALL_GRASS.createBlockData(), 0.22), // Jungle
			new RealBiome(22, 0.95, 0.9, 0.75).surface(Material.GRASS.createBlockData(), 0.72).surface(Material.TALL_GRASS.createBlockData(), 0.28), // Jungle
			new RealBiome(23, 0.9, 0.9, 0.15).surface(Material.GRASS.createBlockData(), 0.62).surface(Material.TALL_GRASS.createBlockData(), 0.22), // Jungle Edge
			new RealBiome(24, t, h, -1.8)
				.water()
				.surface(Material.SAND.createBlockData()), // Deep Ocean
			new RealBiome(25, 0.2, 0.3, 0.1).beach(), // Stone Beach
			new RealBiome(26, 0.2, 0.3, 0).beach(), // Cold Beach
			new RealBiome(27, 0.5, 0.5, a).surface(Material.GRASS.createBlockData(), 0.38).surface(Material.GRASS.createBlockData(), 0.18), // Birch Forest
			new RealBiome(28, 0.4, 0.4, 0.25).surface(Material.GRASS.createBlockData(), 0.38).surface(Material.GRASS.createBlockData(), 0.18), // Birch Forest Hills
			new RealBiome(29, 0.7, 0.8, a).surface(Material.GRASS.createBlockData(), 0.68).surface(Material.GRASS.createBlockData(), 0.28), // Roofed Forest
			new RealBiome(30, -0.5, 0.4, 0.2).surface(Material.GRASS.createBlockData(), 0.28).surface(Material.GRASS.createBlockData(), 0.18), // Cold Taiga
			new RealBiome(31, -0.5, 0.4, 0.75).surface(Material.GRASS.createBlockData(), 0.28).surface(Material.GRASS.createBlockData(), 0.18), // Cold Taiga Hills
			new RealBiome(32, 0.4, 0.8, 0.2).surface(Material.GRASS.createBlockData(), 0.38).surface(Material.GRASS.createBlockData(), 0.18), // Redwood Taiga
			new RealBiome(33, 0.3, 0.8, 0.75).surface(Material.GRASS.createBlockData(), 0.38).surface(Material.GRASS.createBlockData(), 0.18), // Redwood Taiga Hills
			new RealBiome(34, 0.2, 0.3, 1).surface(Material.GRASS.createBlockData(), 0.28).surface(Material.GRASS.createBlockData(), 0.18), // Extra Hills with Trees
			new RealBiome(35, 1.2, 0, 0.125).surface(Material.GRASS.createBlockData(), 0.28).surface(Material.GRASS.createBlockData(), 0.18), // Savanna
			new RealBiome(36, 1, 0, 0.28).surface(Material.GRASS.createBlockData(), 0.18).surface(Material.GRASS.createBlockData(), 0.18), // Savanna Plateau
			new RealBiome(37, 2, 0, a), // Mesa
			new RealBiome(38, 2, 0, 0.28), // Mesa Plateau F
			new RealBiome(39, 2, 0, 0.31), // Mesa Plateau
			new RealBiome(Biome.WARM_OCEAN.ordinal(), 0.6, h, -1)
				.water()
				.surface(Material.SAND.createBlockData(), Material.CLAY.createBlockData(), Material.GRAVEL.createBlockData())
				.surface(Material.SEAGRASS.createBlockData(), 0.64)
				.surface(Material.TALL_SEAGRASS.createBlockData(), 0.44), 
			new RealBiome(Biome.LUKEWARM_OCEAN.ordinal(), 0.7, h, -1)
				.water()
				.surface(Material.SAND.createBlockData(), Material.CLAY.createBlockData(), Material.GRAVEL.createBlockData())
				.surface(Material.SEAGRASS.createBlockData(), 0.44)
				.surface(Material.TALL_SEAGRASS.createBlockData(), 0.24), 
			new RealBiome(Biome.COLD_OCEAN.ordinal(), 0.4, h, -1)
				.water()
				.surface(Material.SAND.createBlockData(), Material.CLAY.createBlockData(), Material.GRAVEL.createBlockData()),
			new RealBiome(Biome.DEEP_WARM_OCEAN.ordinal(), 0.6, h, -1)
				.water()
				.surface(Material.BRAIN_CORAL_BLOCK.createBlockData(), Material.FIRE_CORAL_BLOCK.createBlockData(), Material.HORN_CORAL_BLOCK.createBlockData(), Material.MAGMA_BLOCK.createBlockData(), Material.TUBE_CORAL_BLOCK.createBlockData(), Material.BRAIN_CORAL_BLOCK.createBlockData(), Material.BLUE_CONCRETE_POWDER.createBlockData(), Material.CYAN_CONCRETE_POWDER.createBlockData(), Material.LIGHT_BLUE_CONCRETE_POWDER.createBlockData(), Material.SAND.createBlockData())
				.surface(Material.SAND.createBlockData())
				.surface(Material.SEAGRASS.createBlockData(), 0.39)
				.surface(Material.TALL_SEAGRASS.createBlockData(), 0.52)
				.surface(Material.BRAIN_CORAL.createBlockData(), 0.09)
				.surface(Material.BUBBLE_CORAL.createBlockData(), 0.09)
				.surface(Material.FIRE_CORAL.createBlockData(), 0.09)
				.surface(Material.HORN_CORAL.createBlockData(), 0.09)
				.surface(Material.TUBE_CORAL.createBlockData(), 0.09)
				.surface(Material.BRAIN_CORAL_FAN.createBlockData(), 0.05)
				.surface(Material.BUBBLE_CORAL_FAN.createBlockData(), 0.05)
				.surface(Material.FIRE_CORAL_FAN.createBlockData(), 0.05)
				.surface(Material.HORN_CORAL_FAN.createBlockData(), 0.05)
				.surface(Material.SEA_LANTERN.createBlockData(), 0.006)
				.surface(Material.TUBE_CORAL_FAN.createBlockData(), 0.05), 
			new RealBiome(Biome.DEEP_LUKEWARM_OCEAN.ordinal(), 0.7, h, -1)
				.water()
				.surface(Material.BLUE_CONCRETE_POWDER.createBlockData(), Material.BROWN_CONCRETE_POWDER.createBlockData(), Material.CYAN_CONCRETE_POWDER.createBlockData(), Material.LIGHT_BLUE_CONCRETE_POWDER.createBlockData(), Material.SAND.createBlockData())
				.surface(Material.SAND.createBlockData())
				.surface(Material.SEAGRASS.createBlockData(), 0.24)
				.surface(Material.TALL_SEAGRASS.createBlockData(), 0.55)
				.surface(Material.BRAIN_CORAL.createBlockData(), 0.09)
				.surface(Material.BUBBLE_CORAL.createBlockData(), 0.09)
				.surface(Material.FIRE_CORAL.createBlockData(), 0.09)
				.surface(Material.HORN_CORAL.createBlockData(), 0.09)
				.surface(Material.TUBE_CORAL.createBlockData(), 0.09)
				.surface(Material.BRAIN_CORAL_FAN.createBlockData(), 0.05)
				.surface(Material.BUBBLE_CORAL_FAN.createBlockData(), 0.05)
				.surface(Material.FIRE_CORAL_FAN.createBlockData(), 0.05)
				.surface(Material.HORN_CORAL_FAN.createBlockData(), 0.05)
				.surface(Material.SEA_LANTERN.createBlockData(), 0.009)
				.surface(Material.TUBE_CORAL_FAN.createBlockData(), 0.05), 
			new RealBiome(Biome.DEEP_COLD_OCEAN.ordinal(), 0.4, h, -1)
				.water()
				.surface(Material.SAND.createBlockData())
				.surface(Material.DEAD_BRAIN_CORAL.createBlockData(), 0.09)
				.surface(Material.DEAD_BUBBLE_CORAL.createBlockData(), 0.09)
				.surface(Material.DEAD_FIRE_CORAL.createBlockData(), 0.09)
				.surface(Material.DEAD_HORN_CORAL.createBlockData(), 0.09)
				.surface(Material.DEAD_TUBE_CORAL.createBlockData(), 0.09)
				.surface(Material.DEAD_BRAIN_CORAL_FAN.createBlockData(), 0.05)
				.surface(Material.DEAD_BUBBLE_CORAL_FAN.createBlockData(), 0.05)
				.surface(Material.DEAD_FIRE_CORAL_FAN.createBlockData(), 0.05)
				.surface(Material.DEAD_HORN_CORAL_FAN.createBlockData(), 0.05)
				.surface(Material.SEAGRASS.createBlockData(), 0.09)
				.surface(Material.TALL_SEAGRASS.createBlockData(), 0.02),
			new RealBiome(Biome.DEEP_FROZEN_OCEAN.ordinal(), 0, h, -1)
				.surface(Material.SAND.createBlockData())
				.water()
				.surface(Material.DEAD_BRAIN_CORAL.createBlockData(), 0.09)
				.surface(Material.DEAD_BUBBLE_CORAL.createBlockData(), 0.09)
				.surface(Material.DEAD_FIRE_CORAL.createBlockData(), 0.09)
				.surface(Material.DEAD_HORN_CORAL.createBlockData(), 0.09)
				.surface(Material.DEAD_TUBE_CORAL.createBlockData(), 0.09)
				.surface(Material.DEAD_BRAIN_CORAL_FAN.createBlockData(), 0.05)
				.surface(Material.DEAD_BUBBLE_CORAL_FAN.createBlockData(), 0.05)
				.surface(Material.DEAD_FIRE_CORAL_FAN.createBlockData(), 0.05)
				.surface(Material.DEAD_HORN_CORAL_FAN.createBlockData(), 0.05), 
			new RealBiome(Biome.SUNFLOWER_PLAINS.ordinal(), 0.4, h, 0.015), 
			new RealBiome(Biome.DESERT_LAKES.ordinal(), 1.45, 0.1, 0.015).water(),
			new RealBiome(Biome.GRAVELLY_MOUNTAINS.ordinal(), 0.5, 0.2, 0.75),
			new RealBiome(Biome.FLOWER_FOREST.ordinal(), 0.55, 0.8, a)
				.surface(Material.GRASS.createBlockData(), 0.45)
				.surface(Material.TALL_GRASS.createBlockData(), 0.15)
				.surface(Material.CORNFLOWER.createBlockData(), 0.009)
				.surface(Material.SUNFLOWER.createBlockData(), 0.009)
				.surface(Material.ROSE_BUSH.createBlockData(), 0.009)
				.surface(Material.DANDELION.createBlockData(), 0.009)
				.surface(Material.ORANGE_TULIP.createBlockData(), 0.007)
				.surface(Material.PINK_TULIP.createBlockData(), 0.007)
				.surface(Material.RED_TULIP.createBlockData(), 0.007)
				.surface(Material.WHITE_TULIP.createBlockData(), 0.007)
				.surface(Material.OXEYE_DAISY.createBlockData(), 0.007), 
			new RealBiome(Biome.TAIGA_MOUNTAINS.ordinal(), 0.25, 0.8, 0.8)
				.surface(Material.GRASS.createBlockData(), 0.12), 
			new RealBiome(Biome.SWAMP_HILLS.ordinal(), 0.6, 1, 0.25)
				.surface(Material.GRASS.createBlockData(), 0.18), 
			new RealBiome(Biome.BAMBOO_JUNGLE.ordinal(), 0.8, 0.77, 0.125)
				.surface(Material.GRASS.createBlockData(), 0.78)
				.surface(Material.TALL_GRASS.createBlockData(), 0.28), 
			new RealBiome(Biome.BAMBOO_JUNGLE_HILLS.ordinal(), 0.75, 0.7, 0.225)
				.surface(Material.GRASS.createBlockData(), 0.68) 
				.surface(Material.TALL_GRASS.createBlockData(), 0.28), 

	};
	//@done

	public static RealBiome of(Biome e)
	{
		for(RealBiome i : biomes)
		{
			if(i.getBiome().ordinal() == e.ordinal())
			{
				return i;
			}
		}

		return biomes[1];
	}

	private int biomeId;
	private double temperature;
	private double humidity;
	private double height;
	private boolean modifier;
	private boolean river;
	private boolean water;
	private boolean beach;
	private boolean dimensional;
	private GList<BlockData> surface;
	private GList<BlockData> dirt;
	private GList<BlockData> rock;
	private GMap<BlockData, Double> surfaceDecorator;
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
		surfaceDecorator = new GMap<>();
		surface.add(Material.GRASS_BLOCK.createBlockData());
		dirt.add(Material.DIRT.createBlockData(), Material.COARSE_DIRT.createBlockData());
		rock.add(Material.STONE.createBlockData(), Material.ANDESITE.createBlockData(), Material.COBBLESTONE.createBlockData());
		temperature = temperature > 1 ? 1 : temperature < 0 ? 0 : temperature;
		humidity = humidity > 1 ? 1 : humidity < 0 ? 0 : humidity;
		height = height > 1 ? 1 : height < 0 ? 0 : height;
	}

	public BlockData getSurfaceDecoration()
	{
		for(BlockData i : surfaceDecorator.k())
		{
			if(M.r(surfaceDecorator.get(i)))
			{
				return i;
			}
		}

		return null;
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

	public RealBiome surface(BlockData data, double percentChance)
	{
		surfaceDecorator.put(data, percentChance);
		return this;
	}

	public double getDistance(double temperature, double humidity, double height)
	{
		return Math.abs((temperature - this.temperature) * 3.5) + Math.abs((humidity - this.humidity) * 2.5) + Math.abs((height - this.height) * 4.8);
	}

	public Biome getBiome()
	{
		return Biome.values()[biomeId];
	}

	public RealBiome surface(BlockData... mb)
	{
		if(defs)
		{
			defs = false;
			surface.clear();
		}
		this.surface.add(mb);
		return this;
	}

	public RealBiome dirt(BlockData... mb)
	{
		if(defd)
		{
			defd = false;
			dirt.clear();
		}

		this.dirt.add(mb);
		return this;
	}

	public RealBiome rock(BlockData... mb)
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

	public BlockData surface(int x, int y, int z, GenLayerBase glBase)
	{
		return surface.get(glBase.scatterInt(x, y, z, surface.size()));
	}

	public BlockData dirt(int x, int y, int z, GenLayerBase glBase)
	{
		return dirt.get(glBase.scatterInt(x, y, z, dirt.size()));
	}

	public BlockData rock(int x, int y, int z, GenLayerBase glBase)
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

	public GList<BlockData> getSurface()
	{
		return surface;
	}

	public GList<BlockData> getDirt()
	{
		return dirt;
	}

	public GList<BlockData> getRock()
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
}
