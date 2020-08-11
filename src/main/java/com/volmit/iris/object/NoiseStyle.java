package com.volmit.iris.object;

import com.volmit.iris.noise.CNG;
import com.volmit.iris.noise.CNGFactory;
import com.volmit.iris.noise.NoiseType;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.RNG;

@Desc("Styles of noise")
@DontObfuscate
public enum NoiseStyle {
	@Desc("White Noise is like static. Useful for block scattering but not terrain.")
	@DontObfuscate
	STATIC(rng -> new CNG(rng, NoiseType.WHITE, 1D, 1)),

	@Desc("White Noise is like static. Useful for block scattering but not terrain. 4 Times finer.")
	@DontObfuscate
	STATIC_FINE(rng -> new CNG(rng, NoiseType.WHITE, 1D, 1).scale(4)),

	@Desc("White Noise is like static. Useful for block scattering but not terrain. 16 Times finer.")
	@DontObfuscate
	STATIC_ULTRA_FINE(rng -> new CNG(rng, NoiseType.WHITE, 1D, 1).scale(16)),

	@Desc("Wispy Perlin-looking simplex noise. The 'iris' style noise.")
	@DontObfuscate
	IRIS(rng -> CNG.signature(rng)),

	@Desc("Basic, Smooth & Fast Simplex noise.")
	@DontObfuscate
	SIMPLEX(rng -> new CNG(rng, 1D, 1)),

	@Desc("Basic, Smooth & Fast Simplex noise. Uses 2 octaves")
	@DontObfuscate
	BIOCTAVE_SIMPLEX(rng -> new CNG(rng, 1D, 2)),

	@Desc("Basic, Smooth & Fast Simplex noise. Uses 3 octaves")
	@DontObfuscate
	TRIOCTAVE_SIMPLEX(rng -> new CNG(rng, 1D, 3)),

	@Desc("Basic, Smooth & Fast Simplex noise. Uses 4 octaves")
	@DontObfuscate
	QUADOCTAVE_SIMPLEX(rng -> new CNG(rng, 1D, 4)),

	@Desc("Basic, Smooth & Fast Simplex noise. Uses 5 octaves")
	@DontObfuscate
	QUINTOCTAVE_SIMPLEX(rng -> new CNG(rng, 1D, 5)),

	@Desc("Basic, Smooth & Fast Simplex noise. Uses 6 octaves")
	@DontObfuscate
	SEXOCTAVE_SIMPLEX(rng -> new CNG(rng, 1D, 6)),

	@Desc("Basic, Smooth & Fast Simplex noise. Uses 7 octaves")
	@DontObfuscate
	SEPTOCTAVE_SIMPLEX(rng -> new CNG(rng, 1D, 7)),

	@Desc("Basic, Smooth & Fast Simplex noise. Uses 8 octaves")
	@DontObfuscate
	OCTOCTAVE_SIMPLEX(rng -> new CNG(rng, 1D, 8)),

	@Desc("Basic, Smooth & Fast Simplex noise. Uses 9 octaves")
	@DontObfuscate
	NONOCTAVE_SIMPLEX(rng -> new CNG(rng, 1D, 9)),

	@Desc("Basic, Smooth & Fast Simplex noise. Uses 10 octaves")
	@DontObfuscate
	VIGOCTAVE_SIMPLEX(rng -> new CNG(rng, 1D, 10)),

	@Desc("Cellular noise creates the same noise level for cells, changes noise level on cell borders.")
	@DontObfuscate
	CELLULAR(rng -> new CNG(rng, NoiseType.CELLULAR, 1D, 1)),

	@Desc("Cellular noise creates the same noise level for cells, changes noise level on cell borders. Cells are distorted using Iris styled wispy noise.")
	@DontObfuscate
	CELLULAR_IRIS(rng -> CNG.signature(rng, NoiseType.CELLULAR)),

	@Desc("Inverse of vascular, height gets to 1.0 as it approaches the center of a cell")
	@DontObfuscate
	PERTERB(rng -> new CNG(rng, NoiseType.CELLULAR_HEIGHT, 1D, 1)),

	@Desc("Inverse of vascular, height gets to 1.0 as it approaches the center of a cell, using the iris style.")
	@DontObfuscate
	PERTERB_IRIS(rng -> CNG.signature(rng, NoiseType.CELLULAR_HEIGHT)),

	@Desc("Vascular noise gets higher as the position nears a cell border.")
	@DontObfuscate
	VASCULAR(rng -> new CNG(rng, NoiseType.VASCULAR, 1D, 1)),

	@Desc("Vascular noise gets higher as the position nears a cell border. Cells are distorted using Iris styled wispy noise.")
	@DontObfuscate
	VASCULAR_IRIS(rng -> CNG.signature(rng, NoiseType.VASCULAR)),

	;
	private CNGFactory f;

	private NoiseStyle(CNGFactory f) {
		this.f = f;
	}

	public CNG create(RNG seed) {
		return f.create(seed);
	}
}
