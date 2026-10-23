package com.volmit.stage;

/** Collects the stages a module defines during {@code IrisModule.setup}. Each define call must end with exactly one
 * body (run, produce or accumulate). Each pipeline has its own definition type, so what a pipeline does not allow,
 * such as a chunk stage writing the mantle, does not compile. The graph is resolved once at pack load: missing
 * requirements, cycles, duplicate definitions, stage ids outside the module's id and cross-pipeline edges are load
 * errors. Bodies are shared by concurrent invocations and must not capture invocation state; everything
 * per-invocation arrives through the context and outputs.
 *
 * <p>{@code when(settingsType)} activates a stage only where the settings are configured, and the body reads them
 * with {@link StageContext#configured}. A stage has at most one condition. At least one module naming the type must
 * own its feature id: the id is that module's id or starts with it and '/'. Other modules may name it too, such as
 * moss conditioning on {@code iris:snow}, and any module may name a type its owner registered with
 * {@code FeatureRegistry.registerSettings}.
 */
public interface StageGraph {
    <O> PlacementStageDefinition<O> define(PlacementStage<O> stage);

    <O> BlockStageDefinition<O> define(BlockStage<O> stage);

    <O> ChunkStageDefinition<O> define(ChunkStage<O> stage);

    MantleStageDefinition define(MantleStage stage);
}
