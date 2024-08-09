/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisDimension;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.scheduling.J;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
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

    private static File[] getDataPacksFolder() {
        KList<File> files = new KList<>();
        files.add(new File("plugins/Iris/datapack"));
        Arrays.stream(IrisSettings.get().getGeneral().dataPackPaths)
                .map(File::new)
                .forEach(files::add);
        return files.toArray(File[]::new);
    }

    public static void setupDataPack() {
        File packs = new File("plugins/Iris/packs");
        if (!packs.exists()) {
            disableDataPack();
            return;
        }
        for (File i : packs.listFiles()) {
            if (!i.isDirectory()) continue;

            Iris.verbose("Checking Pack: " + i.getPath());
            IrisData data = IrisData.get(i);
            File dims = new File(i, "dimensions");

            if (dims.exists()) {
                for (File j : dims.listFiles((f, s) -> s.endsWith(".json"))) {
                    if (!j.isFile()) continue;
                    IrisDimension dim = data.getDimensionLoader().load(j.getName().split("\\Q.\\E")[0]);
                    if (dim == null) continue;

                    dim.getAllBiomes(() -> data)
                            .stream()
                            .map(IrisBiome::getCustomDerivitives)
                            .filter(Objects::nonNull)
                            .flatMap(KList::stream)
                            .forEach(b -> INMS.get().registerBiome(dim.getLoadKey(), b, false));
                }
            }
        }
        dumpDataPack();
    }

    public static void dumpDataPack() {
        if (!INMS.get().dumpRegistry(getDataPacksFolder())) {
            return;
        }
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
