/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.engine;

import art.arcane.iris.engine.EngineBackgroundTasks.BackgroundTaskDrain;
import art.arcane.iris.engine.EngineRuntimeBuilder.RuntimeAssembly;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.BiomeEnvironment;
import art.arcane.iris.engine.framework.EngineEffects;
import art.arcane.iris.engine.framework.EngineMetrics;
import art.arcane.iris.engine.framework.EngineMode;
import art.arcane.iris.engine.framework.EnginePlatformHooks;
import art.arcane.iris.engine.framework.NativeStructureVolume;
import art.arcane.iris.engine.framework.NativeStructureVolumeMemo;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.iris.engine.framework.EngineTarget;
import art.arcane.iris.engine.framework.EngineWorldManager;
import art.arcane.iris.engine.framework.GenerationSessionException;
import art.arcane.iris.engine.framework.GenerationSessionLease;
import art.arcane.iris.engine.framework.GenerationSessionManager;
import art.arcane.iris.engine.history.GenerationBoundarySignatureSampler;
import art.arcane.iris.engine.history.GenerationHistory;
import art.arcane.iris.engine.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.engine.history.ChunkGenerationSemantics;
import art.arcane.iris.engine.history.GenerationKernelRegistry;
import art.arcane.iris.engine.history.TransitionGenerationPlan;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.framework.WrongEngineBroException;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisDimensionCarvingResolver;
import art.arcane.iris.engine.object.IrisEngineData;
import art.arcane.iris.engine.object.IrisRegion;
import art.arcane.iris.core.pack.PackValidationCache;
import art.arcane.iris.spi.IrisLogging;
import com.google.common.util.concurrent.AtomicDouble;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.core.nms.container.BlockPos;
import art.arcane.iris.core.nms.container.Pair;
import art.arcane.iris.core.structure.StructureIndexService;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.util.common.data.B;
import art.arcane.iris.util.common.plugin.VolmitSender;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.atomics.AtomicRollingSequence;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.project.context.IrisContext;
import art.arcane.volmlib.util.documentation.BlockCoordinates;
import art.arcane.volmlib.util.documentation.ChunkCoordinates;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.matter.MatterStructurePOI;
import art.arcane.volmlib.util.scheduling.ChronoLatch;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

@Data
public class IrisEngine implements Engine {
    static final long SESSION_DRAIN_TIMEOUT_MILLIS = 15000L;

    private final AtomicInteger bud;
    private final AtomicInteger buds;
    private final AtomicInteger generated;
    private final AtomicInteger generatedLast;
    private final AtomicDouble perSecond;
    private final AtomicLong lastGPS;
    private final EngineTarget target;
    private final InitializationMode initializationMode;
    private final Path initialMantleStorageDirectory;
    private final GenerationKernelRegistry.Version initialKernelVersion;
    private final TransitionGenerationPlan initialTransitionPlan;
    private final ChronoLatch perSecondLatch;
    private final ChronoLatch perSecondBudLatch;
    private final EngineMetrics metrics;
    private final CompletableFuture<Void> generationCacheWarm;
    private final boolean studio;
    @Setter(AccessLevel.NONE)
    private volatile Path studioGenerationSource;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    boolean studioGenerationTransition;
    private final AtomicRollingSequence wallClock;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    final Object lifecycleLock = new Object();
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    final Object generationHistoryRuntimeRouterLock = new Object();
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    final ThreadLocal<RuntimeAssembly> runtimeAssembly = new ThreadLocal<>();
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    final ThreadLocal<BiomeEnvironmentBinding> biomeEnvironmentScopes = new ThreadLocal<>();
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    final GenerationRuntimeScopeState generationRuntimeScopes = new GenerationRuntimeScopeState();
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    final Set<GenerationRuntime> detachedGenerationRuntimes = Collections.synchronizedSet(
            Collections.newSetFromMap(new IdentityHashMap<>()));
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    final Set<GenerationRuntime> retiringGenerationRuntimes = Collections.synchronizedSet(
            Collections.newSetFromMap(new IdentityHashMap<>()));
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    final Set<IntConsumer> generationRuntimeRetirementListeners = new CopyOnWriteArraySet<>();
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    final EngineBackgroundTasks backgroundTasks = new EngineBackgroundTasks();
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    final EngineDataStore engineDataStore = new EngineDataStore(this);
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    final EngineRuntimeBuilder runtimeBuilder = new EngineRuntimeBuilder(this);
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    final EngineShutdownSequence shutdownSequence = new EngineShutdownSequence(this);
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    final EngineHotloader hotloader = new EngineHotloader(this);
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    final NativeStructureBootstrapBarrier nativeStructureBootstrapBarrier = new NativeStructureBootstrapBarrier();
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    final EngineMetricsReport metricsReport = new EngineMetricsReport(this);
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final EngineDiagnostics diagnostics = new EngineDiagnostics(this);
    private final AtomicBoolean cleaning;
    private final ChronoLatch cleanLatch;
    private final SeedManager seedManager;
    private final GenerationSessionManager generationSessions;
    private final EnginePlatformHooks platformHooks;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final NativeStructureVolumeMemo nativeStructureVolumeMemo = new NativeStructureVolumeMemo();
    private final AtomicBoolean closing;
    private final AtomicBoolean nativeStructureVolumeQueriesEnabled;
    @Setter(AccessLevel.NONE)
    volatile IrisEngineData engineData;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    volatile EngineRuntime runtime;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    volatile GenerationHistoryRuntimeRouter generationHistoryRuntimeRouter;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private boolean generationHistoryRoutingRequired;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    volatile EngineTarget publishedTarget;
    private volatile int parallelism;
    private volatile boolean failing;
    volatile boolean closed;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    volatile LifecycleState lifecycleState;
    private final AtomicBoolean modeFallbackLogged;
    private final AtomicBoolean prefetchSaveStarted;

    /**
     * Object identity, not value identity. An engine is a live mutable service, and {@code @Data} would otherwise
     * generate equals/hashCode over every field above - counters, latches, rolling averages - so an engine's hash
     * would change on every generated chunk and its equality would depend on transient timing state.
     * <p>
     * Three live maps key on an engine: the modded GUI host registry and the two WeakHashMap tree-feller indexes. A
     * mutating hash silently loses their entries, and a value-based equals lets two distinct engines collide.
     */
    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    public IrisEngine(EngineTarget target, InitializationMode initializationMode) {
        this(
                target,
                initializationMode,
                IrisEngineMantle.legacyStorageDirectory(target),
                GenerationKernelRegistry.standard().current(),
                null);
    }

    public IrisEngine(
            EngineTarget target,
            InitializationMode initializationMode,
            Path mantleStorageDirectory
    ) {
        this(
                target,
                initializationMode,
                mantleStorageDirectory,
                GenerationKernelRegistry.standard().current(),
                null);
    }

    public IrisEngine(
            EngineTarget target,
            InitializationMode initializationMode,
            Path mantleStorageDirectory,
            TransitionGenerationPlan transitionPlan
    ) {
        this(
                target,
                initializationMode,
                mantleStorageDirectory,
                GenerationKernelRegistry.standard().current(),
                transitionPlan);
    }

