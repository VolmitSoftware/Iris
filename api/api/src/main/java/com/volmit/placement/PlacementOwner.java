package com.volmit.placement;

import com.volmit.config.Level;
import com.volmit.resource.ResourceId;

import java.util.Objects;

/**
 * The biome or region whose own settings placed an object. An object reaches past the column it was placed from, so
 * the biome or region at one of its blocks can be another than the one that placed it.
 *
 * @param level  {@link Level#BIOME} or {@link Level#REGION}
 * @param source the biome or region, as an id
 */
public record PlacementOwner(Level level, ResourceId source) {
    public PlacementOwner {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(source, "source");
        if (level != Level.BIOME && level != Level.REGION) {
            throw new IllegalArgumentException("A placement is owned by a biome or a region, not " + level);
        }
    }

    public static PlacementOwner biome(ResourceId biome) {
        return new PlacementOwner(Level.BIOME, biome);
    }

    public static PlacementOwner region(ResourceId region) {
        return new PlacementOwner(Level.REGION, region);
    }
}
