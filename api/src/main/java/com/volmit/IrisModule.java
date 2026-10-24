package com.volmit;

/** A unit of world-generation behavior. The core engine is an ordinary module too.
 *
 * <p>In a pack's module jar this class is the entry point. The jar names it in
 * {@code META-INF/services/com.volmit.IrisModule}, one entry class per line, and it needs a public no-argument
 * constructor. A jar holds one or more modules.
 */
public interface IrisModule {
    ModuleInfo info();

    /** Defines this module's stages and registers its other contributions (geometry generators, mantle layers,
     * settings no stage names, pack resource kinds, validators, lifecycle hooks and services), before settings are
     * decoded or generation starts. Called once per pack load, in any order relative to other modules; stage bodies
     * must not capture per-invocation state.
     */
    void setup(ModuleSetup setup);
}
