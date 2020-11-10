package com.volmit.iris.generator.noise;

import com.volmit.iris.generator.noise.FastNoiseDouble.FractalType;
import com.volmit.iris.util.RNG;

public class FractalCubicNoise implements NoiseGenerator
{
	private final FastNoiseDouble n;

	public FractalCubicNoise(long seed)
	{
		this.n = new FastNoiseDouble(new RNG(seed).lmax());
		n.setFractalType(FractalType.Billow);
	}

	private double f(double n)
	{
		return (n / 2D) + 0.5D;
	}

	@Override
	public double noise(double x)
	{
		return f(n.GetCubicFractal(x, 0));
	}

	@Override
	public double noise(double x, double z)
	{
		return f(n.GetCubicFractal(x, z));
	}

	@Override
	public double noise(double x, double y, double z)
	{
		return f(n.GetCubicFractal(x, y, z));
	}
}
