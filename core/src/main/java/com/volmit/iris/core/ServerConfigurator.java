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
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class ServerConfigurator {
    public static void configure() {
        IrisSettings.IrisSettingsAutoconfiguration s = IrisSettings.get().getAutoConfiguration();
        if (s.isConfigureSpigotTimeoutTime()) {
            J.attempt(ServerConfigurator::increaseKeepAliveSpigot);
        }

        if (s.isConfigurePaperWatchdogDelay()) {
            J.attempt(ServerConfigurator::increasePaperWatchdog);
        }
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

    private static KList<File> getDataPacksFolder() {
        if (!IrisSettings.get().getGeneral().forceMainWorld.isEmpty()) {
            return new KList<File>().qadd(new File(Bukkit.getWorldContainer(), IrisSettings.get().getGeneral().forceMainWorld + "/datapacks"));
        }
        KList<File> worlds = new KList<>();
        Bukkit.getServer().getWorlds().forEach(w -> worlds.add(new File(w.getWorldFolder(), "datapacks")));
        if (worlds.isEmpty()) {
            worlds.add(new File(getMainWorldFolder(), "datapacks"));
        }
        return worlds;
    }

    private static File getMainWorldFolder() {
        try {
            Properties prop = new Properties();
            prop.load(new FileInputStream("server.properties"));
            String world = prop.getProperty("level-name", "world");
            return new File(Bukkit.getWorldContainer(), world);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void dumpDataPack() {
        if (!INMS.get().dumpRegistry(getDataPacksFolder().toArray(File[]::new)))
            return;
        disableDataPack();
    }

    public static void disableDataPack() {
        var packs = INMS.get().getPackRepository();
        packs.reload();
        if (!packs.removePack("file/iris"))
            return;
        packs.reloadWorldData();
    }
}
