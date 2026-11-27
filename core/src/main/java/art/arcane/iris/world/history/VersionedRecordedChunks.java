package art.arcane.iris.world.history;

import art.arcane.iris.generation.runtime.RecordedChunks;

import java.util.Optional;
import java.util.function.Predicate;

final class VersionedRecordedChunks implements RecordedChunks {
    private final AttachedRouter router;

    VersionedRecordedChunks(AttachedRouter router) {
        this.router = router;
    }

    @Override
    public boolean ownedByEarlierPack(int chunkX, int chunkZ) {
        GenerationHistoryRuntimeRouter attached = router.attachedOrNull();
        return attached != null && attached.history().isHistoricallyOwned(chunkX, chunkZ);
    }

    @Override
    public boolean hasUnrecordedEarlierPackChunkInSquare(int centerChunkX, int centerChunkZ, int radius) {
        GenerationHistoryRuntimeRouter attached = router.attachedOrNull();
        return attached != null && attached.history().hasHistoricalFallbackInSquare(centerChunkX, centerChunkZ, radius);
    }

    @Override
    public Optional<ChunkGenerationSemantics> semantics(int chunkX, int chunkZ) {
        GenerationHistoryRuntimeRouter attached = router.attachedOrNull();
        if (attached == null) {
            return Optional.empty();
        }
        return attached.history().semantics(chunkX, chunkZ).filter(ChunkGenerationSemantics::sealed);
    }

    @Override
    public Optional<GenerationSemanticIndex.Match> findNearest(
            GenerationSemanticIndex.Query query,
            Predicate<ChunkGenerationSemantics> eligibility
    ) {
        GenerationHistoryRuntimeRouter attached = router.attachedOrNull();
        return attached == null ? Optional.empty() : attached.history().findRecorded(query, eligibility);
    }
}
