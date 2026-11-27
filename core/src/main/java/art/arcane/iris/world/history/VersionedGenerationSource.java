package art.arcane.iris.world.history;

import art.arcane.iris.generation.mantle.ObjectContinuationBundle;
import art.arcane.iris.generation.runtime.BiomeEnvironment;
import art.arcane.iris.generation.runtime.GenerationRoute;
import art.arcane.iris.generation.runtime.GenerationScope;
import art.arcane.iris.generation.runtime.GenerationSource;
import art.arcane.iris.generation.subterrain.SubterrainCell;
import art.arcane.volmlib.util.matter.Matter;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Routes each chunk to the generation activation that owns it. Once its router detaches the world can no longer
 * generate, and the source refuses to route instead of falling back to the current pack.
 */
public final class VersionedGenerationSource implements GenerationSource {
    private volatile GenerationHistoryRuntimeRouter router;

    public VersionedGenerationSource(GenerationHistoryRuntimeRouter router) {
        this.router = Objects.requireNonNull(router, "generation-history runtime router");
    }

    public void detach() {
        router = null;
    }

    @Override
    public boolean keepsPackVersions() {
        return true;
    }

    @Override
    public GenerationScope openGenerationScope(int blockX, int blockZ) throws IOException {
        return requireRouter().openCoordinateScope(blockX, blockZ);
    }

    @Override
    public GenerationScope openReadScope(int blockX, int blockZ) throws IOException {
        return requireRouter().openReadScope(blockX, blockZ);
    }

    @Override
    public GenerationRoute openRoute(int chunkX, int chunkZ) throws IOException {
        return requireRouter().openRoute(chunkX, chunkZ);
    }

    @Override
    public Optional<RouteOwnership> currentRouteOwnership() {
        GenerationHistoryRuntimeRouter attached = router;
        if (attached == null) {
            return Optional.empty();
        }
        return attached.currentRuntimeOwnership()
                .map(ownership -> new RouteOwnership(ownership.activationId(), ownership.binding()));
    }

    @Override
    public boolean predatesActivePack(long activationId) {
        GenerationHistoryRuntimeRouter attached = router;
        return attached != null && activationId < attached.history().activeActivation().activationId();
    }

    @Override
    public boolean ownedByEarlierPack(int chunkX, int chunkZ) {
        GenerationHistoryRuntimeRouter attached = router;
        return attached != null && attached.history().isHistoricallyOwned(chunkX, chunkZ);
    }

    @Override
    public boolean hasUnrecordedEarlierPackChunkInSquare(int centerChunkX, int centerChunkZ, int radius) {
        GenerationHistoryRuntimeRouter attached = router;
        return attached != null && attached.history().hasHistoricalFallbackInSquare(centerChunkX, centerChunkZ, radius);
    }

    @Override
    public Optional<ChunkGenerationSemantics> recordedSemantics(int chunkX, int chunkZ) {
        GenerationHistoryRuntimeRouter attached = router;
        if (attached == null) {
            return Optional.empty();
        }
        return attached.history().semantics(chunkX, chunkZ).filter(ChunkGenerationSemantics::sealed);
    }

    @Override
    public Optional<GenerationSemanticIndex.Match> findRecorded(
            GenerationSemanticIndex.Query query,
            Predicate<ChunkGenerationSemantics> eligibility
    ) {
        GenerationHistoryRuntimeRouter attached = router;
        return attached == null ? Optional.empty() : attached.history().findRecorded(query, eligibility);
    }

    @Override
    public Optional<BiomeEnvironment> savedBiome(int blockX, int worldY, int blockZ, boolean surface) {
        GenerationHistoryRuntimeRouter attached = router;
        return attached == null ? Optional.empty() : attached.biomes().resolve(blockX, worldY, blockZ, surface);
    }

    @Override
    public Optional<BiomeEnvironment> savedCaveBase(int blockX, int blockZ) {
        GenerationHistoryRuntimeRouter attached = router;
        return attached == null ? Optional.empty() : attached.biomes().resolveCaveBase(blockX, blockZ);
    }

    @Override
    public Optional<SubterrainCell> savedSubterrainCell(int blockX, int worldY, int blockZ) {
        GenerationHistoryRuntimeRouter attached = router;
        return attached == null ? Optional.empty() : attached.biomes().subterrainCell(blockX, worldY, blockZ);
    }

    @Override
    public <T> T readSavedSurfaceBiome(int blockX, int blockZ, Function<Optional<BiomeEnvironment>, T> reader)
            throws InterruptedException {
        GenerationHistoryRuntimeRouter attached = router;
        return attached == null ? reader.apply(Optional.empty())
                : attached.biomes().readSurfaceBiome(blockX, blockZ, reader);
    }

    @Override
    public CompletableFuture<Optional<SavedBiomeRuntime.ReadSession>> readSavedBiomes(int chunkX, int chunkZ) {
        GenerationHistoryRuntimeRouter attached = router;
        if (attached == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return attached.biomes().readChunkAsync(chunkX, chunkZ).thenApply(Optional::of);
    }

    @Override
    public void prepareSavedBiomes(int chunkX, int chunkZ) {
        GenerationHistoryRuntimeRouter attached = router;
        if (attached != null) {
            attached.biomes().prepareChunk(chunkX, chunkZ);
        }
    }

    @Override
    public boolean savedBiomesMayHaveBlockDropRules(String material) {
        GenerationHistoryRuntimeRouter attached = router;
        return attached != null && attached.biomes().mayHaveBlockDropRules(material);
    }

    @Override
    public List<Matter> objectContinuations(TransitionGenerationPlan plan, int chunkX, int chunkZ) throws IOException {
        GenerationHistoryRuntimeRouter attached = router;
        if (attached == null) {
            return List.of();
        }
        List<GenerationHistoryRuntimeRouter.ObjectContinuation> fragments =
                attached.objectContinuations(plan, chunkX, chunkZ);
        return fragments.isEmpty() ? List.of() : attached.decodeObjectContinuations(fragments);
    }

    @Override
    public boolean allowsObjectFootprint(
            TransitionGenerationPlan plan, int minimumX, int minimumZ, int maximumX, int maximumZ
    ) {
        GenerationHistoryRuntimeRouter attached = router;
        return attached == null || attached.allowsObjectFootprint(plan,
                new ObjectContinuationBundle.Bounds(minimumX, minimumZ, maximumX, maximumZ));
    }

    @Override
    public void recordNaturalTerrain(SavedTerrainChunk terrain, FloatingBiomeOverlay floatingBiomes) {
        GenerationHistoryRuntimeRouter attached = router;
        if (attached == null) {
            return;
        }
        attached.recordNaturalTerrain(terrain);
        attached.recordFloatingBiomes(terrain.chunkX(), terrain.chunkZ(), floatingBiomes);
    }

    @Override
    public void releaseForRegeneration(List<GenerationBoundary.ChunkCoordinate> chunks) throws IOException {
        GenerationHistoryRuntimeRouter attached = router;
        if (attached != null) {
            attached.releaseForRegeneration(chunks);
        }
    }

    @Override
    public void sync() throws IOException {
        GenerationHistoryRuntimeRouter attached = router;
        if (attached != null) {
            attached.history().sync();
        }
    }

    private GenerationHistoryRuntimeRouter requireRouter() {
        GenerationHistoryRuntimeRouter attached = router;
        if (attached == null) {
            throw new IllegalStateException("Iris generation-history runtime router is detached.");
        }
        return attached;
    }
}
