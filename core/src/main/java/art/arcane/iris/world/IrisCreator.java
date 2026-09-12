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

package art.arcane.iris.world;

import art.arcane.iris.world.runtime.TransientWorldCleanupSupport;
import art.arcane.iris.world.runtime.WorldRuntimeControlService;
import com.google.common.util.concurrent.AtomicDouble;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.integration.MultiverseCoreLink;
import art.arcane.iris.pack.datapack.DatapackInstallResult;
import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.pack.datapack.ServerConfigurator;
import art.arcane.iris.world.lifecycle.BukkitWorldConfiguration;
import art.arcane.iris.world.lifecycle.LifecycleOperationCoordinator;
import art.arcane.iris.world.lifecycle.WorldLifecycleCaller;
import art.arcane.iris.world.lifecycle.WorldLifecycleRequest;
import art.arcane.iris.world.lifecycle.WorldLifecycleService;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.RuntimeProgressMessages;
import art.arcane.iris.platform.bukkit.nms.INMS;
import art.arcane.iris.world.pregen.PregenTask;
import art.arcane.iris.pack.AtomicDirectoryPublisher;
import art.arcane.iris.pack.PackValidationRegistry;
import art.arcane.iris.world.runtime.WorldDeletionQueue;
import art.arcane.iris.studio.StudioSVC;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import art.arcane.volmlib.util.exceptions.IrisException;
import art.arcane.iris.localization.C;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import lombok.Data;
import lombok.experimental.Accessors;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.IntSupplier;

import static art.arcane.iris.platform.bootstrap.ServerProperties.BUKKIT_YML;

/**
 * Makes it a lot easier to setup an engine, world, studio or whatever
 */
@Data
@Accessors(fluent = true, chain = true)
public class IrisCreator {
    private static final long WORLD_CREATE_TIMEOUT_SECONDS = 120L;
    private static final long INITIAL_SPAWN_TIMEOUT_SECONDS = 600L;
    private static final long WORLD_ENTRY_TELEPORT_TIMEOUT_SECONDS = 60L;
    private static final long ROLLBACK_PHASE_TIMEOUT_SECONDS = 120L;

    /**
     * Specify an area to pregenerate during creation
     */
    private PregenTask pregen;
    /**
     * Specify a sender to get updates & progress info + tp when world is created.
     */
    private VolmitSender sender;
    /**
     * The seed to use for this generator
     */
    private long seed = 1337;
    /**
     * The dimension to use. This can be any online dimension, or a dimension in the
     * packs folder
     */
    private String dimension = IrisSettings.get().getGenerator().getDefaultWorldType();
    /**
     * The name of this world.
     */
    private String name = "irisworld";
    /**
     * Studio mode snapshots edits from your Iris/packs folder into generation history.
     * Existing chunks retain their generation while new chunks use accepted edits.
     * Studio worlds are deleted when they are unloaded.
     */
    private boolean studio = false;
    /**
     * Benchmark mode
     */
    private boolean benchmark = false;
    private BiConsumer<Double, String> studioProgressConsumer;
    private BiConsumer<String, Long> studioTimingConsumer;
    private DatapackPreparation datapackPreparation = DatapackPreparation.INSTALL_IF_CHANGED;

    public static boolean removeFromBukkitYml(NamespacedKey worldKey) throws IOException {
        return BukkitWorldConfiguration.remove(
                BUKKIT_YML,
                IrisWorldStorage.configuredWorldName(worldKey, IrisWorldStorage.levelRoot().getName())
        );
    }

    public static int removeTransientStudioWorldsFromBukkitYml() throws IOException {
        return BukkitWorldConfiguration.removeMatching(
                BUKKIT_YML,
                TransientWorldCleanupSupport::isTransientStudioWorldName);
    }
    public static boolean worldLoaded(){
        return true;
    }

    /**
     * Create the IrisAccess (contains the world)
     *
     * @return the IrisAccess
     * @throws IrisException shit happens
     */

