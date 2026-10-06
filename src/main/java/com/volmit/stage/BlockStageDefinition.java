package com.volmit.stage;

import com.volmit.mantle.MantleKey;

import com.volmit.IrisFeature;
import java.util.function.Supplier;

/** Declares one per-block placement stage. Requiring another block stage orders the two per
 * block within a fused pass. A block stage's accumulator becomes readable only after its pass.
 */
public interface BlockStageDefinition<O> {
    BlockStageDefinition<O> requires(Stage<?>... stages);

    BlockStageDefinition<O> softAfter(StageRef... stages);

    BlockStageDefinition<O> softBefore(StageRef... stages);

    /** Declares how far, in blocks along X and Z, this stage writes blocks or mantle values beyond its
     * footprint: the chunk for a chunk stage, the placed object's X/Z bounds for a placement stage. The
     * host prepares that much around every chunk or placement of the pipeline. Leaving it out means 0.
     */
    BlockStageDefinition<O> reach(int blocks);

    /** Declares the mantle layers this stage writes; each must be registered by some module. */
    BlockStageDefinition<O> writesMantle(MantleKey<?>... layers);

    <S extends IrisFeature> ConditionalBlockStageDefinition<S, O> when(Class<S> settingsType);

    /** Body for a block stage without accumulator. */
    void run(StageAction<BlockStageContext> action);

    /** Body for a block stage whose accumulator, created once per placement, is its output. */
    void accumulate(Supplier<O> initial, BlockCollector<O> collector);
}
