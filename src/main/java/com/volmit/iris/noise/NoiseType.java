package com.volmit.iris.noise;

public enum NoiseType {
	WHITE(seed -> new WhiteNoise(seed)),
	SIMPLEX(seed -> new SimplexNoise(seed)),
	CELLULAR(seed -> new CellularNoise(seed)),
	CELLULAR_HEIGHT(seed -> new CellHeightNoise(seed)),
	VASCULAR(seed -> new VascularNoise(seed));

	private NoiseFactory f;

	private NoiseType(NoiseFactory f) {
		this.f = f;
	}
	
	public NoiseGenerator create(long seed)
	{
		return f.create(seed);
	}
}
