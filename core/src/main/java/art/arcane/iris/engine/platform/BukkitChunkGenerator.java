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

package art.arcane.iris.engine.platform;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.IrisWorlds;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.nms.INMS;
import art.arcane.iris.core.runtime.ObjectStudioActivation;
import art.arcane.iris.core.runtime.ObjectStudioLayout;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioActivation;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioSession;
import art.arcane.iris.core.service.StudioSVC;
import art.arcane.iris.core.tools.IrisToolbelt;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.IrisEngine;
import art.arcane.iris.engine.data.cache.AtomicCache;
import art.arcane.iris.engine.data.chunk.TerrainChunk;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineTarget;
import art.arcane.iris.engine.framework.GenerationSessionException;
import art.arcane.iris.engine.framework.GenerationSessionLease;
import art.arcane.iris.engine.framework.WrongEngineBroException;
import art.arcane.iris.engine.history.GenerationActivation;
import art.arcane.iris.engine.history.GenerationEpoch;
import art.arcane.iris.engine.history.GenerationHistory;
import art.arcane.iris.engine.history.GenerationAdmission;
import art.arcane.iris.engine.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.engine.history.IrisBoundarySignatureSampler;
import art.arcane.iris.engine.history.TransitionGenerationPlan;
import art.arcane.iris.engine.hydrology.runtime.IrisHydrologyRuntime;
import art.arcane.iris.engine.object.IrisDimensionContractException;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisDimensionRuntimeContract;
import art.arcane.iris.engine.object.IrisWorld;
import art.arcane.iris.engine.object.StudioMode;
import art.arcane.iris.engine.platform.studio.StudioGenerator;
import art.arcane.iris.engine.platform.studio.generators.JigsawStudioGenerator;
import art.arcane.iris.platform.bukkit.BukkitWorldBinding;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.util.common.format.C;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.M;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.iris.util.project.hunk.view.ChunkDataHunkHolder;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.project.context.IrisContext;
import art.arcane.volmlib.util.io.ReactiveFolder;
import art.arcane.volmlib.util.scheduling.ChronoLatch;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.scheduling.Looper;
import io.papermc.lib.PaperLib;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

@EqualsAndHashCode(callSuper = true)
@Data
public class BukkitChunkGenerator extends ChunkGenerator implements PlatformChunkGenerator, Listener {
    private static final int LOAD_LOCKS = Runtime.getRuntime().availableProcessors() * 4;
    private static final long HOTLOAD_LOOP_DELAY_MS = 250L;
    private static final long HOTLOAD_MAINTENANCE_DELAY_MS = 4000L;
    private final GenerationStageGate loadLock;
    private final IrisWorld world;
    private final File dataLocation;
    private final String dimensionKey;
    private final @Nullable GenerationHistory generationHistory;
    private final ReactiveFolder folder;
    private final ReentrantLock lock = new ReentrantLock();
    private final KList<BlockPopulator> populators;
    private final ChronoLatch hotloadChecker;
    private final AtomicBoolean setup;
    private final boolean studio;
    private final AtomicBoolean studioEntryBootstrapActive;
    private final AtomicInteger a = new AtomicInteger(0);
    private final CompletableFuture<Integer> spawnChunks = new CompletableFuture<>();
    private final CompletableFuture<Void> initialSpawnReady = new CompletableFuture<>();
    private final AtomicCache<EngineTarget> targetCache = new AtomicCache<>();
    private final AtomicReference<CompletableFuture<Void>> closeFuture = new AtomicReference<>();
    private volatile Engine engine;
    private volatile Looper hotloader;
    private volatile StudioMode lastMode;
    private volatile UUID lastJigsawStudioRequestId;
    private volatile boolean jigsawStudioActive;
    private volatile boolean objectStudioActive;
    private volatile DummyBiomeProvider dummyBiomeProvider;
    private volatile Throwable initializationFailure;
    private volatile IrisDimension validatedDimension;
    private volatile IrisDimensionRuntimeContract validatedWorldContract;
    private volatile EngineTarget startupTarget;
    private volatile CompletableFuture<Void> startupContentReady = CompletableFuture.completedFuture(null);
    private volatile CompletableFuture<Void> startupReady = CompletableFuture.completedFuture(null);
    private volatile boolean closing;
    @Setter
    private volatile StudioGenerator studioGenerator;

