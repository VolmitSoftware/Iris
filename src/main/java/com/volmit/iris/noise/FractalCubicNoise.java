package com.volmit.iris.noise;

import com.volmit.iris.noise.FastNoise.FractalType;

public class FractalCubicNoise implements NoiseGenerator {
	private final FastNoise n;

	public FractalCubicNoise(long seed) {
		this.n = new FastNoise((int) seed);
		n.setFractalType(FractalType.Billow);
	}

	private double f(double n) {
		return (n / 2D) + 0.5D;
	}

	@Override
	public double noise(double x) {
		return f(n.GetCubicFractal((float) x, 0));
	}

	@Override
	public double noise(double x, double z) {
		return f(n.GetCubicFractal((float) x, (float) z));
	}

	@Override
	public double noise(double x, double y, double z) {
		return f(n.GetCubicFractal((float) x, (float) y, (float) z));
	}
}
