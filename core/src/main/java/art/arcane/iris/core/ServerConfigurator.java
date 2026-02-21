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

import art.arcane.iris.Iris;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.core.nms.INMS;
import art.arcane.iris.core.nms.datapack.DataVersion;
import art.arcane.iris.core.nms.datapack.IDataFixer;
import art.arcane.iris.engine.object.*;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.collection.KSet;
import art.arcane.iris.util.common.format.C;
import art.arcane.iris.util.common.misc.ServerProperties;
import art.arcane.iris.util.common.plugin.VolmitSender;
import art.arcane.iris.util.common.scheduling.J;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.stream.Stream;

public class ServerConfigurator {
    private static volatile boolean deferredInstallPending = false;

    public static void configure() {
        IrisSettings.IrisSettingsAutoconfiguration s = IrisSettings.get().getAutoConfiguration();
        if (s.isConfigureSpigotTimeoutTime()) {
            J.attempt(ServerConfigurator::increaseKeepAliveSpigot);
        }

        if (s.isConfigurePaperWatchdogDelay()) {
            J.attempt(ServerConfigurator::increasePaperWatchdog);
        }

        if (shouldDeferInstallUntilWorldsReady()) {
            deferredInstallPending = true;
            return;
        }

        deferredInstallPending = false;
        installDataPacks(true);
    }

    public static void configureIfDeferred() {
        if (!deferredInstallPending) {
            return;
        }

        configure();
        if (deferredInstallPending) {
            J.a(ServerConfigurator::configureIfDeferred, 20);
        }
    }

    private static void increaseKeepAliveSpigot() throws IOException, InvalidConfigurationException {
        File spigotConfig = new File("spigot.yml");
        FileConfiguration f = new YamlConfiguration();
        f.load(spigotConfig);
        long tt = f.getLong("settings.timeout-time");

        long spigotTimeout = TimeUnit.MINUTES.toSeconds(5);

        if (tt < spigotTimeout) {
            Iris.warn("Updating spigot.yml timeout-time: " + tt + " -> " + spigotTimeout + " (5 minutes)");
            Iris.warn("You can disable this change (autoconfigureServer) in Iris settings, then change back the value.");
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
            Iris.warn("Updating paper.yml watchdog early-warning-delay: " + tt + " -> " + watchdog + " (3 minutes)");
            Iris.warn("You can disable this change (autoconfigureServer) in Iris settings, then change back the value.");
            f.set("watchdog.early-warning-delay", watchdog);
            f.save(spigotConfig);
        }
    }

    private static KList<File> getDatapacksFolder() {
        if (!IrisSettings.get().getGeneral().forceMainWorld.isEmpty()) {
            return new KList<File>().qadd(new File(Bukkit.getWorldContainer(), IrisSettings.get().getGeneral().forceMainWorld + "/datapacks"));
        }
        KList<File> worlds = new KList<>();
        Bukkit.getServer().getWorlds().forEach(w -> worlds.add(new File(w.getWorldFolder(), "datapacks")));
        if (worlds.isEmpty()) worlds.add(new File(Bukkit.getWorldContainer(), ServerProperties.LEVEL_NAME + "/datapacks"));
        return worlds;
    }

    public static boolean installDataPacks(boolean fullInstall) {
        return installDataPacks(fullInstall, true);
    }

    public static boolean installDataPacks(boolean fullInstall, boolean includeExternal) {
        IDataFixer fixer = DataVersion.getDefault();
        if (fixer == null) {
            DataVersion fallback = DataVersion.getLatest();
            Iris.warn("Primary datapack fixer was null, forcing latest fixer: " + fallback.getVersion());
            fixer = fallback.get();
        }
        return installDataPacks(fixer, fullInstall, includeExternal);
    }

    public static boolean installDataPacks(IDataFixer fixer, boolean fullInstall) {
        return installDataPacks(fixer, fullInstall, true);
    }

    public static boolean installDataPacks(IDataFixer fixer, boolean fullInstall, boolean includeExternal) {
        if (fixer == null) {
            Iris.error("Unable to install datapacks, fixer is null!");
            return false;
        }
        if (fullInstall || includeExternal) {
            Iris.info("Checking Data Packs...");
        } else {
            Iris.verbose("Checking Data Packs...");
        }
        DimensionHeight height = new DimensionHeight(fixer);
        KList<File> folders = getDatapacksFolder();
        if (includeExternal) {
            installExternalDataPacks(folders);
        }
        KMap<String, KSet<String>> biomes = new KMap<>();

        try (Stream<IrisData> stream = allPacks()) {
            stream.flatMap(height::merge)
                    .parallel()
                    .forEach(dim -> {
                        Iris.verbose("  Checking Dimension " + dim.getLoadFile().getPath());
                        dim.installBiomes(fixer, dim::getLoader, folders, biomes.computeIfAbsent(dim.getLoadKey(), k -> new KSet<>()));
                        dim.installDimensionType(fixer, folders);
                    });
        }
        IrisDimension.writeShared(folders, height);
        if (fullInstall || includeExternal) {
            Iris.info("Data Packs Setup!");
        } else {
            Iris.verbose("Data Packs Setup!");
        }

        return fullInstall && verifyDataPacksPost(IrisSettings.get().getAutoConfiguration().isAutoRestartOnCustomBiomeInstall());
    }

