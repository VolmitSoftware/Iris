package com.volmit.placement;

import com.volmit.resource.ResourceId;
import com.volmit.geometry.BlockPosition;
import java.util.Map;
import java.util.Objects;

/** Marker metadata. The position has world X and Z and a Y that is the height above the dimension's bottom
 * (0 = bottom).
 */
public record PlacedMarker(BlockPosition position, ResourceId type, Map<String, String> attributes) {
    public PlacedMarker {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(type, "type");
        attributes = Map.copyOf(attributes);
    }
}
