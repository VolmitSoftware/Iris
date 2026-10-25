package art.arcane.iris.world.history;

import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.locator.SubterrainLocator;
import art.arcane.iris.generation.subterrain.SubterrainPlanner;
import art.arcane.iris.structure.placement.IrisStructureLocator;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.hydrology.query.HydrologyFeatureQuery;
import art.arcane.iris.generation.hydrology.query.HydrologyFeatureRef;
import art.arcane.iris.generation.hydrology.query.HydrologyFeatureType;

import java.math.BigInteger;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.BooleanSupplier;

public final class GenerationSemanticQueries {
    public static final int RIVER_SEARCH_DISTANCE = 7168;

    private GenerationSemanticQueries() {
    }

    public static Optional<SubterrainLocator.Result> nearestSubterrain(
            Engine engine, SubterrainLocator.Query query, int x, int worldY, int z, int maximumDistance) {
        return nearestSubterrain(engine, query, x, worldY, z, maximumDistance, () -> true);
    }

    public static Optional<SubterrainLocator.Result> nearestSubterrain(
            Engine engine, SubterrainLocator.Query query, int x, int worldY, int z, int maximumDistance,
            BooleanSupplier running) {
        return nearestSubterrain(engine, query, x, worldY, z, maximumDistance, running, ignored -> true);
    }

