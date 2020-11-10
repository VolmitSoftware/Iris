package com.volmit.iris.object;

import com.volmit.iris.scaffold.stream.ProceduralStream;
import com.volmit.iris.generator.noise.CNG;
import com.volmit.iris.generator.noise.CNGFactory;
import com.volmit.iris.generator.noise.NoiseType;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.RNG;

@Desc("Styles of noise")
@DontObfuscate
public enum NoiseStyle
{
	@Desc("White Noise is like static. Useful for block scattering but not terrain.")
	@DontObfuscate
	STATIC(rng -> new CNG(rng, NoiseType.WHITE, 1D, 1)),

	@Desc("Wispy Perlin-looking simplex noise. The 'iris' style noise.")
	@DontObfuscate
	IRIS(rng -> CNG.signature(rng).scale(1)),

	@Desc("Wispy Perlin-looking simplex noise. The 'iris' style noise.")
	@DontObfuscate
	IRIS_DOUBLE(rng -> CNG.signatureDouble(rng).scale(1)),

	@Desc("Wispy Perlin-looking simplex noise. The 'iris' style noise.")
	@DontObfuscate
	IRIS_THICK(rng -> CNG.signatureThick(rng).scale(1)),

	@Desc("Wispy Perlin-looking simplex noise. The 'iris' style noise.")
	@DontObfuscate
	IRIS_HALF(rng -> CNG.signatureHalf(rng).scale(1)),

	@Desc("Basic, Smooth & Fast Simplex noise.")
	@DontObfuscate
	SIMPLEX(rng -> new CNG(rng, 1D, 1).scale(1)),

	@Desc("Very Detailed smoke using simplex fractured with fractal billow simplex at high octaves.")
	@DontObfuscate
	FRACTAL_SMOKE(rng -> new CNG(rng, 1D, 1).fractureWith(new CNG(rng.nextParallelRNG(1), NoiseType.FRACTAL_BILLOW_SIMPLEX, 1D, 8).scale(0.2), 1000).scale(0.34)),

	@Desc("Thinner Veins.")
	@DontObfuscate
	VASCULAR_THIN(rng -> new CNG(rng.nextParallelRNG(1), NoiseType.VASCULAR, 1D, 1).scale(1).pow(0.65)),

	@Desc("Cells of simplex noise")
	@DontObfuscate
	SIMPLEX_CELLS(rng -> new CNG(rng.nextParallelRNG(1), NoiseType.SIMPLEX, 1D, 1).scale(1).fractureWith(new CNG(rng.nextParallelRNG(8), NoiseType.CELLULAR, 1D, 1).scale(1), 200)),

	@Desc("Veins of simplex noise")
	@DontObfuscate
	SIMPLEX_VASCULAR(rng -> new CNG(rng.nextParallelRNG(1), NoiseType.SIMPLEX, 1D, 1).scale(1).fractureWith(new CNG(rng.nextParallelRNG(8), NoiseType.VASCULAR, 1D, 1).scale(1), 200)),

	@Desc("Very Detailed fluid using simplex fractured with fractal billow simplex at high octaves.")
	@DontObfuscate
	FRACTAL_WATER(rng -> new CNG(rng, 1D, 1).fractureWith(new CNG(rng.nextParallelRNG(1), NoiseType.FRACTAL_FBM_SIMPLEX, 1D, 9).scale(0.03), 9900).scale(1.14)),

	@Desc("Perlin. Like simplex but more natural")
	@DontObfuscate
	PERLIN(rng -> new CNG(rng, NoiseType.PERLIN, 1D, 1).scale(1.47)),

	@Desc("Perlin. Like simplex but more natural")
	@DontObfuscate
	PERLIN_IRIS(rng -> CNG.signature(rng, NoiseType.PERLIN).scale(1.47)),

	@Desc("Perlin. Like simplex but more natural")
	@DontObfuscate
	PERLIN_IRIS_HALF(rng -> CNG.signatureHalf(rng, NoiseType.PERLIN).scale(1.47)),

