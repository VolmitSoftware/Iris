package art.arcane.iris.generation.runtime;

import art.arcane.iris.world.history.ChunkGenerationSemantics;
import art.arcane.iris.world.history.GenerationSemanticIndex;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Answers what earlier generation recorded about each chunk, for locate and recorded lookups.
 */
public interface RecordedChunks {
    boolean ownedByEarlierPack(int chunkX, int chunkZ);

    boolean hasUnrecordedEarlierPackChunkInSquare(int centerChunkX, int centerChunkZ, int radius);

    Optional<ChunkGenerationSemantics> semantics(int chunkX, int chunkZ);

    Optional<GenerationSemanticIndex.Match> findNearest(
            GenerationSemanticIndex.Query query,
            Predicate<ChunkGenerationSemantics> eligibility
    );
}
