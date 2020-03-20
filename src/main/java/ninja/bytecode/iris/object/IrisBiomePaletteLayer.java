package ninja.bytecode.iris.object;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import lombok.Data;
import ninja.bytecode.iris.util.CNG;
import ninja.bytecode.iris.util.KList;
import ninja.bytecode.iris.util.KMap;
import ninja.bytecode.iris.util.RNG;

@Data
public class IrisBiomePaletteLayer
{
	private Dispersion dispersion = Dispersion.WISPY;
	private int minHeight = 1;
	private int maxHeight = 1;
	private double terrainZoom = 5;
	private KList<String> palette = new KList<String>().qadd("GRASS_BLOCK");

	private transient KMap<Long, CNG> layerGenerators;
	private transient KList<BlockData> blockData;

	public CNG getGenerator(RNG rng)
	{
		synchronized(this)
		{
			long key = rng.nextParallelRNG(1).nextLong();

			if(layerGenerators == null)
			{
				layerGenerators = new KMap<>();
			}

			if(!layerGenerators.containsKey(key))
			{
				synchronized(layerGenerators)
				{
					layerGenerators.put(key, CNG.signature(rng.nextParallelRNG(minHeight + maxHeight + getBlockData().size())));
				}
			}

			return layerGenerators.get(key);
		}
	}

	public KList<String> add(String b)
	{
		palette.add(b);

		return palette;
	}

	public KList<BlockData> getBlockData()
	{
		synchronized(this)
		{
			if(blockData == null)
			{
				blockData = new KList<>();
				synchronized(blockData)
				{
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
			}
		}

		return blockData;
	}
}
