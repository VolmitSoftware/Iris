package com.volmit.iris.noise;

import com.volmit.iris.noise.FastNoise.FractalType;
import com.volmit.iris.util.RNG;

public class FractalFBMSimplexNoise implements NoiseGenerator, OctaveNoise {
	private final FastNoise n;

	public FractalFBMSimplexNoise(long seed) {
		this.n = new FastNoise(new RNG(seed).imax());
		n.SetFractalOctaves(1);
		n.SetFractalType(FractalType.FBM);
	}

	public double f(double v) {
		return (v / 2D) + 0.5D;
	}

	@Override
	public double noise(double x) {
		return f(n.GetSimplexFractal((float) x, 0f));
	}

	@Override
	public double noise(double x, double z) {
		return f(n.GetSimplexFractal((float) x, (float) z));
	}

	@Override
	public double noise(double x, double y, double z) {
		return f(n.GetSimplexFractal((float) x, (float) y, (float) z));
	}

	@Override
	public void setOctaves(int o) {
		n.SetFractalOctaves(o);
	}
}
