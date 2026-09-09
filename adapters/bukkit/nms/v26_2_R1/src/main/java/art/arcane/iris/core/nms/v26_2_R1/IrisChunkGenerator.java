package art.arcane.iris.core.nms.v26_2_R1;

import art.arcane.iris.nativegen.NativeTransitionColumn;
import art.arcane.iris.nativegen.NativeTerrainHeightCache;
import art.arcane.iris.engine.history.TerrainBoundarySignature;
import java.util.Optional;
import art.arcane.iris.nativegen.NativeGenerationWriteGuard;
import java.util.function.LongPredicate;
import art.arcane.iris.engine.DimensionStackContext;
import art.arcane.iris.engine.DimensionStackLayout;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineLifecycleTasks;
import art.arcane.iris.engine.framework.GenerationSessionException;
import art.arcane.iris.engine.framework.GenerationSessionLease;
import art.arcane.iris.engine.framework.IrisStructureLocator;
import art.arcane.iris.engine.framework.NativeStructureOwnershipRecord;
import art.arcane.iris.engine.framework.NativeStructureOwnershipStore;
import art.arcane.iris.engine.framework.NativeStructureStartPlan;
import art.arcane.iris.engine.framework.NativeStructureGenerationPolicy;
import art.arcane.iris.engine.IrisEngine;
import art.arcane.iris.engine.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.engine.history.NativeTerrainReceipt;
import art.arcane.iris.engine.history.NativeBiomeSpawnSelection;
import art.arcane.iris.engine.history.SavedTerrainChunk;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import art.arcane.iris.engine.platform.BukkitChunkGenerator;
import art.arcane.iris.engine.platform.studio.generators.JigsawStudioGenerator;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisDimensionCarvingResolver;
import art.arcane.iris.engine.object.IrisMaterialPalette;
import art.arcane.iris.engine.object.IrisNativeStructureDecision;
import art.arcane.iris.engine.object.IrisStaticObjectLayer;
import art.arcane.iris.nativegen.NativeStructureGenerationException;
import art.arcane.iris.nativegen.NativeStructureStartInjector;
import art.arcane.iris.nativegen.NativeStructureReferenceEnvelope;
import art.arcane.iris.nativegen.NativeStructureLocateResults;
import art.arcane.iris.nativegen.NativeStructureLocatePersistence;
import art.arcane.iris.nativegen.NativeStructureOwnershipRecovery;
import art.arcane.iris.nativegen.NativeStructurePostProcessor;
import art.arcane.iris.nativegen.NativeStructureReferenceRepair;
import art.arcane.iris.nativegen.NativeStructureSurfaceFitter;
import art.arcane.iris.nativegen.NativeStructureTerrainIntegrator;
import art.arcane.iris.nativegen.NativeStructureVegetationClearer;
import art.arcane.iris.nativegen.NativeStructureVerticalPlacer;
import art.arcane.iris.nativegen.NativeStructureVanillaLocator;
import art.arcane.iris.nativegen.NativeStructureVolumeIndex;
import art.arcane.iris.nativegen.WorldgenTerrainHeightmaps;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.IrisCustomData;
import art.arcane.iris.util.common.reflect.WrappedField;
import art.arcane.iris.util.common.reflect.WrappedReturningMethod;
import art.arcane.iris.util.project.context.IrisContext;
import art.arcane.volmlib.util.math.RNG;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.craftbukkit.generator.CustomChunkGenerator;
import org.bukkit.block.data.BlockData;
import org.spigotmc.SpigotWorldConfig;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntBinaryOperator;
import java.util.function.Predicate;

public class IrisChunkGenerator extends CustomChunkGenerator implements LongPredicate {
    private static final Set<Heightmap.Types> AUTHORING_HEIGHTMAPS = Set.of(
            Heightmap.Types.WORLD_SURFACE_WG,
            Heightmap.Types.OCEAN_FLOOR_WG,
            Heightmap.Types.WORLD_SURFACE,
            Heightmap.Types.OCEAN_FLOOR,
            Heightmap.Types.MOTION_BLOCKING,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES);
    private static final WrappedField<ChunkGenerator, BiomeSource> BIOME_SOURCE;
    private static final WrappedReturningMethod<Heightmap, Object> SET_HEIGHT;
    private static final Runnable NO_OP = () -> {
    };
    private final ChunkGenerator delegate;
    private final Engine engine;
    private final CustomBiomeSource customBiomeSource;
    private final ServerLevel runtimeLevel;
    private final @Nullable BukkitChunkGenerator platformGenerator;
    private final int runtimeMinY;
    private final int runtimeHeight;
    private final int runtimeSeaLevel;
    private final ConcurrentHashMap<SpawnTableKey, WeightedList<MobSpawnSettings.SpawnerData>> mergedSpawnTables = new ConcurrentHashMap<>();
    private final ImportedFeatureStage importedFeatures;
    private final NativeTerrainHeightCache terrainHeights = new NativeTerrainHeightCache();
    private final AtomicReference<StudioStructureState> retainedStudioStructureState = new AtomicReference<>();
    private volatile ReachableStructureCache reachableStructureCache;
    private volatile StructureStepCache structureStepCache;

    public IrisChunkGenerator(ChunkGenerator delegate, long seed, Engine engine, World world) {
        this(delegate, engine, world, new CustomBiomeSource(seed, engine, world));
    }

    private IrisChunkGenerator(ChunkGenerator delegate, Engine engine, World world, CustomBiomeSource customBiomeSource) {
        super(((CraftWorld) world).getHandle(), edit(delegate, customBiomeSource), world.getGenerator());
        this.delegate = delegate;
        this.engine = engine;
        this.customBiomeSource = customBiomeSource;
        this.importedFeatures = new ImportedFeatureStage(engine);
        ServerLevel level = ((CraftWorld) world).getHandle();
        this.runtimeLevel = level;
        this.platformGenerator = world.getGenerator() instanceof BukkitChunkGenerator bukkitGenerator
                ? bukkitGenerator
                : null;
        this.runtimeMinY = level.getMinY();
        this.runtimeHeight = level.getHeight();
        this.runtimeSeaLevel = runtimeMinY + engine.getDimension().getFluidHeight();
        if (engine instanceof IrisEngine irisEngine) {
            irisEngine.addGenerationRuntimeRetirementListener(this::evictRuntimeCaches);
        }
        installNativeStructureVolumeIndex(level);
    }

    private void evictRuntimeCaches(int runtimeId) {
        terrainHeights.evictRuntime(runtimeId);
        importedFeatures.evictRuntime(runtimeId);
        mergedSpawnTables.keySet().removeIf(key -> key.runtimeId() == runtimeId);
        ReachableStructureCache reachable = reachableStructureCache;
        if (reachable != null && reachable.runtimeId() == runtimeId) {
            reachableStructureCache = null;
        }
        StructureStepCache steps = structureStepCache;
        if (steps != null && steps.runtimeId() == runtimeId) {
            structureStepCache = null;
        }
    }

    private void installNativeStructureVolumeIndex(ServerLevel level) {
        WeakReference<ServerLevel> levelReference = new WeakReference<>(level);
        WeakReference<ChunkGenerator> generatorReference = new WeakReference<>(this);
        WeakReference<BiomeSource> biomeSourceReference = new WeakReference<>(customBiomeSource);
        NativeStructureVolumeIndex.install(engine, new NativeStructureVolumeIndex.Context(
                level.registryAccess(),
                level.getServer().getStructureManager(),
                level.dimension(),
                LevelHeightAccessor.create(runtimeMinY, runtimeHeight),
                generatorReference::get,
                biomeSourceReference::get,
                () -> {
                    ServerLevel active = levelReference.get();
                    return active == null ? null : active.getChunkSource().getGeneratorState();
                }));
    }

    @Override
    public @Nullable Pair<BlockPos, Holder<Structure>> findNearestMapStructure(ServerLevel level, HolderSet<Structure> holders, BlockPos pos, int radius, boolean findUnexplored) {
        if (platformGenerator != null && !platformGenerator.shouldGenerateStructures()) {
            return null;
        }
        if (level != runtimeLevel || level.getChunkSource().getGenerator() != this) {
            return null;
        }
        try (GenerationHistoryRuntimeRouter.RuntimeRoute route = openHistoryRoute(
                     SectionPos.blockToSectionCoord(pos.getX()),
                     SectionPos.blockToSectionCoord(pos.getZ()),
                     "bukkit_nms_structure_locate");
             GenerationHistoryRuntimeRouter.RuntimeRoute.RuntimeScope runtimeScope = openHistoryRuntimeScope(route);
             GenerationSessionLease lease = requireGenerationLease("bukkit_nms_structure_locate");
             IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            HolderSet<Structure> reachable = filterReachableStructures(level, holders);
            NativeStructureVanillaLocator.Candidate nativeCandidate =
                    reachable == null || reachable.size() == 0 ? null
                            : NativeStructureVanillaLocator.predict(
                                    level, reachable, pos, radius, findUnexplored);
            return findNearestIrisStructure(
                    level, holders, pos, Math.max(0, radius),
                    findUnexplored, nativeCandidate);
        }
    }

