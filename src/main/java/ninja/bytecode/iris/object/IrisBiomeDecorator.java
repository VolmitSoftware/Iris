package ninja.bytecode.iris.object;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import lombok.Data;
import ninja.bytecode.iris.util.CNG;
import ninja.bytecode.iris.util.RNG;
import ninja.bytecode.shuriken.collections.KList;
import ninja.bytecode.shuriken.collections.KMap;

@Data
public class IrisBiomeDecorator
{
	private Dispersion variance = Dispersion.SCATTER;
	private Dispersion dispersion = Dispersion.SCATTER;
	private int iterations = 5;
	private double zoom = 1;
	private double chance = 0.1;
	private KList<String> palette = new KList<String>().qadd("GRASS");

	private transient KMap<Long, CNG> layerGenerators;
	private transient KList<BlockData> blockData;

	public CNG getGenerator(RNG rng)
	{
		long key = rng.nextParallelRNG(1).nextLong();

		if(layerGenerators == null)
		{
			layerGenerators = new KMap<>();
		}

		if(!layerGenerators.containsKey(key))
		{
			layerGenerators.put(key, CNG.signature(rng.nextParallelRNG(iterations + getBlockData().size())));
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
		if(getGenerator(rng).fitDoubleD(0D, 1D, x * (dispersion.equals(Dispersion.SCATTER) ? 1000D : 1D), z * (dispersion.equals(Dispersion.SCATTER) ? 1000D : 1D)) <= chance)
		{
			return getBlockData().get(getGenerator(rng.nextParallelRNG(53)).fit(0, getBlockData().size() - 1, x * (dispersion.equals(Dispersion.SCATTER) ? 1000D : 1D), z * (dispersion.equals(Dispersion.SCATTER) ? 1000D : 1D)));
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
				try
				{
					Material m = Material.valueOf(i);

					if(m != null)
					{
						blockData.add(m.createBlockData());
					}
				}
				catch(Throwable e)
				{

				}
			}
		}

		return blockData;
	}
}
