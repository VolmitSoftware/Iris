package com.volmit.stage;

/** Runs once per chunk generation invocation. */
public final class ChunkStage<O> extends Stage<O> {
    ChunkStage(String id, Class<O> outputType) {
        super(id, outputType);
    }

    @Override
    public Pipeline pipeline() {
        return Pipeline.CHUNK;
    }

    @Override
    public StageKind kind() {
        return StageKind.INVOCATION;
    }
}
