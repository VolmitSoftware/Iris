package com.volmit.iris.object;

import com.volmit.iris.generator.noise.CNG;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.scaffold.cache.AtomicCache;
import com.volmit.iris.scaffold.data.DataProvider;
import com.volmit.iris.util.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents an iris region")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisRegion extends IrisRegistrant implements IRare
{
	@MinNumber(2)
	@Required
	@DontObfuscate
	@Desc("The name of the region")
	private String name = "A Region";

	@ArrayType(min = 1, type = IrisJigsawStructurePlacement.class)
	@DontObfuscate
	@Desc("Jigsaw structures")
	private KList<IrisJigsawStructurePlacement> jigsawStructures = new KList<>();

	@DontObfuscate
	@Desc("Add random chances for terrain features")
	@ArrayType(min = 1, type = IrisFeaturePotential.class)
	private KList<IrisFeaturePotential> features = new KList<>();

	@ArrayType(min = 1, type = IrisEffect.class)
	@DontObfuscate
	@Desc("Effects are ambient effects such as potion effects, random sounds, or even particles around each player. All of these effects are played via packets so two players won't see/hear each others effects.\nDue to performance reasons, effects will play arround the player even if where the effect was played is no longer in the biome the player is in.")
	private KList<IrisEffect> effects = new KList<>();

	@DontObfuscate
	@Desc("Entity spawns to override or add to this region")
	@ArrayType(min = 1, type = IrisEntitySpawnOverride.class)
	private KList<IrisEntitySpawnOverride> entitySpawnOverrides = new KList<>();

	@DontObfuscate
	@Desc("Entity spawns during generation")
	@ArrayType(min = 1, type = IrisEntityInitialSpawn.class)
	private KList<IrisEntityInitialSpawn> entityInitialSpawns = new KList<>();

	@MinNumber(1)
	@MaxNumber(256) // TODO: WARNING HEIGHT
	@DontObfuscate
	@Desc("The rarity of the region")
	private int rarity = 1;

	@ArrayType(min = 1, type = IrisBlockDrops.class)
	@DontObfuscate
	@Desc("Define custom block drops for this region")
	private KList<IrisBlockDrops> blockDrops = new KList<>();

	@MinNumber(0.0001)
	@MaxNumber(1)
	@DontObfuscate
	@Desc("The shore ration (How much percent of land should be a shore)")
	private double shoreRatio = 0.13;

	@ArrayType(min = 1, type = IrisObjectPlacement.class)
	@DontObfuscate
	@Desc("Objects define what schematics (iob files) iris will place in this region")
	private KList<IrisObjectPlacement> objects = new KList<IrisObjectPlacement>();

	@MinNumber(0)
	@DontObfuscate
	@Desc("The min shore height")
	private double shoreHeightMin = 1.2;

	@DontObfuscate
	@Desc("Reference loot tables in this area")
	private IrisLootReference loot = new IrisLootReference();

	@MinNumber(0)
	@DontObfuscate
	@Desc("The the max shore height")
	private double shoreHeightMax = 3.2;

	@MinNumber(0.0001)
	@DontObfuscate
	@Desc("The varience of the shore height")
	private double shoreHeightZoom = 3.14;

	@MinNumber(0.0001)
	@DontObfuscate
	@Desc("How large land biomes are in this region")
	private double landBiomeZoom = 1;

	@MinNumber(0.0001)
	@DontObfuscate
	@Desc("How large shore biomes are in this region")
	private double shoreBiomeZoom = 1;

	@MinNumber(0.0001)
	@DontObfuscate
	@Desc("How large lake biomes are in this region")
	private double lakeBiomeZoom = 1;

	@MinNumber(0.0001)
	@DontObfuscate
	@Desc("How large river biomes are in this region")
	private double riverBiomeZoom = 1;

	@MinNumber(0.0001)
	@DontObfuscate
	@Desc("How large sea biomes are in this region")
	private double seaBiomeZoom = 1;

	@MinNumber(0.0001)
	@DontObfuscate
	@Desc("How large cave biomes are in this region")
	private double caveBiomeZoom = 1;

	@MinNumber(0.0001)
	@MaxNumber(1)
	@DontObfuscate
	@Desc("The biome implosion ratio, how much to implode biomes into children (chance)")
	private double biomeImplosionRatio = 0.4;

	@RegistryListBiome
	@Required
	@ArrayType(min = 1, type = String.class)
	@DontObfuscate
	@Desc("A list of root-level biomes in this region. Don't specify child biomes of other biomes here. Just the root parents.")
	private KList<String> landBiomes = new KList<>();

	@RegistryListBiome
	@Required
	@ArrayType(min = 1, type = String.class)
	@DontObfuscate
	@Desc("A list of root-level biomes in this region. Don't specify child biomes of other biomes here. Just the root parents.")
	private KList<String> seaBiomes = new KList<>();

	@RegistryListBiome
	@Required
	@ArrayType(min = 1, type = String.class)
	@DontObfuscate
	@Desc("A list of root-level biomes in this region. Don't specify child biomes of other biomes here. Just the root parents.")
	private KList<String> shoreBiomes = new KList<>();

	@RegistryListBiome
	@ArrayType(min = 1, type = String.class)
	@DontObfuscate
	@Desc("A list of root-level biomes in this region. Don't specify child biomes of other biomes here. Just the root parents.")
	private KList<String> riverBiomes = new KList<>();

	@RegistryListBiome
	@ArrayType(min = 1, type = String.class)
	@DontObfuscate
	@Desc("A list of root-level biomes in this region. Don't specify child biomes of other biomes here. Just the root parents.")
	private KList<String> lakeBiomes = new KList<>();

	@RegistryListBiome
	@ArrayType(min = 1, type = String.class)
	@DontObfuscate
	@Desc("A list of root-level biomes in this region. Don't specify child biomes of other biomes here. Just the root parents.")
	private KList<String> caveBiomes = new KList<>();

	@ArrayType(min = 1, type = IrisRegionRidge.class)
	@DontObfuscate
	@Desc("Ridge biomes create a vein-like network like rivers through this region")
	private KList<IrisRegionRidge> ridgeBiomes = new KList<>();

	@ArrayType(min = 1, type = IrisRegionSpot.class)
	@DontObfuscate
	@Desc("Spot biomes splotch themselves across this region like lakes")
	private KList<IrisRegionSpot> spotBiomes = new KList<>();

	@ArrayType(min = 1, type = IrisDepositGenerator.class)
	@Desc("Define regional deposit generators that add onto the global deposit generators")
	private KList<IrisDepositGenerator> deposits = new KList<>();

	@DontObfuscate
	@Desc("The style of rivers")
	private IrisGeneratorStyle riverStyle = NoiseStyle.VASCULAR_THIN.style().zoomed(7.77);

	@DontObfuscate
	@Desc("The style of lakes")
	private IrisGeneratorStyle lakeStyle = NoiseStyle.CELLULAR_IRIS_THICK.style();

	@DontObfuscate
	@Desc("The style of river chances")
	private IrisGeneratorStyle riverChanceStyle = NoiseStyle.SIMPLEX.style().zoomed(4);

	@DontObfuscate
	@Desc("Generate lakes in this region")
	private boolean lakes = true;

	@DontObfuscate
	@Desc("Generate rivers in this region")
	private boolean rivers = true;

	@MinNumber(1)
	@DontObfuscate
	@Desc("Generate lakes in this region")
	private int lakeRarity = 22;

	@MinNumber(1)
	@DontObfuscate
	@Desc("Generate rivers in this region")
	private int riverRarity = 3;

	@MinNumber(0)
	@MaxNumber(1)
	@DontObfuscate
	@Desc("Generate rivers in this region")
	private double riverThickness = 0.1;

	@DontObfuscate
	@Desc("A color for visualizing this region with a color. I.e. #F13AF5. This will show up on the map.")
	private IrisColor color = null;

	private final transient AtomicCache<KList<IrisObjectPlacement>> surfaceObjectsCache = new AtomicCache<>();
	private final transient AtomicCache<KList<IrisObjectPlacement>> carveObjectsCache = new AtomicCache<>();
	private final transient AtomicCache<KList<String>> cacheRidge = new AtomicCache<>();
	private final transient AtomicCache<KList<String>> cacheSpot = new AtomicCache<>();
	private final transient AtomicCache<CNG> shoreHeightGenerator = new AtomicCache<>();
	private final transient AtomicCache<KList<IrisBiome>> realLandBiomes = new AtomicCache<>();
	private final transient AtomicCache<KList<IrisBiome>> realLakeBiomes = new AtomicCache<>();
	private final transient AtomicCache<KList<IrisBiome>> realRiverBiomes = new AtomicCache<>();
	private final transient AtomicCache<KList<IrisBiome>> realSeaBiomes = new AtomicCache<>();
	private final transient AtomicCache<KList<IrisBiome>> realShoreBiomes = new AtomicCache<>();
	private final transient AtomicCache<KList<IrisBiome>> realCaveBiomes = new AtomicCache<>();
	private final transient AtomicCache<CNG> lakeGen = new AtomicCache<>();
	private final transient AtomicCache<CNG> riverGen = new AtomicCache<>();
	private final transient AtomicCache<CNG> riverChanceGen = new AtomicCache<>();

	public String getName(){
		return name;
	}

	public KList<IrisObjectPlacement> getSurfaceObjects()
	{
		return getSurfaceObjectsCache().aquire(() ->
		{
			KList<IrisObjectPlacement> o = getObjects().copy();

			for(IrisObjectPlacement i : o.copy())
			{
				if(!i.getCarvingSupport().supportsSurface())
				{
					o.remove(i);
				}
			}

			return o;
		});
	}

	public KList<IrisObjectPlacement> getCarvingObjects()
	{
		return getCarveObjectsCache().aquire(() ->
		{
			KList<IrisObjectPlacement> o = getObjects().copy();

			for(IrisObjectPlacement i : o.copy())
			{
				if(!i.getCarvingSupport().supportsCarving())
				{
					o.remove(i);
				}
			}

			return o;
		});
	}

	public boolean isRiver(RNG rng, double x, double z)
	{
		if(!isRivers())
		{
			return false;
		}

		if(getRiverBiomes().isEmpty())
		{
			return false;
		}

		if(getRiverChanceGen().aquire(() -> getRiverChanceStyle().create(rng)).fit(1, getRiverRarity(), x, z) != 1)
		{
			return false;
		}

		if(getRiverGen().aquire(() -> getRiverStyle().create(rng)).fitDouble(0, 1, x, z) < getRiverThickness())
		{
			return true;
		}

		return false;
	}

	public boolean isLake(RNG rng, double x, double z)
	{
		if(!isLakes())
		{
			return false;
		}

		if(getLakeBiomes().isEmpty())
		{
			return false;
		}

		if(getLakeGen().aquire(() -> getLakeStyle().create(rng)).fit(1, getLakeRarity(), x, z) == 1)
		{
			return true;
		}

		return false;
	}

	public double getBiomeZoom(InferredType t)
	{
		switch(t)
		{
			case CAVE:
				return caveBiomeZoom;
			case LAKE:
				return lakeBiomeZoom;
			case RIVER:
				return riverBiomeZoom;
			case LAND:
				return landBiomeZoom;
			case SEA:
				return seaBiomeZoom;
			case SHORE:
				return shoreBiomeZoom;
			default:
				break;
		}

		return 1;
	}

	public KList<String> getRidgeBiomeKeys()
	{
		return cacheRidge.aquire(() ->
		{
			KList<String> cacheRidge = new KList<String>();
			ridgeBiomes.forEach((i) -> cacheRidge.add(i.getBiome()));

			return cacheRidge;
		});
	}

	public KList<String> getSpotBiomeKeys()
	{
		return cacheSpot.aquire(() ->
		{
			KList<String> cacheSpot = new KList<String>();
			spotBiomes.forEach((i) -> cacheSpot.add(i.getBiome()));
			return cacheSpot;
		});
	}

	public CNG getShoreHeightGenerator()
	{
		return shoreHeightGenerator.aquire(() ->
		{
			return CNG.signature(new RNG((long) (getName().length() + getLandBiomeZoom() + getLandBiomes().size() + 3458612)));
		});
	}

	public double getShoreHeight(double x, double z)
	{
		return getShoreHeightGenerator().fitDouble(shoreHeightMin, shoreHeightMax, x / shoreHeightZoom, z / shoreHeightZoom);
	}

	public KList<IrisBiome> getAllBiomes(DataProvider g)
	{
		KMap<String, IrisBiome> b = new KMap<>();
		KSet<String> names = new KSet<>();
		names.addAll(landBiomes);
		names.addAll(caveBiomes);
		names.addAll(seaBiomes);
		names.addAll(shoreBiomes);
		names.addAll(riverBiomes);
		names.addAll(lakeBiomes);
		spotBiomes.forEach((i) -> names.add(i.getBiome()));
		ridgeBiomes.forEach((i) -> names.add(i.getBiome()));

		while(!names.isEmpty())
		{
			for(String i : new KList<>(names))
			{
				if(b.containsKey(i))
				{
					names.remove(i);
					continue;
				}

				IrisBiome biome = g.getData().getBiomeLoader().load(i);

				names.remove(i);
				if(biome == null)
				{
					continue;
				}

				names.add(biome.getCarvingBiome());
				b.put(biome.getLoadKey(), biome);
				names.addAll(biome.getChildren());
			}
		}

		return b.v();
	}

	public KList<IrisBiome> getBiomes(DataProvider g, InferredType type)
	{
		if(type.equals(InferredType.LAND))
		{
			return getRealLandBiomes(g);
		}

		else if(type.equals(InferredType.SEA))
		{
			return getRealSeaBiomes(g);
		}

		else if(type.equals(InferredType.SHORE))
		{
			return getRealShoreBiomes(g);
		}

		else if(type.equals(InferredType.CAVE))
		{
			return getRealCaveBiomes(g);
		}

		else if(type.equals(InferredType.LAKE))
		{
			return getRealLakeBiomes(g);
		}

		else if(type.equals(InferredType.RIVER))
		{
			return getRealRiverBiomes(g);
		}

		return new KList<>();
	}

	public KList<IrisBiome> getRealCaveBiomes(DataProvider g)
	{
		return realCaveBiomes.aquire(() ->
		{
			KList<IrisBiome> realCaveBiomes = new KList<>();

			for(String i : getCaveBiomes())
			{
				realCaveBiomes.add(g.getData().getBiomeLoader().load(i));
			}

			return realCaveBiomes;
		});
	}

	public KList<IrisBiome> getRealLakeBiomes(DataProvider g)
	{
		return realLakeBiomes.aquire(() ->
		{
			KList<IrisBiome> realLakeBiomes = new KList<>();

			for(String i : getLakeBiomes())
			{
				realLakeBiomes.add(g.getData().getBiomeLoader().load(i));
			}

			return realLakeBiomes;
		});
	}

	public KList<IrisBiome> getRealRiverBiomes(DataProvider g)
	{
		return realRiverBiomes.aquire(() ->
		{
			KList<IrisBiome> realRiverBiomes = new KList<>();

			for(String i : getRiverBiomes())
			{
				realRiverBiomes.add(g.getData().getBiomeLoader().load(i));
			}

			return realRiverBiomes;
		});
	}

	public KList<IrisBiome> getRealShoreBiomes(DataProvider g)
	{
		return realShoreBiomes.aquire(() ->
		{
			KList<IrisBiome> realShoreBiomes = new KList<>();

			for(String i : getShoreBiomes())
			{
				realShoreBiomes.add(g.getData().getBiomeLoader().load(i));
			}

			return realShoreBiomes;
		});
	}

	public KList<IrisBiome> getRealSeaBiomes(DataProvider g)
	{
		return realSeaBiomes.aquire(() ->
		{
			KList<IrisBiome> realSeaBiomes = new KList<>();

			for(String i : getSeaBiomes())
			{
				realSeaBiomes.add(g.getData().getBiomeLoader().load(i));
			}

			return realSeaBiomes;
		});
	}

	public KList<IrisBiome> getRealLandBiomes(DataProvider g)
	{
		return realLandBiomes.aquire(() ->
		{
			KList<IrisBiome> realLandBiomes = new KList<>();

			for(String i : getLandBiomes())
			{
				realLandBiomes.add(g.getData().getBiomeLoader().load(i));
			}

			return realLandBiomes;
		});
	}

	public KList<IrisBiome> getAllAnyBiomes() {
		KMap<String, IrisBiome> b = new KMap<>();
		KSet<String> names = new KSet<>();
		names.addAll(landBiomes);
		names.addAll(caveBiomes);
		names.addAll(seaBiomes);
		names.addAll(shoreBiomes);
		names.addAll(riverBiomes);
		names.addAll(lakeBiomes);
		spotBiomes.forEach((i) -> names.add(i.getBiome()));
		ridgeBiomes.forEach((i) -> names.add(i.getBiome()));

		while(!names.isEmpty())
		{
			for(String i : new KList<>(names))
			{
				if(b.containsKey(i))
				{
					names.remove(i);
					continue;
				}

				IrisBiome biome = IrisDataManager.loadAnyBiome(i);

				names.remove(i);
				if(biome == null)
				{
					continue;
				}

				names.add(biome.getCarvingBiome());
				b.put(biome.getLoadKey(), biome);
				names.addAll(biome.getChildren());
			}
		}

		return b.v();
	}
}
