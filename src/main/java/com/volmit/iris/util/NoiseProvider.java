package com.volmit.iris.util;
@FunctionalInterface
public interface NoiseProvider
{
	public double noise(double x, double z);
}