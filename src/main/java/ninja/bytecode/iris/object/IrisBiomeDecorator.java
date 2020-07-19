package ninja.bytecode.iris.object;

import org.bukkit.block.data.BlockData;

import lombok.Data;
import ninja.bytecode.iris.util.BlockDataTools;
import ninja.bytecode.iris.util.CNG;
import ninja.bytecode.iris.util.RNG;
import ninja.bytecode.shuriken.collections.KList;
import ninja.bytecode.shuriken.collections.KMap;

@Data
public class IrisBiomeDecorator
{
	private Dispersion variance = Dispersion.SCATTER;
	private Dispersion dispersion = Dispersion.SCATTER;
	private Dispersion verticalVariance = Dispersion.SCATTER;
	private int iterations = 5;
	private int stackMin = 1;
	private int stackMax = 1;
	private double zoom = 1;
	private double verticalZoom = 1;
	private double chance = 0.1;
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
			heightGenerator = CNG.signature(rng.nextParallelRNG(iterations + getBlockData().size() + stackMax + stackMin)).scale(1D / verticalZoom);
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
			layerGenerators.put(key, CNG.signature(rng.nextParallelRNG(iterations + getBlockData().size())).scale(1D / zoom));
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

		if(getGenerator(rng).fitDoubleD(0D, 1D, x * (dispersion.equals(Dispersion.SCATTER) ? 1000D : 1D), z * (dispersion.equals(Dispersion.SCATTER) ? 1000D : 1D)) <= chance)
		{
			try
			{
				return getBlockData().get(getGenerator(rng.nextParallelRNG(53)).fit(0, getBlockData().size() - 1, x * (dispersion.equals(Dispersion.SCATTER) ? 1000D : 1D), z * (dispersion.equals(Dispersion.SCATTER) ? 1000D : 1D)));
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
