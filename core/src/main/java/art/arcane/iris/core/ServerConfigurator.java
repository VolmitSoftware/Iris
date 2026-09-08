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

package art.arcane.iris.core;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.core.datapack.DatapackIngestService;
import art.arcane.iris.core.datapack.DatapackIngestService.ReapplyOutcome;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.core.lifecycle.BukkitWorldConfiguration;
import art.arcane.iris.core.lifecycle.BukkitWorldConfiguration.IrisGeneratorBinding;
import art.arcane.iris.core.lifecycle.LifecycleOperationCoordinator;
import art.arcane.iris.core.nms.INMS;
import art.arcane.iris.core.nms.datapack.DataVersion;
import art.arcane.iris.core.nms.datapack.IDataFixer;
import art.arcane.iris.core.pack.AtomicDirectoryPublisher;
import art.arcane.iris.core.pack.DefaultPackBootstrapProvisioner;
import art.arcane.iris.core.pack.PackDirectoryResolver;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisBiomeCustom;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KSet;
import art.arcane.iris.util.common.misc.ServerProperties;
import art.arcane.iris.util.common.plugin.VolmitSender;
import art.arcane.iris.util.common.scheduling.J;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import art.arcane.iris.core.localization.BukkitRuntimeMessages;
import art.arcane.iris.core.localization.IrisLanguage;
import art.arcane.volmlib.util.localization.MessageArgument;
public class ServerConfigurator {
    private static final Object DATAPACK_INSTALL_LOCK = new Object();
    private static final String CODE_WORKSPACE_SUFFIX = ".code-workspace";
    private static final String COMPILER_INPUT_FINGERPRINT_CACHE = "datapack-compiler-input-fingerprint";
    static final String POST_COMPILE_RESTART_WARNING = "Iris installed updated datapack registry entries; "
            + "restart the server before creating worlds or opening Studios.";
    private static final int FINGERPRINT_BUFFER_BYTES = 64 * 1024;
    private static volatile boolean loadedDatapackRuntimeReady;
    private static volatile String loadedDatapackCompilerInputFingerprint = "";
    private static volatile Map<String, String> loadedDatapackRegistryRequirements = Map.of();
    private static volatile long loadedDatapackRuntimeGeneration;
    private static volatile boolean loadedDatapackRestartRequired;

    public static void configure() {
        synchronized (DATAPACK_INSTALL_LOCK) {
            invalidateLoadedDatapackRuntime();
            loadedDatapackCompilerInputFingerprint = "";
            loadedDatapackRegistryRequirements = Map.of();
            loadedDatapackRestartRequired = false;
        }
        IrisSettings.IrisSettingsAutoconfiguration s = IrisSettings.get().getAutoConfiguration();
        if (s.isConfigureSpigotTimeoutTime()) {
            J.attempt(ServerConfigurator::increaseKeepAliveSpigot);
        }

        if (s.isConfigurePaperWatchdogDelay()) {
            J.attempt(ServerConfigurator::increasePaperWatchdog);
        }

        if (DefaultPackBootstrapProvisioner.wasProvisionedThisStartup()) {
            loadedDatapackRuntimeReady = !IrisSettings.get().getGeneral().adjustVanillaHeight
                    && pinLoadedDatapackCompilerInputs(
                            DefaultPackBootstrapProvisioner.compilerInputFingerprintThisStartup())
                    && pinLoadedDatapackRegistryRequirements();
            IrisLogging.info("Paper loaded the Iris datapack during bootstrap; skipping the legacy startup install.");
        } else {
            DatapackInstallResult result = installDataPacks(true);
            loadedDatapackRuntimeReady = result.succeeded()
                    && !result.restartRequired()
                    && pinLoadedDatapackCompilerInputs()
                    && pinLoadedDatapackRegistryRequirements();
            if (result.restartRequired()) {
                requireDatapackRestart();
                IrisLogging.warn("Iris datapack changes require another server restart before worlds can use them.");
            }
        }
    }

    public static boolean isLoadedDatapackRuntimeReady(IrisDimension dimension) {
        IrisDimension requiredDimension = Objects.requireNonNull(dimension, "Iris dimension");
        if (!loadedDatapackRuntimeReady
                || loadedDatapackRestartRequired
                || !BukkitPlatform.hasPlugin()
                || !BukkitPlatform.plugin().isEnabled()) {
            return false;
        }
        try {
            if (!INMS.get().supportsIrisWorldGeneration()
                    || INMS.get().missingDimensionTypes(requiredDimension.getDimensionTypeKey())) {
                return false;
            }
            Map<String, String> requiredRegistryEntries =
                    IrisDatapackCompiler.computeRegistryRequirements(requiredDimension, resolveDataFixer());
            return loadedRegistrySatisfies(
                    loadedDatapackRegistryRequirements,
                    requiredRegistryEntries);
        } catch (IOException | RuntimeException exception) {
            IrisLogging.reportError("Unable to verify loaded Iris datapack registry requirements.", exception);
            return false;
        }
    }

    public static void resetLoadedDatapackRuntime() {
        synchronized (DATAPACK_INSTALL_LOCK) {
            loadedDatapackRuntimeReady = false;
            loadedDatapackCompilerInputFingerprint = "";
            loadedDatapackRegistryRequirements = Map.of();
            loadedDatapackRuntimeGeneration++;
            loadedDatapackRestartRequired = false;
        }
    }

