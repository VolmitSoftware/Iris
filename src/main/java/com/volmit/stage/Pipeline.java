package com.volmit.stage;

/** Independent stage graphs. A stage belongs to exactly one pipeline; edges never cross them. */
public enum Pipeline {
    /** Runs once per object placement attempt, against one buffered placement edit. */
    PLACEMENT,
    /** Runs once per chunk generation invocation, against host-owned local hunks. */
    CHUNK
}
