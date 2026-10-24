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

    /** This noise with its coordinates multiplied by {@code factor}: a factor above 1 squeezes the pattern, below 1
     * stretches it. A host folds the factor into the generator's own scale, so the result is the very noise the style
     * would give with that extra scale (same values and float rounding), and builds it without touching this sampler.
     * The default multiplies the coordinates before sampling, which may round differently.
     */
    default NoiseSampler scaled(double factor) {
        NoiseSampler source = this;
        return new NoiseSampler() {
            @Override
            public double sample(double worldX, double worldZ) {
                return source.sample(worldX * factor, worldZ * factor);
            }

            @Override
            public double sample(double worldX, double y, double worldZ) {
                return source.sample(worldX * factor, y * factor, worldZ * factor);
            }

            @Override
            public double signed(double worldX, double worldZ) {
                return source.signed(worldX * factor, worldZ * factor);
            }

            @Override
            public double signed(double worldX, double y, double worldZ) {
                return source.signed(worldX * factor, y * factor, worldZ * factor);
            }
        };
    }
}
