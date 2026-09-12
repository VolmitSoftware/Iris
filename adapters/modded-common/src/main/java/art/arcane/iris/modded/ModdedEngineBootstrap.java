/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.modded;

import art.arcane.iris.studio.view.GuiHost;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.generation.decoration.DecoratorPlatformHooks;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineEffectsProvider;
import art.arcane.iris.generation.runtime.EnginePlatformHooks;
import art.arcane.iris.generation.runtime.EngineWorldManagerProvider;
import art.arcane.iris.generation.runtime.PreservationRegistry;
import art.arcane.iris.generation.block.BlockDataMergeSupport;
import art.arcane.iris.structure.object.IrisObjectRotation;
import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.modded.api.ModdedCustomContentRegistry;
import art.arcane.iris.modded.command.IrisModdedCommands;
import art.arcane.iris.modded.command.ModdedGuiHost;
import art.arcane.iris.modded.command.ModdedObjectUndo;
import art.arcane.iris.modded.command.ModdedPregenBossBar;
import art.arcane.iris.modded.command.ModdedPregenJob;
import art.arcane.iris.modded.command.ModdedStudioCommands;
import art.arcane.iris.modded.command.ModdedWandService;
import art.arcane.iris.modded.service.ModdedChunkUpdateService;
import art.arcane.iris.modded.service.ModdedEngineMaintenanceService;
import art.arcane.iris.modded.service.ModdedEntitySpawnService;
import art.arcane.iris.modded.service.ModdedLogFilterService;
import art.arcane.iris.modded.service.ModdedPreservationService;
import art.arcane.iris.modded.service.ModdedSettingsHotloadService;
import art.arcane.iris.modded.service.ModdedStudioHotloadService;
import art.arcane.iris.modded.service.ModdedTreeFellerService;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.generation.concurrent.MultiBurst;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;

import java.util.ArrayDeque;

public final class ModdedEngineBootstrap {
    private static final String[] CORE_SELF_TEST_CLASSES = {
        "art.arcane.iris.generation.runtime.IrisEngine",
        "art.arcane.iris.generation.block.B",
        "art.arcane.iris.pack.loading.IrisData"
    };
    private static final Object LOCK = new Object();
    private static final ModdedServiceManager UNBOUND_SERVICE_MANAGER = new ModdedServiceManager();
    private static volatile ModdedLoader loader;
    private static volatile BoundRuntime runtime;
    private static volatile MinecraftServer currentServer;
    private static volatile MinecraftServer spawnCaptureServer;
    private static volatile boolean initialSpawnWasDefault;

    private ModdedEngineBootstrap() {
    }

    public static ModdedServiceManager services() {
        BoundRuntime bound = runtime;
        return bound == null ? UNBOUND_SERVICE_MANAGER : bound.services();
    }

    public static ModdedScheduler schedulerOrNull() {
        BoundRuntime bound = runtime;
        return bound == null ? null : bound.platform().moddedScheduler();
    }

    public static void tick(MinecraftServer server) {
        ModdedScheduler.tick(server);
        ModdedStartup.runOnce(server);
        ModdedPrimaryWorldRouter.tick(server);
        services().tick(server);
        ModdedPregenBossBar.tick(server);
        ModdedProtocolHandler.tickDimensionSync(server);
    }

    public static void start(MinecraftServer server) {
        captureInitialSpawn(server);
        currentServer = server;
        bind();
        // Pair of the stop() burst-pools stage. Load-bearing on integrated servers: once
        // closed, MultiBurst falls back to a same-thread executor, so a second world load
        // without reopen() would silently run every burst inline.
        MultiBurst.burst.reopen();
        MultiBurst.ioBurst.reopen();
        ModdedScheduler scheduler = schedulerOrNull();
        if (scheduler != null) {
            scheduler.reset();
        }
        IrisModdedCommands.openDownloadAdmission();
        ModdedStartup.prepareForStartup();
        IrisModdedChunkGenerator.startGenPool();
        bindWorldGenerators(server);
        services().enableAll();
        ModdedProtocolHandler.start(server);
    }

