package com.volmit.world;

import com.volmit.resource.ResourceId;
import java.util.List;
import java.util.Objects;

/** A region of the loaded pack, found through {@code PackResources} by the id a {@link BiomeSample} gives.
 * The biome lists name the pack biomes the region generates on land, at sea, on shores and in caves.
 * Rarity is relative to the other regions: higher is rarer.
 */
public record PackRegion(ResourceId id, String name, int rarity, List<ResourceId> landBiomes,
                         List<ResourceId> seaBiomes, List<ResourceId> shoreBiomes, List<ResourceId> caveBiomes) {
    public PackRegion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        if (rarity < 1) throw new IllegalArgumentException("Region rarity must be at least 1: " + rarity);
        landBiomes = List.copyOf(landBiomes);
        seaBiomes = List.copyOf(seaBiomes);
        shoreBiomes = List.copyOf(shoreBiomes);
        caveBiomes = List.copyOf(caveBiomes);
    }
}