    public World create() throws IrisException {
        if (Bukkit.isPrimaryThread()) {
            throw new IrisException("You cannot invoke create() on the main thread.");
        }
        if (sender == null) {
            sender = BukkitPlatform.console();
        }
        NamespacedKey worldKey;
        try {
            worldKey = IrisWorldStorage.managedKeyFromName(name);
        } catch (RuntimeException e) {
            throw new IrisException(e.getMessage(), e);
        }
        name = IrisWorldStorage.logicalName(worldKey);
        WorldCreationProgressReporter creationReporter = !studio && !benchmark
                ? WorldCreationProgressReporter.start(sender, name)
                : null;

        LifecycleOperationCoordinator coordinator = LifecycleOperationCoordinator.get();
        LifecycleOperationCoordinator.Lease worldLease = null;
        try {
            reportStudioProgress(0.02D, "resolve_dimension");
            reportCreationProgress(creationReporter, 0.02D, "resolve_dimension");
            IrisDimension resolvedDimension = IrisToolbelt.getDimension(dimension());
            if (resolvedDimension == null) {
                throw new IrisException("Dimension cannot be found for id " + dimension());
            }
            reportCreationProgress(creationReporter, 0.06D, "validate_pack");
            ServerConfigurator.requireWorldCreationReady(datapackPreparation.forcesLoadedRuntime());
            PackValidationRegistry.requireLoadable(
                    resolvedDimension.getLoader().getDataFolder().getName());
            worldLease = coordinator.acquire(
                    LifecycleOperationCoordinator.Domain.WORLD_MUTATION,
                    LifecycleOperationCoordinator.OperationKind.WORLD_CREATE,
                    worldKey.toString());
            World world = createReserved(worldKey, resolvedDimension, creationReporter);
            reportCreationProgress(creationReporter, 1.0D, "complete");
            if (creationReporter != null) {
                creationReporter.succeed();
            }
            return world;
        } catch (LifecycleOperationCoordinator.BusyException e) {
            if (creationReporter != null) {
                creationReporter.fail();
            }
            throw new IrisException(e.getMessage(), e);
        } catch (Throwable e) {
            if (creationReporter != null) {
                creationReporter.fail();
            }
            if (e instanceof IrisException irisException) {
                throw irisException;
            }
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (e instanceof Error error) {
                throw error;
            }
            throw new IrisException("Failed to create world \"" + name + "\".", e);
        } finally {
            if (worldLease != null) {
                worldLease.close();
            }
        }
    }