    private static void installExternalDataPacks(KList<File> folders) {
        if (!IrisSettings.get().getGeneral().isImportExternalDatapacks()) {
            return;
        }

        KList<ExternalDataPackPipeline.DatapackRequest> requests = collectExternalDatapackRequests();
        KMap<String, KList<File>> worldDatapackFoldersByPack = collectWorldDatapackFoldersByPack(folders);
        ExternalDataPackPipeline.PipelineSummary summary = ExternalDataPackPipeline.processDatapacks(requests, worldDatapackFoldersByPack);
        if (summary.getLegacyDownloadRemovals() > 0) {
            Iris.verbose("Removed " + summary.getLegacyDownloadRemovals() + " legacy global datapack downloads.");
        }
        if (summary.getLegacyWorldCopyRemovals() > 0) {
            Iris.verbose("Removed " + summary.getLegacyWorldCopyRemovals() + " legacy managed world datapack copies.");
        }
        int loadedDatapackCount = Math.max(0, summary.getRequests() - summary.getOptionalFailures() - summary.getRequiredFailures());
        Iris.info("Loaded Datapacks into Iris: " + loadedDatapackCount + "!");
        if (summary.getRequiredFailures() > 0) {
            throw new IllegalStateException("Required external datapack setup failed for " + summary.getRequiredFailures() + " request(s).");
        }
    }

    private static boolean shouldDeferInstallUntilWorldsReady() {
        String forcedMainWorld = IrisSettings.get().getGeneral().forceMainWorld;
        if (forcedMainWorld != null && !forcedMainWorld.isBlank()) {
            return false;
        }

        return Bukkit.getServer().getWorlds().isEmpty();
    }

    private static KList<ExternalDataPackPipeline.DatapackRequest> collectExternalDatapackRequests() {
        KMap<String, ExternalDataPackPipeline.DatapackRequest> deduplicated = new KMap<>();
        try (Stream<IrisData> stream = allPacks()) {
            stream.forEach(data -> collectExternalDatapackRequestsForPack(data, deduplicated));
        }

        return new KList<>(deduplicated.v());
    }

