package com.volmit.placement;

import com.volmit.resource.ResourceId;
import com.volmit.world.BlockPosition;
import java.util.Map;
import java.util.Objects;

/** Marker metadata in world coordinates. */
public record PlacedMarker(BlockPosition position, ResourceId type, Map<String, String> attributes) {
    public PlacedMarker {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(type, "type");
        attributes = Map.copyOf(attributes);
    }
}
