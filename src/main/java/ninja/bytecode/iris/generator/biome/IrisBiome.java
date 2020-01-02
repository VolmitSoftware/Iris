package ninja.bytecode.iris.generator.biome;

import java.lang.reflect.Field;

import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.block.Biome;

import ninja.bytecode.iris.util.MB;
import ninja.bytecode.iris.util.PolygonGenerator;
import ninja.bytecode.shuriken.collections.GList;
import ninja.bytecode.shuriken.collections.GMap;
import ninja.bytecode.shuriken.execution.J;
import ninja.bytecode.shuriken.math.CNG;
import ninja.bytecode.shuriken.math.M;
import ninja.bytecode.shuriken.math.RNG;

public class IrisBiome
{
	//@builder
	public static final IrisBiome RIVER = new IrisBiome("River", Biome.RIVER)
			.surface(MB.of(Material.SAND));
	public static final IrisBiome BEACH = new IrisBiome("Beach", Biome.BEACHES)
			.surface(MB.of(Material.SAND));
	public static final IrisBiome ROAD_GRAVEL = new IrisBiome("Gravel Road", Biome.PLAINS)
			.surface(MB.of(Material.GRAVEL), MB.of(Material.COBBLESTONE))
			.scatter(MB.of(Material.TORCH), 0.05);
	public static final IrisBiome ROAD_GRASSY = new IrisBiome("Grass Path", Biome.PLAINS)
			.surface(MB.of(Material.GRASS_PATH))
			.scatter(MB.of(Material.TORCH), 0.05);
	public static final IrisBiome OCEAN = new IrisBiome("Ocean", Biome.OCEAN)
			.surface(MB.of(Material.SAND), MB.of(Material.SAND), MB.of(Material.SAND), MB.of(Material.CLAY), MB.of(Material.GRAVEL))
			.simplexSurface()
			.height(-0.03);
	public static final IrisBiome DEEP_OCEAN = new IrisBiome("Deep Ocean", Biome.DEEP_OCEAN)
			.surface(MB.of(Material.SAND), MB.of(Material.CLAY), MB.of(Material.GRAVEL))
			.simplexSurface()
			.height(-0.07);
	public static final IrisBiome DESERT = new IrisBiome("Desert", Biome.DESERT)
			.surface(MB.of(Material.SAND))
			.scatter(MB.of(Material.DEAD_BUSH, 0), 0.008)
			.dirt(MB.of(Material.SANDSTONE));
	public static final IrisBiome DESERT_RED = new IrisBiome("Red Desert", Biome.DESERT)
			.surface(MB.of(Material.SAND, 1))
			.scatter(MB.of(Material.DEAD_BUSH, 0), 0.008)
			.dirt(MB.of(Material.RED_SANDSTONE));
	public static final IrisBiome DESERT_COMBINED = new IrisBiome("Combined Desert", Biome.DESERT)
			.surface(MB.of(Material.SAND), MB.of(Material.SAND, 1))
			.scatter(MB.of(Material.DEAD_BUSH, 0), 0.008)
			.dirt(MB.of(Material.SANDSTONE), MB.of(Material.RED_SANDSTONE))
			.simplexSurface();
	public static final IrisBiome DESERT_HILLS = new IrisBiome("Desert Hills", Biome.DESERT_HILLS)
			.surface(MB.of(Material.SAND))
			.amp(0.75)
			.scatter(MB.of(Material.DEAD_BUSH, 0), 0.08)
			.dirt(MB.of(Material.SANDSTONE));
	public static final IrisBiome MESA = new IrisBiome("Mesa", Biome.MESA)
			.surface(MB.of(Material.HARD_CLAY), MB.of(Material.STAINED_CLAY, 1), MB.of(Material.STAINED_CLAY, 8), MB.of(Material.STAINED_CLAY, 12))
			.dirt(MB.of(Material.CLAY), MB.of(Material.SAND), MB.of(Material.SAND, 1))
			.simplexSurface();
	public static final IrisBiome SAVANNA = new IrisBiome("Savanna", Biome.SAVANNA)
			.tree(TreeType.ACACIA, 0.102)
			.scatter(MB.of(Material.LONG_GRASS, 1), 0.18);
	public static final IrisBiome SAVANNA_HILLS = new IrisBiome("Savanna Hills", Biome.SAVANNA_ROCK)
			.scatter(MB.of(Material.LONG_GRASS, 1), 0.18)
			.tree(TreeType.ACACIA, 0.102)
			.amp(0.75);
	public static final IrisBiome JUNGLE = new IrisBiome("Jungle", Biome.JUNGLE)
			.scatter(MB.of(Material.LONG_GRASS, 1), 0.058)
			.tree(TreeType.JUNGLE, 0.9)
			.tree(TreeType.JUNGLE_BUSH, 0.3)
			.tree(TreeType.SMALL_JUNGLE, 0.1)
			.scatter(MB.of(Material.LONG_GRASS, 2), 0.013);
	public static final IrisBiome JUNGLE_HILLS = new IrisBiome("Jungle Hills", Biome.JUNGLE_HILLS)
			.scatter(MB.of(Material.LONG_GRASS, 1), 0.081)
			.tree(TreeType.JUNGLE, 0.9)
			.tree(TreeType.JUNGLE_BUSH, 0.3)
			.tree(TreeType.SMALL_JUNGLE, 0.1)
			.amp(1.75)
			.height(0.166)
			.scatter(MB.of(Material.LONG_GRASS, 2), 0.02);
	public static final IrisBiome SWAMP = new IrisBiome("Swamp", Biome.SWAMPLAND)
			.scatter(MB.of(Material.LONG_GRASS, 1), 0.04)
			.tree(TreeType.SWAMP, 0.25)
			.scatter(MB.of(Material.LONG_GRASS, 2), 0.03);
	public static final IrisBiome PLAINS = new IrisBiome("Plains", Biome.PLAINS)
			.scatter(MB.of(Material.LONG_GRASS, 1), 0.38)
			.amp(0.4)
			.scatter(MB.of(Material.LONG_GRASS, 2), 0.03);
	public static final IrisBiome DECAYING_PLAINS = new IrisBiome("Decaying Plains", Biome.PLAINS)
			.surface(MB.of(Material.GRASS_PATH), MB.of(Material.GRASS))
			.scatter(MB.of(Material.LONG_GRASS, 1), 0.04)
			.simplexSurface();
	public static final IrisBiome FOREST = new IrisBiome("Forest", Biome.FOREST)
			.scatter(MB.of(Material.LONG_GRASS, 1), 0.23)
			.tree(TreeType.TREE, 0.7)
			.scatter(MB.of(Material.LONG_GRASS, 2), 0.13);
	public static final IrisBiome FOREST_HILLS = new IrisBiome("Forest Hills", Biome.FOREST_HILLS)
			.scatter(MB.of(Material.LONG_GRASS, 1), 0.23)
			.amp(0.75)
			.tree(TreeType.TREE, 0.7)
			.scatter(MB.of(Material.LONG_GRASS, 2), 0.13);
	public static final IrisBiome FOREST_MOUNTAINS = new IrisBiome("Forest Mountains", Biome.MUTATED_EXTREME_HILLS_WITH_TREES)
			.scatter(MB.of(Material.LONG_GRASS, 1), 0.13)
			.amp(2.25)
			.height(0.265)
			.tree(TreeType.MEGA_REDWOOD, 0.5)
			.tree(TreeType.TALL_REDWOOD, 0.7)
			.scatter(MB.of(Material.LONG_GRASS, 2), 0.13);
	public static final IrisBiome HAUNTED_FOREST = new IrisBiome("Haunted Forest", Biome.MUTATED_SWAMPLAND)
			.scatter(MB.of(Material.LONG_GRASS, 1), 0.13)
			.tree(TreeType.JUNGLE_BUSH, 0.5)
			.tree(TreeType.BIG_TREE, 0.4)
			.tree(TreeType.SWAMP, 0.4)
			.tree(TreeType.JUNGLE, 0.4)
			.tree(TreeType.SMALL_JUNGLE, 0.4)
			.tree(TreeType.JUNGLE_BUSH, 0.5)
			.scatter(MB.of(Material.LONG_GRASS, 2), 0.13)
			.surface(MB.of(Material.GRASS), MB.of(Material.GRASS), MB.of(Material.GRASS), MB.of(Material.GRASS), MB.of(Material.DIRT), MB.of(Material.DIRT, 1), MB.of(Material.DIRT, 2))
			.scatterSurface();
	public static final IrisBiome BIRCH_FOREST = new IrisBiome("Birch Forest", Biome.BIRCH_FOREST)
			.scatter(MB.of(Material.LONG_GRASS, 1), 0.23)
			.tree(TreeType.BIRCH, 0.7)
			.scatter(MB.of(Material.LONG_GRASS, 2), 0.13);
	public static final IrisBiome BIRCH_FOREST_HILLS = new IrisBiome("Birch Forest Hills", Biome.BIRCH_FOREST_HILLS)
			.scatter(MB.of(Material.LONG_GRASS, 1), 0.23)
			.tree(TreeType.BIRCH, 0.7)
			.amp(0.75)
			.scatter(MB.of(Material.LONG_GRASS, 2), 0.13);
	public static final IrisBiome ROOFED_FOREST = new IrisBiome("Roofed Forest", Biome.ROOFED_FOREST)
			.scatter(MB.of(Material.LONG_GRASS, 1), 0.23)
			.tree(TreeType.DARK_OAK, 0.9)
			.scatter(MB.of(Material.LONG_GRASS, 2), 0.13);
	public static final IrisBiome TAIGA = new IrisBiome("Taiga", Biome.TAIGA)
			.tree(TreeType.REDWOOD, 0.4)
			.scatter(MB.of(Material.LONG_GRASS, 2), 0.07);
	public static final IrisBiome EXTREME_HILLS = new IrisBiome("Extreme Hills", Biome.EXTREME_HILLS)
			.scatter(MB.of(Material.LONG_GRASS, 2), 0.04)
			.amp(1.565)
			.height(0.22);
	public static final IrisBiome EXTREME_HILLS_TREES = new IrisBiome("Extreme Hills +", Biome.EXTREME_HILLS_WITH_TREES)
			.scatter(MB.of(Material.LONG_GRASS, 2), 0.09)
			.tree(TreeType.REDWOOD, 0.1)
			.amp(1.525)
			.height(0.22);
	public static final IrisBiome TAIGA_COLD = new IrisBiome("Taiga Cold", Biome.TAIGA_COLD)
			.tree(TreeType.REDWOOD, 0.3)
			.scatter(MB.of(Material.LONG_GRASS, 2), 0.04);
	public static final IrisBiome TAIGA_COLD_HILLS = new IrisBiome("Taiga Cold Hills", Biome.TAIGA_COLD_HILLS)
			.tree(TreeType.REDWOOD, 0.15).amp(0.75);
	public static final IrisBiome ICE_FLATS = new IrisBiome("Ice Flats", Biome.ICE_FLATS);
	public static final IrisBiome ICE_MOUNTAINS = new IrisBiome("Ice Mountains", Biome.ICE_MOUNTAINS)
			.amp(1.45);
	public static final IrisBiome REDWOOD_TAIGA = new IrisBiome("Redwood Taiga", Biome.REDWOOD_TAIGA)
			.surface(MB.of(Material.DIRT, 2), MB.of(Material.DIRT, 1))
			.tree(TreeType.TALL_REDWOOD, 0.7)
			.tree(TreeType.MEGA_REDWOOD, 0.6)
			.tree(TreeType.REDWOOD, 0.3)
			.simplexSurface();
	public static final IrisBiome REDWOOD_TAIGA_HILLS = new IrisBiome("Redwood Taiga Hills", Biome.REDWOOD_TAIGA_HILLS)
			.surface(MB.of(Material.DIRT, 2), MB.of(Material.DIRT, 1))
			.amp(0.75)
			.simplexSurface();
	
