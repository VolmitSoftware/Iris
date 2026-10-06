package com.volmit.stage;

import com.volmit.mantle.MantleKey;

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

    /** Declares how far, in blocks along X and Z, this stage writes blocks or mantle values beyond its
     * footprint: the chunk for a chunk stage, the placed object's X/Z bounds for a placement stage. The
     * host prepares that much around every chunk or placement of the pipeline. Leaving it out means 0.
     */
    StageDefinition<C, O> reach(int blocks);

    /** Declares the mantle layers this stage writes; each must be registered by some module. */
    StageDefinition<C, O> writesMantle(MantleKey<?>... layers);

    /** Activates the stage only where the given feature settings are configured. At least one module
     * naming the type must own its feature id: the id is that module's id or starts with it and '/'.
     * Other modules may name it too, such as moss conditioning on {@code iris:snow}.
     */
    <S extends IrisFeature> ConditionalStageDefinition<C, S, O> when(Class<S> settingsType);

    /** Body for a stage without output. */
    void run(StageAction<C> action);

    /** Body for a stage with output; null results fail the invocation. */
    void produce(StageFunction<C, O> function);
}
