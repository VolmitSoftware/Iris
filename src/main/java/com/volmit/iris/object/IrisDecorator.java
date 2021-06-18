package com.volmit.iris.object;

import com.volmit.iris.Iris;
import com.volmit.iris.scaffold.cache.AtomicCache;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.generator.noise.CNG;
import com.volmit.iris.util.*;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.block.data.BlockData;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("A biome decorator is used for placing flowers, grass, cacti and so on")
@Data
public class IrisDecorator
{
	@DontObfuscate
	@Desc("The varience dispersion is used when multiple blocks are put in the palette. Scatter scrambles them, Wispy shows streak-looking varience")
	private IrisGeneratorStyle variance = NoiseStyle.STATIC.style();

	@DontObfuscate
	@Desc("Forcefully place this decorant anywhere it is supposed to go even if it should not go on a specific surface block. For example, you could force tallgrass to place on top of stone by using this.")
	private boolean forcePlace = false;

	@DontObfuscate
	@Desc("Dispersion is used to pick places to spawn. Scatter randomly places them (vanilla) or Wispy for a streak like patch system.")
	private IrisGeneratorStyle style = NoiseStyle.STATIC.style();

	@DependsOn({"stackMin", "stackMax"})
	@DontObfuscate
	@Desc("If this decorator has a height more than 1 this changes how it picks the height between your maxes. Scatter = random, Wispy = wavy heights")
	private IrisGeneratorStyle heightVariance = NoiseStyle.STATIC.style();

	@DontObfuscate
	@Desc("Tells iris where this decoration is a part of. I.e. SHORE_LINE or SEA_SURFACE")
	private DecorationPart partOf = DecorationPart.NONE;

	@DependsOn({"stackMin", "stackMax"})
	@MinNumber(1)
	@MaxNumber(256) // TODO: WARNING HEIGHT
	@DontObfuscate
	@Desc("The minimum repeat stack height (setting to 3 would stack 3 of <block> on top of each other")
	private int stackMin = 1;

	@DependsOn({"stackMin", "stackMax"})
	@MinNumber(1)
	@MaxNumber(256) // TODO: WARNING HEIGHT
	@DontObfuscate
	@Desc("The maximum repeat stack height")
	private int stackMax = 1;

	@MinNumber(0.0001)
	@DontObfuscate
	@Desc("The zoom is for zooming in or out wispy dispersions. Makes patches bigger the higher this zoom value is")
	private double zoom = 1;

	@MinNumber(0.0001)
	@DontObfuscate
	@Desc("The zoom is for zooming in or out variance. Makes patches have more or less of one type.")
	private double varianceZoom = 1;

	@DependsOn({"stackMin", "stackMax"})
	@MinNumber(0.0001)
	@DontObfuscate
	@Desc("The vertical zoom is for wispy stack heights. Zooming this in makes stack heights more slowly change over a distance")
	private double verticalZoom = 1;

	@Required
	@MinNumber(0)
	@MaxNumber(1)
	@DontObfuscate
	@Desc("The chance for this decorator to decorate at a given X,Y coordinate. This is hit 256 times per chunk (per surface block)") // TODO: WARNING HEIGHT
	private double chance = 0.1;

	@Required
	@ArrayType(min = 1, type = IrisBlockData.class)
	@DontObfuscate
	@Desc("The palette of blocks to pick from when this decorator needs to place.")
	private KList<IrisBlockData> palette = new KList<IrisBlockData>().qadd(new IrisBlockData("grass"));

	@ArrayType(min = 1, type = IrisBlockData.class)
	@DontObfuscate
	@Desc("The palette of blocks used at the very top of a 'stackMax' of higher than 1. For example, bamboo tops.")
	private KList<IrisBlockData> topPalette = new KList<IrisBlockData>();

	private final transient AtomicCache<CNG> layerGenerator = new AtomicCache<>();
	private final transient AtomicCache<CNG> varianceGenerator = new AtomicCache<>();
	private final transient AtomicCache<CNG> heightGenerator = new AtomicCache<>();
	private final transient AtomicCache<KList<BlockData>> blockData = new AtomicCache<>();
	private final transient AtomicCache<KList<BlockData>> blockDataTops = new AtomicCache<>();