    private World createReserved(
            NamespacedKey worldKey,
            IrisDimension resolvedDimension,
            WorldCreationProgressReporter creationReporter
    ) throws IrisException {
        File dimensionRoot;
        File storageRoot;
        try {
            if (!studio && !benchmark) {
                dimensionRoot = WorldCreatorCompat.persistentDimensionRoot(worldKey);
                storageRoot = WorldCreatorCompat.persistentLevelRoot(worldKey);
            } else {
                dimensionRoot = IrisWorldStorage.requireSafeManagedDimensionRoot(worldKey);
                storageRoot = dimensionRoot;
            }
        } catch (RuntimeException e) {
            throw new IrisException(e.getMessage(), e);
        }
        if (Files.exists(storageRoot.toPath()) || WorldIdentity.resolve(worldKey).isPresent()) {
            throw new IrisException("World \"" + name + "\" already exists or is loaded.");
        }
        World world = null;
        boolean bukkitRegistered = false;
        PlatformChunkGenerator stagedGenerator = null;
        try {
            reportStudioProgress(0.08D, "resolve_dimension");
            reportStudioProgress(0.16D, "prepare_world_pack");
            reportCreationProgress(creationReporter, 0.10D, "install_datapacks");
            DatapackInstallResult datapackResult = prepareDatapacks(resolvedDimension);
            if (!datapackResult.succeeded()) {
                throw new IrisException("Failed to compile datapacks for dimension \"" + dimension() + "\".");
            }
            if (!datapackPreparation.forcesLoadedRuntime()
                    && (datapackResult.restartRequired()
                    || !ServerConfigurator.verifyDataPackInstalled(resolvedDimension))) {
                throw new IrisException("The dimension types for pack \"" + dimension() + "\" are not loaded yet. "
                        + "Restart the server, then run the command again.");
            }

            reportCreationProgress(creationReporter, 0.26D, "prepare_world_pack");
            StudioSVC studioService = IrisServices.get(StudioSVC.class);
            IrisDimension installedDimension = benchmark && !studio
                    ? studioService.installIntoTransientWorld(sender, resolvedDimension, dimensionRoot)
                    : studioService.installIntoWorld(sender, resolvedDimension, dimensionRoot, seed);
            if (installedDimension == null) {
                throw new IrisException("Failed to install dimension pack for " + dimension());
            }
            dimension = installedDimension.getLoadKey();
            if (studio()) {
                IrisRuntimeSchedulerMode runtimeSchedulerMode = IrisRuntimeSchedulerMode.resolve(IrisSettings.get().getPregen());
                IrisLogging.debug("Studio create scheduling: mode=" + runtimeSchedulerMode.name().toLowerCase(Locale.ROOT)
                        + ", regionizedRuntime=" + FoliaScheduler.isRegionizedRuntime(Bukkit.getServer()));
            }

            reportStudioProgress(0.28D, "install_datapacks");
            reportCreationProgress(creationReporter, 0.36D, "prepare_generator");
            AtomicDouble pp = new AtomicDouble(0);
            AtomicBoolean done = new AtomicBoolean(false);
            long generatorPrepareStart = System.nanoTime();
            WorldCreator wc = new IrisWorldCreator()
                    .dimension(installedDimension)
                    .studioPackSource(resolvedDimension.getLoader().getDataFolder())
                    .name(name)
                    .seed(seed)
                    .studio(studio)
                    .persistent(!studio && !benchmark)
                    .create();
            reportStudioTiming("prepare_studio_generator", generatorPrepareStart);
            reportStudioProgress(0.40D, "install_datapacks");

            PlatformChunkGenerator access = (PlatformChunkGenerator) wc.generator();
            if (access == null) {
                throw new IrisException("Access is null. Something bad happened.");
            }
            stagedGenerator = access;
            AtomicInteger createProgressTask = startCreateProgressReporter(access, done, creationReporter);

            reportStudioProgress(0.46D, "create_world");
            reportCreationProgress(creationReporter, 0.44D, "create_world");
            long nmsStartNanos = System.nanoTime();
            try {
                WorldLifecycleCaller callerKind = benchmark
                        ? WorldLifecycleCaller.BENCHMARK
                        : studio() && datapackPreparation.forcesLoadedRuntime()
                        ? WorldLifecycleCaller.FORCED_STUDIO
                        : studio()
                        ? WorldLifecycleCaller.STUDIO
                        : WorldLifecycleCaller.CREATE;
                WorldLifecycleRequest request = WorldLifecycleRequest.fromCreator(wc, studio(), benchmark, callerKind);
                world = J.sfut(() -> INMS.get().createWorldAsync(wc, request))
                        .thenCompose(Function.identity())
                        .get(WORLD_CREATE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (!studio && !benchmark) {
                    awaitInitialSpawnPreparation(access, name);
                }
            } catch (Throwable e) {
                done.set(true);
                cancelRepeatingTask(createProgressTask);
                if (e instanceof TimeoutException) {
                    ServerConfigurator.restart("World creation timed out for \"" + name + "\".");
                }
                if (J.isFolia() && containsCreateWorldUnsupportedOperation(e)) {
                    throw new IrisException("Runtime world creation is blocked and the selected world lifecycle backend could not create the world.", e);
                }
                if (containsMissingDimensionTypes(e)) {
                    throw new IrisException("The dimension types for pack \"" + dimension() + "\" are not loaded on this server yet. "
                            + "Restart the server, then run the command again.", e);
                }
                throw new IrisException("Failed to create world with backend family " + WorldLifecycleService.get().capabilities().serverFamily().id() + "!", e);
            } finally {
                reportStudioTiming("create_bukkit_world", nmsStartNanos);
            }

            done.set(true);
            cancelRepeatingTask(createProgressTask);
            reportStudioProgress(0.86D, "create_world");
            reportCreationProgress(creationReporter, 0.84D, "register_world");

            if (!studio && !benchmark) {
                // bukkit.yml and Multiverse must agree on the startup name, or Multiverse re-imports the
                // world on the next boot and collides with its own config key.
                String registrationName = IrisWorldStorage.configuredWorldName(
                        worldKey,
                        IrisWorldStorage.levelRoot().getName()
                );
                BukkitWorldConfiguration.register(
                        BUKKIT_YML,
                        registrationName,
                        dimension,
                        seed
                );
                bukkitRegistered = true;
                World createdWorld = world;
                CompletableFuture<Void> multiverseRegistration = J.sfut(
                        () -> IrisServices.get(MultiverseCoreLink.class)
                                .updateWorld(createdWorld, registrationName, dimension)
                );
                if (multiverseRegistration == null) {
                    throw new IrisException("Failed to schedule Multiverse registration for world \"" + name + "\".");
                }
                try {
                    multiverseRegistration.get(30L, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    ServerConfigurator.restart("Multiverse registration timed out for \"" + name + "\".");
                    throw e;
                }
            }
            reportCreationProgress(creationReporter, 0.92D, "teleport_player");
            awaitSenderTeleport(world);

            if (pregen != null) {
                CompletableFuture<Boolean> ff = new CompletableFuture<>();
                IrisToolbelt.pregenerate(pregen, access)
                        .onProgress(pp::set)
                        .whenDone(() -> ff.complete(true));

                AtomicBoolean dx = new AtomicBoolean(false);
                boolean pregenHud = creationReporter == null && sender.isPlayer();
                AtomicInteger pregenProgressTask = startPregenProgressReporter(
                        pp,
                        dx,
                        pregenHud,
                        creationReporter
                );
                try {
                    ff.get();
                    dx.set(true);
                    cancelRepeatingTask(pregenProgressTask);
                    hidePregenLane(pregenHud);
                } catch (Throwable e) {
                    dx.set(true);
                    cancelRepeatingTask(pregenProgressTask);
                    hidePregenLane(pregenHud);
                    IrisLogging.reportError(e);
                }
            }
            reportCreationProgress(creationReporter, 0.99D, "finalize");
            return world;
        } catch (Throwable failure) {
            rollbackWorldCreation(worldKey, world, stagedGenerator, storageRoot, bukkitRegistered, failure);
            if (failure instanceof IrisException irisException) {
                throw irisException;
            }
            throw new IrisException("Failed to create world \"" + name + "\".", failure);
        }
    }

    static Player createTeleportTarget(VolmitSender sender, boolean studio, boolean benchmark) {
        if (studio || benchmark || sender == null || !sender.isPlayer()) {
            return null;
        }
        return sender.player();
    }

    static CompletableFuture<Boolean> teleportSenderToCreatedWorld(
            Player player,
            World world,
            WorldRuntimeControlService runtimeControl
    ) {
        Player requiredPlayer = Objects.requireNonNull(player, "player");
        World requiredWorld = Objects.requireNonNull(world, "world");
        WorldRuntimeControlService requiredRuntime = Objects.requireNonNull(runtimeControl, "runtimeControl");
        Location entryAnchor = requiredRuntime.resolveEntryAnchor(requiredWorld);
        if (entryAnchor == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Unable to resolve the entry anchor for world \"" + requiredWorld.getName() + "\"."));
        }

        int chunkX = entryAnchor.getBlockX() >> 4;
        int chunkZ = entryAnchor.getBlockZ() >> 4;
        return requiredRuntime.requestChunkAsync(requiredWorld, chunkX, chunkZ, true)
                .thenCompose(chunk -> requiredRuntime.resolveSafeEntry(requiredWorld, entryAnchor))
                .thenCompose(safeEntry -> {
                    if (safeEntry == null) {
                        return CompletableFuture.failedFuture(new IllegalStateException(
                                "Unable to resolve a safe entry for world \"" + requiredWorld.getName() + "\"."));
                    }
                    return requiredRuntime.teleport(requiredPlayer, safeEntry);
                });
    }

