package art.arcane.iris.world.history;

import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.structure.placement.IrisStructureLocator;
import art.arcane.iris.generation.hydrology.HydrologyFeatureQuery;
import art.arcane.iris.generation.hydrology.HydrologyFeatureRef;
import art.arcane.iris.generation.hydrology.HydrologyFeatureType;
import art.arcane.iris.generation.hydrology.runtime.IrisHydrologyRuntime;

import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

public final class GenerationSemanticQueries {
    private GenerationSemanticQueries() {
    }

    public static IrisStructureLocator.LocateResult nearestStructure(
            Engine engine,
            String key,
            int blockX,
            int blockZ,
            int maximumChunkRadius
    ) {
        Engine requiredEngine = Objects.requireNonNull(engine, "engine");
        String requiredKey = ChunkGenerationSemantics.requireResourceKey(
                Objects.requireNonNull(key, "key").trim());
        int radius = Math.max(0, Math.min(maximumChunkRadius, 2048));
        GenerationHistory history = history(requiredEngine).orElse(null);
        if (history == null) {
            return IrisStructureLocator.locate(requiredEngine, requiredKey, blockX, blockZ, radius);
        }
        IrisStructureLocator.LocateResult active = IrisStructureLocator.locate(
                requiredEngine, requiredKey, blockX, blockZ, radius,
                (chunkX, chunkZ) -> allowsActivePrediction(requiredEngine, chunkX, chunkZ));
        if (active.status() == IrisStructureLocator.LocateStatus.SEARCH_LIMIT_REACHED) {
            return active;
        }
        GenerationSemanticIndex.Match recorded = history.findRecorded(
                GenerationSemanticIndex.Query.acrossActivations(
                        GenerationSemanticIndex.SemanticKind.STRUCTURE,
                        requiredKey,
                        new ChunkGenerationSemantics.BlockPosition(blockX, 0, blockZ),
                        radius
                )
        ).orElse(null);
        if (recorded == null) {
            return active;
        }
        ChunkGenerationSemantics.BlockPosition position = recorded.exactPosition().orElseThrow();
        if (active.found() && horizontalDistanceSquared(blockX, blockZ, active.originX(), active.originZ())
                .compareTo(horizontalDistanceSquared(blockX, blockZ, position.x(), position.z())) < 0) {
            return active;
        }
        return new IrisStructureLocator.LocateResult(
                IrisStructureLocator.LocateStatus.FOUND, position.x(), position.y(), position.z());
    }

    public static Optional<RiverResult> nearestRiver(
            Engine engine,
            HydrologyFeatureQuery query,
            int blockX,
            int blockZ,
            int maximumDistance,
            IntConsumer progress
    ) {
        Engine requiredEngine = Objects.requireNonNull(engine, "engine");
        HydrologyFeatureQuery requiredQuery = Objects.requireNonNull(query, "query");
        IntConsumer requiredProgress = Objects.requireNonNull(progress, "progress");
        if (maximumDistance < 0) {
            throw new IllegalArgumentException("Maximum river search distance cannot be negative.");
        }
        GenerationHistory history = history(requiredEngine).orElse(null);
        RiverResult recorded = recordedRiver(
                history,
                requiredQuery,
                blockX,
                blockZ,
                maximumDistance
        );
        RiverResult active = activeRiver(
                requiredEngine,
                history,
                requiredQuery,
                blockX,
                blockZ,
                maximumDistance,
                requiredProgress
        );
        if (recorded == null) {
            return Optional.ofNullable(active);
        }
        if (active == null) {
            return Optional.of(recorded);
        }
        int comparison = horizontalDistanceSquared(blockX, blockZ, recorded.x(), recorded.z())
                .compareTo(horizontalDistanceSquared(blockX, blockZ, active.x(), active.z()));
        return Optional.of(comparison <= 0 ? recorded : active);
    }

    public static boolean allowsActivePrediction(Engine engine, int chunkX, int chunkZ) {
        Engine requiredEngine = Objects.requireNonNull(engine, "engine");
        if (!requiredEngine.getComplex().allowsNewGenerationChunk(chunkX, chunkZ)) {
            return false;
        }
        GenerationHistory history = history(requiredEngine).orElse(null);
        if (history == null) {
            return true;
        }
        return history.isActiveUnowned(chunkX, chunkZ)
                && history.semantics(chunkX, chunkZ)
                .filter(ChunkGenerationSemantics::sealed)
                .isEmpty();
    }