	@Desc("Perlin. Like simplex but more natural")
	@DontObfuscate
	PERLIN_IRIS_DOUBLE(rng -> CNG.signatureDouble(rng, NoiseType.PERLIN).scale(1.47)),

	@Desc("Perlin. Like simplex but more natural")
	@DontObfuscate
	PERLIN_IRIS_THICK(rng -> CNG.signatureThick(rng, NoiseType.PERLIN).scale(1.47)),

	@Desc("Billow Fractal Perlin Noise.")
	@DontObfuscate
	FRACTAL_BILLOW_PERLIN(rng -> new CNG(rng, NoiseType.FRACTAL_BILLOW_PERLIN, 1D, 1).scale(1.47)),

	@Desc("Billow Fractal Perlin Noise. 2 Octaves")
	@DontObfuscate
	BIOCTAVE_FRACTAL_BILLOW_PERLIN(rng -> new CNG(rng, NoiseType.FRACTAL_BILLOW_PERLIN, 1D, 2).scale(1.17)),

	@Desc("Billow Fractal Simplex Noise. Single octave.")
	@DontObfuscate
	FRACTAL_BILLOW_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_BILLOW_SIMPLEX, 1D, 1)),

	@Desc("FBM Fractal Simplex Noise. Single octave.")
	@DontObfuscate
	FRACTAL_FBM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_FBM_SIMPLEX, 1D, 1)),

	@Desc("Billow Fractal Iris Noise. Single octave.")
	@DontObfuscate
	FRACTAL_BILLOW_IRIS(rng -> CNG.signature(rng, NoiseType.FRACTAL_BILLOW_SIMPLEX)),

	@Desc("FBM Fractal Iris Noise. Single octave.")
	@DontObfuscate
	FRACTAL_FBM_IRIS(rng -> CNG.signature(rng, NoiseType.FRACTAL_FBM_SIMPLEX)),

	@Desc("Billow Fractal Iris Noise. Single octave.")
	@DontObfuscate
	FRACTAL_BILLOW_IRIS_HALF(rng -> CNG.signatureHalf(rng, NoiseType.FRACTAL_BILLOW_SIMPLEX)),

	@Desc("FBM Fractal Iris Noise. Single octave.")
	@DontObfuscate
	FRACTAL_FBM_IRIS_HALF(rng -> CNG.signatureHalf(rng, NoiseType.FRACTAL_FBM_SIMPLEX)),

	@Desc("Billow Fractal Iris Noise. Single octave.")
	@DontObfuscate
	FRACTAL_BILLOW_IRIS_THICK(rng -> CNG.signatureThick(rng, NoiseType.FRACTAL_BILLOW_SIMPLEX)),

	@Desc("FBM Fractal Iris Noise. Single octave.")
	@DontObfuscate
	FRACTAL_FBM_IRIS_THICK(rng -> CNG.signatureThick(rng, NoiseType.FRACTAL_FBM_SIMPLEX)),

	@Desc("Rigid Multi Fractal Simplex Noise. Single octave.")
	@DontObfuscate
	FRACTAL_RM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX, 1D, 1)),

	@Desc("Billow Fractal Simplex Noise. 2 octaves.")
	@DontObfuscate
	BIOCTAVE_FRACTAL_BILLOW_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_BILLOW_SIMPLEX, 1D, 2)),

	@Desc("FBM Fractal Simplex Noise. 2 octaves.")
	@DontObfuscate
	BIOCTAVE_FRACTAL_FBM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_FBM_SIMPLEX, 1D, 2)),

	@Desc("Rigid Multi Fractal Simplex Noise. 2 octaves.")
	@DontObfuscate
	BIOCTAVE_FRACTAL_RM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX, 1D, 2)),

	@Desc("Rigid Multi Fractal Simplex Noise. 3 octaves.")
	@DontObfuscate
	TRIOCTAVE_FRACTAL_RM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX, 1D, 3)),

	@Desc("Billow Fractal Simplex Noise. 3 octaves.")
	@DontObfuscate
	TRIOCTAVE_FRACTAL_BILLOW_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_BILLOW_SIMPLEX, 1D, 3)),

	@Desc("FBM Fractal Simplex Noise. 3 octaves.")
	@DontObfuscate
	TRIOCTAVE_FRACTAL_FBM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_FBM_SIMPLEX, 1D, 3)),

	@Desc("Rigid Multi Fractal Simplex Noise. 4 octaves.")
	@DontObfuscate
	QUADOCTAVE_FRACTAL_RM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX, 1D, 4)),

	@Desc("Billow Fractal Simplex Noise. 4 octaves.")
	@DontObfuscate
	QUADOCTAVE_FRACTAL_BILLOW_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_BILLOW_SIMPLEX, 1D, 4)),

	@Desc("FBM Fractal Simplex Noise. 4 octaves.")
	@DontObfuscate
	QUADOCTAVE_FRACTAL_FBM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_FBM_SIMPLEX, 1D, 4)),

	@Desc("Rigid Multi Fractal Simplex Noise. 5 octaves.")
	@DontObfuscate
	QUINTOCTAVE_FRACTAL_RM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX, 1D, 5)),

	@Desc("Billow Fractal Simplex Noise. 5 octaves.")
	@DontObfuscate
	QUINTOCTAVE_FRACTAL_BILLOW_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_BILLOW_SIMPLEX, 1D, 5)),

	@Desc("FBM Fractal Simplex Noise. 5 octaves.")
	@DontObfuscate
	QUINTOCTAVE_FRACTAL_FBM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_FBM_SIMPLEX, 1D, 5)),

	@Desc("Rigid Multi Fractal Simplex Noise. 6 octaves.")
	@DontObfuscate
	SEXOCTAVE_FRACTAL_RM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX, 1D, 6)),

	@Desc("Billow Fractal Simplex Noise. 6 octaves.")
	@DontObfuscate
	SEXOCTAVE_FRACTAL_BILLOW_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_BILLOW_SIMPLEX, 1D, 6)),

	@Desc("FBM Fractal Simplex Noise. 6 octaves.")
	@DontObfuscate
	SEXOCTAVE_FRACTAL_FBM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_FBM_SIMPLEX, 1D, 6)),

	@Desc("Rigid Multi Fractal Simplex Noise. 7 octaves.")
	@DontObfuscate
	SEPTOCTAVE_FRACTAL_RM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX, 1D, 7)),

	@Desc("Billow Fractal Simplex Noise. 7 octaves.")
	@DontObfuscate
	SEPTOCTAVE_FRACTAL_BILLOW_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_BILLOW_SIMPLEX, 1D, 7)),

	@Desc("FBM Fractal Simplex Noise. 7 octaves.")
	@DontObfuscate
	SEPTOCTAVE_FRACTAL_FBM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_FBM_SIMPLEX, 1D, 7)),

	@Desc("Rigid Multi Fractal Simplex Noise. 8 octaves.")
	@DontObfuscate
	OCTOCTAVE_FRACTAL_RM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX, 1D, 8)),

	@Desc("Billow Fractal Simplex Noise. 8 octaves.")
	@DontObfuscate
	OCTOCTAVE_FRACTAL_BILLOW_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_BILLOW_SIMPLEX, 1D, 8)),

	@Desc("FBM Fractal Simplex Noise. 8 octaves.")
	@DontObfuscate
	OCTOCTAVE_FRACTAL_FBM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_FBM_SIMPLEX, 1D, 8)),

	@Desc("Rigid Multi Fractal Simplex Noise. 9 octaves.")
	@DontObfuscate
	NONOCTAVE_FRACTAL_RM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX, 1D, 9)),

	@Desc("Billow Fractal Simplex Noise. 9 octaves.")
	@DontObfuscate
	NONOCTAVE_FRACTAL_BILLOW_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_BILLOW_SIMPLEX, 1D, 9)),

	@Desc("FBM Fractal Simplex Noise. 9 octaves.")
	@DontObfuscate
	NONOCTAVE_FRACTAL_FBM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_FBM_SIMPLEX, 1D, 9)),

	@Desc("Rigid Multi Fractal Simplex Noise. 10 octaves.")
	@DontObfuscate
	VIGOCTAVE_FRACTAL_RM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX, 1D, 10)),

	@Desc("Billow Fractal Simplex Noise. 10 octaves.")
	@DontObfuscate
	VIGOCTAVE_FRACTAL_BILLOW_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_BILLOW_SIMPLEX, 1D, 10)),

	@Desc("FBM Fractal Simplex Noise. 10 octaves.")
	@DontObfuscate
	VIGOCTAVE_FRACTAL_FBM_SIMPLEX(rng -> new CNG(rng, NoiseType.FRACTAL_FBM_SIMPLEX, 1D, 10)),

	@Desc("Basic, Smooth & Fast Simplex noise. Uses 2 octaves")
	@DontObfuscate
	BIOCTAVE_SIMPLEX(rng -> new CNG(rng, 1D, 2).scale(1D / 2D)),

	@Desc("Basic, Smooth & Fast Simplex noise. Uses 3 octaves")
	@DontObfuscate
	TRIOCTAVE_SIMPLEX(rng -> new CNG(rng, 1D, 3).scale(1D / 3D)),

	@Desc("Basic, Smooth & Fast Simplex noise. Uses 4 octaves")
	@DontObfuscate
	QUADOCTAVE_SIMPLEX(rng -> new CNG(rng, 1D, 4).scale(1D / 4D)),

	@Desc("Basic, Smooth & Fast Simplex noise. Uses 5 octaves")
	@DontObfuscate
	QUINTOCTAVE_SIMPLEX(rng -> new CNG(rng, 1D, 5).scale(1D / 5D)),

	@Desc("Basic, Smooth & Fast Simplex noise. Uses 6 octaves")
	@DontObfuscate
	SEXOCTAVE_SIMPLEX(rng -> new CNG(rng, 1D, 6).scale(1D / 6D)),

	@Desc("Basic, Smooth & Fast Simplex noise. Uses 7 octaves")
	@DontObfuscate
	SEPTOCTAVE_SIMPLEX(rng -> new CNG(rng, 1D, 7).scale(1D / 12D)),

	@Desc("Basic, Smooth & Fast Simplex noise. Uses 8 octaves")
	@DontObfuscate
	OCTOCTAVE_SIMPLEX(rng -> new CNG(rng, 1D, 8).scale(1D / 25D)),

	@Desc("Basic, Smooth & Fast Simplex noise. Uses 9 octaves")
	@DontObfuscate
	NONOCTAVE_SIMPLEX(rng -> new CNG(rng, 1D, 9).scale(1D / 50D)),

	@Desc("Basic, Smooth & Fast Simplex noise. Uses 10 octaves")
	@DontObfuscate
	VIGOCTAVE_SIMPLEX(rng -> new CNG(rng, 1D, 10).scale(1D / 100D)),

	@Desc("Glob noise is like cellular, but with globs...")
	@DontObfuscate
	GLOB(rng -> new CNG(rng, NoiseType.GLOB, 1D, 1)),

	@Desc("Glob noise is like cellular, but with globs...")
	@DontObfuscate
	GLOB_IRIS(rng -> CNG.signature(rng, NoiseType.GLOB)),

	@Desc("Glob noise is like cellular, but with globs...")
	@DontObfuscate
	GLOB_IRIS_HALF(rng -> CNG.signatureHalf(rng, NoiseType.GLOB)),

	@Desc("Glob noise is like cellular, but with globs...")
	@DontObfuscate
	GLOB_IRIS_DOUBLE(rng -> CNG.signatureDouble(rng, NoiseType.GLOB)),

	@Desc("Glob noise is like cellular, but with globs...")
	@DontObfuscate
	GLOB_IRIS_THICK(rng -> CNG.signatureThick(rng, NoiseType.GLOB)),

	@Desc("Cubic Noise")
	@DontObfuscate
	CUBIC(rng -> new CNG(rng, NoiseType.CUBIC, 1D, 1).scale(256)),

	@Desc("Fractal Cubic Noise")
	@DontObfuscate
	FRACTAL_CUBIC(rng -> new CNG(rng, NoiseType.FRACTAL_CUBIC, 1D, 1).scale(2)),

	@Desc("Fractal Cubic Noise With Iris Swirls")
	@DontObfuscate
	FRACTAL_CUBIC_IRIS(rng -> CNG.signature(rng, NoiseType.FRACTAL_CUBIC).scale(2)),

	@Desc("Fractal Cubic Noise With Iris Swirls")
	@DontObfuscate
	FRACTAL_CUBIC_IRIS_THICK(rng -> CNG.signatureThick(rng, NoiseType.FRACTAL_CUBIC).scale(2)),

	@Desc("Fractal Cubic Noise With Iris Swirls")
	@DontObfuscate
	FRACTAL_CUBIC_IRIS_HALF(rng -> CNG.signatureHalf(rng, NoiseType.FRACTAL_CUBIC).scale(2)),

	@Desc("Fractal Cubic Noise With Iris Swirls")
	@DontObfuscate
	FRACTAL_CUBIC_IRIS_DOUBLE(rng -> CNG.signatureDouble(rng, NoiseType.FRACTAL_CUBIC).scale(2)),

	@Desc("Fractal Cubic Noise, 2 Octaves")
	@DontObfuscate
	BIOCTAVE_FRACTAL_CUBIC(rng -> new CNG(rng, NoiseType.FRACTAL_CUBIC, 1D, 2).scale(2)),

	@Desc("Fractal Cubic Noise, 3 Octaves")
	@DontObfuscate
	TRIOCTAVE_FRACTAL_CUBIC(rng -> new CNG(rng, NoiseType.FRACTAL_CUBIC, 1D, 3).scale(1.5)),

	@Desc("Fractal Cubic Noise, 4 Octaves")
	@DontObfuscate
	QUADOCTAVE_FRACTAL_CUBIC(rng -> new CNG(rng, NoiseType.FRACTAL_CUBIC, 1D, 4).scale(1)),

	@Desc("Cubic Noise")
	@DontObfuscate
	CUBIC_IRIS(rng -> CNG.signature(rng, NoiseType.CUBIC).scale(256)),

	@Desc("Cubic Noise")
	@DontObfuscate
	CUBIC_IRIS_HALF(rng -> CNG.signatureHalf(rng, NoiseType.CUBIC).scale(256)),

	@Desc("Cubic Noise")
	@DontObfuscate
	CUBIC_IRIS_DOUBLE(rng -> CNG.signatureDouble(rng, NoiseType.CUBIC).scale(256)),

	@Desc("Cubic Noise")
	@DontObfuscate
	CUBIC_IRIS_THICK(rng -> CNG.signatureThick(rng, NoiseType.CUBIC).scale(256)),

	@Desc("Cellular noise creates the same noise level for cells, changes noise level on cell borders.")
	@DontObfuscate
	CELLULAR(rng -> new CNG(rng, NoiseType.CELLULAR, 1D, 1)),

	@Desc("Cellular noise creates the same noise level for cells, changes noise level on cell borders. Cells are distorted using Iris styled wispy noise.")
	@DontObfuscate
	CELLULAR_IRIS(rng -> CNG.signature(rng, NoiseType.CELLULAR)),

	@Desc("Cellular noise creates the same noise level for cells, changes noise level on cell borders. Cells are distorted using Iris styled wispy noise.")
	@DontObfuscate
	CELLULAR_IRIS_THICK(rng -> CNG.signatureThick(rng, NoiseType.CELLULAR)),

	@Desc("Cellular noise creates the same noise level for cells, changes noise level on cell borders. Cells are distorted using Iris styled wispy noise.")
	@DontObfuscate
	CELLULAR_IRIS_DOUBLE(rng -> CNG.signatureDouble(rng, NoiseType.CELLULAR)),

	@Desc("Cellular noise creates the same noise level for cells, changes noise level on cell borders. Cells are distorted using Iris styled wispy noise.")
	@DontObfuscate
	CELLULAR_IRIS_HALF(rng -> CNG.signatureHalf(rng, NoiseType.CELLULAR)),

	@Desc("Inverse of vascular, height gets to 1.0 as it approaches the center of a cell")
	@DontObfuscate
	CELLULAR_HEIGHT(rng -> new CNG(rng, NoiseType.CELLULAR_HEIGHT, 1D, 1)),

	@Desc("Inverse of vascular, height gets to 1.0 as it approaches the center of a cell, using the iris style.")
	@DontObfuscate
	CELLULAR_HEIGHT_IRIS(rng -> CNG.signature(rng, NoiseType.CELLULAR_HEIGHT)),

	@Desc("Inverse of vascular, height gets to 1.0 as it approaches the center of a cell, using the iris style.")
	@DontObfuscate
	CELLULAR_HEIGHT_IRIS_DOUBLE(rng -> CNG.signatureDouble(rng, NoiseType.CELLULAR_HEIGHT)),

	@Desc("Inverse of vascular, height gets to 1.0 as it approaches the center of a cell, using the iris style.")
	@DontObfuscate
	CELLULAR_HEIGHT_IRIS_THICK(rng -> CNG.signatureThick(rng, NoiseType.CELLULAR_HEIGHT)),

	@Desc("Inverse of vascular, height gets to 1.0 as it approaches the center of a cell, using the iris style.")
	@DontObfuscate
	CELLULAR_HEIGHT_IRIS_HALF(rng -> CNG.signatureHalf(rng, NoiseType.CELLULAR_HEIGHT)),

	@Desc("Vascular noise gets higher as the position nears a cell border.")
	@DontObfuscate
	VASCULAR(rng -> new CNG(rng, NoiseType.VASCULAR, 1D, 1)),

	@Desc("It always returns 0.5")
	@DontObfuscate
	FLAT(rng -> new CNG(rng, NoiseType.FLAT, 1D, 1)),

	@Desc("Vascular noise gets higher as the position nears a cell border. Cells are distorted using Iris styled wispy noise.")
	@DontObfuscate
	VASCULAR_IRIS(rng -> CNG.signature(rng, NoiseType.VASCULAR)),

	@Desc("Vascular noise gets higher as the position nears a cell border. Cells are distorted using Iris styled wispy noise.")
	@DontObfuscate
	VASCULAR_IRIS_DOUBLE(rng -> CNG.signatureDouble(rng, NoiseType.VASCULAR)),

	@Desc("Vascular noise gets higher as the position nears a cell border. Cells are distorted using Iris styled wispy noise.")
	@DontObfuscate
	VASCULAR_IRIS_THICK(rng -> CNG.signatureThick(rng, NoiseType.VASCULAR)),

	@Desc("Vascular noise gets higher as the position nears a cell border. Cells are distorted using Iris styled wispy noise.")
	@DontObfuscate
	VASCULAR_IRIS_HALF(rng -> CNG.signatureHalf(rng, NoiseType.VASCULAR)),

	;

	private CNGFactory f;

	private NoiseStyle(CNGFactory f)
	{
		this.f = f;
	}

	public ProceduralStream<Double> stream(RNG seed)
	{
		return create(seed).stream();
	}

	public ProceduralStream<Double> stream(long seed)
	{
		return create(new RNG(seed)).stream();
	}

	public CNG create(RNG seed)
	{
		return f.create(seed).bake();
	}

	public IrisGeneratorStyle style()
	{
		return new IrisGeneratorStyle(this);
	}
}
