package com.volmit;

import com.volmit.stage.StageGraph;

/** A unit of world-generation behavior. The core engine is an ordinary module too. */
public interface IrisModule {
    ModuleInfo info();

    /** Defines this module's stages, before settings are decoded or generation starts.
     * Called once per pack load; stage bodies must not capture per-invocation state.
     */
    void install(StageGraph graph);

    /** Registers non-stage contributions: geometry generators and mantle layers. */
    default void register(FeatureRegistry registry) {}
}
