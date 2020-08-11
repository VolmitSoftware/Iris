package com.volmit.iris.noise;

@FunctionalInterface
public interface NoiseFactory 
{
	NoiseGenerator create(long seed);
}
