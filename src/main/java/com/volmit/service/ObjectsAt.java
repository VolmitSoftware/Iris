package com.volmit.service;

import com.volmit.world.PackWorld;
import java.util.Objects;
import java.util.Set;

/** Which pack objects a module places, predicted without generating anything. The host's object search and its
 * studio tools ask every provider and use all the answers together.
 */
@Service(Providers.ALL)
public interface ObjectsAt {
    /**
     * The keys of the pack objects this module would place in the chunk at {@code chunkX}, {@code chunkZ} (chunk
     * coordinates, world X and Z divided by 16), in the form packs write them ({@code trees/oak}). It is a prediction:
     * it may name an object whose placement the chunk's generation later rejects. Empty when the module places nothing
     * there.
     */
    Set<String> objectsAt(PackWorld world, int chunkX, int chunkZ);

    /**
     * The biomes and regions of {@code world}'s layout whose own settings make this module place the object
     * {@code objectKey}. The host finds the areas where the object can appear from these: a biome counts for the areas
     * it generates in and, through its children, for the areas of the biomes inside it, so a provider names only the
     * layout entries that place the object themselves. Only biomes and regions the dimension can reach are named.
     */
    PlacedBy placedBy(PackWorld world, String objectKey);

    /** Layout entries that place an object, named by the keys packs write ({@code land/plains}, {@code temperate}). */
    record PlacedBy(Set<String> biomes, Set<String> regions) {
        public static final PlacedBy NOTHING = new PlacedBy(Set.of(), Set.of());

        public PlacedBy {
            biomes = Set.copyOf(Objects.requireNonNull(biomes, "biomes"));
            regions = Set.copyOf(Objects.requireNonNull(regions, "regions"));
        }

        public boolean isEmpty() {
            return biomes.isEmpty() && regions.isEmpty();
        }
    }
}
