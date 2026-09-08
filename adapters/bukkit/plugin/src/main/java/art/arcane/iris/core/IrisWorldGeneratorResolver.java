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

package art.arcane.iris.core;

import art.arcane.iris.Iris;
import art.arcane.iris.core.lifecycle.WorldLifecycleStaging;
import art.arcane.iris.core.compat.CompatRegistry;
import art.arcane.iris.core.lifecycle.WorldReplacementSeed;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.pack.BrokenPackException;
import art.arcane.iris.core.pack.PackDownloader;
import art.arcane.iris.core.pack.PackDirectoryResolver;
import art.arcane.iris.core.pack.PackValidationCache;
import art.arcane.iris.core.pack.PackValidationRegistry;
import art.arcane.iris.core.pack.PackValidationResult;
import art.arcane.iris.core.pack.PackValidator;
import art.arcane.iris.core.runtime.RuntimeInjection;
import art.arcane.iris.core.service.ExternalDataSVC;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.engine.framework.EngineTarget;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisWorld;
import art.arcane.iris.engine.history.GenerationEpoch;
import art.arcane.iris.engine.history.GenerationEpochContractFactory;
import art.arcane.iris.engine.history.GenerationHistory;
import art.arcane.iris.engine.history.GenerationHistoryPaths;
import art.arcane.iris.engine.history.GenerationPackFingerprint;
import art.arcane.iris.engine.history.GenerationRegistryContract;
import art.arcane.iris.engine.history.GenerationRegistryContractFactory;
import art.arcane.iris.engine.platform.BukkitChunkGenerator;
import art.arcane.iris.util.common.plugin.VolmitPlugin;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Pack validation, dimension lookup, and the world generator / biome provider resolution that the
 * Bukkit plugin entry points delegate to.
 */
public final class IrisWorldGeneratorResolver {
    private static final int VALIDATION_STABILITY_ATTEMPTS = 2;
    private static final int COMPAT_BOOT_KEY_CAP = 3;
    private static final Object SNAPSHOT_VALIDATION_LOCK = new Object();
    private static final String IRIS_DIMENSION_NAMESPACE = "iris";
    private static final String PLOT_SQUARED_DISCOVERY_WORLD = "CheckingPlotSquaredGenerator";
    private static final long EXTERNAL_CONTENT_STARTUP_TIMEOUT_SECONDS = 120L;

    private final VolmitPlugin plugin;
    private final AtomicBoolean externalContentRefreshQueued = new AtomicBoolean();
    private final AtomicBoolean externalContentRefreshRequested = new AtomicBoolean();
    private final Map<Path, PendingSnapshot> pendingSnapshots = new ConcurrentHashMap<>();

    public IrisWorldGeneratorResolver(VolmitPlugin plugin) {
        this.plugin = plugin;
    }

    public void validateAllPacks() {
        validateAllPacks(false);
    }

    public void requestExternalContentRefresh() {
        externalContentRefreshRequested.set(true);
        if (!externalContentRefreshQueued.compareAndSet(false, true)) {
            return;
        }
        try {
            J.a(this::runExternalContentRefresh);
        } catch (RuntimeException | LinkageError error) {
            externalContentRefreshQueued.set(false);
            throw error;
        }
    }

    public CompletableFuture<Void> startupWorldsReady() {
        return CompletableFuture.allOf(pendingSnapshots.values().stream()
                .map(PendingSnapshot::ready)
                .toArray(CompletableFuture<?>[]::new));
    }

    private void runExternalContentRefresh() {
        externalContentRefreshRequested.set(false);
        try {
            retryPendingSnapshots();
            refreshExternalContent();
        } catch (RuntimeException | LinkageError error) {
            Iris.reportError("Failed to revalidate Iris packs after external content changed.", error);
        } finally {
            externalContentRefreshQueued.set(false);
            if (externalContentRefreshRequested.get()) {
                requestExternalContentRefresh();
            }
        }
    }

