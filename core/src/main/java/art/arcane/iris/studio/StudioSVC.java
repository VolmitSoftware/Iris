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

package art.arcane.iris.studio;

import art.arcane.iris.studio.jigsaw.JigsawStudioService;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.world.IrisStartupValidation;
import art.arcane.iris.world.IrisWorldStorage;
import art.arcane.iris.pack.datapack.ServerConfigurator;
import art.arcane.iris.pack.datapack.DatapackInstallResult;
import art.arcane.iris.world.lifecycle.LifecycleOperationCoordinator;
import art.arcane.iris.world.lifecycle.WorldLifecycleService;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.AtomicDirectoryPublisher;
import art.arcane.iris.pack.BrokenPackException;
import art.arcane.iris.pack.PackDirectoryResolver;
import art.arcane.iris.pack.PackDownloadExecution;
import art.arcane.iris.pack.PackDownloader;
import art.arcane.iris.pack.PackValidationRegistry;
import art.arcane.iris.pack.PackValidationResult;
import art.arcane.iris.pack.PackValidator;
import art.arcane.iris.studio.workspace.IrisProject;
import art.arcane.iris.studio.workspace.IrisPackageCompiler;
import art.arcane.iris.studio.workspace.IrisCodeWorkspace;
import art.arcane.iris.studio.workspace.IrisProjectCopier;
import art.arcane.iris.studio.object.StudioOpenCoordinator;
import art.arcane.iris.world.runtime.TransientWorldCleanupSupport;
import art.arcane.iris.world.runtime.WorldDeletionQueue;
import art.arcane.iris.studio.jigsaw.JigsawStudioActivation;
import art.arcane.iris.world.IrisToolbelt;
import art.arcane.iris.generation.cache.AtomicCache;
import art.arcane.iris.world.history.GenerationActivation;
import art.arcane.iris.world.history.GenerationEpoch;
import art.arcane.iris.world.history.GenerationEpochContractFactory;
import art.arcane.iris.world.history.GenerationHistory;
import art.arcane.iris.world.history.GenerationHistoryPaths;
import art.arcane.iris.world.history.GenerationPackFingerprint;
import art.arcane.iris.world.history.GenerationRegistryContract;
import art.arcane.iris.world.history.GenerationRegistryContractFactory;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import art.arcane.volmlib.util.exceptions.IrisException;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.json.JSONObject;
import art.arcane.iris.platform.bukkit.plugin.IrisService;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.iris.generation.concurrent.MultiBurst;
import art.arcane.iris.world.task.J;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import art.arcane.iris.localization.BukkitRuntimeMessages;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.PackDownloadMessages;
import art.arcane.volmlib.util.localization.MessageArgument;
public class StudioSVC implements IrisService {
    public static final String WORKSPACE_NAME = "packs";
    private static final long DOWNLOAD_SHUTDOWN_POLL_SECONDS = 15L;
    private static final Pattern PROJECT_NAME = Pattern.compile("[a-z0-9_-]+");
    private static final AtomicCache<Integer> counter = new AtomicCache<>();
    private final StudioTransitionQueue studioTransitions = new StudioTransitionQueue();
    private static final long STALE_TEMP_CLEANUP_TIMEOUT_SECONDS = 120L;
    private static volatile CompletableFuture<Void> staleTempCleanup = CompletableFuture.completedFuture(null);

    private final Object downloadAdmissionMonitor = new Object();
    private volatile IrisProject activeProject;
    private volatile CompletableFuture<StudioOpenCoordinator.StudioOpenResult> activeOpen;
    private PackDownloadExecution activeDownload;
    private boolean downloadAdmissionOpen;

    public static void gateDownloadsOnStaleTempCleanup(CompletableFuture<Void> cleanup) {
        staleTempCleanup = cleanup == null ? CompletableFuture.completedFuture(null) : cleanup;
    }

