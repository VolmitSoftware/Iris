package com.volmit.stage;

import com.volmit.IrisFeature;
import com.volmit.mantle.MantleKey;

/** Declares one mantle stage. Mantle stages have no output: each runs as its own pass and passes data on through
 * mantle layers. Edges work as on {@link PlacementStageDefinition}.
 */
public interface MantleStageDefinition {
    MantleStageDefinition requires(Stage<?>... stages);

    MantleStageDefinition softAfter(Stage<?>... stages);

    MantleStageDefinition softAfter(StageRef... stages);

    MantleStageDefinition softBefore(Stage<?>... stages);

    MantleStageDefinition softBefore(StageRef... stages);

    /** Declares how far, in blocks along X and Z, this stage writes mantle values beyond its chunk. The host prepares
     * that much around every chunk the stage runs for. Leaving it out means 0.
     */
    MantleStageDefinition reach(int blocks);

    /** Declares a reach the host works out from the pack's settings when the pack loads, for a stage whose reach
     * depends on the pack, such as the largest object any level configures.
     */
    MantleStageDefinition reach(PackReach reach);

    /** Declares the mantle layers this stage writes; each must be registered by some module. */
    MantleStageDefinition writesMantle(MantleKey<?>... layers);

    /** Activates the stage only where any column of the chunk configures the given settings; the body reads every
     * distinct entry of those columns with {@link StageContext#configured}. See {@link StageGraph} for who may name a
     * settings type.
     */
    MantleStageDefinition when(Class<? extends IrisFeature> settingsType);

    void run(StageAction<MantleStageContext> action);
}