    private void awaitSenderTeleport(World world) {
        Player player = createTeleportTarget(sender, studio, benchmark);
        if (player == null) {
            return;
        }

        CompletableFuture<Boolean> teleportFuture;
        try {
            teleportFuture = teleportSenderToCreatedWorld(player, world, WorldRuntimeControlService.get());
        } catch (Throwable e) {
            reportSenderTeleportFailure(player, world, e);
            return;
        }

        Throwable failure = awaitTeleportFailure(
                teleportFuture,
                player.getName(),
                WORLD_ENTRY_TELEPORT_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
        );
        if (failure != null) {
            reportSenderTeleportFailure(player, world, failure);
        }
    }

    static Throwable awaitTeleportFailure(
            CompletableFuture<Boolean> teleportFuture,
            String playerName,
            long timeout,
            TimeUnit unit
    ) {
        CompletableFuture<Boolean> requiredFuture = Objects.requireNonNull(teleportFuture, "teleportFuture");
        String requiredPlayerName = Objects.requireNonNull(playerName, "playerName");
        TimeUnit requiredUnit = Objects.requireNonNull(unit, "unit");
        try {
            Boolean teleported = requiredFuture.get(timeout, requiredUnit);
            return Boolean.TRUE.equals(teleported)
                    ? null
                    : new IllegalStateException(
                    "The runtime teleport operation returned false for player \"" + requiredPlayerName + "\".");
        } catch (TimeoutException e) {
            requiredFuture.cancel(false);
            return e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return e;
        } catch (ExecutionException e) {
            return e.getCause() == null ? e : e.getCause();
        } catch (Throwable e) {
            return e;
        }
    }