    public static Optional<SubterrainLocator.Result> nearestSubterrain(
            Engine engine, SubterrainLocator.Query query, int x, int worldY, int z, int maximumDistance,
            BooleanSupplier running, Predicate<SubterrainLocator.Result> allowed) {
        Engine requiredEngine = Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(query, "query");
        GenerationHistoryRuntimeRouter router = requiredEngine instanceof IrisEngine irisEngine
                ? irisEngine.getGenerationHistoryRuntimeRouter().orElse(null) : null;
        if (router == null) {
            return SubterrainLocator.nearest(requiredEngine.getComplex().getSubterrainPlanner(), query, x, worldY, z,
                    maximumDistance, allowed, running);
        }
        GenerationHistory history = router.history();
        SubterrainLocator.Result nearest = null;
        try {
            for (GenerationActivation activation : history.manifest().activations()) {
                if (history.manifest().pendingActivation().map(pending ->
                        pending.activationId() == activation.activationId()).orElse(false)) {
                    continue;
                }
                SubterrainPlanner planner = router.biomes().subterrainPlanner(activation.activationId());
                SubterrainLocator.Result candidate = SubterrainLocator.nearest(planner, query, x, worldY, z,
                        maximumDistance, result -> allowsSubterrainPrediction(requiredEngine, history,
                                activation.activationId(), result) && allowed.test(result), running).orElse(null);
                if (candidate != null && (nearest == null || distanceSquared(x, worldY, z, candidate)
                        .compareTo(distanceSquared(x, worldY, z, nearest)) < 0)) {
                    nearest = candidate;
                }
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("Unable to locate saved subterrain features", failure);
        }
        return Optional.ofNullable(nearest);
    }

    public static Optional<SubterrainLocator.Result> drySubterrainLanding(
            Engine engine, SubterrainLocator.Result target, BooleanSupplier running) {
        GenerationHistoryRuntimeRouter router = engine instanceof IrisEngine irisEngine
                ? irisEngine.getGenerationHistoryRuntimeRouter().orElse(null) : null;
        if (router == null) {
            return SubterrainLocator.dryLanding(engine.getComplex().getSubterrainPlanner(), target, running);
        }
        GenerationHistory history = router.history();
        GenerationActivation owner = history.resolveActivation(target.x() >> 4, target.z() >> 4);
        try {
            SubterrainPlanner planner = router.biomes().subterrainPlanner(owner.activationId());
            return SubterrainLocator.dryLanding(planner, target, running,
                    landing -> allowsSubterrainPrediction(engine, history, owner.activationId(), landing));
        } catch (IOException failure) {
            throw new UncheckedIOException("Unable to resolve saved subterrain landing", failure);
        }
    }

    private static boolean allowsSubterrainPrediction(Engine engine, GenerationHistory history,
                                                      long activationId, SubterrainLocator.Result result) {
        int chunkX = Math.floorDiv(result.x(), 16);
        int chunkZ = Math.floorDiv(result.z(), 16);
        GenerationActivation owner = history.resolveActivation(chunkX, chunkZ);
        if (owner.activationId() != activationId) {
            return false;
        }
        if (history.isActiveUnowned(chunkX, chunkZ)) {
            return engine.getComplex().allowsNewDiscreteContentAt(result.x(), result.z());
        }
        return true;
    }

    private static BigInteger distanceSquared(int x, int y, int z, SubterrainLocator.Result result) {
        BigInteger deltaY = BigInteger.valueOf((long) y - result.y());
        return horizontalDistanceSquared(x, z, result.x(), result.z()).add(deltaY.multiply(deltaY));
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
            HydrologyFeatureType type,
            int blockX,
            int blockZ,
            int maximumDistance
    ) {
        Engine requiredEngine = Objects.requireNonNull(engine, "engine");
        HydrologyFeatureType requiredType = Objects.requireNonNull(type, "type");
        if (maximumDistance < 0) {
            throw new IllegalArgumentException("Maximum river search distance cannot be negative.");
        }
        GenerationHistory history = history(requiredEngine).orElse(null);
        RiverResult recorded = recordedRiver(
                history,
                requiredType,
                blockX,
                blockZ,
                maximumDistance
        );
        RiverResult active = recordedOnly(requiredType) ? null : activeRiver(
                requiredEngine,
                history,
                requiredType,
                blockX,
                blockZ,
                maximumDistance
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

    private static boolean recordedOnly(HydrologyFeatureType type) {
        return type == HydrologyFeatureType.CAVE_POOL;
    }

    public static String riverSearchMessage(HydrologyFeatureType type, int maximumDistance) {
        String label = type.key().replace('_', ' ');
        return recordedOnly(type)
                ? "Searching generated chunks for the nearest " + label + " within " + maximumDistance + " blocks..."
                : "Searching for the nearest " + label + " within " + maximumDistance + " blocks...";
    }

    public static String riverNotFoundMessage(HydrologyFeatureType type, int maximumDistance) {
        String label = type.key().replace('_', ' ');
        return recordedOnly(type)
                ? "No " + label + " found in generated chunks within " + maximumDistance + " blocks. Cave pools form in carved caves, so "
                + "only chunks that have already generated can hold one."
                : "No " + label + " found within " + maximumDistance + " blocks.";
    }

    public static List<ChunkGenerationSemantics.BlockPosition> recordedCavePools(
            Engine engine,
            int minimumChunkX,
            int minimumChunkZ,
            int maximumChunkX,
            int maximumChunkZ
    ) {
        GenerationHistory history = history(Objects.requireNonNull(engine, "engine")).orElse(null);
        List<ChunkGenerationSemantics.BlockPosition> pools = new ArrayList<>();
        if (history == null) {
            return pools;
        }
        for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
            for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
                ChunkGenerationSemantics semantics = history.semantics(chunkX, chunkZ).orElse(null);
                if (semantics == null) {
                    continue;
                }
                for (ChunkGenerationSemantics.RiverFeatureOccurrence occurrence : semantics.riverFeatures()) {
                    if (occurrence.type() == HydrologyFeatureType.CAVE_POOL) {
                        pools.add(occurrence.position());
                    }
                }
            }
        }
        return pools;
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
            HydrologyFeatureType type,
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
                        Set.of(type),
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
            HydrologyFeatureType type,
            int blockX,
            int blockZ,
            int maximumDistance
    ) {
        HydrologyRuntime runtime = engine.getComplex().getHydrologyRuntime();
        if (runtime == null || !runtime.enabled()) {
            return null;
        }
        Map<Long, Boolean> eligibleChunks = new HashMap<>();
        TransitionDisplacementField displacement = engine.getComplex().getTransitionDisplacement();
        Predicate<HydrologyFeatureRef> eligibility = feature -> (displacement == null
                || displacement.seamWeight(feature.x(), feature.z()) == 1D)
                && eligibleChunks.computeIfAbsent(packChunk(Math.floorDiv(feature.x(), 16), Math.floorDiv(feature.z(), 16)),
                chunk -> allowsActivePrediction(engine, Math.floorDiv(feature.x(), 16), Math.floorDiv(feature.z(), 16)));
        HydrologyFeatureRef feature = runtime.nearestFeature(
                blockX,
                blockZ,
                HydrologyFeatureQuery.of(type),
                maximumDistance,
                eligibility
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
                feature.id(),
                feature.x(),
                Math.addExact(feature.y(), engine.getDimension().getMinHeight()),
                feature.z(),
                activationId
        );
    }

    private static long packChunk(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
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
            long featureId,
            int x,
            int y,
            int z,
            OptionalLong activationId
    ) {
        public RiverResult {
            source = Objects.requireNonNull(source, "source");
            type = Objects.requireNonNull(type, "type");
            activationId = Objects.requireNonNull(activationId, "activationId");
        }
    }
}