    public IrisEngine(
            EngineTarget target,
            InitializationMode initializationMode,
            Path mantleStorageDirectory,
            GenerationKernelRegistry.Version kernelVersion,
            TransitionGenerationPlan transitionPlan
    ) {
        EngineTarget requiredTarget = Objects.requireNonNull(target, "engine target");
        InitializationMode requiredMode = Objects.requireNonNull(initializationMode, "initialization mode");
        this.initializationMode = requiredMode;
        this.studio = requiredMode.studio();
        this.target = requiredTarget;
        this.publishedTarget = requiredTarget;
        this.initialMantleStorageDirectory = IrisEngineMantle.normalizeStorageDirectory(mantleStorageDirectory);
        this.initialKernelVersion = Objects.requireNonNull(kernelVersion, "generation kernel version");
        this.initialTransitionPlan = transitionPlan;
        this.platformHooks = IrisServices.get(EnginePlatformHooks.class);
        this.generationSessions = new GenerationSessionManager(requiredMode.studio());
        this.closing = new AtomicBoolean(true);
        this.nativeStructureVolumeQueriesEnabled = new AtomicBoolean(!requiredMode.studio());
        this.lifecycleState = LifecycleState.INITIALIZING;
        this.closed = false;
        this.failing = false;
        getEngineData();
        verifySeed();
        this.seedManager = EngineRuntimeBuilder.selectRuntimeKernel(initialKernelVersion)
                .createSeedManager(requiredTarget.getWorld().getRawWorldSeed());
        bud = new AtomicInteger(0);
        buds = new AtomicInteger(0);
        metrics = new EngineMetrics(32);
        generationCacheWarm = new CompletableFuture<>();
        cleanLatch = new ChronoLatch(10000);
        generatedLast = new AtomicInteger(0);
        perSecond = new AtomicDouble(0);
        perSecondLatch = new ChronoLatch(1000, false);
        perSecondBudLatch = new ChronoLatch(1000, false);
        wallClock = new AtomicRollingSequence(32);
        lastGPS = new AtomicLong(M.ms());
        generated = new AtomicInteger(0);
        long _t0;
        cleaning = new AtomicBoolean(false);
        modeFallbackLogged = new AtomicBoolean(false);
        prefetchSaveStarted = new AtomicBoolean(false);
        sealInitialGenerationSession();

        try {
            if (studio) {
                _t0 = M.ms();
                getData().dump();
                getData().clearLists();
                IrisDimension replacement = getData().getDimensionLoader().load(getDimension().getLoadKey());
                if (replacement == null) {
                    throw new IllegalStateException("Studio engine could not load Iris dimension '" + getDimension().getLoadKey() + "'");
                }
                publishedTarget = new EngineTarget(getWorld(), replacement, getData());
                IrisLogging.debug("[IrisEngine timing] dump+clearLists+reload=" + (M.ms() - _t0) + "ms");
            }
            getData().registerEngine(this);
            _t0 = M.ms();
            long phaseStartedAt = System.nanoTime();
            String studioCacheIdentity = currentStudioCacheIdentity();
            getData().loadPrefetch(this);
            IrisLogging.debug("[IrisEngine timing] loadPrefetch=" + (M.ms() - _t0) + "ms");
            diagnostics.logStudioInitializationPhase("load_prefetch", phaseStartedAt, false);
            try {
                StructureIndexService.writeOnce(getData());
            } catch (Throwable e) {
                IrisLogging.reportError(e);
            }
            IrisLogging.notice("Engine init: " + requiredTarget.getWorld().name() + "/" + requiredTarget.getDimension().getLoadKey() + " seed=" + getSeedManager().getSeed());
            _t0 = M.ms();
            phaseStartedAt = System.nanoTime();
            EngineRuntime initialRuntime = runtimeBuilder.buildRuntime();
            enableStableStudioCache(initialRuntime, studioCacheIdentity);
            runtimeBuilder.publishRuntime(initialRuntime, null);
            IrisLogging.debug("[IrisEngine timing] setupEngine total=" + (M.ms() - _t0) + "ms");
            diagnostics.logPackCompatSummary();
            diagnostics.logStudioInitializationPhase("build_runtime", phaseStartedAt, false);
            phaseStartedAt = System.nanoTime();
            if (requiredMode.warmGenerationCaches()) {
                if (requiredMode.studio()) {
                    startGenerationCacheWarm(phaseStartedAt);
                } else {
                    warmGenerationCaches(phaseStartedAt);
                }
            } else {
                generationCacheWarm.complete(null);
                diagnostics.logStudioInitializationPhase("generation_cache_warm", phaseStartedAt, true);
            }
            EngineTickRegistry.registerTicking(this);
        } catch (Throwable e) {
            shutdownSequence.cleanupFailedConstruction(e);
            throw new IllegalStateException("Failed to initialize Iris engine for world '" + requiredTarget.getWorld().name() + "'.", e);
        }
        IrisLogging.debug("Engine Initialized " + getCacheID());
    }

    public void awaitGenerationCacheWarm() {
        if (generationCacheWarm.isDone() && !generationCacheWarm.isCompletedExceptionally()) {
            return;
        }
        try {
            generationCacheWarm.join();
        } catch (CompletionException failure) {
            throw new IllegalStateException("Iris generation caches did not become ready.", failure.getCause());
        }
    }

    public boolean isGenerationCacheWarmPending() {
        return !generationCacheWarm.isDone();
    }

    private String currentStudioCacheIdentity() {
        if (initializationMode != InitializationMode.STUDIO || !IrisPlatforms.isBound()) {
            return "";
        }
        try {
            String contentFingerprint = PackValidationCache.contentFingerprint(
                    IrisPlatforms.get().packsFolderNoCreate());
            String contextFingerprint = PackValidationCache.contextFingerprint();
            if (contentFingerprint.isBlank() || contextFingerprint.isBlank()) {
                return "";
            }
            return contentFingerprint + contextFingerprint;
        } catch (RuntimeException failure) {
            IrisLogging.warn("Studio runtime cache fingerprint failed: " + failure.getMessage());
            return "";
        }
    }

    private void enableStableStudioCache(EngineRuntime runtime, String initialIdentity) {
        if (initialIdentity.isBlank()) {
            return;
        }
        String finalIdentity = currentStudioCacheIdentity();
        if (!initialIdentity.equals(finalIdentity)) {
            IrisLogging.warn("Studio packs changed during runtime compilation; shared generation caches are disabled.");
            return;
        }
        runtime.generation().complex().enableStudioHydrologyCache(initialIdentity, studioHydrologyCacheRoot());
        IrisLogging.debug("Enabled shared Studio hydrology cache identity="
                + initialIdentity.substring(0, Math.min(12, initialIdentity.length())));
    }

    private Path studioHydrologyCacheRoot() {
        Path packsRoot = IrisPlatforms.get().packsFolderNoCreate().toPath().toAbsolutePath().normalize();
        Path packRoot = packsRoot.resolve(getDimension().getLoadKey()).normalize();
        if (!packRoot.startsWith(packsRoot)) {
            throw new IllegalStateException("Studio dimension key resolves outside the Iris packs folder.");
        }
        return packRoot.resolve(".iris").resolve("studio-hydrology");
    }

    public void startStudioEntryHydrology(int blockX, int blockZ) {
        GenerationRuntimeBinding binding = getActiveGenerationRuntimeBinding();
        if (!backgroundTasks.scheduleTrackedTask(() -> prepareStudioEntryHydrology(binding, blockX, blockZ))) {
            throw new IllegalStateException("Iris background task admission closed before Studio entry preparation.");
        }
    }

    private void prepareStudioEntryHydrology(GenerationRuntimeBinding binding, int blockX, int blockZ) {
        if (closing.get()) {
            return;
        }
        GenerationSessionLease lease;
        try {
            lease = acquireGenerationLease("studio_entry_hydrology");
        } catch (GenerationSessionException failure) {
            if (closing.get()) {
                return;
            }
            throw new IllegalStateException("Studio entry hydrology preparation could not acquire its generation session.", failure);
        }
        try (lease;
             GenerationRuntimeScope runtimeScope = generationRuntimeScopes.open(binding);
             IrisContext.Scope context = IrisContext.open(this, lease.sessionId(), null)) {
            getComplex().getHydrologyRuntime().prepareChunkColumns(blockX, blockZ);
        } catch (Exception failure) {
            throw new IllegalStateException("Studio entry hydrology preparation failed at "
                    + blockX + "," + blockZ + ".", failure);
        }
    }

