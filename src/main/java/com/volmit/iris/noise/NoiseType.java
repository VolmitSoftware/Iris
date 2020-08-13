package com.volmit.iris.noise;

public enum NoiseType {
	WHITE(seed -> new WhiteNoise(seed)), 
	SIMPLEX(seed -> new SimplexNoise(seed)),
	FRACTAL_BILLOW_SIMPLEX(seed -> new FractalBillowSimplexNoise(seed)),
	FRACTAL_FBM_SIMPLEX(seed -> new FractalFBMSimplexNoise(seed)),
	FRACTAL_RIGID_MULTI_SIMPLEX(seed -> new FractalRigidMultiSimplexNoise(seed)),
	CELLULAR(seed -> new CellularNoise(seed)), 
	GLOB(seed -> new GlobNoise(seed)), 
	CUBIC(seed -> new CubicNoise(seed)), 
	CELLULAR_HEIGHT(seed -> new CellHeightNoise(seed)),
	VASCULAR(seed -> new VascularNoise(seed));

	private NoiseFactory f;

	private NoiseType(NoiseFactory f) {
		this.f = f;
	}

	public NoiseGenerator create(long seed) {
		return f.create(seed);
	}
}
