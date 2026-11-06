package com.volmit.service;

import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.world.PackWorld;
import java.util.Set;

/** Finds the structures a module places, for the host's structure commands, index and locate. Structures are named by
 * their ids: a pack's own structure by the id {@link PackResources#idOf} gives its key ({@code overworld:villages/plains}),
 * a platform structure by the platform's id ({@code minecraft:ancient_city}). The host asks the providers in order and
 * sends an id to the first that places it in the world: {@link #placed} for the structures the module places itself,
 * {@link #locatableStarts} for the platform structures it starts ({@link VanillaStructures#starts}).
 */
@Service(Providers.FIRST)
public interface StructureLocator {
    /** Every structure of the pack this module knows, placed in a world or not, such as one per structure file. The
     * host lists them in its index of the structures a pack has.
     */
    Set<ResourceId> structures(PackResources pack);

    /** The structures this module places in {@code world} and can find with {@link #locate}: commands offer them and
     * send a search for one of them here. A structure no placement of the world uses is not among them.
     */
    Set<ResourceId> locatable(PackWorld world);

    /** The structures {@link #locate} finds among those this module places in {@code world}, locatable or not: its own
     * structures, the platform structures it starts itself and the platform structures its own stand in for. Commands
     * report them and send a search for one of them here. By default the {@link #locatable} ones.
     */
    default Set<ResourceId> placed(PackWorld world) {
        return locatable(world);
    }

    /**
     * The start of {@code structure} nearest to the column {@code fromX}, {@code fromZ} (world coordinates), searched
     * up to {@code maxRadiusChunks} chunks away from it. The position is the structure's origin: world X and Z, and Y the
     * height above the dimension's bottom of its base. Called with an id from {@link #placed}. May take long; the
     * host calls it off the server's main thread.
     */
    LocateResult locate(PackWorld world, ResourceId structure, int fromX, int fromZ, int maxRadiusChunks);

    /** The platform structures this module starts itself in {@code world} ({@link VanillaStructures#starts}), wherever
     * its own placements put them. The host's locate and structure commands send them to {@link #locateStart}.
     */
    default Set<ResourceId> startedStructures(PackWorld world) {
        return Set.of();
    }

    /** Those of {@link #startedStructures} whose starts {@link #locateStart} can find in {@code world}: a start whose
     * placement can never start in the world's height, for one, cannot be searched for.
     */
    default Set<ResourceId> locatableStarts(PackWorld world) {
        return Set.of();
    }

    /**
     * The start of the platform structure {@code structure} this module makes nearest to the column {@code fromX},
     * {@code fromZ} (world coordinates), searched ring by ring outward through its placements' grids up to {@code maxRings}
     * rings: the nearest start of the first ring that has one. The position is the start's origin: world X and Z, and Y the
     * height above the dimension's bottom of its base. Only chunks {@code accepts} allows count; the host checks the start
     * the platform made there and searches again without a chunk it refuses. An exception {@code accepts} throws ends the
     * search and reaches the caller. Called with a structure from {@link #locatableStarts}, off the server's main thread.
     */
    default LocateResult locateStart(PackWorld world, ResourceId structure, int fromX, int fromZ, int maxRings,
                                     ChunkFilter accepts) {
        return LocateResult.NOT_FOUND;
    }

    /** Which chunks a {@link #locateStart} search may answer with. */
    @FunctionalInterface
    interface ChunkFilter {
        boolean accepts(int chunkX, int chunkZ);
    }
}
