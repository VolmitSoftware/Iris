package art.arcane.iris.generation.runtime;

import art.arcane.iris.world.history.GenerationBoundary;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Answers which pack generated, or will generate, a chunk. A single-pack world always answers the current pack. A
 * versioned world keeps every earlier pack version and routes each chunk to the one that owns it.
 */
public interface GenerationSource {
    static GenerationSource singlePack() {
        return SinglePackGenerationSource.INSTANCE;
    }

    /** True when chunks may belong to an earlier pack version than the one loaded now. */
    boolean keepsPackVersions();

    GenerationScope openGenerationScope(int blockX, int blockZ) throws IOException;

    /** Like {@link #openGenerationScope(int, int)}, but may borrow the runtime the thread is already bound to. */
    GenerationScope openReadScope(int blockX, int blockZ) throws IOException;

    /** @return null when every chunk generates with the current pack and needs no route */
    GenerationRoute openRoute(int chunkX, int chunkZ) throws IOException;

    /** The activation and runtime the calling thread generates for, while it holds a route. */
    Optional<RouteOwnership> currentRouteOwnership();

    boolean predatesActivePack(long activationId);

    /** Lets the current pack regenerate these chunks, whichever pack generated them before. */
    void releaseForRegeneration(List<GenerationBoundary.ChunkCoordinate> chunks) throws IOException;

    void sync() throws IOException;

    SavedBiomes savedBiomes();

    RecordedChunks recordedChunks();

    PackTransitions transitions();

    record RouteOwnership(long activationId, IrisEngine.GenerationRuntimeBinding binding) {
    }
}
