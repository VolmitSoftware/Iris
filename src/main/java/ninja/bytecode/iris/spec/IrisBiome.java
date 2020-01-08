package ninja.bytecode.iris.spec;

import java.lang.reflect.Field;

import org.bukkit.Material;
import org.bukkit.block.Biome;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.util.MB;
import ninja.bytecode.iris.util.PolygonGenerator;
import ninja.bytecode.shuriken.collections.GList;
import ninja.bytecode.shuriken.collections.GMap;
import ninja.bytecode.shuriken.execution.J;
import ninja.bytecode.shuriken.json.JSONArray;
import ninja.bytecode.shuriken.json.JSONObject;
import ninja.bytecode.shuriken.logging.L;
import ninja.bytecode.shuriken.math.CNG;
import ninja.bytecode.shuriken.math.M;
import ninja.bytecode.shuriken.math.RNG;

public class IrisBiome
{
	public static final double MAX_HEIGHT = 0.77768;
	public static final double IDEAL_HEIGHT = 0.1127;
	public static final double MIN_HEIGHT = -0.0218;

	//@builder
	private static final IrisBiome RIVER = new IrisBiome("River", Biome.RIVER)
			.surface(MB.of(Material.SAND))
			.coreBiome();
	private static final IrisBiome BEACH = new IrisBiome("Beach", Biome.BEACHES)
			.height(-0.078)
			.coreBiome()
			.surface(MB.of(Material.SAND));
	public static final IrisBiome ROAD_GRAVEL = new IrisBiome("Gravel Road", Biome.PLAINS)
			.surface(MB.of(Material.GRAVEL), MB.of(Material.COBBLESTONE))
			.coreBiome()
			.scatter(MB.of(Material.TORCH), 0.05);
	public static final IrisBiome ROAD_GRASSY = new IrisBiome("Grass Path", Biome.PLAINS)
			.surface(MB.of(Material.GRASS_PATH))
			.coreBiome()
			.scatter(MB.of(Material.TORCH), 0.05);
	private static final IrisBiome OCEAN = new IrisBiome("Ocean", Biome.OCEAN)
			.height(-0.5)
			.coreBiome()
			.surface(MB.of(Material.SAND), MB.of(Material.SAND), MB.of(Material.SAND), MB.of(Material.CLAY), MB.of(Material.GRAVEL))
			.simplexSurface();
	private static final IrisBiome DEEP_OCEAN = new IrisBiome("Deep Ocean", Biome.DEEP_OCEAN)
			.height(-0.88)
			.coreBiome()
			.surface(MB.of(Material.SAND), MB.of(Material.CLAY), MB.of(Material.GRAVEL))
			.simplexSurface();

	//@done
	private static final GMap<Biome, IrisBiome> map = build();
	private String name;
	private Biome realBiome;
	private double height;
	private double amp;
	private GList<MB> surface;
	private GList<MB> dirt;
	private GMap<MB, Double> scatterChance;
	private boolean scatterSurface;
	private boolean core;
	private boolean simplexScatter;
	private GMap<String, Double> schematicGroups;
	private PolygonGenerator.EnumPolygonGenerator<MB> poly;

	public static double getMaxHeight()
	{
		return MAX_HEIGHT;
	}

	public static double getIdealHeight()
	{
		return IDEAL_HEIGHT;
	}

	public static double getMinHeight()
	{
		return MIN_HEIGHT;
	}

	public static IrisBiome getRiver()
	{
		return RIVER;
	}

	public static IrisBiome getBeach()
	{
		return BEACH;
	}

	public static IrisBiome getRoadGravel()
	{
		return ROAD_GRAVEL;
	}

	public static IrisBiome getRoadGrassy()
	{
		return ROAD_GRASSY;
	}

	public static IrisBiome getOcean()
	{
		return OCEAN;
	}

	public static IrisBiome getDeepOcean()
	{
		return DEEP_OCEAN;
	}

	public static GMap<Biome, IrisBiome> getMap()
	{
		return map;
	}

	public boolean isScatterSurface()
	{
		return scatterSurface;
	}

	public boolean isCore()
	{
		return core;
	}

	public boolean isSimplexScatter()
	{
		return simplexScatter;
	}

	public PolygonGenerator.EnumPolygonGenerator<MB> getPoly()
	{
		return poly;
	}

	public IrisBiome(JSONObject json)
	{
		this("Loading", Biome.OCEAN);
		fromJSON(json);
	}

	public IrisBiome(String name, Biome realBiome)
	{
		this.core = false;
		this.name = name;
		this.realBiome = realBiome;
		this.height = IDEAL_HEIGHT;
		this.amp = 0.31;
		scatterChance = new GMap<>();
		schematicGroups = new GMap<>();
		surface(new MB(Material.GRASS)).dirt(new MB(Material.DIRT), new MB(Material.DIRT, 1));
	}

