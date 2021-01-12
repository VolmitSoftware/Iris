package com.volmit.iris.object;

import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;

@Desc("Object Place modes are useful for positioning objects just right. The default value is CENTER_HEIGHT.")
public enum ObjectPlaceMode
{
	@Desc("The default place mode. This mode picks a center point (where the center of the object will be) and takes the height. That height is used for the whole object.")
	@DontObfuscate
	CENTER_HEIGHT,

	@Desc("Samples a lot of points where the object will cover (horizontally) and picks the highest height, that height is then used to place the object. This mode is useful for preventing any part of your object from being buried though it will float off of cliffs.")
	@DontObfuscate
	MAX_HEIGHT,

	@Desc("Samples only 4 points where the object will cover (horizontally) and picks the highest height, that height is then used to place the object. This mode is useful for preventing any part of your object from being buried though it will float off of cliffs.\"")
	@DontObfuscate
	FAST_MAX_HEIGHT,

	@Desc("Samples a lot of points where the object will cover (horizontally) and picks the lowest height, that height is then used to place the object. This mode is useful for preventing any part of your object from overhanging a cliff though it gets buried a lot")
	@DontObfuscate
	MIN_HEIGHT,

	@Desc("Samples only 4 points where the object will cover (horizontally) and picks the lowest height, that height is then used to place the object. This mode is useful for preventing any part of your object from overhanging a cliff though it gets buried a lot")
	@DontObfuscate
	FAST_MIN_HEIGHT,

	@Desc("Stilting is MAX_HEIGHT but it repeats the bottom most block of your object until it hits the surface. This is expensive because it has to first sample every height value for each x,z position of your object. Avoid using this unless its structures for performance reasons.")
	@DontObfuscate
	STILT,

	@Desc("Just like stilting but very inaccurate. Useful for stilting a lot of objects without too much care on accuracy (you can use the over-stilt value to force stilts under ground further)")
	@DontObfuscate
	FAST_STILT,

	@Desc("Samples the height of the terrain at every x,z position of your object and pushes it down to the surface. It's pretty much like a melt function over the terrain.")
	@DontObfuscate
	PAINT,

	@Desc("Applies multiple terrain features into the parallax layer before this object places to distort the height, essentially vacuuming the terrain's heightmap closer to the bottom of this object. Uses MAX_HEIGHT to place")
	@DontObfuscate
	VACUUM
}