    private void retryPendingSnapshots() {
        for (Map.Entry<Path, PendingSnapshot> entry : pendingSnapshots.entrySet()) {
            PendingSnapshot pending = entry.getValue();
            if (pending.contentReady().isDone()) {
                continue;
            }
            try {
                ExternalDataSVC external = IrisServices.getOrNull(ExternalDataSVC.class);
                long revision = external == null ? 0L : external.contentRevision();
                boolean wasPending = hasPendingExternalContent(PackValidationRegistry.get(entry.getKey()), external);
                PackValidationRegistry.remove(entry.getKey());
                IrisData.invalidateLoadedContentRegistries(entry.getKey().toFile());
                PackValidationResult validation = validateSnapshot(entry.getKey().toFile());
                if (external != null && revision != external.contentRevision()) {
                    requestExternalContentRefresh();
                    continue;
                }
                boolean stillPending = hasPendingExternalContent(validation, external);
                if (wasPending && !stillPending) {
                    requestExternalContentRefresh();
                    continue;
                }
                if (stillPending) {
                    continue;
                }
                PackValidationRegistry.requireLoadable(entry.getKey());
                requireHistoricalDimension(pending.history(), entry.getKey().toFile(), pending.dimensionKey());
                pending.contentReady().complete(null);
            } catch (RuntimeException | LinkageError failure) {
                pending.contentReady().completeExceptionally(failure);
            }
        }
    }

    synchronized void refreshExternalContent() {
        for (File pack : PackDirectoryResolver.listVisiblePackDirectories(plugin.getDataFolder("packs"))) {
            IrisData.invalidateLoadedContentRegistries(pack);
        }
        validateAllPacks(true);
    }

    private synchronized void validateAllPacks(boolean externalContentChanged) {
        File packsRoot = plugin.getDataFolder("packs");
        List<File> packDirs = PackDirectoryResolver.listVisiblePackDirectories(packsRoot);
        if (!externalContentChanged) {
            PackValidationRegistry.clear();
        }
        List<String> packNames = packDirs.stream().map(File::getName).sorted().toList();
        Path cacheFile = IrisPlatforms.get().dataFile("cache", "pack-validation.json").toPath();
        ServerConfigurator.PackContentSnapshot contentSnapshot =
                new ServerConfigurator.PackContentSnapshot("", Map.of());
        String contextFingerprint = "";
        Optional<List<PackValidationResult>> cached = Optional.empty();
        try {
            contentSnapshot = ServerConfigurator.computePackContentSnapshot(packsRoot);
            contextFingerprint = PackValidationCache.contextFingerprint();
            if (!externalContentChanged) {
                cached = PackValidationCache.load(
                        cacheFile,
                        contentSnapshot.content(),
                        contextFingerprint,
                        packNames);
            }
        } catch (RuntimeException exception) {
            Iris.reportError("Could not evaluate the persisted pack-validation cache", exception);
        }

        List<PackValidationResult> results;
        if (cached.isPresent()) {
            results = cached.get();
            Iris.info("Reused persisted validation for " + results.size()
                    + " unchanged Iris pack(s); full pack parsing was skipped.");
        } else {
            FreshValidation validation = validateStablePacks(packsRoot, packDirs, contentSnapshot);
            packDirs = validation.packDirs();
            contentSnapshot = validation.contentSnapshot();
            results = validation.results();
            if (validation.stable()) {
                try {
                    PackValidationCache.save(
                            cacheFile,
                            contentSnapshot.content(),
                            contextFingerprint,
                            results);
                } catch (IOException exception) {
                    Iris.reportError("Could not persist Iris pack-validation results", exception);
                }
            }
        }

        Map<String, String> packFingerprints = contentSnapshot.packContents();
        for (PackValidationResult result : results) {
            PackValidationRegistry.publish(result);
            String packFingerprint = packFingerprints.get(result.getPackName());
            File packDirectory = PackDirectoryResolver.resolveExisting(packsRoot, result.getPackName());
            if (packDirectory != null && packFingerprint != null && !packFingerprint.isBlank()) {
                PackValidationRegistry.publish(packDirectory.toPath(), result, packFingerprint);
            }
            String minecraftVersion = IrisPlatforms.isBound() ? IrisPlatforms.get().minecraftVersion() : null;
            String compatSummary = PackValidator.compatSummary(result, minecraftVersion);
            String compatSuffix = compatSummary.isEmpty() ? "" : " " + compatSummary;
            if (!result.isLoadable()) {
                if (hasPendingExternalContent(result, IrisServices.getOrNull(ExternalDataSVC.class))
                        && packDirectory != null
                        && PackValidator.validateForDatapackBootstrap(packDirectory).isLoadable()) {
                    Iris.info("Pack '" + result.getPackName() + "' is waiting for external block providers to initialize.");
                    continue;
                }
                Iris.error("Pack '" + result.getPackName()
                        + "' FAILED validation - world and Studio creation with this pack will be refused. Reasons:");
                for (String reason : result.getBlockingErrors()) {
                    Iris.error("  - " + reason);
                }
            } else if (!result.getWarnings().isEmpty()) {
                Iris.info("Pack '" + result.getPackName() + "' validated ("
                        + result.getWarnings().size() + " warning(s))." + compatSuffix);
                for (String warning : result.getWarnings()) {
                    Iris.warn("  [" + result.getPackName() + "] " + warning);
                }
            } else if (cached.isEmpty()) {
                if (compatSummary.isEmpty()) {
                    Iris.success("Pack '" + result.getPackName() + "' validated.");
                } else {
                    Iris.info("Pack '" + result.getPackName() + "' validated." + compatSuffix);
                }
            } else if (!compatSummary.isEmpty()) {
                Iris.info("Pack '" + result.getPackName() + "' validated." + compatSuffix);
            }
            // Cache hits carry the persisted findings, so the listing prints on every boot, not only after a miss.
            for (String line : PackValidator.compatBootLines(result, minecraftVersion, COMPAT_BOOT_KEY_CAP)) {
                Iris.warn(line);
            }
        }
        IrisStartupValidation.markPacksReady();
    }

