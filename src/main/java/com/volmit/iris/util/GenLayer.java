package ninja.bytecode.iris.util;

import ninja.bytecode.iris.generator.DimensionChunkGenerator;

public abstract class GenLayer
{
	protected final RNG rng;
	protected final DimensionChunkGenerator iris;

	public GenLayer(DimensionChunkGenerator iris, RNG rng)
	{
		this.iris = iris;
		this.rng = rng;
	}

	public abstract double generate(double x, double z);
}
