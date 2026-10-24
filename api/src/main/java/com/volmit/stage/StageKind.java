package com.volmit.stage;

/** How often a stage runs within one pipeline invocation. */
public enum StageKind {
    /** Once per placement or chunk. */
    INVOCATION,
    /** Once per object block, inside a fused block pass. Placement pipeline only. */
    BLOCK
}
