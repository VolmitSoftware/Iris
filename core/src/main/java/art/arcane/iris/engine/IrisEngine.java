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
import art.arcane.iris.core.modules.ModuleServices;
import art.arcane.iris.engine.EngineRuntimeBuilder.RuntimeAssembly;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineEffects;
import art.arcane.iris.engine.framework.EngineMetrics;
import art.arcane.iris.engine.framework.EngineGeneration;
import art.arcane.iris.engine.framework.EnginePlatformHooks;
import art.arcane.iris.engine.framework.NativeStructureVolume;
import art.arcane.iris.engine.framework.NativeStructureVolumeMemo;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.iris.engine.framework.EngineTarget;
import art.arcane.iris.engine.framework.EngineWorldManager;
import art.arcane.iris.engine.framework.GenerationSessionException;
import art.arcane.iris.engine.framework.GenerationSessionLease;
import art.arcane.iris.engine.framework.GenerationSessionManager;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.framework.WrongEngineBroException;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.runtime.IrisEngineData;
import art.arcane.iris.engine.object.world.region.IrisRegion;
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

import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

@Data
public class IrisEngine implements Engine {
    static final long SESSION_DRAIN_TIMEOUT_MILLIS = 15000L;
    private static final long MAINTENANCE_REFUSAL_WARNING_MILLIS = 5000L;
    private static final AtomicLong lastMaintenanceRefusalWarning = new AtomicLong();

