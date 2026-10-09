package com.volmit.service;

import com.volmit.world.PackWorld;
import java.util.Set;

/** Which pack objects a module places in a chunk, predicted without generating it. The host's object search and its
 * studio tools ask every provider and use all the keys together.
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
}
