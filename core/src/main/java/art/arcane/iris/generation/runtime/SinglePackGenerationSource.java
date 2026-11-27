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
    private static final SavedBiomes NO_SAVED_BIOMES = new NoSavedBiomes();
    private static final RecordedChunks NO_RECORDED_CHUNKS = new NoRecordedChunks();
    private static final PackTransitions NO_TRANSITIONS = new NoTransitions();

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
    public void releaseForRegeneration(List<GenerationBoundary.ChunkCoordinate> chunks) {
    }

    @Override
    public void sync() {
    }

    @Override
    public SavedBiomes savedBiomes() {
        return NO_SAVED_BIOMES;
    }

    @Override
    public RecordedChunks recordedChunks() {
        return NO_RECORDED_CHUNKS;
    }

    @Override
    public PackTransitions transitions() {
        return NO_TRANSITIONS;
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

    private static final class NoSavedBiomes implements SavedBiomes {
        @Override
        public Optional<BiomeEnvironment> at(int blockX, int worldY, int blockZ, boolean surface) {
            return Optional.empty();
        }

        @Override
        public Optional<BiomeEnvironment> caveBaseAt(int blockX, int blockZ) {
            return Optional.empty();
        }

        @Override
        public Optional<SubterrainCell> subterrainCellAt(int blockX, int worldY, int blockZ) {
            return Optional.empty();
        }

        @Override
        public <T> T readSurface(int blockX, int blockZ, Function<Optional<BiomeEnvironment>, T> reader) {
            return reader.apply(Optional.empty());
        }

        @Override
        public CompletableFuture<Optional<SavedBiomeRuntime.ReadSession>> readChunk(int chunkX, int chunkZ) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public void prepareChunk(int chunkX, int chunkZ) {
        }

        @Override
        public boolean mayHaveBlockDropRules(String material) {
            return false;
        }
    }

    private static final class NoRecordedChunks implements RecordedChunks {
        @Override
        public boolean ownedByEarlierPack(int chunkX, int chunkZ) {
            return false;
        }

        @Override
        public boolean hasUnrecordedEarlierPackChunkInSquare(int centerChunkX, int centerChunkZ, int radius) {
            return false;
        }

        @Override
        public Optional<ChunkGenerationSemantics> semantics(int chunkX, int chunkZ) {
            return Optional.empty();
        }

        @Override
        public Optional<GenerationSemanticIndex.Match> findNearest(
                GenerationSemanticIndex.Query query,
                Predicate<ChunkGenerationSemantics> eligibility
        ) {
            return Optional.empty();
        }
    }

    private static final class NoTransitions implements PackTransitions {
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
    }
}
