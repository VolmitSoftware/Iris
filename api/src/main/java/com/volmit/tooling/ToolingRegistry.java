package com.volmit.tooling;

/**
 * Where a module adds its tools to the studio, given in {@link com.volmit.ModuleSetup#tooling()} and accepted only while
 * {@link com.volmit.IrisModule#setup} runs. A view's id is the module's id or starts with it and {@code /}; a second
 * view with an id already registered, of either kind, fails the pack load, naming both modules.
 */
public interface ToolingRegistry {
    /** Adds a view to the studio: block scenes such as the pack's objects, or block patterns such as its palettes. */
    void registerView(StudioView view);
}
