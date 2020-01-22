package ninja.bytecode.iris.pack;

import java.lang.reflect.Field;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.block.Biome;

import mortar.util.text.C;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.controller.PackController;
import ninja.bytecode.iris.generator.layer.BiomeNoiseGenerator;
import ninja.bytecode.iris.util.MB;
import ninja.bytecode.iris.util.ObjectMode;
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
	public static final double IDEAL_HEIGHT = 0.138;
	public static final double MIN_HEIGHT = -0.0218;
	private static final GMap<Biome, IrisBiome> map = build();
	private String name;
	private String parent;
	private Biome realBiome;
	private double height;
	private GList<MB> rock;
	private MB fluid;
	private int rockDepth;
	private GList<MB> surface;
	private GList<MB> dirt;
	private GMap<MB, Double> scatterChance;
	private boolean scatterSurface;
	private boolean scatterSurfaceRock;
	private boolean scatterSurfaceSub;
	private boolean core;
	private int dirtDepth;
	private double surfaceScale;
	private double subSurfaceScale;
	private double rockScale;
	private boolean simplexScatter;
	private boolean simplexScatterRock;
	private boolean simplexScatterSub;
	private double snow;
	private double cliffChance;
	private double cliffScale;
	private double genScale;
	private double genAmplifier;
	private double genSwirl;
	private double genSwirlScale;
	private double rarity;
	private boolean cliffs;
	private BiomeNoiseGenerator bng;
	private BiomeType type;
	private String region;
	private GMap<String, Double> schematicGroups;
	private PolygonGenerator.EnumPolygonGenerator<MB> poly;
	private PolygonGenerator.EnumPolygonGenerator<MB> polySub;
	private PolygonGenerator.EnumPolygonGenerator<MB> polyRock;

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
		this.region = "default";
		this.core = false;
		this.name = name;
		type = BiomeType.LAND;
		cliffs = false;
		fluid = MB.of(Material.STATIONARY_WATER);
		genScale = 1;
		rarity = 1;
		genAmplifier = 0.35;
		genSwirl = 1;
		genSwirlScale = 1;
		cliffScale = 1;
		cliffChance = 0.37;
		parent = "";
		dirtDepth = 2;
		this.realBiome = realBiome;
		this.height = IDEAL_HEIGHT;
		rockDepth = 11;
		surfaceScale = 1;
		subSurfaceScale = 1;
		rockScale = 1;
		simplexScatterRock = false;
		scatterSurfaceRock = true;
		simplexScatterSub = false;
		scatterSurfaceSub = true;
		scatterChance = new GMap<>();
		schematicGroups = new GMap<>();
		//@builder
		surface(new MB(Material.GRASS))
		.dirt(new MB(Material.DIRT), new MB(Material.DIRT, 1))
		.rock(MB.of(Material.STONE),
			MB.of(Material.STONE),
			MB.of(Material.STONE),
			MB.of(Material.STONE),
			MB.of(Material.STONE),
			MB.of(Material.STONE),
			MB.of(Material.STONE, 5),
			MB.of(Material.STONE, 5),
			MB.of(Material.COBBLESTONE),
			MB.of(Material.COBBLESTONE),
			MB.of(Material.SMOOTH_BRICK),
			MB.of(Material.SMOOTH_BRICK, 1),
			MB.of(Material.SMOOTH_BRICK, 2),
			MB.of(Material.SMOOTH_BRICK, 3));
		//@done
	}

	public void fromJSON(JSONObject o)
	{
		fromJSON(o, true);
	}

	public IrisBiome seal(RNG rng)
	{
		if(simplexScatter)
		{
			poly = new PolygonGenerator.EnumPolygonGenerator<MB>(rng, 0.125, 2, getSurface().toArray(new MB[getSurface().size()]), (g) ->
			{
				return g.scale(0.09 * surfaceScale).fractureWith(new CNG(rng.nextParallelRNG(56), 1D, 2).scale(0.0955), 55);
			});
		}

		else
		{
			poly = new PolygonGenerator.EnumPolygonGenerator<MB>(rng, 15.05, 2, getSurface().toArray(new MB[getSurface().size()]), (g) ->
			{
				return g.scale(surfaceScale).fractureWith(new CNG(rng.nextParallelRNG(55), 1D, 2).scale(0.0155), 224);
			});
		}

		if(simplexScatterSub)
		{
			polySub = new PolygonGenerator.EnumPolygonGenerator<MB>(rng, 0.125, 2, getDirt().toArray(new MB[getDirt().size()]), (g) ->
			{
				return g.scale(0.06 * subSurfaceScale).fractureWith(new CNG(rng.nextParallelRNG(526), 1D, 2).scale(0.0955), 55);
			});
		}

		else
		{
			polySub = new PolygonGenerator.EnumPolygonGenerator<MB>(rng, 15.05, 2, getDirt().toArray(new MB[getDirt().size()]), (g) ->
			{
				return g.scale(subSurfaceScale).fractureWith(new CNG(rng.nextParallelRNG(515), 1D, 2).scale(0.0155), 224);
			});
		}

		if(simplexScatterRock)
		{
			polyRock = new PolygonGenerator.EnumPolygonGenerator<MB>(rng, 0.125, 2, getRock().toArray(new MB[getRock().size()]), (g) ->
			{
				return g.scale(0.08 * rockScale).fractureWith(new CNG(rng.nextParallelRNG(562), 1D, 2).scale(0.0955), 55);
			});
		}

		else
		{
			polyRock = new PolygonGenerator.EnumPolygonGenerator<MB>(rng, 15.05, 2, getRock().toArray(new MB[getRock().size()]), (g) ->
			{
				return g.scale(rockScale).fractureWith(new CNG(rng.nextParallelRNG(551), 1D, 2).scale(0.0155), 224);
			});
		}

		bng = new BiomeNoiseGenerator(rng.nextParallelRNG(2077), this);

		return this;
	}

	public BiomeNoiseGenerator getGenerator()
	{
		if(polySub == null)
		{
			L.w(getName() + " is not sealed!");
		}

		return bng;
	}

	public void fromJSON(JSONObject o, boolean chain)
	{
		name = o.getString("name");
		realBiome = Biome.valueOf(o.getString("derivative").toUpperCase().replaceAll(" ", "_"));
		type = BiomeType.valueOf(o.getString("type").toUpperCase().replaceAll(" ", "_"));
		J.attempt(() -> region = o.getString("region"));
		J.attempt(() -> parent = o.getString("parent"));
		J.attempt(() -> height(o.getDouble("height")));
		J.attempt(() -> height(o.getDouble("genHeight")));
		J.attempt(() -> genAmplifier = o.getDouble("genAmplifier"));
		J.attempt(() -> genSwirl = o.getDouble("genSwirl"));
		J.attempt(() -> genSwirlScale = o.getDouble("genSwirlScale"));
		J.attempt(() -> genScale = o.getDouble("genScale"));
		J.attempt(() -> snow = o.getDouble("snow"));
		J.attempt(() -> rarity = o.getDouble("rarity"));
		J.attempt(() -> fluid = MB.of(o.getString("fluid")));
		J.attempt(() -> dirtDepth = o.getInt("subSurfaceDepth"));
		J.attempt(() -> dirtDepth = o.getInt("dirtDepth"));
		J.attempt(() -> rockDepth = o.getInt("rockDepth"));
		J.attempt(() -> cliffScale = o.getDouble("cliffScale"));
		J.attempt(() -> rockScale = o.getDouble("rockScale"));
		J.attempt(() -> surfaceScale = o.getDouble("surfaceScale"));
		J.attempt(() -> subSurfaceScale = o.getDouble("subSurfaceScale"));
		J.attempt(() -> cliffChance = o.getDouble("cliffChance"));
		J.attempt(() -> cliffs = o.getBoolean("cliffs"));
		J.attempt(() -> surface = mbListFromJSON(o.getJSONArray("surface")));
		J.attempt(() -> rock = mbListFromJSON(o.getJSONArray("rock")));
		J.attempt(() -> dirt = mbListFromJSON(o.getJSONArray("subSurface")));
		J.attempt(() -> dirt = mbListFromJSON(o.getJSONArray("dirt")));
		J.attempt(() -> scatterChance = scatterFromJSON(o.getJSONArray("scatter")));
		J.attempt(() -> simplexScatter = o.getString("surfaceType").equalsIgnoreCase("simplex"));
		J.attempt(() -> scatterSurface = o.getString("surfaceType").equalsIgnoreCase("scatter"));
		J.attempt(() -> simplexScatterRock = o.getString("rockType").equalsIgnoreCase("simplex"));
		J.attempt(() -> scatterSurfaceRock = o.getString("rockType").equalsIgnoreCase("scatter"));
		J.attempt(() -> simplexScatterSub = o.getString("subSurfaceType").equalsIgnoreCase("simplex"));
		J.attempt(() -> scatterSurfaceSub = o.getString("subSurfaceType").equalsIgnoreCase("scatter"));
		J.attempt(() ->
		{
			if(!Iris.settings.performance.objectMode.equals(ObjectMode.NONE))
			{
				schematicGroups = strFromJSON(o.getJSONArray("objects"));
			}

			else
			{
				schematicGroups = new GMap<>();
			}

			if(chain)
			{
				if(!Iris.settings.performance.objectMode.equals(ObjectMode.NONE))
				{
					for(String i : schematicGroups.k())
					{
						Iris.getController(PackController.class).loadSchematicGroup(i);
					}
				}
			}
		});
	}

	public JSONObject toJSON()
	{
		JSONObject j = new JSONObject();
		j.put("name", name);
		J.attempt(() -> j.put("parent", parent));
		J.attempt(() -> j.put("region", region));
		J.attempt(() -> j.put("derivative", realBiome.name().toLowerCase().replaceAll("_", " ")));
		J.attempt(() -> j.put("type", type.name().toLowerCase().replaceAll("_", " ")));
		J.attempt(() -> j.put("rarity", rarity));
		J.attempt(() -> j.put("fluid", fluid.toString()));
		J.attempt(() -> j.put("genHeight", height));
		J.attempt(() -> j.put("genScale", genScale));
		J.attempt(() -> j.put("genSwirl", genSwirl));
		J.attempt(() -> j.put("genSwirlScale", genSwirlScale));
		J.attempt(() -> j.put("genAmplifier", genAmplifier));
		J.attempt(() -> j.put("snow", snow));
		J.attempt(() -> j.put("cliffs", cliffs));
		J.attempt(() -> j.put("cliffScale", cliffScale));
		J.attempt(() -> j.put("rockScale", rockScale));
		J.attempt(() -> j.put("subSurfaceScale", subSurfaceScale));
		J.attempt(() -> j.put("surfaceScale", surfaceScale));
		J.attempt(() -> j.put("cliffChance", cliffChance));
		J.attempt(() -> j.put("surface", mbListToJSON(surface)));
		J.attempt(() -> j.put("rock", mbListToJSON(rock)));
		J.attempt(() -> j.put("subSurfaceDepth", dirtDepth));
		J.attempt(() -> j.put("rockDepth", rockDepth));
		J.attempt(() -> j.put("subSurface", mbListToJSON(dirt)));
		J.attempt(() -> j.put("scatter", scatterToJson(scatterChance)));
		J.attempt(() -> j.put("surfaceType", simplexScatter ? "simplex" : scatterSurface ? "scatter" : "na"));
		J.attempt(() -> j.put("subSurfaceType", simplexScatterSub ? "simplex" : scatterSurfaceSub ? "scatter" : "na"));
		J.attempt(() -> j.put("rockType", simplexScatterRock ? "simplex" : scatterSurfaceRock ? "scatter" : "na"));
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

	public IrisBiome rock(MB... mbs)
	{
		rock = new GList<>(mbs);
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
			this.height = M.lerp(MIN_HEIGHT, IDEAL_HEIGHT, 1d - Math.abs(M.clip(height, -1D, 0D)));
		}

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

	public GList<MB> getSurface()
	{
		return surface;
	}

	public GList<MB> getRock()
	{
		return rock;
	}

	public GList<MB> getDirt()
	{
		return dirt;
	}

	public MB getSurface(double x, double z, RNG rng)
	{
		double wx = x + 1000D;
		double wz = z + 1000D;

		if(polySub == null)
		{
			L.w(getName() + " is not sealed!");
		}

		if(simplexScatter)
		{
			return poly.getChoice(wx / 3, wz / 3);
		}

		if(scatterSurface)
		{
			return poly.getChoice(wx * 0.2D, wz * 0.2D);
		}

		return getSurface().getRandom();
	}

	public MB getSubSurface(double x, double i, double z, RNG rng)
	{
		double wx = x + 1000D;
		double wz = z + 1000D;

		if(polySub == null)
		{
			L.w(getName() + " is not sealed!");
		}

		if(simplexScatterSub)
		{
			return polySub.getChoice(wx / 3, i / 3, wz / 3);
		}

		if(scatterSurfaceSub)
		{
			return polySub.getChoice(wx * 0.2D, i / 3, wz * 0.2D);
		}

		return getSurface().getRandom();
	}

	public MB getRock(double x, double i, double z, RNG rng)
	{
		double wx = x + 1000D;
		double wz = z + 1000D;

		if(polySub == null)
		{
			L.w(getName() + " is not sealed!");
		}

		if(simplexScatterRock)
		{
			return polyRock.getChoice(wx / 3, i / 3, wz / 3);
		}

		if(scatterSurfaceRock)
		{
			return polyRock.getChoice(wx * 0.2D, i * 0.2D, wz * 0.2D);
		}

		return getSurface().getRandom();
	}

	public GMap<MB, Double> getScatterChance()
	{
		return scatterChance;
	}

	public MB getScatterChanceSingle(double d)
	{
		for(MB i : getScatterChance().keySet())
		{
			if(d < getScatterChance().get(i))
			{
				return i;
			}
		}

		return MB.of(Material.AIR);
	}

	public static GList<IrisBiome> getBiomes()
	{
		return map.v();
	}

	public static IrisBiome findByBiome(Biome biome)
	{
		if(map.containsKey(biome))
		{
			return map.get(biome);
		}

		return null;
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

	public String getRegionID()
	{
		return region;
	}

	public boolean isSnowy()
	{
		return getSnow() > 0;
	}

	public double getSnow()
	{
		return snow;
	}

	public double getCliffScale()
	{
		return cliffScale;
	}

	public boolean hasCliffs()
	{
		return cliffs;
	}

	public int getDirtDepth()
	{
		return dirtDepth;
	}

	public int getRockDepth()
	{
		return rockDepth;
	}

	public boolean isCliffs()
	{
		return cliffs;
	}

	public double getCliffChance()
	{
		return cliffChance;
	}

	public String getParent()
	{
		return parent;
	}

	public GList<String> getParents()
	{
		GList<String> f = new GList<>();

		if(getParent().trim().isEmpty())
		{
			return f;
		}

		if(getParent().contains("&"))
		{
			for(String i : getParent().split("\\Q&\\E"))
			{
				f.add(i.trim());
			}
		}

		else
		{
			f.add(getParent().trim());
		}

		return f;
	}

	public boolean isScatterSurfaceRock()
	{
		return scatterSurfaceRock;
	}

	public boolean isScatterSurfaceSub()
	{
		return scatterSurfaceSub;
	}

	public double getSurfaceScale()
	{
		return surfaceScale;
	}

	public double getSubSurfaceScale()
	{
		return subSurfaceScale;
	}

	public double getRockScale()
	{
		return rockScale;
	}

	public boolean isSimplexScatterRock()
	{
		return simplexScatterRock;
	}

	public boolean isSimplexScatterSub()
	{
		return simplexScatterSub;
	}

	public BiomeType getType()
	{
		return type;
	}

	public PolygonGenerator.EnumPolygonGenerator<MB> getPolySub()
	{
		return polySub;
	}

	public PolygonGenerator.EnumPolygonGenerator<MB> getPolyRock()
	{
		return polyRock;
	}

	public double getGenScale()
	{
		return genScale;
	}

	public void setGenScale(double genScale)
	{
		this.genScale = genScale;
	}

	public double getGenAmplifier()
	{
		return genAmplifier;
	}

	public void setGenAmplifier(double genAmplifier)
	{
		this.genAmplifier = genAmplifier;
	}

	public double getGenSwirl()
	{
		return genSwirl;
	}

	public void setGenSwirl(double genSwirl)
	{
		this.genSwirl = genSwirl;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public void setParent(String parent)
	{
		this.parent = parent;
	}

	public void setRealBiome(Biome realBiome)
	{
		this.realBiome = realBiome;
	}

	public void setHeight(double height)
	{
		this.height = height;
	}

	public void setRock(GList<MB> rock)
	{
		this.rock = rock;
	}

	public void setRockDepth(int rockDepth)
	{
		this.rockDepth = rockDepth;
	}

	public void setSurface(GList<MB> surface)
	{
		this.surface = surface;
	}

	public void setDirt(GList<MB> dirt)
	{
		this.dirt = dirt;
	}

	public void setScatterChance(GMap<MB, Double> scatterChance)
	{
		this.scatterChance = scatterChance;
	}

	public void setScatterSurface(boolean scatterSurface)
	{
		this.scatterSurface = scatterSurface;
	}

	public void setScatterSurfaceRock(boolean scatterSurfaceRock)
	{
		this.scatterSurfaceRock = scatterSurfaceRock;
	}

	public void setScatterSurfaceSub(boolean scatterSurfaceSub)
	{
		this.scatterSurfaceSub = scatterSurfaceSub;
	}

	public void setCore(boolean core)
	{
		this.core = core;
	}

	public void setDirtDepth(int dirtDepth)
	{
		this.dirtDepth = dirtDepth;
	}

	public void setSurfaceScale(double surfaceScale)
	{
		this.surfaceScale = surfaceScale;
	}

	public void setSubSurfaceScale(double subSurfaceScale)
	{
		this.subSurfaceScale = subSurfaceScale;
	}

	public void setRockScale(double rockScale)
	{
		this.rockScale = rockScale;
	}

	public void setSimplexScatter(boolean simplexScatter)
	{
		this.simplexScatter = simplexScatter;
	}

	public void setSimplexScatterRock(boolean simplexScatterRock)
	{
		this.simplexScatterRock = simplexScatterRock;
	}

	public void setSimplexScatterSub(boolean simplexScatterSub)
	{
		this.simplexScatterSub = simplexScatterSub;
	}

	public void setSnow(double snow)
	{
		this.snow = snow;
	}

	public void setCliffChance(double cliffChance)
	{
		this.cliffChance = cliffChance;
	}

	public void setCliffScale(double cliffScale)
	{
		this.cliffScale = cliffScale;
	}

	public void setCliffs(boolean cliffs)
	{
		this.cliffs = cliffs;
	}

	public void setType(BiomeType type)
	{
		this.type = type;
	}

	public void setRegion(String region)
	{
		this.region = region;
	}

	public void setSchematicGroups(GMap<String, Double> schematicGroups)
	{
		this.schematicGroups = schematicGroups;
	}

	public void setPoly(PolygonGenerator.EnumPolygonGenerator<MB> poly)
	{
		this.poly = poly;
	}

	public void setPolySub(PolygonGenerator.EnumPolygonGenerator<MB> polySub)
	{
		this.polySub = polySub;
	}

	public void setPolyRock(PolygonGenerator.EnumPolygonGenerator<MB> polyRock)
	{
		this.polyRock = polyRock;
	}

	public double getGenSwirlScale()
	{
		return genSwirlScale;
	}

	public void setGenSwirlScale(double genSwirlScale)
	{
		this.genSwirlScale = genSwirlScale;
	}

	public BiomeNoiseGenerator getBng()
	{
		return bng;
	}

	public void setBng(BiomeNoiseGenerator bng)
	{
		this.bng = bng;
	}

	public double getRarity()
	{
		return rarity;
	}

	public String getRarityString()
	{
		if(getRarity() <= 0.1)
		{
			return C.RED + "Literally Everywhere";
		}

		else if(getRarity() < 0.25)
		{
			return "Overly Abundant";
		}

		else if(getRarity() < 0.4)
		{
			return "Very Abundant";
		}

		else if(getRarity() < 0.6)
		{
			return "Abundant";
		}

		else if(getRarity() < 0.75)
		{
			return "Very Common";
		}

		else if(getRarity() <= 1)
		{
			return "Common";
		}

		else if(getRarity() <= 2)
		{
			return "Often";
		}

		else if(getRarity() <= 3)
		{
			return "Uncommon";
		}

		else if(getRarity() <= 6)
		{
			return "Rare";
		}

		else if(getRarity() <= 16)
		{
			return "Very Rare";
		}

		else if(getRarity() <= 50)
		{
			return "Exceedingly Rare";
		}

		else if(getRarity() <= 100)
		{
			return "Marvelously Rare";
		}

		else if(getRarity() <= 200)
		{
			return "Extraordinarily Rare";
		}

		else
		{
			return "Start Praying";
		}
	}

	public MB getFluid()
	{
		return fluid;
	}

	public void setFluid(MB fluid)
	{
		this.fluid = fluid;
	}

	public void setRarity(double rarity)
	{
		this.rarity = rarity;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(cliffChance, cliffScale, cliffs, core, dirt, dirtDepth, fluid, genAmplifier, genScale, genSwirl, genSwirlScale, height, name, parent, rarity, realBiome, region, rock, rockDepth, rockScale, scatterChance, scatterSurface, scatterSurfaceRock, scatterSurfaceSub, schematicGroups, simplexScatter, simplexScatterRock, simplexScatterSub, snow, subSurfaceScale, surface, surfaceScale, type);
	}

	@Override
	public boolean equals(Object obj)
	{
		if(this == obj)
		{
			return true;
		}
		if(!(obj instanceof IrisBiome))
		{
			return false;
		}
		IrisBiome other = (IrisBiome) obj;
		return Double.doubleToLongBits(cliffChance) == Double.doubleToLongBits(other.cliffChance) && Double.doubleToLongBits(cliffScale) == Double.doubleToLongBits(other.cliffScale) && cliffs == other.cliffs && core == other.core && Objects.equals(dirt, other.dirt) && dirtDepth == other.dirtDepth && Objects.equals(fluid, other.fluid) && Double.doubleToLongBits(genAmplifier) == Double.doubleToLongBits(other.genAmplifier) && Double.doubleToLongBits(genScale) == Double.doubleToLongBits(other.genScale) && Double.doubleToLongBits(genSwirl) == Double.doubleToLongBits(other.genSwirl) && Double.doubleToLongBits(genSwirlScale) == Double.doubleToLongBits(other.genSwirlScale) && Double.doubleToLongBits(height) == Double.doubleToLongBits(other.height) && Objects.equals(name, other.name) && Objects.equals(parent, other.parent) && Double.doubleToLongBits(rarity) == Double.doubleToLongBits(other.rarity) && realBiome == other.realBiome && Objects.equals(region, other.region) && Objects.equals(rock, other.rock) && rockDepth == other.rockDepth && Double.doubleToLongBits(rockScale) == Double.doubleToLongBits(other.rockScale) && Objects.equals(scatterChance, other.scatterChance) && scatterSurface == other.scatterSurface && scatterSurfaceRock == other.scatterSurfaceRock && scatterSurfaceSub == other.scatterSurfaceSub && Objects.equals(schematicGroups, other.schematicGroups) && simplexScatter == other.simplexScatter && simplexScatterRock == other.simplexScatterRock && simplexScatterSub == other.simplexScatterSub && Double.doubleToLongBits(snow) == Double.doubleToLongBits(other.snow) && Double.doubleToLongBits(subSurfaceScale) == Double.doubleToLongBits(other.subSurfaceScale) && Objects.equals(surface, other.surface) && Double.doubleToLongBits(surfaceScale) == Double.doubleToLongBits(other.surfaceScale) && type == other.type;
	}
}