    private static FreshValidation validateStablePacks(
            File packsRoot,
            List<File> initialPackDirs,
            ServerConfigurator.PackContentSnapshot initialSnapshot
    ) {
        List<File> packDirs = initialPackDirs;
        ServerConfigurator.PackContentSnapshot before = initialSnapshot;
        for (int attempt = 0; attempt < VALIDATION_STABILITY_ATTEMPTS; attempt++) {
            List<String> packNames = packDirs.stream().map(File::getName).sorted().toList();
            List<PackValidationResult> results = validatePacks(packDirs);
            ServerConfigurator.PackContentSnapshot after;
            try {
                after = ServerConfigurator.computePackContentSnapshot(packsRoot);
            } catch (RuntimeException exception) {
                Iris.reportError("Could not verify Iris pack bytes after validation", exception);
                after = new ServerConfigurator.PackContentSnapshot("", Map.of());
            }
            List<File> afterPackDirs = PackDirectoryResolver.listVisiblePackDirectories(packsRoot);
            List<String> afterPackNames = afterPackDirs.stream().map(File::getName).sorted().toList();
            if (!before.content().isBlank()
                    && before.content().equals(after.content())
                    && packNames.equals(afterPackNames)) {
                return new FreshValidation(afterPackDirs, after, results, true);
            }
            packDirs = afterPackDirs;
            before = after;
        }
        Iris.error("Iris pack files kept changing during validation; validation was refused until writes stop.");
        List<PackValidationResult> failures = new ArrayList<>(packDirs.size());
        for (File packDir : packDirs) {
            failures.add(new PackValidationResult(
                    packDir.getName(),
                    List.of("Pack files changed while validation was in progress; retry after writes stop."),
                    List.of(),
                    System.currentTimeMillis()));
        }
        return new FreshValidation(packDirs, before, failures, false);
    }

    private static List<PackValidationResult> validatePacks(List<File> packDirs) {
        List<PackValidationResult> results = new ArrayList<>(packDirs.size());
        for (File packDir : packDirs) {
            try {
                results.add(PackValidator.validate(packDir));
            } catch (Throwable exception) {
                Iris.reportError("Pack validation failed for '" + packDir.getName() + "'", exception);
                String detail = exception.getMessage();
                if (detail == null || detail.isBlank()) {
                    detail = exception.getClass().getSimpleName();
                }
                results.add(new PackValidationResult(
                        packDir.getName(),
                        List.of("Pack validation failed with " + exception.getClass().getSimpleName()
                                + ": " + detail),
                        List.of(),
                        System.currentTimeMillis()));
            }
        }
        return results;
    }

    static PackValidationResult requireSnapshotLoadable(File packRoot) {
        validateSnapshot(packRoot);
        return PackValidationRegistry.requireLoadable(packRoot.toPath());
    }

