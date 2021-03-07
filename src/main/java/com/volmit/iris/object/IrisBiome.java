package com.volmit.iris.object;

import com.volmit.iris.generator.IrisComplex;
import com.volmit.iris.scaffold.cache.AtomicCache;
import com.volmit.iris.generator.noise.CNG;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.scaffold.data.DataProvider;
import com.volmit.iris.scaffold.engine.IrisAccess;
import com.volmit.iris.util.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

import java.awt.*;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@DontObfuscate
@Desc("Represents a biome in iris. Biomes are placed inside of regions and hold objects.\nA biome consists of layers (block palletes), decorations, objects & generators.")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisBiome extends IrisRegistrant implements IRare
{
	@MinNumber(2)
	@Required
	@DontObfuscate
	@Desc("This is the human readable name for this biome. This can and should be different than the file name. This is not used for loading biomes in other objects.")
	private String name = "A Biome";

	/* Needs to be implemented but it's not
	@DontObfuscate
	@Desc("The palette of blocks for 'water' in this biome (overwrites dimension)")
	private IrisMaterialPalette fluidPalette = new IrisMaterialPalette().qclear().qadd("void_air");
	*/

	@DontObfuscate
	@Desc("Entity spawns to override or add to this biome. Anytime an entity spawns, it has a chance to be replaced as one of these overrides.")
	@ArrayType(min = 1, type = IrisEntitySpawnOverride.class)
	private KList<IrisEntitySpawnOverride> entitySpawnOverrides = new KList<>();

	@DontObfuscate
	@Desc("Add random chances for terrain features")
	@ArrayType(min = 1, type = IrisFeaturePotential.class)
	private KList<IrisFeaturePotential> features = new KList<>();

	@DontObfuscate
	@Desc("Entity spawns during generation")
	@ArrayType(min = 1, type = IrisEntityInitialSpawn.class)
	private KList<IrisEntityInitialSpawn> entityInitialSpawns = new KList<>();

	@ArrayType(min = 1, type = IrisEffect.class)
	@DontObfuscate
	@Desc("Effects are ambient effects such as potion effects, random sounds, or even particles around each player. All of these effects are played via packets so two players won't see/hear each others effects.\nDue to performance reasons, effects will play arround the player even if where the effect was played is no longer in the biome the player is in.")
	private KList<IrisEffect> effects = new KList<>();

	@DontObfuscate
	@DependsOn({"biomeStyle", "biomeZoom", "biomeScatter"})
	@Desc("This changes the dispersion of the biome colors if multiple derivatives are chosen.")
	private IrisGeneratorStyle biomeStyle = NoiseStyle.SIMPLEX.style();

	@ArrayType(min = 1, type = IrisBlockDrops.class)
	@DontObfuscate
	@Desc("Define custom block drops for this biome")
	private KList<IrisBlockDrops> blockDrops = new KList<>();

	@DontObfuscate
	@Desc("Reference loot tables in this area")
	private IrisLootReference loot = new IrisLootReference();

	@MinNumber(0.0001)
	@DontObfuscate
	@DependsOn({"biomeStyle", "biomeZoom", "biomeScatter"})
	@Desc("This zooms in the biome colors if multiple derivatives are chosen")
	private double biomeZoom = 1;

	@DontObfuscate
	@Desc("Layers no longer descend from the surface block, they descend from the max possible height the biome can produce (constant) creating mesa like layers.")
	private boolean lockLayers = false;

	@DontObfuscate
	@Desc("The max layers to iterate below the surface for locked layer biomes (mesa).")
	private int lockLayersMax = 7;

	@MinNumber(1)
	@MaxNumber(512)
	@DontObfuscate
	@Desc("The rarity of this biome (integer)")
	private int rarity = 1;

	@DontObfuscate
	@Desc("A color for visualizing this biome with a color. I.e. #F13AF5. This will show up on the map.")
	private IrisColor color = null;

	@Required
	@DontObfuscate
	@Desc("The raw derivative of this biome. This is required or the terrain will not properly generate. Use any vanilla biome type. Look in examples/biome-list.txt")
	private Biome derivative = Biome.THE_VOID;

	@Required
	@DontObfuscate
	@Desc("Override the derivative when vanilla places structures to this derivative. This is useful for example if you have an ocean biome, but you have set the derivative to desert to get a brown-ish color. To prevent desert structures from spawning on top of your ocean, you can set your vanillaDerivative to ocean, to allow for vanilla structures. Not defining this value will simply select the derivative.")
	private Biome vanillaDerivative = null;

	@ArrayType(min = 1, type = Biome.class)
	@DontObfuscate
	@Desc("You can instead specify multiple biome derivatives to randomly scatter colors in this biome")
	private KList<Biome> biomeScatter = new KList<>();

	@ArrayType(min = 1, type = Biome.class)
	@DontObfuscate
	@Desc("Since 1.13 supports 3D biomes, you can add different derivative colors for anything above the terrain. (Think swampy tree leaves with a desert looking grass surface)")
	private KList<Biome> biomeSkyScatter = new KList<>();

	@DontObfuscate
	@DependsOn({"children"})
	@Desc("If this biome has children biomes, and the gen layer chooses one of this biomes children, how much smaller will it be (inside of this biome). Higher values means a smaller biome relative to this biome's size. Set higher than 1.0 and below 3.0 for best results.")
	private double childShrinkFactor = 1.5;

	@DontObfuscate
	@DependsOn({"children"})
	@Desc("If this biome has children biomes, and the gen layer chooses one of this biomes children, How will it be shaped?")
	private IrisGeneratorStyle childStyle = NoiseStyle.CELLULAR_IRIS_DOUBLE.style();

	@RegistryListBiome
	@ArrayType(min = 1, type = String.class)
	@DontObfuscate
	@Desc("List any biome names (file names without.json) here as children. Portions of this biome can sometimes morph into their children. Iris supports cyclic relationships such as A > B > A > B. Iris will stop checking 9 biomes down the tree.")
	private KList<String> children = new KList<>();

	@ArrayType(min = 1, type = IrisJigsawStructurePlacement.class)
	@DontObfuscate
	@Desc("Jigsaw structures")
	private KList<IrisJigsawStructurePlacement> jigsawStructures = new KList<>();

	@RegistryListBiome
	@DontObfuscate
	@Desc("The carving biome. If specified the biome will be used when under a carving instead of this current biome.")
	private String carvingBiome = "";

	@DontObfuscate
	@Desc("The default slab if iris decides to place a slab in this biome. Default is no slab.")
	private IrisBiomePaletteLayer slab = new IrisBiomePaletteLayer().zero();

	@DontObfuscate
	@Desc("The default wall if iris decides to place a wall higher than 2 blocks (steep hills or possibly cliffs)")
	private IrisBiomePaletteLayer wall = new IrisBiomePaletteLayer().zero();

	@Required
	@ArrayType(min = 1, type = IrisBiomePaletteLayer.class)
	@DontObfuscate
	@Desc("This defines the layers of materials in this biome. Each layer has a palette and min/max height and some other properties. Usually a grassy/sandy layer then a dirt layer then a stone layer. Iris will fill in the remaining blocks below your layers with stone.")
	private KList<IrisBiomePaletteLayer> layers = new KList<IrisBiomePaletteLayer>().qadd(new IrisBiomePaletteLayer());

	@ArrayType(min = 1, type = IrisBiomePaletteLayer.class)
	@DontObfuscate
	@Desc("This defines the layers of materials in this biome. Each layer has a palette and min/max height and some other properties. Usually a grassy/sandy layer then a dirt layer then a stone layer. Iris will fill in the remaining blocks below your layers with stone.")
	private KList<IrisBiomePaletteLayer> seaLayers = new KList<IrisBiomePaletteLayer>();

	@ArrayType(min = 1, type = IrisDecorator.class)
	@DontObfuscate
	@Desc("Decorators are used for things like tall grass, bisected flowers, and even kelp or cactus (random heights)")
	private KList<IrisDecorator> decorators = new KList<IrisDecorator>();

	@ArrayType(min = 1, type = IrisObjectPlacement.class)
	@DontObfuscate
	@Desc("Objects define what schematics (iob files) iris will place in this biome")
	private KList<IrisObjectPlacement> objects = new KList<IrisObjectPlacement>();

	@Required
	@ArrayType(min = 1, type = IrisBiomeGeneratorLink.class)
	@DontObfuscate
	@Desc("Generators for this biome. Multiple generators with different interpolation sizes will mix with other biomes how you would expect. This defines your biome height relative to the fluid height. Use negative for oceans.")
	private KList<IrisBiomeGeneratorLink> generators = new KList<IrisBiomeGeneratorLink>().qadd(new IrisBiomeGeneratorLink());

	@ArrayType(min = 1, type = IrisDepositGenerator.class)
	@DontObfuscate
	@Desc("Define biome deposit generators that add onto the existing regional and global deposit generators")
	private KList<IrisDepositGenerator> deposits = new KList<>();

	private transient InferredType inferredType;

	private final transient AtomicCache<KMap<String, IrisBiomeGeneratorLink>> genCache = new AtomicCache<>();
	private final transient AtomicCache<KMap<String, Integer>> genCacheMax = new AtomicCache<>();
	private final transient AtomicCache<KMap<String, Integer>> genCacheMin = new AtomicCache<>();
	private final transient AtomicCache<KList<IrisObjectPlacement>> surfaceObjectsCache = new AtomicCache<>(false);
	private final transient AtomicCache<KList<IrisObjectPlacement>> carveObjectsCache = new AtomicCache<>(false);
	private final transient AtomicCache<Color> cacheColor = new AtomicCache<>(true);
	private final transient AtomicCache<CNG> childrenCell = new AtomicCache<>();
	private final transient AtomicCache<CNG> biomeGenerator = new AtomicCache<>();
	private final transient AtomicCache<Integer> maxHeight = new AtomicCache<>();
	private final transient AtomicCache<IrisBiome> realCarveBiome = new AtomicCache<>();
	private final transient AtomicCache<KList<IrisBiome>> realChildren = new AtomicCache<>();
	private final transient AtomicCache<KList<CNG>> layerHeightGenerators = new AtomicCache<>();
	private final transient AtomicCache<KList<CNG>> layerSeaHeightGenerators = new AtomicCache<>();

	public Biome getVanillaDerivative()
	{
		return vanillaDerivative == null ? derivative : vanillaDerivative;
	}

	public double getGenLinkMax(String loadKey)
	{
		Integer v = genCacheMax.aquire(() ->
		{
			KMap<String, Integer> l = new KMap<>();

			for(IrisBiomeGeneratorLink i : getGenerators())
			{
				l.put(i.getGenerator(), i.getMax());
			}

			return l;
		}).get(loadKey);

		return v == null ? 0 : v;
	}

	public double getGenLinkMin(String loadKey)
	{
		Integer v = genCacheMin.aquire(() ->
		{
			KMap<String, Integer> l = new KMap<>();

			for(IrisBiomeGeneratorLink i : getGenerators())
			{
				l.put(i.getGenerator(), i.getMin());
			}

			return l;
		}).get(loadKey);

		return v == null ? 0 : v;
	}

	public IrisBiomeGeneratorLink getGenLink(String loadKey)
	{
		return genCache.aquire(() ->
		{
			KMap<String, IrisBiomeGeneratorLink> l = new KMap<>();

			for(IrisBiomeGeneratorLink i : getGenerators())
			{
				l.put(i.getGenerator(), i);
			}

			return l;
		}).get(loadKey);
	}

	public IrisBiome getRealCarvingBiome(IrisDataManager data)
	{
		return realCarveBiome.aquire(() ->
		{
			IrisBiome biome = data.getBiomeLoader().load(getCarvingBiome());

			if(biome == null)
			{
				biome = this;
			}

			return biome;
		});
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

	public double getHeight(IrisAccess xg, double x, double z, long seed)
	{
		double height = 0;

		for(IrisBiomeGeneratorLink i : generators)
		{
			height += i.getHeight(xg, x, z, seed);
		}

		return Math.max(0, Math.min(height, 255));
	}

	public CNG getBiomeGenerator(RNG random)
	{
		return biomeGenerator.aquire(() ->
		{
			return biomeStyle.create(random.nextParallelRNG(213949 + 228888 + getRarity() + getName().length()));
		});
	}

	public CNG getChildrenGenerator(RNG random, int sig, double scale)
	{
		return childrenCell.aquire(() -> getChildStyle().create(random.nextParallelRNG(sig * 2137)).bake().scale(scale).bake());
	}

	public KList<BlockData> generateLayers(double wx, double wz, RNG random, int maxDepth, int height, IrisDataManager rdata, IrisComplex complex)
	{
		if(isLockLayers())
		{
			return generateLockedLayers(wx, wz, random, maxDepth, height, rdata, complex);
		}

		KList<BlockData> data = new KList<>();

		if(maxDepth <= 0)
		{
			return data;
		}

		for(int i = 0; i < layers.size(); i++)
		{
			CNG hgen = getLayerHeightGenerators(random, rdata).get(i);
			double d = hgen.fit(layers.get(i).getMinHeight(), layers.get(i).getMaxHeight(), wx / layers.get(i).getZoom(), wz / layers.get(i).getZoom());

			IrisSlopeClip sc = getLayers().get(i).getSlopeCondition();

			if(!sc.isDefault())
			{
				if(!sc.isValid(complex.getSlopeStream().get(wx, wz)))
				{
					d = 0;
				}
			}

			if(d <= 0)
			{
				continue;
			}

			for(int j = 0; j < d; j++)
			{
				if(data.size() >= maxDepth)
				{
					break;
				}

				try
				{
					data.add(getLayers().get(i).get(random.nextParallelRNG(i + j), (wx + j) / layers.get(i).getZoom(), j, (wz - j) / layers.get(i).getZoom(), rdata));
				}

				catch(Throwable e)
				{
					e.printStackTrace();
				}
			}

			if(data.size() >= maxDepth)
			{
				break;
			}
		}

		return data;
	}

	public KList<BlockData> generateLockedLayers(double wx, double wz, RNG random, int maxDepthf, int height, IrisDataManager rdata, IrisComplex complex)
	{
		KList<BlockData> data = new KList<>();
		KList<BlockData> real = new KList<>();
		int maxDepth = Math.min(maxDepthf, getLockLayersMax());
		if(maxDepth <= 0)
		{
			return data;
		}

		for(int i = 0; i < layers.size(); i++)
		{
			CNG hgen = getLayerHeightGenerators(random, rdata).get(i);
			double d = hgen.fit(layers.get(i).getMinHeight(), layers.get(i).getMaxHeight(), wx / layers.get(i).getZoom(), wz / layers.get(i).getZoom());

			IrisSlopeClip sc = getLayers().get(i).getSlopeCondition();

			if(!sc.isDefault())
			{
				if(!sc.isValid(complex.getSlopeStream().get(wx, wz)))
				{
					d = 0;
				}
			}

			if(d <= 0)
			{
				continue;
			}

			for(int j = 0; j < d; j++)
			{
				try
				{
					data.add(getLayers().get(i).get(random.nextParallelRNG(i + j), (wx + j) / layers.get(i).getZoom(), j, (wz - j) / layers.get(i).getZoom(), rdata));
				}

				catch(Throwable e)
				{
					e.printStackTrace();
				}
			}
		}

		if(data.isEmpty())
		{
			return real;
		}

		for(int i = 0; i < maxDepth; i++)
		{
			int offset = (255 - height) - i;
			int index = offset % data.size();
			real.add(data.get(index < 0 ? 0 : index));
		}

		return real;
	}

	private int getMaxHeight()
	{
		return maxHeight.aquire(() ->
		{
			int maxHeight = 0;

			for(IrisBiomeGeneratorLink i : getGenerators())
			{
				maxHeight += i.getMax();
			}

			return maxHeight;
		});
	}

	public IrisBiome infer(InferredType t, InferredType type)
	{
		setInferredType(t.equals(InferredType.DEFER) ? type : t);
		return this;
	}

	public KList<BlockData> generateSeaLayers(double wx, double wz, RNG random, int maxDepth, IrisDataManager rdata)
	{
		KList<BlockData> data = new KList<>();

		for(int i = 0; i < seaLayers.size(); i++)
		{
			CNG hgen = getLayerSeaHeightGenerators(random, rdata).get(i);
			int d = hgen.fit(seaLayers.get(i).getMinHeight(), seaLayers.get(i).getMaxHeight(), wx / seaLayers.get(i).getZoom(), wz / seaLayers.get(i).getZoom());

			if(d < 0)
			{
				continue;
			}

			for(int j = 0; j < d; j++)
			{
				if(data.size() >= maxDepth)
				{
					break;
				}

				try
				{
					data.add(getSeaLayers().get(i).get(random.nextParallelRNG(i + j), (wx + j) / seaLayers.get(i).getZoom(), j, (wz - j) / seaLayers.get(i).getZoom(), rdata));
				}

				catch(Throwable e)
				{
					e.printStackTrace();
				}
			}

			if(data.size() >= maxDepth)
			{
				break;
			}
		}

		return data;
	}

	public KList<CNG> getLayerHeightGenerators(RNG rng, IrisDataManager rdata)
	{
		return layerHeightGenerators.aquire(() ->
		{
			KList<CNG> layerHeightGenerators = new KList<>();

			int m = 7235;

			for(IrisBiomePaletteLayer i : getLayers())
			{
				layerHeightGenerators.add(i.getHeightGenerator(rng.nextParallelRNG((m++) * m * m * m), rdata));
			}

			return layerHeightGenerators;
		});
	}

	public KList<CNG> getLayerSeaHeightGenerators(RNG rng, IrisDataManager data)
	{
		return layerSeaHeightGenerators.aquire(() ->
		{
			KList<CNG> layerSeaHeightGenerators = new KList<>();

			int m = 7735;

			for(IrisBiomePaletteLayer i : getSeaLayers())
			{
				layerSeaHeightGenerators.add(i.getHeightGenerator(rng.nextParallelRNG((m++) * m * m * m), data));
			}

			return layerSeaHeightGenerators;
		});
	}

	public boolean isLand()
	{
		if(inferredType == null)
		{
			return true;
		}

		return inferredType.equals(InferredType.LAND);
	}

	public boolean isSea()
	{
		if(inferredType == null)
		{
			return false;
		}
		return inferredType.equals(InferredType.SEA);
	}

	public boolean isLake()
	{
		if(inferredType == null)
		{
			return false;
		}
		return inferredType.equals(InferredType.LAKE);
	}

	public boolean isRiver()
	{
		if(inferredType == null)
		{
			return false;
		}
		return inferredType.equals(InferredType.RIVER);
	}

	public boolean isAquatic()
	{
		return isSea() || isLake() || isRiver();
	}

	public boolean isShore()
	{
		if(inferredType == null)
		{
			return false;
		}
		return inferredType.equals(InferredType.SHORE);
	}

	public Biome getSkyBiome(RNG rng, double x, double y, double z)
	{
		if(biomeSkyScatter.size() == 1)
		{
			return biomeSkyScatter.get(0);
		}

		if(biomeSkyScatter.isEmpty())
		{
			return getGroundBiome(rng, x, y, z);
		}

		return biomeSkyScatter.get(getBiomeGenerator(rng).fit(0, biomeSkyScatter.size() - 1, x, y, z));
	}

	public KList<IrisBiome> getRealChildren(DataProvider g)
	{
		return realChildren.aquire(() ->
		{
			KList<IrisBiome> realChildren = new KList<>();

			for(String i : getChildren())
			{
				realChildren.add(g.getData().getBiomeLoader().load(i));
			}

			return realChildren;
		});
	}

	public KList<String> getAllChildren(DataProvider g, int limit)
	{
		KSet<String> m = new KSet<>();
		m.addAll(getChildren());
		limit--;

		if(limit > 0)
		{
			for(String i : getChildren())
			{
				IrisBiome b = g.getData().getBiomeLoader().load(i);
				int l = limit;
				m.addAll(b.getAllChildren(g, l));
			}
		}

		return new KList<String>(m);
	}

	public Biome getGroundBiome(RNG rng, double x, double y, double z)
	{
		if(biomeScatter.isEmpty())
		{
			return getDerivative();
		}

		if(biomeScatter.size() == 1)
		{
			return biomeScatter.get(0);
		}

		return getBiomeGenerator(rng).fit(biomeScatter, x, y, z);
	}

	public BlockData getSurfaceBlock(int x, int z, RNG rng, IrisDataManager idm)
	{
		if(getLayers().isEmpty())
		{
			return B.get("AIR");
		}

		return getLayers().get(0).get(rng, x, 0, z, idm);
	}
}
