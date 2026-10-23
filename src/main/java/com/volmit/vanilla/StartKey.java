package com.volmit.vanilla;

/** Names one platform start for as long as the platform keeps it, across chunks: {@link StructureFit.Start#key()}.
 * Two keys are equal only when they are the same object, and the host hands out the same key for the same start, so a
 * module may keep what it worked out about a start in a cache keyed weakly by it; the key holds nothing of the start,
 * so the cache does not keep the start alive. Only the host creates keys.
 */
public final class StartKey {
    /** For the host: a key for a start it has not named yet. */
    public static StartKey create() {
        return new StartKey();
    }

    private StartKey() {
    }

    @Override
    public String toString() {
        return "StartKey@" + Integer.toHexString(System.identityHashCode(this));
    }
}
