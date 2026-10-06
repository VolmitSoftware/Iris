package com.volmit.host;

import com.volmit.random.WorldRandom;
import com.volmit.resource.ResourceId;
import java.nio.charset.StandardCharsets;

/** Derives the random stream of one stage in one invocation from the invocation seed and the
 * stage id alone. A stage's draws therefore never depend on which other modules or stages are
 * installed, their order, or how much they draw.
 *
 * <p>The derivation is fixed so the same seed gives the same world on every machine and JDK:
 * <ol>
 *   <li>{@code idHash} = 64-bit FNV-1a of the UTF-8 bytes of the stage id ({@code namespace:path});</li>
 *   <li>{@code streamSeed} = {@code mix(mix(invocationSeed) ^ idHash)}, where {@code mix} is the
 *       SplitMix64 finalizer (Stafford variant 13);</li>
 *   <li>the stream is {@code new WorldRandom(streamSeed)}, whose every method IrisApi defines.</li>
 * </ol>
 * {@code mix} is a bijection, so one stage gets a different stream for every invocation seed, and
 * within one invocation stages with different id hashes get different streams.
 */
public final class StageStreams {
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final int BYTE_MASK = 0xff;

    private StageStreams() {}

    /** The stream {@code stageId} draws from in the invocation with {@code invocationSeed}. */
    public static WorldRandom stream(long invocationSeed, ResourceId stageId) {
        return stream(invocationSeed, idHash(stageId));
    }

    static WorldRandom stream(long invocationSeed, long idHash) {
        return new WorldRandom(streamSeed(invocationSeed, idHash));
    }

    static long streamSeed(long invocationSeed, long idHash) {
        return mix(mix(invocationSeed) ^ idHash);
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
