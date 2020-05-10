package ninja.bytecode.iris.object;

import java.util.concurrent.locks.ReentrantLock;

import org.bukkit.block.data.BlockData;

import lombok.Data;
import ninja.bytecode.iris.util.BlockDataTools;
import ninja.bytecode.iris.util.CNG;
import ninja.bytecode.iris.util.RNG;
import ninja.bytecode.shuriken.collections.KList;
import ninja.bytecode.shuriken.collections.KMap;

@Data
public class IrisBiomePaletteLayer
{
	private Dispersion dispersion = Dispersion.WISPY;
	private int minHeight = 1;
	private int maxHeight = 1;
	private double terrainZoom = 5;
	private KList<String> palette = new KList<String>().qadd("GRASS_BLOCK");

	private transient ReentrantLock lock = new ReentrantLock();
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
			layerGenerators.put(key, CNG.signature(rng.nextParallelRNG(minHeight + maxHeight + getBlockData().size())));
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
		lock.lock();
		if(blockData == null)
		{
			blockData = new KList<>();
			for(String ix : palette)
			{
				BlockData bx = BlockDataTools.getBlockData(ix);
				if(bx != null)
				{
					blockData.add(bx);
				}
			}
		}
		lock.unlock();

		return blockData;
	}
}
