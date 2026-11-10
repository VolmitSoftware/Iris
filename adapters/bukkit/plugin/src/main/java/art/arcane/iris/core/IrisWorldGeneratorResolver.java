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
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.pack.BrokenPackException;
import art.arcane.iris.core.pack.PackDownloader;
import art.arcane.iris.core.pack.PackDirectoryResolver;
import art.arcane.iris.core.pack.PackValidationCache;
import art.arcane.iris.core.pack.PackValidationRegistry;
import art.arcane.iris.core.pack.PackValidationResult;
import art.arcane.iris.core.pack.PackValidator;
import art.arcane.iris.core.service.ExternalDataSVC;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.IrisWorld;
import art.arcane.iris.engine.platform.BukkitChunkGenerator;
import art.arcane.iris.util.common.plugin.VolmitPlugin;
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
import java.util.function.Supplier;

/**
 * Pack validation, dimension lookup, and the world generator / biome provider resolution that the
 * Bukkit plugin entry points delegate to.
 */
public final class IrisWorldGeneratorResolver {
    private static final int VALIDATION_STABILITY_ATTEMPTS = 2;
    private static final String STARTUP_CACHE_FILE = "pack-validation.json";
    private static final String PLUGIN_CONTENT_CACHE_FILE = "pack-validation-with-plugin-content.json";
    private static final Object SNAPSHOT_VALIDATION_LOCK = new Object();
    private static final String IRIS_DIMENSION_NAMESPACE = "iris";
    private static final String PLOT_SQUARED_DISCOVERY_WORLD = "CheckingPlotSquaredGenerator";

    private final VolmitPlugin plugin;

