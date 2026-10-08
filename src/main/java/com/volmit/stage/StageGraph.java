package com.volmit.stage;

/** Collects the stages a module defines during {@code IrisModule.install}. Each define call must
 * end with exactly one body (run, produce or accumulate). The graph is resolved once at pack load:
 * missing requirements, cycles, duplicate definitions, stage ids outside the module's id and
 * cross-pipeline edges are load errors. Bodies are shared by concurrent invocations and must not capture
 * invocation state; everything per-invocation arrives through the context and outputs.
 */
public interface StageGraph {
    <O> StageDefinition<PlacementStageContext, O> define(PlacementStage<O> stage);

    <O> BlockStageDefinition<O> define(BlockStage<O> stage);

    <O> StageDefinition<ChunkStageContext, O> define(ChunkStage<O> stage);

    <O> StageDefinition<MantleStageContext, O> define(MantleStage<O> stage);
}
