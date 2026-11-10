package art.arcane.iris.core.nms.v26_2_R1;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.GenerationSessionException;
import art.arcane.iris.engine.framework.GenerationSessionLease;
import art.arcane.iris.core.modules.ModuleServices;
import art.arcane.iris.engine.framework.NativeStructureOwnershipRecord;
import art.arcane.iris.engine.framework.NativeStructureStartPlan;
import art.arcane.iris.engine.framework.NativeStructureGenerationPolicy;
import art.arcane.iris.engine.framework.NativeStructureLayouts;
import art.arcane.iris.engine.framework.nativefit.NativeStructureFitting;
import art.arcane.iris.engine.IrisEngine;
import art.arcane.iris.engine.mantle.DeferredPlacementRecords;
import art.arcane.iris.engine.platform.BukkitChunkGenerator;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.nativegen.NativeStructureFitBridge;
import art.arcane.iris.nativegen.NativeStructureGenerationException;
import art.arcane.iris.nativegen.NativeStructureStartInjector;
import art.arcane.iris.nativegen.NativeStructureReferenceEnvelope;
import art.arcane.iris.nativegen.NativeStructureLocateResults;
import art.arcane.iris.nativegen.NativeStructureLocatePersistence;
import art.arcane.iris.nativegen.NativeStructureOwnershipRecovery;
import art.arcane.iris.nativegen.NativeStructureReferenceRepair;
import art.arcane.iris.nativegen.NativeStructureVegetationClearer;
import art.arcane.iris.nativegen.NativeStructureVerticalPlacer;
import art.arcane.iris.nativegen.NativeStructureVanillaLocator;
import art.arcane.iris.nativegen.NativeStructureVolumeIndex;
import art.arcane.iris.nativegen.WorldgenTerrainHeightmaps;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.util.common.reflect.WrappedField;
import art.arcane.iris.util.common.reflect.WrappedReturningMethod;
import art.arcane.iris.util.project.context.IrisContext;
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
import org.bukkit.craftbukkit.generator.CustomChunkGenerator;
import org.spigotmc.SpigotWorldConfig;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntBinaryOperator;

