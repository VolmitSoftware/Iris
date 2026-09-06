package art.arcane.iris.nativegen;

import art.arcane.iris.engine.IrisEngine;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.NativeStructureGenerationPolicy;
import art.arcane.iris.engine.framework.NativeStructureOwnershipRecord;
import art.arcane.iris.engine.framework.NativeStructurePlacementPlanner;
import art.arcane.iris.engine.framework.NativeStructureStartPlan;
import art.arcane.iris.engine.framework.NativeStructureVolume;
import art.arcane.iris.engine.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.engine.object.IrisNativeStructureDecision;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.collection.KList;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntBinaryOperator;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * Resolves the world-space piece bounds of every native structure that will generate around a query rect.
 *
 * <p>The answer is derived from the seed, the registries and this pack's structure policy only: candidate origins
 * come from the placement grid and from vanilla structure placements, and each candidate is assembled through the
 * same {@link NativeStructureFactory} path chunk generation uses. Nothing here reads chunk state, ownership records
 * or generation progress, so the same query answers identically no matter which chunks already exist.
 */
public final class NativeStructureVolumeIndex {
    private static final int ORIGIN_REACH_CHUNKS = NativeStructureOwnershipRecord.MAX_REFERENCE_DISTANCE_CHUNKS;
    private static final int ORIGIN_WINDOW_DIAMETER = ORIGIN_REACH_CHUNKS * 2 + 1;
    private static final int ORIGIN_WINDOW_STRIPE_COUNT = Long.SIZE;
    private static final int MAX_CACHED_ORIGIN_CHUNKS = 16_384;
    private static final int MAX_CACHED_QUERY_CHUNKS = 4_096;
    private static final Map<Engine, NativeStructureVolumeIndex> INDEXES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<String> WARNED_RESOLUTION_FAILURES = ConcurrentHashMap.newKeySet();

    private final Context context;
    private final OriginResolver origins;
    private final Map<RuntimeChunkKey, KList<NativeStructureVolume>> originCache = lru(MAX_CACHED_ORIGIN_CHUNKS);
    private final Map<RuntimeChunkKey, KList<NativeStructureVolume>> queryCache = lru(MAX_CACHED_QUERY_CHUNKS);
    private final ConcurrentHashMap<RuntimeChunkKey, CompletableFuture<KList<NativeStructureVolume>>> originBuilds =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<RuntimeChunkKey, CompletableFuture<KList<NativeStructureVolume>>> queryBuilds =
            new ConcurrentHashMap<>();
    private final ReentrantLock[] originWindowStripes = createOriginWindowStripes();
    private final IntConsumer retirementListener = this::evictRuntime;

    private NativeStructureVolumeIndex(Context context) {
        this.context = Objects.requireNonNull(context, "Native structure volume context must not be null");
        this.origins = this::buildOriginVolumes;
    }

    private NativeStructureVolumeIndex(OriginResolver origins) {
        this.context = null;
        this.origins = Objects.requireNonNull(origins, "Native structure origin resolver must not be null");
    }

    public static void install(Engine engine, Context context) {
        Engine requiredEngine = Objects.requireNonNull(engine, "Native structure volume index requires an engine");
        NativeStructureVolumeIndex installed = new NativeStructureVolumeIndex(context);
        synchronized (INDEXES) {
            NativeStructureVolumeIndex previous = INDEXES.put(requiredEngine, installed);
            if (previous != null) {
                previous.detachRetirementListener(requiredEngine);
            }
            installed.attachRetirementListener(requiredEngine);
        }
    }

    public static void uninstall(Engine engine) {
        if (engine != null) {
            synchronized (INDEXES) {
                NativeStructureVolumeIndex removed = INDEXES.remove(engine);
                if (removed != null) {
                    removed.detachRetirementListener(engine);
                }
            }
        }
    }

    public static void invalidate(Engine engine) {
        if (engine == null) {
            return;
        }
        synchronized (INDEXES) {
            NativeStructureVolumeIndex current = INDEXES.get(engine);
            if (current != null) {
                NativeStructureVolumeIndex replacement = current.fresh();
                current.detachRetirementListener(engine);
                INDEXES.put(engine, replacement);
                replacement.attachRetirementListener(engine);
            }
        }
    }

