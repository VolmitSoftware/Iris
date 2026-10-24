package com.volmit.placement;

import com.volmit.geometry.BlockBox;
import com.volmit.geometry.BlockPosition;
import com.volmit.object.GeneratedObject;
import java.util.Objects;

/** One placement attempt. Geometry is already transformed; anchor is a world block position (world X and Z, Y as the height
 * above the dimension's bottom).
 * World position = local position - object anchor + request anchor. Arithmetic never wraps.
 */
public record PlacementRequest(GeneratedObject object, BlockPosition anchor) {
    public PlacementRequest {
        Objects.requireNonNull(object, "object");
        Objects.requireNonNull(anchor, "anchor");
    }

    public BlockPosition worldPosition(BlockPosition local) {
        return new BlockPosition(translate(local.x(), object.anchor().x(), anchor.x()),
                translate(local.y(), object.anchor().y(), anchor.y()),
                translate(local.z(), object.anchor().z(), anchor.z()));
    }

    public BlockBox footprint() {
        var bounds = object.bounds();
        var min = worldPosition(new BlockPosition(bounds.minX(), bounds.minY(), bounds.minZ()));
        var max = worldPosition(new BlockPosition(bounds.maxX(), bounds.maxY(), bounds.maxZ()));
        return new BlockBox(min.x(), min.y(), min.z(), max.x(), max.y(), max.z());
    }

    private static int translate(int local, int objectAnchor, int worldAnchor) {
        return Math.toIntExact((long) local - objectAnchor + worldAnchor);
    }
}
