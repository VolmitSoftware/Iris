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
import com.volmit.iris.util.misc.getHardware;
import com.volmit.iris.util.plugin.VolmitSender;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private IrisSettingsUpdater updater = new IrisSettingsUpdater();
    private IrisSettingsPregen pregen = new IrisSettingsPregen();
    private IrisSettingsSentry sentry = new IrisSettingsSentry();

    public static int getThreadCount(int c) {
        return Math.max(switch (c) {
            case -1, -2, -4 -> Runtime.getRuntime().availableProcessors() / -c;
            default -> Math.max(c, 2);
        }, 1);
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
        public boolean globalPregenCache = false;
    }

    @Data
    public static class IrisSettingsConcurrency {
        public int parallelism = -1;
        public int ioParallelism = -2;
        public int worldGenParallelism = -1;

        public int getWorldGenThreads() {
            return getThreadCount(worldGenParallelism);
        }
    }

    @Data
    public static class IrisSettingsPregen {
        public boolean useCacheByDefault = true;
        public boolean useHighPriority = false;
        public boolean useVirtualThreads = false;
        public boolean useTicketQueue = true;
        public int maxConcurrency = 256;
    }

    @Data
    public static class IrisSettingsPerformance {
        private IrisSettingsEngineSVC engineSVC = new IrisSettingsEngineSVC();
        public boolean trimMantleInStudio = false; 
        public int mantleKeepAlive = 30;
        public int cacheSize = 4_096;
        public int resourceLoaderCacheSize = 1_024;
        public int objectLoaderCacheSize = 4_096;
        public int scriptLoaderCacheSize = 512;
        public int tectonicPlateSize = -1;
        public int mantleCleanupDelay = 200;

        public int getTectonicPlateSize() {
            if (tectonicPlateSize > 0)
                return tectonicPlateSize;

            return (int) (getHardware.getProcessMemory() / 200L);
        }
    }

    @Data
    public static class IrisSettingsUpdater {
        public int maxConcurrency = 256;
        public double chunkLoadSensitivity = 0.7;
        public MsRange emptyMsRange = new MsRange(80, 100);
        public MsRange defaultMsRange = new MsRange(20, 40);

        public int getMaxConcurrency() {
            return Math.max(Math.abs(maxConcurrency), 1);
        }

        public double getChunkLoadSensitivity() {
            return Math.min(chunkLoadSensitivity, 0.9);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MsRange {
        public int min = 20;
        public int max = 40;
    }

    @Data
    public static class IrisSettingsGeneral {
        public boolean DoomsdayAnnihilationSelfDestructMode = false;
        public boolean commandSounds = true;
        public boolean debug = false;
        public boolean dumpMantleOnError = false;
        public boolean disableNMS = false;
        public boolean pluginMetrics = true;
        public boolean splashLogoStartup = true;
        public boolean useConsoleCustomColors = true;
        public boolean useCustomColorsIngame = true;
        public boolean adjustVanillaHeight = false;
        public String forceMainWorld = "";
        public int spinh = -20;
        public int spins = 7;
        public int spinb = 8;
        public String cartographerMessage = "Iris does not allow cartographers in its world due to crashes.";


        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        public boolean canUseCustomColors(VolmitSender volmitSender) {
            return volmitSender.isPlayer() ? useCustomColorsIngame : useConsoleCustomColors;
        }
    }

    @Data
    public static class IrisSettingsSentry {
        public boolean includeServerId = true;
        public boolean disableAutoReporting = false;
        public boolean debug = false;
    }

    @Data
    public static class IrisSettingsGUI {
        public boolean useServerLaunchedGuis = true;
        public boolean maximumPregenGuiFPS = false;
        public boolean colorMode = true;
    }

    @Data
    public static class IrisSettingsGenerator {
        public String defaultWorldType = "overworld";
        public int maxBiomeChildDepth = 4;
        public boolean preventLeafDecay = true;
        public boolean useMulticore = false;
        public boolean offsetNoiseTypes = false;
        public boolean earlyCustomBlocks = false;
    }

    @Data
    public static class IrisSettingsStudio {
        public boolean studio = true;
        public boolean openVSCode = true;
        public boolean disableTimeAndWeather = true;
        public boolean autoStartDefaultStudio = false;
    }

    @Data
    public static class IrisSettingsEngineSVC {
        public boolean useVirtualThreads = true;
        public boolean forceMulticoreWrite = false;
        public int priority = Thread.NORM_PRIORITY;

        public int getPriority() {
            return Math.max(Math.min(priority, Thread.MAX_PRIORITY), Thread.MIN_PRIORITY);
        }
    }
}
