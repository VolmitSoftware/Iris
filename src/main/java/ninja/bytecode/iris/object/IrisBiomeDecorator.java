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
	private Dispersion dispersion = Dispersion.ZEBRA;
	private int iterations = 5;
	private double zoom = 1;
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
