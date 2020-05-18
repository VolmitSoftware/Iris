package ninja.bytecode.iris.object;

import java.util.concurrent.locks.ReentrantLock;

import org.bukkit.block.data.BlockData;

import lombok.Data;
import ninja.bytecode.iris.util.BlockDataTools;
import ninja.bytecode.iris.util.CNG;
import ninja.bytecode.iris.util.RNG;
import ninja.bytecode.shuriken.collections.KList;

@Data
public class IrisBiomePaletteLayer
{
	private Dispersion dispersion = Dispersion.SCATTER;
	private int minHeight = 1;
	private int maxHeight = 1;
	private double terrainZoom = 5;
	private KList<String> palette = new KList<String>().qadd("GRASS_BLOCK");

	private transient ReentrantLock lock = new ReentrantLock();
	private transient CNG layerGenerator;
	private transient CNG heightGenerator;
	private transient KList<BlockData> blockData;

	public CNG getHeightGenerator(RNG rng)
	{
		if(heightGenerator == null)
		{
			heightGenerator = CNG.signature(rng.nextParallelRNG(minHeight * maxHeight + getBlockData().size()));
		}

		return heightGenerator;
	}

	public BlockData get(RNG rng, double x, double y, double z)
	{
		if(layerGenerator == null)
		{
			cacheGenerator(rng);
		}

		if(layerGenerator != null)
		{
			if(dispersion.equals(Dispersion.SCATTER))
			{
				return getBlockData().get(layerGenerator.fit(0, 30000000, x, y, z) % getBlockData().size());
			}

			else
			{
				return getBlockData().get(layerGenerator.fit(0, getBlockData().size() - 1, x, y, z));
			}
		}

		return getBlockData().get(0);
	}

	public void cacheGenerator(RNG rng)
	{
		RNG rngx = rng.nextParallelRNG(minHeight + maxHeight + getBlockData().size());

		switch(dispersion)
		{
			case SCATTER:
				layerGenerator = CNG.signature(rngx).freq(1000000);
				break;
			case WISPY:
				layerGenerator = CNG.signature(rngx);
				break;
		}
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