    private static void collectExternalDatapackRequestsForPack(IrisData data, KMap<String, ExternalDataPackPipeline.DatapackRequest> deduplicated) {
        ResourceLoader<IrisDimension> loader = data.getDimensionLoader();
        if (loader == null) {
            Iris.warn("Skipping external datapack request discovery for pack " + data.getDataFolder().getName() + " because dimension loader is unavailable.");
            return;
        }

        String[] possibleKeys = loader.getPossibleKeys();
        if (possibleKeys == null || possibleKeys.length == 0) {
            File dimensionsFolder = new File(data.getDataFolder(), "dimensions");
            File[] dimensionFiles = dimensionsFolder.listFiles((dir, name) -> name != null && name.toLowerCase().endsWith(".json"));
            int dimensionFileCount = dimensionFiles == null ? 0 : dimensionFiles.length;
            Iris.warn("Pack " + data.getDataFolder().getName() + " has no loadable dimension keys. Dimension folder json files=" + dimensionFileCount + ". External datapacks in this pack cannot be discovered.");
            return;
        }

        KList<IrisDimension> dimensions = loader.loadAll(possibleKeys);
        int scannedDimensions = 0;
        int dimensionsWithExternalEntries = 0;
        int enabledEntries = 0;
        int disabledEntries = 0;
        int skippedBlankUrl = 0;
        int scopedRequests = 0;
        int unscopedRequests = 0;
        int dedupeMerges = 0;
        for (IrisDimension dimension : dimensions) {
            if (dimension == null) {
                continue;
            }

            scannedDimensions++;
            KList<IrisExternalDatapack> externalDatapacks = dimension.getExternalDatapacks();
            if (externalDatapacks == null || externalDatapacks.isEmpty()) {
                continue;
            }

            dimensionsWithExternalEntries++;
            String targetPack = sanitizePackName(dimension.getLoadKey());
            if (targetPack.isBlank()) {
                targetPack = sanitizePackName(data.getDataFolder().getName());
            }

            String environment = ExternalDataPackPipeline.normalizeEnvironmentValue(dimension.getEnvironment() == null ? null : dimension.getEnvironment().name());
            LinkedHashMap<String, IrisExternalDatapack> definitionsById = new LinkedHashMap<>();
            for (IrisExternalDatapack externalDatapack : externalDatapacks) {
                if (externalDatapack == null) {
                    disabledEntries++;
                    continue;
                }

                if (!externalDatapack.isEnabled()) {
                    disabledEntries++;
                    continue;
                }

                String url = externalDatapack.getUrl() == null ? "" : externalDatapack.getUrl().trim();
                if (url.isBlank()) {
                    skippedBlankUrl++;
                    continue;
                }

                enabledEntries++;
                String requestId = normalizeExternalDatapackId(externalDatapack.getId(), url);
                IrisExternalDatapack existingDefinition = definitionsById.put(requestId, externalDatapack);
                if (existingDefinition != null) {
                    Iris.warn("Duplicate external datapack id '" + requestId + "' in dimension " + dimension.getLoadKey() + ". Latest entry wins.");
                }
            }

            if (definitionsById.isEmpty()) {
                continue;
            }

            KMap<String, KList<ScopedBindingGroup>> scopedGroups = resolveScopedBindingGroups(data, dimension, definitionsById);
            for (Map.Entry<String, IrisExternalDatapack> entry : definitionsById.entrySet()) {
                String requestId = entry.getKey();
                IrisExternalDatapack definition = entry.getValue();
                String url = definition.getUrl() == null ? "" : definition.getUrl().trim();
                if (url.isBlank()) {
                    continue;
                }

                KList<ScopedBindingGroup> groups = scopedGroups.get(requestId);
                if (groups == null || groups.isEmpty()) {
                    String scopeKey = buildRootScopeKey(dimension.getLoadKey(), requestId);
                    ExternalDataPackPipeline.DatapackRequest request = new ExternalDataPackPipeline.DatapackRequest(
                            requestId,
                            url,
                            targetPack,
                            environment,
                            definition.isRequired(),
                            definition.isReplaceVanilla(),
                            definition.getReplaceTargets(),
                            definition.getStructurePatches(),
                            Set.of(),
                            scopeKey,
                            !definition.isReplaceVanilla(),
                            Set.of()
                    );
                    dedupeMerges += mergeDeduplicatedRequest(deduplicated, request);
                    unscopedRequests++;
                    Iris.verbose("External datapack scope resolved: id=" + requestId
                            + ", targetPack=" + targetPack
                            + ", dimension=" + dimension.getLoadKey()
                            + ", scope=dimension-root"
                            + ", forcedBiomes=0"
                            + ", replaceVanilla=" + definition.isReplaceVanilla()
                            + ", alongsideMode=" + (!definition.isReplaceVanilla())
                            + ", required=" + definition.isRequired());
                    continue;
                }

                for (ScopedBindingGroup group : groups) {
                    ExternalDataPackPipeline.DatapackRequest request = new ExternalDataPackPipeline.DatapackRequest(
                            requestId,
                            url,
                            targetPack,
                            environment,
                            group.required(),
                            group.replaceVanilla(),
                            definition.getReplaceTargets(),
                            definition.getStructurePatches(),
                            group.forcedBiomeKeys(),
                            group.scopeKey(),
                            !group.replaceVanilla(),
                            Set.of()
                    );
                    dedupeMerges += mergeDeduplicatedRequest(deduplicated, request);
                    scopedRequests++;
                    Iris.verbose("External datapack scope resolved: id=" + requestId
                            + ", targetPack=" + targetPack
                            + ", dimension=" + dimension.getLoadKey()
                            + ", scope=" + group.source()
                            + ", forcedBiomes=" + group.forcedBiomeKeys().size()
                            + ", replaceVanilla=" + group.replaceVanilla()
                            + ", alongsideMode=" + (!group.replaceVanilla())
                            + ", required=" + group.required());
                }
            }
        }

        if (scannedDimensions == 0) {
            Iris.warn("Pack " + data.getDataFolder().getName() + " did not resolve any dimensions during external datapack discovery.");
            return;
        }

        if (dimensionsWithExternalEntries > 0 || enabledEntries > 0 || disabledEntries > 0 || skippedBlankUrl > 0) {
            Iris.verbose("External datapack discovery for pack " + data.getDataFolder().getName()
                    + ": dimensions=" + scannedDimensions
                    + ", withEntries=" + dimensionsWithExternalEntries
                    + ", enabled=" + enabledEntries
                    + ", disabled=" + disabledEntries
                    + ", skippedBlankUrl=" + skippedBlankUrl
                    + ", scopedRequests=" + scopedRequests
                    + ", unscopedRequests=" + unscopedRequests
                    + ", dedupeMerges=" + dedupeMerges);
        }
    }