    public static LoadedDatapackRuntimeInvalidation invalidateLoadedDatapackRuntime() {
        synchronized (DATAPACK_INSTALL_LOCK) {
            boolean wasReady = loadedDatapackRuntimeReady;
            String fingerprint = loadedDatapackCompilerInputFingerprint;
            loadedDatapackRuntimeReady = false;
            loadedDatapackRuntimeGeneration++;
            return new LoadedDatapackRuntimeInvalidation(
                    loadedDatapackRuntimeGeneration,
                    wasReady,
                    fingerprint);
        }
    }

    public static void requireDatapackRestart() {
        requireWorldCreationRestart();
        IrisStartupValidation.requireRestart(
                "Iris datapack changes require a restart before player admission or world creation.");
    }

    public static void requireWorldCreationRestart() {
        synchronized (DATAPACK_INSTALL_LOCK) {
            invalidateLoadedDatapackRuntime();
            loadedDatapackRestartRequired = true;
        }
    }

    public static Optional<String> worldCreationDenialReason(boolean forceStudio) {
        Optional<String> startupDenial = IrisStartupValidation.studioDenialReason(forceStudio);
        if (startupDenial.isPresent()) {
            return startupDenial;
        }
        if (!forceStudio && loadedDatapackRestartRequired) {
            return Optional.of("Iris installed or updated a dimension pack that requires a server restart before world creation or Studio open.");
        }
        return Optional.empty();
    }

    public static void requireWorldCreationReady(boolean forceStudio) {
        Optional<String> denial = worldCreationDenialReason(forceStudio);
        if (denial.isPresent()) {
            throw new IllegalStateException("Iris world creation is locked: " + denial.get());
        }
    }

    public static void restoreLoadedDatapackRuntimeIfUnchanged(
            LoadedDatapackRuntimeInvalidation invalidation
    ) {
        if (invalidation == null
                || !invalidation.wasReady()
                || invalidation.fingerprint().isBlank()) {
            return;
        }
        String currentFingerprint;
        try {
            currentFingerprint = computeCurrentDatapackCompilerInputFingerprint(resolveDataFixer());
        } catch (IOException | RuntimeException exception) {
            IrisLogging.reportError("Unable to restore loaded Iris datapack runtime readiness.", exception);
            return;
        }
        synchronized (DATAPACK_INSTALL_LOCK) {
            if (loadedDatapackRuntimeGeneration != invalidation.generation()
                    || loadedDatapackRuntimeReady
                    || loadedDatapackRestartRequired
                    || !reusableRuntimeFingerprint(invalidation.fingerprint(), currentFingerprint)) {
                return;
            }
            loadedDatapackCompilerInputFingerprint = currentFingerprint;
            loadedDatapackRuntimeReady = true;
        }
    }

    private static void increaseKeepAliveSpigot() throws IOException, InvalidConfigurationException {
        File spigotConfig = new File("spigot.yml");
        FileConfiguration f = new YamlConfiguration();
        f.load(spigotConfig);
        long tt = f.getLong("settings.timeout-time");

        long spigotTimeout = TimeUnit.MINUTES.toSeconds(5);

        if (tt < spigotTimeout) {
            IrisLogging.warn("Updating spigot.yml timeout-time: " + tt + " -> " + spigotTimeout + " (5 minutes)");
            IrisLogging.warn("You can disable this change (autoconfigureServer) in Iris settings, then change back the value.");
            f.set("settings.timeout-time", spigotTimeout);
            f.save(spigotConfig);
        }
    }

    private static void increasePaperWatchdog() throws IOException, InvalidConfigurationException {
        File spigotConfig = new File("config/paper-global.yml");
        FileConfiguration f = new YamlConfiguration();
        f.load(spigotConfig);
        long tt = f.getLong("watchdog.early-warning-delay");

        long watchdog = TimeUnit.MINUTES.toMillis(3);
        if (tt < watchdog) {
            IrisLogging.warn("Updating paper.yml watchdog early-warning-delay: " + tt + " -> " + watchdog + " (3 minutes)");
            IrisLogging.warn("You can disable this change (autoconfigureServer) in Iris settings, then change back the value.");
            f.set("watchdog.early-warning-delay", watchdog);
            f.save(spigotConfig);
        }
    }

    public static KList<File> getDatapacksFolder() {
        return new KList<File>().qadd(new File(IrisWorldStorage.levelRoot(), "datapacks"));
    }

    public static KList<File> getIrisDatapackRoots() {
        KList<File> roots = new KList<>();
        for (File datapacksFolder : getDatapacksFolder()) {
            roots.add(new File(datapacksFolder, "iris"));
        }
        return roots;
    }

    public static DatapackInstallResult installDataPacks(boolean fullInstall) {
        return installDataPacks(resolveDataFixer(), fullInstall);
    }

    public static DatapackInstallResult installDataPacks(IDataFixer fixer, boolean fullInstall) {
        synchronized (DATAPACK_INSTALL_LOCK) {
            return installDataPacksLocked(fixer, fullInstall);
        }
    }

    private static DatapackInstallResult installDataPacksLocked(IDataFixer fixer, boolean fullInstall) {
        if (fixer == null) {
            IrisLogging.error("Unable to install datapacks, fixer is null!");
            return DatapackInstallResult.failedResult();
        }
        KList<File> datapacksFolders = getDatapacksFolder();
        ReapplyOutcome reapply = DatapackIngestService.reapplyFromStaging(datapacksFolders);
        if (!reapply.succeeded()) {
            return DatapackInstallResult.failedResult();
        }
        return compileDataPacksLocked(fixer, fullInstall, reapply);
    }

