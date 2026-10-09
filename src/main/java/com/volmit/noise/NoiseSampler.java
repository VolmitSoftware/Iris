package com.volmit.noise;

/** Immutable, seeded noise the host built from a {@link NoiseStyle} ({@link NoiseFactory#sampler}). X and Z are
 * world coordinates; Y is the height above the dimension's bottom (0 = bottom). The host's own styles give values
 * from 0 to 1; expressions and image maps give what they compute. Two-dimensional and three-dimensional samples
 * are separate fields, so {@code sample(x, z)} is not {@code sample(x, 0, z)}. Safe for concurrent calls.
 */
public interface NoiseSampler {
    double sample(double worldX, double worldZ);

    double sample(double worldX, double y, double worldZ);
}
