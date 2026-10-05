package com.volmit.world;

/** Immutable, seeded noise resource. Coordinates are world-space sample coordinates.
 * Range and scale belong to the resource; no implicit normalization is performed.
 */
@FunctionalInterface
public interface NoiseSampler {
    double sample(double worldX, double worldY, double worldZ);
}