    private static void awaitStaleTempCleanup() {
        CompletableFuture<Void> cleanup = staleTempCleanup;
        if (cleanup.isDone()) {
            return;
        }

        try {
            cleanup.get(STALE_TEMP_CLEANUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            IrisLogging.reportError("Interrupted while waiting for the stale Iris temp sweep to finish.", interrupted);
        } catch (ExecutionException | TimeoutException failure) {
            IrisLogging.reportError("The stale Iris temp sweep did not finish; this pack import may race it.", failure);
        }
    }

    @Override
    public void onEnable() {
        synchronized (downloadAdmissionMonitor) {
            activeDownload = null;
            downloadAdmissionOpen = true;
        }
        MultiBurst.ioBurst.completeValueAsync(() -> {
            String configuredPack = IrisSettings.get().getGenerator().getDefaultWorldType();
            if (!PackDownloader.isPackPresent(getWorkspaceFolder(), configuredPack)) {
                IrisLogging.warn("Default pack '" + configuredPack
                        + "' is not installed. Install a built-in pack with /iris download pack=overworld or provide /iris download link=<zip-url>.");
            }
            return null;
        });
    }

    @Override
    public void onDisable() {
        quiesceDownloadsForShutdown();
        IrisLogging.debug("Studio Mode Active: Closing Projects");
        boolean stopping = IrisToolbelt.isServerStopping();
        LinkedHashSet<String> worldNamesToDelete = new LinkedHashSet<>(TransientWorldCleanupSupport.collectTransientStudioWorldNames(IrisWorldStorage.levelRoot()));

        if (activeProject != null) {
            PlatformChunkGenerator activeProvider = activeProject.getActiveProvider();
            if (activeProvider != null) {
                String activeWorldName = IrisWorldStorage.logicalName(
                        WorldIdentity.parse(activeProvider.getTarget().getWorld().identity()));
                if (activeWorldName != null && !activeWorldName.isBlank()) {
                    worldNamesToDelete.add(activeWorldName);
                }
            }
        }

        for (World i : Bukkit.getWorlds()) {
            if (!IrisToolbelt.isIrisWorld(i) || !IrisToolbelt.isStudio(i)) {
                continue;
            }

            worldNamesToDelete.add(IrisWorldStorage.logicalName(i));
            PlatformChunkGenerator generator = IrisToolbelt.access(i);
            if (!stopping) {
                destroyStudioWorld(i, generator);
                continue;
            }

            if (generator != null) {
                try {
                    generator.close();
                } catch (Throwable e) {
                    IrisLogging.reportError("Failed to close studio generator for \"" + i.getName() + "\" during shutdown.", e);
                }
            }
        }

        activeProject = null;

        try {
            art.arcane.iris.world.IrisCreator.removeTransientStudioWorldsFromBukkitYml();
        } catch (Throwable e) {
            IrisLogging.reportError("Failed to unregister transient studio worlds from bukkit.yml during shutdown.", e);
        }

        queueStudioWorldDeletionOnStartup(worldNamesToDelete);
    }

    public IrisDimension installIntoWorld(
            VolmitSender sender,
            IrisDimension dimension,
            File dimensionRoot,
            long worldSeed
    ) {
        return publishGenerationHistory(sender, dimension, dimensionRoot, worldSeed, false);
    }

    public IrisDimension replaceIntoWorld(
            VolmitSender sender,
            IrisDimension dimension,
            File dimensionRoot,
            long worldSeed
    ) {
        return publishGenerationHistory(sender, dimension, dimensionRoot, worldSeed, true);
    }

    public IrisDimension installIntoTransientWorld(
            VolmitSender sender,
            IrisDimension dimension,
            File dimensionRoot
    ) {
        return installIntoDirectory(sender, dimension, new File(dimensionRoot, "iris/pack"), false);
    }

    public IrisDimension replaceIntoPackDirectory(VolmitSender sender, IrisDimension dimension, File folder) {
        return installIntoDirectory(sender, dimension, folder, true);
    }

    private IrisDimension publishGenerationHistory(
            VolmitSender sender,
            IrisDimension dimension,
            File dimensionRoot,
            long worldSeed,
            boolean stageUpdate
    ) {
        if (J.isPrimaryThread()) {
            sender.sendMessage(IrisLanguage.text(
                    BukkitRuntimeMessages.STUDIO_S_V_C_PACK_COPY_REQUIRES_ASYNC_THREAD
            ));
            return null;
        }
        String dimensionKey = dimension.getLoadKey();
        try {
            Path source = resolveSafePackSource(dimension.getLoader().getDataFolder());
            GenerationCandidate candidate = captureGenerationCandidate(source, dimensionKey);
            Path root = Objects.requireNonNull(dimensionRoot, "dimensionRoot")
                    .toPath()
                    .toAbsolutePath()
                    .normalize();
            if (!stageUpdate && !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
                Path parent = Objects.requireNonNull(root.getParent(), "dimensionRoot parent");
                Files.createDirectories(parent);
                Files.createDirectory(root);
            }
            GenerationHistory history;
            long previousActivationId = 0L;
            boolean restartRequired = false;
            if (stageUpdate) {
                history = openOrAdoptGenerationHistory(root, dimensionKey, worldSeed);
                previousActivationId = history.activeActivation().activationId();
                GenerationActivation activation = history.stageUpdate(
                        source,
                        candidate.packFingerprint(),
                        candidate.dimensionContract(),
                        candidate.registryContract(),
                        IrisSettings.get().getGenerator().getGenerationTransitionWidthBlocks()
                );
                restartRequired = activation.activationId() != previousActivationId;
            } else {
                history = GenerationHistory.create(
                        root,
                        source,
                        candidate.packFingerprint(),
                        worldSeed,
                        candidate.dimensionContract(),
                        candidate.registryContract()
                );
            }
            Path installedPack = stageUpdate && history.pendingActivation().isPresent()
                    ? history.packRoot(history.pendingActivation().orElseThrow().activationId())
                    : history.activePackRoot();
            IrisDimension installed = loadInstalledDimension(installedPack, dimensionKey);
            if (restartRequired) {
                ServerConfigurator.restart("An Iris generation epoch update is pending activation.");
            }
            return installed;
        } catch (Throwable failure) {
            IrisLogging.reportError("Failed to publish generation history for dimension '"
                    + dimensionKey + "' into " + dimensionRoot.getPath(), failure);
            sender.sendMessage(IrisLanguage.text(
                    BukkitRuntimeMessages.STUDIO_S_V_C_PACK_INSTALL_FAILED,
                    MessageArgument.untrusted("dimension", dimensionKey),
                    MessageArgument.untrusted("error", errorDetail(failure))
            ));
            return null;
        }
    }

    private static GenerationHistory openOrAdoptGenerationHistory(
            Path dimensionRoot,
            String dimensionKey,
            long worldSeed
    ) throws IOException {
        Optional<GenerationHistory> current = GenerationHistory.openIfPresent(dimensionRoot, worldSeed);
        if (current.isPresent()) {
            return current.get();
        }
        Path legacyPack = GenerationHistoryPaths.forDimension(dimensionRoot).legacyPackRoot();
        PackValidationResult validation = validatePublishedPack(legacyPack);
        if (!validation.isLoadable()) {
            throw new BrokenPackException(legacyPack.toString(), validation.getBlockingErrors());
        }
        GenerationCandidate legacy = captureGenerationCandidate(
                legacyPack,
                dimensionKey,
                GenerationRegistryContractFactory.CustomBiomeAliasPolicy.RETAIN_LEGACY_ALIASES
        );
        return GenerationHistory.adoptLegacyPack(
                dimensionRoot,
                legacy.packFingerprint(),
                worldSeed,
                legacy.dimensionContract(),
                legacy.registryContract()
        );
    }

    private static GenerationCandidate captureGenerationCandidate(Path packRoot, String dimensionKey)
            throws IOException {
        return captureGenerationCandidate(
                packRoot,
                dimensionKey,
                GenerationRegistryContractFactory.CustomBiomeAliasPolicy.CONTENT_ADDRESSED_ONLY
        );
    }

    private static GenerationCandidate captureGenerationCandidate(
            Path packRoot,
            String dimensionKey,
            GenerationRegistryContractFactory.CustomBiomeAliasPolicy aliasPolicy
    ) throws IOException {
        String fingerprint = GenerationPackFingerprint.compute(
                packRoot,
                GenerationPackFingerprint.CURRENT_VERSION
        );
        IrisData data = IrisData.openDatapackCompiler(packRoot.toFile());
        try {
            IrisDimension dimension = data.getDimensionLoader().load(dimensionKey, false);
            if (dimension == null) {
                throw new IOException("Generation pack does not contain a loadable dimension '"
                        + dimensionKey + "'.");
            }
            String dimensionTypeKey = IrisPlatforms.get()
                    .registries()
                    .generationRegistry()
                    .dimensionTypeResourceKey(
                            data.getDataFolder().getName(),
                            dimension.getLoadKey(),
                            dimension.getDimensionTypeKey()
                    );
            GenerationEpoch.DimensionContract dimensionContract = GenerationEpochContractFactory.create(
                    dimension,
                    dimension.getLoadKey(),
                    dimensionTypeKey
            );
            GenerationRegistryContract registryContract = GenerationRegistryContractFactory.create(
                    data,
                    dimension,
                    fingerprint,
                    aliasPolicy
            );
            return new GenerationCandidate(fingerprint, dimensionContract, registryContract);
        } finally {
            data.close();
        }
    }

    private static IrisDimension loadInstalledDimension(Path packRoot, String dimensionKey) throws IOException {
        PackValidationResult validation = validatePublishedPack(packRoot);
        if (!validation.isLoadable()) {
            throw new BrokenPackException(packRoot.toString(), validation.getBlockingErrors());
        }
        IrisDimension installed = IrisData.get(packRoot.toFile()).getDimensionLoader().load(dimensionKey, false);
        if (installed == null) {
            throw new IOException("Immutable generation pack does not contain dimension '"
                    + dimensionKey + "'.");
        }
        return installed;
    }

    private IrisDimension installIntoDirectory(
            VolmitSender sender,
            IrisDimension dimension,
            File folder,
            boolean replaceExisting
    ) {
        if (J.isPrimaryThread()) {
            sender.sendMessage(IrisLanguage.text(
                    BukkitRuntimeMessages.STUDIO_S_V_C_PACK_COPY_REQUIRES_ASYNC_THREAD
            ));
            return null;
        }
        String dimensionKey = dimension.getLoadKey();
        Path source;
        try {
            source = resolveSafePackSource(dimension.getLoader().getDataFolder());
        } catch (IOException e) {
            IrisLogging.reportError("Failed to inspect source dimension pack '" + dimensionKey + "'.", e);
            sender.sendMessage(IrisLanguage.text(
                    BukkitRuntimeMessages.STUDIO_S_V_C_PACK_INSTALL_FAILED,
                    MessageArgument.untrusted("dimension", dimensionKey),
                    MessageArgument.untrusted("error", errorDetail(e))
            ));
            return null;
        }
        Path target = folder.toPath().toAbsolutePath().normalize();
        Path parent = target.getParent();
        Path stage = null;
        AtomicDirectoryPublisher.Publication publication = null;
        PackValidationRegistry.RootMutation validationMutation = null;
        IrisData previousData = IrisData.getLoaded(target.toFile()).orElse(null);
        IrisData createdData = null;
        boolean refreshedPreviousData = false;
        try {
            if (parent == null) {
                throw new IOException("World pack target has no parent: " + target);
            }
            Files.createDirectories(parent);
            requireSafePublicationTarget(target, replaceExisting);

            stage = Files.createTempDirectory(parent, ".pack.installing-");
            copyPackTree(source, stage);
            IrisData stagedData = IrisData.openDatapackCompiler(stage.toFile());
            try {
                IrisDimension stagedDimension = stagedData.getDimensionLoader().load(dimensionKey);
                if (stagedDimension == null) {
                    throw new IOException("Copied pack does not contain a loadable dimension '" + dimensionKey + "'.");
                }
            } finally {
                stagedData.close();
            }
            validationMutation = PackValidationRegistry.beginRootMutation(target);
            requireSafePublicationTarget(target, replaceExisting);
            publication = AtomicDirectoryPublisher.publish(stage, target);
            stage = null;
            String copiedFingerprint = ServerConfigurator.computePackTreeFingerprint(target.toFile());
            PackValidationResult publishedValidation =
                    validatePublishedPack(target, source, copiedFingerprint, validationMutation);
            if (!publishedValidation.isLoadable()) {
                throw new BrokenPackException(
                        target.toString(),
                        publishedValidation.getBlockingErrors());
            }

            IrisData installedData;
            // Live engines only ever attach to detached openRuntime loaders, never to the
            // dataLoaders-cached previousData — so the old previousData.getEngines() test was
            // always empty and a running world's pack could be swapped with no restart.
            boolean activeRuntime = IrisData.hasActiveEngines(target.toFile());
            if (previousData == null) {
                createdData = IrisData.get(target.toFile());
                installedData = createdData;
            } else if (!activeRuntime) {
                previousData.hotloaded();
                refreshedPreviousData = true;
                installedData = previousData;
            } else {
                installedData = previousData;
            }
            IrisDimension installedDimension = activeRuntime
                    ? dimension
                    : installedData.getDimensionLoader().load(dimensionKey);
            if (installedDimension == null) {
                throw new IOException("Published pack does not contain a loadable dimension '" + dimensionKey + "'.");
            }
            publication.commit();
            validationMutation.commit();
            try {
                publication.cleanupBackup();
            } catch (IOException cleanupFailure) {
                IrisLogging.warn("World pack was committed but its backup could not be removed: "
                        + cleanupFailure.getMessage());
            }
            if (activeRuntime) {
                ServerConfigurator.restart("An active Iris world pack was replaced.");
            }
            return installedDimension;
        } catch (Throwable e) {
            rollbackFailedPublication(createdData, publication, e);
            if (publication != null) {
                invalidatePackValidation(target);
            }
            if (refreshedPreviousData) {
                try {
                    previousData.hotloaded();
                } catch (Throwable restoreFailure) {
                    e.addSuppressed(restoreFailure);
                }
            }
            IrisLogging.reportError("Failed to install dimension pack '" + dimensionKey + "' into " + folder.getPath(), e);
            sender.sendMessage(IrisLanguage.text(
                    BukkitRuntimeMessages.STUDIO_S_V_C_PACK_INSTALL_FAILED,
                    MessageArgument.untrusted("dimension", dimensionKey),
                    MessageArgument.untrusted("error", errorDetail(e))
            ));
            return null;
        } finally {
            if (validationMutation != null) {
                validationMutation.close();
            }
            if (stage != null) {
                try {
                    AtomicDirectoryPublisher.deleteTree(stage);
                } catch (IOException cleanupFailure) {
                    IrisLogging.reportError("Failed to clean staged world pack " + stage, cleanupFailure);
                }
            }
        }
    }

    static void invalidatePackValidation(Path packRoot) {
        PackValidationRegistry.remove(packRoot);
    }

    static void requireSafePublicationTarget(Path target, boolean replaceExisting) throws IOException {
        if (Files.isSymbolicLink(target)) {
            throw new IOException("World pack target is a symbolic link: " + target);
        }
        if (!replaceExisting && Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileAlreadyExistsException(target.toString());
        }
    }

    static PackValidationResult validatePublishedPack(Path packRoot) {
        try (PackValidationRegistry.RootMutation mutation =
                     PackValidationRegistry.beginRootMutation(packRoot)) {
            PackValidationResult result = PackValidator.validate(packRoot.toFile());
            mutation.stage(result);
            mutation.commit();
            return PackValidationRegistry.requireLoadable(packRoot);
        }
    }

    static PackValidationResult validatePublishedPack(
            Path packRoot,
            Path validatedSource,
            String copiedContentFingerprint
    ) {
        try (PackValidationRegistry.RootMutation mutation =
                     PackValidationRegistry.beginRootMutation(packRoot)) {
            PackValidationResult result = validatePublishedPack(
                    packRoot,
                    validatedSource,
                    copiedContentFingerprint,
                    mutation);
            mutation.commit();
            return PackValidationRegistry.requireLoadable(packRoot);
        }
    }

    private static PackValidationResult validatePublishedPack(
            Path packRoot,
            Path validatedSource,
            String copiedContentFingerprint,
            PackValidationRegistry.RootMutation mutation
    ) {
        PackValidationResult result = mutation.stageMatchingCopy(
                validatedSource,
                copiedContentFingerprint);
        if (result == null) {
            result = PackValidator.validate(packRoot.toFile());
            mutation.stage(result);
        }
        return result;
    }

    static void rollbackFailedPublication(
            IrisData createdData,
            AtomicDirectoryPublisher.Publication publication,
            Throwable failure
    ) {
        if (createdData != null) {
            try {
                createdData.close();
            } catch (Throwable cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
        if (publication != null) {
            try {
                publication.close();
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
    }

    static Path resolveSafePackSource(File sourceFolder) throws IOException {
        Path source = sourceFolder.toPath().toAbsolutePath().normalize().toRealPath();
        if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(source)) {
            throw new IOException("Source pack is missing or unsafe: " + sourceFolder);
        }
        return source;
    }

    public IrisDimension installInto(VolmitSender sender, String type, File folder) {
        if (J.isPrimaryThread()) {
            sender.sendMessage("Iris refused to download or copy a pack on the Bukkit primary thread.");
            return null;
        }
        sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.STUDIO_S_V_C_LOOKING_PACKAGE, MessageArgument.untrusted("type", String.valueOf(type))));
        IrisDimension dimension = IrisData.loadAnyDimension(type, null);
        if (dimension == null) {
            sender.sendMessage("Iris cannot repair world pack '" + type
                    + "' because no installed source contains it.");
            return null;
        }
        return replaceIntoPackDirectory(sender, dimension, folder);
    }

    public void downloadBuiltIn(VolmitSender sender, String key) {
        if (!PackDownloader.isBuiltInPack(key)) {
            sender.sendMessage(IrisLanguage.text(PackDownloadMessages.INVALID_BUILT_IN));
            return;
        }
        PackDownloadProgressReporter reporter = new PackDownloadProgressReporter(sender, key);
        runPackMutation(sender, LifecycleOperationCoordinator.OperationKind.PACK_DOWNLOAD, key, reporter, cancellation -> {
            PackDownloader.PackInstallResult result = downloadBuiltInLocked(key, cancellation, reporter);
            if (result == null) {
                reporter.fail(null);
                return;
            }
            retainPackRestartRequirement(result);
            reporter.succeed(result);
        }, "Failed to download built-in Iris pack '" + key + "'.");
    }

    public void downloadUrl(VolmitSender sender, String url) {
        if (!PackDownloader.isDirectZipUrl(url)) {
            sender.sendMessage(IrisLanguage.text(PackDownloadMessages.INVALID_URL));
            return;
        }
        PackDownloadProgressReporter reporter = new PackDownloadProgressReporter(
                sender,
                IrisLanguage.text(PackDownloadMessages.PROGRESS_SOURCE_REMOTE),
                url
        );
        runPackMutation(sender, LifecycleOperationCoordinator.OperationKind.PACK_DOWNLOAD, "remote-zip", reporter, cancellation -> {
            PackDownloader.PackInstallResult result = PackDownloader.downloadUrl(
                    getWorkspaceFolder(),
                    url,
                    false,
                    reporter::detail,
                    cancellation,
                    reporter
            );
            if (result == null) {
                reporter.fail(null);
                return;
            }
            retainPackRestartRequirement(result);
            reporter.succeed(result);
        }, "Failed to download Iris pack.");
    }

    private PackDownloader.PackInstallResult downloadBuiltInLocked(
            String expectedKey,
            PackDownloader.DownloadCancellation cancellation,
            PackDownloadProgressReporter reporter
    ) throws IOException {
        if (PackDownloader.isBuiltInPackPresent(getWorkspaceFolder(), expectedKey)) {
            return new PackDownloader.PackInstallResult(expectedKey, false, false);
        }

        return PackDownloader.downloadBuiltIn(
                getWorkspaceFolder(),
                expectedKey,
                false,
                reporter::detail,
                cancellation,
                reporter
        );
    }

    public boolean isProjectOpen() {
        return activeProject != null && activeProject.isOpen();
    }

    public CompletableFuture<Boolean> teleportToActiveProject(Player player) {
        Player target = Objects.requireNonNull(player, "Studio teleport player");
        return studioTransitions.submit(() -> {
            IrisProject project = activeProject;
            if (project == null || !project.isOpen()) {
                return CompletableFuture.failedFuture(new IllegalStateException(
                        "No active Studio project is available for teleport."));
            }
            return StudioOpenCoordinator.get().teleportPlayerToProject(project, target);
        });
    }

    public void open(VolmitSender sender, String dimm) {
        open(sender, 1337, dimm);
    }

    public void open(VolmitSender sender, long seed, String dimm) {
        open(sender, seed, dimm, false);
    }

    public void open(VolmitSender sender, long seed, String dimm, boolean force) {
        try {
            StudioOpenCoordinator.StudioOpenKind openKind = force
                    ? StudioOpenCoordinator.StudioOpenKind.FORCED_STANDARD
                    : StudioOpenCoordinator.StudioOpenKind.STANDARD;
            open(sender, seed, dimm, openKind, (w) -> {
            });
        } catch (Exception e) {
            IrisLogging.reportError("Failed to open studio world \"" + dimm + "\".", e);
            sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.STUDIO_S_V_C_FAILED_OPEN_STUDIO_WORLD, MessageArgument.untrusted("error", String.valueOf(e.getMessage()))));
        }
    }

