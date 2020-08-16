package com.volmit.iris.noise;

public class WhiteNoise implements NoiseGenerator
{
	private final FastNoise n;

	public WhiteNoise(long seed)
	{
		n = new FastNoise((int) seed);
	}

	@Override
	public double noise(double x)
	{
		return (n.GetWhiteNoise(Float.intBitsToFloat((int) Double.doubleToLongBits(x / 1000d)) % 1000000F, 0) / 2D) + 0.5D;
	}

	@Override
	public double noise(double x, double z)
	{
		return (n.GetWhiteNoise(Float.intBitsToFloat((int) Double.doubleToLongBits(x / 1000d)) % 1000000F, Float.intBitsToFloat((int) Double.doubleToLongBits(z / 1000d)) % 1000000F) / 2D) + 0.5D;
	}

	@Override
	public double noise(double x, double y, double z)
	{
		return (n.GetWhiteNoise(Float.intBitsToFloat((int) Double.doubleToLongBits(x / 1000d)) % 1000000F, Float.intBitsToFloat((int) Double.doubleToLongBits(y / 1000d)) % 1000000F, Float.intBitsToFloat((int) Double.doubleToLongBits(z / 1000d)) % 1000000F) / 2D) + 0.5D;
	}
}
