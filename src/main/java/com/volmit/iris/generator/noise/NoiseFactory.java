package com.volmit.iris.generator.noise;

@FunctionalInterface
public interface NoiseFactory 
{
	NoiseGenerator create(long seed);
}
