package com.volmit.world;

import com.volmit.resource.ResourceId;
import java.util.List;
import java.util.Objects;

/** A biome of the loaded pack, found through {@code PackResources} by the ids a {@link BiomeSample} gives.
 * platformBiome is the platform biome the host writes for it. vanillaBiome is the biome vanilla's own rules see it as,
 * such as which structures may start in it: the platform biome unless the pack says otherwise. children are the pack
 * biomes that can generate inside it. Rarity is relative to the biome's siblings: higher is rarer.
 */
public record PackBiome(ResourceId id, String name, ResourceId platformBiome, ResourceId vanillaBiome, int rarity,
                        List<ResourceId> children) {
    public PackBiome {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(platformBiome, "platformBiome");
        Objects.requireNonNull(vanillaBiome, "vanillaBiome");
        if (rarity < 1) throw new IllegalArgumentException("Biome rarity must be at least 1: " + rarity);
        children = List.copyOf(children);
    }

    /** A biome that vanilla's rules see as its platform biome. */
    public PackBiome(ResourceId id, String name, ResourceId platformBiome, int rarity, List<ResourceId> children) {
        this(id, name, platformBiome, platformBiome, rarity, children);
    }
}
