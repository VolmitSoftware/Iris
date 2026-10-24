package com.volmit.stage;

/** Runs once per placement attempt. */
public final class PlacementStage<O> extends Stage<O> {
    PlacementStage(String id, Class<O> outputType) {
        super(id, outputType);
    }

    @Override
    public Pipeline pipeline() {
        return Pipeline.PLACEMENT;
    }

    @Override
    public StageKind kind() {
        return StageKind.INVOCATION;
    }
}