    private void reportSenderTeleportFailure(Player player, World world, Throwable throwable) {
        IrisLogging.reportError("World \"" + world.getName()
                + "\" was created, but automatic teleport failed for player \"" + player.getName() + "\".", throwable);
        J.runEntity(player, () -> new VolmitSender(player).sendMessage(IrisLanguage.text(
                RuntimeProgressMessages.WORLD_CREATE_TELEPORT_FAILED,
                MessageArgument.untrusted("world", IrisWorldStorage.logicalName(world))
        )));
    }

    private void reportStudioProgress(double progress, String stage) {
        BiConsumer<Double, String> consumer = studioProgressConsumer;
        if (consumer == null) {
            return;
        }

        double clamped = Math.max(0D, Math.min(1D, progress));
        try {
            consumer.accept(clamped, stage);
        } catch (Throwable e) {
            IrisLogging.reportError("Studio progress consumer failed for world \"" + name() + "\".", e);
        }
    }

    private void reportCreationProgress(
            WorldCreationProgressReporter reporter,
            double progress,
            String stage
    ) {
        if (reporter != null) {
            reporter.update(progress, stage);
        }
    }

    private void reportStudioTiming(String phase, long startedAtNanos) {
        BiConsumer<String, Long> consumer = studioTimingConsumer;
        if (consumer == null) {
            return;
        }

        long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
        try {
            consumer.accept(phase, duration);
        } catch (Throwable e) {
            IrisLogging.reportError("Studio timing consumer failed for world \"" + name() + "\".", e);
        }
    }

    private DatapackInstallResult prepareDatapacks(IrisDimension resolvedDimension) {
        DatapackPreparation preparation = Objects.requireNonNull(
                datapackPreparation,
                "Datapack preparation mode");
        boolean runtimeReady = preparation == DatapackPreparation.REUSE_LOADED_RUNTIME_IF_READY
                && ServerConfigurator.isLoadedDatapackRuntimeReady(resolvedDimension);
        if (!preparation.requiresInstall(runtimeReady)) {
            long reuseStart = System.nanoTime();
            reportStudioTiming("datapack_reuse_loaded_runtime", reuseStart);
            return DatapackInstallResult.unchangedResult();
        }
        return ServerConfigurator.installDataPacksIfChanged(true, studioTimingConsumer);
    }

    private AtomicInteger startCreateProgressReporter(
            PlatformChunkGenerator access,
            AtomicBoolean done,
            WorldCreationProgressReporter creationReporter
    ) {
        AtomicInteger taskId = new AtomicInteger(-1);
        if (benchmark) {
            return taskId;
        }

        IntSupplier generatedSupplier = () -> {
            if (access.getEngine() == null) {
                return 0;
            }
            return access.getEngine().getGenerated();
        };
        access.getSpawnChunks().whenComplete((required, throwable) -> {
            if (throwable != null) {
                IrisLogging.reportError("Failed to resolve spawn chunk target for world \"" + name() + "\".", throwable);
                return;
            }

            if (done.get() || required == null || required <= 0) {
                return;
            }

            int interval = studioProgressConsumer != null || creationReporter != null && sender.isPlayer() ? 1 : 20;
            taskId.set(J.ar(() -> {
                if (done.get()) {
                    cancelRepeatingTask(taskId);
                    return;
                }

                int generated = generatedSupplier.getAsInt();
                if (generated >= required) {
                    cancelRepeatingTask(taskId);
                    return;
                }

                double progress = (double) generated / required;
                if (studioProgressConsumer != null) {
                    reportStudioProgress(0.40D + (0.42D * progress), "create_world");
                }

                if (creationReporter != null) {
                    creationReporter.update(
                            0.44D + (0.38D * progress),
                            "create_world",
                            C.DARK_GRAY + " (" + Form.f(generated) + "/" + Form.f(required) + " chunks)"
                    );
                }
            }, interval));
        });
        return taskId;
    }