    private static KMap<String, KList<ScopedBindingGroup>> resolveScopedBindingGroups(
            IrisData data,
            IrisDimension dimension,
            Map<String, IrisExternalDatapack> definitionsById
    ) {
        KMap<String, KList<ScopedBindingGroup>> groupedRequestsById = new KMap<>();
        if (definitionsById == null || definitionsById.isEmpty()) {
            return groupedRequestsById;
        }

        ResourceLoader<IrisRegion> regionLoader = data.getRegionLoader();
        ResourceLoader<IrisBiome> biomeLoader = data.getBiomeLoader();
        if (regionLoader == null || biomeLoader == null) {
            return groupedRequestsById;
        }

        String biomeNamespace = resolveBiomeNamespace(dimension);
        LinkedHashMap<String, IrisBiome> biomeCache = new LinkedHashMap<>();
        LinkedHashMap<String, IrisRegion> regions = new LinkedHashMap<>();
        KList<String> dimensionRegions = dimension.getRegions();
        if (dimensionRegions != null) {
            for (String regionKey : dimensionRegions) {
                String normalizedRegion = normalizeResourceReference(regionKey);
                if (normalizedRegion.isBlank()) {
                    continue;
                }

                IrisRegion region = regionLoader.load(normalizedRegion, false);
                if (region != null) {
                    regions.put(normalizedRegion, region);
                }
            }
        }

        LinkedHashMap<String, KList<ScopedBindingCandidate>> candidatesById = new LinkedHashMap<>();
        LinkedHashSet<String> discoveryBiomeKeys = new LinkedHashSet<>();
        for (IrisRegion region : regions.values()) {
            Set<String> expandedRegionBiomes = collectRegionBiomeKeys(region, true, biomeLoader, biomeCache);
            discoveryBiomeKeys.addAll(expandedRegionBiomes);

            KList<IrisExternalDatapackBinding> bindings = region.getExternalDatapacks();
            if (bindings == null || bindings.isEmpty()) {
                continue;
            }

            for (IrisExternalDatapackBinding binding : bindings) {
                if (binding == null || !binding.isEnabled()) {
                    continue;
                }

                String id = normalizeExternalDatapackId(binding.getId(), "");
                if (id.isBlank()) {
                    continue;
                }

                IrisExternalDatapack definition = definitionsById.get(id);
                if (definition == null) {
                    Iris.warn("Ignoring region external datapack binding id '" + id + "' in " + region.getLoadKey() + " because no matching dimension externalDatapacks entry exists.");
                    continue;
                }

                boolean replaceVanilla = binding.getReplaceVanillaOverride() == null
                        ? definition.isReplaceVanilla()
                        : binding.getReplaceVanillaOverride();
                boolean required = binding.getRequiredOverride() == null
                        ? definition.isRequired()
                        : binding.getRequiredOverride();
                Set<String> regionBiomeKeys = collectRegionBiomeKeys(region, binding.isIncludeChildren(), biomeLoader, biomeCache);
                Set<String> runtimeBiomeKeys = resolveRuntimeBiomeKeys(regionBiomeKeys, biomeNamespace, biomeLoader, biomeCache);
                if (runtimeBiomeKeys.isEmpty()) {
                    continue;
                }

                KList<ScopedBindingCandidate> candidates = candidatesById.computeIfAbsent(id, key -> new KList<>());
                candidates.add(new ScopedBindingCandidate("region", region.getLoadKey(), 1, replaceVanilla, required, runtimeBiomeKeys));
            }
        }

        for (String biomeKey : discoveryBiomeKeys) {
            IrisBiome biome = loadBiomeFromCache(biomeKey, biomeLoader, biomeCache);
            if (biome == null) {
                continue;
            }

            KList<IrisExternalDatapackBinding> bindings = biome.getExternalDatapacks();
            if (bindings == null || bindings.isEmpty()) {
                continue;
            }

            for (IrisExternalDatapackBinding binding : bindings) {
                if (binding == null || !binding.isEnabled()) {
                    continue;
                }

                String id = normalizeExternalDatapackId(binding.getId(), "");
                if (id.isBlank()) {
                    continue;
                }

                IrisExternalDatapack definition = definitionsById.get(id);
                if (definition == null) {
                    Iris.warn("Ignoring biome external datapack binding id '" + id + "' in " + biome.getLoadKey() + " because no matching dimension externalDatapacks entry exists.");
                    continue;
                }

                boolean replaceVanilla = binding.getReplaceVanillaOverride() == null
                        ? definition.isReplaceVanilla()
                        : binding.getReplaceVanillaOverride();
                boolean required = binding.getRequiredOverride() == null
                        ? definition.isRequired()
                        : binding.getRequiredOverride();
                Set<String> biomeSelection = collectBiomeKeys(biome.getLoadKey(), binding.isIncludeChildren(), biomeLoader, biomeCache);
                Set<String> runtimeBiomeKeys = resolveRuntimeBiomeKeys(biomeSelection, biomeNamespace, biomeLoader, biomeCache);
                if (runtimeBiomeKeys.isEmpty()) {
                    continue;
                }

                KList<ScopedBindingCandidate> candidates = candidatesById.computeIfAbsent(id, key -> new KList<>());
                candidates.add(new ScopedBindingCandidate("biome", biome.getLoadKey(), 2, replaceVanilla, required, runtimeBiomeKeys));
            }
        }

        for (Map.Entry<String, KList<ScopedBindingCandidate>> entry : candidatesById.entrySet()) {
            String id = entry.getKey();
            KList<ScopedBindingCandidate> candidates = entry.getValue();
            if (candidates == null || candidates.isEmpty()) {
                continue;
            }

            LinkedHashMap<String, ScopedBindingSelection> selectedByBiome = new LinkedHashMap<>();
            for (ScopedBindingCandidate candidate : candidates) {
                if (candidate == null || candidate.forcedBiomeKeys() == null || candidate.forcedBiomeKeys().isEmpty()) {
                    continue;
                }

                ArrayList<String> sortedBiomeKeys = new ArrayList<>(candidate.forcedBiomeKeys());
                sortedBiomeKeys.sort(String::compareTo);
                for (String runtimeBiomeKey : sortedBiomeKeys) {
                    ScopedBindingSelection selected = selectedByBiome.get(runtimeBiomeKey);
                    if (selected == null) {
                        selectedByBiome.put(runtimeBiomeKey, new ScopedBindingSelection(
                                candidate.priority(),
                                candidate.replaceVanilla(),
                                candidate.required(),
                                candidate.sourceType(),
                                candidate.sourceKey()
                        ));
                        continue;
                    }

                    if (candidate.priority() > selected.priority()) {
                        selectedByBiome.put(runtimeBiomeKey, new ScopedBindingSelection(
                                candidate.priority(),
                                candidate.replaceVanilla(),
                                candidate.required(),
                                candidate.sourceType(),
                                candidate.sourceKey()
                        ));
                        continue;
                    }

                    if (candidate.priority() == selected.priority()
                            && (candidate.replaceVanilla() != selected.replaceVanilla() || candidate.required() != selected.required())) {
                        Iris.warn("External datapack scope conflict for id=" + id
                                + ", biomeKey=" + runtimeBiomeKey
                                + ", kept=" + selected.sourceType() + "/" + selected.sourceKey()
                                + ", ignored=" + candidate.sourceType() + "/" + candidate.sourceKey());
                    }
                }
            }

            LinkedHashMap<String, LinkedHashSet<String>> groupedBiomes = new LinkedHashMap<>();
            LinkedHashMap<String, ScopedBindingSelection> groupedSelection = new LinkedHashMap<>();
            for (Map.Entry<String, ScopedBindingSelection> selectedEntry : selectedByBiome.entrySet()) {
                String runtimeBiomeKey = selectedEntry.getKey();
                ScopedBindingSelection selection = selectedEntry.getValue();
                String groupKey = selection.replaceVanilla() + "|" + selection.required();
                groupedBiomes.computeIfAbsent(groupKey, key -> new LinkedHashSet<>()).add(runtimeBiomeKey);
                groupedSelection.putIfAbsent(groupKey, selection);
            }

            for (Map.Entry<String, LinkedHashSet<String>> groupedEntry : groupedBiomes.entrySet()) {
                LinkedHashSet<String> runtimeBiomeKeys = groupedEntry.getValue();
                if (runtimeBiomeKeys == null || runtimeBiomeKeys.isEmpty()) {
                    continue;
                }

                ScopedBindingSelection selection = groupedSelection.get(groupedEntry.getKey());
                if (selection == null) {
                    continue;
                }

                Set<String> forcedBiomeKeys = Set.copyOf(runtimeBiomeKeys);
                String scopeKey = buildScopedScopeKey(dimension.getLoadKey(), id, selection.sourceType(), selection.sourceKey(), forcedBiomeKeys);
                String source = selection.sourceType() + ":" + selection.sourceKey();
                KList<ScopedBindingGroup> groups = groupedRequestsById.computeIfAbsent(id, key -> new KList<>());
                groups.add(new ScopedBindingGroup(selection.replaceVanilla(), selection.required(), forcedBiomeKeys, scopeKey, source));
            }
        }

        return groupedRequestsById;
    }

