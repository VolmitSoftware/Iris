package com.volmit.noise;

/** Immutable, seeded noise the host built from a {@link NoiseStyle} ({@link NoiseFactory#sampler}). X and Z are
 * world coordinates; Y is the height above the dimension's bottom (0 = bottom). The host's own styles give values
 * from 0 to 1; expressions and image maps give what they compute. Two-dimensional and three-dimensional samples
 * are separate fields, so {@code sample(x, z)} is not {@code sample(x, 0, z)}. Safe for concurrent calls.
 */
public interface NoiseSampler {
    double sample(double worldX, double worldZ);

    double sample(double worldX, double y, double worldZ);

    /** {@link #sample(double, double)} mapped from 0..1 to -1..1 ({@code sample * 2 - 1}), for noise that is added up
     * and compared around zero. A host may answer a repeated read of the same coordinates from a per-thread memo, so a
     * caller that samples the same field at the same points again and again (a carver testing densities) should read
     * this instead of mapping {@link #sample(double, double)} itself.
     */
    default double signed(double worldX, double worldZ) {
        return sample(worldX, worldZ) * 2D - 1D;
    }

    /** The three-dimensional counterpart of {@link #signed(double, double)}. */
    default double signed(double worldX, double y, double worldZ) {
        return sample(worldX, y, worldZ) * 2D - 1D;
    }
}
