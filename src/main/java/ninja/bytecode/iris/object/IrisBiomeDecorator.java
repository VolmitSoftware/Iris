package ninja.bytecode.iris.object;

import org.bukkit.block.data.BlockData;

import lombok.Data;
import ninja.bytecode.iris.util.BlockDataTools;
import ninja.bytecode.iris.util.CNG;
import ninja.bytecode.iris.util.Desc;
import ninja.bytecode.iris.util.RNG;
import ninja.bytecode.shuriken.collections.KList;
import ninja.bytecode.shuriken.collections.KMap;

@Desc("A biome decorator is used for placing flowers, grass, cacti and so on")
@Data
public class IrisBiomeDecorator
{
	@Desc("The varience dispersion is used when multiple blocks are put in the palette. Scatter scrambles them, Wispy shows streak-looking varience")
	private Dispersion variance = Dispersion.SCATTER;

	@Desc("Dispersion is used to pick places to spawn. Scatter randomly places them (vanilla) or Wispy for a streak like patch system.")
	private Dispersion dispersion = Dispersion.SCATTER;

	@Desc("If this decorator has a height more than 1 this changes how it picks the height between your maxes. Scatter = random, Wispy = wavy heights")
	private Dispersion verticalVariance = Dispersion.SCATTER;

	@Desc("Tells iris where this decoration is a part of. I.e. SHORE_LINE")
	private DecorationPart partOf = DecorationPart.NONE;

	@Desc("The minimum repeat stack height (setting to 3 would stack 3 of <block> on top of each other")
	private int stackMin = 1;

	@Desc("The maximum repeat stack height")
	private int stackMax = 1;

	@Desc("The zoom is for zooming in or out wispy dispersions. Makes patches bigger the higher this zoom value is/")
	private double zoom = 1;

	@Desc("The vertical zoom is for wispy stack heights. Zooming this in makes stack heights more slowly change over a distance")
	private double verticalZoom = 1;

	@Desc("The chance for this decorator to decorate at a given X,Y coordinate. This is hit 256 times per chunk (per surface block)")
	private double chance = 0.1;

	@Desc("The palette of blocks to pick from when this decorator needs to place.")
	private KList<String> palette = new KList<String>().qadd("GRASS");

	private transient KMap<Long, CNG> layerGenerators;
	private transient CNG heightGenerator;
	private transient KList<BlockData> blockData;
	private transient RNG nrng;

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
			layerGenerators.put(key, CNG.signature(rng.nextParallelRNG(getBlockData().size())).scale(1D / zoom));
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

		if(nrng == null)
		{
			nrng = rng.nextParallelRNG(2398552 + hashCode());
		}

		double xx = dispersion.equals(Dispersion.SCATTER) ? nrng.i(-100000, 100000) : x;
		double zz = dispersion.equals(Dispersion.SCATTER) ? nrng.i(-100000, 100000) : z;

		if(getGenerator(rng).fitDoubleD(0D, 1D, xx, zz) <= chance)
		{
			try
			{
				return getBlockData().get(getGenerator(rng.nextParallelRNG(53)).fit(0, getBlockData().size() - 1, xx, zz));
			}

			catch(Throwable e)
			{

			}
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
