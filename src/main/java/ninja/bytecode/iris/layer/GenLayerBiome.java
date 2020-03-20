package ninja.bytecode.iris.layer;

import ninja.bytecode.iris.IrisGenerator;
import ninja.bytecode.iris.object.IrisBiome;
import ninja.bytecode.iris.util.BiomeResult;
import ninja.bytecode.iris.util.CellGenerator2D;
import ninja.bytecode.iris.util.GenLayer;
import ninja.bytecode.iris.util.KList;
import ninja.bytecode.iris.util.RNG;

public class GenLayerBiome extends GenLayer
{
	private CellGenerator2D cells;

	public GenLayerBiome(IrisGenerator iris, RNG rng)
	{
		super(iris, rng);
		cells = new CellGenerator2D(rng.nextParallelRNG(2045662));
	}

	public KList<IrisBiome> getBiomes()
	{
		return iris.getDimension().buildBiomeList();
	}

	public BiomeResult generateData(double x, double z)
	{
		return new BiomeResult(getBiomes().get(cells.getIndex(x / iris.getDimension().getBiomeZoom(), z / iris.getDimension().getBiomeZoom(), getBiomes().size())), cells.getDistance(x / iris.getDimension().getBiomeZoom(), z / iris.getDimension().getBiomeZoom()));
	}

	@Override
	public double generate(double x, double z)
	{
		return 0;
	}
}