	public int getHeight(RNG rng, double x, double z, IrisDataManager data)
	{
		if(stackMin == stackMax)
		{
			return stackMin;
		}

		return getHeightGenerator(rng, data).fit(stackMin, stackMax, x / verticalZoom, z / verticalZoom);
	}

	public CNG getHeightGenerator(RNG rng, IrisDataManager data)
	{
		return heightGenerator.aquire(() ->
		{
			return heightVariance.create(rng.nextParallelRNG(getBlockData(data).size() + stackMax + stackMin));
		});
	}

	public CNG getGenerator(RNG rng, IrisDataManager data)
	{
		return layerGenerator.aquire(() -> style.create(rng.nextParallelRNG((int) (getBlockData(data).size()))));
	}

	public CNG getVarianceGenerator(RNG rng, IrisDataManager data)
	{
		return varianceGenerator.aquire(() ->
				variance.create(
						rng.nextParallelRNG((int) (getBlockData(data).size())))

						.scale(1D / varianceZoom));
	}

	public KList<IrisBlockData> add(String b)
	{
		palette.add(new IrisBlockData(b));
		return palette;
	}

	public BlockData getBlockData(IrisBiome b, RNG rng, double x, double z, IrisDataManager data)
	{
		if(getBlockData(data).isEmpty())
		{
			Iris.warn("Empty Block Data for " + b.getName());
			return null;
		}

		double xx = x / getZoom();
		double zz = z / getZoom();

		if(getGenerator(rng, data).fitDouble(0D, 1D, xx, zz) <= chance)
		{
			if(getBlockData(data).size() == 1)
			{
				return getBlockData(data).get(0);
			}

			return getVarianceGenerator(rng, data).fit(getBlockData(data), xx, zz);
		}

		return null;
	}

	public BlockData getBlockData100(IrisBiome b, RNG rng, double x, double z, IrisDataManager data)
	{
		if(getBlockData(data).isEmpty())
		{
			Iris.warn("Empty Block Data for " + b.getName());
			return null;
		}

		double xx = x;
		double zz = z;

		if(!getVarianceGenerator(rng, data).isStatic())
		{
			xx = x / getZoom();
			zz = z / getZoom();
		}

		if(getBlockData(data).size() == 1)
		{
			return getBlockData(data).get(0);
		}

		return getVarianceGenerator(rng, data).fit(getBlockData(data), xx, zz).clone();
	}

	public BlockData getBlockDataForTop(IrisBiome b, RNG rng, double x, double z, IrisDataManager data)
	{
		if(getBlockDataTops(data).isEmpty())
		{
			return null;
		}

		double xx = x / getZoom();
		double zz = z / getZoom();

		if(getGenerator(rng, data).fitDouble(0D, 1D, xx, zz) <= chance)
		{
			if(getBlockData(data).size() == 1)
			{
				return getBlockDataTops(data).get(0);
			}

			return getVarianceGenerator(rng, data).fit(getBlockDataTops(data), xx, zz);
		}

		return null;
	}

	public KList<BlockData> getBlockData(IrisDataManager data)
	{
		return blockData.aquire(() ->
		{
			KList<BlockData> blockData = new KList<>();
			for(IrisBlockData i : palette)
			{
				BlockData bx = i.getBlockData(data);
				if(bx != null)
				{
					blockData.add(bx);
				}
			}

			return blockData;
		});
	}

	public KList<BlockData> getBlockDataTops(IrisDataManager data)
	{
		return blockDataTops.aquire(() ->
		{
			KList<BlockData> blockDataTops = new KList<>();
			for(IrisBlockData i : topPalette)
			{
				BlockData bx = i.getBlockData(data);
				if(bx != null)
				{
					blockDataTops.add(bx);
				}
			}

			return blockDataTops;
		});
	}

	public boolean isStacking()
	{
		return getStackMax() > 1;
	}
}
