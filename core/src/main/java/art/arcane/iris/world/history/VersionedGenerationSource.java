package art.arcane.iris.world.history;

import art.arcane.iris.generation.runtime.GenerationRoute;
import art.arcane.iris.generation.runtime.GenerationScope;
import art.arcane.iris.generation.runtime.GenerationSource;
import art.arcane.iris.generation.runtime.PackTransitions;
import art.arcane.iris.generation.runtime.RecordedChunks;
import art.arcane.iris.generation.runtime.SavedBiomes;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Routes each chunk to the generation activation that owns it. Once its router detaches the world can no longer
 * generate, and the source refuses to route instead of falling back to the current pack.
 */
public final class VersionedGenerationSource implements GenerationSource {
    private final AttachedRouter router;
    private final SavedBiomes savedBiomes;
    private final RecordedChunks recordedChunks;
    private final PackTransitions transitions;

    public VersionedGenerationSource(GenerationHistoryRuntimeRouter router) {
        this.router = new AttachedRouter(router);
        this.savedBiomes = new VersionedSavedBiomes(this.router);
        this.recordedChunks = new VersionedRecordedChunks(this.router);
        this.transitions = new VersionedPackTransitions(this.router);
    }

    public void detach() {
        router.detach();
    }

    @Override
    public boolean keepsPackVersions() {
        return true;
    }

    @Override
    public GenerationScope openGenerationScope(int blockX, int blockZ) throws IOException {
        return router.require().openCoordinateScope(blockX, blockZ);
    }

    @Override
    public GenerationScope openReadScope(int blockX, int blockZ) throws IOException {
        return router.require().openReadScope(blockX, blockZ);
    }

    @Override
    public GenerationRoute openRoute(int chunkX, int chunkZ) throws IOException {
        return router.require().openRoute(chunkX, chunkZ);
    }

    @Override
    public Optional<RouteOwnership> currentRouteOwnership() {
        GenerationHistoryRuntimeRouter attached = router.attachedOrNull();
        if (attached == null) {
            return Optional.empty();
        }
        return attached.currentRuntimeOwnership()
                .map(ownership -> new RouteOwnership(ownership.activationId(), ownership.binding()));
    }

    @Override
    public boolean predatesActivePack(long activationId) {
        GenerationHistoryRuntimeRouter attached = router.attachedOrNull();
        return attached != null && activationId < attached.history().activeActivation().activationId();
    }

    @Override
    public void releaseForRegeneration(List<GenerationBoundary.ChunkCoordinate> chunks) throws IOException {
        GenerationHistoryRuntimeRouter attached = router.attachedOrNull();
        if (attached != null) {
            attached.releaseForRegeneration(chunks);
        }
    }

    @Override
    public void sync() throws IOException {
        GenerationHistoryRuntimeRouter attached = router.attachedOrNull();
        if (attached != null) {
            attached.history().sync();
        }
    }

    @Override
    public SavedBiomes savedBiomes() {
        return savedBiomes;
    }

    @Override
    public RecordedChunks recordedChunks() {
        return recordedChunks;
    }

    @Override
    public PackTransitions transitions() {
        return transitions;
    }
}
