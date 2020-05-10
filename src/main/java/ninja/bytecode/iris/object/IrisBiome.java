package ninja.bytecode.iris.object;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

import lombok.Data;
import lombok.EqualsAndHashCode;
import ninja.bytecode.iris.util.CNG;
import ninja.bytecode.iris.util.CellGenerator;
import ninja.bytecode.iris.util.RNG;
import ninja.bytecode.shuriken.collections.KList;

@Data
@EqualsAndHashCode(callSuper = false)
public class IrisBiome extends IrisRegisteredObject
{
	private String name = "A Biome";
	private Biome derivative = Biome.THE_VOID;
	private double highHeight = 7;
	private double lowHeight = 1;
	private double childShrinkFactor = 1.5;
	private KList<String> children = new KList<>();
	private KList<IrisBiomePaletteLayer> layers = new KList<IrisBiomePaletteLayer>().qadd(new IrisBiomePaletteLayer());
	private KList<IrisBiomeDecorator> decorators = new KList<IrisBiomeDecorator>();

	private transient ReentrantLock lock = new ReentrantLock();
	private transient CellGenerator childrenCell;
	private transient InferredType inferredType;
	private transient KList<CNG> layerHeightGenerators;
	private transient KList<CNG> layerSurfaceGenerators;

	public CellGenerator getChildrenGenerator(RNG random, int sig, double scale)
	{
		if(childrenCell == null)
		{
			childrenCell = new CellGenerator(random.nextParallelRNG(sig * 213));
			childrenCell.setCellScale(scale);
		}

		return childrenCell;
	}

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

				try
				{
					data.add(palette.get(sgen.fit(0, palette.size() - 1, (wx + j) / layers.get(i).getTerrainZoom(), (wz - j) / layers.get(i).getTerrainZoom())));
				}

				catch(Throwable e)
				{

				}
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
		lock.lock();
		if(layerSurfaceGenerators == null)
		{
			layerSurfaceGenerators = new KList<>();

			int m = 91235;

			for(IrisBiomePaletteLayer i : getLayers())
			{
				layerSurfaceGenerators.add(i.getGenerator(rng.nextParallelRNG((m += 3) * m * m * m)));
			}
		}
		lock.unlock();

		return layerSurfaceGenerators;
	}

	public KList<CNG> getLayerHeightGenerators(RNG rng)
	{
		lock.lock();
		if(layerHeightGenerators == null)
		{
			layerHeightGenerators = new KList<>();

			int m = 7235;

			for(IrisBiomePaletteLayer i : getLayers())
			{
				layerHeightGenerators.add(i.getGenerator(rng.nextParallelRNG((m++) * m * m * m)));
			}
		}
		lock.unlock();

		return layerHeightGenerators;
	}

	public boolean isLand()
	{
		return inferredType.equals(InferredType.LAND);
	}

	public boolean isSea()
	{
		return inferredType.equals(InferredType.SEA);
	}

	public boolean isShore()
	{
		return inferredType.equals(InferredType.SHORE);
	}
}
