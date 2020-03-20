package ninja.bytecode.iris.object;

import java.util.List;

import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

import lombok.Data;
import ninja.bytecode.iris.util.CNG;
import ninja.bytecode.iris.util.KList;
import ninja.bytecode.iris.util.RNG;

@Data
public class IrisBiome
{
	private String name = "A Biome";
	private Biome derivative = Biome.THE_VOID;
	private double highHeight = 7;
	private double lowHeight = 1;
	private double heightExponent = 1;
	private KList<IrisBiomePaletteLayer> layers = new KList<IrisBiomePaletteLayer>().qadd(new IrisBiomePaletteLayer());

	private transient KList<CNG> layerHeightGenerators;
	private transient KList<CNG> layerSurfaceGenerators;

	public KList<BlockData> generateLayers(double wx, double wz, RNG random, int maxDepth)
	{
		KList<BlockData> data = new KList<>();

		for(int i = 0; i < layers.size(); i++)
		{
			CNG hgen = getLayerHeightGenerators(random).get(i);
			CNG sgen = getLayerSurfaceGenerators(random).get(i);
			int d = hgen.fit(layers.get(i).getMinHeight(), layers.get(i).getMaxHeight(), wx / layers.get(i).getTerrainZoom(), wz / layers.get(i).getTerrainZoom());

			if(d < 0)
			{
				continue;
			}

			List<BlockData> palette = getLayers().get(i).getBlockData();

			for(int j = 0; j < d; j++)
			{
				if(data.size() >= maxDepth)
				{
					break;
				}

				data.add(palette.get(sgen.fit(0, palette.size() - 1, (wx + j) / layers.get(i).getTerrainZoom(), (wz - j) / layers.get(i).getTerrainZoom())));
			}

			if(data.size() >= maxDepth)
			{
				break;
			}
		}

		return data;
	}

	public KList<CNG> getLayerSurfaceGenerators(RNG rng)
	{
		synchronized(this)
		{
			if(layerSurfaceGenerators == null)
			{
				layerSurfaceGenerators = new KList<>();

				synchronized(layerSurfaceGenerators)
				{
					int m = 91235;

					for(IrisBiomePaletteLayer i : getLayers())
					{
						layerSurfaceGenerators.add(i.getGenerator(rng.nextParallelRNG((m += 3) * m * m * m)));
					}
				}
			}
		}

		return layerSurfaceGenerators;
	}

	public KList<CNG> getLayerHeightGenerators(RNG rng)
	{
		synchronized(this)
		{
			if(layerHeightGenerators == null)
			{
				layerHeightGenerators = new KList<>();

				synchronized(layerHeightGenerators)
				{
					int m = 7235;

					for(IrisBiomePaletteLayer i : getLayers())
					{
						layerHeightGenerators.add(i.getGenerator(rng.nextParallelRNG((m++) * m * m * m)));
					}
				}
			}
		}

		return layerHeightGenerators;
	}
}
