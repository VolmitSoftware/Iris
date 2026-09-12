/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.iris.world;

import art.arcane.iris.pack.datapack.ServerConfigurator;

import art.arcane.iris.Iris;
import art.arcane.iris.world.lifecycle.BukkitWorldConfiguration;
import art.arcane.iris.world.lifecycle.LifecycleOperationCoordinator;
import art.arcane.iris.platform.bukkit.nms.INMS;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.platform.bukkit.BukkitEnvironment;
import art.arcane.iris.localization.C;
import art.arcane.iris.platform.bootstrap.ServerProperties;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class BukkitWorldReconciler {
    private static final long WORLD_CREATE_TIMEOUT_SECONDS = 120L;

    private final Backend backend;
    private final LifecycleOperationCoordinator coordinator;

    public BukkitWorldReconciler(Iris plugin) {
        this(new BukkitBackend(plugin), LifecycleOperationCoordinator.get());
    }

    BukkitWorldReconciler(Backend backend, LifecycleOperationCoordinator coordinator) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    }

    public CompletableFuture<LoadResult> loadWorld(
            File configurationFile,
            String worldName
    ) {
        NamespacedKey worldKey;
        try {
            worldKey = IrisWorldStorage.managedKeyFromName(worldName);
        } catch (Throwable failure) {
            return CompletableFuture.completedFuture(LoadResult.validationFailure(worldName, failure));
        }

        LifecycleOperationCoordinator.Lease lease;
        try {
            lease = acquireWorldLoad(worldKey);
        } catch (LifecycleOperationCoordinator.BusyException failure) {
            return CompletableFuture.completedFuture(LoadResult.busy(worldKey, failure));
        }

        DimensionResolution dimensionResolution;
        Long configuredSeed;
        String configuredWorldName;
        try {
            configuredWorldName = backend.configuredWorldName(worldKey);
            dimensionResolution = backend.resolveDimension(worldKey);
            configuredSeed = dimensionResolution.succeeded()
                    ? backend.configuredSeed(configuredWorldName)
                    : null;
        } catch (Throwable failure) {
            dimensionResolution = DimensionResolution.failed(failure);
            configuredSeed = null;
            configuredWorldName = IrisWorldStorage.logicalName(worldKey);
        }
        if (!dimensionResolution.succeeded()) {
            lease.close();
            return CompletableFuture.completedFuture(LoadResult.dimensionFailure(
                    worldKey,
                    dimensionResolution.failure()));
        }
        return loadWithLease(
                configurationFile,
                configuredWorldName,
                worldKey,
                dimensionResolution.dimension(),
                configuredSeed,
                lease);
    }

    public CompletableFuture<BatchResult> checkForBukkitWorlds(Predicate<String> filter) {
        Predicate<String> requiredFilter = Objects.requireNonNull(filter, "filter");
        Map<String, String> configuredWorlds;
        try {
            configuredWorlds = backend.configuredWorlds();
        } catch (Throwable failure) {
            Iris.reportError("Failed while reading staged Bukkit worlds.", failure);
            return CompletableFuture.completedFuture(new BatchResult(List.of(), failure));
        }

        CompletableFuture<List<LoadResult>> chain = CompletableFuture.completedFuture(new ArrayList<>());
        for (Map.Entry<String, String> entry : configuredWorlds.entrySet()) {
            String worldName = entry.getKey();
            boolean selected;
            try {
                selected = requiredFilter.test(worldName);
            } catch (Throwable failure) {
                Iris.reportError("Failed while filtering staged Bukkit world \"" + worldName + "\".", failure);
                return CompletableFuture.completedFuture(new BatchResult(List.of(), failure));
            }
            if (!selected) {
                continue;
            }
            NamespacedKey worldKey;
            try {
                worldKey = backend.worldKeyFromConfiguration(worldName);
            } catch (Throwable failure) {
                chain = chain.thenApply(results -> {
                    results.add(LoadResult.validationFailure(worldName, failure));
                    return results;
                });
                continue;
            }
            String dimension = entry.getValue();
            Long seed;
            try {
                seed = backend.configuredSeed(worldName);
            } catch (Throwable failure) {
                chain = chain.thenApply(results -> {
                    results.add(LoadResult.configurationFailure(worldKey, failure));
                    return results;
                });
                continue;
            }
            chain = chain.thenCompose(results -> loadConfiguredWorld(
                            ServerProperties.BUKKIT_YML,
                            worldName,
                            worldKey,
                            dimension,
                            seed)
                    .thenApply(result -> {
                        results.add(result);
                        return results;
                    }));
        }

        return chain.thenApply(results -> {
            BatchResult batchResult = new BatchResult(results, null);
            reportBatch(batchResult);
            return batchResult;
        });
    }

    private CompletableFuture<LoadResult> loadConfiguredWorld(
            File configurationFile,
            String configuredWorldName,
            NamespacedKey worldKey,
            String dimension,
            Long seed
    ) {
        LifecycleOperationCoordinator.Lease lease;
        try {
            lease = acquireWorldLoad(worldKey);
        } catch (LifecycleOperationCoordinator.BusyException failure) {
            return CompletableFuture.completedFuture(LoadResult.busy(worldKey, failure));
        }

        return loadWithLease(configurationFile, configuredWorldName, worldKey, dimension, seed, lease);
    }

    private LifecycleOperationCoordinator.Lease acquireWorldLoad(NamespacedKey worldKey) {
        return coordinator.acquire(
                LifecycleOperationCoordinator.Domain.WORLD_MUTATION,
                LifecycleOperationCoordinator.OperationKind.WORLD_LOAD,
                worldKey.toString());
    }

    private CompletableFuture<LoadResult> loadWithLease(
            File configurationFile,
            String configuredWorldName,
            NamespacedKey worldKey,
            String dimension,
            Long seed,
            LifecycleOperationCoordinator.Lease lease
    ) {

        try {
            IrisStartupValidation.requireWorldCreationReady();
            backend.requireDimensionLoadable(worldKey, dimension);
        } catch (Throwable failure) {
            lease.close();
            return CompletableFuture.completedFuture(LoadResult.dimensionFailure(worldKey, failure));
        }

        BukkitWorldConfiguration.Registration registration;
        try {
            registration = BukkitWorldConfiguration.register(
                    configurationFile,
                    configuredWorldName,
                    dimension,
                    seed);
        } catch (Throwable failure) {
            lease.close();
            return CompletableFuture.completedFuture(LoadResult.configurationFailure(worldKey, failure));
        }

        CompletableFuture<ReconciliationResult> reconciliation;
        try {
            reconciliation = reconcile(worldKey, dimension, seed);
        } catch (Throwable failure) {
            reconciliation = CompletableFuture.completedFuture(ReconciliationResult.createFailure(worldKey, failure));
        }

        return reconciliation.handle((result, failure) -> {
                    ReconciliationResult settled = failure == null
                            ? result
                            : ReconciliationResult.createFailure(worldKey, unwrap(failure));
                    if (settled == null) {
                        settled = ReconciliationResult.createFailure(
                                worldKey,
                                new IllegalStateException("World reconciliation completed without a result."));
                    }
                    if (settled.succeeded()
                            || settled.status() == ReconciliationStatus.RESTART_REQUIRED
                            || registration != BukkitWorldConfiguration.Registration.CREATED) {
                        return new LoadResult(settled, registration, false, true, null);
                    }
                    try {
                        boolean rolledBack = BukkitWorldConfiguration.removeIfMatching(
                                configurationFile,
                                configuredWorldName,
                                dimension,
                                seed);
                        return new LoadResult(settled, registration, true, rolledBack, null);
                    } catch (Throwable rollbackFailure) {
                        return new LoadResult(settled, registration, true, false, rollbackFailure);
                    }
                })
                .whenComplete((result, failure) -> lease.close());
    }

    private CompletableFuture<ReconciliationResult> reconcile(
            NamespacedKey worldKey,
            String dimension,
            Long seed
    ) {
        Optional<World> loaded = backend.loadedWorld(worldKey);
        if (loaded.isPresent()) {
            return CompletableFuture.completedFuture(verifyLoadedWorld(worldKey, loaded.get(), true));
        }

        CompletableFuture<World> created;
        try {
            created = Objects.requireNonNull(
                    backend.createWorld(worldKey, dimension, seed),
                    "World backend returned no creation future.");
        } catch (Throwable failure) {
            return CompletableFuture.completedFuture(classifyCreationFailure(worldKey, failure));
        }

        CompletableFuture<World> guardedCreation = guardCreateCompletion(
                created,
                worldKey,
                TimeUnit.SECONDS.toMillis(WORLD_CREATE_TIMEOUT_SECONDS),
                () -> ServerConfigurator.restart("World load timed out for \"" + worldKey + "\"."));
        return guardedCreation.handle((createdWorld, failure) -> {
            if (failure != null) {
                return classifyCreationFailure(worldKey, unwrap(failure));
            }
            if (createdWorld == null) {
                return ReconciliationResult.notLoaded(worldKey);
            }

            NamespacedKey createdKey;
            try {
                createdKey = WorldIdentity.key(createdWorld);
            } catch (Throwable identityFailure) {
                return ReconciliationResult.createFailure(worldKey, identityFailure);
            }
            if (!worldKey.equals(createdKey)) {
                return ReconciliationResult.identityMismatch(worldKey, createdWorld, createdKey);
            }

            Optional<World> resolved = backend.loadedWorld(worldKey);
            if (resolved.isEmpty()) {
                return ReconciliationResult.notLoaded(worldKey);
            }
            return verifyLoadedWorld(worldKey, resolved.get(), false);
        });
    }

    private ReconciliationResult verifyLoadedWorld(NamespacedKey worldKey, World loadedWorld, boolean alreadyLoaded) {
        NamespacedKey loadedKey;
        try {
            loadedKey = WorldIdentity.key(loadedWorld);
        } catch (Throwable identityFailure) {
            return ReconciliationResult.createFailure(worldKey, identityFailure);
        }
        if (!worldKey.equals(loadedKey)) {
            return ReconciliationResult.identityMismatch(worldKey, loadedWorld, loadedKey);
        }
        if (!backend.isIrisWorld(loadedWorld)) {
            return ReconciliationResult.identityConflict(worldKey, loadedWorld);
        }
        return alreadyLoaded
                ? ReconciliationResult.alreadyLoaded(worldKey, loadedWorld)
                : ReconciliationResult.loaded(worldKey, loadedWorld);
    }

    private static ReconciliationResult classifyCreationFailure(NamespacedKey worldKey, Throwable failure) {
        Throwable cause = unwrap(failure);
        if (cause instanceof TimeoutException || containsCreateWorldUnsupportedOperation(cause)) {
            return ReconciliationResult.restartRequired(worldKey, cause);
        }
        return ReconciliationResult.createFailure(worldKey, cause);
    }

    static CompletableFuture<World> guardCreateCompletion(
            CompletableFuture<World> source,
            NamespacedKey worldKey,
            long timeoutMillis,
            Runnable timeoutAction
    ) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(worldKey, "worldKey");
        Objects.requireNonNull(timeoutAction, "timeoutAction");
        if (timeoutMillis < 1L) {
            throw new IllegalArgumentException("timeoutMillis must be positive");
        }

        CompletableFuture<World> guarded = new CompletableFuture<>();
        AtomicBoolean settled = new AtomicBoolean(false);
        source.whenComplete((world, throwable) -> {
            if (!settled.compareAndSet(false, true)) {
                return;
            }
            if (throwable == null) {
                guarded.complete(world);
            } else {
                guarded.completeExceptionally(unwrap(throwable));
            }
        });
        CompletableFuture.delayedExecutor(timeoutMillis, TimeUnit.MILLISECONDS).execute(() -> {
            if (!settled.compareAndSet(false, true)) {
                return;
            }
            TimeoutException timeout = new TimeoutException(
                    "World load did not settle within " + timeoutMillis + " milliseconds for \""
                            + worldKey + "\".");
            try {
                timeoutAction.run();
            } catch (Throwable failure) {
                timeout.addSuppressed(failure);
            }
            guarded.completeExceptionally(timeout);
        });
        return guarded;
    }

    private static void reportBatch(BatchResult batchResult) {
        for (LoadResult result : batchResult.results()) {
            if (result.succeeded()) {
                Iris.info(C.LIGHT_PURPLE + result.message());
                continue;
            }
            if (result.status() == ReconciliationStatus.BUSY) {
                Iris.warn(result.message());
                continue;
            }
            Iris.error(result.message());
            Throwable failure = result.failure();
            if (failure != null) {
                Iris.reportError("Failed to reconcile staged world \"" + result.worldKey() + "\".", failure);
            }
        }
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static boolean containsCreateWorldUnsupportedOperation(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            if (cursor instanceof UnsupportedOperationException || cursor instanceof IllegalStateException) {
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

    interface Backend {
        Map<String, String> configuredWorlds();

        Long configuredSeed(String worldName);

        String configuredWorldName(NamespacedKey worldKey);

        NamespacedKey worldKeyFromConfiguration(String configuredWorldName);

        Optional<World> loadedWorld(NamespacedKey worldKey);

        CompletableFuture<World> createWorld(NamespacedKey worldKey, String dimension, Long seed);

        boolean isIrisWorld(World world);

        DimensionResolution resolveDimension(NamespacedKey worldKey);

        void requireDimensionLoadable(NamespacedKey worldKey, String dimension);
    }

    public enum ReconciliationStatus {
        LOADED,
        ALREADY_LOADED,
        BUSY,
        INVALID_WORLD,
        DIMENSION_UNRESOLVED,
        CONFIGURATION_FAILED,
        CREATE_FAILED,
        RESTART_REQUIRED,
        IDENTITY_MISMATCH,
        IDENTITY_CONFLICT,
        NOT_LOADED
    }

    public record ReconciliationResult(
            ReconciliationStatus status,
            NamespacedKey worldKey,
            World world,
            Throwable failure,
            String message
    ) {
        public ReconciliationResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(message, "message");
        }

        public boolean succeeded() {
            return status == ReconciliationStatus.LOADED || status == ReconciliationStatus.ALREADY_LOADED;
        }

        private static ReconciliationResult loaded(NamespacedKey worldKey, World world) {
            return new ReconciliationResult(
                    ReconciliationStatus.LOADED,
                    worldKey,
                    world,
                    null,
                    "Loaded Iris world \"" + worldKey + "\".");
        }

        private static ReconciliationResult alreadyLoaded(NamespacedKey worldKey, World world) {
            return new ReconciliationResult(
                    ReconciliationStatus.ALREADY_LOADED,
                    worldKey,
                    world,
                    null,
                    "Iris world \"" + worldKey + "\" is already loaded.");
        }

        private static ReconciliationResult createFailure(NamespacedKey worldKey, Throwable failure) {
            return new ReconciliationResult(
                    ReconciliationStatus.CREATE_FAILED,
                    worldKey,
                    null,
                    failure,
                    "Failed to create Iris world \"" + worldKey + "\": " + failure.getMessage());
        }

        private static ReconciliationResult restartRequired(NamespacedKey worldKey, Throwable failure) {
            return new ReconciliationResult(
                    ReconciliationStatus.RESTART_REQUIRED,
                    worldKey,
                    null,
                    failure,
                    "The server cannot load exact Iris world \"" + worldKey + "\" at this runtime phase.");
        }

        private static ReconciliationResult identityMismatch(
                NamespacedKey worldKey,
                World world,
                NamespacedKey actualKey
        ) {
            return new ReconciliationResult(
                    ReconciliationStatus.IDENTITY_MISMATCH,
                    worldKey,
                    world,
                    null,
                    "World creation returned \"" + actualKey + "\" instead of \"" + worldKey + "\".");
        }

        private static ReconciliationResult identityConflict(NamespacedKey worldKey, World world) {
            return new ReconciliationResult(
                    ReconciliationStatus.IDENTITY_CONFLICT,
                    worldKey,
                    world,
                    null,
                    "World \"" + worldKey + "\" is loaded, but it is not an Iris world.");
        }

        private static ReconciliationResult notLoaded(NamespacedKey worldKey) {
            return new ReconciliationResult(
                    ReconciliationStatus.NOT_LOADED,
                    worldKey,
                    null,
                    null,
                    "World creation completed without loading exact Iris world \"" + worldKey + "\".");
        }
    }

    public record LoadResult(
            ReconciliationResult reconciliation,
            BukkitWorldConfiguration.Registration registration,
            boolean rollbackAttempted,
            boolean rollbackSucceeded,
            Throwable rollbackFailure
    ) {
        public LoadResult {
            Objects.requireNonNull(reconciliation, "reconciliation");
        }

        public boolean succeeded() {
            return reconciliation.succeeded() && rollbackFailure == null;
        }

        public ReconciliationStatus status() {
            return reconciliation.status();
        }

        public NamespacedKey worldKey() {
            return reconciliation.worldKey();
        }

        public World world() {
            return reconciliation.world();
        }

        public Throwable failure() {
            return rollbackFailure == null ? reconciliation.failure() : rollbackFailure;
        }

        public String message() {
            if (rollbackFailure != null) {
                return reconciliation.message() + " Failed to roll back bukkit.yml: " + rollbackFailure.getMessage();
            }
            if (rollbackAttempted && rollbackSucceeded) {
                return reconciliation.message() + " The new bukkit.yml entry was rolled back.";
            }
            if (rollbackAttempted && !rollbackSucceeded) {
                return reconciliation.message() + " The new bukkit.yml entry was no longer an exact match and was not modified.";
            }
            return reconciliation.message();
        }

        private static LoadResult validationFailure(String worldName, Throwable failure) {
            ReconciliationResult reconciliation = new ReconciliationResult(
                    ReconciliationStatus.INVALID_WORLD,
                    null,
                    null,
                    failure,
                    "Invalid Iris world identifier \"" + worldName + "\": " + failure.getMessage());
            return new LoadResult(reconciliation, null, false, true, null);
        }

        private static LoadResult busy(NamespacedKey worldKey, LifecycleOperationCoordinator.BusyException failure) {
            ReconciliationResult reconciliation = new ReconciliationResult(
                    ReconciliationStatus.BUSY,
                    worldKey,
                    null,
                    failure,
                    failure.getMessage());
            return new LoadResult(reconciliation, null, false, true, null);
        }

        private static LoadResult configurationFailure(NamespacedKey worldKey, Throwable failure) {
            ReconciliationResult reconciliation = new ReconciliationResult(
                    ReconciliationStatus.CONFIGURATION_FAILED,
                    worldKey,
                    null,
                    failure,
                    "Failed to register Iris world \"" + worldKey + "\" in bukkit.yml: " + failure.getMessage());
            return new LoadResult(reconciliation, null, false, true, null);
        }

        private static LoadResult dimensionFailure(NamespacedKey worldKey, Throwable failure) {
            ReconciliationResult reconciliation = new ReconciliationResult(
                    ReconciliationStatus.DIMENSION_UNRESOLVED,
                    worldKey,
                    null,
                    failure,
                    "Could not determine one Iris dimension for world \"" + worldKey + "\": " + failure.getMessage());
            return new LoadResult(reconciliation, null, false, true, null);
        }
    }

    record DimensionResolution(String dimension, Throwable failure) {
        DimensionResolution {
            if ((dimension == null) == (failure == null)) {
                throw new IllegalArgumentException("Dimension resolution must contain exactly one outcome.");
            }
        }

        static DimensionResolution resolved(String dimension) {
            return new DimensionResolution(Objects.requireNonNull(dimension, "dimension"), null);
        }

        static DimensionResolution failed(Throwable failure) {
            return new DimensionResolution(null, Objects.requireNonNull(failure, "failure"));
        }

        boolean succeeded() {
            return dimension != null;
        }
    }

    public record BatchResult(List<LoadResult> results, Throwable failure) {
        public BatchResult {
            results = List.copyOf(Objects.requireNonNull(results, "results"));
        }

        public boolean succeeded() {
            return failure == null && results.stream().allMatch(LoadResult::succeeded);
        }
    }

    private static final class BukkitBackend implements Backend {
        private final Iris plugin;

        private BukkitBackend(Iris plugin) {
            this.plugin = Objects.requireNonNull(plugin, "plugin");
        }

        @Override
        public Map<String, String> configuredWorlds() {
            return new LinkedHashMap<>(IrisWorlds.readBukkitWorlds());
        }

        @Override
        public Long configuredSeed(String worldName) {
            return IrisWorlds.readBukkitWorldSeed(worldName);
        }

        @Override
        public String configuredWorldName(NamespacedKey worldKey) {
            return IrisWorldStorage.configuredWorldName(worldKey, IrisWorldStorage.levelRoot().getName());
        }

        @Override
        public NamespacedKey worldKeyFromConfiguration(String configuredWorldName) {
            return IrisWorldStorage.keyFromConfiguredWorldName(
                    configuredWorldName,
                    IrisWorldStorage.levelRoot().getName()
            );
        }

        @Override
        public Optional<World> loadedWorld(NamespacedKey worldKey) {
            return WorldIdentity.resolve(worldKey);
        }

        @Override
        public CompletableFuture<World> createWorld(NamespacedKey worldKey, String dimension, Long seed) {
            try {
                String logicalWorldName = IrisWorldStorage.logicalName(worldKey);
                String configuredWorldName = configuredWorldName(worldKey);
                Iris.info("Loading World: %s | Generator: %s", logicalWorldName, dimension);
                ChunkGenerator generator = plugin.getDefaultWorldGenerator(configuredWorldName, dimension);
                IrisDimension irisDimension = IrisWorldGeneratorResolver.loadDimension(configuredWorldName, dimension);
                if (generator == null || irisDimension == null) {
                    throw new IllegalStateException("Could not resolve the Iris generator or dimension \"" + dimension + "\".");
                }

                Iris.info(C.LIGHT_PURPLE + "Preparing Spawn for " + logicalWorldName + " using Iris:" + dimension + "...");
                WorldCreator creator = WorldCreatorCompat.ofPersistentKey(worldKey)
                        .generator(generator)
                        .environment(BukkitEnvironment.from(irisDimension.getEnvironment()));
                if (seed != null) {
                    creator.seed(seed);
                }
                return INMS.get().createWorldAsync(creator);
            } catch (Throwable failure) {
                return CompletableFuture.failedFuture(failure);
            }
        }

        @Override
        public boolean isIrisWorld(World world) {
            return IrisToolbelt.isIrisWorld(world);
        }

        @Override
        public DimensionResolution resolveDimension(NamespacedKey worldKey) {
            File dimensionsDirectory = new File(snapshotRoot(worldKey), "dimensions");
            if (!dimensionsDirectory.isDirectory()) {
                return DimensionResolution.failed(new IllegalStateException("The world has no Iris dimensions directory."));
            }

            List<String> dimensions = new ArrayList<>();
            Path dimensionsRoot = dimensionsDirectory.toPath().toAbsolutePath().normalize();
            try (Stream<Path> paths = Files.walk(dimensionsRoot)) {
                paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .filter(path -> path.getFileName().toString().endsWith(".json"))
                        .forEach(path -> {
                            String relative = dimensionsRoot.relativize(path).toString().replace(File.separatorChar, '/');
                            dimensions.add(relative.substring(0, relative.length() - 5));
                        });
            } catch (IOException failure) {
                return DimensionResolution.failed(new IllegalStateException(
                        "The Iris dimensions directory could not be read.",
                        failure));
            }
            Collections.sort(dimensions);

            String registeredDimension = IrisWorlds.get().getWorlds().get(worldKey.toString());
            if (registeredDimension != null && dimensions.contains(registeredDimension)) {
                return DimensionResolution.resolved(registeredDimension);
            }
            if (dimensions.size() == 1) {
                return DimensionResolution.resolved(dimensions.getFirst());
            }
            if (dimensions.isEmpty()) {
                return DimensionResolution.failed(new IllegalStateException("No dimension definitions were found."));
            }
            return DimensionResolution.failed(new IllegalStateException(
                    "Multiple dimension definitions were found without an exact registered dimension: "
                            + String.join(", ", dimensions)));
        }

        @Override
        public void requireDimensionLoadable(NamespacedKey worldKey, String dimension) {
            String configuredWorldName = configuredWorldName(worldKey);
            IrisDimension irisDimension = IrisWorldGeneratorResolver.loadDimension(configuredWorldName, dimension);
            if (irisDimension == null) {
                throw new IllegalStateException("Could not resolve the Iris dimension \"" + dimension + "\".");
            }
            File snapshotRoot = snapshotRoot(worldKey);
            IrisWorldGeneratorResolver.requireSnapshotLoadable(snapshotRoot);
        }

        private File snapshotRoot(NamespacedKey worldKey) {
            File levelRoot = IrisWorldStorage.levelRoot();
            File dimensionRoot = IrisWorldStorage.requireFrozenDimensionRoot(
                    Bukkit.getWorldContainer(),
                    levelRoot,
                    configuredWorldName(worldKey),
                    worldKey
            );
            File expectedRoot = WorldCreatorCompat.persistentDimensionRoot(worldKey);
            if (!dimensionRoot.toPath().toAbsolutePath().normalize()
                    .equals(expectedRoot.toPath().toAbsolutePath().normalize())) {
                throw new IllegalStateException("Iris world storage does not match the current platform layout for "
                        + worldKey + ".");
            }
            return IrisWorldStorage.requireActiveGenerationPackRoot(dimensionRoot);
        }
    }
}
