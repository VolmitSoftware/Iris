package com.volmit.host;

import com.volmit.resource.ResourceId;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.random.RandomGenerator;

/** Derives the random stream of one stage in one invocation from the invocation seed and the
 * stage id alone. A stage's draws therefore never depend on which other modules or stages are
 * installed, their order, or how much they draw.
 *
 * <p>The derivation is fixed so the same seed gives the same world on every machine and JDK:
 * <ol>
 *   <li>{@code idHash} = 64-bit FNV-1a of the UTF-8 bytes of the stage id ({@code namespace:path});</li>
 *   <li>{@code streamSeed} = {@code mix(mix(invocationSeed) ^ idHash)}, where {@code mix} is the
 *       SplitMix64 finalizer (Stafford variant 13);</li>
 *   <li>the stream is {@code new java.util.Random(streamSeed)}.</li>
 * </ol>
 * {@code mix} is a bijection, so one stage gets a different stream for every invocation seed, and
 * within one invocation stages with different id hashes get different streams.
 *
 * <p>{@link java.util.Random} is the only JDK generator whose algorithm the Java SE specification
 * fixes for {@code nextInt()}, {@code nextInt(bound)}, {@code nextLong()}, {@code nextBoolean()},
 * {@code nextFloat()}, {@code nextDouble()}, {@code nextGaussian()} and {@code nextBytes}.
 * {@code SplittableRandom} and the named {@code RandomGeneratorFactory} algorithms promise identical
 * sequences only within one program. The ranged methods {@code Random} inherits from
 * {@link RandomGenerator}, such as {@code nextInt(origin, bound)} or {@code nextDouble(bound)}, and
 * {@code nextExponential()} are not fixed by the specification.
 */
public final class StageRandom {
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final int BYTE_MASK = 0xff;

    private StageRandom() {}

    /** The stream {@code stageId} draws from in the invocation with {@code invocationSeed}. */
    public static RandomGenerator stream(long invocationSeed, ResourceId stageId) {
        return stream(invocationSeed, idHash(stageId));
    }

    static RandomGenerator stream(long invocationSeed, long idHash) {
        return new Random(mix(mix(invocationSeed) ^ idHash));
    }

    /** 64-bit FNV-1a over the UTF-8 bytes of the id. */
    static long idHash(ResourceId stageId) {
        long hash = FNV_OFFSET_BASIS;
        for (byte value : stageId.toString().getBytes(StandardCharsets.UTF_8)) {
            hash ^= value & BYTE_MASK;
            hash *= FNV_PRIME;
        }
        return hash;
    }

    /** SplitMix64's finalizer: a bijection on 64-bit values with full avalanche. */
    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
