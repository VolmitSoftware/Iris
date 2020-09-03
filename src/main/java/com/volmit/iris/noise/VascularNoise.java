package com.volmit.iris.noise;

import com.volmit.iris.util.M;

public class VascularNoise implements NoiseGenerator {
	private final FastNoise n;

	public VascularNoise(long seed) {
		this.n = new FastNoise((int) seed);
		n.setNoiseType(FastNoise.NoiseType.Cellular);
		n.setCellularReturnType(FastNoise.CellularReturnType.Distance2Sub);
		n.setCellularDistanceFunction(FastNoise.CellularDistanceFunction.Natural);
	}

	private double filter(double noise) {
		return M.clip((noise / 2D) + 0.5D, 0D, 1D);
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
