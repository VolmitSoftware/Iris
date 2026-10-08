package com.volmit.world;

/** Immutable, seeded noise resource. X and Z are world coordinates; Y is the
 * height above the dimension's bottom (0 = bottom).
 * Range and scale belong to the resource; no implicit normalization is performed.
 */
@FunctionalInterface
public interface NoiseSampler {
    double sample(double worldX, double y, double worldZ);
}
