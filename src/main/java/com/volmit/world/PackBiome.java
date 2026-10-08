package com.volmit.world;

import com.volmit.resource.ResourceId;
import java.util.List;
import java.util.Objects;

/** A biome of the loaded pack, found through {@code PackResources} by the ids a {@link BiomeSample} gives.
 * platformBiome is the platform biome the host writes for it. children are the pack biomes that can
 * generate inside it. Rarity is relative to the biome's siblings: higher is rarer.
 */
public record PackBiome(ResourceId id, String name, ResourceId platformBiome, int rarity, List<ResourceId> children) {
    public PackBiome {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(platformBiome, "platformBiome");
        if (rarity < 1) throw new IllegalArgumentException("Biome rarity must be at least 1: " + rarity);
        children = List.copyOf(children);
    }
}
