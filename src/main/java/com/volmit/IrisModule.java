package com.volmit;

import com.volmit.stage.StageGraph;

/** A unit of world-generation behavior. The core engine is an ordinary module too.
 *
 * <p>In a pack's module jar this class is the entry point. The jar names it in
 * {@code META-INF/services/com.volmit.IrisModule}, and it needs a public no-argument constructor.
 * One jar holds exactly one module.
 */
public interface IrisModule {
    ModuleInfo info();

    /** Defines this module's stages, before settings are decoded or generation starts.
     * Called once per pack load; stage bodies must not capture per-invocation state.
     */
    void install(StageGraph graph);

    /** Registers non-stage contributions: geometry generators, mantle layers, settings no stage names, pack
     * resource kinds, validators, lifecycle hooks and services.
     */
    default void register(FeatureRegistry registry) {}
}
