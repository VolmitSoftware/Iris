package com.volmit.stage;

/** Runs once per object block of a placement. Block stages that are adjacent in the resolved
 * order share one loop over the object's blocks; a placement stage between them splits the pass.
 */
public final class BlockStage<O> extends Stage<O> {
    BlockStage(String id, Class<O> accumulatorType) {
        super(id, accumulatorType);
    }

    @Override
    public Pipeline pipeline() {
        return Pipeline.PLACEMENT;
    }

    @Override
    public StageKind kind() {
        return StageKind.BLOCK;
    }
}