    private static PackValidationResult validateSnapshot(File packRoot) {
        Path normalizedRoot = packRoot.toPath().toAbsolutePath().normalize();
        PackValidationResult result = PackValidationRegistry.get(normalizedRoot);
        if (result == null) {
            synchronized (SNAPSHOT_VALIDATION_LOCK) {
                result = PackValidationRegistry.get(normalizedRoot);
                if (result == null) {
                    PackValidationRegistry.ValidationTicket ticket =
                            PackValidationRegistry.tryBeginValidation(normalizedRoot);
                    if (ticket != null) {
                        try {
                            result = PackValidator.validate(normalizedRoot.toFile());
                        } catch (Throwable exception) {
                            Iris.reportError("Snapshot pack validation failed for '" + normalizedRoot + "'", exception);
                            String detail = exception.getMessage();
                            if (detail == null || detail.isBlank()) {
                                detail = exception.getClass().getSimpleName();
                            }
                            result = new PackValidationResult(
                                    normalizedRoot.getFileName().toString(),
                                    List.of("Pack validation failed with " + exception.getClass().getSimpleName()
                                            + ": " + detail),
                                    List.of(),
                                    System.currentTimeMillis());
                        }
                        PackValidationRegistry.publishIfCurrent(ticket, result);
                    }
                }
            }
        }
        return result;
    }

    @Nullable
    public static IrisDimension loadDimension(@NonNull String worldName, @NonNull String id) {
        File levelRoot = IrisWorldStorage.levelRoot();
        NamespacedKey worldKey = configuredWorldKey(worldName, levelRoot.getName(), levelRoot);
        String configuredWorldName = IrisWorldStorage.configuredWorldName(worldKey, levelRoot.getName());
        File dimensionRoot = IrisWorldStorage.frozenDimensionRoot(
                        Bukkit.getWorldContainer(),
                        levelRoot,
                        configuredWorldName,
                        worldKey
                )
                .orElse(null);
        IrisDimension dimension = null;
        if (dimensionRoot != null) {
            long worldSeed = requireStoredWorldSeed(configuredWorldName, dimensionRoot);
            GenerationHistory history = requireGenerationHistory(dimensionRoot, id, worldSeed);
            File pack = requireActivePack(history);
            requireSnapshotLoadable(pack);
            dimension = requireHistoricalDimension(history, pack, id);
        }
        if (dimension == null) dimension = IrisData.loadAnyDimension(id, null);
        if (dimension == null) {
            File packsRoot = IrisPlatforms.get().packsFolderNoCreate();
            if (PackDownloader.isPackPresent(packsRoot, id)) {
                Iris.error("Pack '" + id + "' exists at " + new File(packsRoot, id).getPath()
                        + " but its dimension failed to load; not redownloading. Fix or delete the pack folder.");
                return null;
            }
            Iris.warn("Unable to find dimension type " + id + ". Install its pack with "
                    + PackDownloader.downloadCommandFor(id) + " and restart the server.");
        }

        return dimension;
    }

    /**
     * Resolves the Iris key behind a Bukkit world name, accepting the runtime keyed name alongside the
     * startup name.
     * <p>
     * Paper names a world built from {@code WorldCreator.ofKey} {@code <namespace>_<key>}, and Multiverse
     * loads Iris worlds that way because its own keyed creator refuses a non-{@code minecraft} namespace.
     * That is not the startup name, so the configured-name parser reads {@code iris_moon} as
     * {@code iris:iris_moon} and loses the world.
     * <p>
     * Only a name whose decoded key already has Iris storage is remapped, and only when the literal
     * reading has none, so a world genuinely created as {@code /iris create "iris moon"} keeps its own
     * identity and a server without Multiverse resolves exactly the names it resolved before.
     */
    static NamespacedKey configuredWorldKey(String worldName, String levelName, File levelRoot) {
        NamespacedKey literal = IrisWorldStorage.keyFromConfiguredWorldName(worldName, levelName);
        String runtimePrefix = IRIS_DIMENSION_NAMESPACE + "_";
        if (!worldName.startsWith(runtimePrefix)
                || worldName.startsWith(levelName + "_" + runtimePrefix)
                || IrisWorldStorage.isExistingManagedDimensionRoot(levelRoot, literal)) {
            return literal;
        }
        NamespacedKey runtime;
        try {
            runtime = IrisWorldStorage.managedKeyFromName(
                    IRIS_DIMENSION_NAMESPACE + ":" + worldName.substring(runtimePrefix.length()),
                    levelName);
        } catch (RuntimeException notAManagedKey) {
            return literal;
        }
        if (!IrisWorldStorage.isExistingManagedDimensionRoot(levelRoot, runtime)) {
            return literal;
        }
        IrisLogging.debug("Resolved runtime keyed world name " + worldName + " as " + runtime);
        return runtime;
    }

