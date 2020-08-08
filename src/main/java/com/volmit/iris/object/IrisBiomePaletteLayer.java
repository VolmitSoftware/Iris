package com.volmit.iris.object;

import org.bukkit.block.data.BlockData;

import com.volmit.iris.gen.atomics.AtomicCache;
import com.volmit.iris.util.ArrayType;
import com.volmit.iris.util.B;
import com.volmit.iris.util.CNG;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MaxNumber;
import com.volmit.iris.util.MinNumber;
import com.volmit.iris.util.RNG;
import com.volmit.iris.util.Required;

import lombok.Data;

@Desc("A layer of surface / subsurface material in biomes")
@Data
public class IrisBiomePaletteLayer
{
	@DontObfuscate
	@Desc("The dispersion of materials from the palette")
	private Dispersion dispersion = Dispersion.SCATTER;

	@MinNumber(0)
	@MaxNumber(256)
	@DontObfuscate
	@Desc("The min thickness of this layer")
	private int minHeight = 1;

	@MinNumber(1)
	@MaxNumber(256)
	@DontObfuscate
	@Desc("The max thickness of this layer")
	private int maxHeight = 1;

	@MinNumber(0.0001)
	@DontObfuscate
	@Desc("The terrain zoom mostly for zooming in on a wispy palette")
	private double terrainZoom = 5;

	@Required
	@ArrayType(min = 1, type = String.class)
	@DontObfuscate
	@Desc("The palette of blocks to be used in this layer")
	private KList<String> palette = new KList<String>().qadd("GRASS_BLOCK");

	private transient AtomicCache<KList<BlockData>> blockData = new AtomicCache<>();
	private transient AtomicCache<CNG> layerGenerator = new AtomicCache<>();
	private transient AtomicCache<CNG> heightGenerator = new AtomicCache<>();

	public CNG getHeightGenerator(RNG rng)
	{
		return heightGenerator.aquire(() -> CNG.signature(rng.nextParallelRNG(minHeight * maxHeight + getBlockData().size())));
	}

	public BlockData get(RNG rng, double x, double y, double z)
	{
		if(getBlockData().isEmpty())
		{
			return null;
		}

		if(getBlockData().size() == 1)
		{
			return getBlockData().get(0);
		}

		if(dispersion.equals(Dispersion.SCATTER))
		{
			return getBlockData().get(getLayerGenerator(rng).fit(0, 30000000, x, y, z) % getBlockData().size());
		}

		else
		{
			return getBlockData().get(getLayerGenerator(rng).fit(0, getBlockData().size() - 1, x, y, z));
		}
	}

	public CNG getLayerGenerator(RNG rng)
	{
		return layerGenerator.aquire(() ->
		{
			CNG layerGenerator = new CNG(rng);
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

			return layerGenerator;
		});
	}

	public KList<String> add(String b)
	{
		palette.add(b);

		return palette;
	}

	public KList<BlockData> getBlockData()
	{
		return blockData.aquire(() ->
		{
			KList<BlockData> blockData = new KList<>();
			for(String ix : palette)
			{
				BlockData bx = B.getBlockData(ix);
				if(bx != null)
				{
					blockData.add(bx);
				}
			}

			return blockData;
		});
	}

	public IrisBiomePaletteLayer zero()
	{
		palette.clear();
		return this;
	}
}
