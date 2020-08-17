package com.volmit.iris.noise;

public class WhiteNoise implements NoiseGenerator
{
	private final FastNoise n;

	public WhiteNoise(long seed)
	{
		n = new FastNoise((int) seed);
	}

	private double f(double m)
	{
		return m;
	}

	@Override
	public double noise(double x)
	{
		return (n.DGetWhiteNoise(f(x), 0d) / 2D) + 0.5D;
	}

	@Override
	public double noise(double x, double z)
	{
		return (n.DGetWhiteNoise(f(x), f(z)) / 2D) + 0.5D;
	}

	@Override
	public double noise(double x, double y, double z)
	{
		return (n.DGetWhiteNoise(f(x), f(y), f(z)) / 2D) + 0.5D;
	}
}