public class IrisChunkGenerator extends CustomChunkGenerator {
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
    private volatile IrisDimension spawnTableDimension;
    private volatile int spawnTableRuntimeId;
    private final ImportedFeatureStage importedFeatures;
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
        installNativeStructureVolumeIndex(level);
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
        try (GenerationSessionLease lease = requireGenerationLease("bukkit_nms_structure_locate");
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
            if (!ModuleServices.startsStructure(engine, structureId)) {
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
            ModuleServices.StructureSearch bestResult = null;
            long bestDistance = Long.MAX_VALUE;
            for (IrisNativeLocateSearch search : searches) {
                ModuleServices.StructureSearch result = search.search().predict();
                if (result.outcome() == ModuleServices.StructureSearch.Outcome.SEARCH_LIMIT_REACHED) {
                    throw new IllegalStateException("Iris structure locate reached its safety limit for "
                            + search.structureId() + " within " + radius + " placement rings");
                }
                if (!result.found()) {
                    continue;
                }
                long dx = (long) result.x() - pos.getX();
                long dz = (long) result.z() - pos.getZ();
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
                    new BlockPos(bestResult.x(), bestResult.y(), bestResult.z()),
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
                    bestResult.x(), verified.ownership().locatorY(),
                    bestResult.z());
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
            if (!NativeStructureGenerationPolicy.generates(engine, key)) {
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
        try (BukkitChunkGenerator.GenerationStagePermit stage = requireGenerationStage("bukkit_nms_create_structures");
             GenerationSessionLease lease = requireGenerationLease("bukkit_nms_create_structures");
             IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
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
            if (configuredStarts.containsKey(structure)) {
                continue;
            }
            Identifier id = registry.getKey(structure);
            String structureId = id == null ? null : id.toString();
            if (structureId == null) {
                throw NativeStructureGenerationException.failure(
                        "resolution", null, chunkPos.x(), chunkPos.z());
            }
            boolean undergroundStep = NativeStructureVegetationClearer.isUndergroundStep(structure.step());
            boolean generates;
            try {
                generates = NativeStructureGenerationPolicy.generates(engine, structureId);
            } catch (Throwable error) {
                throw NativeStructureGenerationException.failure(
                        "policy resolution", structureId, chunkPos.x(), chunkPos.z(), error);
            }
            if (!generates) {
                access.setStartForStructure(structure, StructureStart.INVALID_START);
                continue;
            }
            try {
                IntBinaryOperator surface = (x, z) -> engine.getHeight(x, z, true) + engine.getMinHeight();
                int shift = NativeStructureLayouts.shift(engine, NativeStructureVerticalPlacer.placementOf(
                        start, structureId, undergroundStep, access.getMinY(), surface));
                NativeStructureVerticalPlacer.applyVerticalPlacement(
                        start,
                        structureId,
                        shift,
                        getSeaLevel(),
                        access.getMinY(),
                        access.getMinY() + access.getHeight(),
                        surface);
                StructureStart wrapped = NativeStructureReferenceEnvelope.wrapForPublication(
                        start, structure, start.getReferences(),
                        NativeStructureLayouts.reaches(engine, structureId, undergroundStep, null),
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
        try (BukkitChunkGenerator.GenerationStagePermit stage = requireGenerationStage("bukkit_nms_create_references");
             GenerationSessionLease lease = requireGenerationLease("bukkit_nms_create_references");
             IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            NativeStructureReferenceRepair.createReferences(
                    engine, generatoraccessseed, structuremanager, ichunkaccess);
        }
    }

    @Override
    public CompletableFuture<ChunkAccess> createBiomes(RandomState randomstate, Blender blender, StructureManager structuremanager, ChunkAccess ichunkaccess) {
        try (BukkitChunkGenerator.GenerationStagePermit stage = requireGenerationStage("bukkit_nms_create_biomes");
             GenerationSessionLease lease = requireGenerationLease("bukkit_nms_create_biomes");
             IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            customBiomeSource.prepareVisibleBiomeBatch();
            ichunkaccess.fillBiomesFromNoise(
                    (x, y, z, sampler) -> customBiomeSource.getVisibleNoiseBiomeWithActiveGenerationLease(
                            x, y, z, sampler),
                    randomstate.sampler());
            return CompletableFuture.completedFuture(ichunkaccess);
        }
    }

    @Override
    public void buildSurface(WorldGenRegion regionlimitedworldaccess, StructureManager structuremanager, RandomState randomstate, ChunkAccess ichunkaccess) {
        delegate.buildSurface(regionlimitedworldaccess, structuremanager, randomstate, ichunkaccess);
    }

    @Override
    public void applyCarvers(WorldGenRegion regionlimitedworldaccess, long seed, RandomState randomstate, BiomeManager biomemanager, StructureManager structuremanager, ChunkAccess ichunkaccess) {
        delegate.applyCarvers(regionlimitedworldaccess, seed, randomstate, biomemanager, structuremanager, ichunkaccess);
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomstate, StructureManager structuremanager, ChunkAccess ichunkaccess) {
        BukkitChunkGenerator.GenerationStagePermit stage = requireNoiseGenerationStage(
                ichunkaccess.getPos(),
                "bukkit_nms_chunk_pipeline");
        GenerationSessionLease lease;
        try {
            lease = requireGenerationLease("bukkit_nms_chunk_pipeline");
        } catch (RuntimeException | Error failure) {
            stage.close();
            throw failure;
        }
        try {
            CompletableFuture<ChunkAccess> pipeline = delegate
                    .fillFromNoise(blender, randomstate, structuremanager, ichunkaccess)
                    .thenApply(filled -> {
                        try (IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
                            primeWorldgenHeightmaps(filled);
                            return filled;
                        }
                    });
            CompletableFuture<ChunkAccess> completion = new CompletableFuture<>();
            pipeline.whenComplete((filled, failure) -> {
                boolean cancelled = isCancellationFailure(failure);
                lease.close();
                stage.close();
                if (failure == null) {
                    completion.complete(filled);
                } else if (cancelled) {
                    completion.cancel(false);
                } else {
                    completion.completeExceptionally(failure);
                }
            });
            return completion;
        } catch (RuntimeException | Error failure) {
            lease.close();
            stage.close();
            throw failure;
        }
    }

    private static boolean isCancellationFailure(Throwable failure) {
        Throwable current = failure;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current instanceof CancellationException;
    }

    private void primeWorldgenHeightmaps(ChunkAccess chunkAccess) {
        WorldgenTerrainHeightmaps.primeTerrain(chunkAccess, worldgenSurfaceHeight(), worldgenFloorHeight());
    }

    private IntBinaryOperator worldgenSurfaceHeight() {
        return (x, z) -> engine.getHeight(x, z, false) + runtimeMinY + 1;
    }

    private IntBinaryOperator worldgenFloorHeight() {
        return (x, z) -> engine.getHeight(x, z, true) + runtimeMinY + 1;
    }

    @Override
    public WeightedList<MobSpawnSettings.SpawnerData> getMobsAt(Holder<Biome> holder, StructureManager structuremanager, MobCategory enumcreaturetype, BlockPos blockposition) {
        Holder<Biome> vanillaSpawnBiome = customBiomeSource.getVanillaSpawnBiome(holder);
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

        IrisDimension spawnDimension = engine.getDimension();
        int spawnRuntimeId = engine.getCacheID();
        if (spawnTableDimension != spawnDimension || spawnTableRuntimeId != spawnRuntimeId) {
            synchronized (mergedSpawnTables) {
                if (spawnTableDimension != spawnDimension || spawnTableRuntimeId != spawnRuntimeId) {
                    mergedSpawnTables.clear();
                    spawnTableDimension = spawnDimension;
                    spawnTableRuntimeId = spawnRuntimeId;
                }
            }
        }
        SpawnTableKey key = new SpawnTableKey(holder.value(), enumcreaturetype);
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
        try (BukkitChunkGenerator.GenerationStagePermit stage = requireGenerationStage("bukkit_nms_biome_decoration");
             GenerationSessionLease lease = requireGenerationLease("bukkit_nms_biome_decoration");
             IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            // Bind-time equivalent for Bukkit: the table is built on the first decorated chunk, which is where a
            // feature-order cycle is reported once and degraded to features-off.
            importedFeatures.prepare(generatoraccessseed);
            addVanillaDecorations(generatoraccessseed, ichunkaccess, structuremanager);
            placeVanillaStructures(generatoraccessseed, ichunkaccess, structuremanager);
            // Vanilla's placed-feature pass, on THIS thread. The delegate is still called with
            // addVanillaDecorations=false below, so the vanilla half never runs twice. Inert unless the
            // dimension set importedFeatures.enabled.
            importedFeatures.run(generatoraccessseed, ichunkaccess, this);
            delegate.applyBiomeDecoration(generatoraccessseed, ichunkaccess, structuremanager, false);
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
                    + "and deny families through iris:structure disabled or complete keys through iris:structure disabledExact");
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
        List<NativeStructureFitBridge.FitTarget> fitTargets = new ArrayList<>();
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
                    boolean platformGenerates = NativeStructureGenerationPolicy.generates(engine, structureId);
                    List<StructureStart> starts = structureManager.startsForStructure(sectionPos, structure);
                    List<NativePlacement> resolvedPlacements = new ArrayList<>(starts.size());
                    for (StructureStart start : starts) {
                        NativeStructureOwnershipRecord ownership =
                                NativeStructureOwnershipRecovery.resolve(
                                        engine, world.getLevel(), structureId, structure, start);
                        if (ownership == null && !platformGenerates) {
                            continue;
                        }
                        resolvedPlacements.add(new NativePlacement(start, ownership != null));
                        heightmapStarts.add(start);
                        fitTargets.add(new NativeStructureFitBridge.FitTarget(structureId, start,
                                ownership == null ? null : ownership.restoredMemo()));
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
                    world, chunkPos, heightmapStarts, worldgenSurfaceHeight(), worldgenFloorHeight());
        } catch (Throwable error) {
            throw NativeStructureGenerationException.failure(
                    "heightmap priming", nativeStructureBatchContext(placementGroups),
                    chunkPos.x(), chunkPos.z(), error);
        }
        try {
            NativeStructureVegetationClearer.clearIntersectingVegetation(
                    world, chunk, area, vegetationTargets);
        } catch (Throwable error) {
            throw NativeStructureGenerationException.failure(
                    "vegetation cleanup", nativeStructureBatchContext(placementGroups),
                    chunkPos.x(), chunkPos.z(), error);
        }
        NativeStructureFitting.Session fit;
        try {
            fit = NativeStructureFitting.of(engine).beforePieces(NativeStructureFitBridge.chunk(
                    world, area, fitTargets, (x, z) -> engine.getHeight(x, z, true) + engine.getMinHeight()));
        } catch (Throwable error) {
            throw NativeStructureGenerationException.failure(
                    "terrain fitting", nativeStructureBatchContext(placementGroups),
                    chunkPos.x(), chunkPos.z(), error);
        }
        int placed = 0;
        for (NativePlacementGroup group : placementGroups) {
            random.setFeatureSeed(decoSeed, group.featureIndex(), group.step());
            try {
                for (NativePlacement placement : group.placements()) {
                    placeVanillaStructure(world, structureManager, random, area, chunkPos,
                            group.structureId(), placement);
                    fit.afterPieces(placed++);
                }
            } catch (Throwable error) {
                throw NativeStructureGenerationException.failure(
                        "placement", group.structureId(), chunkPos.x(), chunkPos.z(), error);
            }
        }
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
                                       BoundingBox area, ChunkPos chunkPos, String structureId,
                                       NativePlacement placement) {
        NativeStructureWorldgenAccess boundedWorld = NativeStructureWorldgenAccess.create(
                world, chunkPos, worldgenSurfaceHeight(), worldgenFloorHeight());
        world.setCurrentlyGenerating(() -> "Iris native structure " + structureId);
        try {
            StructureStart start = placement.start();
            NativeStructureVerticalPlacer.ensureMonumentSeaLevelAlignment(start, structureId,
                    () -> placement.recorded() ? 0 : platformShift(start, structureId, world.getMinY()),
                    getSeaLevel(), area.minY(), area.maxY() + 1);
            start.placeInChunk(boundedWorld, structureManager, this, random, area, chunkPos);
        } finally {
            DeferredPlacementRecords.clearWritten(engine, boundedWorld.writtenBlocks());
            world.setCurrentlyGenerating(null);
        }
    }

    /** The shift the pack's modules give the platform's own start, as when the start was published. */
    private int platformShift(StructureStart start, String structureId, int bottomY) {
        return NativeStructureLayouts.shift(engine, NativeStructureVerticalPlacer.placementOf(start, structureId,
                NativeStructureVegetationClearer.isUndergroundStep(start.getStructure().step()), bottomY,
                (x, z) -> engine.getHeight(x, z, true) + engine.getMinHeight()));
    }

    private List<List<Structure>> structuresByStep(Registry<Structure> registry) {
        StructureStepCache cached = structureStepCache;
        if (cached != null && cached.registry() == registry) {
            return cached.structures();
        }
        synchronized (this) {
            cached = structureStepCache;
            if (cached != null && cached.registry() == registry) {
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
            structureStepCache = new StructureStepCache(registry, resolved);
            return resolved;
        }
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
        try (GenerationSessionLease lease = engine.acquireGenerationLease("bukkit_nms_heightmaps");
             IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            SectionPos sectionPos = SectionPos.of(chunkAccess.getPos(), level.getMinSectionY());
            BlockPos blockPos = sectionPos.origin();

            primeWorldgenHeightmaps(chunkAccess);

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
        Holder<Biome> visibleBiome = region.getBiome(center.getWorldPosition().atY(region.getMaxY()));
        Holder<Biome> vanillaBiome = customBiomeSource.getVanillaSpawnBiome(visibleBiome);
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
        random.setDecorationSeed(region.getSeed(), center.getMinBlockX(), center.getMinBlockZ());
        NaturalSpawner.spawnMobsForChunkGeneration(
                region, vanillaBiome == null ? visibleBiome : vanillaBiome, center, random);
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
        try (GenerationSessionLease lease = engine.acquireGenerationLease("bukkit_nms_base_height");
             IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            return levelheightaccessor.getMinY()
                    + engine.getHeight(i, j, !heightmap_type.isOpaque().test(Blocks.WATER.defaultBlockState()))
                    + 1;
        } catch (GenerationSessionException e) {
            throw new IllegalStateException("Iris base height query could not acquire its engine runtime.", e);
        }
    }

    @Override
    public NoiseColumn getBaseColumn(int i, int j, LevelHeightAccessor levelheightaccessor, RandomState randomstate) {
        try (GenerationSessionLease lease = engine.acquireGenerationLease("bukkit_nms_base_column");
             IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            int block = engine.getHeight(i, j, true);
            int water = engine.getHeight(i, j, false);
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

    private BukkitChunkGenerator.GenerationStagePermit requireGenerationStage(String operation) {
        return platformGenerator == null
                ? BukkitChunkGenerator.GenerationStagePermit.noop()
                : platformGenerator.acquireGenerationStage(operation);
    }

    private BukkitChunkGenerator.GenerationStagePermit requireNoiseGenerationStage(
            ChunkPos chunkPos,
            String operation
    ) {
        return platformGenerator == null
                ? BukkitChunkGenerator.GenerationStagePermit.noop()
                : platformGenerator.acquireNoiseGenerationStage(
                        engine,
                        chunkPos.x(),
                        chunkPos.z(),
                        operation);
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

    private record SpawnTableKey(Biome biome, MobCategory category) {
    }

    private record ReachableStructureCache(IrisDimension dimension, int runtimeId, Set<String> keys) {
    }

    private record StructureStepCache(Registry<Structure> registry, List<List<Structure>> structures) {
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

    /** A start placed in the chunk, and whether a module placed it (its ownership record kept its fit). */
    private record NativePlacement(StructureStart start, boolean recorded) {
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
