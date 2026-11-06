package com.volmit.stage;

/** Runs once per chunk in the mantle pipeline, before the chunks it reaches generate. Every mantle stage is
 * its own pass: the host runs it for every chunk around a generating chunk before the next stage starts,
 * so a stage sees what earlier stages wrote into neighboring chunks. Mantle stages therefore have no
 * output; they pass data on through mantle layers.
 */
public final class MantleStage extends Stage<Void> {
    MantleStage(String id) {
        super(id, Void.class);
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
