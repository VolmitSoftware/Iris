package art.arcane.iris.world;

import art.arcane.iris.pack.datapack.DatapackInstallResult;
import art.arcane.iris.pack.datapack.ServerConfigurator;

import art.arcane.iris.Iris;
import art.arcane.iris.world.ExactWorldSlotPathPolicy.SlotKind;
import art.arcane.iris.world.lifecycle.BukkitWorldConfiguration;
import art.arcane.iris.world.lifecycle.BukkitWorldConfiguration.GeneratorReplacement;
import art.arcane.iris.world.lifecycle.BukkitWorldConfiguration.WorldGeneratorSnapshot;
import art.arcane.iris.world.lifecycle.LifecycleOperationCoordinator;
import art.arcane.iris.world.lifecycle.WorldReplacementBootstrap;
import art.arcane.iris.world.lifecycle.WorldReplacementBootstrapMarker;
import art.arcane.iris.world.lifecycle.WorldReplacementEntryGuard;
import art.arcane.iris.world.lifecycle.WorldReplacementFilesystem;
import art.arcane.iris.world.lifecycle.WorldReplacementFilesystem.ReplacementPaths;
import art.arcane.iris.world.lifecycle.WorldReplacementJournal;
import art.arcane.iris.world.lifecycle.WorldReplacementJournal.Phase;
import art.arcane.iris.world.lifecycle.WorldReplacementJournal.Transaction;
import art.arcane.iris.world.lifecycle.WorldReplacementSeed;
import art.arcane.iris.localization.RuntimeProgressMessages;
import art.arcane.iris.pack.PackValidationRegistry;
import art.arcane.iris.world.runtime.WorldRuntimeControlService;
import art.arcane.iris.studio.StudioSVC;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.platform.generation.BukkitChunkGenerator;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import art.arcane.iris.platform.bootstrap.ServerProperties;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldSaveEvent;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class PendingWorldReplacementManager implements Listener {
    private static final WorldSlotKey OVERWORLD_KEY = WorldSlotKey.minecraft("overworld");
    private static final long SAVED_LOCATION_INSPECTION_TIMEOUT_SECONDS = 30L;
    private static final long SAFE_ENTRY_TIMEOUT_SECONDS = 600L;

    private final Iris plugin;
    private final Set<UUID> cleanupInFlight = new HashSet<>();
    private final Set<UUID> verificationInFlight = new HashSet<>();
    private final ConcurrentHashMap<UUID, UUID> pendingEntryAcknowledgements = new ConcurrentHashMap<>();
    private volatile WorldReplacementEntryGuard.Entry overworldEntryGuard;
    private volatile Path overworldEntryWorldDirectory;
    private volatile CompletableFuture<Location> overworldSafeEntry;
    private volatile CompletableFuture<Void> overworldSpawnPersistence;
    private volatile UUID overworldEntryAwaitingVerification;
    private volatile World overworldEntryWorld;
    private volatile boolean paperEntryListenerRegistered;

    public PendingWorldReplacementManager(Iris plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public void registerPlatformEntryListener() {
        ClassLoader loader = getClass().getClassLoader();
        try {
            Class.forName("io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent", false, loader);
        } catch (ClassNotFoundException | LinkageError unavailable) {
            return;
        }
        try {
            Class<?> listenerType = Class.forName(
                    "art.arcane.iris.world.PaperWorldReplacementEntryListener",
                    true,
                    loader
            );
            Constructor<?> constructor = listenerType.getConstructor(PendingWorldReplacementManager.class);
            Listener listener = (Listener) constructor.newInstance(this);
            Bukkit.getPluginManager().registerEvents(listener, plugin);
            paperEntryListenerRegistered = true;
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            throw new IllegalStateException("Paper replacement entry listener registration failed.", cause);
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw new IllegalStateException("Paper replacement entry listener is unavailable.", failure);
        }
    }

    public NamespacedKey resolveRequestedWorldKey(String requestedName) {
        NamespacedKey worldKey = IrisWorldStorage.replacementKeyFromName(
                requestedName,
                IrisWorldStorage.levelRoot().getName()
        );
        ExactWorldSlotPathPolicy.resolve(IrisWorldStorage.levelRoot().toPath(), toWorldSlotKey(worldKey));
        return worldKey;
    }

    public StagedReplacement stageReplacement(
            VolmitSender sender,
            NamespacedKey worldKey,
            IrisDimension dimension,
            Long requestedSeed
    ) throws IOException {
        VolmitSender requiredSender = Objects.requireNonNull(sender, "sender");
        NamespacedKey requiredWorldKey = Objects.requireNonNull(worldKey, "worldKey");
        WorldSlotKey requiredWorldSlotKey = toWorldSlotKey(requiredWorldKey);
        IrisDimension requiredDimension = Objects.requireNonNull(dimension, "dimension");
        OptionalLong seedSelection = requestedSeed == null
                ? OptionalLong.empty()
                : OptionalLong.of(requestedSeed.longValue());
        LifecycleOperationCoordinator coordinator = LifecycleOperationCoordinator.get();
        try (LifecycleOperationCoordinator.Lease ignored = coordinator.acquire(
                LifecycleOperationCoordinator.Domain.WORLD_MUTATION,
                LifecycleOperationCoordinator.OperationKind.WORLD_REPLACE,
                requiredWorldKey.toString()
        ); WorldReplacementProgress progress = WorldReplacementProgress.start(
                requiredSender, requiredWorldKey.toString())) {
            synchronized (this) {
                IrisStartupValidation.requireWorldReplacementStagingReady();
                if (!WorldReplacementBootstrapMarker.wasBootstrappedThisProcess()) {
                    throw new IOException("Exact world replacement requires a full Paper-family startup bootstrap.");
                }
                PackValidationRegistry.requireLoadable(requiredDimension.getLoader().getDataFolder().getName());
                if (findTransaction(requiredWorldSlotKey) != null) {
                    throw new IOException("A replacement is already pending for " + requiredWorldKey + ".");
                }
                ExactWorldSlotPathPolicy.Target target = resolveTarget(requiredWorldSlotKey);
                requireCompatibleEnvironment(target.slotKind(), requiredDimension.getEnvironment());
                requireVanillaSlotEnabled(target.slotKind());
                UUID transactionId = UUID.randomUUID();
                ReplacementPaths paths = WorldReplacementFilesystem.paths(target, transactionId);
                WorldReplacementFilesystem.requireExistingTarget(paths);
                String worldName = WorldReplacementJournal.logicalWorldName(target.levelRoot(), requiredWorldSlotKey);
                progress.stage(RuntimeProgressMessages.WORLD_REPLACE_STAGE_DATAPACKS);
                DatapackInstallResult datapacks = ServerConfigurator.installDataPacksIfChanged(true);
                if (!datapacks.succeeded()) {
                    throw new IOException("Iris could not compile the dimension datapacks.");
                }

                boolean targetPresent = true;
                WorldGeneratorSnapshot originalConfiguration = BukkitWorldConfiguration.snapshot(
                        ServerProperties.BUKKIT_YML,
                        worldName
                );
                Transaction transaction = null;
                boolean journalWritten = false;
                boolean configurationApplied = false;
                try {
                    Files.createDirectory(paths.stage());
                    progress.stage(RuntimeProgressMessages.WORLD_REPLACE_STAGE_SEED);
                    long effectiveSeed = WorldReplacementSeed.stageAuthoritativeSeed(
                            paths.target(),
                            paths.stage(),
                            seedSelection
                    );
                    progress.stage(RuntimeProgressMessages.WORLD_REPLACE_STAGE_PACK);
                    IrisDimension installed = Iris.service(StudioSVC.class).installIntoWorld(
                            requiredSender,
                            requiredDimension,
                            paths.stage().toFile(),
                            effectiveSeed
                    );
                    if (installed == null) {
                        throw new IOException("Iris could not stage the dimension pack.");
                    }
                    requireCompatibleEnvironment(target.slotKind(), installed.getEnvironment());
                    if (target.slotKind() == SlotKind.VANILLA_OVERWORLD) {
                        progress.stage(RuntimeProgressMessages.WORLD_REPLACE_STAGE_PLAYERS);
                        WorldReplacementEntryGuard.stage(target.levelRoot(), paths.stage(), transactionId);
                    }
                    progress.stage(RuntimeProgressMessages.WORLD_REPLACE_STAGE_VERIFY);
                    File stagedPack = IrisWorldStorage.requireActiveGenerationPackRoot(
                            paths.stage().toFile(),
                            effectiveSeed
                    );
                    IrisWorldGeneratorResolver.requireSnapshotLoadable(stagedPack);
                    String packFingerprint = WorldReplacementFilesystem.fingerprintPack(stagedPack.toPath());
                    transaction = new Transaction(
                            transactionId,
                            requiredWorldSlotKey,
                            worldName,
                            target.levelRoot(),
                            installed.getLoadKey(),
                            effectiveSeed,
                            packFingerprint,
                            originalConfiguration,
                            targetPresent,
                            Phase.PREPARED
                    );
                    progress.stage(RuntimeProgressMessages.WORLD_REPLACE_STAGE_SAVE);
                    writeTransaction(transaction);
                    journalWritten = true;
                    GeneratorReplacement replacement = BukkitWorldConfiguration.replaceIfMatching(
                            ServerProperties.BUKKIT_YML,
                            worldName,
                            originalConfiguration,
                            installed.getLoadKey(),
                            effectiveSeed
                    );
                    if (!replacement.applied()) {
                        throw new IOException("bukkit.yml changed while the replacement was being staged.");
                    }
                    configurationApplied = true;
                    transaction = transaction.withPhase(Phase.ARMED);
                    writeTransaction(transaction);
                    return new StagedReplacement(
                            requiredWorldKey,
                            worldName,
                            installed.getLoadKey(),
                            effectiveSeed,
                            targetPresent,
                            datapacks.restartRequired()
                    );
                } catch (Throwable failure) {
                    progress.stage(RuntimeProgressMessages.WORLD_REPLACE_STAGE_CLEANUP);
                    if (configurationApplied && transaction != null) {
                        try {
                            WorldGeneratorSnapshot replacement = WorldReplacementBootstrap.replacementSnapshot(transaction);
                            if (!BukkitWorldConfiguration.restoreIfMatching(
                                    ServerProperties.BUKKIT_YML,
                                    worldName,
                                    replacement,
                                    originalConfiguration
                            )) {
                                failure.addSuppressed(new IOException(
                                        "bukkit.yml changed before the failed replacement could be restored."));
                            }
                        } catch (Throwable restoreFailure) {
                            failure.addSuppressed(restoreFailure);
                        }
                    }
                    if (!configurationApplied || configurationMatches(originalConfiguration, worldName)) {
                        try {
                            WorldReplacementFilesystem.discardStage(paths);
                            if (journalWritten) {
                                deleteJournal(transactionId);
                            }
                        } catch (Throwable cleanupFailure) {
                            failure.addSuppressed(cleanupFailure);
                        }
                    }
                    if (failure instanceof IOException ioFailure) {
                        throw ioFailure;
                    }
                    throw new IOException("Failed to stage replacement for " + requiredWorldKey + ".", failure);
                }
            }
        }
    }

    public synchronized void processPendingStartupReplacements() {
        ArrayList<String> failures = new ArrayList<>();
        ArrayList<String> restartBoundaries = new ArrayList<>();
        try {
            loadOverworldEntryGuard();
        } catch (Throwable failure) {
            String message = "Iris could not load the Overworld replacement entry guard: " + detail(failure);
            Iris.reportError(message, failure);
            IrisStartupValidation.markPacksInvalid(List.of(message));
            return;
        }
        if (overworldEntryGuard != null && !paperEntryListenerRegistered) {
            String message = "A pending Overworld replacement requires the Paper safe-entry capability.";
            Iris.error(message);
            IrisStartupValidation.markPacksInvalid(List.of(message));
            return;
        }
        List<Transaction> transactions;
        try {
            transactions = loadTransactions();
        } catch (Throwable failure) {
            Iris.reportError("Failed to read pending Iris world replacements.", failure);
            IrisStartupValidation.markPacksInvalid(List.of(
                    "Pending Iris world replacement journal validation failed: " + detail(failure)));
            return;
        }
        for (Transaction transaction : transactions) {
            if (OVERWORLD_KEY.equals(transaction.worldKey()) && transaction.phase() == Phase.PUBLISHED) {
                overworldEntryAwaitingVerification = transaction.id();
            }
            try {
                inspectStartupTransaction(transaction);
            } catch (RestartBoundaryRequired boundary) {
                String message = "Pending replacement for " + transaction.worldKey()
                        + " still requires a complete server restart: " + detail(boundary);
                restartBoundaries.add(message);
                Iris.warn(message);
            } catch (Throwable failure) {
                String message = "Pending replacement for " + transaction.worldKey()
                        + " failed safely: " + detail(failure);
                failures.add(message);
                Iris.reportError(message, failure);
            }
        }
        scheduleLoadedOverworldEntryPreparation();
        if (!failures.isEmpty()) {
            IrisStartupValidation.markPacksInvalid(failures);
        }
        if (!restartBoundaries.isEmpty()) {
            IrisStartupValidation.requireRestart(restartBoundaries.getFirst());
        }
    }

    public void verifyLoadedPublishedWorlds() {
        J.a(this::discoverLoadedPublishedWorlds);
    }

    private void discoverLoadedPublishedWorlds() {
        List<Transaction> transactions;
        try {
            synchronized (this) {
                transactions = loadTransactions();
            }
        } catch (Throwable failure) {
            Iris.reportError("Failed to inspect published Iris world replacements.", failure);
            return;
        }
        for (Transaction transaction : transactions) {
            if (transaction.phase() == Phase.CLEANUP_PENDING) {
                scheduleCommittedCleanup(transaction);
            } else if (transaction.phase() == Phase.PUBLISHED) {
                scheduleRuntimeCapture(transaction, 0);
            }
        }
        scheduleLoadedOverworldEntryPreparation();
    }

    private void scheduleLoadedOverworldEntryPreparation() {
        if (overworldEntryGuard == null) {
            return;
        }
        try {
            J.s(this::prepareLoadedOverworldEntry);
        } catch (Throwable failure) {
            failOverworldEntryPreparation(failure);
        }
    }

    private void prepareLoadedOverworldEntry() {
        World world;
        try {
            world = WorldIdentity.resolve(toNamespacedKey(OVERWORLD_KEY)).orElse(null);
        } catch (Throwable failure) {
            failOverworldEntryPreparation(failure);
            return;
        }
        if (world != null) {
            prepareOverworldEntry(world);
        }
    }

    private void prepareOverworldEntry(World world) {
        WorldReplacementEntryGuard.Entry guard = overworldEntryGuard;
        if (guard == null
                || guard.transactionId().equals(overworldEntryAwaitingVerification)
                || !OVERWORLD_KEY.equals(toWorldSlotKey(WorldIdentity.key(world)))) {
            return;
        }
        CompletableFuture<Location> targetFuture;
        synchronized (this) {
            if (overworldSafeEntry != null) {
                return;
            }
            targetFuture = new CompletableFuture<>();
            overworldSafeEntry = targetFuture;
            overworldEntryWorld = world;
        }
        try {
            PlatformChunkGenerator generator = IrisToolbelt.access(world);
            if (!(generator instanceof BukkitChunkGenerator bukkitGenerator)) {
                throw new IOException("The replaced Overworld does not have a Bukkit Iris generator.");
            }
            Location anchor = bukkitGenerator.getInitialSpawnLocation(world);
            int chunkX = anchor.getBlockX() >> 4;
            int chunkZ = anchor.getBlockZ() >> 4;
            WorldRuntimeControlService runtime = WorldRuntimeControlService.get();
            CompletableFuture<Chunk> chunkFuture = runtime.requestChunkAsync(world, chunkX, chunkZ, true, true);
            if (chunkFuture == null) {
                throw new IOException("The replacement spawn chunk request was not accepted.");
            }
            chunkFuture
                    .thenCompose(chunk -> runtime.resolveSafeEntry(world, anchor))
                    .thenCompose(this::applyOverworldSpawn)
                    .whenComplete((safeEntry, failure) -> {
                        if (failure != null) {
                            targetFuture.completeExceptionally(failure);
                            Iris.reportError("Could not prepare a safe spawn for the replaced Overworld.", failure);
                            return;
                        }
                        CompletableFuture<Void> persistence = new CompletableFuture<>();
                        overworldSpawnPersistence = persistence;
                        persistence.thenRun(() -> retireOverworldEntryIfComplete(guard.transactionId()));
                        targetFuture.complete(safeEntry.clone());
                    });
        } catch (Throwable failure) {
            targetFuture.completeExceptionally(failure);
            Iris.reportError("Could not prepare a safe spawn for the replaced Overworld.", failure);
        }
    }

    private CompletableFuture<Location> applyOverworldSpawn(Location safeEntry) {
        Location requiredSafeEntry = Objects.requireNonNull(safeEntry, "safeEntry").clone();
        World world = Objects.requireNonNull(requiredSafeEntry.getWorld(), "safeEntry.world");
        CompletableFuture<Location> applied = new CompletableFuture<>();
        int chunkX = requiredSafeEntry.getBlockX() >> 4;
        int chunkZ = requiredSafeEntry.getBlockZ() >> 4;
        boolean scheduled = J.runRegion(world, chunkX, chunkZ, () -> {
            try {
                if (!world.setSpawnLocation(requiredSafeEntry)) {
                    throw new IOException("The server rejected the replacement spawn location.");
                }
                applied.complete(requiredSafeEntry.clone());
            } catch (Throwable failure) {
                applied.completeExceptionally(failure);
            }
        });
        if (!scheduled) {
            applied.completeExceptionally(new IOException("Could not schedule the replacement spawn update."));
        }
        return applied;
    }

    private CompletableFuture<Boolean> inspectLoginCollision(Location location) {
        Location requiredLocation = Objects.requireNonNull(location, "location").clone();
        World world = Objects.requireNonNull(requiredLocation.getWorld(), "location.world");
        int chunkX = requiredLocation.getBlockX() >> 4;
        int chunkZ = requiredLocation.getBlockZ() >> 4;
        WorldRuntimeControlService runtime = WorldRuntimeControlService.get();
        CompletableFuture<Chunk> chunkFuture = runtime.requestChunkAsync(world, chunkX, chunkZ, false, true);
        if (chunkFuture == null) {
            return CompletableFuture.failedFuture(new IOException("The saved login chunk request was not accepted."));
        }
        return chunkFuture.thenCompose(chunk -> chunk == null
                ? CompletableFuture.completedFuture(false)
                : inspectLoadedLoginCollision(requiredLocation));
    }

    private CompletableFuture<Boolean> inspectLoadedLoginCollision(Location location) {
        World world = Objects.requireNonNull(location.getWorld(), "location.world");
        CompletableFuture<Boolean> inspected = new CompletableFuture<>();
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        boolean scheduled = J.runRegion(world, chunkX, chunkZ, () -> {
            try {
                int blockY = location.getBlockY();
                if (blockY < world.getMinHeight() || blockY + 1 >= world.getMaxHeight()) {
                    inspected.complete(false);
                    return;
                }
                boolean feetPassable = world.getBlockAt(location.getBlockX(), blockY, location.getBlockZ())
                        .isPassable();
                boolean headPassable = world.getBlockAt(location.getBlockX(), blockY + 1, location.getBlockZ())
                        .isPassable();
                inspected.complete(feetPassable && headPassable);
            } catch (Throwable failure) {
                inspected.completeExceptionally(failure);
            }
        });
        if (!scheduled) {
            inspected.completeExceptionally(new IOException("Could not schedule the saved login collision check."));
        }
        return inspected;
    }

    private void completeOverworldEntry(UUID playerId, UUID transactionId) {
        try {
            J.a(() -> completeOverworldEntryAsync(playerId, transactionId));
        } catch (Throwable failure) {
            Iris.reportError("Could not record a completed Overworld replacement entry for " + playerId + ".", failure);
        }
    }

    private void completeOverworldEntryAsync(UUID playerId, UUID transactionId) {
        boolean retire = false;
        try {
            synchronized (this) {
                WorldReplacementEntryGuard.Entry current = overworldEntryGuard;
                Path worldDirectory = overworldEntryWorldDirectory;
                if (current == null
                        || worldDirectory == null
                        || !current.transactionId().equals(transactionId)
                        || !current.pendingPlayers().contains(playerId)) {
                    return;
                }
                Optional<WorldReplacementEntryGuard.Entry> updated = WorldReplacementEntryGuard.completePlayer(
                        worldDirectory,
                        transactionId,
                        playerId
                );
                overworldEntryGuard = updated.orElse(null);
                retire = overworldEntryGuard != null && overworldEntryGuard.pendingPlayers().isEmpty();
            }
        } catch (Throwable failure) {
            Iris.reportError("Could not record a completed Overworld replacement entry for " + playerId + ".", failure);
            return;
        }
        if (retire) {
            retireOverworldEntryIfCompleteAsync(transactionId);
        }
    }

    private void retireOverworldEntryIfComplete(UUID transactionId) {
        try {
            J.a(() -> retireOverworldEntryIfCompleteAsync(transactionId));
        } catch (Throwable failure) {
            Iris.reportError("Could not schedule Overworld replacement entry marker retirement.", failure);
        }
    }

    private synchronized void retireOverworldEntryIfCompleteAsync(UUID transactionId) {
        WorldReplacementEntryGuard.Entry current = overworldEntryGuard;
        Path worldDirectory = overworldEntryWorldDirectory;
        CompletableFuture<Void> persistence = overworldSpawnPersistence;
        if (current == null
                || worldDirectory == null
                || !current.transactionId().equals(transactionId)
                || !current.pendingPlayers().isEmpty()
                || persistence == null
                || !persistence.isDone()
                || persistence.isCompletedExceptionally()) {
            return;
        }
        try {
            if (!WorldReplacementEntryGuard.retireIfEmpty(worldDirectory, transactionId)) {
                return;
            }
            overworldEntryGuard = null;
            overworldEntryWorldDirectory = null;
            overworldSafeEntry = null;
            overworldSpawnPersistence = null;
            overworldEntryWorld = null;
            pendingEntryAcknowledgements.entrySet().removeIf(entry -> entry.getValue().equals(transactionId));
        } catch (Throwable failure) {
            Iris.reportError("Could not retire the completed Overworld replacement entry marker.", failure);
        }
    }

    void reportUnsafeEntry(UUID playerId, Throwable failure) {
        Iris.reportError("Refused unsafe Overworld replacement entry for " + playerId + ".", failure);
    }

    private synchronized void failOverworldEntryPreparation(Throwable failure) {
        CompletableFuture<Location> future = overworldSafeEntry;
        if (future == null) {
            future = new CompletableFuture<>();
            overworldSafeEntry = future;
        }
        future.completeExceptionally(failure);
        Iris.reportError("Could not prepare a safe spawn for the replaced Overworld.", failure);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        WorldSlotKey worldKey;
        try {
            worldKey = toWorldSlotKey(WorldIdentity.key(event.getWorld()));
        } catch (Throwable failure) {
            Iris.reportError("Failed to capture a loaded world identity for replacement verification.", failure);
            return;
        }
        if (OVERWORLD_KEY.equals(worldKey)) {
            prepareOverworldEntry(event.getWorld());
        }
        J.a(() -> discoverLoadedWorldTransaction(worldKey));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldSave(WorldSaveEvent event) {
        World replacementWorld = overworldEntryWorld;
        CompletableFuture<Location> safeEntry = overworldSafeEntry;
        CompletableFuture<Void> persistence = overworldSpawnPersistence;
        if (event.getWorld() != replacementWorld
                || safeEntry == null
                || !safeEntry.isDone()
                || safeEntry.isCompletedExceptionally()
                || persistence == null
                || persistence.isDone()) {
            return;
        }
        persistence.complete(null);
    }

    ReplacementEntryRedirect prepareReplacementEntry(UUID playerId, Location savedLocation, boolean newPlayer)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        WorldReplacementEntryGuard.Entry guard = overworldEntryGuard;
        if (guard == null || playerId == null) {
            return null;
        }
        boolean pendingPlayer = guard.pendingPlayers().contains(playerId);
        if (!pendingPlayer && !newPlayer) {
            return null;
        }
        Location requiredSavedLocation = Objects.requireNonNull(savedLocation, "savedLocation").clone();
        World replacementWorld = overworldEntryWorld;
        if (replacementWorld == null) {
            throw new IOException("The replacement Overworld is not ready.");
        }
        if (requiredSavedLocation.getWorld() != replacementWorld) {
            if (pendingPlayer) {
                completeOverworldEntry(playerId, guard.transactionId());
            }
            return null;
        }
        if (!newPlayer) {
            SavedLocationInspection inspection = awaitSavedLocationInspection(
                    inspectLoginCollision(requiredSavedLocation),
                    SAVED_LOCATION_INSPECTION_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );
            if (inspection.safe()) {
                completeOverworldEntry(playerId, guard.transactionId());
                return null;
            }
            if (inspection.failure() instanceof TimeoutException) {
                Iris.warn("Saved Overworld login location inspection timed out for " + playerId
                        + "; using the prepared replacement spawn.");
            } else if (inspection.failure() != null) {
                Iris.reportError("Could not inspect the saved Overworld login location for " + playerId
                        + "; using the prepared replacement spawn.", inspection.failure());
            }
        }
        CompletableFuture<Location> safeEntry = overworldSafeEntry;
        if (safeEntry == null) {
            throw new IOException("The replacement safe spawn is not ready.");
        }
        Location prepared = safeEntry.get(SAFE_ENTRY_TIMEOUT_SECONDS, TimeUnit.SECONDS).clone();
        prepared.setYaw(requiredSavedLocation.getYaw());
        prepared.setPitch(requiredSavedLocation.getPitch());
        return new ReplacementEntryRedirect(guard.transactionId(), prepared, pendingPlayer);
    }

    static SavedLocationInspection awaitSavedLocationInspection(
            CompletableFuture<Boolean> inspection,
            long timeout,
            TimeUnit timeUnit
    ) throws InterruptedException {
        CompletableFuture<Boolean> requiredInspection = Objects.requireNonNull(inspection, "inspection");
        TimeUnit requiredTimeUnit = Objects.requireNonNull(timeUnit, "timeUnit");
        try {
            return new SavedLocationInspection(
                    Boolean.TRUE.equals(requiredInspection.get(timeout, requiredTimeUnit)),
                    null
            );
        } catch (TimeoutException failure) {
            return new SavedLocationInspection(false, failure);
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            return new SavedLocationInspection(false, cause);
        }
    }

    void expectReplacementEntryAcknowledgement(UUID playerId, UUID transactionId) throws IOException {
        WorldReplacementEntryGuard.Entry guard = overworldEntryGuard;
        if (guard == null
                || !guard.transactionId().equals(transactionId)
                || !guard.pendingPlayers().contains(playerId)) {
            throw new IOException("The Overworld replacement entry receipt is no longer active.");
        }
        pendingEntryAcknowledgements.put(playerId, transactionId);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        UUID transactionId = pendingEntryAcknowledgements.remove(playerId);
        if (transactionId == null) {
            return;
        }
        boolean scheduled = J.runEntity(event.getPlayer(), () -> {
            try {
                event.getPlayer().saveData();
                completeOverworldEntry(playerId, transactionId);
            } catch (Throwable failure) {
                Iris.reportError("Could not persist safe Overworld replacement entry for " + playerId + ".", failure);
            }
        });
        if (!scheduled) {
            Iris.error("Could not schedule safe Overworld replacement entry persistence for " + playerId + ".");
        }
    }

    private void discoverLoadedWorldTransaction(WorldSlotKey worldKey) {
        Transaction transaction;
        try {
            synchronized (this) {
                transaction = findTransaction(worldKey);
            }
        } catch (Throwable failure) {
            Iris.reportError("Failed to inspect a loaded Iris world replacement.", failure);
            return;
        }
        if (transaction == null) {
            return;
        }
        if (transaction.phase() == Phase.PUBLISHED) {
            scheduleRuntimeCapture(transaction, 1);
        } else if (transaction.phase() == Phase.CLEANUP_PENDING) {
            scheduleCommittedCleanup(transaction);
        }
    }

    private void scheduleRuntimeCapture(Transaction transaction, int delayTicks) {
        synchronized (this) {
            if (!verificationInFlight.add(transaction.id())) {
                return;
            }
        }
        try {
            J.s(() -> captureLoadedPublishedWorldOnGlobal(transaction), delayTicks);
        } catch (Throwable failure) {
            finishRuntimeVerification(transaction.id());
            Iris.reportError("Could not schedule runtime verification for " + transaction.worldKey() + ".", failure);
        }
    }

    private void captureLoadedPublishedWorldOnGlobal(Transaction transaction) {
        World world;
        try {
            world = WorldIdentity.resolve(toNamespacedKey(transaction.worldKey())).orElse(null);
        } catch (Throwable failure) {
            dispatchRuntimeCaptureFailure(transaction, failure);
            return;
        }
        if (world == null) {
            finishRuntimeVerification(transaction.id());
            return;
        }
        PublishedWorldRuntimeState runtimeState;
        try {
            runtimeState = capturePublishedWorldRuntime(world);
        } catch (Throwable failure) {
            dispatchRuntimeCaptureFailure(transaction, failure);
            return;
        }
        try {
            J.a(() -> runPublishedWorldVerification(runtimeState, transaction));
        } catch (Throwable failure) {
            finishRuntimeVerification(transaction.id());
            Iris.reportError("Could not dispatch runtime verification for " + transaction.worldKey() + ".", failure);
        }
    }

    private void dispatchRuntimeCaptureFailure(Transaction transaction, Throwable failure) {
        try {
            J.a(() -> runPublishedWorldCaptureFailure(transaction, failure));
        } catch (Throwable dispatchFailure) {
            finishRuntimeVerification(transaction.id());
            dispatchFailure.addSuppressed(failure);
            Iris.reportError("Could not dispatch a failed runtime capture for "
                    + transaction.worldKey() + ".", dispatchFailure);
        }
    }

    private void runPublishedWorldCaptureFailure(Transaction transaction, Throwable failure) {
        try {
            initiateRollback(transaction, failure);
        } finally {
            finishRuntimeVerification(transaction.id());
        }
    }

    private void runPublishedWorldVerification(
            PublishedWorldRuntimeState runtimeState,
            Transaction transaction
    ) {
        try {
            verifyPublishedWorld(runtimeState, transaction);
        } finally {
            finishRuntimeVerification(transaction.id());
        }
    }

    static PublishedWorldRuntimeState capturePublishedWorldRuntime(World world) {
        World requiredWorld = Objects.requireNonNull(world, "world");
        WorldSlotKey worldKey = toWorldSlotKey(WorldIdentity.key(requiredWorld));
        boolean irisWorld = IrisToolbelt.isIrisWorld(requiredWorld);
        long seed = requiredWorld.getSeed();
        World.Environment bukkitEnvironment = requiredWorld.getEnvironment();
        PlatformChunkGenerator generator = irisWorld ? IrisToolbelt.access(requiredWorld) : null;
        String dimension = null;
        IrisEnvironment dimensionEnvironment = null;
        if (generator != null) {
            IrisDimension runtimeDimension = generator.getTarget().getDimension();
            dimension = runtimeDimension.getLoadKey();
            dimensionEnvironment = runtimeDimension.getEnvironment();
        }
        return new PublishedWorldRuntimeState(
                worldKey,
                irisWorld,
                seed,
                bukkitEnvironment,
                dimension,
                dimensionEnvironment
        );
    }

    private void verifyPublishedWorld(PublishedWorldRuntimeState runtimeState, Transaction transaction) {
        try {
            ExactWorldSlotPathPolicy.Target target = resolveTransactionTarget(transaction);
            validatePublishedWorldRuntime(runtimeState, transaction, target.slotKind());
            ReplacementPaths paths = WorldReplacementFilesystem.paths(target, transaction.id());
            String fingerprint = WorldReplacementFilesystem.fingerprintPack(
                    IrisWorldStorage.requireActiveGenerationPackRoot(
                            paths.target().toFile(),
                            transaction.seed()
                    ).toPath());
            if (!transaction.packFingerprint().equals(fingerprint)) {
                throw new IOException("The replacement pack changed before runtime verification.");
            }
            WorldGeneratorSnapshot configured = BukkitWorldConfiguration.snapshot(
                    ServerProperties.BUKKIT_YML,
                    transaction.worldName()
            );
            if (!configured.matchesGeneratorAndSeed(WorldReplacementBootstrap.replacementSnapshot(transaction))) {
                throw new IOException("bukkit.yml changed before the replacement could be committed.");
            }
        } catch (Throwable failure) {
            initiateRollback(transaction, failure);
            return;
        }
        Transaction committed = transaction.withPhase(Phase.CLEANUP_PENDING);
        try {
            writeTransaction(committed);
            Iris.success("Committed Iris world replacement for " + transaction.worldKey() + ".");
            if (OVERWORLD_KEY.equals(transaction.worldKey())) {
                overworldEntryAwaitingVerification = null;
                scheduleLoadedOverworldEntryPreparation();
            }
            scheduleCommittedCleanup(committed);
        } catch (Throwable failure) {
            Iris.reportError("The replacement for " + transaction.worldKey()
                    + " was verified, but its cleanup journal could not be advanced."
                    + " The retained backup was not removed.", failure);
        }
    }

    static void validatePublishedWorldRuntime(
            PublishedWorldRuntimeState runtimeState,
            Transaction transaction,
            SlotKind slotKind
    ) throws IOException {
        PublishedWorldRuntimeState requiredRuntimeState = Objects.requireNonNull(runtimeState, "runtimeState");
        Transaction requiredTransaction = Objects.requireNonNull(transaction, "transaction");
        SlotKind requiredSlotKind = Objects.requireNonNull(slotKind, "slotKind");
        if (!requiredTransaction.worldKey().equals(requiredRuntimeState.worldKey())) {
            throw new IOException("Loaded world identity does not match the replacement journal.");
        }
        if (!requiredRuntimeState.irisWorld()) {
            throw new IOException("The replaced world did not load with an Iris generator.");
        }
        if (requiredRuntimeState.seed() != requiredTransaction.seed()) {
            throw new IOException("The replaced world loaded with an unexpected seed.");
        }
        World.Environment expectedEnvironment = expectedEnvironment(requiredTransaction.worldKey());
        if (expectedEnvironment != null && requiredRuntimeState.bukkitEnvironment() != expectedEnvironment) {
            throw new IOException("The replaced world loaded with an unexpected environment.");
        }
        if (requiredRuntimeState.dimension() == null
                || requiredRuntimeState.dimensionEnvironment() == null
                || !requiredTransaction.dimension().equals(requiredRuntimeState.dimension())) {
            throw new IOException("The replaced world loaded an unexpected Iris dimension.");
        }
        requireCompatibleEnvironment(requiredSlotKind, requiredRuntimeState.dimensionEnvironment());
    }

    private synchronized void finishRuntimeVerification(UUID transactionId) {
        verificationInFlight.remove(transactionId);
    }

    private void initiateRollback(Transaction transaction, Throwable failure) {
        Iris.reportError("Iris world replacement verification failed for " + transaction.worldKey()
                + "; the retained world will be restored on restart.", failure);
        if (OVERWORLD_KEY.equals(transaction.worldKey())) {
            clearOverworldEntryGuard();
        }
        try {
            Transaction rollback = transaction.withPhase(Phase.ROLLBACK_PENDING);
            writeTransaction(rollback);
        } catch (Throwable rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
            IrisStartupValidation.markPacksInvalid(List.of(
                    "Iris could not arm rollback for " + transaction.worldKey() + ": " + detail(rollbackFailure)));
            Iris.reportError("Failed to arm Iris world replacement rollback for "
                    + transaction.worldKey() + ". Stop the server and preserve the replacement artifacts.", rollbackFailure);
        } finally {
            ServerConfigurator.restart("An Iris world replacement failed verification and requires a cold restart.");
        }
    }

    private void inspectStartupTransaction(Transaction transaction) throws IOException {
        ExactWorldSlotPathPolicy.Target target = resolveTransactionTarget(transaction);
        ReplacementPaths paths = WorldReplacementFilesystem.paths(target, transaction.id());
        WorldGeneratorSnapshot current = BukkitWorldConfiguration.snapshot(
                ServerProperties.BUKKIT_YML,
                transaction.worldName()
        );
        WorldGeneratorSnapshot replacement = WorldReplacementBootstrap.replacementSnapshot(transaction);
        if (transaction.phase() == Phase.CLEANUP_PENDING) {
            if (!current.matchesGeneratorAndSeed(replacement)) {
                throw new IOException("A verified replacement no longer matches bukkit.yml.");
            }
            WorldReplacementFilesystem.validateCommittedTarget(paths, transaction.packFingerprint());
            return;
        }
        if (transaction.phase() == Phase.ROLLBACK_PENDING
                || transaction.phase() == Phase.ROLLBACK_CLEANUP
                || transaction.phase() == Phase.ARMED) {
            throw new RestartBoundaryRequired("The world-storage transaction has not reached its cold bootstrap.");
        }
        if (transaction.phase() == Phase.PREPARED) {
            if (current.matchesGeneratorAndSeed(transaction.originalConfiguration())) {
                WorldReplacementFilesystem.discardStage(paths);
                deleteJournal(transaction.id());
                Iris.warn("Cancelled incomplete Iris world replacement for " + transaction.worldKey() + ".");
                return;
            } else if (current.matchesGeneratorAndSeed(replacement)) {
                writeTransaction(transaction.withPhase(Phase.ARMED));
                throw new RestartBoundaryRequired("The replacement was armed before its journal phase was durable.");
            } else {
                throw new IOException("bukkit.yml does not match the prepared replacement or its original state.");
            }
        }
        if (transaction.phase() == Phase.PUBLISHED) {
            if (!current.matchesGeneratorAndSeed(replacement)) {
                throw new IOException("bukkit.yml changed after the replacement was published.");
            }
            WorldReplacementFilesystem.validatePublishedTarget(
                    paths,
                    transaction.originalTargetPresent(),
                    transaction.packFingerprint()
            );
        }
    }

    private synchronized void scheduleCommittedCleanup(Transaction transaction) {
        if (!cleanupInFlight.add(transaction.id())) {
            return;
        }
        try {
            J.a(() -> cleanupCommittedReplacement(transaction));
        } catch (Throwable failure) {
            cleanupInFlight.remove(transaction.id());
            Iris.reportError("Could not schedule retained-backup cleanup for " + transaction.worldKey()
                    + "; cleanup will retry without rolling back the verified world.", failure);
        }
    }

    private void cleanupCommittedReplacement(Transaction transaction) {
        try {
            synchronized (this) {
                Transaction current = findTransaction(transaction.worldKey());
                if (current == null
                        || !current.id().equals(transaction.id())
                        || current.phase() != Phase.CLEANUP_PENDING) {
                    return;
                }
                WorldGeneratorSnapshot configured = BukkitWorldConfiguration.snapshot(
                        ServerProperties.BUKKIT_YML,
                        current.worldName()
                );
                if (!configured.matchesGeneratorAndSeed(WorldReplacementBootstrap.replacementSnapshot(current))) {
                    throw new IOException("bukkit.yml changed before the retained backup could be removed.");
                }
                ExactWorldSlotPathPolicy.Target target = resolveTransactionTarget(current);
                ReplacementPaths paths = WorldReplacementFilesystem.paths(target, current.id());
                WorldReplacementFilesystem.validateCommittedTarget(paths, current.packFingerprint());
                WorldReplacementFilesystem.cleanupBackup(paths);
                deleteJournal(current.id());
                Iris.success("Removed the retained backup for " + current.worldKey() + ".");
            }
        } catch (Throwable failure) {
            Iris.reportError("Could not clean the retained backup for " + transaction.worldKey()
                    + "; cleanup will retry without rolling back the verified world.", failure);
        } finally {
            synchronized (this) {
                cleanupInFlight.remove(transaction.id());
            }
        }
    }

    private static ExactWorldSlotPathPolicy.Target resolveTarget(WorldSlotKey worldKey) {
        return ExactWorldSlotPathPolicy.resolve(IrisWorldStorage.levelRoot().toPath(), worldKey);
    }

    private Transaction findTransaction(NamespacedKey worldKey) throws IOException {
        return findTransaction(toWorldSlotKey(worldKey));
    }

    private Transaction findTransaction(WorldSlotKey worldKey) throws IOException {
        for (Transaction transaction : loadTransactions()) {
            if (transaction.worldKey().equals(worldKey)) {
                return transaction;
            }
        }
        return null;
    }

    private List<Transaction> loadTransactions() throws IOException {
        Path levelRoot = IrisWorldStorage.levelRoot().toPath();
        List<Transaction> transactions = WorldReplacementJournal.load(dataDirectory(), levelRoot);
        List<Transaction> applicable = new ArrayList<>(transactions.size());
        for (Transaction transaction : transactions) {
            // A journal staged against another level root is not this server's transaction;
            // skip it (the bootstrap already told the operator how to resolve it).
            if (WorldReplacementJournal.appliesTo(transaction, levelRoot)) {
                applicable.add(transaction);
            } else {
                Iris.warn("Ignoring pending world replacement " + transaction.id() + " for "
                        + transaction.worldKey() + "; it was staged against level root "
                        + transaction.levelRoot() + ".");
            }
        }
        return applicable;
    }

    private void writeTransaction(Transaction transaction) throws IOException {
        WorldReplacementJournal.write(dataDirectory(), transaction);
    }

    private void deleteJournal(UUID id) throws IOException {
        WorldReplacementJournal.delete(dataDirectory(), id);
    }

    private Path dataDirectory() {
        return plugin.getDataFolder().toPath().toAbsolutePath().normalize();
    }

    private synchronized void loadOverworldEntryGuard() throws IOException {
        ExactWorldSlotPathPolicy.Target target = resolveTarget(OVERWORLD_KEY);
        Optional<WorldReplacementEntryGuard.Entry> loaded = WorldReplacementEntryGuard.load(target.worldDirectory());
        overworldEntryGuard = loaded.orElse(null);
        overworldEntryWorldDirectory = loaded.isPresent() ? target.worldDirectory() : null;
        overworldSafeEntry = null;
        overworldSpawnPersistence = null;
        overworldEntryAwaitingVerification = null;
    }

    private synchronized void clearOverworldEntryGuard() {
        overworldEntryGuard = null;
        overworldEntryWorldDirectory = null;
        overworldSafeEntry = null;
        overworldSpawnPersistence = null;
        overworldEntryAwaitingVerification = null;
        overworldEntryWorld = null;
        pendingEntryAcknowledgements.clear();
    }

    private ExactWorldSlotPathPolicy.Target resolveTransactionTarget(Transaction transaction) throws IOException {
        return WorldReplacementJournal.resolveTarget(transaction, IrisWorldStorage.levelRoot().toPath());
    }

    private static boolean configurationMatches(WorldGeneratorSnapshot expected, String worldName) {
        try {
            return BukkitWorldConfiguration.snapshot(ServerProperties.BUKKIT_YML, worldName)
                    .matchesGeneratorAndSeed(expected);
        } catch (IOException failure) {
            return false;
        }
    }

    static void requireCompatibleEnvironment(SlotKind slotKind, IrisEnvironment environment) {
        IrisEnvironment expected = switch (slotKind) {
            case VANILLA_OVERWORLD -> IrisEnvironment.NORMAL;
            case VANILLA_NETHER -> IrisEnvironment.NETHER;
            case VANILLA_END -> IrisEnvironment.THE_END;
            case IRIS_MANAGED -> null;
        };
        if (expected != null && environment != expected) {
            throw new IllegalArgumentException("The " + slotKind.name().toLowerCase(Locale.ENGLISH)
                    + " slot requires a pack environment of " + expected.name() + ".");
        }
    }

    /**
     * Captures vanilla-slot availability on the main thread at startup so staging never blocks
     * on a main-thread hop while holding the manager monitor. The context is stable for the
     * process lifetime because slot replacements only commit across a restart.
     */
    public void captureVanillaLevelContext() {
        try {
            vanillaLevelContext = new VanillaLevelContext(
                    Iris.instance.getServer().getAllowNether(),
                    Iris.instance.getServer().getAllowEnd()
            );
        } catch (Throwable failure) {
            Iris.reportError("Could not capture vanilla-slot availability at startup;"
                    + " world replacement staging will resolve it on demand.", failure);
        }
    }

    private static void requireVanillaSlotEnabled(SlotKind slotKind) throws IOException {
        if (slotKind != SlotKind.VANILLA_NETHER && slotKind != SlotKind.VANILLA_END) {
            return;
        }
        VanillaLevelContext context = vanillaLevelContext;
        if (context == null) {
            context = resolveVanillaLevelContext();
            vanillaLevelContext = context;
        }
        requireVanillaSlotEnabled(slotKind, context.allowNether(), context.allowEnd());
    }

    static void requireVanillaSlotEnabled(SlotKind slotKind, boolean allowNether, boolean allowEnd)
            throws IOException {
        if (slotKind == SlotKind.VANILLA_NETHER && !allowNether) {
            throw new IOException("allow-nether must be true before the vanilla Nether can be replaced.");
        }
        if (slotKind == SlotKind.VANILLA_END && !allowEnd) {
            throw new IOException("Bukkit allow-end must be true before the vanilla End can be replaced.");
        }
    }

    private static VanillaLevelContext resolveVanillaLevelContext() throws IOException {
        CompletableFuture<VanillaLevelContext> contextFuture = J.sfut(() -> new VanillaLevelContext(
                Iris.instance.getServer().getAllowNether(),
                Iris.instance.getServer().getAllowEnd()
        ));
        if (contextFuture == null) {
            throw new IOException("Could not schedule vanilla-slot availability resolution.");
        }
        try {
            return contextFuture.get(30L, TimeUnit.SECONDS);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IOException("Vanilla-slot availability resolution was interrupted.", failure);
        } catch (ExecutionException | TimeoutException failure) {
            throw new IOException("Could not resolve vanilla-slot availability.", failure);
        }
    }

    private static World.Environment expectedEnvironment(WorldSlotKey worldKey) {
        if (WorldSlotKey.minecraft("overworld").equals(worldKey)) {
            return World.Environment.NORMAL;
        }
        if (WorldSlotKey.minecraft("the_nether").equals(worldKey)) {
            return World.Environment.NETHER;
        }
        if (WorldSlotKey.minecraft("the_end").equals(worldKey)) {
            return World.Environment.THE_END;
        }
        return null;
    }

    private static WorldSlotKey toWorldSlotKey(NamespacedKey worldKey) {
        NamespacedKey requiredWorldKey = Objects.requireNonNull(worldKey, "worldKey");
        return new WorldSlotKey(requiredWorldKey.getNamespace(), requiredWorldKey.getKey());
    }

    private static NamespacedKey toNamespacedKey(WorldSlotKey worldKey) {
        WorldSlotKey requiredWorldKey = Objects.requireNonNull(worldKey, "worldKey");
        return new NamespacedKey(requiredWorldKey.namespace(), requiredWorldKey.key());
    }

    private static String detail(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    public record StagedReplacement(
            NamespacedKey worldKey,
            String worldName,
            String dimension,
            long seed,
            boolean replacedExistingTarget,
            boolean datapackRestartRequired
    ) {
        public StagedReplacement {
            Objects.requireNonNull(worldKey, "worldKey");
            Objects.requireNonNull(worldName, "worldName");
            Objects.requireNonNull(dimension, "dimension");
        }
    }

    private static volatile VanillaLevelContext vanillaLevelContext;

    private record VanillaLevelContext(boolean allowNether, boolean allowEnd) {
    }

    record ReplacementEntryRedirect(
            UUID transactionId,
            Location location,
            boolean acknowledgementRequired
    ) {
        ReplacementEntryRedirect {
            Objects.requireNonNull(transactionId, "transactionId");
            location = Objects.requireNonNull(location, "location").clone();
        }

        @Override
        public Location location() {
            return location.clone();
        }
    }

    record SavedLocationInspection(boolean safe, Throwable failure) {
    }

    private static final class RestartBoundaryRequired extends IOException {
        private RestartBoundaryRequired(String message) {
            super(message);
        }
    }

    record PublishedWorldRuntimeState(
            WorldSlotKey worldKey,
            boolean irisWorld,
            long seed,
            World.Environment bukkitEnvironment,
            String dimension,
            IrisEnvironment dimensionEnvironment
    ) {
        PublishedWorldRuntimeState {
            Objects.requireNonNull(worldKey, "worldKey");
            Objects.requireNonNull(bukkitEnvironment, "bukkitEnvironment");
        }
    }
}
