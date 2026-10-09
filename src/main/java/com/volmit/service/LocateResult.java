package com.volmit.service;

import com.volmit.world.BlockPosition;
import java.util.Objects;

/** How a search for something in a world ended: found at a position, not there, or given up before covering the whole
 * area asked for.
 */
public sealed interface LocateResult {
    LocateResult NOT_FOUND = new NotFound();
    LocateResult SEARCH_LIMIT_REACHED = new SearchLimitReached();

    static LocateResult found(BlockPosition position) {
        return new Found(position);
    }

    /** Found at {@code position}: world X and Z, and Y the height above the dimension's bottom. */
    record Found(BlockPosition position) implements LocateResult {
        public Found {
            Objects.requireNonNull(position, "position");
        }
    }

    /** The whole area was searched and holds none. */
    record NotFound() implements LocateResult {}

    /** The search stopped before covering the area, such as after checking as many candidates as it allows itself. */
    record SearchLimitReached() implements LocateResult {}
}
