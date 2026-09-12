package art.arcane.iris.generation.locator;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.structure.placement.IrisStructureLocator;

import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.world.history.ChunkGenerationSemantics;
import art.arcane.iris.world.history.GenerationHistory;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.world.history.GenerationSemanticIndex;
import art.arcane.volmlib.util.math.Position2;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

final class HistoryAwareLocator<T> implements Locator<T> {
    private final GenerationSemanticIndex.SemanticKind kind;
    private final String key;
    private final Locator<T> active;

    HistoryAwareLocator(
            GenerationSemanticIndex.SemanticKind kind,
            String key,
            Locator<T> active
    ) {
        this.kind = Objects.requireNonNull(kind, "kind");
        String requiredKey = Objects.requireNonNull(key, "key");
        if (requiredKey.isBlank()) {
            throw new IllegalArgumentException("Locator key cannot be blank.");
        }
        this.key = requiredKey;
        this.active = Objects.requireNonNull(active, "active locator");
    }

    @Override
    public boolean matches(Engine engine, Position2 chunk) {
        Optional<ChunkGenerationSemantics> recorded = GenerationLocatorPolicy.recorded(engine, chunk);
        if (recorded.isPresent()) {
            return contains(recorded.get());
        }
        if (!GenerationLocatorPolicy.allowsProceduralPrediction(engine, chunk)) {
            return false;
        }
        return GenerationLocatorPolicy.evaluateScoped(
                engine,
                chunk,
                () -> active.matches(engine, chunk)
        );
    }

    @Override
    public boolean matchesForSearch(Engine engine, Position2 chunk) {
        if (GenerationLocatorPolicy.recorded(engine, chunk).isPresent()
                || !GenerationLocatorPolicy.allowsProceduralPrediction(engine, chunk)) {
            return false;
        }
        return GenerationLocatorPolicy.evaluateScoped(
                engine,
                chunk,
                () -> active.matches(engine, chunk)
        );
    }

    @Override
    public SearchCandidate nearestRecordedCandidate(Engine engine, Position2 origin, int maxChunkRadius) {
        GenerationHistory history = GenerationLocatorPolicy.history(engine).orElse(null);
        if (history == null) {
            return null;
        }
        GenerationSemanticIndex.Match match = history.findRecorded(
                GenerationSemanticIndex.Query.acrossActivations(
                        kind,
                        key,
                        new ChunkGenerationSemantics.BlockPosition(
                                blockCenter(origin.getX()),
                                0,
                                blockCenter(origin.getZ())
                        ),
                        maxChunkRadius
                )
        ).orElse(null);
        if (match == null) {
            return null;
        }
        ChunkGenerationSemantics.BlockPosition exactPosition = match.exactPosition().orElse(null);
        if (exactPosition != null) {
            return new SearchCandidate(
                    new Position2(match.chunk().chunkX(), match.chunk().chunkZ()),
                    exactPosition.x(),
                    exactPosition.z()
            );
        }
        return SearchCandidate.atChunkCenter(
                new Position2(match.chunk().chunkX(), match.chunk().chunkZ())
        );
    }

    @Override
    public SearchCandidate candidateForMatchedChunk(Engine engine, Position2 chunk) {
        if (kind != GenerationSemanticIndex.SemanticKind.STRUCTURE) {
            return SearchCandidate.atChunkCenter(chunk);
        }
        IrisStructureLocator.LocateResult result = GenerationLocatorPolicy.evaluateValueScoped(
                engine,
                chunk,
                () -> IrisStructureLocator.locateInChunk(engine, key, chunk.getX(), chunk.getZ())
        );
        if (!result.found()) {
            return null;
        }
        return new SearchCandidate(chunk, result.originX(), result.originZ());
    }

    private boolean contains(ChunkGenerationSemantics semantics) {
        return switch (kind) {
            case SURFACE_BIOME -> semantics.surfaceBiomeKeys().contains(key);
            case CAVE_BIOME -> semantics.caveBiomeKeys().contains(key);
            case REGION -> semantics.regionKeys().contains(key);
            case RIVER_PROFILE -> semantics.riverProfileKeys().contains(key);
            case OBJECT -> semantics.objectKeys().contains(key);
            case STRUCTURE -> semantics.structures().stream()
                    .anyMatch(occurrence -> occurrence.key().equals(key));
        };
    }