    static void awaitInitialSpawnPreparation(
            PlatformChunkGenerator generator,
            String worldName
    ) throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Void> initialSpawnReady = Objects.requireNonNull(
                generator.getInitialSpawnReady(),
                "Initial spawn preparation future");
        try {
            initialSpawnReady.get(INITIAL_SPAWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException failure) {
            throw new TimeoutException("Initial spawn preparation timed out for world \""
                    + worldName + "\".");
        }
    }

    private AtomicInteger startPregenProgressReporter(
            AtomicDouble progress,
            AtomicBoolean done,
            boolean showLoaderHud,
            WorldCreationProgressReporter creationReporter
    ) {
        AtomicInteger taskId = new AtomicInteger(-1);
        int interval = sender.isPlayer() ? 1 : 20;
        taskId.set(J.ar(() -> {
            if (done.get()) {
                cancelRepeatingTask(taskId);
                return;
            }

            double p = progress.get();
            int percent = (int) Math.round(p * 100.0D);
            if (creationReporter != null) {
                creationReporter.update(0.94D + (0.05D * p), "pregenerate");
                return;
            }
            if (showLoaderHud) {
                if (IrisSettings.get().getGeneral().isProgressBossBar()) {
                    BukkitPlatform.hudLanes().show(sender.player(), "iris:pregen", IrisLanguage.text(
                            RuntimeProgressMessages.WORLD_PREGEN_ACTION,
                            MessageArgument.trusted("bar", ""),
                            MessageArgument.trusted("percent", percent)
                    ), p, BarColor.GREEN, BarStyle.SOLID, 4000L);
                }
                int barWidth = 44;
                int filled = (int) Math.round(Math.max(0.0D, Math.min(1.0D, p)) * barWidth);
                StringBuilder bar = new StringBuilder(barWidth * 3 + 4);
                bar.append(C.DARK_GRAY).append("[");
                for (int bi = 0; bi < barWidth; bi++) {
                    bar.append(bi < filled ? C.GREEN : C.DARK_GRAY).append("|");
                }
                bar.append(C.DARK_GRAY).append("]");
                sender.sendAction(IrisLanguage.text(
                        RuntimeProgressMessages.WORLD_PREGEN_ACTION,
                        MessageArgument.trusted("bar", bar.toString()),
                        MessageArgument.trusted("percent", percent)
                ));
                return;
            }

            sender.sendMessage(IrisLanguage.text(
                    RuntimeProgressMessages.WORLD_PREGEN_CONSOLE,
                    MessageArgument.trusted("percent", percent)
            ));
        }, interval));
        return taskId;
    }

    private void hidePregenLane(boolean shown) {
        if (!shown) {
            return;
        }
        BukkitPlatform.hudLanes().hide(sender.player(), "iris:pregen");
        sender.sendAction(" ");
    }

    private void cancelRepeatingTask(AtomicInteger taskId) {
        if (taskId == null) {
            return;
        }

        int id = taskId.getAndSet(-1);
        if (id >= 0) {
            J.car(id);
        }
    }

