package com.volmit.iris.object;

import org.bukkit.block.data.BlockData;

import com.volmit.iris.gen.atomics.AtomicCache;
import com.volmit.iris.noise.CNG;
import com.volmit.iris.util.ArrayType;
import com.volmit.iris.util.B;
import com.volmit.iris.util.DependsOn;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MaxNumber;
import com.volmit.iris.util.MinNumber;
import com.volmit.iris.util.RNG;
import com.volmit.iris.util.Required;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@Builder
@AllArgsConstructor
@Desc("A layer of surface / subsurface material in biomes")
@Data
public class IrisBiomePaletteLayer
{
	@Builder.Default
	@DontObfuscate
	@Desc("The style of noise")
	private IrisGeneratorStyle style = NoiseStyle.STATIC.style();

	@Builder.Default
	@DependsOn({"minHeight", "maxHeight"})
	@MinNumber(0)
	@MaxNumber(256)
	@DontObfuscate
	@Desc("The min thickness of this layer")
	private int minHeight = 1;

	@Builder.Default
	@DependsOn({"minHeight", "maxHeight"})
	@MinNumber(1)
	@MaxNumber(256)
	@DontObfuscate
	@Desc("The max thickness of this layer")
	private int maxHeight = 1;

	@Builder.Default
	@MinNumber(0.0001)
	@DontObfuscate
	@Desc("The terrain zoom mostly for zooming in on a wispy palette")
	private double zoom = 5;

	@Builder.Default
	@Required
	@ArrayType(min = 1, type = String.class)
	@DontObfuscate
	@Desc("The palette of blocks to be used in this layer")
	private KList<String> palette = new KList<String>().qadd("GRASS_BLOCK");

	private final transient AtomicCache<KList<BlockData>> blockData = new AtomicCache<>();
	private final transient AtomicCache<CNG> layerGenerator = new AtomicCache<>();
	private final transient AtomicCache<CNG> heightGenerator = new AtomicCache<>();

	public IrisBiomePaletteLayer()
	{

	}

	{

	}

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

		return getLayerGenerator(rng).fit(getBlockData(), x / zoom, y / zoom, z / zoom);
	}

	public CNG getLayerGenerator(RNG rng)
	{
		return layerGenerator.aquire(() ->
		{
			RNG rngx = rng.nextParallelRNG(minHeight + maxHeight + getBlockData().size());
			return style.create(rngx);
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