    private static Set<String> collectRegionBiomeKeys(
            IrisRegion region,
            boolean includeChildren,
            ResourceLoader<IrisBiome> biomeLoader,
            Map<String, IrisBiome> biomeCache
    ) {
        LinkedHashSet<String> regionBiomeKeys = new LinkedHashSet<>();
        if (region == null) {
            return regionBiomeKeys;
        }

        addAllResourceReferences(regionBiomeKeys, region.getLandBiomes());
        addAllResourceReferences(regionBiomeKeys, region.getSeaBiomes());
        addAllResourceReferences(regionBiomeKeys, region.getShoreBiomes());
        addAllResourceReferences(regionBiomeKeys, region.getCaveBiomes());
        if (!includeChildren) {
            return regionBiomeKeys;
        }

        LinkedHashSet<String> expanded = new LinkedHashSet<>();
        for (String biomeKey : regionBiomeKeys) {
            expanded.addAll(collectBiomeKeys(biomeKey, true, biomeLoader, biomeCache));
        }
        return expanded;
    }

    private static Set<String> collectBiomeKeys(
            String biomeKey,
            boolean includeChildren,
            ResourceLoader<IrisBiome> biomeLoader,
            Map<String, IrisBiome> biomeCache
    ) {
        LinkedHashSet<String> resolved = new LinkedHashSet<>();
        String normalizedBiomeKey = normalizeResourceReference(biomeKey);
        if (normalizedBiomeKey.isBlank()) {
            return resolved;
        }

        if (!includeChildren) {
            resolved.add(normalizedBiomeKey);
            return resolved;
        }

        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(normalizedBiomeKey);
        while (!queue.isEmpty()) {
            String next = normalizeResourceReference(queue.removeFirst());
            if (next.isBlank() || !resolved.add(next)) {
                continue;
            }

            IrisBiome biome = loadBiomeFromCache(next, biomeLoader, biomeCache);
            if (biome == null) {
                continue;
            }

            addQueueResourceReferences(queue, biome.getChildren());
        }

        return resolved;
    }

