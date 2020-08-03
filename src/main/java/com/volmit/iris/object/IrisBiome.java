package com.volmit.iris.object;

import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.atomics.AtomicCache;
import com.volmit.iris.util.BiomeRarityCellGenerator;
import com.volmit.iris.util.CNG;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.KSet;
import com.volmit.iris.util.RNG;

import lombok.Data;
import lombok.EqualsAndHashCode;

@DontObfuscate
@Desc("Represents a biome in iris.")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisBiome extends IrisRegistrant
{
	@DontObfuscate
	@Desc("This is the human readable name for this biome. This can and should be different than the file name. This is not used for loading biomes in other objects.")
	private String name = "A Biome";

	@DontObfuscate
	@Desc("This changes the dispersion of the biome colors if multiple derivatives are chosen")
	private Dispersion biomeDispersion = Dispersion.SCATTER;

	@DontObfuscate
	@Desc("This zooms in the biome colors if multiple derivatives are chosen")
	private double biomeZoom = 1;

	@DontObfuscate
	@Desc("Layers no longer descend from the surface block, they descend from the max possible height the biome can produce (constant) creating mesa like layers.")
	private boolean lockLayers = false;

	@DontObfuscate
	@Desc("The rarity of this biome (integer)")
	private int rarity = 1;

	@DontObfuscate
	@Desc("The raw derivative of this biome. This is required or the terrain will not properly generate. Use any vanilla biome type. Look in examples/biome-list.txt")
	private Biome derivative = Biome.THE_VOID;

	@DontObfuscate
	@Desc("You can instead specify multiple biome derivatives to randomly scatter colors in this biome")
	private KList<Biome> biomeScatter = new KList<>();

	@DontObfuscate
	@Desc("Since 1.13 supports 3D biomes, you can add different derivative colors for anything above the terrain. (Think swampy tree leaves with a desert looking grass surface)")
	private KList<Biome> biomeSkyScatter = new KList<>();

	@DontObfuscate
	@Desc("If this biome has children biomes, and the gen layer chooses one of this biomes children, how much smaller will it be (inside of this biome). Higher values means a smaller biome relative to this biome's size. Set higher than 1.0 and below 3.0 for best results.")
	private double childShrinkFactor = 1.5;

	@DontObfuscate
	@Desc("List any biome names (file names without.json) here as children. Portions of this biome can sometimes morph into their children. Iris supports cyclic relationships such as A > B > A > B. Iris will stop checking 9 biomes down the tree.")
	private KList<String> children = new KList<>();

	@DontObfuscate
	@Desc("The default slab if iris decides to place a slab in this biome. Default is no slab.")
	private IrisBiomePaletteLayer slab = new IrisBiomePaletteLayer().zero();

	@DontObfuscate
	@Desc("The default wall if iris decides to place a wall higher than 2 blocks (steep hills or possibly cliffs)")
	private IrisBiomePaletteLayer wall = new IrisBiomePaletteLayer().zero();

	@DontObfuscate
	@Desc("This defines the layers of materials in this biome. Each layer has a palette and min/max height and some other properties. Usually a grassy/sandy layer then a dirt layer then a stone layer. Iris will fill in the remaining blocks below your layers with stone.")
	private KList<IrisBiomePaletteLayer> layers = new KList<IrisBiomePaletteLayer>().qadd(new IrisBiomePaletteLayer());

	@DontObfuscate
	@Desc("This defines the layers of materials in this biome. Each layer has a palette and min/max height and some other properties. Usually a grassy/sandy layer then a dirt layer then a stone layer. Iris will fill in the remaining blocks below your layers with stone.")
	private KList<IrisBiomePaletteLayer> seaLayers = new KList<IrisBiomePaletteLayer>();

	@DontObfuscate
	@Desc("Decorators are used for things like tall grass, bisected flowers, and even kelp or cactus (random heights)")
	private KList<IrisBiomeDecorator> decorators = new KList<IrisBiomeDecorator>();

	@DontObfuscate
	@Desc("Objects define what schematics (iob files) iris will place in this biome")
	private KList<IrisObjectPlacement> objects = new KList<IrisObjectPlacement>();

	@DontObfuscate
	@Desc("Generators for this biome. Multiple generators with different interpolation sizes will mix with other biomes how you would expect. This defines your biome height relative to the fluid height. Use negative for oceans.")
	private KList<IrisBiomeGeneratorLink> generators = new KList<IrisBiomeGeneratorLink>().qadd(new IrisBiomeGeneratorLink());

	@DontObfuscate
	@Desc("Define biome deposit generators that add onto the existing regional and global deposit generators")
	private KList<IrisDepositGenerator> deposits = new KList<>();

	private transient InferredType inferredType;
	private transient AtomicCache<BiomeRarityCellGenerator> childrenCell = new AtomicCache<>();
	private transient AtomicCache<CNG> biomeGenerator = new AtomicCache<>();
	private transient AtomicCache<Integer> maxHeight = new AtomicCache<>();
	private transient AtomicCache<KList<IrisBiome>> realChildren = new AtomicCache<>();
	private transient AtomicCache<KList<CNG>> layerHeightGenerators = new AtomicCache<>();
	private transient AtomicCache<KList<CNG>> layerSeaHeightGenerators = new AtomicCache<>();

	public IrisBiome()
	{

	}

	public double getHeight(double x, double z, long seed)
	{
		double height = 0;

		for(IrisBiomeGeneratorLink i : generators)
		{
			height += i.getHeight(x, z, seed);
		}

		return Math.max(0, Math.min(height, 255));
	}

	public CNG getBiomeGenerator(RNG random)
	{
		return biomeGenerator.aquire(() ->
		{
			return CNG.signature(random.nextParallelRNG(213949 + 228888 + getRarity() + getName().length())).scale(biomeDispersion.equals(Dispersion.SCATTER) ? 1000D : 0.1D);
		});
	}

	public BiomeRarityCellGenerator getChildrenGenerator(RNG random, int sig, double scale)
	{
		return childrenCell.aquire(() ->
		{
			BiomeRarityCellGenerator childrenCell = new BiomeRarityCellGenerator(random.nextParallelRNG(sig * 2137));
			childrenCell.setCellScale(scale);
			return childrenCell;
		});
	}

	public KList<BlockData> generateLayers(double wx, double wz, RNG random, int maxDepth, int height)
	{
		if(isLockLayers())
		{
			return generateLockedLayers(wx, wz, random, maxDepth, height);
		}

		KList<BlockData> data = new KList<>();

		if(maxDepth <= 0)
		{
			return data;
		}

		for(int i = 0; i < layers.size(); i++)
		{
			CNG hgen = getLayerHeightGenerators(random).get(i);
			int d = hgen.fit(layers.get(i).getMinHeight(), layers.get(i).getMaxHeight(), wx / layers.get(i).getTerrainZoom(), wz / layers.get(i).getTerrainZoom());

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
					data.add(getLayers().get(i).get(random.nextParallelRNG(i + j), (wx + j) / layers.get(i).getTerrainZoom(), j, (wz - j) / layers.get(i).getTerrainZoom()));
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

	public KList<BlockData> generateLockedLayers(double wx, double wz, RNG random, int maxDepth, int height)
	{
		KList<BlockData> data = new KList<>();
		KList<BlockData> real = new KList<>();

		if(maxDepth <= 0)
		{
			return data;
		}

		for(int i = 0; i < layers.size(); i++)
		{
			CNG hgen = getLayerHeightGenerators(random).get(i);
			int d = hgen.fit(layers.get(i).getMinHeight(), layers.get(i).getMaxHeight(), wx / layers.get(i).getTerrainZoom(), wz / layers.get(i).getTerrainZoom());

			if(d < 0)
			{
				continue;
			}

			for(int j = 0; j < d; j++)
			{
				try
				{
					data.add(getLayers().get(i).get(random.nextParallelRNG(i + j), (wx + j) / layers.get(i).getTerrainZoom(), j, (wz - j) / layers.get(i).getTerrainZoom()));
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
			int offset = (getMaxHeight() - height) - i;
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

	public KList<BlockData> generateSeaLayers(double wx, double wz, RNG random, int maxDepth)
	{
		KList<BlockData> data = new KList<>();

		for(int i = 0; i < seaLayers.size(); i++)
		{
			CNG hgen = getLayerSeaHeightGenerators(random).get(i);
			int d = hgen.fit(seaLayers.get(i).getMinHeight(), seaLayers.get(i).getMaxHeight(), wx / seaLayers.get(i).getTerrainZoom(), wz / seaLayers.get(i).getTerrainZoom());

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
					data.add(getSeaLayers().get(i).get(random.nextParallelRNG(i + j), (wx + j) / seaLayers.get(i).getTerrainZoom(), j, (wz - j) / seaLayers.get(i).getTerrainZoom()));
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

	public KList<CNG> getLayerHeightGenerators(RNG rng)
	{
		return layerHeightGenerators.aquire(() ->
		{
			KList<CNG> layerHeightGenerators = new KList<>();

			int m = 7235;

			for(IrisBiomePaletteLayer i : getLayers())
			{
				layerHeightGenerators.add(i.getHeightGenerator(rng.nextParallelRNG((m++) * m * m * m)));
			}

			return layerHeightGenerators;
		});
	}

	public KList<CNG> getLayerSeaHeightGenerators(RNG rng)
	{
		return layerSeaHeightGenerators.aquire(() ->
		{
			KList<CNG> layerSeaHeightGenerators = new KList<>();

			int m = 7735;

			for(IrisBiomePaletteLayer i : getSeaLayers())
			{
				layerSeaHeightGenerators.add(i.getHeightGenerator(rng.nextParallelRNG((m++) * m * m * m)));
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

	public KList<IrisBiome> getRealChildren()
	{
		return realChildren.aquire(() ->
		{
			KList<IrisBiome> realChildren = new KList<>();

			for(String i : getChildren())
			{
				realChildren.add(Iris.data.getBiomeLoader().load(i));
			}

			return realChildren;
		});
	}

	public KList<String> getAllChildren(int limit)
	{
		KSet<String> m = new KSet<>();
		m.addAll(getChildren());
		limit--;

		if(limit > 0)
		{
			for(String i : getChildren())
			{
				IrisBiome b = Iris.data.getBiomeLoader().load(i);
				int l = limit;
				m.addAll(b.getAllChildren(l));
			}
		}

		return new KList<String>(m);
	}

	public Biome getGroundBiome(RNG rng, double x, double y, double z)
	{
		if(biomeSkyScatter.isEmpty())
		{
			return getDerivative();
		}

		if(biomeScatter.size() == 1)
		{
			return biomeScatter.get(0);
		}

		return biomeScatter.get(getBiomeGenerator(rng).fit(0, biomeScatter.size() - 1, x, y, z));
	}
}
