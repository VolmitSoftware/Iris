package com.volmit.stage;

/** Runs once per chunk generation invocation and writes only its own chunk. It may read the mantle but
 * not write it, and it declares no reach: such work belongs in a {@link MantleStage}.
 */
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