    private static Set<String> resolveRuntimeBiomeKeys(
            Set<String> irisBiomeKeys,
            String biomeNamespace,
            ResourceLoader<IrisBiome> biomeLoader,
            Map<String, IrisBiome> biomeCache
    ) {
        LinkedHashSet<String> resolved = new LinkedHashSet<>();
        if (irisBiomeKeys == null || irisBiomeKeys.isEmpty()) {
            return resolved;
        }

        for (String irisBiomeKey : irisBiomeKeys) {
            String normalizedBiomeKey = normalizeResourceReference(irisBiomeKey);
            if (normalizedBiomeKey.isBlank()) {
                continue;
            }

            IrisBiome biome = loadBiomeFromCache(normalizedBiomeKey, biomeLoader, biomeCache);
            if (biome == null) {
                continue;
            }

            if (biome.isCustom() && biome.getCustomDerivitives() != null && !biome.getCustomDerivitives().isEmpty()) {
                for (IrisBiomeCustom customDerivative : biome.getCustomDerivitives()) {
                    if (customDerivative == null) {
                        continue;
                    }

                    String customId = normalizeResourceReference(customDerivative.getId());
                    if (customId.isBlank()) {
                        continue;
                    }
                    resolved.add((biomeNamespace + ":" + customId).toLowerCase(Locale.ROOT));
                }
                continue;
            }

            Biome vanillaDerivative = biome.getVanillaDerivative();
            NamespacedKey vanillaKey = vanillaDerivative == null ? null : vanillaDerivative.getKey();
            if (vanillaKey != null) {
                resolved.add(vanillaKey.toString().toLowerCase(Locale.ROOT));
            }
        }

        return resolved;
    }

    private static String resolveBiomeNamespace(IrisDimension dimension) {
        if (dimension == null) {
            return "iris";
        }

        String namespace = dimension.getLoadKey() == null ? "" : dimension.getLoadKey().trim().toLowerCase(Locale.ROOT);
        namespace = namespace.replaceAll("[^a-z0-9_\\-.]", "_");
        namespace = namespace.replaceAll("_+", "_");
        namespace = namespace.replaceAll("^_+", "");
        namespace = namespace.replaceAll("_+$", "");
        if (namespace.isBlank()) {
            return "iris";
        }
        return namespace;
    }

    private static IrisBiome loadBiomeFromCache(
            String biomeKey,
            ResourceLoader<IrisBiome> biomeLoader,
            Map<String, IrisBiome> biomeCache
    ) {
        if (biomeLoader == null) {
            return null;
        }

        String normalizedBiomeKey = normalizeResourceReference(biomeKey);
        if (normalizedBiomeKey.isBlank()) {
            return null;
        }

        if (biomeCache.containsKey(normalizedBiomeKey)) {
            return biomeCache.get(normalizedBiomeKey);
        }

        IrisBiome biome = biomeLoader.load(normalizedBiomeKey, false);
        if (biome != null) {
            biomeCache.put(normalizedBiomeKey, biome);
        }
        return biome;
    }

    private static void addAllResourceReferences(Set<String> destination, KList<String> references) {
        if (destination == null || references == null || references.isEmpty()) {
            return;
        }

        for (String reference : references) {
            String normalized = normalizeResourceReference(reference);
            if (!normalized.isBlank()) {
                destination.add(normalized);
            }
        }
    }

    private static void addQueueResourceReferences(ArrayDeque<String> queue, KList<String> references) {
        if (queue == null || references == null || references.isEmpty()) {
            return;
        }

        for (String reference : references) {
            String normalized = normalizeResourceReference(reference);
            if (!normalized.isBlank()) {
                queue.addLast(normalized);
            }
        }
    }

    private static String normalizeResourceReference(String reference) {
        if (reference == null) {
            return "";
        }

        String normalized = reference.trim().replace('\\', '/');
        normalized = normalized.replaceAll("/+", "/");
        normalized = normalized.replaceAll("^/+", "");
        normalized = normalized.replaceAll("/+$", "");
        return normalized;
    }

    private static int mergeDeduplicatedRequest(
            KMap<String, ExternalDataPackPipeline.DatapackRequest> deduplicated,
            ExternalDataPackPipeline.DatapackRequest request
    ) {
        if (request == null) {
            return 0;
        }

        String dedupeKey = request.getDedupeKey();
        ExternalDataPackPipeline.DatapackRequest existing = deduplicated.get(dedupeKey);
        if (existing == null) {
            deduplicated.put(dedupeKey, request);
            return 0;
        }

        deduplicated.put(dedupeKey, existing.merge(request));
        return 1;
    }

    private static String normalizeExternalDatapackId(String id, String fallbackUrl) {
        String normalized = id == null ? "" : id.trim();
        if (!normalized.isBlank()) {
            return normalized.toLowerCase(Locale.ROOT);
        }

        String fallback = fallbackUrl == null ? "" : fallbackUrl.trim();
        if (fallback.isBlank()) {
            return "";
        }
        return fallback.toLowerCase(Locale.ROOT);
    }

    private static String buildRootScopeKey(String dimensionKey, String id) {
        String normalizedDimension = ExternalDataPackPipeline.sanitizePackNameValue(dimensionKey);
        if (normalizedDimension.isBlank()) {
            normalizedDimension = "dimension";
        }
        String normalizedId = ExternalDataPackPipeline.sanitizePackNameValue(id);
        if (normalizedId.isBlank()) {
            normalizedId = "external";
        }
        return "root-" + normalizedDimension + "-" + normalizedId;
    }

