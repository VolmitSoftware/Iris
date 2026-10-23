package com.volmit.stage;

import com.volmit.IrisFeature;
import com.volmit.mantle.MantleKey;

/** Declares one placement stage, run once per placement attempt. {@code requires(...)} is a hard dependency on
 * another module's stage constant: it orders this stage after it, allows reading its output, and deactivates this
 * stage wherever the requirement is inactive. Soft edges only order, and only when the referenced stage is installed
 * in the same pipeline. There is deliberately no hard "before".
 */
public interface PlacementStageDefinition<O> {
    PlacementStageDefinition<O> requires(Stage<?>... stages);

    PlacementStageDefinition<O> softAfter(Stage<?>... stages);

    PlacementStageDefinition<O> softAfter(StageRef... stages);

    PlacementStageDefinition<O> softBefore(Stage<?>... stages);

    PlacementStageDefinition<O> softBefore(StageRef... stages);

    /** Declares how far, in blocks along X and Z, this stage writes blocks or mantle values beyond the placed
     * object's X/Z bounds. The host prepares that much around every placement. Leaving it out means 0.
     */
    PlacementStageDefinition<O> reach(int blocks);

    /** Declares the mantle layers this stage writes; each must be registered by some module. */
    PlacementStageDefinition<O> writesMantle(MantleKey<?>... layers);

    /** Activates the stage only where the placement declares the given settings; the body reads them with
     * {@link StageContext#configured}. See {@link StageGraph} for who may name a settings type.
     */
    PlacementStageDefinition<O> when(Class<? extends IrisFeature> settingsType);

    /** Body for a stage without output. */
    void run(StageAction<PlacementStageContext> action);

    /** Body for a stage with output. A null result fails the attempt, unless the stage rejected it. */
    void produce(StageFunction<PlacementStageContext, O> function);
}
