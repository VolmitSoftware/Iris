package com.volmit.stage;

/** Runs once per chunk in the mantle pipeline, before the chunks it reaches generate. */
public final class MantleStage<O> extends Stage<O> {
    MantleStage(String id, Class<O> outputType) {
        super(id, outputType);
    }

    @Override
    public Pipeline pipeline() {
        return Pipeline.MANTLE;
    }

    @Override
    public StageKind kind() {
        return StageKind.INVOCATION;
    }
}