    private static DatapackInstallResult compileDataPacksLocked(
            IDataFixer fixer,
            boolean fullInstall,
            ReapplyOutcome reapply
    ) {
        if (!Objects.requireNonNull(reapply, "External datapack reapply outcome").succeeded()) {
            return DatapackInstallResult.failedResult();
        }
        if (fixer == null) {
            IrisLogging.error("Unable to install datapacks, fixer is null!");
            return DatapackInstallResult.failedResult();
        }
        IrisLogging.debug("Checking Data Packs...");
        List<File> packRoots;
        List<IrisGeneratorBinding> bindings;
        try {
            packRoots = collectCompilerPackRoots();
            bindings = collectConfiguredLevelStemBindings();
        } catch (IOException exception) {
            IrisLogging.reportError("Unable to resolve Iris datapack compiler inputs.", exception);
            return DatapackInstallResult.failedResult();
        }

        KList<File> liveRoots = getIrisDatapackRoots();
        KList<File> stagedRoots = new KList<>();
        List<Path> stagedPaths = new ArrayList<>(liveRoots.size());
        List<AtomicDirectoryPublisher.Publication> publications = new ArrayList<>(liveRoots.size());
        try {
            for (File liveRoot : liveRoots) {
                Path target = liveRoot.toPath().toAbsolutePath().normalize();
                Path parent = target.getParent();
                if (parent == null) {
                    throw new IOException("Iris datapack root has no parent: " + target);
                }
                Files.createDirectories(parent);
                Path staged = parent.resolve(".iris-compile-" + UUID.randomUUID());
                Files.createDirectories(staged);
                stagedPaths.add(staged);
                stagedRoots.add(staged.toFile());
            }
            IrisDatapackCompiler.compile(
                    packRoots,
                    stagedRoots,
                    bindings,
                    fixer,
                    IrisSettings.get().getGeneral().adjustVanillaHeight
            );
            for (int i = 0; i < liveRoots.size(); i++) {
                publications.add(AtomicDirectoryPublisher.publish(
                        stagedRoots.get(i).toPath(),
                        liveRoots.get(i).toPath()
                ));
            }
            for (AtomicDirectoryPublisher.Publication publication : publications) {
                publication.commit();
                try {
                    publication.cleanupBackup();
                } catch (IOException cleanupFailure) {
                    IrisLogging.warn("Iris datapack was committed but its backup could not be removed: "
                            + cleanupFailure.getMessage());
                }
            }
        } catch (IOException | RuntimeException e) {
            closePublications(publications, e);
            IrisLogging.reportError("Unable to compile Iris datapacks", e);
            return DatapackInstallResult.failedResult();
        } finally {
            for (Path stagedPath : stagedPaths) {
                try {
                    AtomicDirectoryPublisher.deleteTree(stagedPath);
                } catch (IOException cleanupFailure) {
                    IrisLogging.warn("Failed to clean Iris datapack compilation stage " + stagedPath + ": "
                            + cleanupFailure.getMessage());
                }
            }
        }
        IrisLogging.debug("Data Packs Setup!");

        boolean verifiedRestartRequired = fullInstall && verifyDataPacksPost();
        boolean restartRequired = fullInstall && (reapply.changed() || verifiedRestartRequired);
        return restartRequired
                ? DatapackInstallResult.restartRequiredResult()
                : DatapackInstallResult.readyResult();
    }

    private static IDataFixer resolveDataFixer() {
        IDataFixer fixer = DataVersion.getDefault();
        if (fixer != null) {
            return fixer;
        }
        DataVersion fallback = DataVersion.getLatest();
        IrisLogging.warn("Primary datapack fixer was null, forcing latest fixer: " + fallback.getVersion());
        return fallback.get();
    }

