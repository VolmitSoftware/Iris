package com.volmit.service;

import com.volmit.world.PackWorld;
import java.util.Optional;

/**
 * Which biome a module puts underground. The host places the cave biome of a position itself, from the biome its regions give
 * the column and its terrain; this service tells it where a module overrides that and where a cave biome gives way to the
 * surface's. A pack has one provider, since two modules deciding the biome of the same cave could not both be right.
 */
@Service(Providers.ONE)
public interface CaveBiomes {
    /**
     * The pack biome the module puts at the position whatever the regions say, such as a band of the deep dark. Empty where it
     * overrides nothing.
     *
     * @param y the height above the dimension's bottom
     * @return the biome key as packs write it
     */
    Optional<String> overriddenBiome(PackWorld world, int x, int y, int z);

    /**
     * How many blocks below the surface a position must lie for the pack biome {@code biomeKey} to be the cave biome there;
     * nearer the surface the surface's biome is. 0 for a biome that has no such limit.
     */
    int minDepthBelowSurface(PackWorld world, String biomeKey);
}