    /**
     * The key a refusal message should name for a world name, without the storage-existence guard.
     * <p>
     * {@link #configuredWorldKey} only remaps the runtime keyed name when the decoded key has storage, so an
     * orphan keeps the literal reading and a message built from it names {@code iris:iris_moon} and tells
     * the admin to create a world that never existed under that name. Messaging does not have to be safe
     * against mis-mapping a live world, only correct about what the admin typed, so it drops that guard;
     * generation keeps it.
     */
    static NamespacedKey messageWorldKey(String worldName, String levelName) {
        NamespacedKey literal = IrisWorldStorage.keyFromConfiguredWorldName(worldName, levelName);
        String runtimePrefix = IRIS_DIMENSION_NAMESPACE + "_";
        if (!worldName.startsWith(runtimePrefix)
                || worldName.startsWith(levelName + "_" + runtimePrefix)) {
            return literal;
        }
        try {
            return IrisWorldStorage.managedKeyFromName(
                    IRIS_DIMENSION_NAMESPACE + ":" + worldName.substring(runtimePrefix.length()),
                    levelName);
        } catch (RuntimeException notAManagedKey) {
            return literal;
        }
    }

    /**
     * Resolves the biome provider for a world, falling back to the supplied Bukkit default when
     * Iris has nothing staged.
     */
    @Nullable
    public BiomeProvider resolveDefaultBiomeProvider(String worldName, @Nullable String id, Supplier<BiomeProvider> fallback) {
        BiomeProvider stagedBiomeProvider = WorldLifecycleStaging.consumeBiomeProvider(worldName);
        if (stagedBiomeProvider != null) {
            Iris.debug("Using staged runtime biome provider for " + worldName);
            return stagedBiomeProvider;
        }
        Iris.debug("Biome Provider Called for " + worldName + " using ID: " + id);
        return fallback.get();
    }

    @Nullable
    public ChunkGenerator resolveDefaultWorldGenerator(String worldName, @Nullable String id) {
        if (isPlotSquaredGeneratorDiscoveryProbe(worldName, id)) {
            Iris.debug("Ignoring PlotSquared generator discovery probe");
            return null;
        }
        if (isGeneratorDiscoveryProbe(worldName, id)) {
            Iris.debug("Generator discovery probe for loaded world " + worldName);
            return IrisFailClosedChunkGenerator.discoveryProbe(worldName);
        }
        ChunkGenerator startupLock = startupLockedGenerator(worldName);
        if (startupLock != null) {
            return startupLock;
        }
        ChunkGenerator stagedGenerator = WorldLifecycleStaging.consumeGenerator(worldName);
        if (stagedGenerator != null) {
            Iris.debug("Using staged runtime generator for " + worldName);
            return stagedGenerator;
        }
        RuntimeInjection.installIfDeferred();
        startupLock = startupLockedGenerator(worldName);
        if (startupLock != null) {
            return startupLock;
        }
        Iris.debug("Default World Generator Called for " + worldName + " using ID: " + id);
        if (id == null || id.isEmpty()) id = IrisSettings.get().getGenerator().getDefaultWorldType();
        Iris.debug("Generator ID: " + id + " requested by bukkit/plugin");

        File levelRoot = IrisWorldStorage.levelRoot();
        NamespacedKey worldKey = configuredWorldKey(worldName, levelRoot.getName(), levelRoot);
        requireWorldKeyAvailable(worldName, worldKey);
        requireOwnedWorld(worldName, levelRoot, worldKey);

        try {
            return resolveFrozenWorldGenerator(worldName, id);
        } catch (RuntimeException failure) {
            Iris.reportError("Refusing to load configured Iris world '" + worldName
                    + "' because its frozen world-local pack snapshot could not be used.", failure);
            Bukkit.shutdown();
            throw failure;
        }
    }

    @Nullable
    private static ChunkGenerator startupLockedGenerator(String worldName) {
        Optional<String> startupDenial = IrisStartupValidation.denialReason();
        if (startupDenial.isEmpty()) {
            return null;
        }
        Iris.warn("Keeping configured Iris world '" + worldName
                + "' generation-locked: " + startupDenial.get());
        return IrisFailClosedChunkGenerator.startupLock(worldName, startupDenial.get());
    }

    private static boolean isPlotSquaredGeneratorDiscoveryProbe(String worldName, String id) {
        return PLOT_SQUARED_DISCOVERY_WORLD.equals(worldName) && id != null && id.isEmpty();
    }

