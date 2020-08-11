package com.volmit.iris.noise;

public class SimplexNoise implements NoiseGenerator, OctaveNoise {
	private final OpenSimplex n;
	private int octaves;

	public SimplexNoise(long seed) {
		this.n = new OpenSimplex(seed);
		octaves = 1;
	}

	@Override
	public double noise(double x) {
		if (octaves <= 1) {
			return (n.noise2_XBeforeY(x, 0) / 2D) + 0.5D;
		}

		double result = 0;
		double amp = 1;
		double freq = 1;
		double max = 0;

		for (int i = 0; i < octaves; i++) {
			result += ((n.noise2_XBeforeY(x * freq, 0) * amp) / 2D) + 0.5D;
			max += amp;
			freq *= 2;
			amp *= 2;
		}

		return result / max;
	}

	@Override
	public double noise(double x, double z) {
		if (octaves <= 1) {
			return (n.noise2(x, z) / 2D) + 0.5D;
		}

		double result = 0;
		double amp = 1;
		double freq = 1;
		double max = 0;

		for (int i = 0; i < octaves; i++) {
			result += ((n.noise2(x * freq, z * freq) * amp) / 2D) + 0.5D;
			max += amp;
			freq *= 2;
			amp *= 2;
		}

		return result / max;
	}

	@Override
	public double noise(double x, double y, double z) {
		if (octaves <= 1) {
			return (n.noise3_XZBeforeY(x, y, z) / 2D) + 0.5D;
		}

		double result = 0;
		double amp = 1;
		double freq = 1;
		double max = 0;

		for (int i = 0; i < octaves; i++) {
			result += ((n.noise3_XZBeforeY(x * freq, y * freq, z * freq) * amp) / 2D) + 0.5D;
			max += amp;
			freq *= 2;
			amp *= 2;
		}

		return result / max;
	}

	@Override
	public void setOctaves(int o) {
		octaves = o;
	}
}
