package com.volmit.stage;

import com.volmit.IrisFeature;

/** Declares one invocation stage. requires(...) is a hard dependency on another module's
 * stage constant: it orders this stage after it, allows reading its output, and deactivates
 * this stage wherever the requirement is inactive. Soft edges only order, and only when the
 * referenced stage is installed in the same pipeline. There is deliberately no hard "before".
 */
public interface StageDefinition<C extends StageContext, O> {
    StageDefinition<C, O> requires(Stage<?>... stages);

    StageDefinition<C, O> softAfter(StageRef... stages);

    StageDefinition<C, O> softBefore(StageRef... stages);

    /** Activates the stage only where the given feature settings are configured. */
    <S extends IrisFeature> ConditionalStageDefinition<C, S, O> when(Class<S> settingsType);

    /** Body for a stage without output. */
    void run(StageAction<C> action);

    /** Body for a stage with output; null results fail the invocation. */
    void produce(StageFunction<C, O> function);
}
