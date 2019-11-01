package ninja.bytecode.iris.biome;

import org.bukkit.Material;
import org.bukkit.TreeSpecies;
import org.bukkit.TreeType;
import org.bukkit.block.Biome;

import ninja.bytecode.iris.MB;
import ninja.bytecode.iris.util.PolygonGenerator;
import ninja.bytecode.shuriken.collections.GList;
import ninja.bytecode.shuriken.collections.GMap;
import ninja.bytecode.shuriken.math.CNG;
import ninja.bytecode.shuriken.math.M;
import ninja.bytecode.shuriken.math.RNG;

public class CBI
{
	//@builder
	public static final CBI RIVER = new CBI("River", Biome.RIVER)
			.surface(MB.of(Material.SAND))
			.height(0.085);
	public static final CBI OCEAN = new CBI("Ocean", Biome.OCEAN)
			.surface(MB.of(Material.SAND))
			.height(-0.07);
	public static final CBI DEEP_OCEAN = new CBI("Deep Ocean", Biome.DEEP_OCEAN)
			.surface(MB.of(Material.SAND))
			.height(-0.07);
	public static final CBI DESERT = new CBI("Desert", Biome.DESERT)
			.surface(MB.of(Material.SAND))
			.scatter(MB.of(Material.DEAD_BUSH, 0), 0.08)
			.dirt(MB.of(Material.SANDSTONE));
	public static final CBI DESERT_RED = new CBI("Red Desert", Biome.DESERT)
			.surface(MB.of(Material.SAND, 1))
			.scatter(MB.of(Material.DEAD_BUSH, 0), 0.08)
			.dirt(MB.of(Material.RED_SANDSTONE));
	public static final CBI DESERT_COMBINED = new CBI("Combined Desert", Biome.DESERT)
			.surface(MB.of(Material.SAND), MB.of(Material.SAND, 1))
			.scatter(MB.of(Material.DEAD_BUSH, 0), 0.08)
			.dirt(MB.of(Material.SANDSTONE), MB.of(Material.RED_SANDSTONE))
			.simplexSurface();
	public static final CBI DESERT_HILLS = new CBI("Desert Hills", Biome.DESERT_HILLS)
			.surface(MB.of(Material.SAND))
			.amp(0.75)
			.scatter(MB.of(Material.DEAD_BUSH, 0), 0.08)
			.dirt(MB.of(Material.SANDSTONE));
	public static final CBI MESA = new CBI("Mesa", Biome.MESA)
			.surface(MB.of(Material.HARD_CLAY), MB.of(Material.STAINED_CLAY, 1), MB.of(Material.STAINED_CLAY, 8), MB.of(Material.STAINED_CLAY, 12))
			.dirt(MB.of(Material.CLAY), MB.of(Material.SAND), MB.of(Material.SAND, 1))
			.simplexSurface();
	public static final CBI SAVANNA = new CBI("Savanna", Biome.SAVANNA)
			.scatter(MB.of(Material.LONG_GRASS, 1), 0.18)
			.tree(TreeType.ACACIA, 0.2);
	public static final CBI SAVANNA_HILLS = new CBI("Savanna Hills", Biome.SAVANNA_ROCK)
			.scatter(MB.of(Material.LONG_GRASS, 1), 0.18)
			.amp(0.75)
			.tree(TreeType.ACACIA, 0.2);
	public static final CBI JUNGLE = new CBI("Jungle", Biome.JUNGLE)
			.scatter(MB.of(Material.LONG_GRASS, 1), 0.58)
			.scatter(MB.of(Material.LONG_GRASS, 2), 0.13);
	public static final CBI JUNGLE_HILLS = new CBI("Jungle Hills", Biome.JUNGLE_HILLS)
			.scatter(MB.of(Material.LONG_GRASS, 1), 0.58)
			.amp(0.75)
			.scatter(MB.of(Material.LONG_GRASS, 2), 0.13);
	public static final CBI SWAMP = new CBI("Swamp", Biome.SWAMPLAND)
			.scatter(MB.of(Material.LONG_GRASS, 1), 0.04)
			.scatter(MB.of(Material.LONG_GRASS, 2), 0.03);
	public static final CBI PLAINS = new CBI("Plains", Biome.PLAINS)
			.scatter(MB.of(Material.LONG_GRASS, 1), 0.38)
			.amp(0.4)
			.scatter(MB.of(Material.LONG_GRASS, 2), 0.03);
	public static final CBI DECAYING_PLAINS = new CBI("Decaying Plains", Biome.PLAINS)
			.surface(MB.of(Material.GRASS_PATH), MB.of(Material.GRASS))
			.scatter(MB.of(Material.LONG_GRASS, 1), 0.04)
			.simplexSurface();
	public static final CBI FOREST = new CBI("Forest", Biome.FOREST)
			.scatter(MB.of(Material.LONG_GRASS, 1), 0.23)
			.scatter(MB.of(Material.LONG_GRASS, 2), 0.13);
	public static final CBI FOREST_HILLS = new CBI("Forest Hills", Biome.FOREST_HILLS)
			.scatter(MB.of(Material.LONG_GRASS, 1), 0.23)
			.amp(0.75)
			.scatter(MB.of(Material.LONG_GRASS, 2), 0.13);
	public static final CBI BIRCH_FOREST = new CBI("Birch Forest", Biome.BIRCH_FOREST)
			.scatter(MB.of(Material.LONG_GRASS, 1), 0.23)
			.scatter(MB.of(Material.LONG_GRASS, 2), 0.13);
	public static final CBI BIRCH_FOREST_HILLS = new CBI("Birch Forest Hills", Biome.BIRCH_FOREST_HILLS)
			.scatter(MB.of(Material.LONG_GRASS, 1), 0.23)
			.amp(0.75)
			.scatter(MB.of(Material.LONG_GRASS, 2), 0.13);
	public static final CBI ROOFED_FOREST = new CBI("Roofed Forest", Biome.ROOFED_FOREST)
			.scatter(MB.of(Material.LONG_GRASS, 1), 0.23)
			.scatter(MB.of(Material.LONG_GRASS, 2), 0.13);
	public static final CBI TAIGA = new CBI("Taiga", Biome.TAIGA)
			.scatter(MB.of(Material.LONG_GRASS, 2), 0.07);
	public static final CBI EXTREME_HILLS = new CBI("Extreme Hills", Biome.EXTREME_HILLS)
			.scatter(MB.of(Material.LONG_GRASS, 2), 0.04);
	public static final CBI EXTREME_HILLS_TREES = new CBI("Extreme Hills +", Biome.EXTREME_HILLS_WITH_TREES)
			.scatter(MB.of(Material.LONG_GRASS, 2), 0.09)
			.amp(1.25);
	public static final CBI TAIGA_COLD = new CBI("Taiga Cold", Biome.TAIGA_COLD)
			.scatter(MB.of(Material.LONG_GRASS, 2), 0.04);
	public static final CBI TAIGA_COLD_HILLS = new CBI("Taiga Cold Hills", Biome.TAIGA_COLD_HILLS)
			.amp(0.75);
	public static final CBI ICE_FLATS = new CBI("Ice Flats", Biome.ICE_FLATS);
	public static final CBI ICE_MOUNTAINS = new CBI("Ice Mountains", Biome.ICE_MOUNTAINS)
			.amp(1.45);
	public static final CBI REDWOOD_TAIGA = new CBI("Redwood Taiga", Biome.REDWOOD_TAIGA)
			.surface(MB.of(Material.DIRT, 2), MB.of(Material.DIRT, 1))
			.simplexSurface();
	public static final CBI REDWOOD_TAIGA_HILLS = new CBI("Redwood Taiga Hills", Biome.REDWOOD_TAIGA_HILLS)
			.surface(MB.of(Material.DIRT, 2), MB.of(Material.DIRT, 1))
			.amp(0.75)
			.simplexSurface();

