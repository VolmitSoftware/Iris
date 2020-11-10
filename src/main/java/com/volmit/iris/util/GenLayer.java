package com.volmit.iris.util;

import com.volmit.iris.generator.legacy.TopographicTerrainProvider;

public abstract class GenLayer
{
	protected final RNG rng;
	protected final TopographicTerrainProvider iris;

	public GenLayer(TopographicTerrainProvider iris, RNG rng)
	{
		this.iris = iris;
		this.rng = rng;
	}

	public GenLayer()
	{
		this(null, null);
	}

	public abstract double generate(double x, double z);
}
