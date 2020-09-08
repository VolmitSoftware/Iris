package com.volmit.iris.noise;

import com.volmit.iris.util.RNG;

public class WhiteNoise implements NoiseGenerator
{
	private final FastNoiseDouble n;

	public WhiteNoise(long seed)
	{
		n = new FastNoiseDouble(new RNG(seed).lmax());
	}

	private double f(double m)
	{
		return m;
	}

	@Override
	public double noise(double x)
	{
		return (n.GetWhiteNoise(f(x), 0d) / 2D) + 0.5D;
	}

	@Override
	public double noise(double x, double z)
	{
		return (n.GetWhiteNoise(f(x), f(z)) / 2D) + 0.5D;
	}

	@Override
	public double noise(double x, double y, double z)
	{
		return (n.GetWhiteNoise(f(x), f(y), f(z)) / 2D) + 0.5D;
	}
}
