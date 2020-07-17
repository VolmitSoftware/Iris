package ninja.bytecode.iris.object;

import java.util.concurrent.locks.ReentrantLock;

import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

import lombok.Data;
import lombok.EqualsAndHashCode;
import ninja.bytecode.iris.util.CNG;
import ninja.bytecode.iris.util.CellGenerator;
import ninja.bytecode.iris.util.RNG;
import ninja.bytecode.shuriken.collections.KList;
import ninja.bytecode.shuriken.logging.L;

@Data
@EqualsAndHashCode(callSuper = false)
public class IrisBiome extends IrisRegistrant
{
	private String name = "A Biome";
	private Biome derivative = Biome.THE_VOID;
	private double childShrinkFactor = 1.5;
	private KList<String> children = new KList<>();
	private KList<IrisBiomePaletteLayer> layers = new KList<IrisBiomePaletteLayer>().qadd(new IrisBiomePaletteLayer());
	private KList<IrisBiomeDecorator> decorators = new KList<IrisBiomeDecorator>();
	private KList<IrisObjectPlacement> objects = new KList<IrisObjectPlacement>();
	private KList<IrisBiomeGeneratorLink> generators = new KList<IrisBiomeGeneratorLink>().qadd(new IrisBiomeGeneratorLink());
	private transient ReentrantLock lock = new ReentrantLock();
	private transient CellGenerator childrenCell;
	private transient InferredType inferredType;
	private transient KList<CNG> layerHeightGenerators;
	private transient KList<CNG> layerSurfaceGenerators;

	public IrisBiome()
	{

	}

	public double getHeight(double x, double z, long seed)
	{
		double height = 0;

		for(IrisBiomeGeneratorLink i : generators)
		{
			height += i.getHeight(x, z, seed);
		}

		return Math.max(0, Math.min(height, 255));
	}

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
			int d = hgen.fit(layers.get(i).getMinHeight(), layers.get(i).getMaxHeight(), wx / layers.get(i).getTerrainZoom(), wz / layers.get(i).getTerrainZoom());

			if(d < 0)
			{
				continue;
			}

			for(int j = 0; j < d; j++)
			{
				if(data.size() >= maxDepth)
				{
					break;
				}

				try
				{
					data.add(getLayers().get(i).get(random.nextParallelRNG(i + j), (wx + j) / layers.get(i).getTerrainZoom(), j, (wz - j) / layers.get(i).getTerrainZoom()));
				}

				catch(Throwable e)
				{
					L.ex(e);
				}
			}

			if(data.size() >= maxDepth)
			{
				break;
			}
		}

		return data;
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
				layerHeightGenerators.add(i.getHeightGenerator(rng.nextParallelRNG((m++) * m * m * m)));
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