    private static int blockCenter(int chunkCoordinate) {
        long center = (long) chunkCoordinate * 16L + 8L;
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, center));
    }
}

final class GenerationLocatorPolicy {
    private GenerationLocatorPolicy() {
    }

    static Optional<GenerationHistory> history(Engine engine) {
        if (!(engine instanceof IrisEngine irisEngine)) {
            return Optional.empty();
        }
        return irisEngine.getGenerationHistoryRuntimeRouter()
                .map(GenerationHistoryRuntimeRouter::history);
    }

    static Optional<ChunkGenerationSemantics> recorded(Engine engine, Position2 chunk) {
        GenerationHistory history = history(engine).orElse(null);
        if (history == null) {
            return Optional.empty();
        }
        return history.semantics(chunk.getX(), chunk.getZ())
                .filter(ChunkGenerationSemantics::sealed);
    }

    static boolean allowsActivePrediction(Engine engine, Position2 chunk) {
        if (!engine.getComplex().allowsNewGenerationChunk(chunk.getX(), chunk.getZ())) {
            return false;
        }
        GenerationHistory history = history(engine).orElse(null);
        if (history == null) {
            return true;
        }
        return history.isActiveUnowned(chunk.getX(), chunk.getZ())
                && recorded(engine, chunk).isEmpty();
    }

    static boolean allowsHistoricalFallback(Engine engine, Position2 chunk) {
        GenerationHistory history = history(engine).orElse(null);
        return history != null
                && history.isHistoricallyOwned(chunk.getX(), chunk.getZ())
                && recorded(engine, chunk).isEmpty();
    }

    static boolean allowsProceduralPrediction(Engine engine, Position2 chunk) {
        return allowsHistoricalFallback(engine, chunk) || allowsActivePrediction(engine, chunk);
    }

    static boolean hasHistoricalFallbackInSquare(
            Engine engine,
            Position2 center,
            int radius
    ) {
        if (radius < 0) {
            throw new IllegalArgumentException("Historical fallback radius cannot be negative.");
        }
        GenerationHistory history = history(engine).orElse(null);
        return history != null && history.hasHistoricalFallbackInSquare(center.getX(), center.getZ(), radius);
    }

    static boolean evaluateScoped(
            Engine engine,
            Position2 chunk,
            BooleanSupplier query
    ) {
        BooleanSupplier requiredQuery = Objects.requireNonNull(query, "query");
        if (!(engine instanceof IrisEngine irisEngine)) {
            return requiredQuery.getAsBoolean();
        }
        int blockX = Math.multiplyExact(chunk.getX(), 16);
        int blockZ = Math.multiplyExact(chunk.getZ(), 16);
        try (GenerationHistoryRuntimeRouter.CoordinateScope ignored =
                     irisEngine.openGenerationHistoryCoordinateScope(blockX, blockZ)) {
            return requiredQuery.getAsBoolean();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to scope an Iris locator query at chunk "
                    + chunk.getX() + "," + chunk.getZ() + ".", exception);
        }
    }

    static <T> T evaluateValueScoped(
            Engine engine,
            Position2 chunk,
            Supplier<T> query
    ) {
        Supplier<T> requiredQuery = Objects.requireNonNull(query, "query");
        if (!(engine instanceof IrisEngine irisEngine)) {
            return requiredQuery.get();
        }
        int blockX = Math.multiplyExact(chunk.getX(), 16);
        int blockZ = Math.multiplyExact(chunk.getZ(), 16);
        try (GenerationHistoryRuntimeRouter.CoordinateScope ignored =
                     irisEngine.openGenerationHistoryCoordinateScope(blockX, blockZ)) {
            return requiredQuery.get();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to scope an Iris locator query at chunk "
                    + chunk.getX() + "," + chunk.getZ() + ".", exception);
        }
    }
}
