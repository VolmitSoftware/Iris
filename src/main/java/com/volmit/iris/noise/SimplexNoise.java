package com.volmit.iris.noise;

public class SimplexNoise implements NoiseGenerator {
	private final OpenSimplex n;

	public SimplexNoise(long seed) {
		this.n = new OpenSimplex(seed);
	}

	@Override
	public double noise(double x) {
		return (n.noise2(x, 0) / 2D) + 0.5D;
	}

	@Override
	public double noise(double x, double z) {
		return (n.noise2(x, z) / 2D) + 0.5D;
	}

	@Override
	public double noise(double x, double y, double z) {
		return (n.noise3_XZBeforeY(x, y, z) / 2D) + 0.5D;
	}
}