    private static String buildScopedScopeKey(String dimensionKey, String id, String sourceType, String sourceKey, Set<String> forcedBiomeKeys) {
        ArrayList<String> sortedBiomes = new ArrayList<>();
        if (forcedBiomeKeys != null) {
            sortedBiomes.addAll(forcedBiomeKeys);
        }
        sortedBiomes.sort(String::compareTo);
        String biomeFingerprint = Integer.toHexString(String.join(",", sortedBiomes).hashCode());
        String normalizedDimension = ExternalDataPackPipeline.sanitizePackNameValue(dimensionKey);
        if (normalizedDimension.isBlank()) {
            normalizedDimension = "dimension";
        }
        String normalizedId = ExternalDataPackPipeline.sanitizePackNameValue(id);
        if (normalizedId.isBlank()) {
            normalizedId = "external";
        }
        String normalizedSourceType = ExternalDataPackPipeline.sanitizePackNameValue(sourceType);
        if (normalizedSourceType.isBlank()) {
            normalizedSourceType = "scope";
        }
        String normalizedSourceKey = ExternalDataPackPipeline.sanitizePackNameValue(sourceKey);
        if (normalizedSourceKey.isBlank()) {
            normalizedSourceKey = "entry";
        }
        return normalizedDimension + "-" + normalizedId + "-" + normalizedSourceType + "-" + normalizedSourceKey + "-" + biomeFingerprint;
    }

    private record ScopedBindingCandidate(
            String sourceType,
            String sourceKey,
            int priority,
            boolean replaceVanilla,
            boolean required,
            Set<String> forcedBiomeKeys
    ) {
    }

    private record ScopedBindingSelection(
            int priority,
            boolean replaceVanilla,
            boolean required,
            String sourceType,
            String sourceKey
    ) {
    }

    private record ScopedBindingGroup(
            boolean replaceVanilla,
            boolean required,
            Set<String> forcedBiomeKeys,
            String scopeKey,
            String source
    ) {
    }

    private static KMap<String, KList<File>> collectWorldDatapackFoldersByPack(KList<File> fallbackFolders) {
        KMap<String, KList<File>> foldersByPack = new KMap<>();
        KMap<String, String> mappedWorlds = IrisWorlds.get().getWorlds();

        for (String worldName : mappedWorlds.k()) {
            String packName = sanitizePackName(mappedWorlds.get(worldName));
            if (packName.isBlank()) {
                continue;
            }
            File datapacksFolder = new File(Bukkit.getWorldContainer(), worldName + File.separator + "datapacks");
            addWorldDatapackFolder(foldersByPack, packName, datapacksFolder);
        }

        for (org.bukkit.World world : Bukkit.getWorlds()) {
            String worldName = world.getName();
            String mappedPack = mappedWorlds.get(worldName);
            String packName = sanitizePackName(mappedPack);
            if (packName.isBlank()) {
                packName = sanitizePackName(IrisSettings.get().getGenerator().getDefaultWorldType());
            }
            if (packName.isBlank()) {
                continue;
            }
            File datapacksFolder = new File(world.getWorldFolder(), "datapacks");
            addWorldDatapackFolder(foldersByPack, packName, datapacksFolder);
        }

        String defaultPack = sanitizePackName(IrisSettings.get().getGenerator().getDefaultWorldType());
        if (!defaultPack.isBlank()) {
            for (File folder : fallbackFolders) {
                addWorldDatapackFolder(foldersByPack, defaultPack, folder);
            }
        }

        return foldersByPack;
    }

    private static void addWorldDatapackFolder(KMap<String, KList<File>> foldersByPack, String packName, File folder) {
        if (folder == null || packName == null || packName.isBlank()) {
            return;
        }
        KList<File> folders = foldersByPack.computeIfAbsent(packName, k -> new KList<>());
        if (!folders.contains(folder)) {
            folders.add(folder);
        }
    }

    private static String sanitizePackName(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value.trim().toLowerCase().replace("\\", "/");
        sanitized = sanitized.replaceAll("[^a-z0-9_\\-./]", "_");
        sanitized = sanitized.replaceAll("/+", "/");
        sanitized = sanitized.replaceAll("^/+", "");
        sanitized = sanitized.replaceAll("/+$", "");
        if (sanitized.contains("..")) {
            sanitized = sanitized.replace("..", "_");
        }
        return sanitized.replace("/", "_");
    }

    private static boolean verifyDataPacksPost(boolean allowRestarting) {
        try (Stream<IrisData> stream = allPacks()) {
            boolean bad = stream
                    .map(data -> {
                        Iris.verbose("Checking Pack: " + data.getDataFolder().getPath());
                        var loader = data.getDimensionLoader();
                        return loader.loadAll(loader.getPossibleKeys())
                                .stream()
                                .filter(Objects::nonNull)
                                .map(ServerConfigurator::verifyDataPackInstalled)
                                .toList()
                                .contains(false);
                    })
                    .toList()
                    .contains(true);
            if (!bad) return false;
        }


        if (allowRestarting) {
            restart();
        } else if (INMS.get().supportsDataPacks()) {
            Iris.error("============================================================================");
            Iris.error(C.ITALIC + "You need to restart your server to properly generate custom biomes.");
            Iris.error(C.ITALIC + "By continuing, Iris will use backup biomes in place of the custom biomes.");
            Iris.error("----------------------------------------------------------------------------");
            Iris.error(C.UNDERLINE + "IT IS HIGHLY RECOMMENDED YOU RESTART THE SERVER BEFORE GENERATING!");
            Iris.error("============================================================================");

            for (Player i : Bukkit.getOnlinePlayers()) {
                if (i.isOp() || i.hasPermission("iris.all")) {
                    VolmitSender sender = new VolmitSender(i, Iris.instance.getTag("WARNING"));
                    sender.sendMessage("There are some Iris Packs that have custom biomes in them");
                    sender.sendMessage("You need to restart your server to use these packs.");
                }
            }

            J.sleep(3000);
        }
        return true;
    }

