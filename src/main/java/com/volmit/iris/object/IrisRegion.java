package com.volmit.iris.object;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.ContextualChunkGenerator;
import com.volmit.iris.gen.atomics.AtomicCache;
import com.volmit.iris.noise.CNG;
import com.volmit.iris.util.ArrayType;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.IRare;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.KMap;
import com.volmit.iris.util.KSet;
import com.volmit.iris.util.MaxNumber;
import com.volmit.iris.util.MinNumber;
import com.volmit.iris.util.RNG;
import com.volmit.iris.util.RegistryListBiome;
import com.volmit.iris.util.Required;

import lombok.Data;
import lombok.EqualsAndHashCode;

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

	@ArrayType(min = 1, type = IrisEffect.class)
	@DontObfuscate
	@Desc("Effects are ambient effects such as potion effects, random sounds, or even particles around each player. All of these effects are played via packets so two players won't see/hear each others effects.\nDue to performance reasons, effects will play arround the player even if where the effect was played is no longer in the biome the player is in.")
	private KList<IrisEffect> effects = new KList<>();

	@MinNumber(1)
	@MaxNumber(256)
	@DontObfuscate
	@Desc("The rarity of the region")
	private int rarity = 1;

	@DontObfuscate
	@Desc("Place text on terrain")
	@ArrayType(min = 1, type = IrisTextPlacement.class)
	private KList<IrisTextPlacement> text = new KList<>();
	
	@ArrayType(min = 1, type = IrisBlockDrops.class)
	@DontObfuscate
	@Desc("Define custom block drops for this region")
	private KList<IrisBlockDrops> blockDrops = new KList<>();

	@MinNumber(0.0001)
	@MaxNumber(1)
	@DontObfuscate
	@Desc("The shore ration (How much percent of land should be a shore)")
	private double shoreRatio = 0.13;

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

	@ArrayType(min = 1, type = IrisStructurePlacement.class)
	@DontObfuscate
	@Desc("A list of structure tilesets")
	private KList<IrisStructurePlacement> structures = new KList<>();

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

	private transient AtomicCache<KList<String>> cacheRidge = new AtomicCache<>();
	private transient AtomicCache<KList<String>> cacheSpot = new AtomicCache<>();
	private transient AtomicCache<CNG> shoreHeightGenerator = new AtomicCache<>();
	private transient AtomicCache<KList<IrisBiome>> realLandBiomes = new AtomicCache<>();
	private transient AtomicCache<KList<IrisBiome>> realSeaBiomes = new AtomicCache<>();
	private transient AtomicCache<KList<IrisBiome>> realShoreBiomes = new AtomicCache<>();
	private transient AtomicCache<KList<IrisBiome>> realCaveBiomes = new AtomicCache<>();

	public double getBiomeZoom(InferredType t)
	{
		switch(t)
		{
			case CAVE:
				return caveBiomeZoom;
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

	public KList<IrisBiome> getAllBiomes(ContextualChunkGenerator g)
	{
		KMap<String, IrisBiome> b = new KMap<>();
		KSet<String> names = new KSet<>();
		names.addAll(landBiomes);
		names.addAll(caveBiomes);
		names.addAll(seaBiomes);
		names.addAll(shoreBiomes);
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

				IrisBiome biome = (g == null ? Iris.globaldata : g.getData()).getBiomeLoader().load(i);
				b.put(biome.getLoadKey(), biome);
				names.remove(i);
				names.addAll(biome.getChildren());
			}
		}

		return b.v();
	}

	public KList<IrisBiome> getBiomes(ContextualChunkGenerator g, InferredType type)
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

		return new KList<>();
	}

	public KList<IrisBiome> getRealCaveBiomes(ContextualChunkGenerator g)
	{
		return realCaveBiomes.aquire(() ->
		{
			KList<IrisBiome> realCaveBiomes = new KList<>();

			for(String i : getCaveBiomes())
			{
				realCaveBiomes.add((g == null ? Iris.globaldata : g.getData()).getBiomeLoader().load(i));
			}

			return realCaveBiomes;
		});
	}

	public KList<IrisBiome> getRealShoreBiomes(ContextualChunkGenerator g)
	{
		return realShoreBiomes.aquire(() ->
		{
			KList<IrisBiome> realShoreBiomes = new KList<>();

			for(String i : getShoreBiomes())
			{
				realShoreBiomes.add((g == null ? Iris.globaldata : g.getData()).getBiomeLoader().load(i));
			}

			return realShoreBiomes;
		});
	}

	public KList<IrisBiome> getRealSeaBiomes(ContextualChunkGenerator g)
	{
		return realSeaBiomes.aquire(() ->
		{
			KList<IrisBiome> realSeaBiomes = new KList<>();

			for(String i : getSeaBiomes())
			{
				realSeaBiomes.add((g == null ? Iris.globaldata : g.getData()).getBiomeLoader().load(i));
			}

			return realSeaBiomes;
		});
	}

	public KList<IrisBiome> getRealLandBiomes(ContextualChunkGenerator g)
	{
		return realLandBiomes.aquire(() ->
		{
			KList<IrisBiome> realLandBiomes = new KList<>();

			for(String i : getLandBiomes())
			{
				realLandBiomes.add((g == null ? Iris.globaldata : g.getData()).getBiomeLoader().load(i));
			}

			return realLandBiomes;
		});
	}
}
