package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.subterrain.SubterrainCell;
import art.arcane.iris.world.history.ChunkGenerationSemantics;
import art.arcane.iris.world.history.FloatingBiomeOverlay;
import art.arcane.iris.world.history.GenerationBoundary;
import art.arcane.iris.world.history.GenerationSemanticIndex;
import art.arcane.iris.world.history.SavedBiomeRuntime;
import art.arcane.iris.world.history.SavedTerrainChunk;
import art.arcane.iris.world.history.TransitionGenerationPlan;
import art.arcane.volmlib.util.matter.Matter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;

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

    boolean ownedByEarlierPack(int chunkX, int chunkZ);

    boolean hasUnrecordedEarlierPackChunkInSquare(int centerChunkX, int centerChunkZ, int radius);

    Optional<ChunkGenerationSemantics> recordedSemantics(int chunkX, int chunkZ);

    Optional<GenerationSemanticIndex.Match> findRecorded(
            GenerationSemanticIndex.Query query,
            Predicate<ChunkGenerationSemantics> eligibility
    );

    Optional<BiomeEnvironment> savedBiome(int blockX, int worldY, int blockZ, boolean surface);

    Optional<BiomeEnvironment> savedCaveBase(int blockX, int blockZ);

    Optional<SubterrainCell> savedSubterrainCell(int blockX, int worldY, int blockZ);

    <T> T readSavedSurfaceBiome(int blockX, int blockZ, Function<Optional<BiomeEnvironment>, T> reader)
            throws InterruptedException;

    CompletableFuture<Optional<SavedBiomeRuntime.ReadSession>> readSavedBiomes(int chunkX, int chunkZ);

    void prepareSavedBiomes(int chunkX, int chunkZ);

    boolean savedBiomesMayHaveBlockDropRules(String material);

    /** Object fragments that earlier packs placed across the transition band into this chunk. */
    List<Matter> objectContinuations(TransitionGenerationPlan plan, int chunkX, int chunkZ) throws IOException;

    boolean allowsObjectFootprint(TransitionGenerationPlan plan, int minimumX, int minimumZ, int maximumX, int maximumZ);

    void recordNaturalTerrain(SavedTerrainChunk terrain, FloatingBiomeOverlay floatingBiomes);

    /** Lets the current pack regenerate these chunks, whichever pack generated them before. */
    void releaseForRegeneration(List<GenerationBoundary.ChunkCoordinate> chunks) throws IOException;

    void sync() throws IOException;

    record RouteOwnership(long activationId, IrisEngine.GenerationRuntimeBinding binding) {
    }
}