	//@done
	private static final GMap<Biome, IrisBiome> map = build();
	private String name;
	private Biome realBiome;
	private double height;
	private double amp;
	private GMap<TreeType, Double> treeChance;
	private GList<MB> surface;
	private GList<MB> dirt;
	private GMap<MB, Double> scatterChance;
	private boolean scatterSurface;
	private boolean simplexScatter;
	private GMap<String, Double> schematicGroups;
	private PolygonGenerator.EnumPolygonGenerator<MB> poly;

	public IrisBiome(String name, Biome realBiome)
	{
		this.name = name;
		this.realBiome = realBiome;
		this.height = 0.125;
		this.amp = 0.5;
		scatterChance = new GMap<>();
		schematicGroups = new GMap<>();
		treeChance = new GMap<>();
		surface(new MB(Material.GRASS)).dirt(new MB(Material.DIRT), new MB(Material.DIRT, 1));
	}

	private static GMap<Biome, IrisBiome> build()
	{
		GMap<Biome, IrisBiome> g = new GMap<Biome, IrisBiome>();

		for(Field i : IrisBiome.class.getDeclaredFields())
		{
			J.attempt(() ->
			{
				i.setAccessible(true);

				IrisBiome bb = (IrisBiome) i.get(null);

				if(!g.containsKey(bb.realBiome))
				{
					g.put(bb.realBiome, bb);
				}
			});
		}

		return g;
	}