    private static RiverResult recordedRiver(
            GenerationHistory history,
            HydrologyFeatureQuery query,
            int blockX,
            int blockZ,
            int maximumDistance
    ) {
        if (history == null) {
            return null;
        }
        int maxChunkRadius = Math.addExact(Math.floorDiv(maximumDistance, 16), 1);
        GenerationSemanticIndex.RiverMatch match = history.findRecordedRiver(
                GenerationSemanticIndex.RiverQuery.acrossActivations(
                        query.types(),
                        query.profileKey(),
                        new ChunkGenerationSemantics.BlockPosition(blockX, 0, blockZ),
                        maxChunkRadius
                )
        ).orElse(null);
        if (match == null) {
            return null;
        }
        ChunkGenerationSemantics.RiverFeatureOccurrence occurrence = match.occurrence();
        ChunkGenerationSemantics.BlockPosition position = occurrence.position();
        BigInteger maximumDistanceSquared = BigInteger.valueOf(maximumDistance)
                .multiply(BigInteger.valueOf(maximumDistance));
        if (horizontalDistanceSquared(blockX, blockZ, position.x(), position.z())
                .compareTo(maximumDistanceSquared) > 0) {
            return null;
        }
        return new RiverResult(
                RiverSource.RECORDED,
                occurrence.type(),
                Optional.of(occurrence.profileKey()),
                occurrence.featureId(),
                position.x(),
                position.y(),
                position.z(),
                OptionalLong.of(match.chunk().activationId())
        );
    }

    private static RiverResult activeRiver(
            Engine engine,
            GenerationHistory history,
            HydrologyFeatureQuery query,
            int blockX,
            int blockZ,
            int maximumDistance,
            IntConsumer progress
    ) {
        IrisHydrologyRuntime runtime = engine.getComplex().getHydrologyRuntime();
        if (runtime == null) {
            return null;
        }
        Predicate<HydrologyFeatureRef> eligibility = feature -> allowsActiveHydrologyPrediction(
                engine,
                feature.x(),
                feature.z()
        );
        HydrologyFeatureRef feature = runtime.nearestFeature(
                query.types(),
                query.profileKey(),
                blockX,
                blockZ,
                maximumDistance,
                eligibility,
                progress
        ).orElse(null);
        if (feature == null) {
            return null;
        }
        OptionalLong activationId = history == null
                ? OptionalLong.empty()
                : OptionalLong.of(history.activeActivation().activationId());
        return new RiverResult(
                RiverSource.ACTIVE_PREDICTION,
                feature.type(),
                Optional.ofNullable(query.profileKey()),
                feature.id(),
                feature.x(),
                Math.addExact(feature.y(), engine.getDimension().getMinHeight()),
                feature.z(),
                activationId
        );
    }

    private static boolean allowsActiveHydrologyPrediction(Engine engine, int blockX, int blockZ) {
        TransitionGenerationPlan transition = engine.getComplex().getTransitionGenerationPlan();
        if (transition != null && transition.hydrologyWeightAt(blockX, blockZ) != 1D) {
            return false;
        }
        return allowsActivePrediction(
                engine,
                Math.floorDiv(blockX, 16),
                Math.floorDiv(blockZ, 16)
        );
    }

    private static Optional<GenerationHistory> history(Engine engine) {
        if (!(engine instanceof IrisEngine irisEngine)) {
            return Optional.empty();
        }
        return irisEngine.getGenerationHistoryRuntimeRouter()
                .map(GenerationHistoryRuntimeRouter::history);
    }

    private static BigInteger horizontalDistanceSquared(
            int firstX,
            int firstZ,
            int secondX,
            int secondZ
    ) {
        BigInteger deltaX = BigInteger.valueOf((long) firstX - secondX);
        BigInteger deltaZ = BigInteger.valueOf((long) firstZ - secondZ);
        return deltaX.multiply(deltaX).add(deltaZ.multiply(deltaZ));
    }

    public enum RiverSource {
        RECORDED,
        ACTIVE_PREDICTION
    }

    public record RiverResult(
            RiverSource source,
            HydrologyFeatureType type,
            Optional<String> profileKey,
            long featureId,
            int x,
            int y,
            int z,
            OptionalLong activationId
    ) {
        public RiverResult {
            source = Objects.requireNonNull(source, "source");
            type = Objects.requireNonNull(type, "type");
            profileKey = Objects.requireNonNull(profileKey, "profileKey");
            activationId = Objects.requireNonNull(activationId, "activationId");
        }
    }
}
