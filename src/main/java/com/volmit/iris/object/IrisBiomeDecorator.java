package com.volmit.iris.object;

import org.bukkit.block.data.BlockData;

import com.volmit.iris.gen.atomics.AtomicCache;
import com.volmit.iris.noise.CNG;
import com.volmit.iris.util.ArrayType;
import com.volmit.iris.util.B;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.KMap;
import com.volmit.iris.util.MaxNumber;
import com.volmit.iris.util.MinNumber;
import com.volmit.iris.util.RNG;
import com.volmit.iris.util.Required;

import lombok.Data;

@Desc("A biome decorator is used for placing flowers, grass, cacti and so on")
@Data
public class IrisBiomeDecorator
{
	@DontObfuscate
	@Desc("The varience dispersion is used when multiple blocks are put in the palette. Scatter scrambles them, Wispy shows streak-looking varience")
	private NoiseStyle variance = NoiseStyle.STATIC;

	@DontObfuscate
	@Desc("Dispersion is used to pick places to spawn. Scatter randomly places them (vanilla) or Wispy for a streak like patch system.")
	private NoiseStyle dispersion = NoiseStyle.STATIC;

	@DontObfuscate
	@Desc("If this decorator has a height more than 1 this changes how it picks the height between your maxes. Scatter = random, Wispy = wavy heights")
	private NoiseStyle heightVariance = NoiseStyle.STATIC;

	@DontObfuscate
	@Desc("Tells iris where this decoration is a part of. I.e. SHORE_LINE or SEA_SURFACE")
	private DecorationPart partOf = DecorationPart.NONE;

	@MinNumber(1)
	@MaxNumber(256)
	@DontObfuscate
	@Desc("The minimum repeat stack height (setting to 3 would stack 3 of <block> on top of each other")
	private int stackMin = 1;

	@MinNumber(1)
	@MaxNumber(256)
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

	@MinNumber(0.0001)
	@DontObfuscate
	@Desc("The vertical zoom is for wispy stack heights. Zooming this in makes stack heights more slowly change over a distance")
	private double verticalZoom = 1;

	@Required
	@MinNumber(0)
	@MaxNumber(1)
	@DontObfuscate
	@Desc("The chance for this decorator to decorate at a given X,Y coordinate. This is hit 256 times per chunk (per surface block)")
	private double chance = 0.1;

	@Required
	@ArrayType(min = 1, type = String.class)
	@DontObfuscate
	@Desc("The palette of blocks to pick from when this decorator needs to place.")
	private KList<String> palette = new KList<String>().qadd("GRASS");

	private transient KMap<Long, CNG> layerGenerators;
	private transient KMap<Long, CNG> layerVarianceGenerators;
	private transient AtomicCache<CNG> heightGenerator = new AtomicCache<>();
	private transient AtomicCache<KList<BlockData>> blockData = new AtomicCache<>();

	public int getHeight(RNG rng, double x, double z)
	{
		if(stackMin == stackMax)
		{
			return stackMin;
		}

		return getHeightGenerator(rng).fit(stackMin, stackMax, x ,z);
	}

	public CNG getHeightGenerator(RNG rng)
	{
		return heightGenerator.aquire(() ->
		{
			return heightVariance.create(rng.nextParallelRNG(getBlockData().size() + stackMax + stackMin)).scale(1D / verticalZoom);
		});
	}

	public CNG getGenerator(RNG rng)
	{
		long key = rng.nextParallelRNG(1).nextLong();

		if(layerGenerators == null)
		{
			layerGenerators = new KMap<>();
		}

		if(!layerGenerators.containsKey(key))
		{
			layerGenerators.put(key, dispersion.create(rng.nextParallelRNG((int) (getBlockData().size() + key))).scale(1D / zoom));
		}

		return layerGenerators.get(key);
	}
	
	public CNG getVarianceGenerator(RNG rng)
	{
		long key = rng.nextParallelRNG(4).nextLong();

		if(layerVarianceGenerators == null)
		{
			layerGenerators = new KMap<>();
		}

		if(!layerVarianceGenerators.containsKey(key))
		{
			layerVarianceGenerators.put(key, variance.create(rng.nextParallelRNG((int) (getBlockData().size() + key))).scale(1D / varianceZoom));
		}

		return layerVarianceGenerators.get(key);
	}

	public KList<String> add(String b)
	{
		palette.add(b);
		return palette;
	}

	public BlockData getBlockData(RNG rng, double x, double z)
	{
		if(getGenerator(rng) == null)
		{
			return null;
		}

		if(getBlockData() == null)
		{
			return null;
		}

		if(getBlockData().isEmpty())
		{
			return null;
		}

		double xx = x;
		double zz = z;
		xx /= getZoom();
		zz /= getZoom();

		if(getGenerator(rng).fitDoubleD(0D, 1D, xx, zz) <= chance)
		{
			if(getBlockData().size() == 1)
			{
				return getBlockData().get(0);
			}

			return getVarianceGenerator(rng.nextParallelRNG(44)).fit(getBlockData(), xx, zz);
		}

		return null;
	}

	public KList<BlockData> getBlockData()
	{
		return blockData.aquire(() ->
		{
			KList<BlockData> blockData = new KList<>();
			for(String i : palette)
			{
				BlockData bx = B.getBlockData(i);
				if(bx != null)
				{
					blockData.add(bx);
				}
			}

			return blockData;
		});
	}
}