    private Pair<BlockPos, Holder<Structure>> findNearestIrisStructure(ServerLevel level,
                                                                       HolderSet<Structure> holders,
                                                                       BlockPos pos, int radius,
                                                                       boolean findUnexplored,
                                                                       NativeStructureVanillaLocator.Candidate nativeCandidate) {
        Pair<BlockPos, Holder<Structure>> nativeLocated =
                nativeCandidate == null ? null : nativeCandidate.result();
        Runnable nativeReference = () -> {
            if (nativeCandidate != null) {
                nativeCandidate.reference(level.structureManager());
            }
        };
        Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        List<IrisNativeLocateSearch> searches = new ArrayList<>(holders.size());
        NativeStructureLocatePersistence.ProbeBudget budget = NativeStructureLocatePersistence.probeBudget();
        for (Holder<Structure> holder : holders) {
            Object id = registry.getKey(holder.value());
            if (id == null) {
                throw new IllegalStateException("Native structure locate received an unregistered structure holder");
            }
            String structureId = id.toString();
            if (!IrisStructureLocator.hasNativePlacement(engine, structureId)) {
                continue;
            }
            NativeStructureLocatePersistence.Probe probe = NativeStructureLocatePersistence.probe(
                    level, holder.value(), findUnexplored, budget);
            searches.add(new IrisNativeLocateSearch(
                    holder, structureId, NativeStructureLocatePersistence.search(
                    engine, structureId, pos.getX(), pos.getZ(), radius, probe)));
        }
        searches.sort(Comparator.comparing(IrisNativeLocateSearch::structureId));
        for (int attempt = 0; attempt < NativeStructureLocatePersistence.MAX_SELECTED_CANDIDATE_RETRIES; attempt++) {
            IrisNativeLocateSearch bestSearch = null;
            IrisStructureLocator.LocateResult bestResult = null;
            long bestDistance = Long.MAX_VALUE;
            for (IrisNativeLocateSearch search : searches) {
                IrisStructureLocator.LocateResult result = search.search().predict();
                if (result.status() == IrisStructureLocator.LocateStatus.SEARCH_LIMIT_REACHED) {
                    throw new IllegalStateException("Iris structure locate reached its safety limit for "
                            + search.structureId() + " within " + radius + " placement rings");
                }
                if (!result.found()) {
                    continue;
                }
                long dx = (long) result.originX() - pos.getX();
                long dz = (long) result.originZ() - pos.getZ();
                long distance = dx * dx + dz * dz;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestSearch = search;
                    bestResult = result;
                }
            }
            if (bestSearch == null) {
                return NativeStructureLocateResults.selectAndReference(
                        pos, null, () -> { }, nativeLocated, nativeReference);
            }
            Pair<BlockPos, Holder<Structure>> predicted = Pair.of(
                    new BlockPos(bestResult.originX(), bestResult.baseY(), bestResult.originZ()),
                    bestSearch.holder());
            if (NativeStructureLocateResults.nearest(pos, predicted, nativeLocated) != predicted) {
                return NativeStructureLocateResults.selectAndReference(
                        pos, predicted, () -> { }, nativeLocated, nativeReference);
            }
            NativeStructureLocatePersistence.VerifiedStart verified =
                    bestSearch.search().verify(bestResult);
            if (verified == null) {
                bestSearch.search().reject(bestResult);
                continue;
            }
            BlockPos located = new BlockPos(
                    bestResult.originX(), verified.ownership().locatorY(),
                    bestResult.originZ());
            Pair<BlockPos, Holder<Structure>> irisLocated = Pair.of(located, bestSearch.holder());
            IrisNativeLocateSearch selectedSearch = bestSearch;
            NativeStructureLocatePersistence.VerifiedStart selectedStart = verified;
            return NativeStructureLocateResults.selectAndReference(
                    pos, irisLocated, () -> selectedSearch.search().reference(selectedStart),
                    nativeLocated, nativeReference);
        }
        throw new IllegalStateException("Iris structure locate rejected too many selected candidates within "
                + radius + " placement rings");
    }

    private HolderSet<Structure> filterReachableStructures(ServerLevel level, HolderSet<Structure> holders) {
        Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        List<NativeLocateCandidate> candidates = new ArrayList<>(holders.size());
        for (Holder<Structure> holder : holders) {
            Object id = registry.getKey(holder.value());
            if (id == null) {
                throw new IllegalStateException("Native structure filtering received an unregistered structure holder");
            }
            String key = id.toString();
            IrisNativeStructureDecision decision = NativeStructureGenerationPolicy.resolve(engine,
                    key, NativeStructureVegetationClearer.isUndergroundStep(holder.value().step()));
            if (!decision.generate()) {
                continue;
            }
            candidates.add(new NativeLocateCandidate(holder, key));
        }
        if (candidates.isEmpty()) {
            return HolderSet.direct(List.of());
        }
        Set<String> reachable = reachableStructureKeys(level);
        List<Holder<Structure>> kept = new ArrayList<>(candidates.size());
        for (NativeLocateCandidate candidate : candidates) {
            if (reachable.contains(candidate.key())) {
                kept.add(candidate.holder());
            }
        }
        if (kept.size() == holders.size()) {
            return holders;
        }
        return HolderSet.direct(kept);
    }

    private Set<String> reachableStructureKeys(ServerLevel level) {
        IrisDimension dimension = engine.getDimension();
        int runtimeId = engine.getCacheID();
        ReachableStructureCache cached = reachableStructureCache;
        if (cached != null && cached.dimension() == dimension && cached.runtimeId() == runtimeId) {
            return cached.keys();
        }
        synchronized (this) {
            cached = reachableStructureCache;
            if (cached != null && cached.dimension() == dimension && cached.runtimeId() == runtimeId) {
                return cached.keys();
            }
            Set<String> reachable = Set.copyOf(
                    VanillaStructureBiomes.reachableStructureKeys(level, customBiomeSource));
            reachableStructureCache = new ReachableStructureCache(dimension, runtimeId, reachable);
            return reachable;
        }
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return MapCodec.unit(null);
    }

    @Override
    public ChunkGenerator getDelegate() {
        if (delegate instanceof CustomChunkGenerator chunkGenerator)
            return chunkGenerator.getDelegate();
        return delegate;
    }

    @Override
    public int getMinY() {
        return runtimeMinY;
    }

    @Override
    public int getSeaLevel() {
        return runtimeSeaLevel;
    }

    @Override
    public void createStructures(RegistryAccess registryAccess, ChunkGeneratorStructureState structureState, StructureManager structureManager, ChunkAccess access, StructureTemplateManager templateManager, ResourceKey<Level> levelKey) {
        if (platformGenerator != null && !platformGenerator.shouldGenerateStructures()) {
            return;
        }
        if (runtimeLevel.getChunkSource().getGenerator() != this
                || runtimeLevel.getChunkSource().getGeneratorState() != structureState) {
            return;
        }
        ChunkPos chunkPos = access.getPos();
        try (BukkitChunkGenerator.GenerationStagePermit stage = requireGenerationStage("bukkit_nms_create_structures");
             GenerationHistoryRuntimeRouter.RuntimeRoute route = openHistoryRoute(
                     chunkPos.x(), chunkPos.z(), "bukkit_nms_create_structures");
             GenerationHistoryRuntimeRouter.RuntimeRoute.RuntimeScope runtimeScope = openHistoryRuntimeScope(route);
             GenerationSessionLease lease = requireGenerationLease("bukkit_nms_create_structures");
             IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            if (!allowsRoutedDiscreteGeneration(access, ChunkStatus.STRUCTURE_STARTS)) {
                return;
            }
            Map<Structure, StructureStart> previousStarts = new HashMap<>(access.getAllStarts());
            super.createStructures(registryAccess, structureState, structureManager, access, templateManager, levelKey);
            Map<Structure, NativeStructureStartPlan> configuredStarts = NativeStructureStartInjector.inject(
                    new NativeStructureStartInjector.InjectionContext(
                            engine,
                            registryAccess,
                            structureState,
                            structureManager,
                            access,
                            templateManager,
                            levelKey,
                            this,
                            customBiomeSource
                    ));
            adjustGeneratedStructures(
                    registryAccess, access, previousStarts, configuredStarts, templateManager);
            if (route != null) {
                access.persistentDataContainer.set(new NamespacedKey("iris", "structure_activation"),
                        PersistentDataType.LONG, route.activation().activationId());
                access.markUnsaved();
            }
        }
    }

    private void adjustGeneratedStructures(RegistryAccess registryAccess, ChunkAccess access,
                                           Map<Structure, StructureStart> previousStarts,
                                           Map<Structure, NativeStructureStartPlan> configuredStarts,
                                           StructureTemplateManager templateManager) {
        Registry<Structure> registry = registryAccess.lookupOrThrow(Registries.STRUCTURE);
        ChunkPos chunkPos = access.getPos();
        for (Map.Entry<Structure, StructureStart> entry : access.getAllStarts().entrySet()) {
            Structure structure = entry.getKey();
            StructureStart start = entry.getValue();
            if (!start.isValid() || previousStarts.get(structure) == start) {
                continue;
            }
            Identifier id = registry.getKey(structure);
            String structureId = id == null ? null : id.toString();
            if (structureId == null) {
                throw NativeStructureGenerationException.failure(
                        "resolution", null, chunkPos.x(), chunkPos.z());
            }
            BoundingBox footprint = start.getBoundingBox();
            if (!engine.getComplex().allowsNewGenerationFootprint(
                    footprint.minX(),
                    footprint.minZ(),
                    footprint.maxX(),
                    footprint.maxZ())) {
                access.setStartForStructure(structure, StructureStart.INVALID_START);
                if (configuredStarts.containsKey(structure)) {
                    NativeStructureOwnershipStore.discard(
                            engine, structureId, chunkPos.x(), chunkPos.z());
                }
                continue;
            }
            if (configuredStarts.containsKey(structure)) {
                continue;
            }
            boolean undergroundStep = NativeStructureVegetationClearer.isUndergroundStep(structure.step());
            IrisNativeStructureDecision decision;
            try {
                decision = NativeStructureGenerationPolicy.resolve(engine,
                        structureId, undergroundStep);
            } catch (Throwable error) {
                throw NativeStructureGenerationException.failure(
                        "policy resolution", structureId, chunkPos.x(), chunkPos.z(), error);
            }
            if (!decision.generate()) {
                access.setStartForStructure(structure, StructureStart.INVALID_START);
                continue;
            }
            try {
                NativeStructureVerticalPlacer.applyVerticalPlacement(
                        start,
                        structureId,
                        decision.yShift(),
                        getSeaLevel(),
                        access.getMinY(),
                        access.getMinY() + access.getHeight(),
                        undergroundStep,
                        decision.preserveSourceY(),
                        decision.yBand(),
                        (x, z) -> Engine.hostHeight(engine, x, z, true) + engine.getMinHeight());
                StructureStart wrapped = NativeStructureReferenceEnvelope.wrapForPublication(
                        start, structure, start.getReferences(),
                        NativeStructureTerrainIntegrator.resolveNativeTerrain(start, decision.terrain()),
                        structureId);
                access.setStartForStructure(structure, wrapped);
            } catch (Throwable error) {
                throw NativeStructureGenerationException.failure(
                        "vertical adjustment", structureId, chunkPos.x(), chunkPos.z(), error);
            }
        }
    }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> holderlookup, RandomState randomstate, long i, SpigotWorldConfig conf) {
        return delegate.createState(holderlookup, randomstate, i, conf);
    }

    void retainStudioStructureState(
            ServerLevel level,
            ChunkMap chunkMap,
            ChunkGeneratorStructureState structureState
    ) {
        requireCurrentStructureOwner(level, chunkMap);
        StudioStructureState retained = new StudioStructureState(
                level,
                chunkMap,
                Objects.requireNonNull(structureState, "Studio native structure state"));
        if (!retainedStudioStructureState.compareAndSet(null, retained)) {
            throw new IllegalStateException("Studio native structure state is already retained.");
        }
    }

    StudioStructureState retainedStudioStructureState(ServerLevel level, ChunkMap chunkMap) {
        StudioStructureState retained = retainedStudioStructureState.get();
        if (retained == null) {
            return null;
        }
        requireCurrentStructureOwner(level, chunkMap);
        if (retained.level() != level || retained.chunkMap() != chunkMap) {
            throw new IllegalStateException("Retained Studio native structure state belongs to another world runtime.");
        }
        if (level.getChunkSource().getGeneratorState() != retained.structureState()) {
            throw new IllegalStateException("Studio native structure state is no longer current.");
        }
        return retained;
    }

    void claimStudioStructureState(StudioStructureState retained) {
        if (!retainedStudioStructureState.compareAndSet(retained, null)) {
            throw new IllegalStateException("Studio native structure state changed before activation began.");
        }
    }

    void abandonStudioStructureState() {
        retainedStudioStructureState.set(null);
    }

    CompletableFuture<Void> initializeAndPublishStructureState(
            ChunkGeneratorStructureState structureState,
            StructureStatePublisher publisher
    ) {
        return startStructureStateBootstrap(
                structureState,
                NO_OP,
                () -> publishStructureState(publisher));
    }

    CompletableFuture<Void> activateStudioStructureState(StudioStructureState retained) {
        Objects.requireNonNull(retained, "Retained Studio native structure state");
        return startStructureStateBootstrap(
                retained.structureState(),
                () -> {
                    StudioStructureState current = retainedStudioStructureState(
                            retained.level(), retained.chunkMap());
                    if (current != retained) {
                        throw new IllegalStateException("Studio native structure state changed before activation.");
                    }
                    claimStudioStructureState(retained);
                },
                NO_OP);
    }

    private CompletableFuture<Void> startStructureStateBootstrap(
            ChunkGeneratorStructureState structureState,
            Runnable claim,
            Runnable activation
    ) {
        if (!(engine instanceof IrisEngine irisEngine)) {
            throw new IllegalStateException("Native structure bootstrap requires an IrisEngine runtime.");
        }
        AtomicReference<CompletableFuture<Void>> registeredCompletion = new AtomicReference<>();
        CompletableFuture<Void> completion = irisEngine.startNativeStructureBootstrap(
                claim,
                () -> {
                    CompletableFuture<Void> rings = initializeStructureState(structureState);
                    registeredCompletion.set(rings);
                    return rings;
                },
                () -> {
                    CompletableFuture<Void> rings = Objects.requireNonNull(
                            registeredCompletion.get(),
                            "Registered native structure ring completion");
                    if (rings.isCompletedExceptionally()) {
                        throw new IllegalStateException(
                                "Minecraft native structure ring bootstrap failed before activation.");
                    }
                    activation.run();
                });
        completion.whenComplete((ignored, failure) -> {
            if (failure != null) {
                Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                        ? failure.getCause()
                        : failure;
                IrisLogging.reportError("Native structure ring bootstrap failed for world '"
                        + runtimeLevel.getWorld().getName() + "'.", cause);
            }
        });
        return completion;
    }

    private CompletableFuture<Void> initializeStructureState(ChunkGeneratorStructureState structureState) {
        Map<?, ?> ringPositions = structureRingPositions(structureState);
        structureState.ensureStructuresGenerated();
        return structureRingCompletion(ringPositions);
    }

    private Map<?, ?> structureRingPositions(ChunkGeneratorStructureState structureState) {
        try {
            Field field = structureRingPositionsField();
            field.setAccessible(true);
            Object value = field.get(structureState);
            if (!(value instanceof Map<?, ?> ringPositions)) {
                throw new IllegalStateException("Minecraft native structure ring state is unavailable.");
            }
            return ringPositions;
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Could not bind Minecraft native structure ring completions.", e);
        }
    }

    private CompletableFuture<Void> structureRingCompletion(Map<?, ?> ringPositions) {
        List<CompletableFuture<?>> futures = new ArrayList<>(ringPositions.size());
        for (Object candidate : ringPositions.values()) {
            if (candidate instanceof CompletableFuture<?> future) {
                futures.add(future);
            } else {
                throw new IllegalStateException(
                        "Minecraft native structure ring completion is not a future.");
            }
        }
        CompletableFuture<?>[] completions = futures.toArray(new CompletableFuture<?>[0]);
        return CompletableFuture.allOf(completions);
    }

    private Field structureRingPositionsField() {
        List<Field> candidates = new ArrayList<>(1);
        for (Field field : ChunkGeneratorStructureState.class.getDeclaredFields()) {
            if (!Map.class.isAssignableFrom(field.getType())) {
                continue;
            }
            Type genericType = field.getGenericType();
            if (!(genericType instanceof ParameterizedType parameterizedType)) {
                continue;
            }
            Type[] arguments = parameterizedType.getActualTypeArguments();
            if (arguments.length == 2
                    && arguments[1].getTypeName().contains(CompletableFuture.class.getName())) {
                candidates.add(field);
            }
        }
        if (candidates.size() != 1) {
            throw new IllegalStateException("Expected one Minecraft native structure ring-future map, found "
                    + candidates.size() + ".");
        }
        return candidates.getFirst();
    }

    private void requireCurrentStructureOwner(ServerLevel level, ChunkMap chunkMap) {
        if (runtimeLevel != level
                || level.getChunkSource().chunkMap != chunkMap
                || level.getChunkSource().getGenerator() != this) {
            throw new IllegalStateException("Iris native structure state no longer belongs to the active world runtime.");
        }
    }

    private void publishStructureState(StructureStatePublisher publisher) {
        try {
            publisher.publish();
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Could not publish Minecraft native structure state.", e);
        }
    }

    @Override
    public void createReferences(WorldGenLevel generatoraccessseed, StructureManager structuremanager, ChunkAccess ichunkaccess) {
        if (platformGenerator != null && !platformGenerator.shouldGenerateStructures()) {
            return;
        }
        if (runtimeLevel.getChunkSource().getGenerator() != this) {
            return;
        }
        ChunkPos chunkPos = ichunkaccess.getPos();
        try (BukkitChunkGenerator.GenerationStagePermit stage = requireGenerationStage("bukkit_nms_create_references");
             GenerationHistoryRuntimeRouter.RuntimeRoute route = openHistoryRoute(
                     chunkPos.x(), chunkPos.z(), "bukkit_nms_create_references");
             GenerationHistoryRuntimeRouter.RuntimeRoute.RuntimeScope runtimeScope = openHistoryRuntimeScope(route);
             GenerationSessionLease lease = requireGenerationLease("bukkit_nms_create_references");
             IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            NativeStructureReferenceRepair.createReferences(
                    engine, generatoraccessseed, structuremanager, ichunkaccess);
        }
    }

    @Override
    public CompletableFuture<ChunkAccess> createBiomes(RandomState randomstate, Blender blender, StructureManager structuremanager, ChunkAccess ichunkaccess) {
        ChunkPos chunkPos = ichunkaccess.getPos();
        try (BukkitChunkGenerator.GenerationStagePermit stage = requireGenerationStage("bukkit_nms_create_biomes");
             GenerationHistoryRuntimeRouter.RuntimeRoute route = openHistoryRoute(
                     chunkPos.x(), chunkPos.z(), "bukkit_nms_create_biomes");
             GenerationHistoryRuntimeRouter.RuntimeRoute.RuntimeScope runtimeScope = openHistoryRuntimeScope(route);
             GenerationSessionLease lease = requireGenerationLease("bukkit_nms_create_biomes");
             IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            customBiomeSource.prepareVisibleBiomeBatch();
            IrisDimensionCarvingResolver.State resolverState = new IrisDimensionCarvingResolver.State();
            ichunkaccess.fillBiomesFromNoise(
                    (x, y, z, sampler) -> customBiomeSource.getVisibleNoiseBiomeWithActiveGenerationLease(
                            x, y, z, sampler, resolverState),
                    randomstate.sampler());
            return CompletableFuture.completedFuture(ichunkaccess);
        }
    }

    @Override
    public void buildSurface(WorldGenRegion regionlimitedworldaccess, StructureManager structuremanager, RandomState randomstate, ChunkAccess ichunkaccess) {
        ChunkPos chunkPos = ichunkaccess.getPos();
        try (BukkitChunkGenerator.GenerationStagePermit stage = requireGenerationStage("bukkit_nms_build_surface");
             GenerationHistoryRuntimeRouter.RuntimeRoute route = openHistoryRoute(
                     chunkPos.x(), chunkPos.z(), "bukkit_nms_build_surface");
             GenerationHistoryRuntimeRouter.RuntimeRoute.RuntimeScope runtimeScope = openHistoryRuntimeScope(route);
             GenerationSessionLease lease = requireGenerationLease("bukkit_nms_build_surface");
             IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            if (!engine.getComplex().allowsMantleChunkWrite(chunkPos.x(), chunkPos.z())) {
                return;
            }
            delegate.buildSurface(regionlimitedworldaccess, structuremanager, randomstate, ichunkaccess);
        }
    }

    @Override
    public void applyCarvers(WorldGenRegion regionlimitedworldaccess, long seed, RandomState randomstate, BiomeManager biomemanager, StructureManager structuremanager, ChunkAccess ichunkaccess) {
        ChunkPos chunkPos = ichunkaccess.getPos();
        try (BukkitChunkGenerator.GenerationStagePermit stage = requireGenerationStage("bukkit_nms_apply_carvers");
             GenerationHistoryRuntimeRouter.RuntimeRoute route = openHistoryRoute(
                     chunkPos.x(), chunkPos.z(), "bukkit_nms_apply_carvers");
             GenerationHistoryRuntimeRouter.RuntimeRoute.RuntimeScope runtimeScope = openHistoryRuntimeScope(route);
             GenerationSessionLease lease = requireGenerationLease("bukkit_nms_apply_carvers");
             IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            if (!engine.getComplex().allowsMantleChunkWrite(chunkPos.x(), chunkPos.z())) {
                return;
            }
            delegate.applyCarvers(regionlimitedworldaccess, seed, randomstate, biomemanager, structuremanager, ichunkaccess);
        }
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomstate, StructureManager structuremanager, ChunkAccess ichunkaccess) {
        ChunkPos chunkPos = ichunkaccess.getPos();
        BukkitChunkGenerator.GenerationStagePermit stage = requireGenerationStage("bukkit_nms_chunk_pipeline");
        GenerationHistoryRuntimeRouter.RuntimeRoute route;
        try {
            route = openHistoryRoute(chunkPos.x(), chunkPos.z(), "bukkit_nms_chunk_pipeline");
        } catch (RuntimeException | Error failure) {
            stage.close();
            throw failure;
        }
        GenerationSessionLease lease;
        try {
            lease = requireGenerationLease("bukkit_nms_chunk_pipeline");
        } catch (RuntimeException | Error failure) {
            closeHistoryRoute(route, failure);
            stage.close();
            throw failure;
        }
        try {
            CompletableFuture<ChunkAccess> delegatePipeline;
            try (GenerationHistoryRuntimeRouter.RuntimeRoute.RuntimeScope runtimeScope = openHistoryRuntimeScope(route);
                 IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
                delegatePipeline = delegate.fillFromNoise(
                        blender, randomstate, structuremanager, ichunkaccess);
            }
            CompletableFuture<ChunkAccess> pipeline = delegatePipeline
                    .thenApply(filled -> {
                        try (GenerationHistoryRuntimeRouter.RuntimeRoute.RuntimeScope runtimeScope =
                                     openHistoryRuntimeScope(route);
                             IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
                            claimGeneratedSemantics(route, filled, engine.getMinHeight());
                            persistNaturalTerrain(filled, route);
                            primeWorldgenHeightmaps(filled);
                            return filled;
                        }
                    });
            CompletableFuture<ChunkAccess> completion = new CompletableFuture<>();
            pipeline.whenComplete((filled, failure) -> {
                boolean cancelled = isCancellationFailure(failure);
                Throwable completionFailure = closeNoisePipelineResources(
                        failure, lease, route, stage);
                if (completionFailure == null) {
                    completion.complete(filled);
                } else if (cancelled) {
                    completion.cancel(false);
                } else {
                    completion.completeExceptionally(completionFailure);
                }
            });
            lease.detachThread();
            if (route != null) {
                route.detachThread();
            }
            return completion;
        } catch (RuntimeException | Error failure) {
            lease.close();
            closeHistoryRoute(route, failure);
            stage.close();
            throw failure;
        }
    }

    private static void persistNaturalTerrain(ChunkAccess chunk, GenerationHistoryRuntimeRouter.RuntimeRoute route) {
        if (route == null || route.naturalTerrain().isEmpty()) {
            return;
        }
        try {
            SavedTerrainChunk terrain = route.naturalTerrain().orElseThrow();
            chunk.persistentDataContainer.set(new NamespacedKey("iris", "natural_terrain"), PersistentDataType.BYTE_ARRAY,
                    NativeTerrainReceipt.encode(terrain, route.activation().activationId(), route.epoch().epochId()));
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to persist natural terrain for chunk " + chunk.getPos(), failure);
        }
    }

    private static Throwable closeNoisePipelineResources(
            Throwable failure,
            GenerationSessionLease lease,
            GenerationHistoryRuntimeRouter.RuntimeRoute route,
            BukkitChunkGenerator.GenerationStagePermit stage
    ) {
        Throwable result = failure;
        try {
            lease.close();
        } catch (Throwable closeFailure) {
            result = appendFailure(result, closeFailure);
        }
        try {
            if (route != null) {
                route.close();
            }
        } catch (Throwable closeFailure) {
            result = appendFailure(result, closeFailure);
        }
        try {
            stage.close();
        } catch (Throwable closeFailure) {
            result = appendFailure(result, closeFailure);
        }
        return result;
    }

    private static void closeHistoryRoute(
            GenerationHistoryRuntimeRouter.RuntimeRoute route,
            Throwable failure
    ) {
        if (route == null) {
            return;
        }
        try {
            route.close();
        } catch (Throwable closeFailure) {
            if (failure != closeFailure) {
                failure.addSuppressed(closeFailure);
            }
        }
    }

    private static Throwable appendFailure(Throwable failure, Throwable closeFailure) {
        if (failure == null) {
            return closeFailure;
        }
        if (failure != closeFailure) {
            failure.addSuppressed(closeFailure);
        }
        return failure;
    }

    private static boolean isCancellationFailure(Throwable failure) {
        Throwable current = failure;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current instanceof CancellationException;
    }

    private void primeWorldgenHeightmaps(ChunkAccess chunkAccess) {
        if (platformGenerator != null && platformGenerator.usesFlatStudioTerrain()) {
            primeAuthoringHeightmaps(chunkAccess);
            return;
        }
        WorldgenTerrainHeightmaps.primeTerrain(chunkAccess, worldgenSurfaceHeight(), worldgenFloorHeight());
    }

    static void primeAuthoringHeightmaps(ChunkAccess chunkAccess) {
        Heightmap.primeHeightmaps(chunkAccess, AUTHORING_HEIGHTMAPS);
    }

    private IntBinaryOperator worldgenSurfaceHeight() {
        return (x, z) -> engine.getHeight(x, z, false) + runtimeMinY + 1;
    }

    private IntBinaryOperator worldgenFloorHeight() {
        return (x, z) -> engine.getHeight(x, z, true) + runtimeMinY + 1;
    }

    private IntBinaryOperator hostWorldgenSurfaceHeight() {
        return (x, z) -> Engine.hostHeight(engine, x, z, false) + runtimeMinY + 1;
    }

    private IntBinaryOperator hostWorldgenFloorHeight() {
        return (x, z) -> Engine.hostHeight(engine, x, z, true) + runtimeMinY + 1;
    }

    @Override
    public WeightedList<MobSpawnSettings.SpawnerData> getMobsAt(Holder<Biome> holder, StructureManager structuremanager, MobCategory enumcreaturetype, BlockPos blockposition) {
        return EngineLifecycleTasks.call(engine, "bukkit_nms_mob_spawns",
                () -> getAdmittedMobsAt(holder, structuremanager, enumcreaturetype, blockposition),
                WeightedList.of(List.of()));
    }

    private WeightedList<MobSpawnSettings.SpawnerData> getAdmittedMobsAt(
            Holder<Biome> holder, StructureManager structuremanager, MobCategory enumcreaturetype, BlockPos blockposition) {
        NativeBiomeSpawnSelection selection = NativeBiomeSpawnSelection.at(
                engine, blockposition.getX(), blockposition.getY(), blockposition.getZ(),
                holder.unwrapKey().map(key -> key.identifier().toString()).orElse(""));
        if (selection.mode() == NativeBiomeSpawnSelection.Mode.LOADING) {
            return WeightedList.of(List.of());
        }
        try (GenerationHistoryRuntimeRouter.CoordinateScope route = openHistoryCoordinateScope(
                     blockposition.getX(), blockposition.getZ(), "bukkit_nms_mob_spawns")) {
            return getMobsAtWithActiveRuntime(holder, structuremanager, enumcreaturetype, blockposition, selection);
        }
    }

    private WeightedList<MobSpawnSettings.SpawnerData> getMobsAtWithActiveRuntime(
            Holder<Biome> holder,
            StructureManager structuremanager,
            MobCategory enumcreaturetype,
            BlockPos blockposition,
            NativeBiomeSpawnSelection selection
    ) {
        Holder<Biome> vanillaSpawnBiome = switch (selection.mode()) {
            case RETAINED -> customBiomeSource.getRetainedVanillaSpawnBiome(selection.derivativeKey());
            case CURRENT -> customBiomeSource.getVanillaSpawnBiome(holder);
            case NONE, LOADING -> null;
        };
        if (vanillaSpawnBiome == null) {
            return delegate.getMobsAt(holder, structuremanager, enumcreaturetype, blockposition);
        }

        WeightedList<MobSpawnSettings.SpawnerData> vanillaSpawns = vanillaSpawnBiome.value().getMobSettings().getMobs(enumcreaturetype);
        WeightedList<MobSpawnSettings.SpawnerData> resolvedSpawns = delegate.getMobsAt(
                vanillaSpawnBiome, structuremanager, enumcreaturetype, blockposition);
        if (resolvedSpawns != vanillaSpawns) {
            return resolvedSpawns;
        }

        WeightedList<MobSpawnSettings.SpawnerData> explicitSpawns = holder.value().getMobSettings().getMobs(enumcreaturetype);
        if (explicitSpawns.isEmpty()) {
            return vanillaSpawns;
        }
        if (vanillaSpawns.isEmpty()) {
            return explicitSpawns;
        }

        int spawnRuntimeId = engine.getCacheID();
        SpawnTableKey key = new SpawnTableKey(spawnRuntimeId, holder.value(), vanillaSpawnBiome.value(), enumcreaturetype);
        return mergedSpawnTables.computeIfAbsent(key, ignored -> mergeSpawnTables(vanillaSpawns, explicitSpawns));
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel generatoraccessseed, ChunkAccess ichunkaccess, StructureManager structuremanager) {
        applyBiomeDecoration(generatoraccessseed, ichunkaccess, structuremanager, true);
    }

    @Override
    public void addDebugScreenInfo(List<String> list, RandomState randomstate, BlockPos blockposition) {
        delegate.addDebugScreenInfo(list, randomstate, blockposition);
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel generatoraccessseed, ChunkAccess ichunkaccess, StructureManager structuremanager, boolean vanilla) {
        ChunkPos chunkPos = ichunkaccess.getPos();
        try (BukkitChunkGenerator.GenerationStagePermit stage = requireGenerationStage("bukkit_nms_biome_decoration");
             GenerationHistoryRuntimeRouter.RuntimeRoute route = openHistoryRoute(
                     chunkPos.x(), chunkPos.z(), "bukkit_nms_biome_decoration");
             GenerationHistoryRuntimeRouter.RuntimeRoute.RuntimeScope runtimeScope = openHistoryRuntimeScope(route);
             GenerationSessionLease lease = requireGenerationLease("bukkit_nms_biome_decoration");
            IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            boolean flatStudioTerrain = platformGenerator != null && platformGenerator.usesFlatStudioTerrain();
            if (!flatStudioTerrain && !ichunkaccess.getPersistedStatus().isOrAfter(ChunkStatus.FEATURES)) {
                placeVanillaStructures(generatoraccessseed, ichunkaccess, structuremanager);
            }
            if (allowsRoutedDiscreteGeneration(ichunkaccess, ChunkStatus.FEATURES)
                    && NativeGenerationWriteGuard.allowsDecoration(engine, generatoraccessseed, chunkPos)) {
                if (!flatStudioTerrain) {
                    // Bind-time equivalent for Bukkit: the table is built on the first decorated chunk, which is where a
                    // feature-order cycle is reported once and degraded to features-off.
                    importedFeatures.prepare(generatoraccessseed);
                }
                addVanillaDecorations(generatoraccessseed, ichunkaccess, structuremanager);
                if (!flatStudioTerrain) {
                    // Vanilla's placed-feature pass, on THIS thread. The delegate is still called with
                    // addVanillaDecorations=false below, so the vanilla half never runs twice. Inert unless the
                    // dimension set importedFeatures.enabled.
                    importedFeatures.run(generatoraccessseed, ichunkaccess, this);
                }
                delegate.applyBiomeDecoration(generatoraccessseed, ichunkaccess, structuremanager, false);
            }
            claimGeneratedSemantics(route, ichunkaccess, engine.getMinHeight());
        }
    }

    /**
     * Iris custom biomes carry no features in their datapack JSON by design; when importedFeatures is on they
     * inherit the generation settings of the vanilla biome their Iris biome derives from. This is the gate
     * {@code BiomeFilter} consults, so it has to agree with the feature pass. With the control off this is
     * exactly the inherited behaviour.
     */
    @Override
    public BiomeGenerationSettings getBiomeGenerationSettings(Holder<Biome> holder) {
        BiomeGenerationSettings imported = importedFeatures.generationSettings(holder);
        return imported == null ? super.getBiomeGenerationSettings(holder) : imported;
    }

    private void placeVanillaStructures(WorldGenLevel world, ChunkAccess chunk, StructureManager structureManager) {
        if (!structureManager.shouldGenerateStructures()) {
            ChunkPos disabledChunk = chunk.getPos();
            throw new IllegalStateException("Iris cannot generate native structures in chunk "
                    + disabledChunk.x() + "," + disabledChunk.z()
                    + " because structure generation is disabled outside the pack; enable native structure generation "
                    + "and deny families through importedStructures.disabled or complete keys through importedStructures.disabledExact");
        }
        ChunkPos chunkPos = chunk.getPos();
        SectionPos sectionPos = SectionPos.of(chunkPos, world.getMinSectionY());
        BlockPos origin = sectionPos.origin();
        Registry<Structure> registry = world.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        List<List<Structure>> byStep = structuresByStep(registry);
        WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
        long decoSeed = random.setDecorationSeed(world.getSeed(), origin.getX(), origin.getZ());
        BoundingBox area = writableArea(chunk);
        int steps = GenerationStep.Decoration.values().length;
        List<NativePlacementGroup> placementGroups = new ArrayList<>();
        List<StructureStart> heightmapStarts = new ArrayList<>();
        List<StructureStart> vegetationTargets = new ArrayList<>();
        List<NativeStructureTerrainIntegrator.TerrainTarget> terrainTargets = new ArrayList<>();
        for (int step = 0; step < steps; step++) {
            int index = 0;
            for (Structure structure : byStep.get(step)) {
                Object id = registry.getKey(structure);
                String structureId = id == null ? null : id.toString();
                if (structureId == null) {
                    throw NativeStructureGenerationException.failure(
                            "resolution", null, chunkPos.x(), chunkPos.z());
                }
                try {
                    IrisNativeStructureDecision sourceDecision = NativeStructureGenerationPolicy.resolve(engine,
                            structureId, NativeStructureVegetationClearer.isUndergroundStep(structure.step()));
                    List<StructureStart> starts = structureManager.startsForStructure(sectionPos, structure);
                    List<NativePlacement> resolvedPlacements = new ArrayList<>(starts.size());
                    for (StructureStart start : starts) {
                        BoundingBox footprint = start.getBoundingBox();
                        if (!isHistoricalStructureStart(world, start)
                                && !engine.getComplex().allowsNewGenerationFootprint(
                                footprint.minX(),
                                footprint.minZ(),
                                footprint.maxX(),
                                footprint.maxZ())) {
                            continue;
                        }
                        NativeStructureOwnershipRecord ownership =
                                NativeStructureOwnershipRecovery.resolve(
                                        engine, world.getLevel(), structureId, structure, start);
                        IrisNativeStructureDecision decision =
                                ownership == null ? sourceDecision : ownership.restoredDecision();
                        if (!decision.generate()) {
                            continue;
                        }
                        resolvedPlacements.add(new NativePlacement(start, decision));
                        heightmapStarts.add(start);
                        terrainTargets.add(new NativeStructureTerrainIntegrator.TerrainTarget(
                                structureId, start,
                                NativeStructureTerrainIntegrator.resolveNativeTerrain(
                                        start, decision.terrain())));
                        vegetationTargets.add(start);
                    }
                    if (!resolvedPlacements.isEmpty()) {
                        placementGroups.add(new NativePlacementGroup(
                                structureId, index, step, List.copyOf(resolvedPlacements)));
                    }
                } catch (Throwable error) {
                    throw NativeStructureGenerationException.failure(
                            "resolution", structureId, chunkPos.x(), chunkPos.z(), error);
                }
                index++;
            }
        }
        try {
            WorldgenTerrainHeightmaps.primeStructurePlacement(
                    world, chunkPos, heightmapStarts,
                    hostWorldgenSurfaceHeight(), hostWorldgenFloorHeight());
        } catch (Throwable error) {
            throw NativeStructureGenerationException.failure(
                    "heightmap priming", nativeStructureBatchContext(placementGroups),
                    chunkPos.x(), chunkPos.z(), error);
        }
        IrisStaticObjectLayer staticObjects = engine.getDimension().getStaticObjectLayer(engine.getData());
        Predicate<BlockPos> protectedPosition = nativeStructureProtection(staticObjects);
        WorldGenLevel boundedWorld = NativeStructureWorldgenAccess.create(
                world, chunkPos, hostWorldgenSurfaceHeight(), hostWorldgenFloorHeight(),
                engine.getDimensionStackContext() != null,
                protectedPosition);
        try {
            NativeStructureVegetationClearer.clearIntersectingVegetation(
                    boundedWorld, chunk, area, vegetationTargets);
        } catch (Throwable error) {
            throw NativeStructureGenerationException.failure(
                    "vegetation cleanup", nativeStructureBatchContext(placementGroups),
                    chunkPos.x(), chunkPos.z(), error);
        }
        NativeStructureSurfaceFitter.SurfaceTerrainPlan surfaceTerrainPlan;
        try {
            surfaceTerrainPlan = NativeStructureSurfaceFitter.prepareSurfaceStructures(
                    boundedWorld, area, terrainTargets,
                    (x, z) -> Engine.hostHeight(engine, x, z, true) + engine.getMinHeight());
            surfaceTerrainPlan.primeHeightmaps(chunk);
        } catch (Throwable error) {
            throw NativeStructureGenerationException.failure(
                    "terrain integration", nativeStructureBatchContext(placementGroups),
                    chunkPos.x(), chunkPos.z(), error);
        }
        try {
            NativeStructurePostProcessor.prepareTerrain(
                    boundedWorld, area, terrainTargets, this::resolvePaletteBlock);
        } catch (Throwable error) {
            throw NativeStructureGenerationException.failure(
                    "terrain preparation", nativeStructureBatchContext(placementGroups),
                    chunkPos.x(), chunkPos.z(), error);
        }
        for (NativePlacementGroup group : placementGroups) {
            random.setFeatureSeed(decoSeed, group.featureIndex(), group.step());
            try {
                for (NativePlacement placement : group.placements()) {
                    placeVanillaStructure(boundedWorld, structureManager, random, area, chunkPos,
                            group.structureId(), placement.start(), placement.decision());
                }
            } catch (Throwable error) {
                throw NativeStructureGenerationException.failure(
                        "placement", group.structureId(), chunkPos.x(), chunkPos.z(), error);
            }
        }
        try {
            NativeStructureSurfaceFitter.repairVacuumFoundations(
                    boundedWorld, area, surfaceTerrainPlan);
        } catch (Throwable error) {
            throw NativeStructureGenerationException.failure(
                    "foundation repair", nativeStructureBatchContext(placementGroups),
                    chunkPos.x(), chunkPos.z(), error);
        }
    }

    private boolean isHistoricalStructureStart(WorldGenLevel world, StructureStart start) {
        ChunkPos origin = start.getChunkPos();
        if (!engine.getComplex().allowsMantleChunkWrite(origin.x(), origin.z())) {
            return true;
        }
        ChunkAccess source = world.getChunk(origin.x(), origin.z(), ChunkStatus.EMPTY, false);
        if (source == null) {
            return false;
        }
        long activation = source.persistentDataContainer.getOrDefault(
                new NamespacedKey("iris", "structure_activation"), PersistentDataType.LONG, 0L);
        return NativeGenerationWriteGuard.isHistoricalStructure(engine, activation);
    }

    private static String nativeStructureBatchContext(List<NativePlacementGroup> placementGroups) {
        if (placementGroups.isEmpty()) {
            return "<no resolved native structures>";
        }
        StringBuilder context = new StringBuilder("[");
        for (int i = 0; i < placementGroups.size(); i++) {
            if (i > 0) {
                context.append(", ");
            }
            context.append(placementGroups.get(i).structureId());
        }
        return context.append(']').toString();
    }

    private void placeVanillaStructure(WorldGenLevel world, StructureManager structureManager, WorldgenRandom random,
                                       BoundingBox area, ChunkPos chunkPos, String structureId, StructureStart start,
                                       IrisNativeStructureDecision decision) {
        WorldGenLevel boundedWorld = world instanceof NativeStructureWorldgenAccess ? world : NativeStructureWorldgenAccess.create(
                world,
                chunkPos,
                hostWorldgenSurfaceHeight(),
                hostWorldgenFloorHeight(),
                engine.getDimensionStackContext() != null,
                nativeStructureProtection(
                        engine.getDimension().getStaticObjectLayer(engine.getData())));
        world.setCurrentlyGenerating(() -> "Iris native structure " + structureId);
        try {
            NativeStructurePostProcessor.place(boundedWorld, structureManager, this, random, area, chunkPos,
                    structureId, start, decision, this::resolvePaletteBlock,
                    (x, z) -> Engine.hostHeight(engine, x, z, true) + engine.getMinHeight());
        } finally {
            world.setCurrentlyGenerating(null);
        }
    }

    private Predicate<BlockPos> nativeStructureProtection(IrisStaticObjectLayer staticObjects) {
        DimensionStackContext stackContext = engine.getDimensionStackContext();
        int minimumY = engine.getMinHeight();
        Map<Long, DimensionStackLayout> layouts = new ConcurrentHashMap<>();
        return position -> {
            if (!staticObjects.isEmpty() && staticObjects.contains(
                    position.getX(), position.getY() - minimumY, position.getZ())) {
                return true;
            }
            if (stackContext == null) {
                return false;
            }
            long columnKey = ((long) position.getX() << 32)
                    ^ (position.getZ() & 0xFFFFFFFFL);
            DimensionStackLayout layout = layouts.computeIfAbsent(
                    columnKey,
                    ignored -> stackContext.sample(position.getX(), position.getZ())
            );
            return layout.isHostFeatureProtectedY(position.getY() - minimumY);
        };
    }

    private List<List<Structure>> structuresByStep(Registry<Structure> registry) {
        int runtimeId = engine.getCacheID();
        StructureStepCache cached = structureStepCache;
        if (cached != null && cached.runtimeId() == runtimeId && cached.registry() == registry) {
            return cached.structures();
        }
        synchronized (this) {
            cached = structureStepCache;
            if (cached != null && cached.runtimeId() == runtimeId && cached.registry() == registry) {
                return cached.structures();
            }
            int steps = GenerationStep.Decoration.values().length;
            List<List<Structure>> grouped = new ArrayList<>(steps);
            for (int step = 0; step < steps; step++) {
                grouped.add(new ArrayList<>());
            }
            for (Structure structure : registry) {
                grouped.get(structure.step().ordinal()).add(structure);
            }
            for (int step = 0; step < steps; step++) {
                grouped.set(step, List.copyOf(grouped.get(step)));
            }
            List<List<Structure>> resolved = List.copyOf(grouped);
            structureStepCache = new StructureStepCache(runtimeId, registry, resolved);
            return resolved;
        }
    }

    private BlockState resolvePaletteBlock(IrisMaterialPalette palette, RNG rng, int x, int y, int z) {
        PlatformBlockState platformState = palette.get(rng, x, y, z, engine.getData());
        if (platformState == null || !(platformState.nativeHandle() instanceof BlockData blockData)) {
            throw new IllegalStateException("Configured native structure palette did not resolve a Bukkit block at "
                    + x + "," + y + "," + z);
        }
        if (blockData instanceof IrisCustomData customData) {
            blockData = customData.getBase();
        }
        if (blockData instanceof CraftBlockData craftBlockData) {
            return craftBlockData.getState();
        }
        throw new IllegalStateException("Configured native structure palette resolved unsupported Bukkit block data "
                + blockData.getClass().getName() + " at " + x + "," + y + "," + z);
    }

    private BoundingBox writableArea(ChunkAccess chunk) {
        ChunkPos cp = chunk.getPos();
        int i = cp.getMinBlockX();
        int j = cp.getMinBlockZ();
        int minY = chunk.getMinY() + 1;
        int maxY = chunk.getMinY() + chunk.getHeight() - 1;
        return new BoundingBox(i, minY, j, i + 15, maxY, j + 15);
    }

    @Override
    public void addVanillaDecorations(WorldGenLevel level, ChunkAccess chunkAccess, StructureManager structureManager) {
        ChunkPos chunkPos = chunkAccess.getPos();
        try (GenerationHistoryRuntimeRouter.CoordinateScope historyScope = openHistoryCoordinateScope(
                     chunkPos.getMinBlockX(), chunkPos.getMinBlockZ(), "bukkit_nms_heightmaps");
             GenerationSessionLease lease = engine.acquireGenerationLease("bukkit_nms_heightmaps");
             IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            SectionPos sectionPos = SectionPos.of(chunkAccess.getPos(), level.getMinSectionY());
            BlockPos blockPos = sectionPos.origin();

            primeWorldgenHeightmaps(chunkAccess);
            if (platformGenerator != null && platformGenerator.usesFlatStudioTerrain()) {
                return;
            }

            Heightmap motion = chunkAccess.getOrCreateHeightmapUnprimed(Heightmap.Types.MOTION_BLOCKING);
            Heightmap motionNoLeaves = chunkAccess.getOrCreateHeightmapUnprimed(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES);
            int minHeight = engine.getMinHeight();

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int wX = x + blockPos.getX();
                    int wZ = z + blockPos.getZ();

                    int terrainTop = engine.getHeight(wX, wZ, false) + minHeight + 1;
                    SET_HEIGHT.invoke(motion, x, z, terrainTop);
                    SET_HEIGHT.invoke(motionNoLeaves, x, z, terrainTop);
                }
            }

            Heightmap.primeHeightmaps(chunkAccess, ChunkStatus.FINAL_HEIGHTMAPS);
        } catch (GenerationSessionException e) {
            throw new IllegalStateException("Iris heightmap generation could not acquire its engine runtime.", e);
        }
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        ChunkPos center = region.getCenter();
        try (BukkitChunkGenerator.GenerationStagePermit stage = requireGenerationStage("bukkit_nms_spawn_original_mobs");
             GenerationHistoryRuntimeRouter.RuntimeRoute route = openHistoryRoute(
                     center.x(), center.z(), "bukkit_nms_spawn_original_mobs");
             GenerationHistoryRuntimeRouter.RuntimeRoute.RuntimeScope runtimeScope = openHistoryRuntimeScope(route);
             GenerationSessionLease lease = requireGenerationLease("bukkit_nms_spawn_original_mobs");
             IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            if (!allowsRoutedDiscreteGeneration(region.getChunk(center.x(), center.z()), ChunkStatus.SPAWN)) {
                return;
            }
            Holder<Biome> visibleBiome;
            if (engine.getDimensionStackContext() == null) {
                visibleBiome = region.getBiome(center.getWorldPosition().atY(region.getMaxY()));
            } else {
                visibleBiome = customBiomeSource.getVisibleSurfaceBiome(
                        center.getMinBlockX() + 8,
                        center.getMinBlockZ() + 8);
                if (visibleBiome == null) {
                    visibleBiome = region.getBiome(center.getWorldPosition().atY(region.getMaxY()));
                }
            }
            Holder<Biome> vanillaBiome = customBiomeSource.getVanillaSpawnBiome(visibleBiome);
            WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
            random.setDecorationSeed(region.getSeed(), center.getMinBlockX(), center.getMinBlockZ());
            NaturalSpawner.spawnMobsForChunkGeneration(
                    region, vanillaBiome == null ? visibleBiome : vanillaBiome, center, random);
        }
    }

    @Override
    public boolean test(long packedChunk) {
        return !engine.isClosing() && !engine.isClosed()
                && engine.getComplex().allowsMantleChunkWrite(ChunkPos.getX(packedChunk), ChunkPos.getZ(packedChunk));
    }

    private boolean allowsRoutedDiscreteGeneration(ChunkAccess chunk, ChunkStatus stage) {
        if (chunk.getPersistedStatus().isOrAfter(stage)) {
            return false;
        }
        ChunkPos chunkPos = chunk.getPos();
        return NativeGenerationWriteGuard.allowsPendingStage(engine, chunk, stage)
                || engine.getComplex().allowsNewGenerationChunk(chunkPos.x(), chunkPos.z());
    }

    private static WeightedList<MobSpawnSettings.SpawnerData> mergeSpawnTables(
            WeightedList<MobSpawnSettings.SpawnerData> vanillaSpawns,
            WeightedList<MobSpawnSettings.SpawnerData> explicitSpawns) {
        List<Weighted<MobSpawnSettings.SpawnerData>> entries = new ArrayList<>(
                vanillaSpawns.unwrap().size() + explicitSpawns.unwrap().size());
        Set<EntityType<?>> explicitTypes = new HashSet<>();
        for (Weighted<MobSpawnSettings.SpawnerData> entry : explicitSpawns.unwrap()) {
            explicitTypes.add(entry.value().type());
        }
        for (Weighted<MobSpawnSettings.SpawnerData> entry : vanillaSpawns.unwrap()) {
            if (!explicitTypes.contains(entry.value().type())) {
                entries.add(entry);
            }
        }
        entries.addAll(explicitSpawns.unwrap());
        return WeightedList.of(entries);
    }

    @Override
    public int getSpawnHeight(LevelHeightAccessor levelheightaccessor) {
        return delegate.getSpawnHeight(levelheightaccessor);
    }

    @Override
    public int getGenDepth() {
        return runtimeHeight;
    }

    @Override
    public int getBaseHeight(int i, int j, Heightmap.Types heightmap_type, LevelHeightAccessor levelheightaccessor, RandomState randomstate) {
        if (platformGenerator != null && platformGenerator.usesFlatStudioTerrain()) {
            return platformGenerator.getAuthoringBaseHeight();
        }
        try (GenerationHistoryRuntimeRouter.CoordinateScope route = openHistoryCoordinateScope(
                     i, j, "bukkit_nms_base_height");
             GenerationSessionLease lease = engine.acquireGenerationLease("bukkit_nms_base_height");
             IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            NativeTerrainHeightCache.Query query = new NativeTerrainHeightCache.Query(
                    engine.getCacheID(), i, j, heightmap_type,
                    levelheightaccessor.getMinY(), levelheightaccessor.getHeight());
            OptionalInt resolved = terrainHeights.resolvedHeight(query,
                    () -> resolvedBaseHeight(i, j, heightmap_type, levelheightaccessor));
            if (resolved.isPresent()) {
                return resolved.getAsInt();
            }
            boolean ignoreFluid = !heightmap_type.isOpaque().test(Blocks.WATER.defaultBlockState());
            int height = engine.getDimensionStackContext() == null
                    ? engine.getHeight(i, j, ignoreFluid)
                    : Engine.hostHeight(engine, i, j, ignoreFluid);
            return levelheightaccessor.getMinY() + height + 1;
        } catch (GenerationSessionException e) {
            throw new IllegalStateException("Iris base height query could not acquire its engine runtime.", e);
        }
    }

    private OptionalInt resolvedBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor) {
        Optional<TerrainBoundarySignature> resolved = engine.getComplex().resolvedTerrainColumn(x, z);
        return resolved.isPresent()
                ? OptionalInt.of(NativeTransitionColumn.height(resolved.get(), type, heightAccessor))
                : OptionalInt.empty();
    }

    @Override
    public NoiseColumn getBaseColumn(int i, int j, LevelHeightAccessor levelheightaccessor, RandomState randomstate) {
        if (platformGenerator != null && platformGenerator.usesFlatStudioTerrain()) {
            BlockState[] column = new BlockState[levelheightaccessor.getHeight()];
            int floorIndex = platformGenerator.getAuthoringFloorY() - levelheightaccessor.getMinY();
            BlockState floor = platformGenerator.isJigsawStudioActive() && JigsawStudioGenerator.isLightFloor(i, j)
                    ? Blocks.SMOOTH_STONE.defaultBlockState()
                    : Blocks.POLISHED_DEEPSLATE.defaultBlockState();
            for (int index = 0; index < column.length; index++) {
                column[index] = index == floorIndex
                        ? floor
                        : Blocks.AIR.defaultBlockState();
            }
            return new NoiseColumn(levelheightaccessor.getMinY(), column);
        }
        try (GenerationHistoryRuntimeRouter.CoordinateScope route = openHistoryCoordinateScope(
                     i, j, "bukkit_nms_base_column");
             GenerationSessionLease lease = engine.acquireGenerationLease("bukkit_nms_base_column");
             IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            Optional<TerrainBoundarySignature> resolved = engine.getComplex().resolvedTerrainColumn(i, j);
            if (resolved.isPresent()) {
                return NativeTransitionColumn.column(resolved.get(), levelheightaccessor);
            }
            boolean dimensionStack = engine.getDimensionStackContext() != null;
            int block = dimensionStack
                    ? Engine.hostHeight(engine, i, j, true)
                    : engine.getHeight(i, j, true);
            int water = dimensionStack
                    ? Engine.hostHeight(engine, i, j, false)
                    : engine.getHeight(i, j, false);
            BlockState[] column = new BlockState[levelheightaccessor.getHeight()];
            for (int k = 0; k < column.length; k++) {
                if (k <= block) {
                    column[k] = Blocks.STONE.defaultBlockState();
                } else if (k <= water) {
                    column[k] = Blocks.WATER.defaultBlockState();
                } else {
                    column[k] = Blocks.AIR.defaultBlockState();
                }
            }
            return new NoiseColumn(levelheightaccessor.getMinY(), column);
        } catch (GenerationSessionException e) {
            throw new IllegalStateException("Iris base column query could not acquire its engine runtime.", e);
        }
    }

    private GenerationSessionLease requireGenerationLease(String operation) {
        try {
            return engine.acquireGenerationLease(operation);
        } catch (GenerationSessionException exception) {
            throw new IllegalStateException("Iris " + operation + " could not acquire its engine runtime.", exception);
        }
    }

    private GenerationHistoryRuntimeRouter.RuntimeRoute openHistoryRoute(
            int chunkX,
            int chunkZ,
            String operation
    ) {
        if (!(engine instanceof IrisEngine irisEngine)) {
            return null;
        }
        GenerationHistoryRuntimeRouter router = irisEngine.getGenerationHistoryRuntimeRouter().orElse(null);
        if (router == null) {
            if (platformGenerator != null && platformGenerator.getGenerationHistory() != null) {
                throw new IllegalStateException("Iris " + operation
                        + " has no generation-history runtime router.");
            }
            return null;
        }
        try {
            return router.openRoute(chunkX, chunkZ);
        } catch (IOException failure) {
            throw new IllegalStateException("Iris " + operation
                    + " could not route generation history for chunk "
                    + chunkX + "," + chunkZ + ".", failure);
        }
    }

    private GenerationHistoryRuntimeRouter.CoordinateScope openHistoryCoordinateScope(
            int blockX,
            int blockZ,
            String operation
    ) {
        if (!(engine instanceof IrisEngine irisEngine)) {
            return null;
        }
        try {
            return irisEngine.openGenerationHistoryCoordinateScope(blockX, blockZ);
        } catch (IOException failure) {
            throw new IllegalStateException("Iris " + operation
                    + " could not route generation history at "
                    + blockX + "," + blockZ + ".", failure);
        }
    }

    private static GenerationHistoryRuntimeRouter.RuntimeRoute.RuntimeScope openHistoryRuntimeScope(
            GenerationHistoryRuntimeRouter.RuntimeRoute route
    ) {
        return route == null ? null : route.openRuntimeScope();
    }

    private static void claimGeneratedSemantics(
            GenerationHistoryRuntimeRouter.RuntimeRoute route,
            ChunkAccess chunk,
            int minimumY
    ) {
        if (route == null) {
            return;
        }
        try {
            BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
            route.claimGeneratedSemantics((x, y, z) -> {
                BlockState state = chunk.getBlockState(position.set(x, minimumY + y, z));
                return state.isAir() || state.liquid();
            });
        } catch (IOException failure) {
            throw new IllegalStateException("Iris could not durably claim generated chunk semantics.", failure);
        }
    }

    private BukkitChunkGenerator.GenerationStagePermit requireGenerationStage(String operation) {
        return platformGenerator == null
                ? BukkitChunkGenerator.GenerationStagePermit.noop()
                : platformGenerator.acquireGenerationStage(operation);
    }

    @Override
    public Optional<Identifier> getTypeNameForDataFixer() {
        return delegate.getTypeNameForDataFixer();
    }

    @Override
    public void validate() {
        delegate.validate();
    }

    static {
        List<Field> biomeSources = new ArrayList<>(1);
        for (Field field : ChunkGenerator.class.getDeclaredFields()) {
            if (!field.getType().equals(BiomeSource.class))
                continue;
            biomeSources.add(field);
        }
        if (biomeSources.size() != 1)
            throw new IllegalStateException("Expected exactly one BiomeSource field in ChunkGenerator, found "
                    + biomeSources.size() + " " + biomeSources.stream().map(Field::getName).toList());
        Field biomeSource = biomeSources.getFirst();

        List<Method> setHeights = new ArrayList<>(1);
        for (Method method : Heightmap.class.getDeclaredMethods()) {
            Class<?>[] types = method.getParameterTypes();
            if (!method.getName().equals("setHeight")
                    || !Arrays.equals(types, new Class<?>[]{int.class, int.class, int.class})
                    || !method.getReturnType().equals(void.class))
                continue;
            setHeights.add(method);
        }
        if (setHeights.size() != 1)
            throw new IllegalStateException("Expected exactly one Heightmap.setHeight(int,int,int) method, found "
                    + setHeights.size());
        Method setHeight = setHeights.getFirst();

        BIOME_SOURCE = new WrappedField<>(ChunkGenerator.class, biomeSource.getName());
        SET_HEIGHT = new WrappedReturningMethod<>(Heightmap.class, setHeight.getName(), setHeight.getParameterTypes());
    }

    private static ChunkGenerator edit(ChunkGenerator generator, BiomeSource source) {
        try {
            BIOME_SOURCE.set(generator, source);
            if (generator instanceof CustomChunkGenerator custom)
                BIOME_SOURCE.set(custom.getDelegate(), source);

            return generator;
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private record SpawnTableKey(int runtimeId, Biome biome, Biome vanillaBiome, MobCategory category) {
    }

    private record ReachableStructureCache(IrisDimension dimension, int runtimeId, Set<String> keys) {
    }

    private record StructureStepCache(int runtimeId, Registry<Structure> registry,
                                      List<List<Structure>> structures) {
    }

    record StudioStructureState(
            ServerLevel level,
            ChunkMap chunkMap,
            ChunkGeneratorStructureState structureState
    ) {
    }

    @FunctionalInterface
    interface StructureStatePublisher {
        void publish() throws IllegalAccessException;
    }

    private record NativePlacement(StructureStart start, IrisNativeStructureDecision decision) {
    }

    private record NativePlacementGroup(String structureId, int featureIndex, int step,
                                        List<NativePlacement> placements) {
    }

    private record NativeLocateCandidate(Holder<Structure> holder, String key) {
    }

    private record IrisNativeLocateSearch(Holder<Structure> holder, String structureId,
                                          NativeStructureLocatePersistence.Search search) {
    }
}
