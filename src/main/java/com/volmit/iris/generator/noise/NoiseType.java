package com.volmit.iris.generator.noise;

public enum NoiseType {
	WHITE(WhiteNoise::new),
	SIMPLEX(SimplexNoise::new),
	PERLIN(seed -> new PerlinNoise(seed).hermite()),
	FRACTAL_BILLOW_SIMPLEX(FractalBillowSimplexNoise::new),
	FRACTAL_BILLOW_PERLIN(FractalBillowPerlinNoise::new),
	FRACTAL_FBM_SIMPLEX(FractalFBMSimplexNoise::new),
	FRACTAL_RIGID_MULTI_SIMPLEX(FractalRigidMultiSimplexNoise::new),
	FLAT(FlatNoise::new),
	CELLULAR(CellularNoise::new),
	GLOB(GlobNoise::new),
	CUBIC(CubicNoise::new),
	FRACTAL_CUBIC(FractalCubicNoise::new),
	CELLULAR_HEIGHT(CellHeightNoise::new),
	VASCULAR(VascularNoise::new);

	private final NoiseFactory f;

	private NoiseType(NoiseFactory f) {
		this.f = f;
	}

	public NoiseGenerator create(long seed) {
		return f.create(seed);
	}
}