    public IrisWorldGeneratorResolver(VolmitPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Validates every pack before worlds load, so a broken pack refuses its worlds. Block plugins like CraftEngine
     * register their blocks only after that, so the warnings wait for them: once they have loaded, a pack is validated
     * again when the registry changed, and only then does it report the keys that are still unknown.
     */
    public void validateAllPacks() {
        PackValidationRun startupRun = validateAndPublish(STARTUP_CACHE_FILE);
        reportBlockingErrors(startupRun.results());
        IrisStartupValidation.markPacksReady();
        IrisServices.get(ExternalDataSVC.class).afterProviderContentLoads(() -> reportWarningsWithPluginContent(startupRun));
    }

    private void reportWarningsWithPluginContent(PackValidationRun startupRun) {
        if (safeContextFingerprint().equals(startupRun.contextFingerprint())) {
            reportWarnings(startupRun);
            return;
        }
        reportWarnings(validateAndPublish(PLUGIN_CONTENT_CACHE_FILE));
    }

    private static String safeContextFingerprint() {
        try {
            return PackValidationCache.contextFingerprint();
        } catch (RuntimeException exception) {
            Iris.reportError("Could not fingerprint the pack-validation context", exception);
            return "";
        }
    }

    private static void reportBlockingErrors(List<PackValidationResult> results) {
        for (PackValidationResult result : results) {
            if (result.isLoadable()) {
                continue;
            }
            Iris.error("Pack '" + result.getPackName()
                    + "' FAILED validation - world and Studio creation with this pack will be refused. Reasons:");
            for (String reason : result.getBlockingErrors()) {
                Iris.error("  - " + reason);
            }
        }
    }

    private static void reportWarnings(PackValidationRun run) {
        for (PackValidationResult result : run.results()) {
            if (!result.isLoadable()) {
                continue;
            }
            if (!result.getWarnings().isEmpty()) {
                Iris.info("Pack '" + result.getPackName() + "' validated ("
                        + result.getWarnings().size() + " warning(s)).");
                for (String warning : result.getWarnings()) {
                    Iris.warn("  [" + result.getPackName() + "] " + warning);
                }
            } else if (!run.fromCache()) {
                Iris.success("Pack '" + result.getPackName() + "' validated.");
            }
        }
    }

    private PackValidationRun validateAndPublish(String cacheFileName) {
        File packsRoot = plugin.getDataFolder("packs");
        List<File> packDirs = PackDirectoryResolver.listVisiblePackDirectories(packsRoot);
        PackValidationRegistry.clear();
        List<String> packNames = packDirs.stream().map(File::getName).sorted().toList();
        Path cacheFile = IrisPlatforms.get().dataFile("cache", cacheFileName).toPath();
        ServerConfigurator.PackContentSnapshot contentSnapshot =
                new ServerConfigurator.PackContentSnapshot("", Map.of());
        String contextFingerprint = "";
        Optional<List<PackValidationResult>> cached = Optional.empty();
        try {
            contentSnapshot = ServerConfigurator.computePackContentSnapshot(packsRoot);
            contextFingerprint = PackValidationCache.contextFingerprint();
            cached = PackValidationCache.load(
                    cacheFile,
                    contentSnapshot.content(),
                    contextFingerprint,
                    packNames);
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
        }
        return new PackValidationRun(results, contextFingerprint, cached.isPresent());
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
        return PackValidationRegistry.requireLoadable(normalizedRoot);
    }

    @Nullable
    public static IrisDimension loadDimension(@NonNull String worldName, @NonNull String id) {
        File levelRoot = IrisWorldStorage.levelRoot();
        NamespacedKey worldKey = configuredWorldKey(worldName, levelRoot.getName(), levelRoot);
        String configuredWorldName = IrisWorldStorage.configuredWorldName(worldKey, levelRoot.getName());
        File pack = IrisWorldStorage.frozenDimensionRoot(
                        Bukkit.getWorldContainer(),
                        levelRoot,
                        configuredWorldName,
                        worldKey
                )
                .map(IrisWorldStorage::requireFrozenPackRoot)
                .orElse(null);
        IrisDimension dimension = pack == null ? null : IrisData.get(pack).getDimensionLoader().load(id);
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
        Optional<String> startupDenial = IrisStartupValidation.denialReason();
        if (startupDenial.isPresent()) {
            Iris.warn("Keeping configured Iris world '" + worldName
                    + "' generation-locked: " + startupDenial.get());
            return IrisFailClosedChunkGenerator.startupLock(worldName, startupDenial.get());
        }
        ChunkGenerator stagedGenerator = WorldLifecycleStaging.consumeGenerator(worldName);
        if (stagedGenerator != null) {
            Iris.debug("Using staged runtime generator for " + worldName);
            return stagedGenerator;
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
                && (IRIS_DIMENSION_NAMESPACE.equals(worldKey.getNamespace()) || hasFrozenPack(dimensionRoot))) {
            return;
        }
        NamespacedKey messageKey = messageWorldKey(worldName, levelRoot.getName());
        throw new IllegalStateException("'" + worldName + "' (" + messageKey
                + ") has no Iris world storage, so Iris cannot generate it."
                + " Create Iris worlds with /iris create " + IrisWorldStorage.logicalName(messageKey)
                + " type=<pack>; Iris registers them with Multiverse itself.");
    }

    private static boolean hasFrozenPack(File dimensionRoot) {
        Path packRoot = dimensionRoot.toPath().toAbsolutePath().normalize().resolve("iris").resolve("pack");
        return Files.isDirectory(packRoot, LinkOption.NOFOLLOW_LINKS);
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
        File snapshotRoot = IrisWorldStorage.requireFrozenPackRoot(dimensionRoot);
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

        IrisDimension dimension = IrisData.get(snapshotRoot).getDimensionLoader().load(id, false);
        if (dimension == null) {
            throw new IllegalStateException("Frozen Iris pack snapshot at " + snapshotRoot
                    + " does not contain dimension " + id + ".");
        }

        Iris.debug("Assuming IrisDimension: " + dimension.getName());

        IrisWorld world = IrisWorld.builder()
                .platformIdentity(worldKey.toString())
                .name(worldName)
                .seed(1337)
                .worldFolder(dimensionRoot)
                .minHeight(dimension.getMinHeight())
                .maxHeight(dimension.getMaxHeight())
                .build();

        Iris.debug("Generator Config: " + world);

        return new BukkitChunkGenerator(world, false, snapshotRoot, dimension.getLoadKey());
    }

    private record PackValidationRun(List<PackValidationResult> results, String contextFingerprint, boolean fromCache) {
    }

    private record FreshValidation(
            List<File> packDirs,
            ServerConfigurator.PackContentSnapshot contentSnapshot,
            List<PackValidationResult> results,
            boolean stable
    ) {
    }
}
