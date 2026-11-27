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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Every chunk belongs to the pack loaded now, so nothing is routed, recorded or stored.
 */
final class SinglePackGenerationSource implements GenerationSource {
    static final SinglePackGenerationSource INSTANCE = new SinglePackGenerationSource();
    private static final GenerationScope UNROUTED = new UnroutedScope();

    private SinglePackGenerationSource() {
    }

    @Override
    public boolean keepsPackVersions() {
        return false;
    }

    @Override
    public GenerationScope openGenerationScope(int blockX, int blockZ) {
        return UNROUTED;
    }

    @Override
    public GenerationScope openReadScope(int blockX, int blockZ) {
        return UNROUTED;
    }

    @Override
    public GenerationRoute openRoute(int chunkX, int chunkZ) {
        return null;
    }

    @Override
    public Optional<RouteOwnership> currentRouteOwnership() {
        return Optional.empty();
    }

    @Override
    public boolean predatesActivePack(long activationId) {
        return false;
    }

    @Override
    public boolean ownedByEarlierPack(int chunkX, int chunkZ) {
        return false;
    }

    @Override
    public boolean hasUnrecordedEarlierPackChunkInSquare(int centerChunkX, int centerChunkZ, int radius) {
        return false;
    }

    @Override
    public Optional<ChunkGenerationSemantics> recordedSemantics(int chunkX, int chunkZ) {
        return Optional.empty();
    }

    @Override
    public Optional<GenerationSemanticIndex.Match> findRecorded(
            GenerationSemanticIndex.Query query,
            Predicate<ChunkGenerationSemantics> eligibility
    ) {
        return Optional.empty();
    }

    @Override
    public Optional<BiomeEnvironment> savedBiome(int blockX, int worldY, int blockZ, boolean surface) {
        return Optional.empty();
    }

    @Override
    public Optional<BiomeEnvironment> savedCaveBase(int blockX, int blockZ) {
        return Optional.empty();
    }

    @Override
    public Optional<SubterrainCell> savedSubterrainCell(int blockX, int worldY, int blockZ) {
        return Optional.empty();
    }

    @Override
    public <T> T readSavedSurfaceBiome(int blockX, int blockZ, Function<Optional<BiomeEnvironment>, T> reader) {
        return reader.apply(Optional.empty());
    }

    @Override
    public CompletableFuture<Optional<SavedBiomeRuntime.ReadSession>> readSavedBiomes(int chunkX, int chunkZ) {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public void prepareSavedBiomes(int chunkX, int chunkZ) {
    }

    @Override
    public boolean savedBiomesMayHaveBlockDropRules(String material) {
        return false;
    }

    @Override
    public List<Matter> objectContinuations(TransitionGenerationPlan plan, int chunkX, int chunkZ) {
        return List.of();
    }

    @Override
    public boolean allowsObjectFootprint(
            TransitionGenerationPlan plan, int minimumX, int minimumZ, int maximumX, int maximumZ
    ) {
        return true;
    }

    @Override
    public void recordNaturalTerrain(SavedTerrainChunk terrain, FloatingBiomeOverlay floatingBiomes) {
    }

    @Override
    public void releaseForRegeneration(List<GenerationBoundary.ChunkCoordinate> chunks) {
    }

    @Override
    public void sync() {
    }

    private static final class UnroutedScope implements GenerationScope {
        @Override
        public boolean claimGeneratedSemantics() {
            return false;
        }

        @Override
        public void close() {
        }
    }
}