    private static void bindWorldGenerators(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getChunkSource().getGenerator() instanceof IrisModdedChunkGenerator generator) {
                generator.bindLevel(level);
            }
        }
    }

    public static void serverAboutToStart(MinecraftServer server) {
        captureInitialSpawn(server);
        ModdedStartup.prepareForStartup();
    }

    public static void serverStarted(MinecraftServer server) {
        // Prime the off-thread level snapshot before anything can read it; the per-tick refresh in
        // ModdedScheduler.tick has not run yet at this point.
        ModdedServerLevels.refreshIfStale(server);
        bindWorldGenerators(server);
        ModdedStartup.runOnce(server);
        reconcileSpawn(server);
        ModdedWorldCheck.serverStarted(server);
    }

    public static void levelLoaded(ServerLevel level) {
        if (level.getChunkSource().getGenerator() instanceof IrisModdedChunkGenerator generator) {
            generator.bindLevel(level);
        }
    }

    public static void levelUnloaded(ServerLevel level) {
        if (!(level.getChunkSource().getGenerator() instanceof IrisModdedChunkGenerator generator)) {
            return;
        }

        try {
            generator.unbindEngine(level);
        } catch (Throwable exception) {
            ModdedIrisLog.error("Iris engine unload failed for {}", level.dimension().identifier(), exception);
            if (exception instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (exception instanceof Error fatalError) {
                throw fatalError;
            }
            throw new IllegalStateException("Iris engine unload failed for "
                    + level.dimension().identifier(), exception);
        }
    }

    public static void stop() {
        MinecraftServer stoppingServer = currentServer;
        Throwable failure = null;
        failure = runStopStage(failure, "pack downloads", IrisModdedCommands::shutdownDownloads);
        failure = runStopStage(failure, "world check", () -> ModdedWorldCheck.serverStopped(stoppingServer));
        failure = runStopStage(failure, "protocol", ModdedProtocolHandler::stop);
        failure = runStopStage(failure, "pregenerator", ModdedPregenJob::shutdown);
        failure = runStopStage(failure, "object undo", ModdedObjectUndo::clearAll);
        failure = runStopStage(failure, "wand service", ModdedWandService::clearAll);
        failure = runStopStage(failure, "block break handler", ModdedBlockBreakHandler::clear);
        failure = runStopStage(failure, "studio commands", ModdedStudioCommands::clear);
        failure = runStopStage(failure, "gui host", ModdedGuiHost::clear);
        failure = runStopStage(failure, "services", () -> services().disableAll());
        failure = runStopStage(failure, "world engines", ModdedWorldEngines::shutdown);
        failure = runStopStage(failure, "primary world router", ModdedPrimaryWorldRouter::clear);
        failure = runStopStage(failure, "dimension manager", ModdedDimensionManager::clear);
        ModdedScheduler scheduler = schedulerOrNull();
        if (scheduler != null) {
            failure = runStopStage(failure, "scheduler", scheduler::shutdown);
        } else {
            // No bound runtime: the scheduler shutdown that normally drops the level snapshot never runs, and a
            // static snapshot of a stopped server keeps its whole level graph alive.
            failure = runStopStage(failure, "level snapshot", ModdedServerLevels::forget);
        }
        failure = runStopStage(failure, "generation pool", IrisModdedChunkGenerator::shutdownGenPool);
        failure = runStopStage(failure, "burst pools", () -> {
            MultiBurst.burst.close();
            MultiBurst.ioBurst.close();
        });
        failure = runStopStage(failure, "startup state", ModdedStartup::reset);
        failure = runStopStage(failure, "server state", () -> {
            currentServer = null;
            spawnCaptureServer = null;
            initialSpawnWasDefault = false;
        });
        if (failure != null) {
            // The shutdown path must not propagate: propagating aborts the remaining loader stop handlers and
            // can leave the level unsaved. Every stage already logged its own failure.
            ModdedIrisLog.error("Iris modded shutdown completed with failures", failure);
        }
    }

    private static Throwable runStopStage(Throwable failure, String stage, StopAction action) {
        try {
            action.run();
            return failure;
        } catch (Throwable stageFailure) {
            ModdedIrisLog.error("Iris modded shutdown stage '{}' failed", stage, stageFailure);
            if (failure == null) {
                return stageFailure;
            }
            if (stageFailure != failure) {
                failure.addSuppressed(stageFailure);
            }
            return failure;
        }
    }

    private static void captureInitialSpawn(MinecraftServer server) {
        if (spawnCaptureServer == server) {
            return;
        }
        LevelData.RespawnData respawnData = server.getRespawnData();
        initialSpawnWasDefault = respawnData == null || LevelData.RespawnData.DEFAULT.equals(respawnData);
        spawnCaptureServer = server;
    }

    private static void reconcileSpawn(MinecraftServer server) {
        LevelData.RespawnData current = server.getRespawnData();
        if (current == null) {
            return;
        }
        ServerLevel level = server.getLevel(current.dimension());
        if (level == null || !(level.getChunkSource().getGenerator() instanceof IrisModdedChunkGenerator)) {
            return;
        }
        String dimensionId = level.dimension().identifier().toString();
        boolean studio = dimensionId.startsWith("irisworldgen:studio_");
        if (!shouldReconcileSpawn(initialSpawnWasDefault, studio, current.pos().getX(), current.pos().getZ())) {
            return;
        }
        LevelChunk originChunk = level.getChunk(0, 0);
        int surfaceY = originChunk.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0) + 1;
        BlockPos position = reconciledSpawnPosition(surfaceY, level.getMinY(), level.getHeight());
        server.setRespawnData(LevelData.RespawnData.of(
                level.dimension(), position, current.yaw(), current.pitch()));
        ModdedIrisLog.info("Iris spawn reconciled for {} at {},{},{}", dimensionId,
                position.getX(), position.getY(), position.getZ());
    }

    static boolean shouldReconcileSpawn(boolean initialDefault, boolean studio, int currentX, int currentZ) {
        return initialDefault || studio || currentX == 0 && currentZ == 0;
    }

    static BlockPos reconciledSpawnPosition(int surfaceY, int minY, int height) {
        int y = Math.max(minY + 1, Math.min(minY + height - 2, surfaceY));
        return new BlockPos(0, y, 0);
    }

    public static void bootCommon(ModdedLoader moddedLoader, String loaderDescription, Runnable chunkGeneratorRegistration) {
        loader = moddedLoader;
        ModdedIrisLog.info("Iris " + moddedLoader.modVersion() + " bootstrapping on Minecraft " + moddedLoader.minecraftVersion() + " (" + loaderDescription + ")");
        selfTest(moddedLoader.getClass().getClassLoader());
        bind();
        IrisLanguage.initialize();
        ModdedStartup.prefetchStartupDatapack();
        if (!moddedLoader.clientEnvironment()) {
            MainWorldService.reconcileEarly();
        }
        chunkGeneratorRegistration.run();
        ModdedIrisLog.info("Iris chunk generator registered as irisworldgen:iris");
        armParityProbe();
        armWorldCheck();
    }

    private static void armParityProbe() {
        String parity = System.getProperty("iris.parity");
        if (parity == null) {
            return;
        }
        ModdedIrisLog.info("Iris parity probe armed: " + parity);
        ModdedParityProbe.schedule(parity);
    }

    private static void armWorldCheck() {
        if (System.getProperty("iris.worldcheck") == null) {
            return;
        }
        ModdedIrisLog.info("Iris world check armed");
        ModdedWorldCheck.schedule();
    }

    public static ModdedLoader loader() {
        ModdedLoader bound = loader;
        if (bound == null) {
            throw new IllegalStateException("Iris modded loader is not initialized; the loader bootstrap must call ModdedEngineBootstrap.bootCommon first");
        }
        return bound;
    }

    public static MinecraftServer currentServer() {
        MinecraftServer tracked = currentServer;
        return tracked != null ? tracked : loader().currentServer();
    }

    private static void selfTest(ClassLoader classLoader) {
        int loadedClasses = 0;
        for (String className : CORE_SELF_TEST_CLASSES) {
            try {
                Class.forName(className, true, classLoader);
                loadedClasses++;
            } catch (Throwable error) {
                ModdedIrisLog.error("Iris core self-test failed to initialize {}", className, error);
            }
        }

        if (loadedClasses != CORE_SELF_TEST_CLASSES.length) {
            throw new IllegalStateException("Iris core self-test failed: only " + loadedClasses + " of " + CORE_SELF_TEST_CLASSES.length + " engine classes initialized");
        }

        ModdedIrisLog.info("Iris core loaded (" + loadedClasses + " classes ok)");
    }

    public static ModdedPlatform bind() {
        BoundRuntime bound = runtime;
        if (bound != null) {
            return bound.platform();
        }
        synchronized (LOCK) {
            BoundRuntime synchronizedBound = runtime;
            if (synchronizedBound != null) {
                return synchronizedBound.platform();
            }
            ModdedLoader boundLoader = loader();
            ModdedPlatform created = new ModdedPlatform(boundLoader);
            ModdedServiceManager createdServices = new ModdedServiceManager();
            BindRollback rollback = new BindRollback();
            try {
                boolean previousDesktopSuppression = GuiHost.isDesktopSuppressed();
                GuiHost.suppressDesktop(boundLoader.clientEnvironment());
                rollback.add(() -> GuiHost.suppressDesktop(previousDesktopSuppression));

                IrisPlatform previousPlatform = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
                IrisPlatforms.bind(created);
                rollback.add(() -> restorePlatform(previousPlatform));

                ModdedServerAccess previousServerAccess = ModdedDimensionManager.bindAccess(
                        new ModdedServerLevels(boundLoader::invalidateLevelCache));
                rollback.add(() -> ModdedDimensionManager.restoreAccess(previousServerAccess));

                IrisObjectRotation.StateRotator previousRotator = IrisObjectRotation.bindPlatformRotator(new ModdedStateRotator());
                rollback.add(() -> IrisObjectRotation.restorePlatformRotator(previousRotator));

                BlockDataMergeSupport.StateMerger previousMerger = BlockDataMergeSupport.bindPlatformMerger(new ModdedStateMerger());
                rollback.add(() -> BlockDataMergeSupport.restorePlatformMerger(previousMerger));

                TileData.TileReader previousTileReader = TileData.bindPlatformReader(new ModdedTileReader(boundLoader::currentServer));
                rollback.add(() -> TileData.restorePlatformReader(previousTileReader));

                TileData.TileFactory previousTileFactory = TileData.bindPlatformFactory(ModdedTileData::fromProperties);
                rollback.add(() -> TileData.restorePlatformFactory(previousTileFactory));

                GuiHost.Provider previousGuiHost = GuiHost.get();
                ModdedGuiHost.install();
                rollback.add(() -> GuiHost.set(previousGuiHost));

                ModdedDecoratorHooks decoratorHooks = new ModdedDecoratorHooks();
                DecoratorPlatformHooks.Bindings previousDecoratorBindings = DecoratorPlatformHooks.bind(decoratorHooks, decoratorHooks);
                rollback.add(() -> DecoratorPlatformHooks.restore(previousDecoratorBindings));

                ModdedPreservationService preservation = createdServices.register(
                        ModdedPreservationService.class, new ModdedPreservationService());
                createdServices.register(ModdedLogFilterService.class, new ModdedLogFilterService());
                createdServices.register(ModdedEngineMaintenanceService.class, new ModdedEngineMaintenanceService());
                createdServices.register(ModdedSettingsHotloadService.class, new ModdedSettingsHotloadService());
                ModdedStudioHotloadService studioHotloadService = createdServices.register(
                        ModdedStudioHotloadService.class, new ModdedStudioHotloadService());
                createdServices.register(ModdedChunkUpdateService.class, new ModdedChunkUpdateService());
                createdServices.register(ModdedEntitySpawnService.class, new ModdedEntitySpawnService());
                createdServices.register(ModdedTreeFellerService.class, new ModdedTreeFellerService());

                bindService(PreservationRegistry.class, preservation, rollback);
                bindService(EngineEffectsProvider.class,
                        (EngineEffectsProvider) ModdedEngineEffects::new, rollback);
                bindService(EnginePlatformHooks.class, studioHotloadService, rollback);
                bindService(EngineWorldManagerProvider.class,
                        (EngineWorldManagerProvider) (Engine engine) -> new ModdedWorldManager(engine), rollback);

                ModdedCustomContentRegistry.Discovery customContentDiscovery =
                        ModdedCustomContentRegistry.discover();
                rollback.add(customContentDiscovery::rollback);
                try {
                    ModdedIrisSplash.print(boundLoader);
                } catch (Throwable splashFailure) {
                    // A cosmetic banner must never roll back the platform bind.
                    ModdedIrisLog.warn("Iris splash could not be printed", splashFailure);
                }
                createdServices.enableAll();
                runtime = new BoundRuntime(created, createdServices);
                rollback.clear();
                return created;
            } catch (Throwable failure) {
                createdServices.rollback(failure);
                rollback.restore(failure);
                ModdedIrisLog.error("Iris modded platform binding failed", failure);
                if (failure instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (failure instanceof Error fatalError) {
                    throw fatalError;
                }
                throw new IllegalStateException("Iris modded platform binding failed", failure);
            }
        }
    }

    private static <T> void bindService(Class<T> type, T service, BindRollback rollback) {
        T previous = IrisServices.getOrNull(type);
        IrisServices.register(type, service);
        rollback.add(() -> restoreService(type, previous));
    }

    private static <T> void restoreService(Class<T> type, T service) {
        if (service == null) {
            IrisServices.remove(type);
            return;
        }
        IrisServices.register(type, service);
    }

    private static void restorePlatform(IrisPlatform platform) {
        IrisLanguage.shutdown();
        IrisPlatforms.unbind();
        if (platform != null) {
            IrisPlatforms.bind(platform);
        }
    }

    private record BoundRuntime(ModdedPlatform platform, ModdedServiceManager services) {
    }

    @FunctionalInterface
    private interface RollbackAction {
        void restore() throws Throwable;
    }

    @FunctionalInterface
    private interface StopAction {
        void run() throws Throwable;
    }

    private static final class BindRollback {
        private final ArrayDeque<RollbackAction> actions = new ArrayDeque<>();

        void add(RollbackAction action) {
            actions.push(action);
        }

        void restore(Throwable failure) {
            while (!actions.isEmpty()) {
                try {
                    actions.pop().restore();
                } catch (Throwable rollbackFailure) {
                    if (rollbackFailure != failure) {
                        failure.addSuppressed(rollbackFailure);
                    }
                }
            }
        }

        void clear() {
            actions.clear();
        }
    }
}
