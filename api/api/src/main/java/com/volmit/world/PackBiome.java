package com.volmit.world;

import com.volmit.resource.ResourceId;
import java.util.List;
import java.util.Objects;

/** A biome of the loaded pack, found through {@code PackResources} by the ids a {@link BiomeSample} gives.
 * platformBiome is the platform biome the host writes for it. vanillaBiome is the biome vanilla's own rules see it as,
 * such as which structures may start in it: the platform biome unless the pack says otherwise. children are the pack
 * biomes that can generate inside it, in the order the child noise walks them; the biome itself is listed among them
 * where it stays itself.
 */
public record PackBiome(ResourceId id, String name, ResourceId platformBiome, ResourceId vanillaBiome,
                        List<Weighted<ResourceId>> children) {
    public PackBiome {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(platformBiome, "platformBiome");
        Objects.requireNonNull(vanillaBiome, "vanillaBiome");
        children = List.copyOf(children);
    }

    /** A biome that vanilla's rules see as its platform biome. */
    public PackBiome(ResourceId id, String name, ResourceId platformBiome, List<Weighted<ResourceId>> children) {
        this(id, name, platformBiome, platformBiome, children);
    }
}
