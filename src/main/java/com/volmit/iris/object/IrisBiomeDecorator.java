package com.volmit.iris.object;

import org.bukkit.block.data.BlockData;

import com.volmit.iris.util.BlockDataTools;
import com.volmit.iris.util.CNG;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.KMap;
import com.volmit.iris.util.RNG;

import lombok.Data;

@Desc("A biome decorator is used for placing flowers, grass, cacti and so on")
@Data
public class IrisBiomeDecorator
{
	@DontObfuscate
	@Desc("The varience dispersion is used when multiple blocks are put in the palette. Scatter scrambles them, Wispy shows streak-looking varience")
	private Dispersion variance = Dispersion.SCATTER;

	@DontObfuscate
	@Desc("Dispersion is used to pick places to spawn. Scatter randomly places them (vanilla) or Wispy for a streak like patch system.")
	private Dispersion dispersion = Dispersion.SCATTER;

	@DontObfuscate
	@Desc("If this decorator has a height more than 1 this changes how it picks the height between your maxes. Scatter = random, Wispy = wavy heights")
	private Dispersion verticalVariance = Dispersion.SCATTER;

	@DontObfuscate
	@Desc("Tells iris where this decoration is a part of. I.e. SHORE_LINE or SEA_SURFACE")
	private DecorationPart partOf = DecorationPart.NONE;

	@DontObfuscate
	@Desc("The minimum repeat stack height (setting to 3 would stack 3 of <block> on top of each other")
	private int stackMin = 1;

	@DontObfuscate
	@Desc("The maximum repeat stack height")
	private int stackMax = 1;

	@DontObfuscate
	@Desc("The zoom is for zooming in or out wispy dispersions. Makes patches bigger the higher this zoom value is/")
	private double zoom = 1;

	@DontObfuscate
	@Desc("The vertical zoom is for wispy stack heights. Zooming this in makes stack heights more slowly change over a distance")
	private double verticalZoom = 1;

	@DontObfuscate
	@Desc("The chance for this decorator to decorate at a given X,Y coordinate. This is hit 256 times per chunk (per surface block)")
	private double chance = 0.1;

	@DontObfuscate
	@Desc("The palette of blocks to pick from when this decorator needs to place.")
	private KList<String> palette = new KList<String>().qadd("GRASS");

	private transient KMap<Long, CNG> layerGenerators;
	private transient CNG heightGenerator;
	private transient KList<BlockData> blockData;

	public int getHeight(RNG rng, double x, double z)
	{
		if(stackMin == stackMax)
		{
			return stackMin;
		}

		return getGenerator(rng).fit(stackMin, stackMax, x * (verticalVariance.equals(Dispersion.SCATTER) ? 1000D : 1D), z * (verticalVariance.equals(Dispersion.SCATTER) ? 1000D : 1D));
	}

	public CNG getHeightGenerator(RNG rng)
	{
		if(heightGenerator == null)
		{
			heightGenerator = CNG.signature(rng.nextParallelRNG(getBlockData().size() + stackMax + stackMin)).scale(1D / verticalZoom);
		}

		return heightGenerator;
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
			layerGenerators.put(key, CNG.signature(rng.nextParallelRNG((int) (getBlockData().size() + key))).scale(1D / zoom));
		}

		return layerGenerators.get(key);
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

		RNG nrng = dispersion.equals(Dispersion.SCATTER) ? rng.nextParallelRNG((int) (z - (int) ((x + 34856) * (int) (x + z + (int) (28835521 + (getChance() * 1000) + getStackMin() + getStackMax() + (getZoom() * 556)))))) : null;
		double xx = dispersion.equals(Dispersion.SCATTER) ? nrng.i(-1000000, 1000000) + z : x;
		double zz = dispersion.equals(Dispersion.SCATTER) ? nrng.i(-1000000, 1000000) - x : z;
		xx /= getZoom();
		zz /= getZoom();

		if(getGenerator(rng).fitDoubleD(0D, 1D, xx, zz) <= chance)
		{
			if(getBlockData().size() == 1)
			{
				return getBlockData().get(0);
			}

			return getBlockData().get(getGenerator(rng.nextParallelRNG(44)).fit(0, getBlockData().size() - 1, xx, zz));
		}

		return null;
	}

	public KList<BlockData> getBlockData()
	{
		if(blockData == null)
		{
			blockData = new KList<>();
			for(String i : palette)
			{
				BlockData bx = BlockDataTools.getBlockData(i);
				if(bx != null)
				{
					blockData.add(bx);
				}
			}
		}

		return blockData;
	}
}
