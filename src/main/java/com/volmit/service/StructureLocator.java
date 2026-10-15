package com.volmit.service;

import com.volmit.resource.PackResources;
import com.volmit.world.PackWorld;
import java.util.Set;

/** Finds the structures a module places, for the host's structure commands and index. Structures are named by their
 * keys, in the form packs write them ({@code castle}, {@code villages/plains}). The host asks the providers in order and
 * sends a key to the first that places it in the world ({@link #placed}).
 */
@Service(Providers.FIRST)
public interface StructureLocator {
    /** Every structure key of the pack this module knows, placed in a world or not, such as one per structure file. The
     * host lists them in its index of the structures a pack has.
     */
    Set<String> structures(PackResources pack);

    /** The keys of the structures this module places in {@code world} and can find with {@link #locate}: commands offer
     * them and send a search for one of them here. A structure no placement of the world uses is not among them.
     */
    Set<String> locatable(PackWorld world);

    /** The keys under which {@link #locate} finds the structures this module places in {@code world}, locatable or not: its
     * own structures, the platform structures it starts itself and the platform structures its own stand in for (namespaced,
     * such as {@code minecraft:ancient_city}). Commands report them and send a search for one of them here. By default the
     * {@link #locatable} ones.
     */
    default Set<String> placed(PackWorld world) {
        return locatable(world);
    }

    /**
     * The start of structure {@code key} nearest to the column {@code fromX}, {@code fromZ} (world coordinates), searched
     * up to {@code maxRadiusChunks} chunks away from it. The position is the structure's origin: world X and Z, and Y the
     * height above the dimension's bottom of its base. Called with a key from {@link #placed}. May take long; the
     * host calls it off the server's main thread.
     */
    LocateResult locate(PackWorld world, String key, int fromX, int fromZ, int maxRadiusChunks);
}