    /**
     * Multiverse-Core probes every enabled plugin by asking for a generator with an empty dimension
     * id and the name of a world that is already loaded. Bukkit never creates a world that is
     * already loaded, so that pair only ever means discovery, never generation.
     */
    private static boolean isGeneratorDiscoveryProbe(String worldName, String id) {
        return id != null && id.isEmpty() && Bukkit.getWorld(worldName) != null;
    }

    /**
     * Refuses a second generator for a world key that is already live. Multiverse imports accept a
     * world name that maps onto a loaded Iris key, which would otherwise start a second engine on
     * the same storage with a different seed.
     */
    private static void requireWorldKeyAvailable(String worldName, NamespacedKey worldKey) {
        World loaded = WorldIdentity.resolve(worldKey).orElse(null);
        if (loaded == null) {
            return;
        }
        throw new IllegalStateException("Refusing to generate '" + worldName + "': " + worldKey
                + " is already loaded as '" + loaded.getName() + "'.");
    }

    /**
     * Refuses worlds Iris does not own before the fail-fast path can see them. A world without Iris
     * storage is somebody else's create or import, and returning null there would silently hand the
     * caller a vanilla world.
     * <p>
     * The {@code iris} dimension namespace is Iris-exclusive, so a directory in it is ownership on
     * its own. Every server already has {@code dimensions/minecraft/*}, so a vanilla slot counts as
     * Iris' only once it carries a frozen pack snapshot.
     */
    private static void requireOwnedWorld(String worldName, File levelRoot, NamespacedKey worldKey) {
        File dimensionRoot;
        try {
            dimensionRoot = IrisWorldStorage.frozenDimensionRoot(
                    Bukkit.getWorldContainer(),
                    levelRoot,
                    worldName,
                    worldKey
            ).orElse(null);
        } catch (RuntimeException unusableStorage) {
            // Storage exists but cannot be resolved: an owned world, left to the fail-fast path.
            return;
        }
        if (dimensionRoot != null
                && (IRIS_DIMENSION_NAMESPACE.equals(worldKey.getNamespace()) || hasGenerationStorage(dimensionRoot))) {
            return;
        }
        NamespacedKey messageKey = messageWorldKey(worldName, levelRoot.getName());
        throw new IllegalStateException("'" + worldName + "' (" + messageKey
                + ") has no Iris world storage, so Iris cannot generate it."
                + " Create Iris worlds with /iris create " + IrisWorldStorage.logicalName(messageKey)
                + " type=<pack>; Iris registers them with Multiverse itself.");
    }

    private static boolean hasGenerationStorage(File dimensionRoot) {
        GenerationHistoryPaths paths = GenerationHistoryPaths.forDimension(dimensionRoot.toPath());
        return Files.isDirectory(paths.generationRoot(), LinkOption.NOFOLLOW_LINKS)
                || Files.isDirectory(paths.legacyPackRoot(), LinkOption.NOFOLLOW_LINKS);
    }

    private ChunkGenerator resolveFrozenWorldGenerator(String worldName, String id) {
        File levelRoot = IrisWorldStorage.levelRoot();
        NamespacedKey worldKey = configuredWorldKey(worldName, levelRoot.getName(), levelRoot);
        File dimensionRoot = IrisWorldStorage.requireFrozenDimensionRoot(
                Bukkit.getWorldContainer(),
                levelRoot,
                worldName,
                worldKey
        );
        File expectedDimensionRoot = WorldCreatorCompat.persistentDimensionRoot(worldKey);
        if (!dimensionRoot.toPath().toAbsolutePath().normalize()
                .equals(expectedDimensionRoot.toPath().toAbsolutePath().normalize())) {
            throw new IllegalStateException("Frozen Iris world storage does not match the current platform layout for "
                    + worldKey + ".");
        }
        long worldSeed = requireStoredWorldSeed(worldName, dimensionRoot);
        GenerationHistory history = requireGenerationHistory(dimensionRoot, id, worldSeed);
        File snapshotRoot = requireActivePack(history);
        PackValidationResult validation = validateSnapshot(snapshotRoot);
        if (hasPendingExternalContent(validation, IrisServices.getOrNull(ExternalDataSVC.class))) {
            return deferFrozenWorldGenerator(worldName, worldKey, dimensionRoot, snapshotRoot, id, history);
        }
        try {
            requireSnapshotLoadable(snapshotRoot);
        } catch (BrokenPackException exception) {
            Iris.error("Refusing to create world '" + worldName + "' using broken snapshot at '"
                    + snapshotRoot + "':");
            for (String reason : exception.getReasons()) {
                Iris.error("  - " + reason);
            }
            throw exception;
        }

        IrisDimension dimension = requireHistoricalDimension(history, snapshotRoot, id);

        Iris.debug("Assuming IrisDimension: " + dimension.getName());

        IrisWorld world = IrisWorld.builder()
                .platformIdentity(worldKey.toString())
                .name(worldName)
                .seed(history.activeEpoch().worldSeed())
                .worldFolder(dimensionRoot)
                .minHeight(dimension.getMinHeight())
                .maxHeight(dimension.getMaxHeight())
                .build();

        Iris.debug("Generator Config: " + world);

        return new BukkitChunkGenerator(world, false, snapshotRoot, dimension.getLoadKey(), history);
    }

