package com.volmit.iris.noise;

public class GlobNoise implements NoiseGenerator {
	private final FastNoise n;

	public GlobNoise(long seed) {
		this.n = new FastNoise((int) seed);
		n.setNoiseType(FastNoise.NoiseType.Cellular);
		n.setCellularReturnType(FastNoise.CellularReturnType.Distance2Div);
		n.setCellularDistanceFunction(FastNoise.CellularDistanceFunction.Natural);
	}
	
	private double f(double n)
	{
		return n+1D;
	}

	@Override
	public double noise(double x) {
		return f(n.GetCellular((float) x, 0));
	}

	@Override
	public double noise(double x, double z) {
		return f(n.GetCellular((float) x, (float) z));
	}

	@Override
	public double noise(double x, double y, double z) {
		return f(n.GetCellular((float) x, (float) y, (float) z));
	}
}
