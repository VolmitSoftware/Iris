package com.volmit.iris.object;

import java.util.concurrent.locks.ReentrantLock;

import org.bukkit.block.data.BlockData;

import com.volmit.iris.util.BlockDataTools;
import com.volmit.iris.util.CNG;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.RNG;

import lombok.Data;

@Desc("A layer of surface / subsurface material in biomes")
@Data
public class IrisBiomePaletteLayer
{
	@DontObfuscate
	@Desc("The dispersion of materials from the palette")
	private Dispersion dispersion = Dispersion.SCATTER;

	@DontObfuscate
	@Desc("The min thickness of this layer")
	private int minHeight = 1;

	@DontObfuscate
	@Desc("The max thickness of this layer")
	private int maxHeight = 1;

	@DontObfuscate
	@Desc("The terrain zoom mostly for zooming in on a wispy palette")
	private double terrainZoom = 5;

	@DontObfuscate
	@Desc("The palette of blocks to be used in this layer")
	private KList<String> palette = new KList<String>().qadd("GRASS_BLOCK");

	private transient ReentrantLock lock = new ReentrantLock();
	private transient KList<BlockData> blockData;
	private transient CNG layerGenerator;
	private transient CNG heightGenerator;

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

		if(getBlockData().isEmpty())
		{
			return null;
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

	public IrisBiomePaletteLayer zero()
	{
		palette.clear();
		return this;
	}
}