    static boolean hasPendingExternalContent(PackValidationResult validation, ExternalDataSVC external) {
        return validation != null && external != null && validation.getCompatFindings().stream()
                .anyMatch(finding -> finding.registry() == CompatRegistry.BLOCK
                        && external.hasPendingBlockProvider(finding.key()));
    }

    private BukkitChunkGenerator deferFrozenWorldGenerator(
            String worldName,
            NamespacedKey worldKey,
            File dimensionRoot,
            File snapshotRoot,
            String dimensionKey,
            GenerationHistory history
    ) {
        PackValidationResult structural = PackValidator.validateForDatapackBootstrap(snapshotRoot);
        if (!structural.isLoadable()) {
            throw new BrokenPackException(snapshotRoot.toString(), structural.getBlockingErrors());
        }
        IrisData data = IrisData.openDatapackCompiler(snapshotRoot);
        try {
            IrisDimension dimension = requireHistoricalDimension(history, data, dimensionKey);
            GenerationEpoch.DimensionContract contract = history.activeEpoch().dimensionContract();
            IrisWorld world = IrisWorld.builder()
                    .platformIdentity(worldKey.toString())
                    .name(worldName)
                    .seed(history.activeEpoch().worldSeed())
                    .worldFolder(dimensionRoot)
                    .minHeight(contract.minHeight())
                    .maxHeight(Math.addExact(contract.minHeight(), contract.height()))
                    .build();
            Path snapshotPath = snapshotRoot.toPath().toAbsolutePath().normalize();
            CompletableFuture<Void> contentReady = new CompletableFuture<>();
            BukkitChunkGenerator generator = new BukkitChunkGenerator(
                    world, false, snapshotRoot, dimensionKey, history);
            generator.deferStartup(new EngineTarget(world, dimension, data), contentReady);
            PendingSnapshot pending = new PendingSnapshot(history, dimensionKey, contentReady, generator.getStartupReady());
            if (pendingSnapshots.putIfAbsent(snapshotPath, pending) != null) {
                generator.closeAsync();
                throw new IllegalStateException("Iris snapshot already has a deferred startup: " + snapshotPath);
            }
            contentReady.orTimeout(EXTERNAL_CONTENT_STARTUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            pending.ready().whenComplete((ignored, failure) -> {
                if (failure == null) {
                    pendingSnapshots.remove(snapshotPath, pending);
                }
            });
            Iris.info("Waiting for external block providers before initializing Iris world '" + worldName + "'.");
            requestExternalContentRefresh();
            return generator;
        } catch (RuntimeException | LinkageError failure) {
            data.close();
            throw failure;
        }
    }

    private static GenerationHistory requireGenerationHistory(
            File dimensionRoot,
            String dimensionKey,
            long worldSeed
    ) {
        Path root = dimensionRoot.toPath().toAbsolutePath().normalize();
        try {
            Optional<GenerationHistory> current = GenerationHistory.openIfPresent(root, worldSeed);
            GenerationHistory history = current.isPresent()
                    ? current.get()
                    : adoptLegacyGenerationHistory(root, dimensionKey, worldSeed);
            List<GenerationRegistryContract> retainedContracts = history.manifest()
                    .epochs()
                    .stream()
                    .map(GenerationEpoch::registryContract)
                    .toList();
            history.requireRegistryDefinitions(
                    GenerationRegistryContractFactory.captureRequiredDefinitions(retainedContracts)
            );
            return history;
        } catch (IOException failure) {
            throw new IllegalStateException("Iris generation history is unusable at " + root + ".", failure);
        }
    }

    private static GenerationHistory adoptLegacyGenerationHistory(
            Path dimensionRoot,
            String dimensionKey,
            long worldSeed
    ) throws IOException {
        Path legacyPack = GenerationHistoryPaths.forDimension(dimensionRoot).legacyPackRoot();
        requireSnapshotLoadable(legacyPack.toFile());
        String fingerprint = GenerationPackFingerprint.compute(
                legacyPack,
                GenerationPackFingerprint.CURRENT_VERSION
        );
        IrisData data = IrisData.openDatapackCompiler(legacyPack.toFile());
        try {
            IrisDimension dimension = data.getDimensionLoader().load(dimensionKey, false);
            if (dimension == null) {
                throw new IOException("Legacy Iris pack does not contain dimension " + dimensionKey + ".");
            }
            GenerationEpoch.DimensionContract dimensionContract = captureDimensionContract(data, dimension);
            GenerationRegistryContract registryContract = GenerationRegistryContractFactory.create(
                    data,
                    dimension,
                    fingerprint,
                    GenerationRegistryContractFactory.CustomBiomeAliasPolicy.RETAIN_LEGACY_ALIASES
            );
            return GenerationHistory.adoptLegacyPack(
                    dimensionRoot,
                    fingerprint,
                    worldSeed,
                    dimensionContract,
                    registryContract
            );
        } finally {
            data.close();
        }
    }

    private static IrisDimension requireHistoricalDimension(
            GenerationHistory history,
            File packRoot,
            String dimensionKey
    ) {
        return requireHistoricalDimension(history, IrisData.get(packRoot), dimensionKey);
    }

    private static IrisDimension requireHistoricalDimension(
            GenerationHistory history,
            IrisData data,
            String dimensionKey
    ) {
        GenerationEpoch epoch = history.activeEpoch();
        if (!epoch.dimensionContract().dimensionKey().equals(dimensionKey)) {
            throw new IllegalStateException("Configured dimension " + dimensionKey
                    + " does not match active generation history dimension "
                    + epoch.dimensionContract().dimensionKey() + ".");
        }
        IrisDimension dimension = data.getDimensionLoader().load(dimensionKey, false);
        if (dimension == null) {
            throw new IllegalStateException("Immutable Iris generation pack at " + data.getDataFolder()
                    + " does not contain dimension " + dimensionKey + ".");
        }
        GenerationEpoch.DimensionContract actualContract = GenerationEpochContractFactory.createForEpoch(
                dimension, dimensionTypeKey(data, dimension), epoch);
        if (!epoch.dimensionContract().equals(actualContract)) {
            throw new IllegalStateException("Immutable Iris generation pack dimension contract changed at "
                    + data.getDataFolder() + ".");
        }
        return dimension;
    }

    private record PendingSnapshot(GenerationHistory history, String dimensionKey,
                                   CompletableFuture<Void> contentReady, CompletableFuture<Void> ready) {
    }

    private static GenerationEpoch.DimensionContract captureDimensionContract(
            IrisData data,
            IrisDimension dimension
    ) {
        return GenerationEpochContractFactory.create(
                dimension,
                dimension.getLoadKey(),
                dimensionTypeKey(data, dimension)
        );
    }

    private static String dimensionTypeKey(IrisData data, IrisDimension dimension) {
        return IrisPlatforms.get()
                .registries()
                .generationRegistry()
                .dimensionTypeResourceKey(
                        data.getDataFolder().getName(),
                        dimension.getLoadKey(),
                        dimension.getDimensionTypeKey()
                );
    }

    private static File requireActivePack(GenerationHistory history) {
        try {
            return history.activePackRoot().toFile();
        } catch (IOException failure) {
            throw new IllegalStateException("Active Iris generation pack is unusable.", failure);
        }
    }

    private static long requireStoredWorldSeed(String worldName, File dimensionRoot) {
        Long configuredSeed = IrisWorlds.readBukkitWorldSeed(worldName);
        if (configuredSeed != null) {
            return configuredSeed;
        }
        try {
            return WorldReplacementSeed.readAuthoritativeSeed(dimensionRoot.toPath());
        } catch (IOException failure) {
            throw new IllegalStateException("Iris world " + worldName
                    + " has no authoritative generation seed.", failure);
        }
    }

    private record FreshValidation(
            List<File> packDirs,
            ServerConfigurator.PackContentSnapshot contentSnapshot,
            List<PackValidationResult> results,
            boolean stable
    ) {
    }
}
