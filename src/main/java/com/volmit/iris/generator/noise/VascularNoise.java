package com.volmit.iris.generator.noise;

import com.volmit.iris.util.M;
import com.volmit.iris.util.RNG;

public class VascularNoise implements NoiseGenerator
{
	private final FastNoiseDouble n;

	public VascularNoise(long seed)
	{
		this.n = new FastNoiseDouble(new RNG(seed).lmax());
		n.setNoiseType(FastNoiseDouble.NoiseType.Cellular);
		n.setCellularReturnType(FastNoiseDouble.CellularReturnType.Distance2Sub);
		n.setCellularDistanceFunction(FastNoiseDouble.CellularDistanceFunction.Natural);
	}

	private double filter(double noise)
	{
		return M.clip((noise / 2D) + 0.5D, 0D, 1D);
	}

	@Override
	public double noise(double x)
	{
		return filter(n.GetCellular(x, 0));
	}

	@Override
	public double noise(double x, double z)
	{
		return filter(n.GetCellular(x, z));
	}

	@Override
	public double noise(double x, double y, double z)
	{
		return filter(n.GetCellular(x, y, z));
	}
}
