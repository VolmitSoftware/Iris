package com.volmit.iris.generator.noise;

import com.volmit.iris.util.RNG;

public class CubicNoise implements NoiseGenerator
{
	private final FastNoiseDouble n;

	public CubicNoise(long seed)
	{
		this.n = new FastNoiseDouble(new RNG(seed).lmax());
	}

	private double f(double n)
	{
		return (n / 2D) + 0.5D;
	}

	@Override
	public double noise(double x)
	{
		return f(n.GetCubic(x, 0));
	}

	@Override
	public double noise(double x, double z)
	{
		return f(n.GetCubic(x, z));
	}

	@Override
	public double noise(double x, double y, double z)
	{
		return f(n.GetCubic(x, y, z));
	}
}