    public static KList<NativeStructureVolume> volumes(Engine engine, int minX, int minZ, int maxX, int maxZ) {
        NativeStructureVolumeIndex index = engine == null ? null : INDEXES.get(engine);
        return index == null ? NativeStructureVolume.NONE : index.resolve(engine, minX, minZ, maxX, maxZ);
    }

    static NativeStructureVolumeIndex forTesting(OriginResolver origins) {
        return new NativeStructureVolumeIndex(origins);
    }

    static int originReachChunks() {
        return ORIGIN_REACH_CHUNKS;
    }

    private NativeStructureVolumeIndex fresh() {
        return context == null
                ? new NativeStructureVolumeIndex(origins)
                : new NativeStructureVolumeIndex(context);
    }

    KList<NativeStructureVolume> resolve(Engine engine, int minX, int minZ, int maxX, int maxZ) {
        int runtimeId = runtimeId(engine);
        int fromChunkX = Math.min(minX, maxX) >> 4;
        int toChunkX = Math.max(minX, maxX) >> 4;
        int fromChunkZ = Math.min(minZ, maxZ) >> 4;
        int toChunkZ = Math.max(minZ, maxZ) >> 4;
        KList<NativeStructureVolume> matches = null;
        for (int chunkX = fromChunkX; chunkX <= toChunkX; chunkX++) {
            for (int chunkZ = fromChunkZ; chunkZ <= toChunkZ; chunkZ++) {
                for (NativeStructureVolume volume : chunkVolumes(engine, runtimeId, chunkX, chunkZ)) {
                    if (!volume.intersectsRect(minX, minZ, maxX, maxZ)) {
                        continue;
                    }
                    if (matches == null) {
                        matches = new KList<>();
                    }
                    if (!matches.contains(volume)) {
                        matches.add(volume);
                    }
                }
            }
        }
        return matches == null ? NativeStructureVolume.NONE : matches;
    }

    private KList<NativeStructureVolume> chunkVolumes(
            Engine engine,
            int runtimeId,
            int chunkX,
            int chunkZ
    ) {
        return cached(queryCache, queryBuilds, new RuntimeChunkKey(runtimeId, chunkKey(chunkX, chunkZ)),
                () -> buildChunkVolumes(engine, chunkX, chunkZ));
    }

    private KList<NativeStructureVolume> buildChunkVolumes(Engine engine, int chunkX, int chunkZ) {
        long stripeMask = originWindowStripeMask(chunkX, chunkZ);
        lockOriginWindow(stripeMask);
        try {
            return buildChunkVolumesCoordinated(engine, chunkX, chunkZ);
        } finally {
            unlockOriginWindow(stripeMask);
        }
    }

    private KList<NativeStructureVolume> buildChunkVolumesCoordinated(Engine engine, int chunkX, int chunkZ) {
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        int maxX = minX + 15;
        int maxZ = minZ + 15;
        KList<NativeStructureVolume> volumes = null;
        for (int originX = chunkX - ORIGIN_REACH_CHUNKS; originX <= chunkX + ORIGIN_REACH_CHUNKS; originX++) {
            for (int originZ = chunkZ - ORIGIN_REACH_CHUNKS; originZ <= chunkZ + ORIGIN_REACH_CHUNKS; originZ++) {
                for (NativeStructureVolume volume : originVolumes(engine, originX, originZ)) {
                    if (!volume.intersectsRect(minX, minZ, maxX, maxZ)) {
                        continue;
                    }
                    if (volumes == null) {
                        volumes = new KList<>();
                    }
                    volumes.add(volume);
                }
            }
        }
        return volumes == null ? NativeStructureVolume.NONE : volumes;
    }

