package com.volmit.host;

import com.volmit.resource.ResourceId;
import java.util.Objects;

/** A resolved ordering edge: from runs before to. */
public record StageEdge(ResourceId from, ResourceId to, Kind kind) {
    public enum Kind {
        /** Declared with requires(...): orders, exposes output and cascades deactivation. */
        REQUIRED,
        /** Declared with softAfter/softBefore against an installed stage: ordering only. */
        SOFT;

        Kind strongest(Kind other) {
            return this == REQUIRED || other == REQUIRED ? REQUIRED : SOFT;
        }
    }

    public StageEdge {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(kind, "kind");
    }
}
