package com.volmit.iris.util;

@FunctionalInterface
public interface NoiseInjector
{
	public double[] combine(double src, double value);
}
