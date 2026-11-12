package com.volmit.placement;

import com.volmit.config.Level;
import com.volmit.resource.ResourceId;

import java.util.Objects;

/**
 * The layout entry whose own settings placed an object or a structure: a biome, a region or the dimension. A placement
 * reaches past the column it was placed from, so the biome or region at one of its blocks can be another than its owner.
 *
 * @param level  {@link Level#BIOME}, {@link Level#REGION} or {@link Level#DIMENSION}
 * @param source the biome, region or dimension, as an id
 */
public record PlacementOwner(Level level, ResourceId source) {
    public PlacementOwner {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(source, "source");
        if (level == Level.PLACEMENT) {
            throw new IllegalArgumentException("A placement is owned by a biome, a region or the dimension, not by a placement");
        }
    }

    public static PlacementOwner biome(ResourceId biome) {
        return new PlacementOwner(Level.BIOME, biome);
    }

    public static PlacementOwner region(ResourceId region) {
        return new PlacementOwner(Level.REGION, region);
    }
}
