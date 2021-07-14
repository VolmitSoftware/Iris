package com.volmit.iris.object;

import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;

@Desc("Use 3D Interpolation on scaled objects if they are larger than the origin.")
public enum IrisObjectPlacementScaleInterpolator
{
    @DontObfuscate
    @Desc("Don't interpolate, big cubes")
    NONE,

    @DontObfuscate
    @Desc("Uses linear interpolation in 3 dimensions, generally pretty good, but slow")
    TRILINEAR,

    @DontObfuscate
    @Desc("Uses cubic spline interpolation in 3 dimensions, even better, but extreme slowdowns")
    TRICUBIC,

    @DontObfuscate
    @Desc("Uses hermite spline interpolation in 3 dimensions, even better, but extreme slowdowns")
    TRIHERMITE
}
