package com.volmit.object;

/** Requested transform. The host owns rasterization, property orientation and interpolation.
 * Rotation is in degrees about the object's anchor; mirrors precede scale, then X/Y/Z rotation.
 * Unsupported transformations fail rather than silently changing the request.
 */
public record ObjectTransform(double rotationX, double rotationY, double rotationZ,
                              double scaleX, double scaleY, double scaleZ,
                              boolean mirrorX, boolean mirrorY, boolean mirrorZ) {
    public ObjectTransform {
        for (double angle : new double[]{rotationX, rotationY, rotationZ}) {
            if (!Double.isFinite(angle)) throw new IllegalArgumentException("Non-finite rotation");
        }
        for (double scale : new double[]{scaleX, scaleY, scaleZ}) {
            if (!Double.isFinite(scale) || scale <= 0) throw new IllegalArgumentException("Scale must be finite and positive");
        }
    }

    public static ObjectTransform identity() { return new ObjectTransform(0, 0, 0, 1, 1, 1, false, false, false); }
}
