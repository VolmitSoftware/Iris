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

import com.google.gson.Gson;
import com.volmit.iris.Iris;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.json.JSONException;
import com.volmit.iris.util.json.JSONObject;
import com.volmit.iris.util.plugin.VolmitSender;
import lombok.Data;

import java.io.File;
import java.io.IOException;

@SuppressWarnings("SynchronizeOnNonFinalField")
@Data
public class IrisSettings {
    public static IrisSettings settings;
    private IrisSettingsGeneral general = new IrisSettingsGeneral();
    private IrisSettingsWorld world = new IrisSettingsWorld();
    private IrisSettingsGUI gui = new IrisSettingsGUI();
    private IrisSettingsAutoconfiguration autoConfiguration = new IrisSettingsAutoconfiguration();
    private IrisSettingsGenerator generator = new IrisSettingsGenerator();
    private IrisSettingsConcurrency concurrency = new IrisSettingsConcurrency();
    private IrisSettingsStudio studio = new IrisSettingsStudio();
    private IrisSettingsPerformance performance = new IrisSettingsPerformance();

    public static int getThreadCount(int c) {
        return switch (c) {
            case -1, -2, -4 -> Runtime.getRuntime().availableProcessors() / -c;
            case 0, 1, 2 -> 1;
            default -> Math.max(c, 2);
        };
    }

    public static IrisSettings get() {
        if (settings != null) {
            return settings;
        }

        settings = new IrisSettings();

        File s = Iris.instance.getDataFile("settings.json");

        if (!s.exists()) {
            try {
                IO.writeAll(s, new JSONObject(new Gson().toJson(settings)).toString(4));
            } catch (JSONException | IOException e) {
                e.printStackTrace();
                Iris.reportError(e);
            }
        } else {
            try {
                String ss = IO.readAll(s);
                settings = new Gson().fromJson(ss, IrisSettings.class);
                try {
                    IO.writeAll(s, new JSONObject(new Gson().toJson(settings)).toString(4));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (Throwable ee) {
                // Iris.reportError(ee); causes a self-reference & stackoverflow
                Iris.error("Configuration Error in settings.json! " + ee.getClass().getSimpleName() + ": " + ee.getMessage());
            }
        }

        return settings;
    }

    public static void invalidate() {
        synchronized (settings) {
            settings = null;
        }
    }

    public void forceSave() {
        File s = Iris.instance.getDataFile("settings.json");

        try {
            IO.writeAll(s, new JSONObject(new Gson().toJson(settings)).toString(4));
        } catch (JSONException | IOException e) {
            e.printStackTrace();
            Iris.reportError(e);
        }
    }

    @Data
    public static class IrisSettingsAutoconfiguration {
        public boolean configureSpigotTimeoutTime = true;
        public boolean configurePaperWatchdogDelay = true;
        public boolean autoRestartOnCustomBiomeInstall = true;
    }

    @Data
    public static class IrisAsyncTeleport {
        public boolean enabled = false;
        public int loadViewDistance = 2;
        public boolean urgent = false;
    }

    @Data
    public static class IrisSettingsWorld {
        public IrisAsyncTeleport asyncTeleport = new IrisAsyncTeleport();
        public boolean postLoadBlockUpdates = true;
        public boolean forcePersistEntities = true;
        public boolean anbientEntitySpawningSystem = true;
        public long asyncTickIntervalMS = 700;
        public double targetSpawnEntitiesPerChunk = 0.95;
        public boolean markerEntitySpawningSystem = true;
        public boolean effectSystem = true;
        public boolean worldEditWandCUI = true;
    }

    @Data
    public static class IrisSettingsConcurrency {
        public int parallelism = -1;
    }

    @Data
    public static class IrisSettingsPerformance {
        public boolean trimMantleInStudio = false;
        public int mantleKeepAlive = 30;
        public int cacheSize = 4_096;
        public int resourceLoaderCacheSize = 1_024;
        public int objectLoaderCacheSize = 4_096;
        public int scriptLoaderCacheSize = 512;
    }

    @Data
    public static class IrisSettingsGeneral {
        public boolean commandSounds = true;
        public boolean debug = false;
        public boolean disableNMS = false;
        public boolean pluginMetrics = true;
        public boolean splashLogoStartup = true;
        public boolean useConsoleCustomColors = true;
        public boolean useCustomColorsIngame = true;
        public String forceMainWorld = "";
        public int spinh = -20;
        public int spins = 7;
        public int spinb = 8;

        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        public boolean canUseCustomColors(VolmitSender volmitSender) {
            return volmitSender.isPlayer() ? useCustomColorsIngame : useConsoleCustomColors;
        }
    }

    @Data
    public static class IrisSettingsGUI {
        public boolean useServerLaunchedGuis = true;
        public boolean maximumPregenGuiFPS = false;
    }

    @Data
    public static class IrisSettingsGenerator {
        public String defaultWorldType = "overworld";
        public int maxBiomeChildDepth = 4;
        public boolean preventLeafDecay = true;
    }

    @Data
    public static class IrisSettingsStudio {
        public boolean studio = true;
        public boolean openVSCode = true;
        public boolean disableTimeAndWeather = true;
        public boolean autoStartDefaultStudio = false;
    }
}
