package com.volmit.world;

import com.volmit.resource.ResourceId;
import java.util.List;
import java.util.Objects;

/** A region of the loaded pack, found through {@code PackResources} by the id a {@link BiomeSample} gives.
 * The biome lists name the pack biomes the region generates on land, at sea, on shores and in caves, in the order
 * the biome noise walks them.
 */
public record PackRegion(ResourceId id, String name, List<Weighted<ResourceId>> landBiomes,
                         List<Weighted<ResourceId>> seaBiomes, List<Weighted<ResourceId>> shoreBiomes,
                         List<Weighted<ResourceId>> caveBiomes) {
    public PackRegion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        landBiomes = List.copyOf(landBiomes);
        seaBiomes = List.copyOf(seaBiomes);
        shoreBiomes = List.copyOf(shoreBiomes);
        caveBiomes = List.copyOf(caveBiomes);
    }
}