	public IrisBiome scatter(MB mb, Double chance)
	{
		scatterChance.put(mb, chance);

		return this;
	}

	public IrisBiome tree(TreeType t, Double chance)
	{
		treeChance.put(t, chance);

		return this;
	}

	public IrisBiome schematic(String t, Double chance)
	{
		schematicGroups.put(t, chance);

		return this;
	}

	public IrisBiome simplexSurface()
	{
		simplexScatter = true;
		return this;
	}
	
	public IrisBiome scatterSurface()
	{
		scatterSurface = true;
		return this;
	}

	public IrisBiome surface(MB... mbs)
	{
		surface = new GList<>(mbs);
		return this;
	}

	public IrisBiome dirt(MB... mbs)
	{
		dirt = new GList<>(mbs);
		return this;
	}

	public IrisBiome height(double height)
	{
		this.height = height;
		return this;
	}

	public IrisBiome amp(double amp)
	{
		this.amp = amp;
		return this;
	}

	public String getName()
	{
		return name;
	}

	public Biome getRealBiome()
	{
		return realBiome;
	}

	public double getHeight()
	{
		return height;
	}

	public double getAmp()
	{
		return amp;
	}

	public GList<MB> getSurface()
	{
		return surface;
	}

	public GList<MB> getDirt()
	{
		return dirt;
	}

