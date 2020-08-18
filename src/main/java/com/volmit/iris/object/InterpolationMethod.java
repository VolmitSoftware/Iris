package com.volmit.iris.object;

import com.volmit.iris.util.DontObfuscate;

public enum InterpolationMethod
{
	@DontObfuscate
	NONE,

	@DontObfuscate
	BILINEAR,

	@DontObfuscate
	BILINEAR_BEZIER,

	@DontObfuscate
	BILINEAR_PARAMETRIC_2,

	@DontObfuscate
	BILINEAR_PARAMETRIC_4,

	@DontObfuscate
	BILINEAR_PARAMETRIC_1_5,

	@DontObfuscate
	BICUBIC,

	@DontObfuscate
	HERMITE,

	@DontObfuscate
	CATMULL_ROM_SPLINE,

	@DontObfuscate
	HERMITE_TENSE,

	@DontObfuscate
	HERMITE_LOOSE,

	@DontObfuscate
	HERMITE_LOOSE_HALF_POSITIVE_BIAS,
	
	@DontObfuscate
	HERMITE_LOOSE_HALF_NEGATIVE_BIAS,
	
	@DontObfuscate
	HERMITE_LOOSE_FULL_POSITIVE_BIAS,
	
	@DontObfuscate
	HERMITE_LOOSE_FULL_NEGATIVE_BIAS,

	;
}
