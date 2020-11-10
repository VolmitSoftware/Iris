package com.volmit.iris.generator.noise;

public class FlatNoise implements NoiseGenerator
{
	public FlatNoise(long seed)
	{

	}

	@Override
	public double noise(double x)
	{
		return 1D;
	}

	@Override
	public double noise(double x, double z)
	{
		return 1D;
	}

	@Override
	public double noise(double x, double y, double z)
	{
		return 1D;
	}
}
