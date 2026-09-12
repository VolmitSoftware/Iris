package art.arcane.iris.studio.object;

import art.arcane.iris.world.runtime.TransientWorldCleanupSupport;
import art.arcane.iris.world.runtime.WorldDeletionQueue;
import art.arcane.iris.world.runtime.WorldRuntimeControlService;

import art.arcane.iris.platform.bukkit.BukkitWorldBinding;
import art.arcane.iris.world.IrisWorldStorage;
import art.arcane.iris.pack.datapack.ServerConfigurator;
import art.arcane.iris.world.lifecycle.LifecycleOperationCoordinator;
import art.arcane.iris.integration.MultiverseCoreLink;
import art.arcane.iris.platform.bukkit.nms.INMS;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.world.lifecycle.WorldLifecycleService;
import art.arcane.iris.pack.AtomicDirectoryPublisher;
import art.arcane.iris.studio.workspace.IrisProject;
import art.arcane.iris.studio.workspace.IrisCodeWorkspace;
import art.arcane.iris.world.IrisCreator;
import art.arcane.iris.world.IrisToolbelt;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.platform.generation.BukkitChunkGenerator;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.exceptions.IrisException;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class StudioOpenCoordinator {
    private static final long STUDIO_CLOSE_TIMEOUT_SECONDS = 120L;
    private static final long STUDIO_STRUCTURE_ACTIVATION_TIMEOUT_SECONDS = 30L;
    private static volatile StudioOpenCoordinator instance;

    private StudioOpenCoordinator() {
    }

    public static StudioOpenCoordinator get() {
        StudioOpenCoordinator current = instance;
        if (current != null) {
            return current;
        }

        synchronized (StudioOpenCoordinator.class) {
            if (instance != null) {
                return instance;
            }

            instance = new StudioOpenCoordinator();
            return instance;
        }
    }

    public CompletableFuture<StudioOpenResult> open(StudioOpenRequest request) {
        CompletableFuture<StudioOpenResult> future = new CompletableFuture<>();
        J.aBukkit(() -> executeOpen(request, future));
        return future;
    }

    public CompletableFuture<StudioCloseResult> closeProject(IrisProject project) {
        if (project == null) {
            return CompletableFuture.completedFuture(new StudioCloseResult(null, true, true, false, null));
        }

        PlatformChunkGenerator provider = project.getActiveProvider();
        if (provider == null) {
            return CompletableFuture.completedFuture(new StudioCloseResult(null, true, true, false, null));
        }

        World world = BukkitWorldBinding.world(provider.getTarget().getWorld());
        String worldName = world == null
                ? IrisWorldStorage.logicalName(WorldIdentity.parse(provider.getTarget().getWorld().identity()))
                : IrisWorldStorage.logicalName(world);
        return closeWorldCoordinated(provider, worldName, world, true, project);
    }

    public CompletableFuture<Boolean> teleportPlayerToProject(
            IrisProject project,
            Player player
    ) {
        if (project == null || player == null) {
            return CompletableFuture.completedFuture(false);
        }
        PlatformChunkGenerator provider = project.getActiveProvider();
        if (provider == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Studio runtime provider is unavailable."));
        }
        World world = BukkitWorldBinding.world(provider.getTarget().getWorld());
        if (world == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Studio world is not loaded."));
        }
        Location entry = WorldRuntimeControlService.get().resolveEntryAnchor(world, provider);
        if (entry == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Studio entry point could not be resolved."));
        }
        CompletableFuture<Boolean> teleport = project.getActiveOpenKind().teleportThroughStandardEntry()
                ? WorldRuntimeControlService.get().teleportInMode(player, entry, GameMode.SPECTATOR)
                : WorldRuntimeControlService.get().teleport(player, entry);
        if (teleport == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Studio native teleport returned no completion future."));
        }
        return teleport;
    }

    private void executeOpen(StudioOpenRequest request, CompletableFuture<StudioOpenResult> future) {
        World world = null;
        PlatformChunkGenerator provider = null;
        try {
            long openStart = System.nanoTime();
            long t = openStart;
            updateStage(request, "resolve_dimension", 0.04D);
            if (IrisToolbelt.getDimension(request.dimensionKey()) == null) {
                throw new IrisException("Dimension cannot be found for id " + request.dimensionKey() + ".");
            }

            updateStage(request, "prepare_world_pack", 0.10D);
            cleanupStaleTransientWorlds(request.worldName());
            t = logStudioPhase(request, "resolve_dimension_and_cleanup", t, openStart);

            updateStage(request, "install_datapacks", 0.18D);
            IrisCreator creator = IrisToolbelt.createWorld()
                    .seed(request.seed())
                    .sender(request.sender())
                    .studio(true)
                    .name(request.worldName())
                    .dimension(request.dimensionKey())
                    .datapackPreparation(request.openKind().datapackPreparation())
                    .studioProgressConsumer((progress, stage) -> updateStage(request, mapCreatorStage(stage), progress))
                    .studioTimingConsumer((phase, duration) -> logMeasuredStudioPhase(request, phase, duration));
            world = creator.create();
            t = logStudioPhase(request, "create_world_total", t, openStart);
            provider = IrisToolbelt.access(world);
            if (provider == null) {
                throw new IllegalStateException("Studio runtime provider is unavailable for world \"" + request.worldName() + "\".");
            }
            World entryWorld = world;
            PlatformChunkGenerator entryProvider = provider;
            CompletableFuture<Void> entryBootstrap = J.afut(
                    () -> endStudioEntryBootstrap(entryWorld, entryProvider));

            updateStage(request, "apply_world_rules", 0.72D);
            final World rulesWorld = world;
            CompletableFuture<Boolean> rulesApplied =
                    J.sfut(() -> WorldRuntimeControlService.get().applyStudioWorldRules(rulesWorld));
            if (rulesApplied != null) {
                rulesApplied.get(15L, TimeUnit.SECONDS);
            }
            t = logStudioPhase(request, "apply_world_rules", t, openStart);

            updateStage(request, "prepare_generator", 0.78D);
            if (request.openKind().prepareGeneratorState()) {
                WorldRuntimeControlService.get().prepareGenerator(world);
            }
            t = logStudioPhase(request, "prepare_generator", t, openStart);

            Location entryAnchor = WorldRuntimeControlService.get().resolveEntryAnchor(world);
            if (entryAnchor == null) {
                throw new IllegalStateException("Studio entry anchor could not be resolved.");
            }
            t = logStudioPhase(request, "resolve_entry_anchor", t, openStart);

            updateStage(request, "prepare_structure_rings", 0.79D);
            entryBootstrap.get(STUDIO_STRUCTURE_ACTIVATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            t = logStudioPhase(request, "prepare_structure_rings", t, openStart);

            updateStage(request, "prepare_generation_caches", 0.88D);
            if (entryProvider.getEngine() instanceof IrisEngine irisEngine) {
                irisEngine.awaitGenerationCacheWarm();
            }
            t = logStudioPhase(request, "prepare_generation_caches", t, openStart);

            Location entryLocation = entryAnchor;

            if (request.openKind().teleportThroughStandardEntry()
                    && request.playerName() != null
                    && !request.playerName().isBlank()) {
                updateStage(request, "teleport_player", 0.96D);
                Player player = resolvePlayer(request.playerName());
                if (player == null) {
                    throw new IllegalStateException("Player \"" + request.playerName() + "\" is not online.");
                }
                Boolean teleported;
                try {
                    CompletableFuture<Boolean> nativeTeleportFuture = WorldRuntimeControlService.get().teleportInMode(
                            player,
                            entryLocation,
                            GameMode.SPECTATOR);
                    if (nativeTeleportFuture == null) {
                        throw new IllegalStateException(
                                "Studio native teleport returned no completion future.");
                    }
                    teleported = nativeTeleportFuture.get();
                } catch (ExecutionException e) {
                    Throwable failure = unwrapFailure(e);
                    throw new IllegalStateException("Studio teleport failed.", failure);
                }
                if (!Boolean.TRUE.equals(teleported)) {
                    throw new IllegalStateException("Studio teleport did not complete successfully.");
                }
                t = logStudioPhase(request, "teleport_standard_entry", t, openStart);
                IrisLogging.info("Studio player %s arrived in %dms: dimension=%s world=%s",
                        player.getName(),
                        elapsedMillis(request.requestedAtNanos()),
                        request.dimensionKey(),
                        world.getName());
            }

            updateStage(request, "finalize_open", 1.00D);
            if (request.project() != null) {
                request.project().setActiveOpenKind(request.openKind());
                request.project().setActiveProvider(provider);
            }
            if (request.openKind().openWorkspace() && request.project() != null) {
                new IrisCodeWorkspace(request.project()).openVSCode(request.sender());
            }
            runOpenFinalizer(request.onDone(), world);
            t = logStudioPhase(request, "finalize_open", t, openStart);

            IrisLogging.debug("Studio open: " + world.getName() + " ready in "
                    + elapsedMillis(openStart) + "ms");
            future.complete(new StudioOpenResult(world, entryLocation));
        } catch (Throwable e) {
            abandonStudioEntryBootstrap(world, e);
            IrisLogging.reportError("Studio open failed for world \"" + request.worldName() + "\".", e);
            if (!request.retainOnFailure()) {
                updateStage(request, "cleanup", 1.00D);
                if (LifecycleOperationCoordinator.get()
                        .active(LifecycleOperationCoordinator.Domain.SERVER_LIFECYCLE)
                        .isPresent()) {
                    deferFailedOpenCleanupToRestart(
                            provider,
                            request.worldName(),
                            world);
                } else {
                    try {
                        CompletableFuture<Void> cleanup = cleanupFailedOpen(
                                provider,
                                request.worldName(),
                                world,
                                request.project());
                        cleanup.get(45L, TimeUnit.SECONDS);
                    } catch (Throwable cleanupError) {
                        IrisLogging.reportError("Studio cleanup failed for world \""
                                + request.worldName() + "\".", unwrapFailure(cleanupError));
                    }
                }
            }
            future.completeExceptionally(e);
        }
    }

    private void deferFailedOpenCleanupToRestart(
            PlatformChunkGenerator provider,
            String worldName,
            World world
    ) {
        if (provider != null || world != null || transientWorldStorageExists(worldName)) {
            queueStartupCleanup(
                    worldName,
                    new IllegalStateException("Studio cleanup deferred across the queued server restart."));
        }
    }

    private boolean transientWorldStorageExists(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return false;
        }
        try {
            File root = IrisWorldStorage.requireSafeManagedDimensionRoot(
                    IrisWorldStorage.managedKeyFromName(worldName));
            return Files.exists(root.toPath(), LinkOption.NOFOLLOW_LINKS);
        } catch (RuntimeException failure) {
            return true;
        }
    }

    private long logStudioPhase(StudioOpenRequest request, String phase, long t, long openStart) {
        long now = System.nanoTime();
        IrisLogging.debug("[Studio timing] world=%s kind=%s phase=%s duration=%dms cumulative=%dms",
                request.worldName(),
                request.openKind().name().toLowerCase(Locale.ROOT),
                phase,
                TimeUnit.NANOSECONDS.toMillis(now - t),
                TimeUnit.NANOSECONDS.toMillis(now - openStart));
        return now;
    }

    private void runOpenFinalizer(Consumer<World> finalizer, World world)
            throws InterruptedException, ExecutionException, TimeoutException {
        if (finalizer == null) {
            return;
        }
        if (J.isPrimaryThread()) {
            finalizer.accept(world);
            return;
        }

        CompletableFuture<Void> completion = J.sfut(() -> finalizer.accept(world));
        completion.get(STUDIO_STRUCTURE_ACTIVATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private long elapsedMillis(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }

    private void logMeasuredStudioPhase(StudioOpenRequest request, String phase, long duration) {
        IrisLogging.debug("[Studio timing] world=%s kind=%s phase=%s duration=%dms",
                request.worldName(),
                request.openKind().name().toLowerCase(Locale.ROOT),
                phase,
                duration);
    }

    private void endStudioEntryBootstrap(World world, PlatformChunkGenerator provider) {
        if (!(provider instanceof BukkitChunkGenerator bukkitGenerator)) {
            throw new IllegalStateException("Studio runtime provider cannot finish its entry bootstrap.");
        }
        AtomicBoolean activationClaim = new AtomicBoolean(true);
        CompletableFuture<CompletableFuture<Void>> scheduledActivation = J.sfut(() -> {
            if (!activationClaim.compareAndSet(true, false)) {
                INMS.get().abandonStudioStructureBootstrap(world);
                return CompletableFuture.failedFuture(new IllegalStateException(
                        "Studio native structure activation was cancelled before it began."));
            }
            try {
                CompletableFuture<Void> nativeActivation =
                        INMS.get().completeStudioStructureBootstrap(world);
                if (nativeActivation == null) {
                    return CompletableFuture.failedFuture(new IllegalStateException(
                            "Studio native structure activation returned no completion future."));
                }
                return nativeActivation;
            } catch (ReflectiveOperationException e) {
                return CompletableFuture.failedFuture(new IllegalStateException(
                        "Studio native structure state could not be activated after entry bootstrap.", e));
            }
        });
        CompletableFuture<Void> activation = scheduledActivation
                .thenCompose(nativeActivation -> nativeActivation)
                .thenCompose(ignored -> J.sfut(bukkitGenerator::endStudioEntryBootstrap));
        try {
            activation.get(STUDIO_STRUCTURE_ACTIVATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            activationClaim.compareAndSet(true, false);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Studio native structure activation was interrupted.", e);
        } catch (ExecutionException e) {
            activationClaim.compareAndSet(true, false);
            throw new IllegalStateException("Studio native structure activation did not complete.",
                    unwrapFailure(e));
        } catch (TimeoutException e) {
            if (!activationClaim.compareAndSet(true, false)) {
                try {
                    activation.get(5L, TimeUnit.SECONDS);
                    return;
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(
                            "Studio native structure activation was interrupted.", interrupted);
                } catch (ExecutionException | TimeoutException settlementFailure) {
                    throw new IllegalStateException(
                            "Studio native structure activation did not settle after claiming completion.",
                            unwrapFailure(settlementFailure));
                }
            }
            throw new IllegalStateException("Studio native structure activation did not complete.", e);
        }
    }

    private void abandonStudioEntryBootstrap(World world, Throwable failure) {
        if (world == null) {
            return;
        }
        if (J.isPrimaryThread()) {
            try {
                INMS.get().abandonStudioStructureBootstrap(world);
            } catch (Throwable abandonmentFailure) {
                failure.addSuppressed(abandonmentFailure);
            }
            return;
        }
        CompletableFuture<Void> abandonment = J.sfut(
                () -> INMS.get().abandonStudioStructureBootstrap(world));
        try {
            abandonment.get(STUDIO_STRUCTURE_ACTIVATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failure.addSuppressed(new IllegalStateException(
                    "Studio native structure abandonment was interrupted.", e));
        } catch (ExecutionException | TimeoutException e) {
            failure.addSuppressed(new IllegalStateException(
                    "Studio native structure abandonment did not complete.", unwrapFailure(e)));
        }
    }

    private CompletableFuture<Void> cleanupFailedOpen(
            PlatformChunkGenerator provider,
            String worldName,
            World world,
            IrisProject project
    ) {
        return closeWorldCoordinated(provider, worldName, world, true, project)
                .thenCompose(result -> result.failureCause() == null
                        ? CompletableFuture.completedFuture(null)
                        : CompletableFuture.failedFuture(result.failureCause()));
    }

    private CompletableFuture<StudioCloseResult> closeWorldCoordinated(
            PlatformChunkGenerator provider,
            String worldName,
            World world,
            boolean deleteFolder,
            IrisProject project
    ) {
        String operationTarget = worldName == null || worldName.isBlank() ? "unknown-studio-world" : worldName;
        LifecycleOperationCoordinator.Lease lease;
        try {
            lease = LifecycleOperationCoordinator.get().acquire(
                    LifecycleOperationCoordinator.Domain.WORLD_MUTATION,
                    LifecycleOperationCoordinator.OperationKind.STUDIO_CLOSE,
                    operationTarget
            );
        } catch (Throwable failure) {
            boolean queued = deleteFolder && queueStartupCleanup(worldName, failure);
            return CompletableFuture.completedFuture(new StudioCloseResult(
                    worldName,
                    false,
                    false,
                    queued,
                    failure
            ));
        }

        CompletableFuture<StudioCloseResult> closeFuture;
        try {
            closeFuture = closeWorldReserved(provider, worldName, world, deleteFolder, project);
        } catch (Throwable failure) {
            boolean queued = deleteFolder && queueStartupCleanup(worldName, failure);
            closeFuture = CompletableFuture.completedFuture(new StudioCloseResult(
                    worldName,
                    false,
                    false,
                    queued,
                    failure
            ));
        }
        return closeFuture.whenComplete((result, throwable) -> lease.close());
    }

    private CompletableFuture<StudioCloseResult> closeWorldReserved(
            PlatformChunkGenerator provider,
            String worldName,
            World world,
            boolean deleteFolder,
            IrisProject project
    ) {
        AtomicBoolean unloadConfirmed = new AtomicBoolean(false);
        AtomicBoolean unloadStarted = new AtomicBoolean(false);
        AtomicBoolean folderDeleted = new AtomicBoolean(!deleteFolder);
        AtomicBoolean terminalTimeout = new AtomicBoolean(false);
        if (world != null) {
            IrisToolbelt.beginWorldMaintenance(world, "studio-close", true);
        }

        CompletableFuture<Void> sequence = sequenceStudioClose(
                () -> evacuateWorldFamily(worldName, world),
                () -> {
                    unloadStarted.set(true);
                    return unloadWorldFamily(worldName, world).thenRun(() -> {
                        if (terminalTimeout.get()) {
                            throw new CompletionException(new TimeoutException(
                                    "Studio close stopped after its terminal timeout."));
                        }
                        unloadConfirmed.set(true);
                        if (project != null) {
                            project.setActiveProvider(null);
                            project.setActiveOpenKind(null);
                        }
                    });
                },
                () -> provider == null ? CompletableFuture.completedFuture(null) : provider.closeAsync(),
                () -> deleteFolder
                        ? deleteWorldFamily(worldName).thenRun(() -> folderDeleted.set(true))
                        : CompletableFuture.completedFuture(null),
                terminalTimeout::get
        );
        CompletableFuture<StudioCloseResult> operation = guardCloseCompletion(
                sequence,
                terminalTimeout,
                worldName)
                .thenApply(ignored -> new StudioCloseResult(
                        worldName,
                        true,
                        folderDeleted.get(),
                        false,
                        null
                ))
                .exceptionally(throwable -> {
                    Throwable failure = unwrapFailure(throwable);
                    requestRestartAfterPartialClose(worldName, unloadStarted.get());
                    boolean queued = deleteFolder && queueStartupCleanup(worldName, failure);
                    return new StudioCloseResult(
                            worldName,
                            unloadConfirmed.get(),
                            folderDeleted.get(),
                            queued,
                            failure
                    );
                });
        return operation.whenComplete((result, throwable) -> {
            if (world != null) {
                IrisToolbelt.endWorldMaintenance(world, "studio-close", true);
            }
        });
    }

    static void requestRestartAfterPartialClose(String worldName, boolean unloadStarted) {
        if (unloadStarted) {
            ServerConfigurator.restart("Studio close failed after world unload began for \"" + worldName + "\".");
        }
    }

    static CompletableFuture<Void> sequenceStudioClose(
            Supplier<CompletableFuture<Void>> evacuate,
            Supplier<CompletableFuture<Void>> unload,
            Supplier<CompletableFuture<Void>> closeGenerator,
            Supplier<CompletableFuture<Void>> deleteFolders
    ) {
        return sequenceStudioClose(evacuate, unload, closeGenerator, deleteFolders, () -> false);
    }

    static CompletableFuture<Void> sequenceStudioClose(
            Supplier<CompletableFuture<Void>> evacuate,
            Supplier<CompletableFuture<Void>> unload,
            Supplier<CompletableFuture<Void>> closeGenerator,
            Supplier<CompletableFuture<Void>> deleteFolders,
            BooleanSupplier terminalTimeout
    ) {
        return invokePhase(evacuate)
                .thenCompose(ignored -> invokePhaseUnlessTimedOut(unload, terminalTimeout))
                .thenCompose(ignored -> invokePhaseUnlessTimedOut(closeGenerator, terminalTimeout))
                .thenCompose(ignored -> invokePhaseUnlessTimedOut(deleteFolders, terminalTimeout));
    }

    private static CompletableFuture<Void> invokePhase(Supplier<CompletableFuture<Void>> phase) {
        try {
            CompletableFuture<Void> future = phase.get();
            if (future == null) {
                return CompletableFuture.failedFuture(new IllegalStateException("Studio close phase returned no completion future."));
            }
            return future;
        } catch (Throwable failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static CompletableFuture<Void> invokePhaseUnlessTimedOut(
            Supplier<CompletableFuture<Void>> phase,
            BooleanSupplier terminalTimeout
    ) {
        if (terminalTimeout.getAsBoolean()) {
            return CompletableFuture.failedFuture(new TimeoutException(
                    "Studio close stopped after its terminal timeout."));
        }
        return invokePhase(phase);
    }

    private CompletableFuture<Void> guardCloseCompletion(
            CompletableFuture<Void> source,
            AtomicBoolean terminalTimeout,
            String worldName
    ) {
        CompletableFuture<Void> guarded = new CompletableFuture<>();
        AtomicBoolean settled = new AtomicBoolean(false);
        source.whenComplete((ignored, throwable) -> {
            if (!settled.compareAndSet(false, true)) {
                return;
            }
            if (throwable == null) {
                guarded.complete(null);
            } else {
                guarded.completeExceptionally(throwable);
            }
        });
        CompletableFuture.delayedExecutor(STUDIO_CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS).execute(() -> {
            if (!settled.compareAndSet(false, true)) {
                return;
            }
            terminalTimeout.set(true);
            TimeoutException timeout = new TimeoutException(
                    "Studio close did not settle within " + STUDIO_CLOSE_TIMEOUT_SECONDS
                            + " seconds for \"" + worldName + "\".");
            ServerConfigurator.restart("Studio close timed out for \"" + worldName + "\".");
            guarded.completeExceptionally(timeout);
        });
        return guarded;
    }

    private CompletableFuture<Void> evacuateWorldFamily(String worldName, World primaryWorld) {
        List<World> loadedWorlds = loadedWorldFamily(worldName, primaryWorld);
        if (loadedWorlds.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        ArrayList<CompletableFuture<Void>> evacuations = new ArrayList<>(loadedWorlds.size());
        for (World loadedWorld : loadedWorlds) {
            CompletableFuture<Void> evacuation = J.sfut(() -> IrisToolbelt.evacuateAsync(loadedWorld))
                    .thenCompose(evacuationFuture -> evacuationFuture)
                    .thenCompose(evacuated -> Boolean.TRUE.equals(evacuated)
                            ? CompletableFuture.completedFuture(null)
                            : CompletableFuture.failedFuture(new IllegalStateException(
                                    "Studio player evacuation failed for \"" + loadedWorld.getName() + "\".")));
            evacuations.add(evacuation);
        }
        return CompletableFuture.allOf(evacuations.toArray(CompletableFuture[]::new));
    }

    private CompletableFuture<Void> unloadWorldFamily(String worldName, World primaryWorld) {
        List<World> loadedWorlds = loadedWorldFamily(worldName, primaryWorld);
        ArrayList<CompletableFuture<Boolean>> unloads = new ArrayList<>(loadedWorlds.size());
        for (World loadedWorld : loadedWorlds) {
            CompletableFuture<Boolean> unload = J.sfut(() ->
                            IrisServices.get(MultiverseCoreLink.class)
                                    .removeIfPresent(loadedWorld))
                    .thenCompose(ignored -> WorldLifecycleService.get().unloadAsync(loadedWorld, false));
            unloads.add(unload);
        }

        return CompletableFuture.allOf(unloads.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
            for (CompletableFuture<Boolean> unload : unloads) {
                if (!Boolean.TRUE.equals(unload.join())) {
                    throw new CompletionException(new IllegalStateException(
                            "Studio world family unload returned false for \"" + worldName + "\"."));
                }
            }
            if (isWorldFamilyLoaded(worldName)) {
                throw new CompletionException(new IllegalStateException(
                        "Studio world family remained loaded after confirmed unload for \"" + worldName + "\"."));
            }
            return null;
        });
    }

    private List<World> loadedWorldFamily(String worldName, World primaryWorld) {
        LinkedHashSet<World> worlds = new LinkedHashSet<>();
        if (primaryWorld != null) {
            worlds.add(primaryWorld);
        }
        if (worldName == null || worldName.isBlank()) {
            return List.copyOf(worlds);
        }
        for (String familyWorldName : TransientWorldCleanupSupport.worldFamilyNames(worldName)) {
            WorldIdentity.resolve(IrisWorldStorage.keyFromName(familyWorldName)).ifPresent(worlds::add);
        }
        return List.copyOf(worlds);
    }

    private CompletableFuture<Void> deleteWorldFamily(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        if (isWorldFamilyLoaded(worldName)) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Refusing to delete a loaded studio world family for \"" + worldName + "\"."));
        }

        return CompletableFuture.runAsync(() -> {
            for (String familyWorldName : TransientWorldCleanupSupport.worldFamilyNames(worldName)) {
                try {
                    if (isWorldFamilyLoaded(worldName)) {
                        throw new IOException("Studio world family became loaded before deletion for \""
                                + worldName + "\".");
                    }
                    File folder = IrisWorldStorage.requireSafeManagedDimensionRoot(
                            IrisWorldStorage.managedKeyFromName(familyWorldName));
                    AtomicDirectoryPublisher.deleteTree(folder.toPath());
                } catch (IOException | IllegalArgumentException failure) {
                    throw new CompletionException(failure);
                }
            }
        });
    }

    private boolean queueStartupCleanup(String worldName, Throwable failure) {
        if (worldName == null || worldName.isBlank()) {
            return false;
        }
        try {
            IrisServices.get(WorldDeletionQueue.class).queueFamilyForStartupDeletion(Collections.singleton(worldName));
            return true;
        } catch (Throwable queueFailure) {
            if (failure != null) {
                failure.addSuppressed(queueFailure);
            }
            IrisLogging.reportError("Failed to queue deferred deletion for world \"" + worldName + "\".", queueFailure);
            return false;
        }
    }

    private void cleanupStaleTransientWorlds(String worldName) {
        LinkedHashSet<String> staleWorldNames = collectSafeTransientWorldNames();
        String requestedBaseName = TransientWorldCleanupSupport.transientStudioBaseWorldName(worldName);
        if (requestedBaseName != null) {
            staleWorldNames.add(requestedBaseName);
        }

        for (String staleWorldName : staleWorldNames) {
            try {
                StudioCloseResult cleanupResult = closeWorldCoordinated(
                        null,
                        staleWorldName,
                        null,
                        true,
                        null
                ).get(30L, TimeUnit.SECONDS);
                if (cleanupResult.failureCause() != null) {
                    IrisLogging.reportError("Stale studio world cleanup failed for \"" + staleWorldName + "\".", cleanupResult.failureCause());
                }
            } catch (Throwable failure) {
                IrisLogging.reportError("Stale studio world cleanup failed for \"" + staleWorldName + "\".", unwrapFailure(failure));
            }
        }
    }

    private LinkedHashSet<String> collectSafeTransientWorldNames() {
        LinkedHashSet<String> worldNames = new LinkedHashSet<>();
        Path irisNamespace = IrisWorldStorage.levelRoot()
                .toPath()
                .toAbsolutePath()
                .normalize()
                .resolve("dimensions")
                .resolve("iris");
        if (!Files.exists(irisNamespace, LinkOption.NOFOLLOW_LINKS)) {
            return worldNames;
        }
        if (Files.isSymbolicLink(irisNamespace) || !Files.isDirectory(irisNamespace, LinkOption.NOFOLLOW_LINKS)) {
            IrisLogging.warn("Skipping stale studio cleanup because Iris dimension storage is unsafe: " + irisNamespace);
            return worldNames;
        }

        try (DirectoryStream<Path> children = Files.newDirectoryStream(irisNamespace)) {
            for (Path child : children) {
                if (Files.isSymbolicLink(child) || !Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                String transientName = TransientWorldCleanupSupport.transientStudioBaseWorldName(
                        child.getFileName().toString());
                if (transientName != null) {
                    worldNames.add(transientName);
                }
            }
        } catch (IOException failure) {
            IrisLogging.reportError("Failed to inspect stale studio worlds in \"" + irisNamespace + "\".", failure);
        }
        return worldNames;
    }

    private void updateStage(StudioOpenRequest request, String stage, double progress) {
        if (request.progressConsumer() != null) {
            request.progressConsumer().accept(new StudioOpenProgress(progress, stage));
        }
    }

    private String mapCreatorStage(String stage) {
        if (stage == null || stage.isBlank()) {
            return "create_world";
        }

        String normalized = stage.trim().toLowerCase();
        return switch (normalized) {
            case "resolve_dimension", "resolving dimension" -> "resolve_dimension";
            case "prepare_world_pack", "preparing world pack" -> "prepare_world_pack";
            case "install_datapacks", "installing datapacks", "datapacks ready" -> "install_datapacks";
            case "create_world", "creating world", "world created" -> "create_world";
            default -> normalized.replace(' ', '_');
        };
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

    private Player resolvePlayer(String playerName) {
        Player exact = Bukkit.getPlayerExact(playerName);
        if (exact != null) {
            return exact;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().equalsIgnoreCase(playerName)) {
                return player;
            }
        }

        return null;
    }

    private boolean isWorldFamilyLoaded(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return false;
        }

        for (String familyWorldName : TransientWorldCleanupSupport.worldFamilyNames(worldName)) {
            if (WorldIdentity.resolve(IrisWorldStorage.keyFromName(familyWorldName)).isPresent()) {
                return true;
            }
        }

        return false;
    }

    public record StudioOpenRequest(
            String dimensionKey,
            IrisProject project,
            VolmitSender sender,
            long seed,
            String worldName,
            String playerName,
            StudioOpenKind openKind,
            boolean retainOnFailure,
            Consumer<StudioOpenProgress> progressConsumer,
            Consumer<World> onDone,
            long requestedAtNanos
    ) {
        public StudioOpenRequest {
            openKind = Objects.requireNonNull(openKind, "Studio open kind");
            if (requestedAtNanos <= 0L) {
                throw new IllegalArgumentException("Studio request time must be positive.");
            }
        }

        public static StudioOpenRequest studioProject(
                IrisProject project,
                VolmitSender sender,
                long seed,
                StudioOpenKind openKind,
                Consumer<StudioOpenProgress> progressConsumer,
                Consumer<World> onDone
        ) {
            return studioProject(
                    project,
                    sender,
                    seed,
                    openKind,
                    progressConsumer,
                    onDone,
                    System.nanoTime());
        }

        public static StudioOpenRequest studioProject(
                IrisProject project,
                VolmitSender sender,
                long seed,
                StudioOpenKind openKind,
                Consumer<StudioOpenProgress> progressConsumer,
                Consumer<World> onDone,
                long requestedAtNanos
        ) {
            String playerName = sender != null && sender.isPlayer() && sender.player() != null ? sender.player().getName() : null;
            return new StudioOpenRequest(
                    project.getName(),
                    project,
                    sender,
                    seed,
                    "iris-" + UUID.randomUUID(),
                    playerName,
                    openKind,
                    false,
                    progressConsumer,
                    onDone,
                    requestedAtNanos
            );
        }
    }

    public enum StudioOpenKind {
        STANDARD(
                true,
                true,
                IrisCreator.DatapackPreparation.REUSE_LOADED_RUNTIME_IF_READY),
        FORCED_STANDARD(
                true,
                true,
                IrisCreator.DatapackPreparation.FORCE_REUSE_LOADED_RUNTIME),
        OBJECT(
                false,
                true,
                IrisCreator.DatapackPreparation.REUSE_LOADED_RUNTIME_IF_READY),
        JIGSAW(
                false,
                false,
                IrisCreator.DatapackPreparation.REUSE_LOADED_RUNTIME_IF_READY);

        private final boolean teleportThroughStandardEntry;
        private final boolean openWorkspace;
        private final IrisCreator.DatapackPreparation datapackPreparation;

        StudioOpenKind(
                boolean teleportThroughStandardEntry,
                boolean openWorkspace,
                IrisCreator.DatapackPreparation datapackPreparation
        ) {
            this.teleportThroughStandardEntry = teleportThroughStandardEntry;
            this.openWorkspace = openWorkspace;
            this.datapackPreparation = Objects.requireNonNull(
                    datapackPreparation,
                    "Studio datapack preparation");
        }

        public boolean teleportThroughStandardEntry() {
            return teleportThroughStandardEntry;
        }

        public boolean openWorkspace() {
            return openWorkspace;
        }

        public boolean prepareGeneratorState() {
            return this == STANDARD || this == FORCED_STANDARD;
        }

        public IrisCreator.DatapackPreparation datapackPreparation() {
            return datapackPreparation;
        }
    }

    public record StudioOpenProgress(double progress, String stage) {
    }

    public record StudioOpenResult(World world, Location entryLocation) {
    }

    public record StudioCloseResult(
            String worldName,
            boolean unloadCompletedLive,
            boolean folderDeletionCompletedLive,
            boolean startupCleanupQueued,
            Throwable failureCause
    ) {
        public boolean successful() {
            return failureCause == null;
        }
    }

}
