package com.volmit.iris.generator.noise;

import com.volmit.iris.generator.noise.FastNoiseDouble.FractalType;
import com.volmit.iris.util.RNG;

public class FractalRigidMultiSimplexNoise implements NoiseGenerator, OctaveNoise
{
	private final FastNoiseDouble n;

	public FractalRigidMultiSimplexNoise(long seed)
	{
		this.n = new FastNoiseDouble(new RNG(seed).lmax());
		n.setFractalOctaves(1);
		n.setFractalType(FractalType.RigidMulti);
	}

	public double f(double v)
	{
		return (v / 2D) + 0.5D;
	}

	@Override
	public double noise(double x)
	{
		return f(n.GetSimplexFractal(x, 0d));
	}

	@Override
	public double noise(double x, double z)
	{
		return f(n.GetSimplexFractal(x, z));
	}

	@Override
	public double noise(double x, double y, double z)
	{
		return f(n.GetSimplexFractal(x, y, z));
	}

	@Override
	public void setOctaves(int o)
	{
		n.setFractalOctaves(o);
	}
}