    public static void restart() {
        J.s(() -> {
            Iris.warn("New data pack entries have been installed in Iris! Restarting server!");
            Iris.warn("This will only happen when your pack changes (updates/first time setup)");
            Iris.warn("(You can disable this auto restart in iris settings)");
            J.s(() -> {
                Iris.warn("Looks like the restart command didn't work. Stopping the server instead!");
                Bukkit.shutdown();
            }, 100);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "restart");
        });
    }

    public static boolean verifyDataPackInstalled(IrisDimension dimension) {
        KSet<String> keys = new KSet<>();
        boolean warn = false;

        for (IrisBiome i : dimension.getAllBiomes(dimension::getLoader)) {
            if (i.isCustom()) {
                for (IrisBiomeCustom j : i.getCustomDerivitives()) {
                    keys.add(dimension.getLoadKey() + ":" + j.getId());
                }
            }
        }
        String key = getWorld(dimension.getLoader());
        if (key == null) key = dimension.getLoadKey();
        else key += "/" + dimension.getLoadKey();

        if (!INMS.get().supportsDataPacks()) {
            if (!keys.isEmpty()) {
                Iris.warn("===================================================================================");
                Iris.warn("Pack " + key + " has " + keys.size() + " custom biome(s). ");
                Iris.warn("Your server version does not yet support datapacks for iris.");
                Iris.warn("The world will generate these biomes as backup biomes.");
                Iris.warn("====================================================================================");
            }

            return true;
        }

        for (String i : keys) {
            Object o = INMS.get().getCustomBiomeBaseFor(i);

            if (o == null) {
                Iris.warn("The Biome " + i + " is not registered on the server.");
                warn = true;
            }
        }

        if (INMS.get().missingDimensionTypes(dimension.getDimensionTypeKey())) {
            Iris.warn("The Dimension Type for " + dimension.getLoadFile() + " is not registered on the server.");
            warn = true;
        }

        if (warn) {
            Iris.error("The Pack " + key + " is INCAPABLE of generating custom biomes");
            Iris.error("If not done automatically, restart your server before generating with this pack!");
        }

        return !warn;
    }

    public static Stream<IrisData> allPacks() {
        File[] packs = Iris.instance.getDataFolder("packs").listFiles(File::isDirectory);
        Stream<File> locals = packs == null ? Stream.empty() : Arrays.stream(packs);
        return Stream.concat(locals
                .filter( base -> {
                    var content = new File(base, "dimensions").listFiles();
                    return content != null && content.length > 0;
                })
                .map(IrisData::get), IrisWorlds.get().getPacks());
    }

    @Nullable
    public static String getWorld(@NonNull IrisData data) {
        String worldContainer = Bukkit.getWorldContainer().getAbsolutePath();
        if (!worldContainer.endsWith(File.separator)) worldContainer += File.separator;
        
        String path = data.getDataFolder().getAbsolutePath();
        if (!path.startsWith(worldContainer)) return null;
        int l = path.endsWith(File.separator) ? 11 : 10;
        return path.substring(worldContainer.length(), path.length() - l);
    }

    public static class DimensionHeight {
        private final IDataFixer fixer;
        private final AtomicIntegerArray[] dimensions = new AtomicIntegerArray[3];

        public DimensionHeight(IDataFixer fixer) {
            this.fixer = fixer;
            for (int i = 0; i < 3; i++) {
                dimensions[i] = new AtomicIntegerArray(new int[]{
                        Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE
                });
            }
        }

        public Stream<IrisDimension> merge(IrisData data) {
            Iris.verbose("Checking Pack: " + data.getDataFolder().getPath());
            var loader = data.getDimensionLoader();
            return loader.loadAll(loader.getPossibleKeys())
                    .stream()
                    .filter(Objects::nonNull)
                    .peek(this::merge);
        }

        public void merge(IrisDimension dimension) {
            AtomicIntegerArray array = dimensions[dimension.getBaseDimension().ordinal()];
            array.updateAndGet(0, min -> Math.min(min, dimension.getMinHeight()));
            array.updateAndGet(1, max -> Math.max(max, dimension.getMaxHeight()));
            array.updateAndGet(2, logical -> Math.max(logical, dimension.getLogicalHeight()));
        }

        public String[] jsonStrings() {
            var dims = IDataFixer.Dimension.values();
            var arr = new String[3];
            for (int i = 0; i < 3; i++) {
                arr[i] = jsonString(dims[i]);
            }
            return arr;
        }

        public String jsonString(IDataFixer.Dimension dimension) {
            var data = dimensions[dimension.ordinal()];
            int minY = data.get(0);
            int maxY = data.get(1);
            int logicalHeight = data.get(2);
            if (minY == Integer.MAX_VALUE || maxY == Integer.MIN_VALUE || Integer.MIN_VALUE == logicalHeight)
                return null;
            return fixer.createDimension(dimension, minY, maxY - minY, logicalHeight, null).toString(4);
        }
    }
}