    static long originWindowStripeMask(int chunkX, int chunkZ) {
        int minCellX = Math.floorDiv(chunkX - ORIGIN_REACH_CHUNKS, ORIGIN_WINDOW_DIAMETER);
        int maxCellX = Math.floorDiv(chunkX + ORIGIN_REACH_CHUNKS, ORIGIN_WINDOW_DIAMETER);
        int minCellZ = Math.floorDiv(chunkZ - ORIGIN_REACH_CHUNKS, ORIGIN_WINDOW_DIAMETER);
        int maxCellZ = Math.floorDiv(chunkZ + ORIGIN_REACH_CHUNKS, ORIGIN_WINDOW_DIAMETER);
        long mask = 0L;
        for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
            for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                mask |= 1L << originWindowStripe(cellX, cellZ);
            }
        }
        return mask;
    }

    KList<NativeStructureVolume> originVolumes(Engine engine, int chunkX, int chunkZ) {
        try (GenerationHistoryRuntimeRouter.CoordinateScope ignored =
                     openHistoryCoordinateScope(engine, chunkX, chunkZ)) {
            int runtimeId = runtimeId(engine);
            return cached(
                    originCache,
                    originBuilds,
                    new RuntimeChunkKey(runtimeId, chunkKey(chunkX, chunkZ)),
                    () -> origins.volumesAt(engine, chunkX, chunkZ)
            );
        }
    }

    private KList<NativeStructureVolume> buildOriginVolumes(Engine engine, int chunkX, int chunkZ) {
        ChunkGeneratorStructureState state = context.structureState().get();
        ChunkGenerator generator = context.generator().get();
        BiomeSource biomeSource = context.biomeSource().get();
        if (state == null || generator == null || biomeSource == null) {
            return NativeStructureVolume.NONE;
        }
        Registry<Structure> registry = context.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Bindings bindings = new Bindings(state, generator, biomeSource);
        KList<NativeStructureVolume> volumes = appendPlannedVolumes(engine, bindings, registry, chunkX, chunkZ, null);
        volumes = appendVanillaVolumes(engine, bindings, registry, chunkX, chunkZ, volumes);
        return volumes == null ? NativeStructureVolume.NONE : volumes;
    }

    private KList<NativeStructureVolume> appendPlannedVolumes(Engine engine, Bindings bindings,
                                                             Registry<Structure> registry, int chunkX, int chunkZ,
                                                             KList<NativeStructureVolume> volumes) {
        KList<NativeStructureStartPlan> plans;
        try {
            plans = NativeStructurePlacementPlanner.plansAt(engine, chunkX, chunkZ);
        } catch (RuntimeException | Error error) {
            warnOnce("iris-placements", error);
            return volumes;
        }

        KList<NativeStructureVolume> target = volumes;
        for (NativeStructureStartPlan plan : plans) {
            IrisNativeStructureDecision decision = NativeStructurePlacementPlanner.decisionFor(plan);
            if (!decision.generate()) {
                continue;
            }
            Identifier identifier = Identifier.tryParse(plan.source().getStructure());
            if (identifier == null) {
                continue;
            }
            Structure structure = registry.getValue(identifier);
            if (structure == null) {
                continue;
            }
            String structureId = identifier.toString();
            try {
                StructureStart generated = NativeStructureFactory.generate(
                        generationContext(engine, bindings), registry.wrapAsHolder(structure), plan, 0);
                target = appendPieces(target, structureId, generated);
            } catch (RuntimeException | Error error) {
                warnOnce(structureId, error);
            }
        }
        return target;
    }

    private KList<NativeStructureVolume> appendVanillaVolumes(Engine engine, Bindings bindings,
                                                             Registry<Structure> registry, int chunkX, int chunkZ,
                                                             KList<NativeStructureVolume> volumes) {
        ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
        IntBinaryOperator surfaceHeight = surfaceHeight(engine);
        int worldMinY = context.heightAccessor().getMinY();
        int worldMaxYExclusive = worldMinY + context.heightAccessor().getHeight();
        KList<NativeStructureVolume> target = volumes;
        for (Holder<StructureSet> setHolder : bindings.state().possibleStructureSets()) {
            StructureSet set = setHolder.value();
            if (!set.placement().isStructureChunk(bindings.state(), chunkX, chunkZ)) {
                continue;
            }
            for (StructureSet.StructureSelectionEntry entry : set.structures()) {
                Holder<Structure> holder = entry.structure();
                Structure structure = holder.value();
                Identifier identifier = registry.getKey(structure);
                if (identifier == null) {
                    continue;
                }
                String structureId = identifier.toString();
                boolean undergroundStep = NativeStructureVegetationClearer.isUndergroundStep(structure.step());
                try {
                    IrisNativeStructureDecision decision = NativeStructureGenerationPolicy.resolve(
                            engine, structureId, undergroundStep);
                    if (!decision.generate()) {
                        continue;
                    }
                    StructureStart generated = structure.generate(
                            holder,
                            context.levelKey(),
                            context.registryAccess(),
                            bindings.generator(),
                            bindings.biomeSource(),
                            bindings.state().randomState(),
                            context.templateManager(),
                            bindings.state().getLevelSeed(),
                            chunkPos,
                            0,
                            context.heightAccessor(),
                            structure.biomes()::contains
                    );
                    if (generated == null || !generated.isValid()) {
                        continue;
                    }
                    NativeStructureVerticalPlacer.applyVerticalPlacement(
                            generated,
                            structureId,
                            decision.yShift(),
                            bindings.generator().getSeaLevel(),
                            worldMinY,
                            worldMaxYExclusive,
                            undergroundStep,
                            decision.preserveSourceY(),
                            decision.yBand(),
                            surfaceHeight);
                    target = appendPieces(target, structureId, generated);
                } catch (RuntimeException | Error error) {
                    warnOnce(structureId, error);
                }
            }
        }
        return target;
    }

    private NativeStructureFactory.GenerationContext generationContext(Engine engine, Bindings bindings) {
        return new NativeStructureFactory.GenerationContext(
                context.registryAccess(),
                bindings.generator(),
                bindings.biomeSource(),
                bindings.state().randomState(),
                context.templateManager(),
                bindings.state().getLevelSeed(),
                context.levelKey(),
                context.heightAccessor(),
                biome -> true,
                bindings.generator().getSeaLevel(),
                surfaceHeight(engine)
        );
    }

    private record Bindings(ChunkGeneratorStructureState state, ChunkGenerator generator, BiomeSource biomeSource) {
    }

    @FunctionalInterface
    interface OriginResolver {
        KList<NativeStructureVolume> volumesAt(Engine engine, int chunkX, int chunkZ);
    }

    private static IntBinaryOperator surfaceHeight(Engine engine) {
        return (x, z) -> Engine.hostHeight(engine, x, z, true) + engine.getMinHeight();
    }

    static KList<NativeStructureVolume> appendPieces(KList<NativeStructureVolume> volumes,
                                                     String structureId, StructureStart start) {
        if (start == null || !start.isValid()) {
            return volumes;
        }
        KList<NativeStructureVolume> target = volumes;
        for (StructurePiece piece : start.getPieces()) {
            BoundingBox bounds = piece.getBoundingBox();
            if (target == null) {
                target = new KList<>();
            }
            target.add(new NativeStructureVolume(structureId,
                    bounds.minX(), bounds.minY(), bounds.minZ(),
                    bounds.maxX(), bounds.maxY(), bounds.maxZ()));
        }
        return target;
    }

    void evictRuntime(int runtimeId) {
        synchronized (originCache) {
            originBuilds.keySet().removeIf(key -> key.runtimeId() == runtimeId);
            originCache.keySet().removeIf(key -> key.runtimeId() == runtimeId);
        }
        synchronized (queryCache) {
            queryBuilds.keySet().removeIf(key -> key.runtimeId() == runtimeId);
            queryCache.keySet().removeIf(key -> key.runtimeId() == runtimeId);
        }
    }

    private KList<NativeStructureVolume> cached(
            Map<RuntimeChunkKey, KList<NativeStructureVolume>> cache,
            ConcurrentHashMap<RuntimeChunkKey, CompletableFuture<KList<NativeStructureVolume>>> builds,
            RuntimeChunkKey key,
            Supplier<KList<NativeStructureVolume>> loader
    ) {
        synchronized (cache) {
            KList<NativeStructureVolume> hit = cache.get(key);
            if (hit != null) {
                return hit;
            }
        }

        CompletableFuture<KList<NativeStructureVolume>> future = new CompletableFuture<>();
        CompletableFuture<KList<NativeStructureVolume>> existing = builds.putIfAbsent(key, future);
        if (existing != null) {
            return NativeBuildFutures.awaitBuild(existing, "Native structure volume build");
        }

        // Close the check-then-claim window: a racer whose cache check missed before the
        // previous builder cached could claim the build slot after it retired and rebuild.
        synchronized (cache) {
            KList<NativeStructureVolume> published = cache.get(key);
            if (published != null) {
                future.complete(published);
                builds.remove(key, future);
                return published;
            }
        }

        try {
            KList<NativeStructureVolume> built = loader.get();
            synchronized (cache) {
                if (builds.get(key) == future) {
                    cache.put(key, built);
                }
            }
            future.complete(built);
            return built;
        } catch (RuntimeException | Error error) {
            future.completeExceptionally(error);
            throw error;
        } finally {
            builds.remove(key, future);
        }
    }

    private static void warnOnce(String structureId, Throwable error) {
        if (WARNED_RESOLUTION_FAILURES.add(structureId)) {
            IrisLogging.reportError("Native structure volume resolution failed for '" + structureId
                    + "'; objects will not be vetoed against it.", error);
        }
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    private static int originWindowStripe(int cellX, int cellZ) {
        long mixed = chunkKey(cellX, cellZ);
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53L;
        mixed ^= mixed >>> 33;
        return (int) mixed & (ORIGIN_WINDOW_STRIPE_COUNT - 1);
    }

    private static ReentrantLock[] createOriginWindowStripes() {
        ReentrantLock[] stripes = new ReentrantLock[ORIGIN_WINDOW_STRIPE_COUNT];
        for (int i = 0; i < stripes.length; i++) {
            stripes[i] = new ReentrantLock();
        }
        return stripes;
    }

    private void lockOriginWindow(long stripeMask) {
        long pending = stripeMask;
        while (pending != 0L) {
            int stripe = Long.numberOfTrailingZeros(pending);
            originWindowStripes[stripe].lock();
            pending &= pending - 1L;
        }
    }

    private void unlockOriginWindow(long stripeMask) {
        long pending = stripeMask;
        while (pending != 0L) {
            int stripe = Long.SIZE - 1 - Long.numberOfLeadingZeros(pending);
            originWindowStripes[stripe].unlock();
            pending &= ~(1L << stripe);
        }
    }

    private static GenerationHistoryRuntimeRouter.CoordinateScope openHistoryCoordinateScope(
            Engine engine,
            int chunkX,
            int chunkZ
    ) {
        if (!(engine instanceof IrisEngine irisEngine)) {
            return null;
        }
        try {
            return irisEngine.openGenerationHistoryCoordinateScope(chunkX << 4, chunkZ << 4);
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to route native structure origin "
                    + chunkX + "," + chunkZ + " through generation history.", failure);
        }
    }

    private static int runtimeId(Engine engine) {
        return engine == null ? 0 : engine.getCacheID();
    }

    private void attachRetirementListener(Engine engine) {
        if (engine instanceof IrisEngine irisEngine) {
            irisEngine.addGenerationRuntimeRetirementListener(retirementListener);
        }
    }

    private void detachRetirementListener(Engine engine) {
        if (engine instanceof IrisEngine irisEngine) {
            irisEngine.removeGenerationRuntimeRetirementListener(retirementListener);
        }
    }

    private static Map<RuntimeChunkKey, KList<NativeStructureVolume>> lru(int capacity) {
        return new LinkedHashMap<>(16, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(
                    Map.Entry<RuntimeChunkKey, KList<NativeStructureVolume>> eldest
            ) {
                return size() > capacity;
            }
        };
    }

    private record RuntimeChunkKey(int runtimeId, long chunkKey) {
    }

    /**
     * The generator, biome source and structure state arrive as suppliers so an installed index never holds the
     * level (and through it the engine) strongly. That keeps the weakly keyed registry collectable on world unload.
     */
    public record Context(
            RegistryAccess registryAccess,
            StructureTemplateManager templateManager,
            ResourceKey<Level> levelKey,
            LevelHeightAccessor heightAccessor,
            Supplier<ChunkGenerator> generator,
            Supplier<BiomeSource> biomeSource,
            Supplier<ChunkGeneratorStructureState> structureState
    ) {
    }
}
