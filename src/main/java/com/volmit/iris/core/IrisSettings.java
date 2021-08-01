/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.json.JSONException;
import com.volmit.iris.util.json.JSONObject;
import com.volmit.iris.util.scheduling.J;
import lombok.Data;

import java.io.File;
import java.io.IOException;

@SuppressWarnings("SynchronizeOnNonFinalField")
@Data
public class IrisSettings {
    public static transient IrisSettings settings;
    private IrisSettingsCache cache = new IrisSettingsCache();
    private IrisSettingsConcurrency concurrency = new IrisSettingsConcurrency();
    private IrisSettingsParallax parallax = new IrisSettingsParallax();
    private IrisSettingsGeneral general = new IrisSettingsGeneral();
    private IrisSettingsGUI gui = new IrisSettingsGUI();
    private IrisSettingsGenerator generator = new IrisSettingsGenerator();
    private IrisSettingsStudio studio = new IrisSettingsStudio();
    public int configurationVersion = 3;

    public boolean isStudio() {
        return getStudio().isStudio();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isUseServerLaunchedGuis() {
        return getGui().isUseServerLaunchedGuis();
    }

    public long getParallaxRegionEvictionMS() {
        return getParallax().getParallaxRegionEvictionMS();
    }

    public static int getThreadCount(int c) {
        if (c < 2 && c >= 0) {
            return 2;
        }

        return Math.max(2, c < 0 ? Runtime.getRuntime().availableProcessors() / -c : c);
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
    public static class IrisSettingsCache {
        public int complexCacheSize = 131072;
    }

    @Data
    public static class IrisSettingsConcurrency {
        public int engineThreadCount = -1;
        public int engineThreadPriority = 6;
        public int pregenThreadCount = -1;
        public int pregenThreadPriority = 8;
        public int miscThreadCount = -4;
        public int miscThreadPriority = 3;
        public boolean unstableLockingHeuristics = false;
    }

    @Data
    public static class IrisSettingsParallax {
        public int parallaxRegionEvictionMS = 15000;
        public int parallaxChunkEvictionMS = 5000;
    }

    @Data
    public static class IrisSettingsGeneral {
        public boolean commandSounds = true;
        public boolean debug = false;
        public boolean verbose = false;
        public boolean ignoreWorldEdit = false;
        public boolean disableNMS = false;
        public boolean pluginMetrics = true;
        public boolean splashLogoStartup = true;
        public String forceMainWorld = "";
    }

    @Data
    public static class IrisSettingsGUI {

        public boolean useServerLaunchedGuis = true;
        public boolean maximumPregenGuiFPS = false;
        public boolean localPregenGui = true;
    }

    @Data
    public static class IrisSettingsGenerator {

        public String defaultWorldType = "overworld";
        public boolean disableMCA = false;
        public boolean systemEffects = true;
        public boolean systemEntitySpawnOverrides = true;
        public boolean systemEntityInitialSpawns = true;
        public int maxBiomeChildDepth = 4;
    }

    @Data
    public static class IrisSettingsStudio {
        public boolean studio = true;
        public boolean openVSCode = true;
        public boolean disableTimeAndWeather = true;
    }

    public static IrisSettings get() {
        if (settings != null) {
            return settings;
        }

        IrisSettings defaults = new IrisSettings();
        JSONObject def = new JSONObject(new Gson().toJson(defaults));
        if (settings == null) {
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

                    J.a(() ->
                    {
                        try {
                            JSONObject j = new JSONObject(ss);
                            boolean u = false;
                            for (String i : def.keySet()) {
                                if (!j.has(i)) {
                                    u = true;
                                    j.put(i, def.get(i));
                                    Iris.warn("Adding new config key: " + i);
                                }
                            }

                            for (String i : new KSet<>(j.keySet())) {
                                if (!def.has(i)) {
                                    u = true;
                                    j.remove(i);
                                    Iris.warn("Removing unused config key: " + i);
                                }
                            }

                            if (u) {
                                try {
                                    IO.writeAll(s, j.toString(4));
                                    Iris.info("Updated Configuration Files");
                                } catch (Throwable e) {
                                    e.printStackTrace();
                                    Iris.reportError(e);
                                }
                            }
                        } catch (Throwable ee) {
                            Iris.reportError(ee);
                            Iris.error("Configuration Error in settings.json! " + ee.getClass().getSimpleName() + ": " + ee.getMessage());
                            Iris.warn("Attempting to fix configuration while retaining valid in-memory settings...");

                            try {
                                IO.writeAll(s, new JSONObject(new Gson().toJson(settings)).toString(4));
                                Iris.info("Configuration Fixed!");
                            } catch (IOException e) {
                                Iris.reportError(e);
                                e.printStackTrace();
                                Iris.error("ERROR! CONFIGURATION IMPOSSIBLE TO READ! Using an unmodifiable configuration from memory. Please delete the settings.json at some point to try to restore configurability!");
                            }
                        }
                    });
                } catch (Throwable ee) {
                    Iris.reportError(ee);
                    Iris.error("Configuration Error in settings.json! " + ee.getClass().getSimpleName() + ": " + ee.getMessage());
                    Iris.warn("Attempting to fix configuration while retaining valid in-memory settings...");

                    try {
                        IO.writeAll(s, new JSONObject(new Gson().toJson(settings)).toString(4));
                        Iris.info("Configuration Fixed!");
                    } catch (IOException e) {
                        Iris.reportError(e);
                        e.printStackTrace();
                        Iris.error("ERROR! CONFIGURATION IMPOSSIBLE TO READ! Using an unmodifiable configuration from memory. Please delete the settings.json at some point to try to restore configurability!");
                    }
                }
            }

            if (!s.exists()) {
                try {
                    IO.writeAll(s, new JSONObject(new Gson().toJson(settings)).toString(4));
                } catch (JSONException | IOException e) {
                    Iris.reportError(e);
                    e.printStackTrace();
                }
            }
        }

        return settings;
    }

    public static void invalidate() {
        synchronized (settings) {
            settings = null;
        }
    }
}
