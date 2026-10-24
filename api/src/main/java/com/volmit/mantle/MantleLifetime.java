package com.volmit.mantle;

public enum MantleLifetime {
    /** May be removed only after all generation consumers, including neighbors, finish. */
    GENERATION,
    /** Survives generation cleanup; explicit removal is still permitted. */
    PERSISTENT
}
