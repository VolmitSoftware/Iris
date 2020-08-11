package com.volmit.iris.noise;

public class CellularNoise implements NoiseGenerator {
	private final FastNoise n;

	public CellularNoise(long seed) {
		this.n = new FastNoise((int) seed);
		n.SetNoiseType(FastNoise.NoiseType.Cellular);
		n.SetCellularReturnType(FastNoise.CellularReturnType.CellValue);
		n.SetCellularDistanceFunction(FastNoise.CellularDistanceFunction.Natural);
	}

	@Override
	public double noise(double x) {
		return (n.GetCellular((float) x, 0) / 2D) + 0.5D;
	}

	@Override
	public double noise(double x, double z) {
		return (n.GetCellular((float) x, (float) z) / 2D) + 0.5D;
	}

	@Override
	public double noise(double x, double y, double z) {
		return (n.GetCellular((float) x, (float) y, (float) z) / 2D) + 0.5D;
	}
}