	//@done

	private String name;
	private Biome realBiome;
	private double height;
	private double amp;
	private GMap<TreeType, Double> treeChance;
	private GList<MB> surface;
	private GList<MB> dirt;
	private GMap<MB, Double> scatterChance;
	private boolean simplexScatter;
	private PolygonGenerator.EnumPolygonGenerator<MB> poly;

	public CBI(String name, Biome realBiome)
	{
		this.name = name;
		this.realBiome = realBiome;
		this.height = 0.125;
		this.amp = 0.5;
		scatterChance = new GMap<>();
		treeChance = new GMap<>();
		surface(new MB(Material.GRASS)).dirt(new MB(Material.DIRT), new MB(Material.DIRT, 1));
	}

	public CBI scatter(MB mb, Double chance)
	{
		scatterChance.put(mb, chance);

		return this;
	}

	public CBI tree(TreeType t, Double chance)
	{
		treeChance.put(t, chance);

		return this;
	}

	public CBI simplexSurface()
	{
		simplexScatter = true;
		return this;
	}

	public CBI surface(MB... mbs)
	{
		surface = new GList<>(mbs);
		return this;
	}

	public CBI dirt(MB... mbs)
	{
		dirt = new GList<>(mbs);
		return this;
	}

	public CBI height(double height)
	{
		this.height = height;
		return this;
	}

	public CBI amp(double amp)
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

	public MB getSurface(int wx, int wz, RNG rng)
	{
		if(simplexScatter)
		{
			if(poly == null)
			{
				poly = new PolygonGenerator.EnumPolygonGenerator<MB>(rng, 0.05, 12, getSurface().toArray(new MB[getSurface().size()]), (g) ->
				{
					return g.fractureWith(new CNG(rng.nextRNG(), 1D, 2).scale(0.155), 24);
				});
			}

			return poly.getChoice(wx, wz);
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
}