    private static boolean containsCreateWorldUnsupportedOperation(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            if (cursor instanceof UnsupportedOperationException) {
                for (StackTraceElement element : cursor.getStackTrace()) {
                    if ("org.bukkit.craftbukkit.CraftServer".equals(element.getClassName())
                            && "createWorld".equals(element.getMethodName())) {
                        return true;
                    }
                }
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private static boolean containsMissingDimensionTypes(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            if (cursor instanceof IllegalStateException && String.valueOf(cursor.getMessage()).contains("Missing dimension types")) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private void rollbackWorldCreation(
            NamespacedKey worldKey,
            World createdWorld,
            PlatformChunkGenerator stagedGenerator,
            File storageRoot,
            boolean bukkitRegistered,
            Throwable failure
    ) {
        World activeWorld = createdWorld == null ? WorldIdentity.resolve(worldKey).orElse(null) : createdWorld;
        boolean safeToDelete = activeWorld != null || !containsTimeout(failure);
        if (activeWorld == null && stagedGenerator != null) {
            // The world never materialized, so no unload path will ever close the staged
            // generator; without this it stays registered on WorldInitEvent and attaches a
            // second engine when a same-name world is created later.
            try {
                stagedGenerator.closeAsync().get(ROLLBACK_PHASE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (Throwable rollbackFailure) {
                failure.addSuppressed(unwrapFailure(rollbackFailure));
            }
        }
        if (activeWorld != null) {
            IrisToolbelt.beginWorldMaintenance(activeWorld, "world-create-rollback", true);
            try {
                PlatformChunkGenerator generator = IrisToolbelt.access(activeWorld);
                boolean evacuated = Boolean.TRUE.equals(IrisToolbelt.evacuateAsync(activeWorld)
                        .get(ROLLBACK_PHASE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
                if (!evacuated) {
                    safeToDelete = false;
                    failure.addSuppressed(new IllegalStateException(
                            "Rollback could not evacuate world \"" + name + "\"."));
                }
                boolean unloaded = safeToDelete && Boolean.TRUE.equals(WorldLifecycleService.get()
                        .unloadAsync(activeWorld, true)
                        .get(ROLLBACK_PHASE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
                if (!unloaded) {
                    safeToDelete = false;
                    failure.addSuppressed(new IllegalStateException("Rollback could not unload world \"" + name + "\"."));
                }
                if (safeToDelete && generator != null) {
                    generator.closeAsync().get(ROLLBACK_PHASE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                }
                // A staged generator the world never bound (e.g. the creation failed after a
                // retry re-staged a fresh one) has no unload path either.
                if (stagedGenerator != null && stagedGenerator != generator) {
                    stagedGenerator.closeAsync().get(ROLLBACK_PHASE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                }
            } catch (Throwable rollbackFailure) {
                Throwable cause = unwrapFailure(rollbackFailure);
                failure.addSuppressed(cause);
                safeToDelete = false;
                if (cause instanceof TimeoutException) {
                    ServerConfigurator.restart("World creation rollback timed out for \"" + name + "\".");
                }
            } finally {
                IrisToolbelt.endWorldMaintenance(activeWorld, "world-create-rollback", true);
            }
        }

        if (bukkitRegistered) {
            try {
                CompletableFuture<Boolean> multiverseRemoval = J.sfut(
                        () -> IrisServices.get(MultiverseCoreLink.class).removeFromConfig(
                                IrisWorldStorage.configuredWorldName(
                                        worldKey,
                                        IrisWorldStorage.levelRoot().getName()
                                )
                        )
                );
                if (multiverseRemoval == null) {
                    throw new IllegalStateException("Failed to schedule Multiverse rollback for \"" + name + "\".");
                }
                try {
                    multiverseRemoval.get(30L, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    ServerConfigurator.restart("Multiverse rollback timed out for \"" + name + "\".");
                    throw e;
                }
            } catch (Throwable rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            try {
                BukkitWorldConfiguration.remove(
                        BUKKIT_YML,
                        IrisWorldStorage.configuredWorldName(worldKey, IrisWorldStorage.levelRoot().getName())
                );
            } catch (Throwable rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
        try {
            IrisWorlds.get().remove(worldKey.toString());
        } catch (Throwable rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
        if (!safeToDelete) {
            queueRollbackDeletion(name, failure);
            return;
        }
        try {
            AtomicDirectoryPublisher.deleteTree(storageRoot.toPath());
        } catch (Throwable rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
            queueRollbackDeletion(name, failure);
        }
    }

    private void queueRollbackDeletion(String worldName, Throwable failure) {
        try {
            IrisServices.get(WorldDeletionQueue.class).queueExactForStartupDeletion(List.of(worldName));
        } catch (Throwable queueFailure) {
            failure.addSuppressed(queueFailure);
        }
    }

    private static boolean containsTimeout(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            if (cursor instanceof TimeoutException) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private static Throwable unwrapFailure(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor instanceof CompletionException || cursor instanceof ExecutionException) {
            if (cursor.getCause() == null) {
                break;
            }
            cursor = cursor.getCause();
        }
        return cursor;
    }

    public enum DatapackPreparation {
        INSTALL_IF_CHANGED(false, false),
        REUSE_LOADED_RUNTIME_IF_READY(true, false),
        FORCE_REUSE_LOADED_RUNTIME(true, true);

        private final boolean reusesLoadedRuntime;
        private final boolean forcesLoadedRuntime;

        DatapackPreparation(boolean reusesLoadedRuntime, boolean forcesLoadedRuntime) {
            this.reusesLoadedRuntime = reusesLoadedRuntime;
            this.forcesLoadedRuntime = forcesLoadedRuntime;
        }

        boolean requiresInstall(boolean runtimeReady) {
            return !forcesLoadedRuntime && (!reusesLoadedRuntime || !runtimeReady);
        }

        public boolean forcesLoadedRuntime() {
            return forcesLoadedRuntime;
        }
    }
}
