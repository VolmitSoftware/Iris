package art.arcane.iris.world.history;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Historical chunks that regen rebuilt with their activation's own pack. They generate like chunks the transition never
 * froze. Ownership records each one, so the set is rebuilt after a restart.
 */
final class ReclaimedChunks {
    private final Set<Long> chunks = ConcurrentHashMap.newKeySet();

    boolean contains(int chunkX, int chunkZ) {
        return !chunks.isEmpty() && chunks.contains(ChunkGenerationOwnership.packChunk(chunkX, chunkZ));
    }

    boolean add(int chunkX, int chunkZ) {
        return chunks.add(ChunkGenerationOwnership.packChunk(chunkX, chunkZ));
    }
}
