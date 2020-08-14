package com.volmit.iris.noise;

public class FlatNoise implements NoiseGenerator {
	public FlatNoise(long seed) {
		
	}
	
	@Override
	public double noise(double x) {
		return 0.5;
	}

	@Override
	public double noise(double x, double z) {
		return 0.5;
	}

	@Override
	public double noise(double x, double y, double z) {
		return 0.5;
	}
}
