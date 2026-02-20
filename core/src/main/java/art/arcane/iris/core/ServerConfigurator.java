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
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.stream.Stream;

public class ServerConfigurator {
    public static void configure() {
        IrisSettings.IrisSettingsAutoconfiguration s = IrisSettings.get().getAutoConfiguration();
        if (s.isConfigureSpigotTimeoutTime()) {
            J.attempt(ServerConfigurator::increaseKeepAliveSpigot);
        }

        if (s.isConfigurePaperWatchdogDelay()) {
            J.attempt(ServerConfigurator::increasePaperWatchdog);
        }

        installDataPacks(true);
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
        IDataFixer fixer = DataVersion.getDefault();
        if (fixer == null) {
            DataVersion fallback = DataVersion.getLatest();
            Iris.warn("Primary datapack fixer was null, forcing latest fixer: " + fallback.getVersion());
            fixer = fallback.get();
        }
        return installDataPacks(fixer, fullInstall);
    }

    public static boolean installDataPacks(IDataFixer fixer, boolean fullInstall) {
        if (fixer == null) {
            Iris.error("Unable to install datapacks, fixer is null!");
            return false;
        }
        Iris.info("Checking Data Packs...");
        DimensionHeight height = new DimensionHeight(fixer);
        KList<File> folders = getDatapacksFolder();
        installExternalDataPacks(folders);
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
        Iris.info("Data Packs Setup!");

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
            Iris.info("Removed " + summary.getLegacyDownloadRemovals() + " legacy global datapack downloads.");
        }
        if (summary.getLegacyWorldCopyRemovals() > 0) {
            Iris.info("Removed " + summary.getLegacyWorldCopyRemovals() + " legacy managed world datapack copies.");
        }
        if (summary.getRequests() > 0 || summary.getImportedSources() > 0 || summary.getWorldDatapacksInstalled() > 0) {
            Iris.info("External datapack sync/import/install: requests=" + summary.getRequests()
                    + ", synced=" + summary.getSyncedRequests()
                    + ", restored=" + summary.getRestoredRequests()
                    + ", importedSources=" + summary.getImportedSources()
                    + ", cachedSources=" + summary.getCachedSources()
                    + ", converted=" + summary.getConvertedStructures()
                    + ", failedConversions=" + summary.getFailedConversions()
                    + ", worldDatapacks=" + summary.getWorldDatapacksInstalled()
                    + ", worldAssets=" + summary.getWorldAssetsInstalled()
                    + ", optionalFailures=" + summary.getOptionalFailures()
                    + ", requiredFailures=" + summary.getRequiredFailures());
        }
        if (summary.getRequiredFailures() > 0) {
            throw new IllegalStateException("Required external datapack setup failed for " + summary.getRequiredFailures() + " request(s).");
        }
    }

    private static KList<ExternalDataPackPipeline.DatapackRequest> collectExternalDatapackRequests() {
        KMap<String, ExternalDataPackPipeline.DatapackRequest> deduplicated = new KMap<>();
        try (Stream<IrisData> stream = allPacks()) {
            stream.forEach(data -> {
                ResourceLoader<IrisDimension> loader = data.getDimensionLoader();
                if (loader == null) {
                    return;
                }

                KList<IrisDimension> dimensions = loader.loadAll(loader.getPossibleKeys());
                for (IrisDimension dimension : dimensions) {
                    if (dimension == null || dimension.getExternalDatapacks() == null || dimension.getExternalDatapacks().isEmpty()) {
                        continue;
                    }

                    String targetPack = sanitizePackName(dimension.getLoadKey());
                    if (targetPack.isBlank()) {
                        targetPack = sanitizePackName(data.getDataFolder().getName());
                    }
                    String environment = ExternalDataPackPipeline.normalizeEnvironmentValue(dimension.getEnvironment() == null ? null : dimension.getEnvironment().name());

                    for (IrisExternalDatapack externalDatapack : dimension.getExternalDatapacks()) {
                        if (externalDatapack == null || !externalDatapack.isEnabled()) {
                            continue;
                        }

                        String url = externalDatapack.getUrl() == null ? "" : externalDatapack.getUrl().trim();
                        if (url.isBlank()) {
                            continue;
                        }

                        String requestId = externalDatapack.getId() == null ? "" : externalDatapack.getId().trim();
                        if (requestId.isBlank()) {
                            requestId = url;
                        }

                        IrisExternalDatapackReplaceTargets replaceTargets = externalDatapack.getReplaceTargets();
                        ExternalDataPackPipeline.DatapackRequest request = new ExternalDataPackPipeline.DatapackRequest(
                                requestId,
                                url,
                                targetPack,
                                environment,
                                externalDatapack.isRequired(),
                                externalDatapack.isReplaceVanilla(),
                                replaceTargets
                        );

                        String dedupeKey = request.getDedupeKey();
                        ExternalDataPackPipeline.DatapackRequest existing = deduplicated.get(dedupeKey);
                        if (existing == null) {
                            deduplicated.put(dedupeKey, request);
                            continue;
                        }

                        deduplicated.put(dedupeKey, existing.merge(request));
                    }
                }
            });
        }

        return new KList<>(deduplicated.v());
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
