package com.volmit.stage;

import com.volmit.IrisFeature;
import com.volmit.mantle.MantleKey;
import java.util.function.Supplier;

/** Declares one per-block placement stage. Requiring another block stage orders the two per block within a fused
 * pass. A block stage's accumulator becomes readable only after its pass. Edges work as on
 * {@link PlacementStageDefinition}.
 */
public interface BlockStageDefinition<O> {
    BlockStageDefinition<O> requires(Stage<?>... stages);

    BlockStageDefinition<O> softAfter(Stage<?>... stages);

    BlockStageDefinition<O> softAfter(StageRef... stages);

    BlockStageDefinition<O> softBefore(Stage<?>... stages);

    BlockStageDefinition<O> softBefore(StageRef... stages);

    /** Declares how far, in blocks along X and Z, this stage writes blocks or mantle values beyond the placed
     * object's X/Z bounds. The host prepares that much around every placement. Leaving it out means 0.
     */
    BlockStageDefinition<O> reach(int blocks);

    /** Declares the mantle layers this stage writes; each must be registered by some module. */
    BlockStageDefinition<O> writesMantle(MantleKey<?>... layers);

    /** Activates the stage only where the placement declares the given settings; the body reads them with
     * {@link StageContext#configured}. See {@link StageGraph} for who may name a settings type.
     */
    BlockStageDefinition<O> when(Class<? extends IrisFeature> settingsType);

    /** Body for a block stage without accumulator. */
    void run(StageAction<BlockStageContext> action);

    /** Body for a block stage whose accumulator, created once per placement, is its output. */
    void accumulate(Supplier<O> initial, BlockCollector<O> collector);
}
