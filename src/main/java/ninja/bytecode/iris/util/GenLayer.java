package ninja.bytecode.iris.util;

import ninja.bytecode.iris.IrisGenerator;

public abstract class GenLayer
{
	protected final RNG rng;
	protected final IrisGenerator iris;

	public GenLayer(IrisGenerator iris, RNG rng)
	{
		this.iris = iris;
		this.rng = rng;
	}

	public abstract double generate(double x, double z);
}
