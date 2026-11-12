package com.volmit.placement;

import com.volmit.resource.ResourceId;

import java.util.Objects;
import java.util.Optional;

/**
 * What a module recorded beside a block when it placed an object there ({@link PlacementTerrain#recordPlacement}), and
 * what later placements and services read back ({@link PlacementTerrain#placementAt},
 * {@link com.volmit.world.RunningWorld#placementAt}): which object stands there, which placement of it that was, for a
 * piece of a structure which structure, and which biome, region or dimension placed it.
 *
 * @param object      the pack object, or the name the module gave an object it made itself, as an id
 * @param placementId the number the placing module gave this placement, which tells it apart from other placements of the
 *                    same object; the module picks it, the host keeps it as it is
 * @param structure   the structure the object is a piece of; empty for an object placed on its own
 * @param owner       the biome, region or dimension whose settings placed the object, or placed the structure it is a
 *                    piece of; empty when no layout entry did, such as for a grown sapling
 */
public record PlacementRecord(ResourceId object, int placementId, Optional<ResourceId> structure,
                              Optional<PlacementOwner> owner) {
    public PlacementRecord {
        Objects.requireNonNull(object, "object");
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(owner, "owner");
    }

    /** An object placed on its own. */
    public static PlacementRecord of(ResourceId object, int placementId) {
        return new PlacementRecord(object, placementId, Optional.empty(), Optional.empty());
    }

    /** A piece of {@code structure}. */
    public static PlacementRecord ofPiece(ResourceId object, int placementId, ResourceId structure) {
        return new PlacementRecord(object, placementId, Optional.of(structure), Optional.empty());
    }

    /** This record, placed by {@code owner}. */
    public PlacementRecord ownedBy(PlacementOwner owner) {
        return new PlacementRecord(object, placementId, structure, Optional.of(owner));
    }
}