    private static void closePublications(List<AtomicDirectoryPublisher.Publication> publications, Throwable failure) {
        for (int i = publications.size() - 1; i >= 0; i--) {
            try {
                publications.get(i).close();
            } catch (IOException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
    }

    public static DatapackInstallResult installDataPacksIfChanged(boolean fullInstall) {
        return installDataPacksIfChanged(fullInstall, null);
    }

    public static DatapackInstallResult installDataPacksIfChanged(
            boolean fullInstall,
            BiConsumer<String, Long> timingConsumer
    ) {
        synchronized (DATAPACK_INSTALL_LOCK) {
            long totalStart = System.nanoTime();
            File cacheFile = new File(
                    IrisPlatforms.get().dataFolder("cache"),
                    COMPILER_INPUT_FINGERPRINT_CACHE);
            String cached = readCompilerInputFingerprintCache(cacheFile.toPath());
            long recoveryStart = System.nanoTime();
            ReapplyOutcome reapply = DatapackIngestService.reapplyFromStaging(getDatapacksFolder());
            reportTiming(timingConsumer, "datapack_external_recovery", recoveryStart);
            if (!reapply.succeeded()) {
                reportTiming(timingConsumer, "datapack_install_if_changed_total", totalStart);
                return DatapackInstallResult.failedResult();
            }
            if (fullInstall && loadedDatapackRestartRequired) {
                reportTiming(timingConsumer, "datapack_install_if_changed_total", totalStart);
                return DatapackInstallResult.restartRequiredResult();
            }
            String current;
            long fingerprintStart = System.nanoTime();
            try {
                current = restoredCompilerInputFingerprint();
                if (current.isBlank()) {
                    current = computeCurrentDatapackCompilerInputFingerprint(resolveDataFixer());
                }
            } catch (IOException | RuntimeException exception) {
                reportTiming(timingConsumer, "datapack_compiler_input_fingerprint", fingerprintStart);
                reportTiming(timingConsumer, "datapack_install_if_changed_total", totalStart);
                IrisLogging.reportError("Unable to fingerprint Iris datapack compiler inputs safely", exception);
                return DatapackInstallResult.failedResult();
            }
            reportTiming(timingConsumer, "datapack_compiler_input_fingerprint", fingerprintStart);
            boolean loadedCompilerInputsChanged = !loadedDatapackCompilerInputFingerprint.isBlank()
                    && !reusableRuntimeFingerprint(
                            loadedDatapackCompilerInputFingerprint,
                            current);
            boolean loadedRegistryRestartRequired = fullInstall
                    && loadedCompilerInputsChanged
                    && currentRegistryRequiresRestart();
            if (!current.isEmpty() && current.equals(cached)) {
                IrisLogging.debug("Data packs unchanged, skipping install.");
                DatapackInstallResult result = loadedRegistryRestartRequired
                        ? DatapackInstallResult.restartRequiredResult()
                        : resultForUnchangedFingerprint(fullInstall, reapply);
                if (result.restartRequired()) {
                    requireDatapackRestart();
                } else if (result.succeeded()) {
                    loadedDatapackCompilerInputFingerprint = current;
                }
                reportTiming(timingConsumer, "datapack_install_if_changed_total", totalStart);
                return result;
            }
            long compileStart = System.nanoTime();
            DatapackInstallResult result = compileDataPacksLocked(
                    resolveDataFixer(),
                    fullInstall,
                    reapply);
            if (loadedRegistryRestartRequired && result.succeeded()) {
                result = DatapackInstallResult.restartRequiredResult();
            }
            if (result.restartRequired()) {
                requireDatapackRestart();
            }
            reportTiming(timingConsumer, "datapack_compile_publish", compileStart);
            if (result.succeeded() && !result.restartRequired()) {
                writeCompilerInputFingerprintCache(cacheFile.toPath(), current);
                loadedDatapackCompilerInputFingerprint = current;
            }
            reportTiming(timingConsumer, "datapack_install_if_changed_total", totalStart);
            return result;
        }
    }

    private static List<File> collectCompilerPackRoots() throws IOException {
        return IrisDatapackCompiler.collectPackRoots(
                IrisPlatforms.get().dataFolder().toPath(),
                IrisWorldStorage.levelRoot().toPath());
    }

    private static boolean currentRegistryRequiresRestart() {
        try {
            Map<String, String> currentRequirements = IrisDatapackCompiler.computeRegistryRequirements(
                    collectCompilerPackRoots(),
                    resolveDataFixer());
            return runtimeRequiresRegistryRestart(
                    loadedDatapackRegistryRequirements,
                    currentRequirements);
        } catch (IOException | RuntimeException exception) {
            IrisLogging.reportError(
                    "Unable to compare loaded Iris datapack registry requirements.",
                    exception);
            return true;
        }
    }

    private static List<IrisGeneratorBinding> collectConfiguredLevelStemBindings() throws IOException {
        File levelRoot = IrisWorldStorage.levelRoot();
        String levelId = levelRoot.getName();
        if (levelId.isBlank()) {
            throw new IOException("Configured level root has no Paper startup level id: " + levelRoot);
        }
        return BukkitWorldConfiguration.readIrisGeneratorBindings(
                ServerProperties.BUKKIT_YML,
                levelId,
                levelRoot.toPath()
        );
    }

    private static String computeCurrentDatapackCompilerInputFingerprint(IDataFixer fixer) throws IOException {
        return IrisDatapackCompiler.computeInputFingerprint(
                IrisDatapackCompiler.collectCompilerInputRoots(
                        IrisPlatforms.get().dataFolder().toPath(),
                        IrisWorldStorage.levelRoot().toPath()),
                collectConfiguredLevelStemBindings(),
                Objects.requireNonNull(fixer, "Datapack fixer"),
                IrisSettings.get().getGeneral().adjustVanillaHeight);
    }

    static String restoredCompilerInputFingerprint() {
        if (!loadedDatapackRuntimeReady || loadedDatapackRestartRequired) {
            return "";
        }
        return Objects.requireNonNullElse(loadedDatapackCompilerInputFingerprint, "");
    }

    private static boolean pinLoadedDatapackCompilerInputs() {
        return pinLoadedDatapackCompilerInputs(null);
    }

    private static boolean pinLoadedDatapackCompilerInputs(String expectedFingerprint) {
        if (loadedDatapackRestartRequired) {
            return false;
        }
        try {
            String fingerprint = computeCurrentDatapackCompilerInputFingerprint(resolveDataFixer());
            if (fingerprint.isBlank()
                    || expectedFingerprint != null
                    && !reusableRuntimeFingerprint(expectedFingerprint, fingerprint)) {
                return false;
            }
            loadedDatapackCompilerInputFingerprint = fingerprint;
            File cacheFile = new File(
                    IrisPlatforms.get().dataFolder("cache"),
                    COMPILER_INPUT_FINGERPRINT_CACHE);
            writeCompilerInputFingerprintCache(cacheFile.toPath(), fingerprint);
            return true;
        } catch (IOException | RuntimeException exception) {
            loadedDatapackCompilerInputFingerprint = "";
            IrisLogging.reportError("Unable to pin loaded Iris datapack compiler inputs.", exception);
            return false;
        }
    }

    private static boolean pinLoadedDatapackRegistryRequirements() {
        if (loadedDatapackRestartRequired) {
            return false;
        }
        try {
            loadedDatapackRegistryRequirements = IrisDatapackCompiler.computeRegistryRequirements(
                    collectCompilerPackRoots(),
                    resolveDataFixer());
            return true;
        } catch (IOException | RuntimeException exception) {
            loadedDatapackRegistryRequirements = Map.of();
            IrisLogging.reportError("Unable to pin loaded Iris datapack registry requirements.", exception);
            return false;
        }
    }

    static boolean loadedRegistrySatisfies(
            Map<String, String> loadedRequirements,
            Map<String, String> requiredEntries
    ) {
        if (loadedRequirements == null || requiredEntries == null || requiredEntries.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, String> entry : requiredEntries.entrySet()) {
            if (!entry.getValue().equals(loadedRequirements.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    static boolean runtimeRequiresRegistryRestart(
            Map<String, String> loadedRequirements,
            Map<String, String> currentRequirements
    ) {
        if (currentRequirements == null) {
            return true;
        }
        if (currentRequirements.isEmpty()) {
            return false;
        }
        return !loadedRegistrySatisfies(loadedRequirements, currentRequirements);
    }

    static boolean reusableRuntimeFingerprint(String loadedFingerprint, String currentFingerprint) {
        return loadedFingerprint != null
                && !loadedFingerprint.isBlank()
                && loadedFingerprint.equals(currentFingerprint);
    }

    private static void reportTiming(
            BiConsumer<String, Long> timingConsumer,
            String phase,
            long startedAtNanos
    ) {
        if (timingConsumer == null) {
            return;
        }
        try {
            timingConsumer.accept(phase, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos));
        } catch (Throwable exception) {
            IrisLogging.reportError("Datapack timing consumer failed during phase \"" + phase + "\".", exception);
        }
    }

    static DatapackInstallResult resultForUnchangedFingerprint(
            boolean fullInstall,
            ReapplyOutcome reapply
    ) {
        if (!Objects.requireNonNull(reapply, "External datapack reapply outcome").succeeded()) {
            return DatapackInstallResult.failedResult();
        }
        if (!reapply.changed()) {
            return DatapackInstallResult.unchangedResult();
        }
        return fullInstall
                ? DatapackInstallResult.restartRequiredResult()
                : DatapackInstallResult.readyResult();
    }

    static PackFingerprint resolvePostRecoveryPackFingerprint(
            File packsDir,
            String cachedMetadata,
            String cachedContent,
            ReapplyOutcome reapply
    ) {
        if (Objects.requireNonNull(reapply, "External datapack reapply outcome").changed()) {
            return resolvePackFingerprint(packsDir, "", "");
        }
        return resolvePackFingerprint(packsDir, cachedMetadata, cachedContent);
    }

    static PackFingerprint resolvePackFingerprint(File packsDir, String cachedMetadata, String cachedContent) {
        String metadata = computePackMetadataDigest(packsDir);
        if (!metadata.isEmpty()
                && metadata.equals(cachedMetadata)
                && cachedContent != null
                && !cachedContent.isEmpty()) {
            return new PackFingerprint(metadata, cachedContent);
        }
        return new PackFingerprint(metadata, computePackFingerprint(packsDir));
    }

    public static String computePackMetadataDigest(File packsDir) {
        Path root = resolveFingerprintRoot(packsDir);
        if (root == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            List<FingerprintEntry> entries = collectFingerprintEntries(root.toRealPath());
            entries.sort(Comparator.comparing(FingerprintEntry::relativePath));
            for (FingerprintEntry entry : entries) {
                byte[] relativePath = entry.relativePath().getBytes(StandardCharsets.UTF_8);
                updateDigestInt(digest, relativePath.length);
                digest.update(relativePath);
                updateDigestLong(digest, entry.size());
                updateDigestLong(digest, entry.lastModifiedMillis());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to fingerprint Iris packs at " + root, exception);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static String computePackFingerprint(File packsDir) {
        return computePackContentSnapshot(packsDir).content();
    }

    public static PackContentSnapshot computePackContentSnapshot(File packsDir) {
        Path root = resolveFingerprintRoot(packsDir);
        if (root == null) {
            return new PackContentSnapshot("", Map.of());
        }
        try {
            Path resolvedRoot = root.toRealPath();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            Map<String, MessageDigest> packDigests = new LinkedHashMap<>();
            List<FingerprintEntry> entries = collectFingerprintEntries(resolvedRoot);
            entries.sort(Comparator.comparing(FingerprintEntry::relativePath));
            byte[] buffer = new byte[FINGERPRINT_BUFFER_BYTES];
            for (FingerprintEntry entry : entries) {
                MessageDigest packDigest = entry.packName() == null
                        ? null
                        : packDigests.computeIfAbsent(entry.packName(), ignored -> newSha256Digest());
                updateFingerprintEntry(digest, entry.relativePath(), entry.size());
                if (packDigest != null) {
                    updateFingerprintEntry(packDigest, entry.packRelativePath(), entry.size());
                }
                long readBytes = 0L;
                try (InputStream input = Files.newInputStream(
                        entry.source(),
                        StandardOpenOption.READ,
                        LinkOption.NOFOLLOW_LINKS)) {
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        if (read > 0) {
                            digest.update(buffer, 0, read);
                            if (packDigest != null) {
                                packDigest.update(buffer, 0, read);
                            }
                            readBytes += read;
                        }
                    }
                }
                if (readBytes != entry.size()) {
                    throw new IOException("Iris pack changed while fingerprinting: " + entry.source());
                }
            }
            Map<String, String> packContents = new LinkedHashMap<>();
            for (Map.Entry<String, MessageDigest> entry : packDigests.entrySet()) {
                packContents.put(entry.getKey(), HexFormat.of().formatHex(entry.getValue().digest()));
            }
            return new PackContentSnapshot(
                    HexFormat.of().formatHex(digest.digest()),
                    Map.copyOf(packContents));
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to fingerprint Iris packs at " + root, exception);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static String computePackTreeFingerprint(File packDir) {
        Path root = resolveFingerprintRoot(packDir);
        if (root == null) {
            return "";
        }
        try {
            List<FingerprintEntry> entries = new ArrayList<>();
            collectFingerprintTree(root.toRealPath(), "", null, entries);
            entries.sort(Comparator.comparing(FingerprintEntry::relativePath));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[FINGERPRINT_BUFFER_BYTES];
            for (FingerprintEntry entry : entries) {
                updateFingerprintEntry(digest, entry.relativePath(), entry.size());
                long readBytes = 0L;
                try (InputStream input = Files.newInputStream(
                        entry.source(),
                        StandardOpenOption.READ,
                        LinkOption.NOFOLLOW_LINKS)) {
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        if (read > 0) {
                            digest.update(buffer, 0, read);
                            readBytes += read;
                        }
                    }
                }
                if (readBytes != entry.size()) {
                    throw new IOException("Iris pack changed while fingerprinting: " + entry.source());
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to fingerprint Iris pack at " + root, exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    private static MessageDigest newSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    private static void updateFingerprintEntry(MessageDigest digest, String relativePath, long size) {
        byte[] relativeBytes = relativePath.getBytes(StandardCharsets.UTF_8);
        updateDigestInt(digest, relativeBytes.length);
        digest.update(relativeBytes);
        updateDigestLong(digest, size);
    }

    private static Path resolveFingerprintRoot(File packsDir) {
        if (packsDir == null) {
            return null;
        }
        Path root = packsDir.toPath().toAbsolutePath().normalize();
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        if (!Files.isDirectory(root)) {
            if (Files.isSymbolicLink(root)) {
                throw new IllegalArgumentException("Iris packs root target is missing or unsafe: " + root);
            }
            return null;
        }
        return root;
    }

    private static FingerprintCache readFingerprintCache(Path cacheFile) {
        if (!Files.isRegularFile(cacheFile)) {
            return new FingerprintCache("", "");
        }
        try {
            List<String> lines = Files.readAllLines(cacheFile, StandardCharsets.UTF_8);
            String content = lines.isEmpty() ? "" : lines.getFirst().trim();
            String metadata = lines.size() > 1 ? lines.get(1).trim() : "";
            return new FingerprintCache(content, metadata);
        } catch (IOException e) {
            return new FingerprintCache("", "");
        }
    }

    private static String readCompilerInputFingerprintCache(Path cacheFile) {
        if (!Files.isRegularFile(cacheFile)) {
            return "";
        }
        try {
            return Files.readString(cacheFile, StandardCharsets.UTF_8).trim();
        } catch (IOException exception) {
            return "";
        }
    }

    private static void writeFingerprintCache(Path cacheFile, PackFingerprint fingerprint) {
        try {
            writeFingerprintAtomic(cacheFile, fingerprint.content() + "\n" + fingerprint.metadata());
        } catch (IOException e) {
            IrisLogging.warn("Failed to write datapack fingerprint cache: " + e.getMessage());
        }
    }

    private static void writeCompilerInputFingerprintCache(Path cacheFile, String fingerprint) {
        try {
            writeFingerprintAtomic(cacheFile, fingerprint);
        } catch (IOException exception) {
            IrisLogging.warn("Failed to write datapack compiler-input fingerprint cache: "
                    + exception.getMessage());
        }
    }

    private static void writeFingerprintAtomic(Path target, String fingerprint) throws IOException {
        Path absoluteTarget = target.toAbsolutePath().normalize();
        Path parent = absoluteTarget.getParent();
        if (parent == null) {
            throw new IOException("Datapack fingerprint target has no parent: " + absoluteTarget);
        }
        Files.createDirectories(parent);
        Path staged = Files.createTempFile(parent, ".datapack-fingerprint-", ".tmp");
        try {
            Files.writeString(staged, fingerprint, StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(staged, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(staged, absoluteTarget, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(staged, absoluteTarget, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    private static List<FingerprintEntry> collectFingerprintEntries(Path root) throws IOException {
        List<FingerprintEntry> entries = new ArrayList<>();
        try (Stream<Path> children = Files.list(root)) {
            for (Path child : children.toList()) {
                String childName = child.getFileName().toString();
                if (PackDirectoryResolver.isHiddenName(childName) || isGeneratedPackFile(childName)) {
                    continue;
                }
                if (Files.isSymbolicLink(child)) {
                    if (!Files.isDirectory(child)) {
                        throw new IOException("Iris pack fingerprint rejected symbolic link: " + child);
                    }
                    PackDirectoryResolver.requireSafePackTree(child.toFile());
                    collectFingerprintTree(child.toRealPath(), childName, childName, entries);
                } else if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                    collectFingerprintTree(child, childName, childName, entries);
                } else if (Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS)) {
                    BasicFileAttributes attributes = Files.readAttributes(
                            child, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    entries.add(new FingerprintEntry(
                            child,
                            childName,
                            null,
                            null,
                            attributes.size(),
                            attributes.lastModifiedTime().toMillis()));
                } else {
                    throw new IOException("Iris pack fingerprint rejected unsupported entry: " + child);
                }
            }
        }
        return entries;
    }

    private static void collectFingerprintTree(
            Path treeRoot,
            String logicalRoot,
            String packName,
            List<FingerprintEntry> entries
    ) throws IOException {
        Files.walkFileTree(treeRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(
                    Path directory,
                    BasicFileAttributes attributes
            ) {
                if (!directory.equals(treeRoot)
                        && treeRoot.relativize(directory).getNameCount() == 1
                        && PackDirectoryResolver.isHiddenName(directory.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                String fileName = file.getFileName().toString();
                if ((treeRoot.relativize(file).getNameCount() == 1
                        && PackDirectoryResolver.isHiddenName(fileName))
                        || isGeneratedPackFile(fileName)) {
                    return FileVisitResult.CONTINUE;
                }
                if (attributes.isSymbolicLink() || Files.isSymbolicLink(file)) {
                    throw new IOException("Iris pack fingerprint rejected symbolic link: " + file);
                }
                if (!attributes.isRegularFile()) {
                    throw new IOException("Iris pack fingerprint rejected unsupported entry: " + file);
                }
                String relative = treeRoot.relativize(file).toString().replace(File.separatorChar, '/');
                String logicalRelative = logicalRoot.isEmpty() ? relative : logicalRoot + "/" + relative;
                entries.add(new FingerprintEntry(
                        file,
                        logicalRelative,
                        packName,
                        packName == null ? null : relative,
                        attributes.size(),
                        attributes.lastModifiedTime().toMillis()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException failure) throws IOException {
                throw new IOException("Unable to inspect Iris pack entry: " + file, failure);
            }
        });
    }

    private static boolean isGeneratedPackFile(String name) {
        return name != null && name.endsWith(CODE_WORKSPACE_SUFFIX);
    }

    private record FingerprintEntry(
            Path source,
            String relativePath,
            String packName,
            String packRelativePath,
            long size,
            long lastModifiedMillis
    ) {
    }

    record PackFingerprint(String metadata, String content) {
    }

    public record PackContentSnapshot(String content, Map<String, String> packContents) {
        public PackContentSnapshot {
            content = Objects.requireNonNullElse(content, "");
            packContents = Map.copyOf(Objects.requireNonNullElse(packContents, Map.of()));
        }
    }

    private record FingerprintCache(String content, String metadata) {
    }

    private static void updateDigestInt(MessageDigest digest, int value) {
        for (int shift = Integer.SIZE - Byte.SIZE; shift >= 0; shift -= Byte.SIZE) {
            digest.update((byte) (value >>> shift));
        }
    }

    private static void updateDigestLong(MessageDigest digest, long value) {
        for (int shift = Long.SIZE - Byte.SIZE; shift >= 0; shift -= Byte.SIZE) {
            digest.update((byte) (value >>> shift));
        }
    }

    public static File resolveDatapacksFolder(File worldFolder) {
        File rootFolder = resolveWorldRootFolder(worldFolder);
        return new File(rootFolder, "datapacks");
    }

    static File resolveWorldRootFolder(File worldFolder) {
        if (worldFolder == null) {
            return IrisWorldStorage.levelRoot();
        }
        return IrisWorldStorage.levelRoot(worldFolder);
    }

    private static boolean verifyDataPacksPost() {
        try (Stream<IrisData> stream = allPacks()) {
            return verifyDataPacksPost(stream);
        }
    }

    static boolean verifyDataPacksPost(Stream<IrisData> packs) {
        boolean bad = Objects.requireNonNull(packs, "Iris packs")
                .map(data -> {
                    IrisLogging.debug("Checking Pack: " + data.getDataFolder().getPath());
                    ResourceLoader<IrisDimension> loader = data.getDimensionLoader();
                    return loader.loadAll(loader.getPossibleKeys())
                            .stream()
                            .filter(Objects::nonNull)
                            .map(dimension -> verifyDataPackInstalled(dimension, false))
                            .toList()
                            .contains(false);
                })
                .toList()
                .contains(true);
        if (!bad) {
            return false;
        }
        if (INMS.get().supportsDataPacks()) {
            IrisLogging.warn(POST_COMPILE_RESTART_WARNING);

            for (Player i : Bukkit.getOnlinePlayers()) {
                if (i.isOp() || i.hasPermission("iris.all")) {
                    VolmitSender sender = new VolmitSender(i, BukkitPlatform.volmitPlugin().getTag("WARNING"));
                    sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.SERVER_CONFIGURATOR_THERE_ARE_SOME_IRIS_PACKS_THAT_HAVE_CUSTOM_BIOMES_THEM));
                    sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.SERVER_CONFIGURATOR_YOU_NEED_RESTART_YOUR_SERVER_USE_THESE_PACKS));
                }
            }

        }
        return true;
    }

    public static void restart() {
        restart("New data pack entries have been installed in Iris.");
    }

    public static void restart(String reason) {
        requireDatapackRestart();
        LifecycleOperationCoordinator.get().quiesceForRestart(() -> J.s(() -> {
            IrisLogging.warn(reason + " Restarting server to restore a safe lifecycle boundary.");
            J.s(() -> {
                IrisLogging.warn("Looks like the restart command didn't work. Stopping the server instead!");
                Bukkit.shutdown();
            }, 100);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "restart");
        }));
    }

    public static void restartAtStartupBoundary(String reason) {
        String restartReason = reason == null || reason.isBlank()
                ? "Iris startup validation requires a restart."
                : reason.trim();
        IrisLogging.warn(restartReason + " Restarting server before default worlds are loaded.");
        boolean restartInvoked = false;
        try {
            restartInvoked = invokeImmediateRestartIfSupported(Bukkit.class);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            Throwable cause = failure instanceof InvocationTargetException invocationFailure
                    && invocationFailure.getCause() != null
                    ? invocationFailure.getCause()
                    : failure;
            IrisLogging.reportError("Unable to restart the server at the Iris startup boundary.", cause);
        }
        if (restartInvoked) {
            IrisLogging.error("The immediate Iris startup restart returned unexpectedly; stopping the server instead.");
        } else {
            IrisLogging.warn("This server has no immediate restart API; stopping at the Iris startup boundary instead.");
        }
        try {
            Bukkit.shutdown();
        } catch (Throwable failure) {
            IrisLogging.reportError("Unable to stop the server after the Iris startup restart returned.", failure);
        }
    }

    static boolean invokeImmediateRestartIfSupported(Class<?> bukkitApi) throws ReflectiveOperationException {
        Method restartMethod;
        try {
            restartMethod = Objects.requireNonNull(bukkitApi, "Bukkit API class").getMethod("restart");
        } catch (NoSuchMethodException ignored) {
            return false;
        }
        restartMethod.invoke(null);
        return true;
    }

    public static boolean verifyDataPackInstalled(IrisDimension dimension) {
        return verifyDataPackInstalled(dimension, true);
    }

    private static boolean verifyDataPackInstalled(IrisDimension dimension, boolean reportRuntimeFailure) {
        KSet<String> keys = new KSet<>();
        boolean warn = false;

        for (IrisBiome i : dimension.getAllBiomes(dimension::getLoader)) {
            if (i.isCustom()) {
                for (IrisBiomeCustom j : i.getCustomDerivitives()) {
                    keys.add(dimension.getLoader().customBiomeResourceKey(dimension, j));
                }
            }
        }
        String key = getWorld(dimension.getLoader());
        if (key == null) key = dimension.getLoadKey();
        else key += "/" + dimension.getLoadKey();

        if (!INMS.get().supportsDataPacks()) {
            if (!keys.isEmpty()) {
                IrisLogging.warn("Pack " + key + " has " + keys.size() + " custom biome(s). ");
                IrisLogging.warn("Your server version does not yet support datapacks for iris.");
                IrisLogging.warn("The world will generate these biomes as backup biomes.");
            }

            return true;
        }

        for (String i : keys) {
            Object o = INMS.get().getCustomBiomeBaseFor(i);

            if (o == null) {
                if (reportRuntimeFailure) {
                    IrisLogging.warn("The Biome " + i + " is not registered on the server.");
                }
                warn = true;
            }
        }

        if (INMS.get().missingDimensionTypes(dimension.getDimensionTypeKey())) {
            if (reportRuntimeFailure) {
                IrisLogging.warn("The Dimension Type for " + dimension.getLoadFile() + " is not registered on the server.");
            }
            warn = true;
        }

        if (warn && reportRuntimeFailure) {
            IrisLogging.error("The Pack " + key + " is INCAPABLE of generating custom biomes");
            IrisLogging.error("If not done automatically, restart your server before generating with this pack!");
        }

        return !warn;
    }

    public static Stream<IrisData> allPacks() {
        Stream<File> locals = PackDirectoryResolver.listVisiblePackDirectories(
                IrisPlatforms.get().packsFolder()
        ).stream();
        return Stream.concat(locals
                .filter(base -> {
                    File[] content = new File(base, "dimensions").listFiles();
                    return content != null && content.length > 0;
                })
                .map(IrisData::get), IrisWorlds.get().getPacks());
    }

    @Nullable
    public static String getWorld(@NonNull IrisData data) {
        Path packPath = data.getDataFolder().toPath().toAbsolutePath().normalize();
        Path irisPath = packPath.getParent();
        if (irisPath == null || !"pack".equals(packPath.getFileName().toString()) || !"iris".equals(irisPath.getFileName().toString())) {
            return null;
        }

        Path dimensionPath = irisPath.getParent();
        if (dimensionPath == null) {
            return null;
        }
        File dimensionRoot = dimensionPath.toFile();
        NamespacedKey key = IrisWorldStorage.keyFromDimensionRoot(IrisWorldStorage.levelRoot(), dimensionRoot).orElse(null);
        return key == null ? null : key.toString();
    }

    public record LoadedDatapackRuntimeInvalidation(
            long generation,
            boolean wasReady,
            String fingerprint
    ) {
        public LoadedDatapackRuntimeInvalidation {
            fingerprint = Objects.requireNonNullElse(fingerprint, "");
        }
    }

}
