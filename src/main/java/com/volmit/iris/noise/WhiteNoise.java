package com.volmit.iris.noise;

public class WhiteNoise implements NoiseGenerator {
	private final FastNoise n;

	public WhiteNoise(long seed) {
		n = new FastNoise((int) seed);
	}

	@Override
	public double noise(double x) {
		return (n.GetWhiteNoise((float) x, 0) / 2D) + 0.5D;
	}

	@Override
	public double noise(double x, double z) {
		return (n.GetWhiteNoise((float) x, (float) z) / 2D) + 0.5D;
	}

	@Override
	public double noise(double x, double y, double z) {
		return (n.GetWhiteNoise((float) x, (float) y, (float) z) / 2D) + 0.5D;
	}

}