	public MB getSurface(int x, int z, RNG rng)
	{
		double wx = x + 1000D;
		double wz = z + 1000D;
		if(simplexScatter)
		{
			if(poly == null)
			{
				poly = new PolygonGenerator.EnumPolygonGenerator<MB>(rng, 0.25, 2, getSurface().toArray(new MB[getSurface().size()]), (g) ->
				{
					return g.fractureWith(new CNG(rng.nextParallelRNG(56), 1D, 2).scale(0.0155), 242);
				});
			}

			return poly.getChoice(wx * 0.2D, wz *  0.2D);
		}
		
		if(scatterSurface)
		{
			if(poly == null)
			{
				poly = new PolygonGenerator.EnumPolygonGenerator<MB>(rng, 15.05, 2, getSurface().toArray(new MB[getSurface().size()]), (g) ->
				{
					return g.fractureWith(new CNG(rng.nextParallelRNG(55), 1D, 2).scale(0.0155), 224);
				});
			}

			return poly.getChoice(wx * 0.2D, wz *  0.2D);
		}

		return getSurface().getRandom();
	}

	public MB getDirt(int wx, int wz)
	{
		return getDirt().getRandom();
	}

	public GMap<MB, Double> getScatterChance()
	{
		return scatterChance;
	}

	public MB getScatterChanceSingle()
	{
		for(MB i : getScatterChance().keySet())
		{
			if(M.r(getScatterChance().get(i)))
			{
				return i;
			}
		}

		return MB.of(Material.AIR);
	}

	public GMap<TreeType, Double> getTreeChance()
	{
		return treeChance;
	}

	public TreeType getTreeChanceSingle()
	{
		for(TreeType i : getTreeChance().keySet())
		{
			if(M.r(getTreeChance().get(i)))
			{
				return i;
			}
		}

		return null;
	}

	public String getSchematicChanceSingle()
	{
		for(String i : schematicGroups.keySet())
		{
			if(M.r(schematicGroups.get(i)))
			{
				return i;
			}
		}

		return null;
	}

	public static IrisBiome find(Biome biome)
	{
		if(map.containsKey(biome))
		{
			return map.get(biome);
		}

		return IrisBiome.PLAINS;
	}
}
