package com.volmit.noise;

/** Builds noise from styles a pack describes. Noise is the host's: modules describe it with {@link NoiseStyle} in
 * their settings and get samplers from here ({@link com.volmit.resource.PackResources#noise()}), never the host's
 * noise classes. Safe for concurrent calls.
 */
public interface NoiseFactory {
    /** The noise {@code style} describes, seeded with {@code seed}. The same style and seed give the same values
     * in every world of the pack, so derive the seed from the world seed ({@code context.world().seed()}), not from a
     * stage's random stream, when the noise must line up across chunks. The host keeps what it builds for the pack
     * snapshot, so asking again for the same style and seed is cheap.
     *
     * <p>A style with its own seed ({@link NoiseStyle#seed()}) ignores {@code seed}: the host rolls it from that number
     * and the seed of the world the pack generates, so every reader of the same seeded style gets the same noise.
     *
     * <p>A style name the host does not know, an expression the pack does not have, and an image map the pack does not
     * have or cannot use as noise throw {@link IllegalArgumentException}.
     */
    NoiseSampler sampler(NoiseStyle style, long seed);
}
