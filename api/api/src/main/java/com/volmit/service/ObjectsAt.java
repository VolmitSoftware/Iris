package com.volmit.service;

import com.volmit.placement.PlacementRecord;
import com.volmit.resource.ResourceId;
import com.volmit.world.PackWorld;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Which pack objects a module places, predicted without generating anything. The host's object search and its
 * studio tools ask every provider and use all the answers together.
 */
@Service(Providers.ALL)
public interface ObjectsAt {
    /**
     * The ids of the pack objects this module would place in the chunk at {@code chunkX}, {@code chunkZ} (chunk
     * coordinates, world X and Z divided by 16), as {@link com.volmit.resource.PackResources#idOf} gives them. It is a
     * prediction: it may name an object whose placement the chunk's generation later rejects. Empty when the module
     * places nothing there.
     */
    Set<ResourceId> objectsAt(PackWorld world, int chunkX, int chunkZ);

    /**
     * The biomes and regions of {@code world}'s layout whose own settings make this module place the object
     * {@code object}. The host finds the areas where the object can appear from these: a biome counts for the areas
     * it generates in and, through its children, for the areas of the biomes inside it, so a provider names only the
     * layout entries that place the object themselves. Only biomes and regions the dimension can reach are named.
     */
    PlacedBy placedBy(PackWorld world, ResourceId object);

    /**
     * The placement this module plans to cover the block at {@code worldX}, {@code y}, {@code worldZ}, as
     * {@link com.volmit.placement.PlacementTerrain#recordPlacement} would record it, predicted without generating
     * anything. Y is the height above the dimension's bottom. The host's block inspection asks for it where generation
     * recorded nothing at the block ({@link com.volmit.world.RunningWorld#placementAt}). Like {@link #objectsAt}, it is
     * a prediction. Empty when the module plans nothing there.
     */
    default Optional<PlacementRecord> placementAt(PackWorld world, int worldX, int y, int worldZ) {
        return Optional.empty();
    }

    /** Layout entries that place an object, by their pack ids. */
    record PlacedBy(Set<ResourceId> biomes, Set<ResourceId> regions) {
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
