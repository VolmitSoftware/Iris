package com.volmit.stage;

/** Independent stage graphs. A stage belongs to exactly one pipeline; edges never cross them. */
public enum Pipeline {
    /** Runs once per object placement attempt, against one buffered placement edit. */
    PLACEMENT,
    /** Runs once per chunk generation invocation, against host-owned local hunks. Its stages write only
     * their own chunk and read the mantle.
     */
    CHUNK,
    /** Runs once per chunk before any chunk near it generates, against the mantle only: work that writes
     * the mantle or reaches beyond its chunk. The host runs each stage as its own pass, in resolved order,
     * around a chunk as far as that stage's reach plus every later stage's, so a chunk's mantle is
     * complete when the chunk pipeline reads it.
     */
    MANTLE
}
