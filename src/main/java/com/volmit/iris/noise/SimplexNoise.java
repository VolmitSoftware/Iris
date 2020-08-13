package com.volmit.iris.noise;

import com.volmit.iris.util.RNG;

public class SimplexNoise implements NoiseGenerator, OctaveNoise {
	private final FastNoise n;
	private int octaves;

	public SimplexNoise(long seed) {
		this.n = new FastNoise(new RNG(seed).imax());
		octaves = 1;
	}

	public double f(double v) {
		return (v / 2D) + 0.5D;
	}

	@Override
	public double noise(double x) {
		if (octaves <= 1) {
			return f(n.GetNoise((float) x, 0f));
		}

		double f = 1;
		double m = 0;
		double v = 0;

		for (int i = 0; i < octaves; i++) {
			v += n.GetNoise((float) (x * (f == 1 ? f++ : (f *= 2))), 0f) * f;
			m += f;
		}

		return f(v / m);
	}

	@Override
	public double noise(double x, double z) {
		if (octaves <= 1) {
			return f(n.GetNoise((float) x, (float) z));
		}
		double f = 1;
		double m = 0;
		double v = 0;

		for (int i = 0; i < octaves; i++) {
			f = f == 1 ? f + 1 : f * 2;
			v += n.GetNoise((float) (x * f), (float) (z * f)) * f;
			m += f;
		}

		return f(v / m);
	}

	@Override
	public double noise(double x, double y, double z) {
		if (octaves <= 1) {
			return f(n.GetNoise((float) x, (float) y, (float) z));
		}
		double f = 1;
		double m = 0;
		double v = 0;

		for (int i = 0; i < octaves; i++) {
			f = f == 1 ? f + 1 : f * 2;
			v += n.GetNoise((float) (x * f), (float) (y * f), (float) (z * f)) * f;
			m += f;
		}

		return f(v / m);
	}

	@Override
	public void setOctaves(int o) {
		octaves = o;
	}
}
