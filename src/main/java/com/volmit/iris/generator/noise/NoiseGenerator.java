package com.volmit.iris.generator.noise;

public interface NoiseGenerator 
{
	public double noise(double x);
	
	public double noise(double x, double z);
	
	public double noise(double x, double y, double z);

	public default boolean isStatic()
	{
		return false;
	}
}
