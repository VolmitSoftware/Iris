package com.volmit.iris.generator.noise;

import com.volmit.iris.util.RNG;

public class CellularNoise implements NoiseGenerator
{
	private final FastNoiseDouble n;

	public CellularNoise(long seed)
	{
		this.n = new FastNoiseDouble(new RNG(seed).lmax());
		n.setNoiseType(FastNoiseDouble.NoiseType.Cellular);
		n.setCellularReturnType(FastNoiseDouble.CellularReturnType.CellValue);
		n.setCellularDistanceFunction(FastNoiseDouble.CellularDistanceFunction.Natural);
	}

	@Override
	public double noise(double x)
	{
		return (n.GetCellular(x, 0) / 2D) + 0.5D;
	}

	@Override
	public double noise(double x, double z)
	{
		return (n.GetCellular(x, z) / 2D) + 0.5D;
	}

	@Override
	public double noise(double x, double y, double z)
	{
		return (n.GetCellular(x, y, z) / 2D) + 0.5D;
	}
}
