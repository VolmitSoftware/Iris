package com.volmit.iris.util;

import com.volmit.iris.gen.DimensionalTerrainProvider;

public abstract class GenLayer
{
	protected final RNG rng;
	protected final DimensionalTerrainProvider iris;

	public GenLayer(DimensionalTerrainProvider iris, RNG rng)
	{
		this.iris = iris;
		this.rng = rng;
	}

	public abstract double generate(double x, double z);
}
