package com.volmit.tooling.world;

/** A host refused to open a tool world for a reason its message tells the user and the pack's author can fix, such as a pack
 * with blocking errors. {@link ToolWorlds#open} completes with it; it is not a fault, so a tool reports the message and logs no
 * stack trace.
 */
public final class ToolWorldDenied extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ToolWorldDenied(String message) {
        super(message);
    }
}
