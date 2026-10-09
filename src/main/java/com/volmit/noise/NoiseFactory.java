package com.volmit.noise;

/** Builds noise from styles a pack describes. Noise is the host's: modules describe it with {@link NoiseStyle} in
 * their settings and get samplers from here ({@link com.volmit.resource.PackResources#noise()}), never the host's
 * noise classes. Safe for concurrent calls.
 */
public interface NoiseFactory {
    /** The noise {@code style} describes, seeded with {@code seed}. The same style and seed give the same values
     * in every world of the pack, so derive the seed from the world seed ({@code context.worldSeed()}), not from a
     * stage's random stream, when the noise must line up across chunks. The host keeps what it builds for the pack
     * snapshot, so asking again for the same style and seed is cheap.
     *
     * <p>A style name the host does not know samples as {@link NoiseStyle#FLAT}, and an expression the pack does not
     * have falls back to the base style, as packs have always been read. An image map the pack does not have, or cannot
     * use as noise, throws {@link IllegalArgumentException}.
     */
    NoiseSampler sampler(NoiseStyle style, long seed);
}