	public void fromJSON(JSONObject o)
	{
		name = o.getString("name");
		realBiome = Biome.valueOf(o.getString("derivative").toUpperCase().replaceAll(" ", "_"));
		J.attempt(() -> height = o.getDouble("height"));
		J.attempt(() -> surface = mbListFromJSON(o.getJSONArray("surface")));
		J.attempt(() -> dirt = mbListFromJSON(o.getJSONArray("dirt")));
		J.attempt(() -> scatterChance = scatterFromJSON(o.getJSONArray("scatter")));
		J.attempt(() -> simplexScatter = o.getString("surfaceType").equalsIgnoreCase("simplex"));
		J.attempt(() -> scatterSurface = o.getString("surfaceType").equalsIgnoreCase("scatter"));
		J.attempt(() ->
		{
			schematicGroups = strFromJSON(o.getJSONArray("objects"));

			for(String i : schematicGroups.k())
			{
				L.v("Loading Object Group: " + i);
				Iris.loadSchematicGroup(i);
			}
		});
	}

	public JSONObject toJSON()
	{
		JSONObject j = new JSONObject();
		j.put("name", name);
		J.attempt(() -> j.put("derivative", realBiome.name().toLowerCase().replaceAll("_", " ")));
		J.attempt(() -> j.put("height", height));
		J.attempt(() -> j.put("surface", mbListToJSON(surface)));
		J.attempt(() -> j.put("dirt", mbListToJSON(dirt)));
		J.attempt(() -> j.put("scatter", scatterToJson(scatterChance)));
		J.attempt(() -> j.put("surfaceType", simplexScatter ? "simplex" : scatterSurface ? "scatter" : "na"));
		J.attempt(() -> j.put("objects", strToJson(schematicGroups)));

		return j;
	}

	private GList<MB> mbListFromJSON(JSONArray ja)
	{
		GList<MB> mb = new GList<>();

		for(int i = 0; i < ja.length(); i++)
		{
			mb.add(MB.of(ja.getString(i)));
		}

		return mb;
	}

	private JSONArray mbListToJSON(GList<MB> mbs)
	{
		JSONArray ja = new JSONArray();

		for(MB i : mbs)
		{
			ja.put(i.toString());
		}

		return ja;
	}

	public IrisBiome coreBiome()
	{
		this.core = true;
		return this;
	}

	private GMap<MB, Double> scatterFromJSON(JSONArray ja)
	{
		GMap<MB, Double> mb = new GMap<MB, Double>();

		for(int i = 0; i < ja.length(); i++)
		{
			String s = ja.getString(i);
			mb.put(MB.of(s.split("\\Q=\\E")[0]), Double.valueOf(s.split("\\Q=\\E")[1]));
		}

		return mb;
	}

	private JSONArray scatterToJson(GMap<MB, Double> mbs)
	{
		JSONArray ja = new JSONArray();

		for(MB i : mbs.k())
		{
			ja.put(i.toString() + "=" + mbs.get(i));
		}

		return ja;
	}

	private GMap<String, Double> strFromJSON(JSONArray ja)
	{
		GMap<String, Double> mb = new GMap<String, Double>();

		for(int i = 0; i < ja.length(); i++)
		{
			String s = ja.getString(i);
			mb.put(s.split("\\Q=\\E")[0], Double.valueOf(s.split("\\Q=\\E")[1]));
		}

		return mb;
	}

	private JSONArray strToJson(GMap<String, Double> mbs)
	{
		JSONArray ja = new JSONArray();

		for(String i : mbs.k())
		{
			ja.put(i.toString() + "=" + mbs.get(i));
		}

		return ja;
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

	public IrisBiome schematic(String t, double chance)
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
		if(height >= 0)
		{
			this.height = M.lerp(IDEAL_HEIGHT, MAX_HEIGHT, M.clip(height, 0D, 1D));
		}

		else
		{
			this.height = M.lerp(MIN_HEIGHT, IDEAL_HEIGHT, M.clip(height, -1D, 0D));
		}

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

	public MB getSurface(double x, double z, RNG rng)
	{
		double wx = x + 1000D;
		double wz = z + 1000D;
		if(simplexScatter)
		{
			if(poly == null)
			{
				poly = new PolygonGenerator.EnumPolygonGenerator<MB>(rng, 0.125, 2, getSurface().toArray(new MB[getSurface().size()]), (g) ->
				{
					return g.scale(0.05).fractureWith(new CNG(rng.nextParallelRNG(56), 1D, 2).scale(0.0955), 55);
				});
			}

			return poly.getChoice(wx / 3, wz / 3);
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

			return poly.getChoice(wx * 0.2D, wz * 0.2D);
		}

		return getSurface().getRandom();
	}

	public MB getDirtRNG()
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

	public static GList<IrisBiome> getBiomes()
	{
		return map.v().remove(IrisBiome.BEACH, IrisBiome.OCEAN, IrisBiome.DEEP_OCEAN, IrisBiome.ROAD_GRASSY, IrisBiome.ROAD_GRAVEL, IrisBiome.BEACH, IrisBiome.RIVER);
	}

	public static GList<IrisBiome> getAllBiomes()
	{
		return map.v();
	}

	public static IrisBiome findByBiome(Biome biome)
	{
		if(map.containsKey(biome))
		{
			return map.get(biome);
		}

		return IrisBiome.OCEAN;
	}

	public GMap<String, Double> getSchematicGroups()
	{
		return schematicGroups;
	}

	public boolean isSurface(Material t)
	{
		for(MB i : surface)
		{
			if(i.material.equals(t))
			{
				return true;
			}
		}
		
		return false;
	}
}