    private final AtomicInteger bud;
    private final AtomicInteger buds;
    private final AtomicInteger generated;
    private final AtomicInteger generatedLast;
    private final AtomicDouble perSecond;
    private final AtomicLong lastGPS;
    private final EngineTarget target;
    private final InitializationMode initializationMode;
    private final EngineMantle mantle;
    private final ChronoLatch perSecondLatch;
    private final ChronoLatch perSecondBudLatch;
    private final EngineMetrics metrics;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final GenerationCacheWarmGate generationCacheWarm = new GenerationCacheWarmGate();
    private final boolean studio;
    private final AtomicRollingSequence wallClock;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    final Object lifecycleLock = new Object();
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    final ThreadLocal<RuntimeAssembly> runtimeAssembly = new ThreadLocal<>();
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
    volatile EngineTarget publishedTarget;
    private volatile int parallelism;
    private volatile boolean failing;
    volatile boolean closed;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    volatile LifecycleState lifecycleState;
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
        InitializationMode requiredMode = Objects.requireNonNull(initializationMode, "initialization mode");
        this.initializationMode = requiredMode;
        this.studio = requiredMode.studio();
        this.target = target;
        this.publishedTarget = target;
        this.platformHooks = IrisServices.get(EnginePlatformHooks.class);
        this.generationSessions = new GenerationSessionManager();
        this.closing = new AtomicBoolean(true);
        this.nativeStructureVolumeQueriesEnabled = new AtomicBoolean(!requiredMode.studio());
        this.lifecycleState = LifecycleState.INITIALIZING;
        this.closed = false;
        this.failing = false;
        getEngineData();
        verifySeed();
        this.seedManager = new SeedManager(target.getWorld().getRawWorldSeed());
        bud = new AtomicInteger(0);
        buds = new AtomicInteger(0);
        metrics = new EngineMetrics(32);
        cleanLatch = new ChronoLatch(10000);
        generatedLast = new AtomicInteger(0);
        perSecond = new AtomicDouble(0);
        perSecondLatch = new ChronoLatch(1000, false);
        perSecondBudLatch = new ChronoLatch(1000, false);
        wallClock = new AtomicRollingSequence(32);
        lastGPS = new AtomicLong(M.ms());
        generated = new AtomicInteger(0);
        long _t0 = M.ms();
        mantle = new IrisEngineMantle(this);
        IrisLogging.debug("[IrisEngine timing] new IrisEngineMantle=" + (M.ms() - _t0) + "ms");
        cleaning = new AtomicBoolean(false);
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
            getData().loadPrefetch(this);
            IrisLogging.debug("[IrisEngine timing] loadPrefetch=" + (M.ms() - _t0) + "ms");
            logStudioInitializationPhase("load_prefetch", phaseStartedAt, false);
            try {
                StructureIndexService.writeOnce(getData());
            } catch (Throwable e) {
                IrisLogging.reportError(e);
            }
            IrisLogging.notice("Engine init: " + target.getWorld().name() + "/" + target.getDimension().getLoadKey() + " seed=" + getSeedManager().getSeed());
            _t0 = M.ms();
            phaseStartedAt = System.nanoTime();
            EngineRuntime initialRuntime = runtimeBuilder.buildRuntime();
            runtimeBuilder.publishRuntime(initialRuntime, null);
            IrisLogging.debug("[IrisEngine timing] setupEngine total=" + (M.ms() - _t0) + "ms");
            logStudioInitializationPhase("build_runtime", phaseStartedAt, false);
            phaseStartedAt = System.nanoTime();
            if (requiredMode.warmGenerationCaches()) {
                if (requiredMode.studio()) {
                    startGenerationCacheWarm(phaseStartedAt);
                } else {
                    warmGenerationCaches(phaseStartedAt);
                }
            } else {
                generationCacheWarm.skipFirstWorld();
                logStudioInitializationPhase("generation_cache_warm", phaseStartedAt, true);
            }
            EngineTickRegistry.registerTicking(this);
        } catch (Throwable e) {
            shutdownSequence.cleanupFailedConstruction(e);
            throw new IllegalStateException("Failed to initialize Iris engine for world '" + target.getWorld().name() + "'.", e);
        }
        IrisLogging.debug("Engine Initialized " + getCacheID());
    }

    public void awaitGenerationCacheWarm() {
        generationCacheWarm.await();
    }

    public boolean isGenerationCacheWarmPending() {
        return generationCacheWarm.isPending();
    }

    /**
     * Warms the modules' caches of a world that started after the engine initialised: the one a hotload built, or the
     * one an in-place restart replaced. The first world is warmed by initialisation. Same mode as there: studio warms in
     * the background, server worlds before this returns, and generation waits for it either way.
     */
    void warmRestartedWorld() {
        if (!initializationMode.warmGenerationCaches()) {
            return;
        }
        CompletableFuture<Void> warm = generationCacheWarm.beginRestartedWorld();
        if (!studio) {
            warmModulesThenComplete(warm);
            return;
        }
        if (!backgroundTasks.scheduleTrackedTask(() -> warmModulesThenComplete(warm))) {
            warm.complete(null);
            IrisLogging.warn("Iris background task admission closed before module warm-up; caches are built when first used.");
        }
    }

    private void warmModulesThenComplete(CompletableFuture<Void> warm) {
        try {
            warmModules();
        } finally {
            warm.complete(null);
        }
    }

    private void warmModules() {
        try {
            getGeneration().warmModules();
        } catch (RuntimeException failure) {
            IrisLogging.reportError(failure);
            IrisLogging.warn("Module warm-up failed, so its caches are built when first used: " + failure.getMessage());
        }
    }

    private void logStudioInitializationPhase(String phase, long startedAtNanos, boolean skipped) {
        if (!studio) {
            return;
        }
        IrisLogging.info("[Studio engine timing] world=%s kind=%s phase=%s duration=%dms skipped=%s",
                target.getWorld().name(),
                initializationMode.name().toLowerCase(Locale.ROOT),
                phase,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos),
                Boolean.toString(skipped));
    }

    private void startGenerationCacheWarm(long phaseStartedAtNanos) {
        if (!backgroundTasks.scheduleTrackedTask(() -> warmGenerationCaches(phaseStartedAtNanos))) {
            throw new IllegalStateException("Iris background task admission closed before generation cache warming.");
        }
    }

    private void warmGenerationCaches(long phaseStartedAtNanos) {
        long startedAtMillis = M.ms();
        try {
            generationCacheWarm.warmFirstWorld(this::warmModules);
        } finally {
            IrisLogging.debug("[IrisEngine timing] cache warm total=" + (M.ms() - startedAtMillis) + "ms");
            logStudioInitializationPhase("generation_cache_warm", phaseStartedAtNanos, false);
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
        try (GenerationSessionLease lease = acquireGenerationLease("matter_generate");
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
        } catch (GenerationSessionException e) {
            throw new IllegalStateException("Matter generation was rejected by the Iris lifecycle.", e);
        }
    }

    @Override
    public Set<String> getObjectsAt(int x, int z) {
        return ModuleServices.objectsAt(this, x, z);
    }

    @Override
    public Set<Pair<String, BlockPos>> getPOIsAt(int chunkX, int chunkY) {
        Set<Pair<String, BlockPos>> pois = new HashSet<>();
        getMantle().getMantle().iterateChunk(chunkX, chunkY, MatterStructurePOI.class, (x, y, z, d) -> pois.add(new Pair<>(d.getType(), new BlockPos(x, y, z))));
        return pois;
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

    public void hotloadComplex() {
        hotloader.hotloadComplex();
    }

    public void hotloadSilently() {
        nativeStructureVolumeMemo.clear();
        hotloader.hotloadSilently();
    }

    @Override
    public KList<NativeStructureVolume> getNativeStructureVolumes(int minX, int minZ, int maxX, int maxZ) {
        if (!nativeStructureVolumeQueriesEnabled.get()) {
            return NativeStructureVolume.NONE;
        }
        return nativeStructureVolumeMemo.volumes(this, platformHooks, minX, minZ, maxX, maxZ);
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
    public IrisComplex getComplex() {
        RuntimeAssembly assembly = runtimeAssembly.get();
        if (assembly != null && assembly.complex != null) {
            return assembly.complex;
        }
        EngineRuntime current = runtime;
        return current == null ? null : current.complex();
    }

    @Override
    public EngineTarget getTarget() {
        RuntimeAssembly assembly = runtimeAssembly.get();
        if (assembly != null) {
            return assembly.target;
        }
        EngineRuntime current = runtime;
        return current == null ? publishedTarget : current.target();
    }

    @Override
    public void packResourcesReloaded() {
        EngineRuntime current = runtime;
        if (current == null) {
            return;
        }
        current.generation().restartModules();
        warmRestartedWorld();
    }

    @Override
    public EngineGeneration getGeneration() {
        RuntimeAssembly assembly = runtimeAssembly.get();
        if (assembly != null && assembly.generation != null) {
            return assembly.generation;
        }
        EngineRuntime current = runtimeBuilder.requireRuntime("access the engine generation");
        return current.generation();
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
        EngineRuntime current = runtime;
        return current == null ? null : current.upperContext();
    }

    @Override
    public CompletableFuture<Long> getHash32() {
        RuntimeAssembly assembly = runtimeAssembly.get();
        if (assembly != null && assembly.hash32 != null) {
            return assembly.hash32;
        }
        EngineRuntime current = runtimeBuilder.requireRuntime("access the pack hash");
        return current.hash32();
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
        if (platformHooks.shouldRefuseGeneration(this)) {
            refuseMaintenanceGeneration(x >> 4, z >> 4);
        }
        try (GenerationSessionLease lease = acquireGenerationLease("chunk_generate");
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
                getGeneration().generate(x, z, blocks, vbiomes, multicore, lease.sessionId());
            }

            getMantle().getMantle().flag(x >> 4, z >> 4, MantleFlag.REAL, true);
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

    private void refuseMaintenanceGeneration(int chunkX, int chunkZ) throws GenerationSessionException {
        long now = System.currentTimeMillis();
        long last = lastMaintenanceRefusalWarning.get();
        if (now - last >= MAINTENANCE_REFUSAL_WARNING_MILLIS && lastMaintenanceRefusalWarning.compareAndSet(last, now)) {
            IrisLogging.warn("Iris refused chunk " + chunkX + "," + chunkZ + " in " + getWorld().name()
                    + ": world maintenance forbids mantle generation, the chunk stays ungenerated");
        }
        throw new GenerationSessionException("Iris refused chunk " + chunkX + "," + chunkZ + " in "
                + getWorld().name() + " during world maintenance.", true);
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

        return getData().getBiomeLoader().load(getDimension().getFocus());
    }

    @Override
    public IrisRegion getFocusRegion() {
        if (getDimension().getFocusRegion() == null || getDimension().getFocusRegion().trim().isEmpty()) {
            return null;
        }

        return getData().getRegionLoader().load(getDimension().getFocusRegion());
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
        EngineRuntime current = runtime;
        return current == null ? -1 : current.cacheId();
    }

    void requireRunning(String operation) {
        if (closed || closing.get() || lifecycleState != LifecycleState.RUNNING || runtime == null) {
            throw new IllegalStateException("Cannot " + operation + " while Iris engine " + getWorld().name()
                    + " is " + lifecycleState.name().toLowerCase() + ".");
        }
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

}