    private static BrokenPackException reportPackAdmissionFailure(
            VolmitSender sender,
            String dimm,
            StudioOpenCoordinator.StudioOpenKind openKind
    ) {
        boolean force = openKind == StudioOpenCoordinator.StudioOpenKind.FORCED_STANDARD;
        Optional<String> unforcedStartupDenial = ServerConfigurator.worldCreationDenialReason(false);
        Optional<String> startupDenial = ServerConfigurator.worldCreationDenialReason(force);
        IrisDimension dimension = IrisToolbelt.getDimension(dimm);
        String packName = dimension == null || dimension.getLoader() == null
                ? dimm
                : dimension.getLoader().getDataFolder().getName();
        PackValidationResult validation = PackValidationRegistry.get(packName);
        BrokenPackException failure = resolvePackAdmissionFailure(
                packName, startupDenial, validation);
        if (failure == null) {
            if (force && unforcedStartupDenial.isPresent()) {
                sender.sendMessage("Force-opening Studio with the currently loaded registry state. The open may fail; restart remains required.");
            }
            return null;
        }
        if (startupDenial.isPresent()) {
            sender.sendMessage(startupDenial.get());
            return failure;
        }
        sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.STUDIO_S_V_C_CANNOT_OPEN_STUDIO_PACK_HAS_BLOCKING_ERRORS, MessageArgument.untrusted("dimm", String.valueOf(dimm))));
        for (String reason : failure.getReasons()) {
            sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.STUDIO_S_V_C_MESSAGE, MessageArgument.untrusted("reason", String.valueOf(reason))));
        }
        sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.STUDIO_S_V_C_FIX_PACK_RUN_IRIS_PACK_VALIDATE_REVALIDATE, MessageArgument.untrusted("dimm", String.valueOf(packName))));
        return failure;
    }

    static void retainPackRestartRequirement(PackDownloader.PackInstallResult result) {
        if (result != null && result.restartRequired()) {
            ServerConfigurator.requireWorldCreationRestart();
        }
    }

    static BrokenPackException resolvePackAdmissionFailure(
            String packName,
            Optional<String> startupDenial,
            PackValidationResult validation
    ) {
        if (startupDenial.isPresent()) {
            return new BrokenPackException(packName, List.of(startupDenial.get()));
        }
        if (validation != null && validation.isLoadable()) {
            return null;
        }
        List<String> failures = validation == null
                ? List.of("Required pack validation has not completed. Studio creation fails closed until validation succeeds.")
                : validation.getBlockingErrors();
        return new BrokenPackException(packName, failures);
    }

    public void open(VolmitSender sender, long seed, String dimm, Consumer<World> onDone) throws IrisException {
        open(sender, seed, dimm, StudioOpenCoordinator.StudioOpenKind.STANDARD, onDone);
    }

    public void open(
            VolmitSender sender,
            long seed,
            String dimm,
            StudioOpenCoordinator.StudioOpenKind openKind,
            Consumer<World> onDone
    ) throws IrisException {
        long requestedAtNanos = System.nanoTime();
        if (reportPackAdmissionFailure(sender, dimm, openKind) != null) {
            return;
        }
        StudioOpenCoordinator.StudioOpenKind requiredOpenKind = Objects.requireNonNull(
                openKind,
                "Studio open kind");
        studioTransitions.submit(() -> replaceActiveProject(
                        sender,
                        seed,
                        dimm,
                        requiredOpenKind,
                        onDone,
                        requestedAtNanos))
                .whenComplete((ignored, throwable) -> {
                    if (throwable == null) {
                        return;
                    }
                    IrisLogging.reportError("Failed to replace the active studio project with \"" + dimm + "\".", throwable);
                    J.s(() -> sender.sendMessage(IrisLanguage.text(
                            BukkitRuntimeMessages.STUDIO_S_V_C_FAILED_OPEN_STUDIO_WORLD_2,
                            MessageArgument.untrusted("error", String.valueOf(errorDetail(throwable))))));
                });
    }

    public CompletableFuture<StudioOpenCoordinator.StudioOpenResult> openTracked(
            VolmitSender sender,
            long seed,
            String dimension,
            StudioOpenCoordinator.StudioOpenKind openKind,
            Runnable beforeOpen,
            Consumer<World> onDone
    ) {
        long requestedAtNanos = System.nanoTime();
        BrokenPackException failure = reportPackAdmissionFailure(sender, dimension, openKind);
        if (failure != null) {
            return CompletableFuture.failedFuture(failure);
        }
        return studioTransitions.submit(() -> replaceActiveProjectTracked(
                sender,
                seed,
                dimension,
                Objects.requireNonNull(openKind, "Studio open kind"),
                Objects.requireNonNull(beforeOpen, "Studio before-open callback"),
                Objects.requireNonNull(onDone, "Studio open completion callback"),
                requestedAtNanos));
    }

    private CompletableFuture<StudioOpenCoordinator.StudioOpenResult> replaceActiveProjectTracked(
            VolmitSender sender,
            long seed,
            String dimension,
            StudioOpenCoordinator.StudioOpenKind openKind,
            Runnable beforeOpen,
            Consumer<World> onDone,
            long requestedAtNanos
    ) {
        return closeActiveProject().thenCompose(closeResult -> {
            if (closeResult == null) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("Studio close completed without a result."));
            }
            if (closeResult.failureCause() != null) {
                return CompletableFuture.failedFuture(closeResult.failureCause());
            }
            beforeOpen.run();
            return beginStudioOpenTracked(
                    sender,
                    seed,
                    dimension,
                    openKind,
                    onDone,
                    requestedAtNanos);
        });
    }

    private CompletableFuture<StudioOpenCoordinator.StudioOpenResult> beginStudioOpenTracked(
            VolmitSender sender,
            long seed,
            String dimension,
            StudioOpenCoordinator.StudioOpenKind openKind,
            Consumer<World> onDone,
            long requestedAtNanos
    ) {
        IrisProject project = new IrisProject(new File(getWorkspaceFolder(), dimension));
        activeProject = project;
        CompletableFuture<StudioOpenCoordinator.StudioOpenResult> opening;
        try {
            opening = project.open(sender, seed, openKind, onDone, requestedAtNanos);
        } catch (IrisException exception) {
            if (activeProject == project) {
                activeProject = null;
            }
            return CompletableFuture.failedFuture(exception);
        }

        activeOpen = opening;
        return opening.thenApply(result -> Objects.requireNonNull(
                        result,
                        "Studio open completed without a result."))
                .whenComplete((result, throwable) -> {
                    if (activeOpen == opening) {
                        activeOpen = null;
                    }
                    if (throwable != null && activeProject == project && !project.isOpen()) {
                        activeProject = null;
                    }
                });
    }

    private CompletableFuture<Void> replaceActiveProject(
            VolmitSender sender,
            long seed,
            String dimension,
            StudioOpenCoordinator.StudioOpenKind openKind,
            Consumer<World> onDone,
            long requestedAtNanos
    ) {
        return closeActiveProjectForReplacement(sender).handle((closeResult, closeThrowable) -> {
            if (closeThrowable != null) {
                IrisLogging.reportError("Failed while closing an existing studio project before opening \"" + dimension + "\".", closeThrowable);
                J.s(() -> sender.sendMessage(IrisLanguage.text(
                        BukkitRuntimeMessages.STUDIO_S_V_C_FAILED_CLOSE_EXISTING_STUDIO_PROJECT,
                        MessageArgument.untrusted("error", String.valueOf(errorDetail(closeThrowable))))));
                return false;
            }
            if (closeResult == null) {
                IllegalStateException failure = new IllegalStateException("Studio close completed without a result.");
                IrisLogging.reportError("Failed while closing an existing studio project before opening \"" + dimension + "\".", failure);
                J.s(() -> sender.sendMessage(IrisLanguage.text(
                        BukkitRuntimeMessages.STUDIO_S_V_C_FAILED_CLOSE_EXISTING_STUDIO_PROJECT_2,
                        MessageArgument.untrusted("error", String.valueOf(errorDetail(failure))))));
                return false;
            }
            if (closeResult.failureCause() != null) {
                Throwable failure = closeResult.failureCause();
                IrisLogging.reportError("Failed while closing an existing studio project before opening \"" + dimension + "\".", failure);
                J.s(() -> sender.sendMessage(IrisLanguage.text(
                        BukkitRuntimeMessages.STUDIO_S_V_C_FAILED_CLOSE_EXISTING_STUDIO_PROJECT_2,
                        MessageArgument.untrusted("error", String.valueOf(errorDetail(failure))))));
                return false;
            }
            return true;
        }).thenCompose(closed -> closed
                ? beginStudioOpen(
                        sender,
                        seed,
                        dimension,
                        openKind,
                        onDone,
                        requestedAtNanos)
                : CompletableFuture.completedFuture(null));
    }

    private CompletableFuture<Void> beginStudioOpen(
            VolmitSender sender,
            long seed,
            String dimension,
            StudioOpenCoordinator.StudioOpenKind openKind,
            Consumer<World> onDone,
            long requestedAtNanos
    ) {
        IrisProject project = new IrisProject(new File(getWorkspaceFolder(), dimension));
        activeProject = project;
        CompletableFuture<StudioOpenCoordinator.StudioOpenResult> opening;
        try {
            opening = project.open(
                    sender,
                    seed,
                    openKind,
                    onDone,
                    requestedAtNanos);
        } catch (IrisException e) {
            if (activeProject == project) {
                activeProject = null;
            }
            J.s(() -> sender.sendMessage(IrisLanguage.text(
                    BukkitRuntimeMessages.STUDIO_S_V_C_FAILED_OPEN_STUDIO_WORLD_2,
                    MessageArgument.untrusted("error", String.valueOf(errorDetail(e))))));
            return CompletableFuture.completedFuture(null);
        }

        activeOpen = opening;
        return opening.handle((result, throwable) -> {
            if (activeOpen == opening) {
                activeOpen = null;
            }
            if (throwable != null && activeProject == project && !project.isOpen()) {
                activeProject = null;
            }
            return null;
        });
    }

    public void openVSCode(VolmitSender sender, String dim) {
        new IrisCodeWorkspace(new IrisProject(new File(getWorkspaceFolder(), dim))).openVSCode(sender);
    }

    public File getWorkspaceFolder(String... sub) {
        return art.arcane.iris.platform.bukkit.BukkitPlatform.volmitPlugin().getDataFolderList(WORKSPACE_NAME, sub);
    }

    public File getWorkspaceFile(String... sub) {
        return art.arcane.iris.platform.bukkit.BukkitPlatform.volmitPlugin().getDataFileList(WORKSPACE_NAME, sub);
    }

    public CompletableFuture<StudioOpenCoordinator.StudioCloseResult> close() {
        return studioTransitions.submit(this::closeActiveProject);
    }

    CompletableFuture<StudioOpenCoordinator.StudioCloseResult> closeActiveProjectForReplacement(
            VolmitSender sender
    ) {
        IrisProject project = activeProject;
        if (project == null) {
            return closeActiveProject();
        }
        JigsawStudioActivation.Request request = JigsawStudioActivation.getRequest(project.getName());
        if (request == null || !sender.isPlayer()) {
            return closeActiveProject();
        }
        UUID ownerId = sender.player().getUniqueId();
        return JigsawStudioService.get()
                .awaitCloseForReplacement(request.requestId(), ownerId)
                .thenCompose(ignored -> {
                    if (activeProject != project) {
                        return CompletableFuture.failedFuture(new IllegalStateException(
                                "The active Studio project changed while replacement was waiting to close."));
                    }
                    return closeActiveProject();
                });
    }

    private CompletableFuture<StudioOpenCoordinator.StudioCloseResult> closeActiveProject() {
        IrisProject project = activeProject;
        if (project == null) {
            return CompletableFuture.completedFuture(new StudioOpenCoordinator.StudioCloseResult(
                    null,
                    true,
                    true,
                    false,
                    null
            ));
        }

        JigsawStudioActivation.Request jigsawRequest = JigsawStudioActivation.getRequest(project.getName());
        if (jigsawRequest != null) {
            String protectionFailure = JigsawStudioService.get()
                    .closeProtectionFailure(jigsawRequest.requestId());
            if (protectionFailure != null) {
                return CompletableFuture.failedFuture(new IllegalStateException(protectionFailure));
            }
        }

        IrisLogging.debug("Closing Active Project");
        CompletableFuture<StudioOpenCoordinator.StudioCloseResult> closing = project.close();
        return closing.whenComplete((result, throwable) -> {
            if (throwable == null
                    && result != null
                    && result.failureCause() == null
                    && activeProject == project) {
                activeProject = null;
            }
        });
    }

    private void destroyStudioWorld(World world, PlatformChunkGenerator generator) {
        IrisToolbelt.beginWorldMaintenance(world, "studio-disable", true);
        try {
            IrisToolbelt.evacuate(world);
        } catch (Throwable e) {
            IrisLogging.reportError("Failed to evacuate studio world \"" + world.getName() + "\" during shutdown cleanup.", e);
        }
        try {
            WorldLifecycleService.get().unloadAsync(world, false)
                    .thenCompose(unloaded -> {
                        if (!Boolean.TRUE.equals(unloaded) || generator == null) {
                            return CompletableFuture.completedFuture(Boolean.TRUE.equals(unloaded));
                        }
                        return generator.closeAsync().thenApply(ignored -> true);
                    })
                    .whenComplete((unloaded, throwable) -> {
                        IrisToolbelt.endWorldMaintenance(world, "studio-disable", true);
                        if (throwable != null) {
                            IrisLogging.reportError("Failed to unload studio world \"" + world.getName()
                                    + "\" during disable cleanup; startup deletion remains queued.", throwable);
                        } else if (!Boolean.TRUE.equals(unloaded)) {
                            IrisLogging.warn("Studio world \"" + world.getName()
                                    + "\" remained loaded during disable cleanup; startup deletion remains queued.");
                        }
                    });
        } catch (Throwable e) {
            IrisToolbelt.endWorldMaintenance(world, "studio-disable", true);
            IrisLogging.reportError("Failed to unload studio world \"" + world.getName() + "\" during shutdown cleanup.", e);
        }
    }

    private void queueStudioWorldDeletionOnStartup(LinkedHashSet<String> worldNamesToDelete) {
        if (worldNamesToDelete.isEmpty()) {
            return;
        }

        LinkedHashSet<String> normalizedNames = new LinkedHashSet<>();
        for (String worldName : worldNamesToDelete) {
            String baseWorldName = TransientWorldCleanupSupport.transientStudioBaseWorldName(worldName);
            if (baseWorldName != null) {
                normalizedNames.add(baseWorldName);
                continue;
            }

            if (worldName != null && !worldName.isBlank()) {
                normalizedNames.add(worldName);
            }
        }

        if (normalizedNames.isEmpty()) {
            return;
        }

        try {
            IrisServices.get(WorldDeletionQueue.class).queueFamilyForStartupDeletion(List.copyOf(normalizedNames));
        } catch (IOException e) {
            IrisLogging.reportError("Failed to queue studio world deletion on startup.", e);
        }
    }

    public File compilePackage(VolmitSender sender, String d, boolean obfuscate, boolean minify) {
        return new IrisPackageCompiler(new IrisProject(new File(getWorkspaceFolder(), d))).compilePackage(sender, obfuscate, minify);
    }

    private void createFrom(File sourcePack, String sourceDimensionKey, String newName) throws IOException {
        if (J.isPrimaryThread()) {
            throw new IOException("Studio project copying cannot run on the Bukkit primary thread.");
        }
        String sourceKey = normalizeProjectName(sourceDimensionKey);
        String targetName = normalizeProjectName(newName);
        File workspace = requireSafeWorkspace(getWorkspaceFolder());
        File newPack = new File(workspace, targetName);
        IrisProjectCopier.copyProject(sourcePack, workspace, sourceKey, targetName);

        try {
            IrisProject project = new IrisProject(newPack);
            JSONObject workspaceConfiguration = new IrisCodeWorkspace(project).createCodeWorkspaceConfig();
            writeWorkspaceAtomically(newPack.toPath(), targetName, workspaceConfiguration.toString(0));
        } catch (Throwable e) {
            try {
                AtomicDirectoryPublisher.deleteTree(newPack.toPath());
            } catch (IOException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            if (e instanceof Error error) {
                throw error;
            }
            throw new IOException("Failed to create the editor workspace for project '" + targetName + "'.", e);
        }
    }

    public void create(VolmitSender sender, String s, String downloadable) {
        Runnable work = () -> createProject(sender, s, downloadable, null);
        runOffPrimaryThread(work);
    }

    public void create(VolmitSender sender, String name, IrisDimension template) {
        IrisData loader = template == null ? null : template.getLoader();
        File sourcePack = loader == null ? null : loader.getDataFolder();
        String sourceKey = template == null ? null : template.getLoadKey();
        Runnable work = () -> createProject(sender, name, sourceKey, sourcePack);
        runOffPrimaryThread(work);
    }

    public void create(VolmitSender sender, String s) {
        Runnable work = () -> createProject(sender, s, null, null);
        runOffPrimaryThread(work);
    }

    private void createProject(VolmitSender sender, String requestedName, String requestedTemplate, File selectedTemplatePack) {
        Optional<String> startupDenial = IrisStartupValidation.denialReason();
        if (startupDenial.isPresent()) {
            sender.sendMessage("Studio project creation refused: " + startupDenial.get());
            return;
        }
        String normalizedName;
        String templateName;
        File workspace;
        try {
            normalizedName = normalizeProjectName(requestedName);
            templateName = requestedTemplate == null ? null : normalizeProjectName(requestedTemplate);
            workspace = requireSafeWorkspace(getWorkspaceFolder());
        } catch (IOException e) {
            sender.sendMessage("Studio project creation refused: " + errorDetail(e));
            return;
        }

        LifecycleOperationCoordinator.Lease lease;
        try {
            lease = LifecycleOperationCoordinator.get().acquire(
                    LifecycleOperationCoordinator.Domain.PACK_MUTATION,
                    LifecycleOperationCoordinator.OperationKind.PACK_CREATE,
                    normalizedName
            );
        } catch (LifecycleOperationCoordinator.BusyException e) {
            sendBusy(sender, e);
            return;
        }

        String projectName = normalizedName;
        File createdPack = null;
        boolean projectPublished = false;
        CreationOutcome outcome = CreationOutcome.FAILED;
        try {
            if ("studio".equals(projectName)) {
                projectName = nextAvailableProjectName(workspace, projectName);
            }

            File newPack = new File(workspace, projectName);
            createdPack = newPack;
            if (Files.exists(newPack.toPath(), LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(newPack.toPath())) {
                sender.sendMessage("Studio project '" + projectName + "' already exists; nothing was changed.");
                return;
            }

            if (templateName == null) {
                createStarterProject(workspace, projectName);
            } else {
                File importPack = selectedTemplatePack == null ? new File(workspace, templateName) : selectedTemplatePack;
                if (!hasLoadableDimensionFile(importPack, templateName)) {
                    sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.STUDIO_S_V_C_COULDN_T_FIND_PACK_CREATE_NEW_DIMENSION_FROM));
                    sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.STUDIO_S_V_C_MISSING_IMPORTED_DIMENSION_FILE));
                    return;
                }
                PackValidationRegistry.requireLoadable(importPack.getName());

                sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.STUDIO_S_V_C_IMPORTING_INTO_NEW_PROJECT, MessageArgument.untrusted("downloadable", String.valueOf(templateName)), MessageArgument.untrusted("s", String.valueOf(projectName))));
                createFrom(importPack, templateName, projectName);
            }
            projectPublished = true;

            PackValidationResult createdValidation = PackValidator.validate(newPack);
            PackValidationRegistry.publish(createdValidation);
            if (!createdValidation.isLoadable()) {
                rollbackCreatedProject(sender, newPack,
                        "Studio project validation failed; the new project was rolled back.");
                for (String reason : createdValidation.getBlockingErrors()) {
                    sender.sendMessage(reason);
                }
                return;
            }

            DatapackInstallResult installResult = ServerConfigurator.installDataPacksIfChanged(true);
            CreationOutcome installOutcome = switch (installResult.status()) {
                case FAILED -> CreationOutcome.FAILED;
                case RESTART_REQUIRED -> CreationOutcome.RESTART;
                case READY, UNCHANGED -> CreationOutcome.OPEN;
            };
            if (installOutcome == CreationOutcome.FAILED) {
                rollbackCreatedProject(sender, newPack, "Datapack installation failed; the new project was rolled back.");
                return;
            }

            sender.sendMessage("Created studio project '" + projectName + "' at " + newPack.getAbsolutePath() + ".");
            if (installOutcome == CreationOutcome.RESTART) {
                sender.sendMessage("The project is complete, but Iris must restart before opening it. After restart, run /iris studio open " + projectName + ".");
            }
            outcome = installOutcome;
        } catch (Throwable e) {
            if (projectPublished && createdPack != null) {
                rollbackCreatedProject(sender, createdPack, "Studio project creation failed; the new project was rolled back.");
            }
            IrisLogging.reportError("Failed to create studio project '" + projectName + "'.", e);
            sender.sendMessage("Studio project creation failed: " + errorDetail(e));
        } finally {
            closeLease(lease);
        }

        if (outcome == CreationOutcome.OPEN) {
            String completedProjectName = projectName;
            LifecycleOperationCoordinator.get().whenIdle(() -> open(sender, completedProjectName));
        }
    }

    private void runPackMutation(
            VolmitSender sender,
            LifecycleOperationCoordinator.OperationKind operationKind,
            String target,
            PackDownloadProgressReporter reporter,
            PackMutation mutation,
            String failureMessage
    ) {
        String operationTarget = target == null || target.isBlank() ? "unspecified-pack" : target.trim();
        PackDownloadExecution execution;
        synchronized (downloadAdmissionMonitor) {
            if (!downloadAdmissionOpen) {
                sender.sendMessage(IrisLanguage.text(PackDownloadMessages.SHUTTING_DOWN));
                return;
            }

            LifecycleOperationCoordinator.Lease lease;
            try {
                lease = LifecycleOperationCoordinator.get().acquire(
                        LifecycleOperationCoordinator.Domain.PACK_MUTATION,
                        operationKind,
                        operationTarget
                );
            } catch (LifecycleOperationCoordinator.BusyException e) {
                sendBusy(sender, e);
                return;
            }

            execution = new PackDownloadExecution(
                    lease,
                    cancellation -> {
                        awaitStaleTempCleanup();
                        executePackMutation(mutation, reporter, failureMessage, cancellation);
                    }
            );
            PackDownloadExecution trackedExecution = execution;
            execution.onCompletion(() -> {
                clearActiveDownload(trackedExecution);
                reporter.executionComplete();
            });
            activeDownload = execution;
        }

        try {
            reporter.start();
            Future<?> future = MultiBurst.ioBurst.submit(execution);
            execution.bind(future);
        } catch (Throwable e) {
            try {
                IrisLogging.reportError(failureMessage, e);
                reporter.fail(e);
            } catch (Throwable reportingFailure) {
                IrisLogging.reportError("Failed to report an Iris pack download startup failure.", reportingFailure);
            } finally {
                execution.cancel();
            }
        }
    }

    private void executePackMutation(
            PackMutation mutation,
            PackDownloadProgressReporter reporter,
            String failureMessage,
            PackDownloader.DownloadCancellation cancellation
    ) throws PackDownloader.PackDownloadCancelledException {
        try {
            mutation.run(cancellation);
        } catch (PackDownloader.PackDownloadCancelledException e) {
            reporter.cancel();
            throw e;
        } catch (PackDownloader.PackDownloadBusyException e) {
            reporter.fail(e);
            return;
        } catch (Throwable e) {
            IrisLogging.reportError(failureMessage, e);
            reporter.fail(e);
        }
    }

    public void quiesceDownloadsForShutdown() {
        PackDownloadExecution execution;
        synchronized (downloadAdmissionMonitor) {
            downloadAdmissionOpen = false;
            execution = activeDownload;
        }
        if (execution == null) {
            return;
        }

        execution.cancel();
        boolean interrupted = false;
        boolean warned = false;
        while (!execution.isComplete()) {
            try {
                if (!execution.await(DOWNLOAD_SHUTDOWN_POLL_SECONDS, TimeUnit.SECONDS) && !warned) {
                    warned = true;
                    IrisLogging.warn(execution.isPublishing()
                            ? "Waiting for atomic pack publication to finish before Iris shutdown."
                            : "Waiting for the active pack download to cancel before Iris shutdown.");
                }
            } catch (InterruptedException e) {
                interrupted = true;
                execution.cancel();
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void clearActiveDownload(PackDownloadExecution execution) {
        synchronized (downloadAdmissionMonitor) {
            if (activeDownload == execution) {
                activeDownload = null;
            }
        }
    }

    private void runOffPrimaryThread(Runnable work) {
        if (J.isPrimaryThread()) {
            J.a(work);
            return;
        }
        work.run();
    }

    static String normalizeProjectName(String value) throws IOException {
        if (value == null) {
            throw new IOException("Project name cannot be empty.");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > 64 || !PROJECT_NAME.matcher(normalized).matches()) {
            throw new IOException("Invalid project name '" + value + "' (allowed: lowercase a-z, 0-9, _ and -).");
        }
        return normalized;
    }

    static File requireSafeWorkspace(File workspaceFolder) throws IOException {
        if (workspaceFolder == null) {
            throw new IOException("Pack workspace is unavailable.");
        }
        Path workspace = workspaceFolder.toPath().toAbsolutePath().normalize();
        Files.createDirectories(workspace);
        if (Files.isSymbolicLink(workspace) || !Files.isDirectory(workspace, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Pack workspace is missing or unsafe: " + workspace);
        }
        return workspace.toFile();
    }

    static String nextAvailableProjectName(File workspace, String baseName) throws IOException {
        String normalizedBase = normalizeProjectName(baseName);
        Path root = requireSafeWorkspace(workspace).toPath();
        String candidate = normalizedBase;
        int suffix = 2;
        while (Files.exists(root.resolve(candidate), LinkOption.NOFOLLOW_LINKS)) {
            candidate = normalizedBase + suffix++;
        }
        return candidate;
    }

    static void createStarterProject(File workspace, String projectName) throws IOException {
        File safeWorkspace = requireSafeWorkspace(workspace);
        String safeName = normalizeProjectName(projectName);
        Path target = safeWorkspace.toPath().resolve(safeName);
        Path stage = Files.createTempDirectory(safeWorkspace.toPath(), ".iris-starter-" + safeName + "-");
        boolean published = false;
        try {
            Files.createDirectories(stage.resolve("dimensions"));
            Files.createDirectories(stage.resolve("regions"));
            Files.createDirectories(stage.resolve("biomes"));
            Files.createDirectories(stage.resolve("generators"));
            Files.writeString(stage.resolve("dimensions/" + safeName + ".json"), """
                    {
                      "name": "%s",
                      "version": 1,
                      "regions": ["starter"],
                      "logicalHeight": 384,
                      "dimensionHeight": {"min": -64, "max": 320}
                    }
                    """.formatted(safeName), StandardCharsets.UTF_8);
            Files.writeString(stage.resolve("regions/starter.json"), """
                    {
                      "name": "Starter",
                      "landBiomes": ["starter"],
                      "seaBiomes": ["starter"],
                      "shoreBiomes": ["starter"]
                    }
                    """, StandardCharsets.UTF_8);
            Files.writeString(stage.resolve("biomes/starter.json"), """
                    {
                      "name": "Starter Plains",
                      "layers": [{"palette": [{"block": "minecraft:grass_block"}]}],
                      "generators": [{"generator": "flat", "min": 96, "max": 96}],
                      "derivative": "minecraft:plains",
                      "vanillaDerivative": "minecraft:plains"
                    }
                    """, StandardCharsets.UTF_8);
            Files.writeString(stage.resolve("generators/flat.json"), """
                    {
                      "interpolator": {"function": "NONE", "horizontalScale": 1},
                      "seed": 310,
                      "composite": [{"seed": 310, "style": {"style": "FLAT"}}]
                    }
                    """, StandardCharsets.UTF_8);
            publishNewDirectory(stage, target);
            published = true;
            IrisProject project = new IrisProject(target.toFile());
            JSONObject workspaceConfiguration = new IrisCodeWorkspace(project).createCodeWorkspaceConfig();
            writeWorkspaceAtomically(target, safeName, workspaceConfiguration.toString(0));
        } catch (Throwable failure) {
            if (published) {
                Throwable cleanupFailure = rollbackCreatedProjectFiles(target.toFile());
                if (cleanupFailure != null && cleanupFailure != failure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            if (failure instanceof Error error) {
                throw error;
            }
            if (failure instanceof IOException ioFailure) {
                throw ioFailure;
            }
            throw new IOException("Failed to create starter project '" + safeName + "'.", failure);
        } finally {
            AtomicDirectoryPublisher.deleteTree(stage);
        }
    }

    private static boolean hasLoadableDimensionFile(File pack, String key) {
        Path packPath = pack.toPath().toAbsolutePath().normalize();
        Path dimension = packPath.resolve("dimensions").resolve(key + ".json").normalize();
        try {
            PackDirectoryResolver.requireSafePackTree(pack);
        } catch (IOException exception) {
            return false;
        }
        return dimension.startsWith(packPath)
                && Files.isDirectory(packPath)
                && !Files.isSymbolicLink(dimension)
                && Files.isRegularFile(dimension, LinkOption.NOFOLLOW_LINKS);
    }

    private static void rollbackCreatedProject(VolmitSender sender, File project, String message) {
        Throwable cleanupFailure = rollbackCreatedProjectFiles(project);
        if (cleanupFailure == null) {
            sender.sendMessage(message);
            return;
        }
        IrisLogging.reportError("Failed to fully roll back studio project " + project.getPath(), cleanupFailure);
        sender.sendMessage(message + " Cleanup was incomplete; check the console and "
                + project.getAbsolutePath() + ".");
    }

    static Throwable rollbackCreatedProjectFiles(File project) {
        File target = project.toPath().toAbsolutePath().normalize().toFile();
        Throwable cleanupFailure = null;
        IrisData loadedData = IrisData.getLoaded(target).orElse(null);
        if (loadedData != null) {
            try {
                loadedData.close();
            } catch (Throwable closeFailure) {
                cleanupFailure = closeFailure;
            }
        }
        try {
            AtomicDirectoryPublisher.deleteTree(target.toPath());
        } catch (Throwable deleteFailure) {
            if (cleanupFailure == null) {
                cleanupFailure = deleteFailure;
            } else if (cleanupFailure != deleteFailure) {
                cleanupFailure.addSuppressed(deleteFailure);
            }
        }
        return cleanupFailure;
    }

    private static void sendBusy(VolmitSender sender, LifecycleOperationCoordinator.BusyException busy) {
        sender.sendMessage(packMutationBusyMessage(busy.currentOperation()));
    }

    static String packMutationBusyMessage(LifecycleOperationCoordinator.ActiveOperation operation) {
        if (operation.domain() == LifecycleOperationCoordinator.Domain.PACK_MUTATION
                && operation.kind() == LifecycleOperationCoordinator.OperationKind.PACK_DOWNLOAD) {
            return IrisLanguage.plain(PackDownloadMessages.IN_PROGRESS);
        }
        return "Iris pack changes are busy with " + operation.kind().name().toLowerCase(Locale.ROOT)
                + " for '" + operation.target() + "'. Try again when it completes.";
    }

    private static void closeLease(LifecycleOperationCoordinator.Lease lease) {
        try {
            lease.close();
        } catch (Throwable e) {
            IrisLogging.reportError("Lifecycle idle callback failed after a studio pack operation.", e);
        }
    }

    private static String errorDetail(Throwable failure) {
        Throwable detail = failure;
        while (detail.getCause() != null && detail.getCause() != detail) {
            detail = detail.getCause();
        }
        String message = detail.getMessage();
        return message == null || message.isBlank() ? detail.getClass().getSimpleName() : message;
    }

    static void copyPackTree(Path source, Path target) throws IOException {
        Path normalizedSource = source.toRealPath();
        if (!Files.isDirectory(normalizedSource, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Pack source is not a directory: " + normalizedSource);
        }
        Path requestedTarget = target.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(requestedTarget)) {
            throw new IOException("Pack installation stage is a symbolic link: " + requestedTarget);
        }
        Path normalizedTarget;
        if (Files.exists(requestedTarget, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isDirectory(requestedTarget, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Pack installation stage is not a directory: " + requestedTarget);
            }
            normalizedTarget = requestedTarget.toRealPath();
        } else {
            Path parent = Objects.requireNonNull(
                    requestedTarget.getParent(),
                    "Pack installation stage parent");
            normalizedTarget = parent.toRealPath().resolve(requestedTarget.getFileName()).normalize();
        }
        if (normalizedTarget.startsWith(normalizedSource)
                || normalizedSource.startsWith(normalizedTarget)) {
            throw new IOException("Pack source and installation stage overlap: "
                    + normalizedSource + " and " + normalizedTarget);
        }
        Files.walkFileTree(normalizedSource, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(
                    Path directory,
                    BasicFileAttributes attributes
            ) throws IOException {
                if (attributes.isSymbolicLink()) {
                    throw new IOException("Pack contains a symbolic link: " + directory);
                }
                if (!directory.equals(normalizedSource)
                        && normalizedSource.relativize(directory).getNameCount() == 1
                        && PackDirectoryResolver.isHiddenName(directory.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(copyDestination(
                        normalizedSource,
                        normalizedTarget,
                        directory));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (attributes.isSymbolicLink()) {
                    throw new IOException("Pack contains a symbolic link: " + file);
                }
                if (!attributes.isRegularFile()) {
                    throw new IOException("Pack contains an unsupported entry: " + file);
                }
                String fileName = file.getFileName().toString();
                if ((normalizedSource.relativize(file).getNameCount() == 1
                        && PackDirectoryResolver.isHiddenName(fileName))
                        || fileName.endsWith(".code-workspace")) {
                    return FileVisitResult.CONTINUE;
                }
                Path destination = copyDestination(normalizedSource, normalizedTarget, file);
                Files.createDirectories(Objects.requireNonNull(destination.getParent(), "Pack entry parent"));
                Files.copy(file, destination, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException failure) throws IOException {
                throw new IOException("Unable to copy pack entry: " + file, failure);
            }
        });
    }

    private static Path copyDestination(Path source, Path target, Path entry) throws IOException {
        Path destination = target.resolve(source.relativize(entry)).normalize();
        if (!destination.startsWith(target)) {
            throw new IOException("Pack entry escapes its installation stage: " + entry);
        }
        return destination;
    }

    static void publishNewDirectory(Path stage, Path target) throws IOException {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(target)) {
            throw new FileAlreadyExistsException(target.toString());
        }
        try {
            Files.move(stage, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(stage, target);
        }
    }

    private static void writeWorkspaceAtomically(Path project, String projectName, String content) throws IOException {
        Path target = project.resolve(projectName + ".code-workspace");
        Path stage = Files.createTempFile(project, "." + projectName + ".workspace-", ".tmp");
        IOException operationFailure = null;
        try {
            Files.writeString(stage, content, StandardCharsets.UTF_8);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(target)) {
                throw new FileAlreadyExistsException(target.toString());
            }
            try {
                Files.move(stage, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(stage, target);
            }
        } catch (IOException e) {
            operationFailure = e;
            throw e;
        } finally {
            try {
                Files.deleteIfExists(stage);
            } catch (IOException cleanupFailure) {
                if (operationFailure != null) {
                    operationFailure.addSuppressed(cleanupFailure);
                } else {
                    throw cleanupFailure;
                }
            }
        }
    }

    static final class StudioTransitionQueue {
        private final Object monitor;
        private CompletableFuture<Void> tail;

        StudioTransitionQueue() {
            monitor = new Object();
            tail = CompletableFuture.completedFuture(null);
        }

        <T> CompletableFuture<T> submit(Supplier<CompletableFuture<T>> operation) {
            Supplier<CompletableFuture<T>> queuedOperation = Objects.requireNonNull(operation, "operation");
            CompletableFuture<T> result = new CompletableFuture<>();
            synchronized (monitor) {
                tail = tail.handle((ignored, previousFailure) -> null)
                        .thenCompose(ignored -> execute(queuedOperation, result));
            }
            return result;
        }

        private <T> CompletableFuture<Void> execute(
                Supplier<CompletableFuture<T>> operation,
                CompletableFuture<T> result
        ) {
            CompletableFuture<T> running;
            try {
                running = Objects.requireNonNull(operation.get(), "Studio transition returned no future.");
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
                return CompletableFuture.completedFuture(null);
            }
            return running.handle((value, failure) -> {
                if (failure == null) {
                    result.complete(value);
                } else {
                    result.completeExceptionally(failure);
                }
                return null;
            });
        }
    }

    @FunctionalInterface
    private interface PackMutation {
        void run(PackDownloader.DownloadCancellation cancellation) throws Exception;
    }

    private enum CreationOutcome {
        FAILED,
        OPEN,
        RESTART
    }

    private record GenerationCandidate(
            String packFingerprint,
            GenerationEpoch.DimensionContract dimensionContract,
            GenerationRegistryContract registryContract
    ) {
    }

    public IrisProject getActiveProject() {
        return activeProject;
    }

    public void updateWorkspace() {
        if (isProjectOpen()) {
            new IrisCodeWorkspace(activeProject).updateWorkspace();
        }
    }
}
