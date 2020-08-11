package com.volmit.iris.noise;

public class CellHeightNoise implements NoiseGenerator {
	private final FastNoise n;

	public CellHeightNoise(long seed) {
		this.n = new FastNoise((int) seed);
		n.SetNoiseType(FastNoise.NoiseType.Cellular);
		n.SetCellularReturnType(FastNoise.CellularReturnType.Distance2Sub);
		n.SetCellularDistanceFunction(FastNoise.CellularDistanceFunction.Natural);
	}

	private double filter(double noise) {
		return (noise / 2D) + 0.5D;
	}

	@Override
	public double noise(double x) {
		return filter(n.GetCellular((float) x, 0));
	}

	@Override
	public double noise(double x, double z) {
		return filter(n.GetCellular((float) x, (float) z));
	}

	@Override
	public double noise(double x, double y, double z) {
		return filter(n.GetCellular((float) x, (float) y, (float) z));
	}
}
