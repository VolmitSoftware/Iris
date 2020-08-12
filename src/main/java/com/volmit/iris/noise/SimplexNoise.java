package com.volmit.iris.noise;

import com.volmit.iris.util.RNG;

public class SimplexNoise implements NoiseGenerator, OctaveNoise {
	private final SNG n;
	private int octaves;

	public SimplexNoise(long seed) {
		this.n = new SNG(new RNG(seed));
		octaves = 1;
	}

	public double f(double v) {
		return (v / 2D) + 0.5D;
	}

	@Override
	public double noise(double x) {
		if (octaves <= 1) {
			return f(n.noise(x));
		}

		return f(n.noise(x, octaves, 1D, 1D, false));
	}

	@Override
	public double noise(double x, double z) {
		if (octaves <= 1) {
			return f(n.noise(x, z));
		}
		return f(n.noise(x, z, octaves, 1D, 1D, true));
	}

	@Override
	public double noise(double x, double y, double z) {
		if (octaves <= 1) {
			return f(n.noise(x, y, z));
		}
		return f(n.noise(x, y, z, octaves, 1D, 1D, true));
	}

	@Override
	public void setOctaves(int o) {
		octaves = o;
	}
}
