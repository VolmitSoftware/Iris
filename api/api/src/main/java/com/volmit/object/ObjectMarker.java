package com.volmit.object;

import com.volmit.resource.ResourceId;
import com.volmit.geometry.BlockPosition;
import java.util.Map;
import java.util.Objects;

/** Immutable, named metadata in object-local coordinates. The registered host/module owns meaning. */
public record ObjectMarker(BlockPosition position, ResourceId type, Map<String, String> attributes) {
    public ObjectMarker {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(type, "type");
        attributes = Map.copyOf(attributes);
    }
}
