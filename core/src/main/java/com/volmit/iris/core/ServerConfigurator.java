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

package com.volmit.iris.core;

import com.volmit.iris.Iris;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.core.nms.datapack.DataVersion;
import com.volmit.iris.core.nms.datapack.IDataFixer;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisBiomeCustom;
import com.volmit.iris.engine.object.IrisDimension;
import com.volmit.iris.engine.object.IrisRange;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.plugin.VolmitSender;
import com.volmit.iris.util.scheduling.J;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static com.volmit.iris.core.nms.datapack.IDataFixer.Dimension.*;

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

        if (tt < TimeUnit.MINUTES.toSeconds(5)) {
            Iris.warn("Updating spigot.yml timeout-time: " + tt + " -> " + TimeUnit.MINUTES.toSeconds(20) + " (5 minutes)");
            Iris.warn("You can disable this change (autoconfigureServer) in Iris settings, then change back the value.");
            f.set("settings.timeout-time", TimeUnit.MINUTES.toSeconds(5));
            f.save(spigotConfig);
        }
    }
    private static void increasePaperWatchdog() throws IOException, InvalidConfigurationException {
        File spigotConfig = new File("config/paper-global.yml");
        FileConfiguration f = new YamlConfiguration();
        f.load(spigotConfig);
        long tt = f.getLong("watchdog.early-warning-delay");

        if (tt < TimeUnit.MINUTES.toMillis(3)) {
            Iris.warn("Updating paper.yml watchdog early-warning-delay: " + tt + " -> " + TimeUnit.MINUTES.toMillis(15) + " (3 minutes)");
            Iris.warn("You can disable this change (autoconfigureServer) in Iris settings, then change back the value.");
            f.set("watchdog.early-warning-delay", TimeUnit.MINUTES.toMillis(3));
            f.save(spigotConfig);
        }
    }

    private static List<File> getDatapacksFolder() {
        if (!IrisSettings.get().getGeneral().forceMainWorld.isEmpty()) {
            return new KList<File>().qadd(new File(Bukkit.getWorldContainer(), IrisSettings.get().getGeneral().forceMainWorld + "/datapacks"));
        }
        KList<File> worlds = new KList<>();
        Bukkit.getServer().getWorlds().forEach(w -> worlds.add(new File(w.getWorldFolder(), "datapacks")));
        return worlds;
    }

    public static void installDataPacks(boolean fullInstall) {
        installDataPacks(DataVersion.getDefault(), fullInstall);
    }

    public static void installDataPacks(IDataFixer fixer, boolean fullInstall) {
        Iris.info("Checking Data Packs...");
        DimensionHeight height = new DimensionHeight(fixer);

        allPacks().flatMap(height::merge)
                .forEach(dim -> {
                    for (File dpack : getDatapacksFolder()) {
                        Iris.verbose("  Checking Dimension " + dim.getLoadFile().getPath());
                        dim.installDataPack(fixer, dim::getLoader, dpack, height);
                    }
                });

        Iris.info("Data Packs Setup!");

        if (fullInstall)
            verifyDataPacksPost(IrisSettings.get().getAutoConfiguration().isAutoRestartOnCustomBiomeInstall());
    }

    private static void verifyDataPacksPost(boolean allowRestarting) {
        boolean bad = allPacks()
                .map(data -> {
                    Iris.verbose("Checking Pack: " + data.getDataFolder().getPath());
                    var loader = data.getDimensionLoader();
                    return loader.loadAll(loader.getPossibleKeys())
                            .stream()
                            .map(ServerConfigurator::verifyDataPackInstalled)
                            .toList()
                            .contains(false);
                })
                .toList()
                .contains(true);
        if (!bad) return;


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
    }

    public static void restart() {
        J.s(() -> {
            Iris.warn("New data pack entries have been installed in Iris! Restarting server!");
            Iris.warn("This will only happen when your pack changes (updates/first time setup)");
            Iris.warn("(You can disable this auto restart in iris settings)");
            J.s(() -> {
                Iris.warn("Looks like the restart command diddn't work. Stopping the server instead!");
                Bukkit.shutdown();
            }, 100);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "restart");
        });
    }

    public static boolean verifyDataPackInstalled(IrisDimension dimension) {
        IrisData idm = IrisData.get(Iris.instance.getDataFolder("packs", dimension.getLoadKey()));
        KSet<String> keys = new KSet<>();
        boolean warn = false;

        for (IrisBiome i : dimension.getAllBiomes(() -> idm)) {
            if (i.isCustom()) {
                for (IrisBiomeCustom j : i.getCustomDerivitives()) {
                    keys.add(dimension.getLoadKey() + ":" + j.getId());
                }
            }
        }

        if (!INMS.get().supportsDataPacks()) {
            if (!keys.isEmpty()) {
                Iris.warn("===================================================================================");
                Iris.warn("Pack " + dimension.getLoadKey() + " has " + keys.size() + " custom biome(s). ");
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

        if (warn) {
            Iris.error("The Pack " + dimension.getLoadKey() + " is INCAPABLE of generating custom biomes");
            Iris.error("If not done automatically, restart your server before generating with this pack!");
        }

        return !warn;
    }

    public static Stream<IrisData> allPacks() {
        return Stream.concat(listFiles(new File("plugins/Iris/packs")),
                        listFiles(Bukkit.getWorldContainer()).map(w -> new File(w, "iris/pack")))
                .filter(File::isDirectory)
                .map(IrisData::get);
    }

    private static Stream<File> listFiles(File parent) {
        var files = parent.listFiles();
        return files == null ? Stream.empty() : Arrays.stream(files);
    }

    @Data
    public static class DimensionHeight {
        private final IDataFixer fixer;
        private IrisRange overworld = new IrisRange();
        private IrisRange nether = new IrisRange();
        private IrisRange end = new IrisRange();
        private int logicalOverworld = 0;
        private int logicalNether = 0;
        private int logicalEnd = 0;

        public Stream<IrisDimension> merge(IrisData data) {
            Iris.verbose("Checking Pack: " + data.getDataFolder().getPath());
            var loader = data.getDimensionLoader();
            return loader.loadAll(loader.getPossibleKeys())
                    .stream()
                    .peek(this::merge);
        }

        public void merge(IrisDimension dimension) {
            overworld.merge(dimension.getDimensionHeight());
            nether.merge(dimension.getDimensionHeight());
            end.merge(dimension.getDimensionHeight());

            logicalOverworld = Math.max(logicalOverworld, dimension.getLogicalHeight());
            logicalNether = Math.max(logicalNether, dimension.getLogicalHeightNether());
            logicalEnd = Math.max(logicalEnd, dimension.getLogicalHeightEnd());
        }

        public String overworldType() {
            return fixer.createDimension(OVERRWORLD, overworld, logicalOverworld).toString(4);
        }

        public String netherType() {
            return fixer.createDimension(NETHER, nether, logicalNether).toString(4);
        }

        public String endType() {
            return fixer.createDimension(THE_END, end, logicalEnd).toString(4);
        }
    }
}
