package com.volmit.world;

import com.volmit.resource.ResourceId;
import java.util.Objects;
import java.util.Optional;

/** Pack biome identity, distinct from the platform biome written into the chunk. */
public record BiomeSample(ResourceId region, ResourceId surfaceBiome, Optional<ResourceId> caveBiome) {
    public BiomeSample {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(surfaceBiome, "surfaceBiome");
        Objects.requireNonNull(caveBiome, "caveBiome");
    }
}
