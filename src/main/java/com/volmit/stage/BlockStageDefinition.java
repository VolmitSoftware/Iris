package com.volmit.stage;

import com.volmit.IrisFeature;
import java.util.function.Supplier;

/** Declares one per-block placement stage. Requiring another block stage orders the two per
 * block within a fused pass. A block stage's accumulator becomes readable only after its pass.
 */
public interface BlockStageDefinition<O> {
    BlockStageDefinition<O> requires(Stage<?>... stages);

    BlockStageDefinition<O> softAfter(StageRef... stages);

    BlockStageDefinition<O> softBefore(StageRef... stages);

    <S extends IrisFeature> ConditionalBlockStageDefinition<S, O> when(Class<S> settingsType);

    /** Body for a block stage without accumulator. */
    void run(StageAction<BlockStageContext> action);

    /** Body for a block stage whose accumulator, created once per placement, is its output. */
    void accumulate(Supplier<O> initial, BlockCollector<O> collector);
}
