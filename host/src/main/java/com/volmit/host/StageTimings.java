package com.volmit.host;

/** Where a runner reports how long each stage it ran took, such as the host's generation metrics. Every runner takes
 * one ({@link PlacementRunner#place}, {@link ChunkRunner#generate}, {@link MantleRunner#generate}). Called on the
 * invocation's thread, once per stage that ran, in run order; a block stage is reported once at the end of its pass
 * with its time summed over the blocks it ran for. A stage the invocation left inactive, or one that threw, is not
 * reported.
 */
@FunctionalInterface
public interface StageTimings {
    /** Reports nothing. */
    StageTimings NONE = (stage, nanos) -> {
    };

    void record(ResolvedStage stage, long nanos);
}
