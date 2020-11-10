package com.volmit.iris.generator.noise;

import com.volmit.iris.util.RNG;

public class GlobNoise implements NoiseGenerator
{
	private final FastNoiseDouble n;

	public GlobNoise(long seed)
	{
		this.n = new FastNoiseDouble(new RNG(seed).lmax());
		n.setNoiseType(FastNoiseDouble.NoiseType.Cellular);
		n.setCellularReturnType(FastNoiseDouble.CellularReturnType.Distance2Div);
		n.setCellularDistanceFunction(FastNoiseDouble.CellularDistanceFunction.Natural);
	}

	private double f(double n)
	{
		return n + 1D;
	}

	@Override
	public double noise(double x)
	{
		return f(n.GetCellular(x, 0));
	}

	@Override
	public double noise(double x, double z)
	{
		return f(n.GetCellular(x, z));
	}

	@Override
	public double noise(double x, double y, double z)
	{
		return f(n.GetCellular(x, y, z));
	}
}
