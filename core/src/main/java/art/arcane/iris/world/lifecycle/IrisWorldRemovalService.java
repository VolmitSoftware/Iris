package art.arcane.iris.world.lifecycle;

import art.arcane.iris.world.IrisWorldStorage;
import art.arcane.iris.world.IrisWorlds;
import art.arcane.iris.pack.datapack.ServerConfigurator;
import art.arcane.iris.world.SnapshotDirectoryTreeDeleter;
import art.arcane.iris.world.WorldRemovalPathPolicy;
import art.arcane.iris.integration.MultiverseCoreLink;
import art.arcane.iris.world.runtime.WorldDeletionQueue;
import art.arcane.iris.world.IrisToolbelt;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.platform.bootstrap.ServerProperties;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class IrisWorldRemovalService {
    private static final long PHASE_TIMEOUT_SECONDS = 120L;
    private static final IrisWorldRemovalService INSTANCE = new IrisWorldRemovalService(
            LifecycleOperationCoordinator.get(),
            new BukkitRemovalBackend(ForkJoinPool.commonPool())
    );

    private final LifecycleOperationCoordinator coordinator;
    private final RemovalBackend backend;
    private final long phaseTimeoutMillis;
    private final Consumer<String> restartRequester;

    IrisWorldRemovalService(LifecycleOperationCoordinator coordinator, RemovalBackend backend) {
        this(coordinator, new ServiceOptions(
                backend,
                TimeUnit.SECONDS.toMillis(PHASE_TIMEOUT_SECONDS),
                ServerConfigurator::restart
        ));
    }

    IrisWorldRemovalService(LifecycleOperationCoordinator coordinator, ServiceOptions options) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        ServiceOptions requiredOptions = Objects.requireNonNull(options, "options");
        backend = requiredOptions.backend();
        phaseTimeoutMillis = requiredOptions.phaseTimeoutMillis();
        restartRequester = requiredOptions.restartRequester();
    }

    public static IrisWorldRemovalService get() {
        return INSTANCE;
    }

    public CompletableFuture<RemovalResult> remove(String worldIdentifier, boolean deleteFiles) {
        String operationTarget = normalizeOperationTarget(worldIdentifier);
        if (operationTarget == null) {
            return CompletableFuture.completedFuture(RemovalResult.failure(
                    RemovalStatus.INVALID_IDENTIFIER,
                    worldIdentifier,
                    null,
                    new IllegalArgumentException("World identifier cannot be empty.")
            ));
        }

        LifecycleOperationCoordinator.Lease lease;
        try {
            lease = coordinator.acquire(
                    LifecycleOperationCoordinator.Domain.WORLD_MUTATION,
                    LifecycleOperationCoordinator.OperationKind.WORLD_REMOVE,
                    operationTarget
            );
        } catch (LifecycleOperationCoordinator.BusyException exception) {
            return CompletableFuture.completedFuture(RemovalResult.busy(worldIdentifier, exception.currentOperation()));
        } catch (Throwable failure) {
            return CompletableFuture.completedFuture(RemovalResult.failure(
                    RemovalStatus.INTERNAL_FAILURE,
                    worldIdentifier,
                    null,
                    failure
            ));
        }

        CompletableFuture<RemovalResult> operation;
        try {
            WorldRemovalPathPolicy.Target target = backend.resolveTarget(operationTarget);
            operation = execute(target, deleteFiles, new AtomicBoolean(false));
        } catch (WorldRemovalPathPolicy.Rejection rejection) {
            operation = CompletableFuture.completedFuture(RemovalResult.failure(
                    mapRejection(rejection.reason()),
                    worldIdentifier,
                    null,
                    rejection
            ));
        } catch (Throwable failure) {
            operation = CompletableFuture.completedFuture(RemovalResult.failure(
                    RemovalStatus.RESOLUTION_FAILED,
                    worldIdentifier,
                    null,
                    failure
            ));
        }

        CompletableFuture<RemovalResult> result = new CompletableFuture<>();
        operation.whenComplete((removalResult, throwable) -> completeAndRelease(
                result,
                lease,
                worldIdentifier,
                removalResult,
                throwable
        ));
        return result;
    }

    private CompletableFuture<RemovalResult> execute(
            WorldRemovalPathPolicy.Target target,
            boolean deleteFiles,
            AtomicBoolean terminal
    ) {
        return phase(RemovalStatus.RESOLUTION_FAILED, () -> backend.resolve(target), terminal)
                .thenCompose(resolvedWorld -> validateResolvedWorld(resolvedWorld))
                .thenCompose(resolvedWorld -> runWithMaintenance(resolvedWorld, deleteFiles, terminal))
                .exceptionally(throwable -> failureResult(target, throwable));
    }

    private CompletableFuture<RemovalResult> runWithMaintenance(
            ResolvedWorld resolved,
            boolean deleteFiles,
            AtomicBoolean terminal
    ) {
        CompletableFuture<RemovalResult> operation = phase(
                RemovalStatus.RESOLUTION_FAILED,
                () -> backend.beginMaintenance(resolved).thenApply(ignored -> resolved),
                terminal
        )
                .thenCompose(activeWorld -> phase(
                        RemovalStatus.TELEPORT_FAILED,
                        () -> backend.evacuatePlayers(activeWorld)
                                .thenApply(ignored -> activeWorld),
                        terminal
                ))
                .thenCompose(activeWorld -> phase(
                        RemovalStatus.UNLOAD_FAILED,
                        () -> backend.unloadWorld(activeWorld)
                                .thenApply(unloaded -> requireUnloaded(activeWorld, unloaded)),
                        terminal
                ))
                .thenCompose(activeWorld -> phase(
                        RemovalStatus.GENERATOR_CLOSE_FAILED,
                        () -> backend.closeGenerator(activeWorld)
                                .thenApply(ignored -> activeWorld),
                        terminal
                ))
                .thenCompose(activeWorld -> phase(
                        RemovalStatus.CONFIGURATION_FAILED,
                        () -> backend.unregister(activeWorld.target(), terminal::get),
                        terminal
                ).thenCompose(configuration -> {
                    if (configuration.failure() != null) {
                        return CompletableFuture.completedFuture(RemovalResult.partialFailure(
                                RemovalStatus.CONFIGURATION_FAILED,
                                activeWorld.target(),
                                null,
                                configuration.changed(),
                                false,
                                configuration.failure()
                        ));
                    }
                    return unregisterRegistryAndFinish(
                            activeWorld,
                            configuration.changed(),
                            deleteFiles,
                            terminal
                    );
                }));

        return operation.handle((result, throwable) -> backend.endMaintenance(resolved)
                .handle((ignored, cleanupFailure) -> {
                    if (cleanupFailure != null) {
                        Throwable unwrappedCleanup = unwrap(cleanupFailure);
                        if (throwable != null) {
                            Throwable original = unwrap(throwable);
                            original.addSuppressed(unwrappedCleanup);
                            throw new CompletionException(original);
                        }
                        throw new CompletionException(unwrappedCleanup);
                    }
                    if (throwable != null) {
                        throw new CompletionException(unwrap(throwable));
                    }
                    return result;
                })).thenCompose(result -> result);
    }

    private CompletableFuture<ResolvedWorld> validateResolvedWorld(ResolvedWorld resolvedWorld) {
        if (!resolvedWorld.managed()) {
            return CompletableFuture.failedFuture(new RemovalFailure(
                    RemovalStatus.NOT_FOUND,
                    new IllegalStateException("No Iris-managed world was found for "
                            + resolvedWorld.target().worldKey() + ".")
            ));
        }
        if (resolvedWorld.loadedWorld() != null && resolvedWorld.generator() == null) {
            return CompletableFuture.failedFuture(new RemovalFailure(
                    RemovalStatus.NOT_IRIS_WORLD,
                    new IllegalStateException("Loaded world " + resolvedWorld.target().worldKey()
                            + " is not using an Iris generator.")
            ));
        }
        if (resolvedWorld.conflictingConfiguration()) {
            return CompletableFuture.failedFuture(new RemovalFailure(
                    RemovalStatus.NOT_IRIS_WORLD,
                    new IllegalStateException("bukkit.yml assigns a non-Iris generator to "
                            + resolvedWorld.target().logicalName() + ".")
            ));
        }
        return CompletableFuture.completedFuture(resolvedWorld);
    }

    private CompletableFuture<RemovalResult> unregisterRegistryAndFinish(
            ResolvedWorld resolvedWorld,
            boolean configurationChanged,
            boolean deleteFiles,
            AtomicBoolean terminal
    ) {
        return phase(
                RemovalStatus.REGISTRY_FAILED,
                () -> backend.unregisterRegistry(resolvedWorld.target(), terminal::get),
                terminal
        ).handle((registryChanged, throwable) -> {
            if (throwable == null) {
                return finishRemoval(
                        resolvedWorld,
                        configurationChanged,
                        registryChanged,
                        deleteFiles,
                        terminal
                );
            }

            Throwable failure = unwrap(throwable);
            RemovalStatus status = RemovalStatus.INTERNAL_FAILURE;
            Throwable cause = failure;
            if (failure instanceof RemovalFailure removalFailure) {
                status = removalFailure.status();
                cause = removalFailure.getCause();
            }
            return CompletableFuture.completedFuture(RemovalResult.partialFailure(
                    status,
                    resolvedWorld.target(),
                    null,
                    configurationChanged,
                    false,
                    cause
            ));
        }).thenCompose(result -> result);
    }

    private ResolvedWorld requireUnloaded(ResolvedWorld resolvedWorld, Boolean unloaded) {
        if (!Boolean.TRUE.equals(unloaded)) {
            throw new RemovalFailure(
                    RemovalStatus.UNLOAD_FAILED,
                    new IllegalStateException("World lifecycle backend refused to unload "
                            + resolvedWorld.target().worldKey() + ".")
            );
        }
        return resolvedWorld;
    }

    private CompletableFuture<RemovalResult> finishRemoval(
            ResolvedWorld resolvedWorld,
            boolean configurationChanged,
            boolean registryChanged,
            boolean deleteFiles,
            AtomicBoolean terminal
    ) {
        WorldRemovalPathPolicy.Target target = resolvedWorld.target();
        if (!deleteFiles) {
            return CompletableFuture.completedFuture(RemovalResult.success(
                    RemovalStatus.UNREGISTERED,
                    target,
                    null,
                    configurationChanged,
                    registryChanged
            ));
        }

        return phase(
                RemovalStatus.QUARANTINE_FAILED,
                () -> backend.delete(target, terminal::get),
                terminal
        )
                .handle((disposition, throwable) -> {
                    if (throwable == null) {
                        return RemovalResult.success(
                                disposition.queued() ? RemovalStatus.DELETE_QUEUED : RemovalStatus.DELETED,
                                target,
                                disposition.quarantineDirectory(),
                                configurationChanged,
                                registryChanged
                        );
                    }

                    Throwable failure = unwrap(throwable);
                    if (failure instanceof RemovalFailure removalFailure) {
                        return RemovalResult.partialFailure(
                                removalFailure.status(),
                                target,
                                removalFailure.quarantineDirectory(),
                                configurationChanged,
                                registryChanged,
                                removalFailure.getCause()
                        );
                    }
                    return RemovalResult.partialFailure(
                            RemovalStatus.INTERNAL_FAILURE,
                            target,
                            null,
                            configurationChanged,
                            registryChanged,
                            failure
                    );
                });
    }

    private <T> CompletableFuture<T> phase(
            RemovalStatus failureStatus,
            Supplier<CompletableFuture<T>> operation,
            AtomicBoolean terminal
    ) {
        if (terminal.get()) {
            return CompletableFuture.failedFuture(wrapFailure(
                    failureStatus,
                    new TimeoutException("World removal already crossed its terminal timeout boundary.")
            ));
        }

        CompletableFuture<T> future;
        try {
            future = Objects.requireNonNull(operation.get(), "Removal phase returned no completion future.");
        } catch (Throwable failure) {
            return CompletableFuture.failedFuture(wrapFailure(failureStatus, failure));
        }
        CompletableFuture<T> guarded = new CompletableFuture<>();
        AtomicBoolean settled = new AtomicBoolean(false);
        future.whenComplete((value, throwable) -> {
            if (!settled.compareAndSet(false, true)) {
                return;
            }
            if (throwable == null) {
                guarded.complete(value);
            } else {
                guarded.completeExceptionally(unwrap(throwable));
            }
        });
        CompletableFuture.delayedExecutor(phaseTimeoutMillis, TimeUnit.MILLISECONDS).execute(() -> {
            if (!settled.compareAndSet(false, true)) {
                return;
            }

            terminal.set(true);
            String phaseName = failureStatus.name().toLowerCase();
            String restartReason = "World removal timed out during " + phaseName + ".";
            TimeoutException timeout = new TimeoutException("World removal phase "
                    + phaseName + " did not settle within " + phaseTimeoutMillis + " milliseconds.");
            try {
                restartRequester.accept(restartReason);
            } catch (Throwable restartFailure) {
                timeout.addSuppressed(restartFailure);
            }
            if (coordinator.active(LifecycleOperationCoordinator.Domain.SERVER_LIFECYCLE).isEmpty()) {
                coordinator.quiesceForRestart(() -> IrisLogging.error(
                        restartReason + " Automatic restart dispatch failed; restart the server manually."));
            }
            try {
                future.cancel(false);
            } catch (Throwable cancellationFailure) {
                timeout.addSuppressed(cancellationFailure);
            }
            guarded.completeExceptionally(timeout);
        });
        return guarded.handle((value, throwable) -> {
            if (throwable != null) {
                throw wrapFailure(failureStatus, throwable);
            }
            return value;
        });
    }

    private RemovalResult failureResult(WorldRemovalPathPolicy.Target target, Throwable throwable) {
        Throwable failure = unwrap(throwable);
        if (failure instanceof RemovalFailure removalFailure) {
            return RemovalResult.failure(
                    removalFailure.status(),
                    target.requestedIdentifier(),
                    target,
                    removalFailure.getCause()
            );
        }
        return RemovalResult.failure(
                RemovalStatus.INTERNAL_FAILURE,
                target.requestedIdentifier(),
                target,
                failure
        );
    }

    private RemovalFailure wrapFailure(RemovalStatus status, Throwable throwable) {
        Throwable failure = unwrap(throwable);
        if (failure instanceof RemovalFailure removalFailure) {
            return removalFailure;
        }
        if (failure instanceof WorldRemovalPathPolicy.Rejection rejection) {
            return new RemovalFailure(mapRejection(rejection.reason()), rejection);
        }
        return new RemovalFailure(status, failure);
    }

    private void completeAndRelease(
            CompletableFuture<RemovalResult> destination,
            LifecycleOperationCoordinator.Lease lease,
            String worldIdentifier,
            RemovalResult removalResult,
            Throwable throwable
    ) {
        RemovalResult completedResult = removalResult;
        if (throwable != null) {
            completedResult = RemovalResult.failure(
                    RemovalStatus.INTERNAL_FAILURE,
                    worldIdentifier,
                    null,
                    unwrap(throwable)
            );
        }
        try {
            lease.close();
        } catch (Throwable releaseFailure) {
            Throwable existingFailure = completedResult == null ? null : completedResult.failure();
            if (existingFailure != null) {
                releaseFailure.addSuppressed(existingFailure);
            }
            completedResult = RemovalResult.failure(
                    RemovalStatus.INTERNAL_FAILURE,
                    worldIdentifier,
                    completedResult == null ? null : completedResult.target(),
                    releaseFailure
            );
        }
        destination.complete(completedResult);
    }

    private static String normalizeOperationTarget(String worldIdentifier) {
        if (worldIdentifier == null || worldIdentifier.isBlank()) {
            return null;
        }
        return worldIdentifier.trim();
    }

    private static RemovalStatus mapRejection(WorldRemovalPathPolicy.RejectionReason reason) {
        return switch (reason) {
            case INVALID_IDENTIFIER -> RemovalStatus.INVALID_IDENTIFIER;
            case CONFIGURED_MAIN_WORLD, MINECRAFT_NAMESPACE -> RemovalStatus.PROTECTED_WORLD;
            case NOT_IRIS_NAMESPACE -> RemovalStatus.NOT_IRIS_WORLD;
            case OUTSIDE_STORAGE_ROOT, SYMBOLIC_LINK -> RemovalStatus.UNSAFE_PATH;
        };
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (!(current instanceof RemovalFailure)
                && (current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    record ServiceOptions(
            RemovalBackend backend,
            long phaseTimeoutMillis,
            Consumer<String> restartRequester
    ) {
        ServiceOptions {
            Objects.requireNonNull(backend, "backend");
            if (phaseTimeoutMillis < 1L) {
                throw new IllegalArgumentException("phaseTimeoutMillis must be positive");
            }
            Objects.requireNonNull(restartRequester, "restartRequester");
        }
    }

    interface RemovalBackend {
        WorldRemovalPathPolicy.Target resolveTarget(String identifier);

        CompletableFuture<ResolvedWorld> resolve(WorldRemovalPathPolicy.Target target);

        CompletableFuture<Void> evacuatePlayers(ResolvedWorld resolvedWorld);

        CompletableFuture<Void> beginMaintenance(ResolvedWorld resolvedWorld);

        CompletableFuture<Void> endMaintenance(ResolvedWorld resolvedWorld);

        CompletableFuture<Void> closeGenerator(ResolvedWorld resolvedWorld);

        CompletableFuture<Boolean> unloadWorld(ResolvedWorld resolvedWorld);

        CompletableFuture<ConfigurationDisposition> unregister(
                WorldRemovalPathPolicy.Target target,
                BooleanSupplier terminal
        );

        CompletableFuture<Boolean> unregisterRegistry(
                WorldRemovalPathPolicy.Target target,
                BooleanSupplier terminal
        );

        CompletableFuture<DeleteDisposition> delete(
                WorldRemovalPathPolicy.Target target,
                BooleanSupplier terminal
        );
    }

    record ResolvedWorld(
            WorldRemovalPathPolicy.Target target,
            World loadedWorld,
            PlatformChunkGenerator generator,
            boolean configurationManaged,
            boolean conflictingConfiguration,
            boolean registryManaged,
            boolean directoryPresent
    ) {
        ResolvedWorld {
            Objects.requireNonNull(target, "target");
        }

        boolean managed() {
            return loadedWorld != null || configurationManaged || registryManaged || directoryPresent;
        }
    }

    record DeleteDisposition(boolean queued, Path quarantineDirectory) {
    }

    record ConfigurationDisposition(boolean changed, Throwable failure) {
    }

    public enum RemovalStatus {
        UNREGISTERED,
        DELETED,
        DELETE_QUEUED,
        BUSY,
        INVALID_IDENTIFIER,
        PROTECTED_WORLD,
        NOT_IRIS_WORLD,
        UNSAFE_PATH,
        NOT_FOUND,
        RESOLUTION_FAILED,
        TELEPORT_FAILED,
        GENERATOR_CLOSE_FAILED,
        UNLOAD_FAILED,
        CONFIGURATION_FAILED,
        REGISTRY_FAILED,
        QUARANTINE_FAILED,
        DELETE_FAILED,
        INTERNAL_FAILURE
    }

    public record RemovalResult(
            RemovalStatus status,
            String requestedIdentifier,
            WorldRemovalPathPolicy.Target target,
            Path quarantineDirectory,
            boolean configurationChanged,
            boolean registryChanged,
            LifecycleOperationCoordinator.ActiveOperation blockingOperation,
            Throwable failure
    ) {
        public RemovalResult {
            Objects.requireNonNull(status, "status");
        }

        static RemovalResult success(
                RemovalStatus status,
                WorldRemovalPathPolicy.Target target,
                Path quarantineDirectory,
                boolean configurationChanged,
                boolean registryChanged
        ) {
            if (status != RemovalStatus.UNREGISTERED
                    && status != RemovalStatus.DELETED
                    && status != RemovalStatus.DELETE_QUEUED) {
                throw new IllegalArgumentException("Not a successful removal status: " + status);
            }
            return new RemovalResult(
                    status,
                    target.requestedIdentifier(),
                    target,
                    quarantineDirectory,
                    configurationChanged,
                    registryChanged,
                    null,
                    null
            );
        }

        static RemovalResult busy(
                String requestedIdentifier,
                LifecycleOperationCoordinator.ActiveOperation blockingOperation
        ) {
            return new RemovalResult(
                    RemovalStatus.BUSY,
                    requestedIdentifier,
                    null,
                    null,
                    false,
                    false,
                    Objects.requireNonNull(blockingOperation, "blockingOperation"),
                    null
            );
        }

        static RemovalResult failure(
                RemovalStatus status,
                String requestedIdentifier,
                WorldRemovalPathPolicy.Target target,
                Throwable failure
        ) {
            return new RemovalResult(
                    status,
                    requestedIdentifier,
                    target,
                    null,
                    false,
                    false,
                    null,
                    Objects.requireNonNull(failure, "failure")
            );
        }

        public boolean succeeded() {
            return status == RemovalStatus.UNREGISTERED
                    || status == RemovalStatus.DELETED
                    || status == RemovalStatus.DELETE_QUEUED;
        }

        public boolean deletionDeferred() {
            return status == RemovalStatus.DELETE_QUEUED;
        }

        public boolean busy() {
            return status == RemovalStatus.BUSY;
        }

        static RemovalResult partialFailure(
                RemovalStatus status,
                WorldRemovalPathPolicy.Target target,
                Path quarantineDirectory,
                boolean configurationChanged,
                boolean registryChanged,
                Throwable failure
        ) {
            return new RemovalResult(
                    status,
                    target.requestedIdentifier(),
                    target,
                    quarantineDirectory,
                    configurationChanged,
                    registryChanged,
                    null,
                    Objects.requireNonNull(failure, "failure")
            );
        }
    }

    static String bukkitConfigurationWorldName(WorldRemovalPathPolicy.Target target) {
        WorldRemovalPathPolicy.Target requiredTarget = Objects.requireNonNull(target, "target");
        Path levelName = requiredTarget.levelRoot().getFileName();
        if (levelName == null || levelName.toString().isBlank()) {
            throw new IllegalArgumentException("Level root must have a Bukkit startup name.");
        }
        return IrisWorldStorage.configuredWorldName(requiredTarget.worldKey(), levelName.toString());
    }

    static String bukkitGenerator(
            YamlConfiguration configuration,
            WorldRemovalPathPolicy.Target target
    ) {
        ConfigurationSection worlds = Objects.requireNonNull(configuration, "configuration")
                .getConfigurationSection("worlds");
        if (worlds == null) {
            return null;
        }
        ConfigurationSection world = worlds.getConfigurationSection(bukkitConfigurationWorldName(target));
        return world == null ? null : world.getString("generator");
    }

    private static final class RemovalFailure extends CompletionException {
        private final RemovalStatus status;
        private final Path quarantineDirectory;

        private RemovalFailure(RemovalStatus status, Throwable cause) {
            this(status, cause, null);
        }

        private RemovalFailure(RemovalStatus status, Throwable cause, Path quarantineDirectory) {
            super(Objects.requireNonNull(cause, "cause"));
            this.status = Objects.requireNonNull(status, "status");
            this.quarantineDirectory = quarantineDirectory;
        }

        private RemovalStatus status() {
            return status;
        }

        private Path quarantineDirectory() {
            return quarantineDirectory;
        }
    }

    private static final class BukkitRemovalBackend implements RemovalBackend {
        private final Executor ioExecutor;

        private BukkitRemovalBackend(Executor ioExecutor) {
            this.ioExecutor = Objects.requireNonNull(ioExecutor, "ioExecutor");
        }

        @Override
        public WorldRemovalPathPolicy.Target resolveTarget(String identifier) {
            String currentMainWorld = IrisWorldStorage.levelRoot().getName();
            String configuredMainWorld = IrisWorldStorage.configuredLevelName();
            return WorldRemovalPathPolicy.resolve(
                    identifier,
                    currentMainWorld,
                    List.of(currentMainWorld, configuredMainWorld),
                    IrisWorldStorage.levelRoot().toPath()
            );
        }

        @Override
        public CompletableFuture<ResolvedWorld> resolve(WorldRemovalPathPolicy.Target target) {
            CompletableFuture<BukkitInspection> runtimeInspection = onGlobal(() -> inspectRuntime(target));
            CompletableFuture<DiskInspection> diskInspection = CompletableFuture.supplyAsync(
                    () -> inspectDisk(target),
                    ioExecutor
            );
            return runtimeInspection.thenCombine(diskInspection, (runtime, disk) -> new ResolvedWorld(
                    target,
                    runtime.world(),
                    runtime.generator(),
                    disk.configurationManaged(),
                    disk.conflictingConfiguration(),
                    runtime.registryManaged(),
                    disk.directoryPresent()
            ));
        }

        @Override
        public CompletableFuture<Void> beginMaintenance(ResolvedWorld resolvedWorld) {
            World world = resolvedWorld.loadedWorld();
            if (world != null) {
                IrisToolbelt.beginWorldMaintenance(world, "world-remove", true);
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> endMaintenance(ResolvedWorld resolvedWorld) {
            World world = resolvedWorld.loadedWorld();
            if (world != null) {
                IrisToolbelt.endWorldMaintenance(world, "world-remove", true);
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> evacuatePlayers(ResolvedWorld resolvedWorld) {
            World world = resolvedWorld.loadedWorld();
            if (world == null) {
                return CompletableFuture.completedFuture(null);
            }

            return onGlobal(() -> createEvacuationPlan(world)).thenCompose(plan -> {
                if (plan.players().isEmpty()) {
                    return CompletableFuture.completedFuture(null);
                }
                if (plan.destination() == null) {
                    return CompletableFuture.failedFuture(new IllegalStateException(
                            "No destination world is available for " + plan.players().size() + " player(s)."
                    ));
                }

                List<CompletableFuture<Void>> teleports = new ArrayList<>(plan.players().size());
                for (Player player : plan.players()) {
                    teleports.add(teleport(player, world, plan.destination()));
                }
                return CompletableFuture.allOf(teleports.toArray(CompletableFuture[]::new));
            });
        }

        @Override
        public CompletableFuture<Void> closeGenerator(ResolvedWorld resolvedWorld) {
            PlatformChunkGenerator generator = resolvedWorld.generator();
            if (generator == null) {
                return CompletableFuture.completedFuture(null);
            }
            CompletableFuture<Void> closeFuture = generator.closeAsync();
            if (closeFuture == null) {
                return CompletableFuture.failedFuture(new IllegalStateException(
                        "Iris generator returned no close completion future."
                ));
            }
            return closeFuture;
        }

        @Override
        public CompletableFuture<Boolean> unloadWorld(ResolvedWorld resolvedWorld) {
            World world = resolvedWorld.loadedWorld();
            if (world == null) {
                return CompletableFuture.completedFuture(true);
            }
            return WorldLifecycleService.get().unloadAsync(world, true);
        }

        @Override
        public CompletableFuture<ConfigurationDisposition> unregister(
                WorldRemovalPathPolicy.Target target,
                BooleanSupplier terminal
        ) {
            return onGlobal(() -> {
                requireNotTerminal(terminal, "Multiverse unregistration");
                return IrisServices.get(MultiverseCoreLink.class).removeFromConfig(
                        bukkitConfigurationWorldName(target)
                );
            }).thenCompose(multiverseChanged -> {
                if (terminal.getAsBoolean()) {
                    return CompletableFuture.failedFuture(new IllegalStateException(
                            "World removal stopped before Bukkit configuration unregistration."));
                }
                return CompletableFuture.supplyAsync(() -> {
                    requireNotTerminal(terminal, "Bukkit configuration unregistration");
                    try {
                        boolean bukkitChanged = BukkitWorldConfiguration.remove(
                                ServerProperties.BUKKIT_YML,
                                bukkitConfigurationWorldName(target)
                        );
                        return new ConfigurationDisposition(multiverseChanged || bukkitChanged, null);
                    } catch (Throwable failure) {
                        return new ConfigurationDisposition(multiverseChanged, unwrap(failure));
                    }
                }, ioExecutor);
            });
        }

        @Override
        public CompletableFuture<Boolean> unregisterRegistry(
                WorldRemovalPathPolicy.Target target,
                BooleanSupplier terminal
        ) {
            return CompletableFuture.supplyAsync(
                    () -> {
                        requireNotTerminal(terminal, "Iris registry unregistration");
                        return IrisWorlds.get().remove(target.worldKey().toString());
                    },
                    ioExecutor
            );
        }

        @Override
        public CompletableFuture<DeleteDisposition> delete(
                WorldRemovalPathPolicy.Target target,
                BooleanSupplier terminal
        ) {
            Path quarantine = target.levelRoot()
                    .resolve("dimensions/iris/.iris-delete-" + UUID.randomUUID())
                    .toAbsolutePath()
                    .normalize();
            return CompletableFuture.supplyAsync(
                            () -> {
                                requireNotTerminal(terminal, "deletion intent");
                                return queueDeletionIntent(target, quarantine);
                            },
                            ioExecutor
                    )
                    .thenCompose(queuedQuarantine -> {
                        if (queuedQuarantine == null) {
                            return CompletableFuture.completedFuture(null);
                        }
                        if (terminal.getAsBoolean()) {
                            return CompletableFuture.failedFuture(new IllegalStateException(
                                    "World removal stopped after recording its deletion intent."));
                        }
                        return onGlobal(() -> {
                            requireNotTerminal(terminal, "world quarantine");
                            return quarantineWorld(target, queuedQuarantine);
                        });
                    })
                    .thenCompose(quarantined -> {
                        if (quarantined == null) {
                            return CompletableFuture.completedFuture(new DeleteDisposition(false, null));
                        }
                        if (terminal.getAsBoolean()) {
                            return CompletableFuture.failedFuture(new IllegalStateException(
                                    "World removal stopped after quarantining its directory."));
                        }
                        return CompletableFuture.supplyAsync(() -> {
                            requireNotTerminal(terminal, "quarantine deletion");
                            return deleteQuarantine(quarantined);
                        }, ioExecutor);
                    });
        }

        private static void requireNotTerminal(BooleanSupplier terminal, String stage) {
            if (terminal.getAsBoolean()) {
                throw new IllegalStateException("World removal stopped before " + stage + ".");
            }
        }

        private BukkitInspection inspectRuntime(WorldRemovalPathPolicy.Target target) {
            World world = WorldIdentity.resolve(target.worldKey()).orElse(null);
            PlatformChunkGenerator generator = null;
            if (world != null) {
                Path loadedDirectory = world.getWorldFolder().toPath().toAbsolutePath().normalize();
                WorldRemovalPathPolicy.validateStorageRoot(target.levelRoot(), target.worldKey(), loadedDirectory);
                generator = IrisToolbelt.access(world);
            }
            boolean registryManaged = IrisWorlds.get().getWorlds().containsKey(target.worldKey().toString());
            return new BukkitInspection(world, generator, registryManaged);
        }

        private DiskInspection inspectDisk(WorldRemovalPathPolicy.Target target) {
            WorldRemovalPathPolicy.validateStoragePath(
                    target.levelRoot(),
                    target.worldKey(),
                    target.worldDirectory()
            );
            WorldRemovalPathPolicy.validateStorageRoot(
                    target.levelRoot(),
                    target.worldKey(),
                    target.storageDirectory()
            );
            boolean directoryPresent = Files.isDirectory(target.storageDirectory(), LinkOption.NOFOLLOW_LINKS);
            YamlConfiguration configuration = YamlConfiguration.loadConfiguration(ServerProperties.BUKKIT_YML);
            String generator = bukkitGenerator(configuration, target);
            boolean configurationManaged = generator != null
                    && (generator.equalsIgnoreCase("Iris") || generator.regionMatches(true, 0, "Iris:", 0, 5));
            boolean conflictingConfiguration = generator != null && !configurationManaged;
            return new DiskInspection(configurationManaged, conflictingConfiguration, directoryPresent);
        }

        private EvacuationPlan createEvacuationPlan(World source) {
            List<Player> players = List.copyOf(source.getPlayers());
            Location destination = null;
            for (World candidate : Bukkit.getWorlds()) {
                if (!WorldIdentity.key(candidate).equals(WorldIdentity.key(source))) {
                    destination = candidate.getSpawnLocation().clone();
                    break;
                }
            }
            return new EvacuationPlan(players, destination);
        }

        private CompletableFuture<Void> teleport(Player player, World source, Location destination) {
            CompletableFuture<Void> result = new CompletableFuture<>();
            Runnable operation = () -> {
                try {
                    if (!WorldIdentity.key(player.getWorld()).equals(WorldIdentity.key(source))) {
                        result.complete(null);
                        return;
                    }
                    CompletableFuture<Boolean> teleportFuture = BukkitPlatform.teleportAsync(player, destination);
                    if (teleportFuture == null) {
                        result.completeExceptionally(new IllegalStateException(
                                "No teleport completion future was returned for " + player.getName() + "."
                        ));
                        return;
                    }
                    teleportFuture.whenComplete((success, throwable) -> {
                        if (throwable != null) {
                            result.completeExceptionally(throwable);
                        } else if (!Boolean.TRUE.equals(success)) {
                            result.completeExceptionally(new IllegalStateException(
                                    "Player evacuation was refused for " + player.getName() + "."
                            ));
                        } else {
                            result.complete(null);
                        }
                    });
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
            };
            boolean scheduled = J.runEntity(player, operation, 0, () -> result.completeExceptionally(
                    new IllegalStateException("Player retired before evacuation: " + player.getName() + ".")
            ));
            if (!scheduled) {
                result.completeExceptionally(new IllegalStateException(
                        "Failed to schedule evacuation for " + player.getName() + "."
                ));
            }
            return result;
        }

        private Path queueDeletionIntent(WorldRemovalPathPolicy.Target target, Path quarantine) {
            WorldRemovalPathPolicy.validateStoragePath(
                    target.levelRoot(),
                    target.worldKey(),
                    target.worldDirectory()
            );
            WorldRemovalPathPolicy.validateStorageRoot(
                    target.levelRoot(),
                    target.worldKey(),
                    target.storageDirectory()
            );
            Path storageDirectory = target.storageDirectory();
            if (!Files.exists(storageDirectory, LinkOption.NOFOLLOW_LINKS)) {
                return null;
            }
            if (!Files.isDirectory(storageDirectory, LinkOption.NOFOLLOW_LINKS)) {
                throw new RemovalFailure(
                        RemovalStatus.QUARANTINE_FAILED,
                        new IOException("Iris world target is not a directory: " + storageDirectory)
                );
            }

            requireSafeQuarantineParent(target, quarantine);

            WorldDeletionQueue deletionQueue = IrisServices.getOrNull(WorldDeletionQueue.class);
            if (deletionQueue == null) {
                throw new RemovalFailure(
                        RemovalStatus.QUARANTINE_FAILED,
                        new IOException("World deletion queue is unavailable; no files were moved.")
                );
            }
            try {
                deletionQueue.queueExactForStartupDeletion(List.of(quarantine.getFileName().toString()));
                return quarantine;
            } catch (IOException failure) {
                throw new RemovalFailure(RemovalStatus.QUARANTINE_FAILED, failure);
            }
        }

        private Path quarantineWorld(WorldRemovalPathPolicy.Target target, Path quarantine) {
            if (WorldIdentity.resolve(target.worldKey()).isPresent()) {
                throw new RemovalFailure(
                        RemovalStatus.QUARANTINE_FAILED,
                        new IllegalStateException("World became loaded again before quarantine: "
                                + target.worldKey() + ".")
                );
            }
            WorldRemovalPathPolicy.validateStoragePath(
                    target.levelRoot(),
                    target.worldKey(),
                    target.worldDirectory()
            );
            WorldRemovalPathPolicy.validateStorageRoot(
                    target.levelRoot(),
                    target.worldKey(),
                    target.storageDirectory()
            );
            Path storageDirectory = target.storageDirectory();
            if (!Files.exists(storageDirectory, LinkOption.NOFOLLOW_LINKS)) {
                return null;
            }
            if (!Files.isDirectory(storageDirectory, LinkOption.NOFOLLOW_LINKS)) {
                throw new RemovalFailure(
                        RemovalStatus.QUARANTINE_FAILED,
                        new IOException("Iris world target is not a directory: " + storageDirectory)
                );
            }
            try {
                try {
                    Files.move(storageDirectory, quarantine, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException unsupported) {
                    Files.move(storageDirectory, quarantine);
                }
                return quarantine;
            } catch (IOException failure) {
                throw new RemovalFailure(RemovalStatus.QUARANTINE_FAILED, failure);
            }
        }

        private static void requireSafeQuarantineParent(
                WorldRemovalPathPolicy.Target target,
                Path quarantine
        ) {
            Path expectedParent = target.levelRoot().resolve("dimensions/iris").toAbsolutePath().normalize();
            if (!Objects.equals(quarantine.getParent(), expectedParent)) {
                throw new RemovalFailure(
                        RemovalStatus.QUARANTINE_FAILED,
                        new IOException("Iris quarantine path is outside its exact namespace root: " + quarantine)
                );
            }
            try {
                Path current = target.levelRoot().toAbsolutePath().normalize();
                for (Path segment : target.levelRoot().relativize(expectedParent)) {
                    current = current.resolve(segment);
                    if (Files.isSymbolicLink(current)) {
                        throw new IOException("Iris quarantine storage contains a symbolic link: " + current);
                    }
                }
                Files.createDirectories(expectedParent);
            } catch (IOException failure) {
                throw new RemovalFailure(RemovalStatus.QUARANTINE_FAILED, failure);
            }
        }

        private DeleteDisposition deleteQuarantine(Path quarantine) {
            try {
                SnapshotDirectoryTreeDeleter.delete(quarantine);
                return new DeleteDisposition(false, quarantine);
            } catch (Throwable deletionFailure) {
                IrisLogging.reportError(
                        "Immediate Iris world deletion failed; startup cleanup remains queued for " + quarantine + ".",
                        deletionFailure
                );
                return new DeleteDisposition(true, quarantine);
            }
        }

        private static <T> CompletableFuture<T> onGlobal(Supplier<T> supplier) {
            CompletableFuture<T> result = new CompletableFuture<>();
            boolean scheduled = J.runGlobal(() -> {
                try {
                    result.complete(supplier.get());
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
            });
            if (!scheduled) {
                result.completeExceptionally(new IllegalStateException("Failed to schedule world removal on the global thread."));
            }
            return result;
        }

        private record BukkitInspection(
                World world,
                PlatformChunkGenerator generator,
                boolean registryManaged
        ) {
        }

        private record DiskInspection(
                boolean configurationManaged,
                boolean conflictingConfiguration,
                boolean directoryPresent
        ) {
        }

        private record EvacuationPlan(List<Player> players, Location destination) {
            private EvacuationPlan {
                Objects.requireNonNull(players, "players");
            }
        }
    }
}
