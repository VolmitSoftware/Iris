package com.volmit.iris.noise;

public class CubicNoise implements NoiseGenerator {
	private final FastNoise n;

	public CubicNoise(long seed) {
		this.n = new FastNoise((int) seed);
	}

	private double f(double n) {
		return (n / 2D) + 0.5D;
	}

	@Override
	public double noise(double x) {
		return f(n.GetCubic((float) x, 0));
	}

	@Override
	public double noise(double x, double z) {
		return f(n.GetCubic((float) x, (float) z));
	}

	@Override
	public double noise(double x, double y, double z) {
		return f(n.GetCubic((float) x, (float) y, (float) z));
	}
}
