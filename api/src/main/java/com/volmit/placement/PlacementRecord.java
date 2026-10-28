package com.volmit.placement;

import com.volmit.resource.ResourceId;

import java.util.Objects;
import java.util.Optional;

/**
 * What a module recorded beside a block when it placed an object there ({@link PlacementTerrain#recordPlacement}), and
 * what later placements and services read back ({@link PlacementTerrain#placementAt},
 * {@link com.volmit.world.RunningWorld#placementAt}): which object stands there, which placement of it that was and, for a
 * piece of a structure, which structure.
 *
 * @param object      the pack object, or the name the module gave an object it made itself, as an id
 * @param placementId the number the placing module gave this placement, which tells it apart from other placements of the
 *                    same object; the module picks it, the host keeps it as it is
 * @param structure   the structure the object is a piece of; empty for an object placed on its own
 */
public record PlacementRecord(ResourceId object, int placementId, Optional<ResourceId> structure) {
    public PlacementRecord {
        Objects.requireNonNull(object, "object");
        Objects.requireNonNull(structure, "structure");
    }

    /** An object placed on its own. */
    public static PlacementRecord of(ResourceId object, int placementId) {
        return new PlacementRecord(object, placementId, Optional.empty());
    }

    /** A piece of {@code structure}. */
    public static PlacementRecord ofPiece(ResourceId object, int placementId, ResourceId structure) {
        return new PlacementRecord(object, placementId, Optional.of(structure));
    }
}
