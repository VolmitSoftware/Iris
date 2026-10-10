package com.volmit.host;

/** Where a runner reports how long each stage it ran took, such as the host's generation metrics. Called on the
 * invocation's thread, once per stage that ran, in run order; a stage the invocation left inactive is not reported.
 */
@FunctionalInterface
public interface StageTimings {
    /** Reports nothing. */
    StageTimings NONE = (stage, nanos) -> {
    };

    void record(ResolvedStage stage, long nanos);
}
