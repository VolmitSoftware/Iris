package com.volmit.tooling;

/**
 * Where a module adds its tools, given in {@link com.volmit.ModuleSetup#tooling()} and accepted only while
 * {@link com.volmit.IrisModule#setup} runs. A view's id is the module's id or starts with it and {@code /}; a second
 * view with an id already registered, of either kind, fails the pack load, naming both modules. A host without tooling,
 * such as a build check, accepts the registrations and ignores them.
 */
public interface ToolingRegistry {
    /** Adds a view to the studio: block scenes such as the pack's objects, or block patterns such as its palettes. */
    void registerView(StudioView view);

    /**
     * Mounts {@code command} under the host's command root, such as {@code /iris structure}. The command's name, and each
     * of its aliases, is a root name; one that another module of the pack took already fails the pack load, naming
     * both modules.
     */
    void registerCommand(Command command);
}