    public BukkitChunkGenerator(
            IrisWorld world,
            boolean studio,
            File dataLocation,
            String dimensionKey,
            @Nullable GenerationHistory generationHistory
    ) {
        setup = new AtomicBoolean(false);
        studioGenerator = null;
        dummyBiomeProvider = new DummyBiomeProvider();
        populators = new KList<>();
        loadLock = new GenerationStageGate(LOAD_LOCKS, () -> closing);
        this.world = world;
        this.hotloadChecker = new ChronoLatch(1000, false);
        this.studio = studio;
        this.studioEntryBootstrapActive = new AtomicBoolean(studio);
        this.dataLocation = dataLocation;
        this.dimensionKey = dimensionKey;
        this.generationHistory = generationHistory;
        this.folder = new ReactiveFolder(
                dataLocation,
                (_a, _b, _c) -> hotloadFromWatcher(),
                new KList<>(".iob", ".json", ".png"),
                new KList<>(".iris"),
                new KList<>()
        );
        this.initializationFailure = null;
        this.jigsawStudioActive = false;
        this.validatedDimension = null;
        this.validatedWorldContract = null;
        this.closing = false;
        Bukkit.getServer().getPluginManager().registerEvents(this, BukkitPlatform.plugin());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onWorldInit(WorldInitEvent event) {
        if (!Objects.equals(world.identity(), WorldIdentity.key(event.getWorld()).toString())) return;
        if (event.getWorld().getGenerator() != this) {
            // A generator leaked by an earlier failed creation of a same-key world; only the
            // instance Bukkit actually bound may attach an engine to this world.
            BukkitPlatform.volmitPlugin().unregisterListener(this);
            return;
        }
        BukkitPlatform.volmitPlugin().unregisterListener(this);
        world.setRawWorldSeed(event.getWorld().getSeed());
        if (startupTarget != null) {
            startupContentReady.whenComplete((ignored, failure) ->
                    J.s(() -> resumeDeferredStartup(event.getWorld(), failure)));
            return;
        }
        if (initialize(event.getWorld())) return;

        IrisLogging.warn("Failed to get Engine for " + event.getWorld().getName() + " re-trying...");
        J.s(() -> {
            if (!initialize(event.getWorld())) {
                IrisLogging.error("Failed to get Engine for " + event.getWorld().getName() + "!");
            }
        }, 10);
    }

    public void deferStartup(EngineTarget target, CompletableFuture<Void> contentReady) {
        if (setup.get() || startupTarget != null) {
            throw new IllegalStateException("Iris generator startup has already begun.");
        }
        startupTarget = Objects.requireNonNull(target);
        startupContentReady = Objects.requireNonNull(contentReady);
        startupReady = new CompletableFuture<>();
    }

    private void resumeDeferredStartup(World runtimeWorld, Throwable failure) {
        lock.lock();
        try {
            closeStartupTarget();
            if (closing) {
                startupReady.completeExceptionally(new IllegalStateException("Iris generator closed during startup."));
                return;
            }
            if (failure != null) {
                initializationFailure = failure;
                spawnChunks.completeExceptionally(failure);
                initialSpawnReady.completeExceptionally(failure);
                startupReady.completeExceptionally(failure);
                IrisLogging.reportError("External block providers did not become available for Iris world '"
                        + world.name() + "'; generation remains locked.", failure);
                Bukkit.shutdown();
                return;
            }
            if (!initialize(runtimeWorld)) {
                throw new IllegalStateException("Iris engine initialization did not complete for '" + world.name() + "'.");
            }
            startupReady.complete(null);
        } catch (RuntimeException | Error initializationError) {
            initializationFailure = initializationError;
            spawnChunks.completeExceptionally(initializationError);
            initialSpawnReady.completeExceptionally(initializationError);
            startupReady.completeExceptionally(initializationError);
            Bukkit.shutdown();
            throw initializationError;
        } finally {
            lock.unlock();
        }
    }

    private void closeStartupTarget() {
        EngineTarget target = startupTarget;
        startupTarget = null;
        if (target != null) {
            target.getData().close();
        }
    }

    private void requireStartupContentReady() {
        if (startupReady.isCompletedExceptionally()) {
            startupReady.join();
        }
        boolean initializationPending = startupTarget != null
                || !startupReady.isDone() && !lock.isHeldByCurrentThread();
        requireStartupContentReady(startupContentReady, initializationPending);
    }

    static void requireStartupContentReady(CompletableFuture<Void> ready, boolean initializationPending) {
        if (!ready.isDone() || initializationPending) {
            throw new IllegalStateException("Iris generation is waiting for external block providers to initialize.");
        }
        ready.join();
    }

    private boolean initialize(World world) {
        try {
            Engine engine = getEngine(world);
            if (engine == null) {
                return false;
            }
            INMS.get().inject(world.getSeed(), engine, world);
            engine.getPlatformHooks().applyWorldBoundary(engine);
            IrisLogging.debug("Injected Iris Biome Source into " + world.getName());
            prefetchSpawnHydrology(engine, world);
            if (!studio) {
                J.sfut(() -> updateSpawnLocation(world), 1)
                        .whenComplete((ignored, failure) -> {
                            if (failure != null) {
                                initialSpawnReady.completeExceptionally(failure);
                            }
                        });
            } else {
                initialSpawnReady.complete(null);
            }
        } catch (Throwable e) {
            initializationFailure = e;
            spawnChunks.completeExceptionally(e);
            initialSpawnReady.completeExceptionally(e);
            IrisLogging.reportError(e);
            IrisLogging.error("Failed to initialize Iris generator for " + world.getName());
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (e instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Failed to initialize Iris for world '" + world.getName() + "'", e);
        }
        spawnChunks.complete(INMS.get().getSpawnChunkCount(world));
        BukkitPlatform.volmitPlugin().unregisterListener(this);
        if (shouldPersistWorldRegistration(studio)) {
            IrisWorlds.get().put(WorldIdentity.serialize(world), dimensionKey);
        }
        return true;
    }

    @Nullable
    @Override
    public Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        return getInitialSpawnLocation(world);
    }

    public Location getInitialSpawnLocation(World world) {
        int minY = world.getMinHeight() + 1;
        int maxY = world.getMaxHeight() - 2;
        int y = Math.max(minY, Math.min(maxY, 96));
        return new Location(world, 0.5D, y, 0.5D);
    }

    /** Starts nearest-first hydrology planning around a new world's initial spawn. */
    private CompletableFuture<Void> prefetchSpawnHydrology(Engine engine, World world) {
        if (usesFlatStudioTerrain()) {
            return CompletableFuture.completedFuture(null);
        }
        IrisComplex complex = engine.getComplex();
        IrisHydrologyRuntime hydrology = complex == null ? null : complex.getHydrologyRuntime();
        if (hydrology == null) {
            return CompletableFuture.completedFuture(null);
        }
        Location spawn = getInitialSpawnLocation(world);
        return requestChunkAsync(world, spawn.getBlockX() >> 4, spawn.getBlockZ() >> 4, false)
                .exceptionally(failure -> {
                    if (closing || this.engine != engine || engine.isClosing() || engine.isClosed()) {
                        return null;
                    }
                    throw new CompletionException(failure);
                })
                .thenAcceptAsync(chunk -> {
                    if (chunk == null) {
                        startSpawnHydrology(engine, hydrology, spawn);
                    }
                })
                .whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        IrisLogging.reportError("Failed to prefetch spawn hydrology for " + world.getName(), failure);
                    }
                });
    }

    private void startSpawnHydrology(Engine expectedEngine, IrisHydrologyRuntime hydrology, Location spawn) {
        if (closing) {
            return;
        }
        GenerationStagePermit permit;
        try {
            permit = acquireGenerationStage("spawn_hydrology_prefetch");
        } catch (IllegalStateException failure) {
            if (closing && failure.getCause() == null) {
                return;
            }
            throw failure;
        }
        try (permit) {
            if (engine != expectedEngine || expectedEngine.isClosing() || expectedEngine.isClosed() || usesFlatStudioTerrain()) {
                return;
            }
            IrisComplex complex = expectedEngine.getComplex();
            if (complex == null || complex.getHydrologyRuntime() != hydrology) {
                return;
            }
            int spawnX = spawn.getBlockX();
            int spawnZ = spawn.getBlockZ();
            if (studio && expectedEngine.getDimension().getStudioMode() == StudioMode.NORMAL
                    && expectedEngine instanceof IrisEngine irisEngine) {
                irisEngine.startStudioEntryHydrology(spawnX, spawnZ);
                return;
            }
            int reach = Math.max(16, hydrology.settings().routing().tileSize() / 2);
            hydrology.prefetchArea(
                    spawnX - reach,
                    spawnZ - reach,
                    spawnX + reach - 1,
                    spawnZ + reach - 1,
                    spawnX,
                    spawnZ
            );
        }
    }

    private void updateSpawnLocation(World world) {
        try {
            Location initialSpawn = getInitialSpawnLocation(world);
            int chunkX = initialSpawn.getBlockX() >> 4;
            int chunkZ = initialSpawn.getBlockZ() >> 4;
            CompletableFuture<Chunk> chunkFuture = requestChunkAsync(world, chunkX, chunkZ, true);
            if (chunkFuture == null) {
                initialSpawnReady.completeExceptionally(new IllegalStateException(
                        "Initial spawn chunk request returned no completion future for world \""
                                + world.getName() + "\"."));
                return;
            }

            chunkFuture.whenComplete((chunk, failure) -> {
                try {
                    if (failure != null) {
                        initialSpawnReady.completeExceptionally(failure);
                        return;
                    }
                    if (chunk == null) {
                        throw new IllegalStateException(
                                "Initial spawn chunk request completed without a chunk for world \""
                                        + world.getName() + "\".");
                    }
                    J.runRegionFuture(
                            chunk.getWorld(),
                            chunk.getX(),
                            chunk.getZ(),
                            () -> completeSpawnLocation(chunk.getWorld(), initialSpawn))
                            .whenComplete((ignored, scheduleFailure) -> {
                                if (scheduleFailure != null) {
                                    initialSpawnReady.completeExceptionally(scheduleFailure);
                                }
                            });
                } catch (Throwable callbackFailure) {
                    initialSpawnReady.completeExceptionally(new IllegalStateException(
                            "Initial spawn preparation failed for world \""
                                    + world.getName() + "\".",
                            callbackFailure));
                }
            });
        } catch (Throwable failure) {
            initialSpawnReady.completeExceptionally(failure);
        }
    }

    private void completeSpawnLocation(World world, Location initialSpawn) {
        try {
            applySpawnLocation(world, initialSpawn);
            initialSpawnReady.complete(null);
        } catch (Throwable failure) {
            initialSpawnReady.completeExceptionally(failure);
        }
    }

    private void applySpawnLocation(World world, Location initialSpawn) {
        Location currentSpawn = world.getSpawnLocation();
        if (currentSpawn == null) {
            return;
        }

        if (!studio && (currentSpawn.getBlockX() != initialSpawn.getBlockX() || currentSpawn.getBlockZ() != initialSpawn.getBlockZ())) {
            return;
        }

        int minY = world.getMinHeight() + 1;
        int maxY = world.getMaxHeight() - 2;
        int y = resolveInitialSpawnY(world, initialSpawn, minY, maxY);
        world.setSpawnLocation(new Location(world, initialSpawn.getX(), y, initialSpawn.getZ(), initialSpawn.getYaw(), initialSpawn.getPitch()));
    }

    private int resolveInitialSpawnY(World world, Location initialSpawn, int minY, int maxY) {
        return Math.max(minY, Math.min(maxY, world.getHighestBlockYAt(initialSpawn) + 1));
    }

    @Override
    public CompletableFuture<Void> getInitialSpawnReady() {
        return initialSpawnReady;
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<Chunk> requestChunkAsync(World world, int chunkX, int chunkZ, boolean generate) {
        try {
            Object result = World.class
                    .getMethod("getChunkAtAsync", int.class, int.class, boolean.class)
                    .invoke(world, chunkX, chunkZ, generate);
            if (result instanceof CompletableFuture<?>) {
                return (CompletableFuture<Chunk>) result;
            }
            if (PaperLib.isPaper()) {
                return CompletableFuture.failedFuture(new IllegalStateException("Paper World#getChunkAtAsync returned a non-future result."));
            }
        } catch (Throwable e) {
            if (PaperLib.isPaper()) {
                return CompletableFuture.failedFuture(new IllegalStateException("Paper World#getChunkAtAsync is unavailable.", e));
            }
        }

        return PaperLib.getChunkAtAsync(world, chunkX, chunkZ, generate);
    }

    private void setupEngine() {
        try (GenerationAdmission.RuntimeLease startupAdmission = generationHistory == null ? null : generationHistory.retainRuntime()) {
            lastMode = StudioMode.NORMAL;
            lastJigsawStudioRequestId = null;
            if (generationHistory != null) {
                try {
                    generationHistory.prepareCurrentGenerator(IrisSettings.get().getGenerator().getGenerationTransitionWidthBlocks());
                    targetCache.reset();
                } catch (IOException failure) {
                    throw new IllegalStateException("Unable to prepare saved Iris terrain for the current generator.", failure);
                }
            }
            EngineTarget engineTarget = getTarget();
            String packKey = engineTarget.getDimension().getLoadKey();
            JigsawStudioActivation.Request request = studio
                    ? JigsawStudioActivation.getGeneratorRequest(packKey)
                    : null;
            JigsawStudioSession session = request == null
                    ? null
                    : JigsawStudioActivation.getGeneratorSession(packKey);
            jigsawStudioActive = request != null
                    && session != null
                    && session.sessionId().equals(request.requestId());
            objectStudioActive = studio && ObjectStudioActivation.isActive(packKey);
            IrisEngine createdEngine = createEngine(engineTarget);
            if (generationHistory != null) {
                long initialActivationId = generationHistory.activeActivation().activationId();
                int transitionWidthBlocks = IrisSettings.get()
                        .getGenerator()
                        .getGenerationTransitionWidthBlocks();
                try {
                    createdEngine.attachGenerationHistory(
                            generationHistory,
                            IrisBoundarySignatureSampler.INSTANCE,
                            transitionWidthBlocks);
                    if (generationHistory.activeActivation().activationId() != initialActivationId) {
                        createdEngine.close();
                        targetCache.reset();
                        createdEngine = createEngine(loadActiveGenerationHistoryTarget());
                        createdEngine.attachGenerationHistory(
                                generationHistory,
                                IrisBoundarySignatureSampler.INSTANCE,
                                transitionWidthBlocks);
                    }
                } catch (Throwable failure) {
                    try {
                        createdEngine.close();
                    } catch (Throwable closeFailure) {
                        if (failure != closeFailure) {
                            failure.addSuppressed(closeFailure);
                        }
                    }
                    throw propagateEngineSetupFailure(failure);
                }
            }
            createdEngine.setNativeStructureVolumeQueriesEnabled(shouldGenerateNativeStructures(
                    isAuthoringStudio(),
                    studioEntryBootstrapActive.get(),
                    initializationFailure != null));
            engine = createdEngine;
            populators.clear();
            targetCache.reset();
        }
    }

    private EngineTarget loadActiveGenerationHistoryTarget() throws IOException {
        Path packRoot = generationHistory.activePackRoot();
        IrisData data = IrisData.openRuntime(packRoot.toFile());
        try {
            data.dump();
            data.clearLists();
            IrisDimension dimension = data.getDimensionLoader().load(dimensionKey);
            if (dimension == null) {
                throw new IOException("Active Iris generation pack does not contain dimension '"
                        + dimensionKey + "': " + packRoot);
            }
            return new EngineTarget(world, dimension, data);
        } catch (Throwable failure) {
            if (!data.isClosed()) {
                data.close();
            }
            if (failure instanceof IOException ioFailure) {
                throw ioFailure;
            }
            if (failure instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            if (failure instanceof Error error) {
                throw error;
            }
            throw new IOException("Unable to load the active Iris generation pack.", failure);
        }
    }

    private IrisEngine createEngine(EngineTarget engineTarget) {
        IrisEngine.InitializationMode mode = selectInitializationMode(studio, jigsawStudioActive, objectStudioActive);
        if (generationHistory == null) {
            return new IrisEngine(engineTarget, mode);
        }
        GenerationActivation active = generationHistory.activeActivation();
        GenerationEpoch epoch = generationHistory.activeEpoch();
        TransitionGenerationPlan plan;
        try {
            plan = active.isInitial() ? null : generationHistory.transitionPlan(active.activationId());
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to load the active Iris generation transition.", failure);
        }
        IrisEngine createdEngine = new IrisEngine(
                engineTarget,
                mode,
                generationHistory.paths().activationMantleRoot(active.activationId()),
                epoch.kernelVersion(),
                plan);
        if (studio) {
            createdEngine.configureStudioGenerationSource(dataLocation.toPath());
        }
        return createdEngine;
    }

    private static RuntimeException propagateEngineSetupFailure(Throwable failure) {
        if (failure instanceof RuntimeException runtimeFailure) {
            return runtimeFailure;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        return new IllegalStateException("Unable to attach Iris generation history.", failure);
    }

    static IrisEngine.InitializationMode selectInitializationMode(boolean studio, boolean jigsawStudioActive, boolean objectStudioActive) {
        if (!studio) {
            return IrisEngine.InitializationMode.RUNTIME;
        }
        return jigsawStudioActive
                ? IrisEngine.InitializationMode.JIGSAW_STUDIO
                : objectStudioActive
                ? IrisEngine.InitializationMode.OBJECT_STUDIO
                : IrisEngine.InitializationMode.STUDIO;
    }

    static boolean shouldPersistWorldRegistration(boolean studio) {
        return !studio;
    }

    @NotNull
    @Override
    public EngineTarget getTarget() {
        if (engine != null) return engine.getTarget();
        EngineTarget pendingTarget = startupTarget;
        if (pendingTarget != null) {
            return pendingTarget;
        }

        return targetCache.aquireOrThrow(() -> {
            if (generationHistory != null) {
                try {
                    return loadActiveGenerationHistoryTarget();
                } catch (IOException failure) {
                    throw new IllegalStateException("Unable to load the active Iris generation pack.", failure);
                }
            }
            IrisData data = IrisData.openRuntime(dataLocation);
            data.dump();
            data.clearLists();
            IrisDimension dimension = data.getDimensionLoader().load(dimensionKey);

            if (dimension == null) {
                IrisLogging.error("Oh No! There's no pack in " + data.getDataFolder().getPath() + " or... there's no dimension for the key " + dimensionKey);
                IrisDimension test = IrisData.loadAnyDimension(dimensionKey, null);

                if (test != null) {
                    IrisLogging.warn("Looks like " + dimensionKey + " exists in " + test.getLoadFile().getPath() + " ");
                    test = IrisServices.get(StudioSVC.class).installInto(BukkitPlatform.console(), dimensionKey, dataLocation);
                    IrisLogging.warn("Attempted to install into " + data.getDataFolder().getPath());

                    if (test != null) {
                        IrisLogging.msg(C.IRIS + "Woo! Patched the Engine!");
                        dimension = test;
                    } else {
                        IrisLogging.error("Failed to patch dimension!");
                        throw new RuntimeException("Missing Dimension: " + dimensionKey);
                    }
                } else {
                    IrisLogging.error("Nope, you don't have an installation containing " + dimensionKey + " try downloading it?");
                    throw new RuntimeException("Missing Dimension: " + dimensionKey);
                }
            }

            return new EngineTarget(world, dimension, data);
        });
    }

    private Engine getEngine(WorldInfo world) {
        throwIfInitializationFailed();
        requireStartupContentReady();
        validateAndBindWorld(world);
        if (setup.get()) {
            return getEngine();
        }

        lock.lock();

        try {
            throwIfInitializationFailed();
            if (setup.get()) {
                return getEngine();
            }


            getWorld().setRawWorldSeed(world.getSeed());
            setupEngine();
            try {
                computeStudioGenerator();
            } catch (Throwable failure) {
                initializationFailure = failure;
                try {
                    engine.close();
                } catch (Throwable closeFailure) {
                    if (failure != closeFailure) {
                        failure.addSuppressed(closeFailure);
                    }
                }
                throw propagateEngineSetupFailure(failure);
            }
            setup.set(true);
            this.hotloader = shouldRunStudioHotload(studio, closing, jigsawStudioActive) ? new Looper() {
                @Override
                protected long loop() {
                    Engine activeEngine = engine;
                    if (activeEngine instanceof IrisEngine irisEngine
                            && irisEngine.isGenerationCacheWarmPending()) {
                        return HOTLOAD_LOOP_DELAY_MS;
                    }
                    if (isMaintenanceActive()) {
                        return HOTLOAD_MAINTENANCE_DELAY_MS;
                    }

                    if (hotloadChecker.flip()) {
                        folder.check();
                    }

                    return HOTLOAD_LOOP_DELAY_MS;
                }
            } : null;

            if (hotloader != null) {
                hotloader.setPriority(Thread.MIN_PRIORITY);
                hotloader.start();
                hotloader.setName(getTarget().getWorld().name() + " Hotloader");
            }

            return engine;
        } finally {
            lock.unlock();
        }
    }

    private void throwIfInitializationFailed() {
        Throwable failure = initializationFailure;
        if (failure == null) {
            return;
        }
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException("Iris initialization previously failed for world '" + world.name() + "'", failure);
    }

    private void validateAndBindWorld(WorldInfo worldInfo) {
        IrisDimension dimension = getTarget().getDimension();
        IrisDimensionRuntimeContract activeContract = validatedWorldContract;
        if (activeContract == null || validatedDimension != dimension) {
            IrisDimensionRuntimeContract expected = IrisDimensionRuntimeContract.expected(dimension, "iris");
            if (activeContract != null) {
                expected.requireExact("Bukkit world '" + worldInfo.getName() + "'", activeContract);
            }
            int runtimeHeight = worldInfo.getMaxHeight() - worldInfo.getMinHeight();
            expected.requireHeight("Bukkit world '" + worldInfo.getName() + "'", worldInfo.getMinHeight(), runtimeHeight);
            BukkitWorldBinding.bind(world, worldInfo);
            validatedWorldContract = expected;
            validatedDimension = dimension;
            return;
        }
        if (!world.hasPlatformWorld() && worldInfo instanceof World) {
            BukkitWorldBinding.bind(world, worldInfo);
        }
    }

    @Override
    public void close() {
        closeAsync();
    }

    @Override
    public CompletableFuture<Void> closeAsync() {
        // Outside the exclusive-control block so every close path detaches the WorldInit
        // listener, including a rollback where the world never materialized. Guarded: with no
        // hosted plugin (unit tests, teardown) volmitPlugin() throws, and that must not stop
        // the close.
        try {
            BukkitPlatform.volmitPlugin().unregisterListener(this);
        } catch (Throwable ignored) {
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        while (!closeFuture.compareAndSet(null, future)) {
            CompletableFuture<Void> existing = closeFuture.get();
            if (existing != null) {
                return existing;
            }
        }

        boolean alreadyClosing = closing;
        closing = true;
        CompletableFuture<Void> operation;
        try {
            operation = withExclusiveControlFuture(() -> {
                Looper activeHotloader = hotloader;
                hotloader = null;
                if (isStudio() && activeHotloader != null) {
                    activeHotloader.interrupt();
                    try {
                        activeHotloader.join(1000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        IrisLogging.reportError(e);
                    }
                }

                Engine currentEngine = engine;
                if (currentEngine != null && !currentEngine.isClosed()) {
                    currentEngine.close();
                }
                folder.clear();
                populators.clear();
                closeStartupTarget();
                startupReady.completeExceptionally(new IllegalStateException("Iris generator closed during startup."));
            });
        } catch (Throwable throwable) {
            if (!alreadyClosing) {
                // A failed close must stay retryable; leaving closing latched would permanently
                // reject generation for a world that may still be loaded.
                closing = false;
            }
            closeFuture.compareAndSet(future, null);
            future.completeExceptionally(throwable);
            return future;
        }

        operation.whenComplete((ignored, throwable) -> {
            if (throwable == null) {
                future.complete(null);
            } else {
                // The close body already ran and tore the engine down; unlike the pre-dispatch
                // failure above, resetting the gate here would advertise a healthy generator
                // over a CLOSED or FAILED engine. Stay latched and surface the failure.
                closeFuture.compareAndSet(future, null);
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    @Override
    public void quiesceForServerShutdown() {
        Looper activeHotloader = hotloader;
        hotloader = null;
        if (activeHotloader != null) {
            activeHotloader.interrupt();
        }
    }

    @Override
    public boolean isStudio() {
        return studio;
    }

    public void endStudioEntryBootstrap() {
        Engine activeEngine = engine;
        if (activeEngine instanceof IrisEngine irisEngine) {
            irisEngine.setNativeStructureVolumeQueriesEnabled(shouldGenerateNativeStructures(
                    isAuthoringStudio(),
                    false,
                    initializationFailure != null));
        }
        studioEntryBootstrapActive.set(false);
    }

    public boolean isStudioEntryBootstrapActive() {
        return studioEntryBootstrapActive.get();
    }

    public boolean isJigsawStudioActive() {
        return jigsawStudioActive;
    }

    public boolean isAuthoringStudio() {
        return jigsawStudioActive || objectStudioActive;
    }

    public boolean usesFlatStudioTerrain() {
        Engine activeEngine = engine;
        return usesFlatStudioTerrain(studio, isAuthoringStudio(),
                activeEngine == null ? null : activeEngine.getDimension().getStudioMode());
    }

    static boolean usesFlatStudioTerrain(boolean studio, boolean authoringStudio, StudioMode mode) {
        return studio && (authoringStudio || mode == StudioMode.OBJECT_BUFFET);
    }

    public int getAuthoringFloorY() {
        return Math.max(engine.getMinHeight(), ObjectStudioLayout.FLOOR_Y);
    }

    public int getAuthoringBaseHeight() {
        return authoringBaseHeight(engine.getMinHeight(), engine.getMaxHeight());
    }

    static int authoringBaseHeight(int minimumY, int maximumY) {
        int floorY = Math.max(minimumY, ObjectStudioLayout.FLOOR_Y);
        return floorY < maximumY ? floorY + 1 : minimumY;
    }

    @Override
    public void hotload() {
        if (!shouldRunStudioHotload(isStudio(), closing, jigsawStudioActive)) {
            return;
        }

        withExclusiveControl(() -> getEngine().hotload());
    }

    private void hotloadFromWatcher() {
        if (!shouldRunStudioHotload(isStudio(), closing, jigsawStudioActive)) {
            return;
        }
        withExclusiveControlFuture(() -> getEngine().hotload(), 30L, TimeUnit.SECONDS).join();
    }

    @Override
    public CompletableFuture<Void> hotloadComplexAsync(long acquisitionTimeout, TimeUnit unit) {
        Engine activeEngine = getEngine();
        if (activeEngine == null) {
            return CompletableFuture.completedFuture(null);
        }
        return withExclusiveControlFuture(activeEngine::hotloadComplex, acquisitionTimeout, unit);
    }

    static boolean shouldRunStudioHotload(boolean studio, boolean closing, boolean jigsawStudioActive) {
        return studio && !closing && !jigsawStudioActive;
    }

    public GenerationStagePermit acquireGenerationStage(String operation) {
        return loadLock.acquireStage(operation);
    }

    public GenerationStagePermit acquireNoiseGenerationStage(
            Engine expectedEngine,
            int chunkX,
            int chunkZ,
            String operation
    ) {
        Engine activeEngine = Objects.requireNonNull(expectedEngine, "Noise generation engine");
        return acquirePreparedGenerationStage(
                loadLock,
                operation,
                () -> resolveStudioGeneratorForNoise(activeEngine),
                activeEngine,
                chunkX,
                chunkZ);
    }

    static GenerationStagePermit acquirePreparedGenerationStage(
            GenerationStageGate gate,
            String operation,
            Supplier<StudioGenerator> resolver,
            Engine engine,
            int chunkX,
            int chunkZ
    ) {
        GenerationStageGate activeGate = Objects.requireNonNull(gate, "Generation stage gate");
        Supplier<StudioGenerator> activeResolver = Objects.requireNonNull(resolver, "Studio generator resolver");
        Engine activeEngine = Objects.requireNonNull(engine, "Prepared generation engine");
        while (true) {
            GenerationStagePermit stage = activeGate.acquireStage(operation);
            StudioGenerator selected;
            boolean retained = false;
            try {
                selected = activeResolver.get();
                if (selected == null || !selected.requiresPreSessionPreparation()) {
                    if (activeResolver.get() == selected) {
                        retained = true;
                        return stage;
                    }
                    continue;
                }
            } finally {
                if (!retained) {
                    stage.close();
                }
            }

            GenerationStageExclusivePermit exclusive = activeGate.acquireExclusiveStage(operation);
            try {
                StudioGenerator active = activeResolver.get();
                if (active != selected) {
                    continue;
                }
                active.prepareChunkBeforeSession(activeEngine, chunkX, chunkZ);
                return exclusive.downgradeToStage();
            } catch (WrongEngineBroException e) {
                throw new IllegalStateException("Iris generation stage " + operation
                        + " could not prepare its Studio generator.", e);
            } finally {
                exclusive.close();
            }
        }
    }

    private StudioGenerator resolveStudioGeneratorForNoise(Engine expectedEngine) {
        if (engine != expectedEngine) {
            throw new IllegalStateException("Iris noise generation belongs to a replaced engine runtime.");
        }
        StudioGenerator selected = computeStudioGenerator();
        if (engine != expectedEngine) {
            throw new IllegalStateException("Iris noise generation changed engine runtime during Studio resolution.");
        }
        return selected;
    }

    public void withExclusiveControl(Runnable r) {
        J.a(() -> {
            boolean acquired = false;
            try {
                loadLock.acquireExclusive();
                acquired = true;
                r.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                IrisLogging.reportError(e);
            } catch (Throwable e) {
                IrisLogging.reportError(e);
            } finally {
                if (acquired) {
                    loadLock.releaseExclusive();
                }
            }
        });
    }

    public CompletableFuture<Void> withExclusiveControlFuture(Runnable r) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        J.a(() -> completeExclusiveControlFuture(loadLock, r, future));
        return future;
    }

    CompletableFuture<Void> withExclusiveControlFuture(
            Runnable operation,
            long acquisitionTimeout,
            TimeUnit unit
    ) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        J.a(() -> completeExclusiveControlFuture(
                loadLock,
                operation,
                future,
                acquisitionTimeout,
                unit));
        return future;
    }

    static void completeExclusiveControlFuture(
            GenerationStageGate gate,
            Runnable operation,
            CompletableFuture<Void> future
    ) {
        GenerationStageGate activeGate = Objects.requireNonNull(gate, "Exclusive control gate");
        Runnable activeOperation = Objects.requireNonNull(operation, "Exclusive control operation");
        CompletableFuture<Void> outward = Objects.requireNonNull(future, "Exclusive control future");
        boolean acquired = false;
        Throwable failure = null;
        try {
            activeGate.acquireExclusive();
            acquired = true;
            activeOperation.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failure = e;
        } catch (Throwable e) {
            failure = e;
        } finally {
            if (acquired) {
                activeGate.releaseExclusive();
            }
        }

        if (failure == null) {
            outward.complete(null);
        } else {
            outward.completeExceptionally(failure);
        }
    }

    static void completeExclusiveControlFuture(
            GenerationStageGate gate,
            Runnable operation,
            CompletableFuture<Void> future,
            long acquisitionTimeout,
            TimeUnit unit
    ) {
        GenerationStageGate activeGate = Objects.requireNonNull(gate, "Exclusive control gate");
        Runnable activeOperation = Objects.requireNonNull(operation, "Exclusive control operation");
        CompletableFuture<Void> outward = Objects.requireNonNull(future, "Exclusive control future");
        TimeUnit activeUnit = Objects.requireNonNull(unit, "Exclusive control timeout unit");
        boolean acquired = false;
        Throwable failure = null;
        try {
            acquired = activeGate.tryAcquireExclusive(
                    acquisitionTimeout,
                    activeUnit,
                    outward::isCancelled);
            if (!acquired) {
                if (!outward.isCancelled()) {
                    failure = new TimeoutException("Timed out waiting for exclusive Iris generation control after "
                            + acquisitionTimeout + " " + activeUnit.name().toLowerCase(Locale.ROOT) + ".");
                }
            } else if (!outward.isCancelled()) {
                activeOperation.run();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (!outward.isCancelled()) {
                failure = e;
            }
        } catch (Throwable e) {
            failure = e;
        } finally {
            if (acquired) {
                activeGate.releaseExclusive();
            }
        }

        if (outward.isCancelled()) {
            return;
        }
        if (failure == null) {
            outward.complete(null);
        } else {
            outward.completeExceptionally(failure);
        }
    }

    public void touch(World world) {
        if (!startupReady.isDone() || startupReady.isCompletedExceptionally()) {
            return;
        }
        getEngine(world);
    }

    @Override
    public void generateNoise(@NotNull WorldInfo world, @NotNull Random random, int x, int z, @NotNull ChunkGenerator.ChunkData d) {
        if (closing) {
            throw new IllegalStateException("Iris chunk generation was rejected while the generator is closing.");
        }
        throwIfInitializationFailed();

        try {
            Engine engine = getEngine(world);
            StudioGenerator selected = computeStudioGenerator();
            TerrainChunk tc = TerrainChunk.create(d);
            if (selected != null) {
                selected.generateChunk(engine, tc, x, z);
            } else {
                ChunkDataHunkHolder blocks = new ChunkDataHunkHolder(d);
                Hunk<PlatformBiome> biomes = Hunk.viewBiomes(tc);
                try (GenerationHistoryRuntimeRouter.CoordinateScope historyScope =
                             openGenerationHistoryCoordinateScope(engine, x << 4, z << 4);
                     GenerationSessionLease lease = engine.acquireGenerationLease("bukkit_terrain_stage");
                    IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
                    engine.generate(x << 4, z << 4, blocks, biomes, engine.shouldGenerateMulticore());
                    blocks.apply();
                    if (historyScope != null) {
                        historyScope.claimGeneratedSemantics();
                    }
                }
            }

            IrisLogging.debug("Generated " + x + " " + z);
        } catch (IrisDimensionContractException e) {
            throw e;
        } catch (GenerationSessionException e) {
            if (closing || isExpectedTeardown(engine, e)) {
                throw new IllegalStateException("Iris chunk generation was rejected during an engine transition.", e);
            }

            IrisLogging.reportError("Iris chunk generation could not acquire its engine runtime at " + x + "," + z + ".", e);
            reportErrorChunk(x, z, e);

            throw new IllegalStateException("Iris chunk generation could not acquire its engine runtime.", e);
        } catch (Throwable e) {
            IrisLogging.reportError("Iris failed to generate chunk " + x + "," + z + ".", e);
            reportErrorChunk(x, z, e);

            throw new IllegalStateException("Iris failed to generate chunk " + x + "," + z + ".", e);
        }
    }

    private @Nullable GenerationHistoryRuntimeRouter.CoordinateScope openGenerationHistoryCoordinateScope(
            Engine currentEngine,
            int blockX,
            int blockZ
    ) throws IOException {
        if (generationHistory == null) {
            return null;
        }
        if (!(currentEngine instanceof IrisEngine irisEngine)) {
            throw new IllegalStateException("Persistent Iris generation requires an IrisEngine runtime.");
        }
        GenerationHistoryRuntimeRouter router = irisEngine.getGenerationHistoryRuntimeRouter()
                .orElseThrow(() -> new IllegalStateException(
                        "Persistent Iris generation has no generation-history runtime router."));
        return router.openCoordinateScope(blockX, blockZ);
    }

    private boolean isExpectedTeardown(Engine currentEngine, Throwable throwable) {
        if (throwable instanceof GenerationSessionException generationSessionException && generationSessionException.isExpectedTeardown()) {
            return true;
        }

        if (currentEngine != null && currentEngine.isClosing()) {
            return true;
        }

        return isMaintenanceActive();
    }

    private boolean isMaintenanceActive() {
        World realWorld = BukkitWorldBinding.world(this.world);
        return realWorld != null && IrisToolbelt.isWorldMaintenanceActive(realWorld);
    }

    private static void reportErrorChunk(int x, int z, Throwable e) {
        if (IrisSettings.get().getGeneral().isDebug()) {
            File f = IrisPlatforms.get().dataFile("debug", "chunk-errors", "chunk." + x + "." + z + ".txt");

            if (!f.exists()) {
                J.attempt(() -> {
                    PrintWriter pw = new PrintWriter(f);
                    pw.println("Thread: " + Thread.currentThread().getName());
                    pw.println("First: " + new Date(M.ms()));
                    e.printStackTrace(pw);
                    pw.close();
                });
            }

            IrisLogging.debug("Chunk " + x + "," + z + " Exception Logged: " + e.getClass().getSimpleName() + ": " + C.RESET + "" + C.LIGHT_PURPLE + e.getMessage());
        }
    }

    @Override
    public int getBaseHeight(@NotNull WorldInfo worldInfo, @NotNull Random random, int x, int z, @NotNull HeightMap heightMap) {
        Engine currentEngine = getEngine(worldInfo);
        if (usesFlatStudioTerrain()) {
            return getAuthoringBaseHeight();
        }

        boolean ignoreFluid = switch (heightMap) {
            case OCEAN_FLOOR, OCEAN_FLOOR_WG -> true;
            default -> false;
        };

        try (GenerationHistoryRuntimeRouter.CoordinateScope historyScope =
                     openGenerationHistoryCoordinateScope(currentEngine, x, z);
             GenerationSessionLease lease = currentEngine.acquireGenerationLease("bukkit_base_height");
             IrisContext.Scope ignored = IrisContext.open(currentEngine, lease.sessionId(), null)) {
            return currentEngine.getMinHeight() + currentEngine.getHeight(x, z, ignoreFluid) + 1;
        } catch (GenerationSessionException | IOException e) {
            throw new IllegalStateException("Iris base height query was rejected for world '"
                    + worldInfo.getName() + "'.", e);
        }
    }

    private StudioGenerator computeStudioGenerator() {
        if (!studio) {
            return studioGenerator;
        }
        return computeStudioGeneratorForStudio();
    }

    private synchronized StudioGenerator computeStudioGeneratorForStudio() {
        String packKey = getEngine().getDimension().getLoadKey();
        JigsawStudioActivation.Request jigsawRequest = studio
                ? JigsawStudioActivation.getGeneratorRequest(packKey)
                : null;
        if (jigsawRequest != null) {
            JigsawStudioSession session = JigsawStudioActivation.getGeneratorSession(packKey);
            if (session != null && session.sessionId().equals(jigsawRequest.requestId())) {
                if (!jigsawRequest.requestId().equals(lastJigsawStudioRequestId)) {
                    setStudioGenerator(new JigsawStudioGenerator(getEngine(), jigsawRequest, session));
                    lastJigsawStudioRequestId = jigsawRequest.requestId();
                    lastMode = null;
                }
                return studioGenerator;
            }
        }
        if (lastJigsawStudioRequestId != null) {
            lastJigsawStudioRequestId = null;
            lastMode = null;
        }
        // Gson nulls unknown enum names, so a pack carrying a removed or typo'd studioMode must not NPE the generator.
        // Pack-declared studio modes are studio-only: a shipped pack that forgot to reset
        // studioMode must never replace production terrain with a debug generator.
        StudioMode desired = studio
                ? java.util.Optional.ofNullable(getEngine().getDimension().getStudioMode()).orElse(StudioMode.NORMAL)
                : StudioMode.NORMAL;
        if (studio && ObjectStudioActivation.isActive(getEngine().getDimension().getLoadKey())) {
            desired = StudioMode.OBJECT_BUFFET;
        }
        if (!desired.equals(lastMode)) {
            desired.inject(this);
            lastMode = desired;
        }
        return studioGenerator;
    }

    @NotNull
    @Override
    public List<BlockPopulator> getDefaultPopulators(@NotNull World world) {
        return populators;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return shouldGenerateNativeStructures(
                isAuthoringStudio(),
                studioEntryBootstrapActive.get(),
                initializationFailure != null || startupTarget != null);
    }

    static boolean shouldGenerateNativeStructures(
            boolean authoringStudio,
            boolean studioEntryBootstrapActive,
            boolean initializationFailed
    ) {
        return !authoringStudio && !studioEntryBootstrapActive && !initializationFailed;
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public void generateBedrock(@NotNull WorldInfo worldInfo, @NotNull Random random, int x, int z, @NotNull ChunkData chunkData) {

    }

    @Nullable
    @Override
    public BiomeProvider getDefaultBiomeProvider(@NotNull WorldInfo worldInfo) {
        return dummyBiomeProvider;
    }

    static final class GenerationStageGate {
        private final int permitCount;
        private final @Nullable Semaphore permits;
        private final BooleanSupplier closing;

        GenerationStageGate(int permitCount, BooleanSupplier closing) {
            if (permitCount <= 0) {
                throw new IllegalArgumentException("Generation stage permit count must be positive.");
            }
            this.permitCount = permitCount;
            this.permits = new Semaphore(permitCount, true);
            this.closing = Objects.requireNonNull(closing, "Generation stage close state");
        }

        GenerationStagePermit acquireStage(String operation) {
            String activeOperation = Objects.requireNonNull(operation, "Generation stage operation");
            if (closing.getAsBoolean()) {
                throw rejected(activeOperation);
            }
            try {
                permits.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Iris generation stage " + activeOperation
                        + " was interrupted while waiting for engine access.", e);
            }
            if (closing.getAsBoolean()) {
                permits.release();
                throw rejected(activeOperation);
            }
            return new GenerationStagePermit(permits);
        }

        GenerationStageExclusivePermit acquireExclusiveStage(String operation) {
            String activeOperation = Objects.requireNonNull(operation, "Generation stage operation");
            if (closing.getAsBoolean()) {
                throw rejected(activeOperation);
            }
            try {
                permits.acquire(permitCount);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Iris generation stage " + activeOperation
                        + " was interrupted while waiting for exclusive engine access.", e);
            }
            if (closing.getAsBoolean()) {
                permits.release(permitCount);
                throw rejected(activeOperation);
            }
            return new GenerationStageExclusivePermit(permits, permitCount);
        }

        void acquireExclusive() throws InterruptedException {
            permits.acquire(permitCount);
        }

        boolean tryAcquireExclusive(
                long timeout,
                TimeUnit unit,
                BooleanSupplier cancelled
        ) throws InterruptedException {
            if (timeout <= 0L) {
                throw new IllegalArgumentException("Exclusive generation control timeout must be positive.");
            }
            TimeUnit activeUnit = Objects.requireNonNull(unit, "Exclusive generation control timeout unit");
            BooleanSupplier cancellation = Objects.requireNonNull(cancelled, "Exclusive generation control cancellation");
            long timeoutNanos = activeUnit.toNanos(timeout);
            long started = System.nanoTime();
            long remainingNanos = timeoutNanos;
            long cancellationPollNanos = TimeUnit.MILLISECONDS.toNanos(50L);
            while (!cancellation.getAsBoolean() && remainingNanos > 0L) {
                if (permits.tryAcquire(
                        permitCount,
                        Math.min(remainingNanos, cancellationPollNanos),
                        TimeUnit.NANOSECONDS)) {
                    return true;
                }
                remainingNanos = timeoutNanos - (System.nanoTime() - started);
            }
            return false;
        }

        void releaseExclusive() {
            permits.release(permitCount);
        }

        int queueLength() {
            return permits.getQueueLength();
        }

        int availablePermits() {
            return permits.availablePermits();
        }

        private IllegalStateException rejected(String operation) {
            return new IllegalStateException("Iris generation stage " + operation
                    + " was rejected while the generator is closing.");
        }
    }

    static final class GenerationStageExclusivePermit implements AutoCloseable {
        private final Semaphore permits;
        private final int permitCount;
        private final AtomicBoolean released;

        private GenerationStageExclusivePermit(Semaphore permits, int permitCount) {
            this.permits = permits;
            this.permitCount = permitCount;
            this.released = new AtomicBoolean(false);
        }

        GenerationStagePermit downgradeToStage() {
            if (!released.compareAndSet(false, true)) {
                throw new IllegalStateException("Exclusive Iris generation stage was already released.");
            }
            GenerationStagePermit stage = new GenerationStagePermit(permits);
            if (permitCount > 1) {
                permits.release(permitCount - 1);
            }
            return stage;
        }

        @Override
        public void close() {
            if (released.compareAndSet(false, true)) {
                permits.release(permitCount);
            }
        }
    }

    public static final class GenerationStagePermit implements AutoCloseable {
        private static final GenerationStagePermit NOOP = new GenerationStagePermit(null);

        private final Semaphore permits;
        private final AtomicBoolean released;

        private GenerationStagePermit(@Nullable Semaphore permits) {
            this.permits = permits;
            this.released = new AtomicBoolean(permits == null);
        }

        public static GenerationStagePermit noop() {
            return NOOP;
        }

        @Override
        public void close() {
            if (permits != null && released.compareAndSet(false, true)) {
                permits.release();
            }
        }
    }
}
