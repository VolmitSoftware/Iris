package art.arcane.iris.world.history;

import art.arcane.iris.generation.mantle.ObjectContinuationBundle;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter.ObjectContinuation;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class HistoricalObjectContinuations {
    private static final Comparator<ObjectContinuation> PLACEMENT_ORDER = Comparator
            .comparingLong(ObjectContinuation::activationId)
            .thenComparing(continuation -> continuation.fragment().key().kind())
            .thenComparingInt(continuation -> continuation.fragment().key().sourceChunkX())
            .thenComparingInt(continuation -> continuation.fragment().key().sourceChunkZ())
            .thenComparingInt(continuation -> continuation.fragment().key().ordinal());
    private static final long MAXIMUM_BYTES = 32L * 1024L * 1024L;

    private final GenerationHistory history;
    private final RegionReader reader;
    private final Cache<RegionKey, Region> regions = Caffeine.newBuilder()
            .maximumWeight(MAXIMUM_BYTES)
            .weigher((RegionKey key, Region region) -> region.weight())
            .build();

    HistoricalObjectContinuations(GenerationHistory history, RegionReader reader) {
        this.history = history;
        this.reader = reader;
    }

    List<ObjectContinuation> at(TransitionGenerationPlan plan, int chunkX, int chunkZ) {
        if (plan == null || plan.boundary().isHistoricalChunk(chunkX, chunkZ)) {
            return List.of();
        }
        RegionKey key = new RegionKey(plan.activationId(), chunkX >> 5, chunkZ >> 5);
        Region region = regions.get(key, ignored -> load(plan, key));
        return region.chunks().getOrDefault(ChunkGenerationOwnership.packChunk(chunkX, chunkZ), List.of());
    }

    boolean allowsFootprint(TransitionGenerationPlan plan, int minimumX, int minimumZ, int maximumX, int maximumZ) {
        for (int chunkX = minimumX >> 4; chunkX <= maximumX >> 4; chunkX++) {
            for (int chunkZ = minimumZ >> 4; chunkZ <= maximumZ >> 4; chunkZ++) {
                for (ObjectContinuation continuation : at(plan, chunkX, chunkZ)) {
                    ObjectContinuationBundle.Bounds bounds = continuation.fragment().bounds();
                    if (bounds.minimumX() <= maximumX && bounds.maximumX() >= minimumX
                            && bounds.minimumZ() <= maximumZ && bounds.maximumZ() >= minimumZ) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    void clear() {
        regions.invalidateAll();
    }

    private Region load(TransitionGenerationPlan plan, RegionKey key) {
        HashMap<Long, List<ObjectContinuation>> chunks = new HashMap<>();
        long weight = 256L;
        try {
            for (GenerationActivation activation : history.manifest().activations()) {
                if (activation.activationId() >= key.activationId()) {
                    continue;
                }
                Map<Long, ObjectContinuationBundle> stored = reader.read(activation, key.x(), key.z());
                for (Map.Entry<Long, ObjectContinuationBundle> entry : stored.entrySet()) {
                    int chunkX = ChunkGenerationOwnership.chunkX(entry.getKey());
                    int chunkZ = ChunkGenerationOwnership.chunkZ(entry.getKey());
                    if (plan.boundary().isHistoricalChunk(chunkX, chunkZ)) {
                        continue;
                    }
                    for (ObjectContinuationBundle.Fragment fragment : entry.getValue().fragments()) {
                        if (!intersectsSavedPlacement(plan, activation.activationId(), fragment)) {
                            continue;
                        }
                        chunks.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>()).add(new ObjectContinuation(activation.activationId(), fragment));
                        weight += 128L + fragment.encodedSize() + fragment.touchedChunks().size() * 16L;
                    }
                }
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("Unable to read pending objects for transition " + key.activationId(), failure);
        }
        chunks.replaceAll((coordinate, fragments) -> {
            fragments.sort(PLACEMENT_ORDER);
            return List.copyOf(fragments);
        });
        return new Region(Map.copyOf(chunks), (int) Math.min(Integer.MAX_VALUE, weight));
    }

    private boolean intersectsSavedPlacement(TransitionGenerationPlan plan, long activationId,
                                            ObjectContinuationBundle.Fragment fragment) {
        for (ObjectContinuationBundle.ChunkPosition chunk : fragment.touchedChunks()) {
            if (plan.boundary().isHistoricalChunk(chunk.x(), chunk.z())
                    && history.resolveActivation(chunk.x(), chunk.z()).activationId() == activationId) {
                return true;
            }
        }
        return false;
    }

    @FunctionalInterface
    interface RegionReader {
        Map<Long, ObjectContinuationBundle> read(GenerationActivation activation, int regionX, int regionZ) throws IOException;
    }

    private record RegionKey(long activationId, int x, int z) {
    }

    private record Region(Map<Long, List<ObjectContinuation>> chunks, int weight) {
    }
}