    private void startGenerationCacheWarm(long phaseStartedAtNanos) {
        if (!backgroundTasks.scheduleTrackedTask(() -> warmGenerationCaches(phaseStartedAtNanos))) {
            throw new IllegalStateException("Iris background task admission closed before generation cache warming.");
        }
    }

    private void warmGenerationCaches(long phaseStartedAtNanos) {
        long startedAtMillis = M.ms();
        try {
            GenerationCacheWarmer.warm(this);
            generationCacheWarm.complete(null);
        } catch (Throwable failure) {
            generationCacheWarm.completeExceptionally(failure);
            if (failure instanceof Error error) {
                throw error;
            }
            if (failure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Generation cache warming failed.", failure);
        } finally {
            IrisLogging.debug("[IrisEngine timing] cache warm total=" + (M.ms() - startedAtMillis) + "ms");
            diagnostics.logStudioInitializationPhase("generation_cache_warm", phaseStartedAtNanos, false);
        }
    }

    private void verifySeed() {
        if (getEngineData().getSeed() != null && getEngineData().getSeed() != target.getWorld().getRawWorldSeed()) {
            target.getWorld().setRawWorldSeed(getEngineData().getSeed());
        }
    }

    void tickRandomPlayer() {
        if (closing.get() || closed) {
            return;
        }
        recycle();
        if (perSecondBudLatch.flip()) {
            buds.set(bud.get());
            bud.set(0);
        }

        EngineEffects currentEffects = getEffects();
        if (currentEffects != null) {
            currentEffects.tickRandomPlayer();
        }
    }

    private void sealInitialGenerationSession() {
        try {
            generationSessions.sealAndAwait("initialization", 0L);
        } catch (GenerationSessionException e) {
            throw new IllegalStateException("Failed to seal the initial Iris generation session.", e);
        }
    }

    void sealForTransition(String reason, boolean teardown) {
        closing.set(true);
        backgroundTasks.closeBackgroundTaskAdmission();
        try {
            generationSessions.sealAndAwait(reason, SESSION_DRAIN_TIMEOUT_MILLIS, teardown);
            BackgroundTaskDrain backgroundDrain = backgroundTasks.drainBackgroundTasks(reason);
            backgroundDrain.requireComplete(reason);
        } catch (GenerationSessionException e) {
            throw new IllegalStateException("Failed to drain Iris generation for " + reason + ".", e);
        }
    }

    public CompletableFuture<Void> startNativeStructureBootstrap(
            Runnable claim,
            Supplier<CompletableFuture<Void>> starter,
            Runnable activation
    ) {
        synchronized (lifecycleLock) {
            requireRunning("prepare native structure placements");
            CompletableFuture<Void> completion = nativeStructureBootstrapBarrier.start(claim, starter);
            activation.run();
            return completion;
        }
    }

    void awaitNativeStructureBootstrap(String transition) {
        nativeStructureBootstrapBarrier.await(transition);
    }

    @Override
    public void generateMatter(int x, int z, boolean multicore, ChunkContext context) {
        awaitGenerationCacheWarm();
        try (GenerationHistoryRuntimeRouter.CoordinateScope historyScope =
                     openGenerationHistoryCoordinateScope(x << 4, z << 4);
             GenerationSessionLease lease = acquireGenerationLease("matter_generate");
             IrisContext.Scope ignored = IrisContext.open(this, lease.sessionId(), context)) {
            IrisComplex activeComplex = getComplex();
            if (context == null || context.getComplex() != activeComplex) {
                throw new IllegalStateException("Matter generation context does not belong to the active Iris runtime.");
            }
            if (context.getGenerationSessionId() != 0L
                    && context.getGenerationSessionId() != lease.sessionId()) {
                throw new IllegalStateException("Matter generation context belongs to Iris session "
                        + context.getGenerationSessionId() + " instead of " + lease.sessionId() + ".");
            }
            getMantle().generateMatter(x, z, multicore, context);
        } catch (GenerationSessionException | IOException e) {
            throw new IllegalStateException("Matter generation was rejected by the Iris lifecycle.", e);
        }
    }

    @Override
    public Color drawForPreview(int x, int z) throws InterruptedException {
        try (GenerationSessionLease lease = acquireGenerationLease("pregen_preview")) {
            if (!usesSavedBiomeEnvironment()) {
                return Engine.super.draw(x, z);
            }
            return generationHistoryRuntimeRouter.biomes().readSurfaceBiome(x, z, saved -> {
                if (saved.isPresent()) {
                    return drawBiomeEnvironment(x, z, saved.get());
                }
                try (GenerationHistoryRuntimeRouter.CoordinateScope ignored =
                             openGenerationHistoryCoordinateScopeUnchecked(x, z, "draw a pregeneration preview")) {
                    return Engine.super.draw(x, z);
                }
            });
        } catch (GenerationSessionException failure) {
            throw new IllegalStateException("Pregeneration preview was rejected by the Iris lifecycle.", failure);
        }
    }

    @Override
    public BiomeEnvironment getBiomeEnvironment(int x, int y, int z) {
        Optional<BiomeEnvironment> saved = resolveSavedBiomeEnvironment(x, y, z, false);
        if (saved.isPresent()) {
            return saved.get();
        }
        try (GenerationHistoryRuntimeRouter.CoordinateScope ignored =
                     openGenerationHistoryCoordinateScopeUnchecked(x, z, "resolve a biome environment")) {
            return Engine.super.getBiomeEnvironment(x, y, z);
        }
    }

    @Override
    public BiomeEnvironment getSurfaceBiomeEnvironment(int x, int z) {
        Optional<BiomeEnvironment> saved = resolveSavedBiomeEnvironment(x, 0, z, true);
        if (saved.isPresent()) {
            return saved.get();
        }
        try (GenerationHistoryRuntimeRouter.CoordinateScope ignored =
                     openGenerationHistoryCoordinateScopeUnchecked(x, z, "resolve a surface biome environment")) {
            return Engine.super.getSurfaceBiomeEnvironment(x, z);
        }
    }

    @Override
    public BiomeEnvironment.Scope openBiomeEnvironmentScope(BiomeEnvironment environment) {
        BiomeEnvironmentBinding binding = new BiomeEnvironmentBinding(
                Objects.requireNonNull(environment, "environment"), biomeEnvironmentScopes.get());
        biomeEnvironmentScopes.set(binding);
        return binding;
    }

    private Optional<BiomeEnvironment> resolveSavedBiomeEnvironment(int x, int y, int z, boolean surface) {
        if (!usesSavedBiomeEnvironment()) {
            return Optional.empty();
        }
        return generationHistoryRuntimeRouter.biomes().resolve(x, y + getWorld().minHeight(), z, surface);
    }

    private boolean usesSavedBiomeEnvironment() {
        if (hasGenerationRuntimeScope() || generationHistoryRuntimeRouter == null) {
            return false;
        }
        IrisContext context = IrisContext.get();
        return context == null || context.getEngine() != this || context.getChunkContext() == null;
    }

    @BlockCoordinates
    @Override
    public IrisBiome getBiome(int x, int y, int z) {
        Optional<BiomeEnvironment> saved = resolveSavedBiomeEnvironment(x, y, z, false);
        if (saved.isPresent()) {
            return saved.get().biome();
        }
        try (GenerationHistoryRuntimeRouter.CoordinateScope ignored =
                     openGenerationHistoryCoordinateScopeUnchecked(x, z, "resolve a biome")) {
            return Engine.super.getBiome(x, y, z);
        }
    }

    @BlockCoordinates
    @Override
    public IrisBiome getBiomeOrMantle(int x, int y, int z) {
        Optional<BiomeEnvironment> saved = resolveSavedBiomeEnvironment(x, y, z, false);
        if (saved.isPresent()) {
            return saved.get().biome();
        }
        try (GenerationHistoryRuntimeRouter.CoordinateScope ignored =
                     openGenerationHistoryCoordinateScopeUnchecked(x, z, "resolve a biome or mantle biome")) {
            return Engine.super.getBiomeOrMantle(x, y, z);
        }
    }

    @BlockCoordinates
    @Override
    public IrisRegion getRegion(int x, int z) {
        Optional<BiomeEnvironment> saved = resolveSavedBiomeEnvironment(x, 0, z, true);
        if (saved.isPresent()) {
            return saved.get().region();
        }
        try (GenerationHistoryRuntimeRouter.CoordinateScope ignored =
                     openGenerationHistoryCoordinateScopeUnchecked(x, z, "resolve a region")) {
            return Engine.super.getRegion(x, z);
        }
    }

    @BlockCoordinates
    @Override
    public IrisRegion getRegion(int x, int y, int z) {
        Optional<BiomeEnvironment> saved = resolveSavedBiomeEnvironment(x, y, z, false);
        if (saved.isPresent()) {
            return saved.get().region();
        }
        try (GenerationHistoryRuntimeRouter.CoordinateScope ignored =
                     openGenerationHistoryCoordinateScopeUnchecked(x, z, "resolve a vertical region")) {
            return Engine.super.getRegion(x, y, z);
        }
    }

    @BlockCoordinates
    @Override
    public IrisBiome getCaveOrMantleBiome(int x, int y, int z) {
        Optional<BiomeEnvironment> saved = resolveSavedBiomeEnvironment(x, y, z, false);
        if (saved.isPresent()) {
            return saved.get().biome();
        }
        try (GenerationHistoryRuntimeRouter.CoordinateScope ignored =
                     openGenerationHistoryCoordinateScopeUnchecked(x, z, "resolve a cave or mantle biome")) {
            return Engine.super.getCaveOrMantleBiome(x, y, z);
        }
    }

    @BlockCoordinates
    @Override
    public IrisBiome getCaveBiome(int x, int z) {
        if (usesSavedBiomeEnvironment()) {
            Optional<BiomeEnvironment> saved = generationHistoryRuntimeRouter.biomes().resolveCaveBase(x, z);
            if (saved.isPresent()) {
                return saved.get().biome();
            }
        }
        if (usesScopedNaturalTerrain()) {
            return Engine.super.getCaveBiome(x, z);
        }
        try (GenerationHistoryRuntimeRouter.CoordinateScope ignored =
                     openGenerationHistoryCoordinateScopeUnchecked(x, z, "resolve a cave biome")) {
            return Engine.super.getCaveBiome(x, z);
        }
    }

    @BlockCoordinates
    @Override
    public IrisBiome getCaveBiome(int x, int y, int z) {
        return getCaveBiome(x, y, z, null);
    }

    @BlockCoordinates
    @Override
    public IrisBiome getCaveBiome(
            int x,
            int y,
            int z,
            IrisDimensionCarvingResolver.State state
    ) {
        Optional<BiomeEnvironment> saved = resolveSavedBiomeEnvironment(x, y, z, false);
        if (saved.isPresent()) {
            return saved.get().biome();
        }
        if (usesScopedNaturalTerrain()) {
            return Engine.super.getCaveBiome(x, y, z, state);
        }
        try (GenerationHistoryRuntimeRouter.CoordinateScope ignored =
                     openGenerationHistoryCoordinateScopeUnchecked(x, z, "resolve a vertical cave biome")) {
            return Engine.super.getCaveBiome(x, y, z, state);
        }
    }

    @BlockCoordinates
    @Override
    public IrisBiome getSurfaceBiome(int x, int z) {
        Optional<BiomeEnvironment> saved = resolveSavedBiomeEnvironment(x, 0, z, true);
        if (saved.isPresent()) {
            return saved.get().biome();
        }
        if (usesScopedNaturalTerrain()) {
            return Engine.super.getSurfaceBiome(x, z);
        }
        try (GenerationHistoryRuntimeRouter.CoordinateScope ignored =
                     openGenerationHistoryCoordinateScopeUnchecked(x, z, "resolve a surface biome")) {
            return Engine.super.getSurfaceBiome(x, z);
        }
    }

    @BlockCoordinates
    @Override
    public double getSlope(int x, int z) {
        try (GenerationHistoryRuntimeRouter.CoordinateScope ignored =
                     openGenerationHistoryCoordinateScopeUnchecked(x, z, "resolve terrain slope")) {
            return Engine.super.getSlope(x, z);
        }
    }

    @BlockCoordinates
    @Override
    public int getHeight(int x, int z) {
        return getHeight(x, z, true);
    }

    @BlockCoordinates
    @Override
    public int getHeight(int x, int z, boolean ignoreFluid) {
        try (GenerationHistoryRuntimeRouter.CoordinateScope ignored =
                     openGenerationHistoryCoordinateScopeUnchecked(x, z, "resolve terrain height")) {
            return Engine.super.getHeight(x, z, ignoreFluid);
        }
    }

    @ChunkCoordinates
    @Override
    public Set<String> getObjectsAt(int x, int z) {
        Optional<ChunkGenerationSemantics> recorded = recordedChunkSemantics(x, z);
        if (recorded.isPresent()) {
            return recorded.get().objectKeys();
        }
        try (GenerationHistoryRuntimeRouter.CoordinateScope ignored =
                     openGenerationHistoryCoordinateScopeUnchecked(x << 4, z << 4, "resolve chunk objects")) {
            return getMantle().getObjectComponent().guess(x, z);
        }
    }

    @ChunkCoordinates
    @Override
    public Set<Pair<String, BlockPos>> getPOIsAt(int chunkX, int chunkZ) {
        Optional<ChunkGenerationSemantics> recorded = recordedChunkSemantics(chunkX, chunkZ);
        if (recorded.isPresent()) {
            Set<Pair<String, BlockPos>> points = new HashSet<>();
            for (ChunkGenerationSemantics.PointOfInterest point : recorded.get().pointsOfInterest()) {
                ChunkGenerationSemantics.BlockPosition position = point.position();
                points.add(new Pair<>(point.key(), new BlockPos(position.x(), position.y(), position.z())));
            }
            return points;
        }
        try (GenerationHistoryRuntimeRouter.CoordinateScope ignored =
                     openGenerationHistoryCoordinateScopeUnchecked(
                             chunkX << 4, chunkZ << 4, "resolve chunk points of interest")) {
            Set<Pair<String, BlockPos>> pois = new HashSet<>();
            getMantle().getMantle().iterateChunk(
                    chunkX,
                    chunkZ,
                    MatterStructurePOI.class,
                    (x, y, z, data) -> pois.add(new Pair<>(data.getType(), new BlockPos(
                            Math.addExact(Math.multiplyExact(chunkX, 16), x), y,
                            Math.addExact(Math.multiplyExact(chunkZ, 16), z)))));
            return pois;
        }
    }

    private Optional<ChunkGenerationSemantics> recordedChunkSemantics(int chunkX, int chunkZ) {
        GenerationHistoryRuntimeRouter router = getGenerationHistoryRuntimeRouter().orElse(null);
        return router == null ? Optional.empty()
                : router.history().semantics(chunkX, chunkZ).filter(ChunkGenerationSemantics::sealed);
    }

    private void warmupChunk(int x, int z) {
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                int xx = x + (i << 4);
                int zz = z + (z << 4);
                getComplex().getTrueBiomeStream().get(xx, zz);
                getComplex().getHeightStream().get(xx, zz);
            }
        }
    }

    @Override
    public void hotload() {
        hotloader.hotload();
    }

    public void configureStudioGenerationSource(Path source) {
        if (!studio) {
            throw new IllegalStateException("An authoring pack source requires a Studio engine.");
        }
        Path required = Objects.requireNonNull(source, "Studio generation source").toAbsolutePath().normalize();
        synchronized (lifecycleLock) {
            requireRunning("configure the Studio authoring pack");
            if (studioGenerationSource != null && !studioGenerationSource.equals(required)) {
                throw new IllegalStateException("The Studio authoring pack is already configured.");
            }
            studioGenerationSource = required;
        }
    }

    public Path getStudioGenerationSource() {
        return Objects.requireNonNull(studioGenerationSource, "Studio authoring pack is not configured");
    }

    @Override
    public Path getPackSource() {
        Path source = studioGenerationSource;
        return source == null ? getData().getDataFolder().toPath() : source;
    }

    public void hotloadComplex() {
        hotloader.hotloadComplex();
    }

    public void hotloadSilently() {
        hotloader.hotloadSilently();
    }

    void prepareRuntimeHotload() {
        nativeStructureVolumeMemo.clear();
        platformHooks.prepareRuntimeHotload(this);
    }

    @Override
    public KList<NativeStructureVolume> getNativeStructureVolumes(int minX, int minZ, int maxX, int maxZ) {
        if (!nativeStructureVolumeQueriesEnabled.get()) {
            return NativeStructureVolume.NONE;
        }
        int minimumX = Math.min(minX, maxX);
        int maximumX = Math.max(minX, maxX);
        int minimumZ = Math.min(minZ, maxZ);
        int maximumZ = Math.max(minZ, maxZ);
        int fromChunkX = minimumX >> 4;
        int toChunkX = maximumX >> 4;
        int fromChunkZ = minimumZ >> 4;
        int toChunkZ = maximumZ >> 4;
        KList<NativeStructureVolume> matches = null;
        for (int chunkX = fromChunkX; chunkX <= toChunkX; chunkX++) {
            for (int chunkZ = fromChunkZ; chunkZ <= toChunkZ; chunkZ++) {
                int chunkMinimumX = chunkX << 4;
                int chunkMinimumZ = chunkZ << 4;
                int queryMinimumX = Math.max(minimumX, chunkMinimumX);
                int queryMaximumX = Math.min(maximumX, chunkMinimumX + 15);
                int queryMinimumZ = Math.max(minimumZ, chunkMinimumZ);
                int queryMaximumZ = Math.min(maximumZ, chunkMinimumZ + 15);
                try (GenerationHistoryRuntimeRouter.CoordinateScope ignored =
                             openGenerationHistoryCoordinateScopeUnchecked(
                                     chunkMinimumX,
                                     chunkMinimumZ,
                                     "resolve native structure volumes")) {
                    KList<NativeStructureVolume> resolved = nativeStructureVolumeMemo.volumes(
                            this,
                            platformHooks,
                            queryMinimumX,
                            queryMinimumZ,
                            queryMaximumX,
                            queryMaximumZ);
                    for (NativeStructureVolume volume : resolved) {
                        if (!volume.intersectsRect(minimumX, minimumZ, maximumX, maximumZ)) {
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
        }
        return matches == null ? NativeStructureVolume.NONE : matches;
    }

    public void setNativeStructureVolumeQueriesEnabled(boolean enabled) {
        if (!enabled) {
            nativeStructureVolumeQueriesEnabled.set(false);
            nativeStructureVolumeMemo.clear();
            return;
        }
        nativeStructureVolumeMemo.clear();
        nativeStructureVolumeQueriesEnabled.set(true);
    }

    @Override
    public IrisEngineData getEngineData() {
        return engineDataStore.getEngineData();
    }

    @Override
    public int getGenerated() {
        return generated.get();
    }

    @Override
    public double getGeneratedPerSecond() {
        return metricsReport.getGeneratedPerSecond();
    }

    @Override
    public boolean isStudio() {
        return studio;
    }

    @Override
    public int getBlockUpdatesPerSecond() {
        return buds.get();
    }

    public void printMetrics(VolmitSender sender) {
        metricsReport.printMetrics(sender);
    }

    @Override
    public void close() {
        shutdownSequence.close();
    }

    public GenerationHistoryRuntimeRouter attachGenerationHistory(
            GenerationHistory history,
            GenerationBoundarySignatureSampler signatureSampler
    ) throws IOException {
        return GenerationHistoryRuntimeRouter.attachAndPromotePending(this, history, signatureSampler);
    }

    public GenerationHistoryRuntimeRouter attachGenerationHistory(
            GenerationHistory history,
            GenerationBoundarySignatureSampler signatureSampler,
            int transitionWidthBlocks
    ) throws IOException {
        return GenerationHistoryRuntimeRouter.attachAndPromotePending(
                this,
                history,
                signatureSampler,
                transitionWidthBlocks
        );
    }

    public Optional<GenerationHistoryRuntimeRouter> getGenerationHistoryRuntimeRouter() {
        synchronized (generationHistoryRuntimeRouterLock) {
            return Optional.ofNullable(generationHistoryRuntimeRouter);
        }
    }

    public GenerationHistoryRuntimeRouter.CoordinateScope openGenerationHistoryCoordinateScope(
            int blockX,
            int blockZ
    ) throws IOException {
        GenerationHistoryRuntimeRouter router;
        synchronized (generationHistoryRuntimeRouterLock) {
            router = generationHistoryRuntimeRouter;
            if (router == null && generationHistoryRoutingRequired) {
                throw new IllegalStateException("Iris generation-history runtime router is detached.");
            }
        }
        return router == null ? null : router.openCoordinateScope(blockX, blockZ);
    }

    public boolean hasGenerationRuntimeScope() {
        return generationRuntimeScopes.current() != null;
    }

    private boolean usesScopedNaturalTerrain() {
        return hasGenerationRuntimeScope() && getComplex().isNaturalTerrainContext();
    }

    private GenerationHistoryRuntimeRouter.CoordinateScope openGenerationHistoryCoordinateScopeUnchecked(
            int blockX,
            int blockZ,
            String operation
    ) {
        try {
            return openGenerationHistoryCoordinateScope(blockX, blockZ);
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to " + operation + " through Iris generation history at "
                    + blockX + "," + blockZ + ".", failure);
        }
    }

    public void attachGenerationHistoryRuntimeRouter(GenerationHistoryRuntimeRouter router) {
        GenerationHistoryRuntimeRouter required = Objects.requireNonNull(router, "generation-history runtime router");
        if (required.engine() != this) {
            throw new IllegalArgumentException("Generation-history runtime router belongs to a different Iris engine.");
        }
        synchronized (lifecycleLock) {
            requireRunning("attach a generation-history runtime router");
            synchronized (generationHistoryRuntimeRouterLock) {
                if (generationHistoryRuntimeRouter != null) {
                    throw new IllegalStateException("Iris engine already has a generation-history runtime router.");
                }
                shutdownSequence.retainGenerationHistory(required.history());
                generationHistoryRuntimeRouter = required;
                generationHistoryRoutingRequired = true;
            }
        }
    }

    public void detachGenerationHistoryRuntimeRouter(GenerationHistoryRuntimeRouter router) {
        synchronized (generationHistoryRuntimeRouterLock) {
            if (generationHistoryRuntimeRouter == router) {
                generationHistoryRuntimeRouter = null;
            }
        }
    }

    void closeAttachedGenerationHistoryRuntimeRouter() {
        GenerationHistoryRuntimeRouter attached;
        synchronized (generationHistoryRuntimeRouterLock) {
            attached = generationHistoryRuntimeRouter;
            if (attached == null) {
                return;
            }
        }
        attached.close();
        synchronized (generationHistoryRuntimeRouterLock) {
            if (generationHistoryRuntimeRouter == attached) {
                generationHistoryRuntimeRouter = null;
            }
        }
    }

    boolean beginShutdown() {
        synchronized (lifecycleLock) {
            if (closed) {
                return false;
            }
            lifecycleState = LifecycleState.CLOSING;
            closing.set(true);
            return true;
        }
    }

    public GenerationRuntimeBinding getActiveGenerationRuntimeBinding() {
        EngineRuntime current = runtimeBuilder.requireRuntime("capture the active generation runtime");
        return new GenerationRuntimeBinding(this, current.generation());
    }

    public GenerationRuntimeBinding captureGenerationRuntimeBinding() {
        GenerationRuntimeBinding scoped = generationRuntimeScopes.current();
        return scoped == null ? getActiveGenerationRuntimeBinding() : scoped;
    }

    public GenerationRuntimeScope openGenerationRuntimeScope(GenerationRuntimeBinding binding) {
        GenerationRuntimeBinding required = Objects.requireNonNull(binding, "generation runtime binding");
        synchronized (lifecycleLock) {
            if (required.engine != this) {
                throw new IllegalArgumentException("Generation runtime binding belongs to a different Iris engine.");
            }
            if (retiringGenerationRuntimes.contains(required.runtime)) {
                throw new IllegalStateException("Iris generation runtime binding is retiring.");
            }
            if (!isGenerationRuntimeBindingLive(required)) {
                throw new IllegalStateException("Iris generation runtime binding is closed or no longer owned.");
            }
            return generationRuntimeScopes.open(required);
        }
    }

    public GenerationRuntimeBinding buildDetachedGenerationRuntime(EngineTarget runtimeTarget) {
        return buildDetachedGenerationRuntime(
                runtimeTarget,
                IrisEngineMantle.legacyStorageDirectory(runtimeTarget));
    }

    public GenerationRuntimeBinding buildDetachedGenerationRuntime(
            EngineTarget runtimeTarget,
            Path mantleStorageDirectory
    ) {
        return buildDetachedGenerationRuntime(
                runtimeTarget,
                mantleStorageDirectory,
                GenerationKernelRegistry.standard().current(),
                null);
    }

    public GenerationRuntimeBinding buildDetachedGenerationRuntime(
            EngineTarget runtimeTarget,
            Path mantleStorageDirectory,
            TransitionGenerationPlan transitionPlan
    ) {
        return buildDetachedGenerationRuntime(
                runtimeTarget,
                mantleStorageDirectory,
                GenerationKernelRegistry.standard().current(),
                transitionPlan);
    }

    public GenerationRuntimeBinding buildDetachedGenerationRuntime(
            EngineTarget runtimeTarget,
            Path mantleStorageDirectory,
            GenerationKernelRegistry.Version kernelVersion,
            TransitionGenerationPlan transitionPlan
    ) {
        EngineTarget requiredTarget = Objects.requireNonNull(runtimeTarget, "detached generation target");
        Path requiredMantleStorageDirectory = IrisEngineMantle.normalizeStorageDirectory(mantleStorageDirectory);
        synchronized (lifecycleLock) {
            requireGenerationRuntimeMutation("build a detached generation runtime");
            GenerationRuntime active = runtime.generation();
            if (requiredTarget.getWorld() != active.target().getWorld()) {
                throw new IllegalArgumentException("Detached generation runtime must target this Iris world.");
            }
            IrisData detachedData = requiredTarget.getData();
            if (detachedData == active.data()) {
                throw new IllegalArgumentException("Detached generation runtime requires a detached Iris data loader.");
            }
            if (requiredMantleStorageDirectory.equals(active.mantleStorageDirectory())) {
                throw new IllegalArgumentException("Detached generation runtime requires an exclusive mantle storage directory.");
            }
            for (GenerationRuntime detached : detachedGenerationRuntimes) {
                if (detached.data() == detachedData) {
                    throw new IllegalArgumentException("Detached Iris data loader already belongs to a generation runtime.");
                }
                if (detached.mantleStorageDirectory().equals(requiredMantleStorageDirectory)) {
                    throw new IllegalArgumentException("Mantle storage directory already belongs to a detached generation runtime.");
                }
            }
            GenerationRuntime detached = null;
            try {
                detachedData.registerEngine(this);
                GenerationKernelRegistry.Version requiredKernelVersion = Objects.requireNonNull(
                        kernelVersion,
                        "generation kernel version");
                detached = transitionPlan == null
                        && requiredKernelVersion.equals(GenerationKernelRegistry.standard().current())
                        ? runtimeBuilder.buildDetachedGenerationRuntime(
                                requiredTarget,
                                requiredMantleStorageDirectory)
                        : runtimeBuilder.buildDetachedGenerationRuntime(
                                requiredTarget,
                                requiredMantleStorageDirectory,
                                requiredKernelVersion,
                                transitionPlan);
                detachedGenerationRuntimes.add(detached);
                return new GenerationRuntimeBinding(this, detached);
            } catch (Throwable e) {
                Throwable cleanupFailure = detached == null
                        ? shutdownSequence.closeDetachedTarget(requiredTarget, null)
                        : shutdownSequence.closeDetachedGenerationRuntime(detached, null);
                if (cleanupFailure != null) {
                    e.addSuppressed(cleanupFailure);
                }
                throw EngineShutdownSequence.propagate(e);
            }
        }
    }

    public void closeDetachedGenerationRuntime(GenerationRuntimeBinding binding) {
        GenerationRuntimeBinding required = Objects.requireNonNull(binding, "detached generation runtime binding");
        GenerationRuntime retired;
        synchronized (lifecycleLock) {
            if (required.engine != this) {
                throw new IllegalArgumentException("Generation runtime binding belongs to a different Iris engine.");
            }
            EngineRuntime current = runtime;
            if (current != null && current.generation() == required.runtime) {
                throw new IllegalArgumentException("The active Iris generation runtime cannot be closed as detached.");
            }
            if (!detachedGenerationRuntimes.contains(required.runtime)) {
                return;
            }
            if (!retiringGenerationRuntimes.add(required.runtime)) {
                throw new IllegalStateException("Detached Iris generation runtime is already retiring.");
            }
            retired = required.runtime;
        }
        Throwable failure = retireGenerationRuntimeCaches(retired.cacheId(), null);
        try {
            failure = shutdownSequence.closeDetachedGenerationRuntime(retired, failure);
        } finally {
            synchronized (lifecycleLock) {
                detachedGenerationRuntimes.remove(retired);
                retiringGenerationRuntimes.remove(retired);
            }
        }
        if (failure != null) {
            throw new IllegalStateException("Failed to close a detached Iris generation runtime.", failure);
        }
    }

    public void addGenerationRuntimeRetirementListener(IntConsumer listener) {
        generationRuntimeRetirementListeners.add(Objects.requireNonNull(listener, "generation runtime retirement listener"));
    }

    public void removeGenerationRuntimeRetirementListener(IntConsumer listener) {
        generationRuntimeRetirementListeners.remove(listener);
    }

    Throwable retireGenerationRuntimeCaches(int runtimeId, Throwable failure) {
        nativeStructureVolumeMemo.evictRuntime(runtimeId);
        for (IntConsumer listener : generationRuntimeRetirementListeners) {
            try {
                listener.accept(runtimeId);
            } catch (Throwable listenerFailure) {
                if (failure == null) {
                    failure = listenerFailure;
                } else if (failure != listenerFailure) {
                    failure.addSuppressed(listenerFailure);
                }
            }
        }
        return failure;
    }

    public void setDefaultGenerationRuntime(GenerationRuntimeBinding binding) {
        GenerationRuntimeBinding required = Objects.requireNonNull(binding, "generation runtime binding");
        synchronized (lifecycleLock) {
            requireGenerationRuntimeMutation("select the default generation runtime");
            if (required.engine != this) {
                throw new IllegalArgumentException("Generation runtime binding belongs to a different Iris engine.");
            }
            EngineRuntime current = runtime;
            GenerationRuntime previous = current.generation();
            if (previous == required.runtime) {
                return;
            }
            if (!detachedGenerationRuntimes.contains(required.runtime)) {
                throw new IllegalStateException("Default Iris generation runtime must be live and engine-owned.");
            }
            if (retiringGenerationRuntimes.contains(required.runtime)) {
                throw new IllegalStateException("Default Iris generation runtime cannot be retiring.");
            }
            detachedGenerationRuntimes.add(previous);
            detachedGenerationRuntimes.remove(required.runtime);
            runtime = current.withGeneration(required.runtime);
            publishedTarget = required.runtime.target();
            nativeStructureVolumeMemo.clear();
        }
    }

    private boolean isPregeneratorActiveForThisWorld() {
        return platformHooks.isPregeneratorActive(this);
    }

    void savePrefetchOnce() {
        if (prefetchSaveStarted.compareAndSet(false, true)) {
            try {
                getData().savePrefetch(this);
            } catch (Throwable e) {
                prefetchSaveStarted.set(false);
                throw EngineShutdownSequence.propagate(e);
            }
        }
    }

    @Override
    public SeedManager getSeedManager() {
        RuntimeAssembly assembly = runtimeAssembly.get();
        if (assembly != null) {
            return assembly.seedManager;
        }
        GenerationRuntime current = selectedGenerationRuntime();
        return current == null ? seedManager : current.seedManager();
    }

    @Override
    public IrisComplex getComplex() {
        RuntimeAssembly assembly = runtimeAssembly.get();
        if (assembly != null && assembly.complex != null) {
            return assembly.complex;
        }
        GenerationRuntime current = selectedGenerationRuntime();
        return current == null ? null : current.complex();
    }

    @Override
    public EngineTarget getTarget() {
        RuntimeAssembly assembly = runtimeAssembly.get();
        if (assembly != null) {
            return assembly.target;
        }
        GenerationRuntime current = selectedGenerationRuntime();
        return current == null ? publishedTarget : current.target();
    }

    @Override
    public IrisData getData() {
        BiomeEnvironmentBinding environment = biomeEnvironmentScopes.get();
        if (environment != null && !hasGenerationRuntimeScope()) {
            return environment.environment.data();
        }
        RuntimeAssembly assembly = runtimeAssembly.get();
        if (assembly != null) {
            return assembly.target.getData();
        }
        GenerationRuntime current = selectedGenerationRuntime();
        return current == null ? publishedTarget.getData() : current.data();
    }

    @Override
    public IrisDimension getDimension() {
        BiomeEnvironmentBinding environment = biomeEnvironmentScopes.get();
        if (environment != null && !hasGenerationRuntimeScope()) {
            return environment.environment.dimension();
        }
        RuntimeAssembly assembly = runtimeAssembly.get();
        if (assembly != null) {
            return assembly.target.getDimension();
        }
        GenerationRuntime current = selectedGenerationRuntime();
        return current == null ? publishedTarget.getDimension() : current.dimension();
    }

    @Override
    public EngineMantle getMantle() {
        RuntimeAssembly assembly = runtimeAssembly.get();
        if (assembly != null && assembly.mantle != null) {
            return assembly.mantle;
        }
        GenerationRuntime current = selectedGenerationRuntime();
        return current == null ? null : current.mantle();
    }

    @Override
    public EngineMode getMode() {
        RuntimeAssembly assembly = runtimeAssembly.get();
        if (assembly != null && assembly.mode != null) {
            return assembly.mode;
        }
        GenerationRuntime current = requireSelectedGenerationRuntime("access the engine mode");
        return current.mode();
    }

    @Override
    public EngineEffects getEffects() {
        RuntimeAssembly assembly = runtimeAssembly.get();
        if (assembly != null && assembly.effects != null) {
            return assembly.effects;
        }
        EngineRuntime current = runtime;
        return current == null ? null : current.effects();
    }

    @Override
    public EngineWorldManager getWorldManager() {
        RuntimeAssembly assembly = runtimeAssembly.get();
        if (assembly != null && assembly.worldManager != null) {
            return assembly.worldManager;
        }
        EngineRuntime current = runtime;
        return current == null ? null : current.worldManager();
    }

    @Override
    public UpperDimensionContext getUpperContext() {
        RuntimeAssembly assembly = runtimeAssembly.get();
        if (assembly != null) {
            return assembly.upperContext;
        }
        GenerationRuntime current = selectedGenerationRuntime();
        return current == null ? null : current.upperContext();
    }

    @Override
    public DimensionStackContext getDimensionStackContext() {
        RuntimeAssembly assembly = runtimeAssembly.get();
        if (assembly != null) {
            return assembly.dimensionStackContext;
        }
        GenerationRuntime current = selectedGenerationRuntime();
        return current == null ? null : current.dimensionStackContext();
    }

    @Override
    public CompletableFuture<Long> getHash32() {
        RuntimeAssembly assembly = runtimeAssembly.get();
        if (assembly != null && assembly.hash32 != null) {
            return assembly.hash32;
        }
        GenerationRuntime current = requireSelectedGenerationRuntime("access the pack hash");
        return current.hash32();
    }

    @Override
    public double getMaxBiomeObjectDensity() {
        GenerationRuntime current = selectedGenerationRuntime();
        return current == null ? 0D : current.biomeMaxes().objectDensity();
    }

    @Override
    public double getMaxBiomeLayerDensity() {
        GenerationRuntime current = selectedGenerationRuntime();
        return current == null ? 0D : current.biomeMaxes().layerDensity();
    }

    @Override
    public double getMaxBiomeDecoratorDensity() {
        GenerationRuntime current = selectedGenerationRuntime();
        return current == null ? 0D : current.biomeMaxes().decoratorDensity();
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    public boolean isClosing() {
        return closing.get();
    }

    @Override
    public void recycle() {
        if (closing.get() || closed) {
            return;
        }
        if (!cleanLatch.flip()) {
            return;
        }

        if (cleaning.get()) {
            cleanLatch.flipDown();
            return;
        }

        cleaning.set(true);

        if (!backgroundTasks.scheduleTrackedTask(() -> {
            try {
                getData().getObjectLoader().clean();
            } catch (Throwable e) {
                IrisLogging.reportError(e);
                IrisLogging.error("Cleanup failed! Enable debug to see stacktrace.");
            }

            cleaning.lazySet(false);
        })) {
            cleaning.set(false);
        }
    }

    @BlockCoordinates
    @Override
    public void generate(int x, int z, Hunk<PlatformBlockState> vblocks, Hunk<PlatformBiome> vbiomes, boolean multicore) throws WrongEngineBroException {
        awaitGenerationCacheWarm();
        try (GenerationHistoryRuntimeRouter.CoordinateScope historyScope =
                     openGenerationHistoryCoordinateScope(x, z);
             GenerationSessionLease lease = acquireGenerationLease("chunk_generate");
             IrisContext.Scope generationScope = IrisContext.open(this, lease.sessionId(), null)) {
            getEngineData().getStatistics().generatedChunk();
            PrecisionStopwatch p = PrecisionStopwatch.start();
            Hunk<PlatformBlockState> blocks = vblocks.listen((xx, y, zz, t) -> catchBlockUpdates(x + xx, y, z + zz, t));

            if (getDimension().isDebugChunkCrossSections() && ((x >> 4) % getDimension().getDebugCrossSectionsMod() == 0 || (z >> 4) % getDimension().getDebugCrossSectionsMod() == 0)) {
                PlatformBlockState crossSection = B.getState("CRYING_OBSIDIAN");
                for (int i = 0; i < 16; i++) {
                    for (int j = 0; j < 16; j++) {
                        blocks.set(i, 0, j, crossSection);
                    }
                }
            } else {
                EngineMode activeMode = getMode();
                activeMode.generate(x, z, blocks, vbiomes, multicore, lease.sessionId());
            }

            boolean skipRealFlag = platformHooks.shouldBypassMantleStages(this);
            if (!skipRealFlag) {
                getMantle().getMantle().flag(x >> 4, z >> 4, MantleFlag.REAL, true);
            }
            getMetrics().getTotal().put(p.getMilliseconds());
            generated.incrementAndGet();

            if (generated.get() == 661 && !isPregeneratorActiveForThisWorld()) {
                backgroundTasks.scheduleTrackedTask(this::savePrefetchOnce);
            }
        } catch (GenerationSessionException e) {
            throw e;
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            fail("Failed to generate " + x + ", " + z, e);
            if (e instanceof WrongEngineBroException wrongEngine) {
                throw wrongEngine;
            }
            throw new WrongEngineBroException("Failed to generate chunk at " + x + ", " + z + ".", e);
        }
    }

    @Override
    public GenerationSessionManager getGenerationSessions() {
        return generationSessions;
    }

    @Override
    public void saveEngineData() {
        engineDataStore.saveEngineData();
    }

    @Override
    public void blockUpdatedMetric() {
        bud.incrementAndGet();
    }

    @Override
    public IrisBiome getFocus() {
        if (getDimension().getFocus() == null || getDimension().getFocus().trim().isEmpty()) {
            return null;
        }

        IrisBiome focus = getData().getBiomeLoader().load(getDimension().getFocus());
        return focus == null || focus.isCompatExcluded() ? null : focus;
    }

    @Override
    public IrisRegion getFocusRegion() {
        if (getDimension().getFocusRegion() == null || getDimension().getFocusRegion().trim().isEmpty()) {
            return null;
        }

        IrisRegion focus = getData().getRegionLoader().load(getDimension().getFocusRegion());
        return focus == null || focus.isCompatExcluded() ? null : focus;
    }

    @Override
    public void fail(String error, Throwable e) {
        failing = true;
        IrisLogging.error(error);
        IrisLogging.reportError(e);
    }

    @Override
    public boolean hasFailed() {
        return failing;
    }

    @Override
    public int getCacheID() {
        RuntimeAssembly assembly = runtimeAssembly.get();
        if (assembly != null) {
            return assembly.cacheId;
        }
        GenerationRuntime current = selectedGenerationRuntime();
        return current == null ? -1 : current.cacheId();
    }

    private GenerationRuntime selectedGenerationRuntime() {
        GenerationRuntimeBinding scoped = generationRuntimeScopes.current();
        if (scoped != null && isGenerationRuntimeBindingLive(scoped)) {
            return scoped.runtime;
        }
        EngineRuntime current = runtime;
        return current == null ? null : current.generation();
    }

    public GenerationKernelRegistry.Version getGenerationKernelVersion() {
        RuntimeAssembly assembly = runtimeAssembly.get();
        if (assembly != null) {
            return assembly.kernelVersion;
        }
        return requireSelectedGenerationRuntime("resolve the generation kernel version").kernelVersion();
    }

    public GenerationKernelRegistry.RuntimeKernel getGenerationRuntimeKernel() {
        RuntimeAssembly assembly = runtimeAssembly.get();
        if (assembly != null) {
            return assembly.runtimeKernel;
        }
        return requireSelectedGenerationRuntime("resolve the executable generation kernel").runtimeKernel();
    }

    public TransitionGenerationPlan getTransitionGenerationPlan() {
        RuntimeAssembly assembly = runtimeAssembly.get();
        if (assembly != null) {
            return assembly.transitionPlan;
        }
        return requireSelectedGenerationRuntime("resolve the generation transition plan").transitionPlan();
    }

    private boolean isGenerationRuntimeBindingLive(GenerationRuntimeBinding binding) {
        EngineRuntime current = runtime;
        return (current != null && current.generation() == binding.runtime)
                || detachedGenerationRuntimes.contains(binding.runtime);
    }

    private GenerationRuntime requireSelectedGenerationRuntime(String operation) {
        GenerationRuntime current = selectedGenerationRuntime();
        if (current == null) {
            throw new IllegalStateException("Cannot " + operation + " without an active Iris runtime for "
                    + getWorld().name() + ".");
        }
        return current;
    }

    void requireRunning(String operation) {
        if (closed || closing.get() || lifecycleState != LifecycleState.RUNNING || runtime == null) {
            throw new IllegalStateException("Cannot " + operation + " while Iris engine " + getWorld().name()
                    + " is " + lifecycleState.name().toLowerCase() + ".");
        }
    }

    private void requireGenerationRuntimeMutation(String operation) {
        if (!closed && studio && studioGenerationTransition && lifecycleState == LifecycleState.HOTLOADING
                && generationHistoryRuntimeRouter != null) {
            return;
        }
        requireRunning(operation);
    }

    private boolean EngineSafe() {
        // Todo: this has potential if done right
        int EngineMCVersion = getEngineData().getStatistics().getMCVersion();
        int EngineIrisVersion = getEngineData().getStatistics().getVersion();
        int MinecraftVersion = IrisPlatforms.get().minecraftVersionNumber();
        int IrisVersion = IrisPlatforms.get().irisVersionNumber();
        if (EngineIrisVersion != IrisVersion) {
            return false;
        }
        if (EngineMCVersion != MinecraftVersion) {
            return false;
        }
        return true;
    }

    enum LifecycleState {
        INITIALIZING,
        RUNNING,
        HOTLOADING,
        CLOSING,
        CLOSED,
        FAILED
    }

    public enum InitializationMode {
        RUNTIME(false, true),
        STUDIO(true, true),
        OBJECT_STUDIO(true, false),
        JIGSAW_STUDIO(true, false);

        private final boolean studio;
        private final boolean warmGenerationCaches;

        InitializationMode(boolean studio, boolean warmGenerationCaches) {
            this.studio = studio;
            this.warmGenerationCaches = warmGenerationCaches;
        }

        public boolean studio() {
            return studio;
        }

        public boolean warmGenerationCaches() {
            return warmGenerationCaches;
        }
    }

    public static final class GenerationRuntimeBinding {
        private final IrisEngine engine;
        private final GenerationRuntime runtime;

        GenerationRuntimeBinding(IrisEngine engine, GenerationRuntime runtime) {
            this.engine = Objects.requireNonNull(engine, "Iris engine");
            this.runtime = Objects.requireNonNull(runtime, "generation runtime");
        }

        GenerationRuntime generationRuntime() {
            return runtime;
        }

        public TransitionGenerationPlan transitionPlan() {
            return runtime.transitionPlan();
        }

        public GenerationKernelRegistry.Version kernelVersion() {
            return runtime.kernelVersion();
        }

        public GenerationKernelRegistry.RuntimeKernel runtimeKernel() {
            return runtime.runtimeKernel();
        }

        public EngineTarget target() {
            return runtime.target();
        }

        public Path mantleStorageDirectory() {
            return runtime.mantleStorageDirectory();
        }

        public int runtimeId() {
            return runtime.cacheId();
        }
    }

    public static final class GenerationRuntimeScope implements AutoCloseable {
        private final GenerationRuntimeScopeState state;
        private final Thread owner;
        private final GenerationRuntimeBinding previous;
        private final GenerationRuntimeBinding installed;
        private boolean closed;

        GenerationRuntimeScope(
                GenerationRuntimeScopeState state,
                Thread owner,
                GenerationRuntimeBinding previous,
                GenerationRuntimeBinding installed
        ) {
            this.state = state;
            this.owner = owner;
            this.previous = previous;
            this.installed = installed;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            state.close(owner, previous, installed);
            closed = true;
        }
    }


    private final class BiomeEnvironmentBinding implements BiomeEnvironment.Scope {
        private final BiomeEnvironment environment;
        private final BiomeEnvironmentBinding previous;
        private final Thread owner = Thread.currentThread();
        private boolean closed;

        private BiomeEnvironmentBinding(BiomeEnvironment environment, BiomeEnvironmentBinding previous) {
            this.environment = environment;
            this.previous = previous;
        }

        @Override
        public void close() {
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException("Biome environment scope must close on its owning thread.");
            }
            if (closed) {
                return;
            }
            if (biomeEnvironmentScopes.get() != this) {
                throw new IllegalStateException("Biome environment scopes must close in reverse order.");
            }
            if (previous == null) {
                biomeEnvironmentScopes.remove();
            } else {
                biomeEnvironmentScopes.set(previous);
            }
            closed = true;
        }
    }

}
