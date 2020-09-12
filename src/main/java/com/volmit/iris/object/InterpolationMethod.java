package com.volmit.iris.object;

import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;

@Desc("An interpolation method (or function) is simply a method of smoothing a position based on surrounding points on a grid. Bicubic for example is smoother, but has 4 times the checks than Bilinear for example. Try using BILINEAR_STARCAST_9 for beautiful results.")
public enum InterpolationMethod
{
	@Desc("No interpolation. Nearest Neighbor (bad for terrain, great for performance).")
	@DontObfuscate
	NONE,

	@Desc("Uses 4 nearby points in a square to calculate a 2d slope. Very fast but creates square artifacts. See: https://en.wikipedia.org/wiki/Bilinear_interpolation")
	@DontObfuscate
	BILINEAR,

	@Desc("Starcast is Iris's own creation. It uses raytrace checks to find a horizontal boundary nearby. 3 Checks in a circle. Typically starcast is used with another interpolation method. See BILINEAR_STARCAST_9 For example. Starcast is meant to 'break up' large, abrupt cliffs to make cheap interpolation smoother.")
	@DontObfuscate
	STARCAST_3,

	@Desc("Starcast is Iris's own creation. It uses raytrace checks to find a horizontal boundary nearby. 6 Checks in a circle. Typically starcast is used with another interpolation method. See BILINEAR_STARCAST_9 For example. Starcast is meant to 'break up' large, abrupt cliffs to make cheap interpolation smoother.")
	@DontObfuscate
	STARCAST_6,

	@Desc("Starcast is Iris's own creation. It uses raytrace checks to find a horizontal boundary nearby. 9 Checks in a circle. Typically starcast is used with another interpolation method. See BILINEAR_STARCAST_9 For example. Starcast is meant to 'break up' large, abrupt cliffs to make cheap interpolation smoother.")
	@DontObfuscate
	STARCAST_9,

	@Desc("Starcast is Iris's own creation. It uses raytrace checks to find a horizontal boundary nearby. 12 Checks in a circle. Typically starcast is used with another interpolation method. See BILINEAR_STARCAST_9 For example. Starcast is meant to 'break up' large, abrupt cliffs to make cheap interpolation smoother.")
	@DontObfuscate
	STARCAST_12,

	@Desc("Uses starcast to break up the abrupt sharp cliffs, then smooths the rest out with bilinear.")
	@DontObfuscate
	BILINEAR_STARCAST_3,

	@Desc("Uses starcast to break up the abrupt sharp cliffs, then smooths the rest out with bilinear.")
	@DontObfuscate
	BILINEAR_STARCAST_6,

	@Desc("Uses starcast to break up the abrupt sharp cliffs, then smooths the rest out with bilinear.")
	@DontObfuscate
	BILINEAR_STARCAST_9,

	@Desc("Uses starcast to break up the abrupt sharp cliffs, then smooths the rest out with bilinear.")
	@DontObfuscate
	BILINEAR_STARCAST_12,

	@Desc("Uses starcast to break up the abrupt sharp cliffs, then smooths the rest out with hermite.")
	@DontObfuscate
	HERMITE_STARCAST_3,

	@Desc("Uses starcast to break up the abrupt sharp cliffs, then smooths the rest out with hermite.")
	@DontObfuscate
	HERMITE_STARCAST_6,

	@Desc("Uses starcast to break up the abrupt sharp cliffs, then smooths the rest out with hermite.")
	@DontObfuscate
	HERMITE_STARCAST_9,

	@Desc("Uses starcast to break up the abrupt sharp cliffs, then smooths the rest out with hermite.")
	@DontObfuscate
	HERMITE_STARCAST_12,

	@Desc("Uses bilinear but on a bezier curve. See: https://en.wikipedia.org/wiki/Bezier_curve")
	@DontObfuscate
	BILINEAR_BEZIER,

	@Desc("Uses Bilinear but with parametric curves alpha 2.")
	@DontObfuscate
	BILINEAR_PARAMETRIC_2,

	@Desc("Uses Bilinear but with parametric curves alpha 4.")
	@DontObfuscate
	BILINEAR_PARAMETRIC_4,

	@Desc("Uses Bilinear but with parametric curves alpha 1.5.")
	@DontObfuscate
	BILINEAR_PARAMETRIC_1_5,

	@Desc("Bicubic noise creates 4, 4-point splines for a total of 16 checks. Bcubic can go higher than expected and lower than expected right before a large change in slope.")
	@DontObfuscate
	BICUBIC,

	@Desc("Hermite is similar to bicubic, but faster and it can be tuned a little bit")
	@DontObfuscate
	HERMITE,

	@Desc("Essentially bicubic with zero tension")
	@DontObfuscate
	CATMULL_ROM_SPLINE,

	@Desc("Essentially bicubic with max tension")
	@DontObfuscate
	HERMITE_TENSE,

	@Desc("Hermite is similar to bicubic, this variant reduces the dimple artifacts of bicubic")
	@DontObfuscate
	HERMITE_LOOSE,

	@Desc("Hermite is similar to bicubic, this variant reduces the dimple artifacts of bicubic")
	@DontObfuscate
	HERMITE_LOOSE_HALF_POSITIVE_BIAS,

	@Desc("Hermite is similar to bicubic, this variant reduces the dimple artifacts of bicubic")
	@DontObfuscate
	HERMITE_LOOSE_HALF_NEGATIVE_BIAS,

	@Desc("Hermite is similar to bicubic, this variant reduces the dimple artifacts of bicubic")
	@DontObfuscate
	HERMITE_LOOSE_FULL_POSITIVE_BIAS,

	@Desc("Hermite is similar to bicubic, this variant reduces the dimple artifacts of bicubic")
	@DontObfuscate
	HERMITE_LOOSE_FULL_NEGATIVE_BIAS,

	;
}
