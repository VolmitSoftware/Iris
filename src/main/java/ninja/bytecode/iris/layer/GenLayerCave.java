package ninja.bytecode.iris.layer;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.generator.DimensionChunkGenerator;
import ninja.bytecode.iris.util.CellGenerator;
import ninja.bytecode.iris.util.FastNoise;
import ninja.bytecode.iris.util.FastNoise.CellularDistanceFunction;
import ninja.bytecode.iris.util.FastNoise.CellularReturnType;
import ninja.bytecode.iris.util.FastNoise.NoiseType;
import ninja.bytecode.iris.util.GenLayer;
import ninja.bytecode.iris.util.RNG;

public class GenLayerCave extends GenLayer
{
	private CellGenerator g;
	private double max = -10000;

	public GenLayerCave(DimensionChunkGenerator iris, RNG rng)
	{
		super(iris, rng);
		g = new CellGenerator(rng.nextParallelRNG(2345));
		g.setShuffle(0);
	}

	public boolean isCave(int i, int j, int k)
	{
		double v = g.getDistance(i, j, k);

		if(v > max)
		{
			max = v;
			Iris.info("MAX: " + max);
		}

		if(v < 0.08)
		{
			return true;
		}

		return false;
	}

	@Override
	public double generate(double x, double z)
	{
		return 0;
	}
}
