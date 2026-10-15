/*
 * Iris is a World Generator for Minecraft Servers
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

package art.arcane.iris.modded;

import art.arcane.volmlib.util.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

public final class ModdedModConfig {
    private static final Object LOCK = new Object();
    private static final String DIMENSION_TAKEOVERS = "dimensionTakeovers";
    private static volatile ModdedModConfig instance;

    private final String defaultPack;
    private final String primaryWorld;
    private final boolean routePlayersToPrimaryWorld;
    private final String mainWorldPack;
    private final long mainWorldSeed;
    private final boolean mainWorldAutoRestart;
    private final Map<String, String> dimensionTakeovers;

    private ModdedModConfig(String defaultPack, String primaryWorld, boolean routePlayersToPrimaryWorld,
                           String mainWorldPack, long mainWorldSeed, boolean mainWorldAutoRestart,
                           Map<String, String> dimensionTakeovers) {
        this.defaultPack = defaultPack;
        this.primaryWorld = primaryWorld == null ? "" : primaryWorld.trim();
        this.routePlayersToPrimaryWorld = routePlayersToPrimaryWorld;
        this.mainWorldPack = mainWorldPack == null ? "" : mainWorldPack.trim();
        this.mainWorldSeed = mainWorldSeed;
        this.mainWorldAutoRestart = mainWorldAutoRestart;
        this.dimensionTakeovers = Map.copyOf(dimensionTakeovers);
    }

    public static ModdedModConfig get() {
        ModdedModConfig bound = instance;
        if (bound != null) {
            return bound;
        }
        synchronized (LOCK) {
            if (instance != null) {
                return instance;
            }
            instance = load();
            return instance;
        }
    }

    public static void setPrimaryWorld(String dimensionId) {
        synchronized (LOCK) {
            ModdedModConfig current = get();
            ModdedModConfig updated = new ModdedModConfig(current.defaultPack, dimensionId, current.routePlayersToPrimaryWorld,
                    current.mainWorldPack, current.mainWorldSeed, current.mainWorldAutoRestart, current.dimensionTakeovers);
            instance = updated;
            write(configFile(), updated);
        }
    }

    public static void setMainWorld(String packRef, long seed) {
        synchronized (LOCK) {
            ModdedModConfig current = get();
            ModdedModConfig updated = new ModdedModConfig(current.defaultPack, current.primaryWorld, current.routePlayersToPrimaryWorld,
                    packRef == null ? "" : packRef.trim(), seed, current.mainWorldAutoRestart, current.dimensionTakeovers);
            instance = updated;
            write(configFile(), updated);
        }
    }

    public static boolean setDimensionTakeover(ModdedDimensionTakeover takeover) {
        return updateDimensionTakeover(takeover.levelKey(), takeover.packRef());
    }

    public static boolean clearDimensionTakeover(String levelKey) {
        return updateDimensionTakeover(levelKey, null);
    }

    public String defaultPack() {
        return defaultPack;
    }

    public String primaryWorld() {
        return primaryWorld;
    }

    public boolean routePlayersToPrimaryWorld() {
        return routePlayersToPrimaryWorld;
    }

    public String mainWorldPack() {
        return mainWorldPack;
    }

    public long mainWorldSeed() {
        return mainWorldSeed;
    }

    public boolean mainWorldAutoRestart() {
        return mainWorldAutoRestart;
    }

    public Map<String, String> dimensionTakeovers() {
        return dimensionTakeovers;
    }

    private static boolean updateDimensionTakeover(String levelKey, String packRef) {
        synchronized (LOCK) {
            ModdedModConfig current = get();
            Map<String, String> takeovers = new TreeMap<>(current.dimensionTakeovers);
            String previous = packRef == null ? takeovers.remove(levelKey) : takeovers.put(levelKey, packRef);
            if (packRef == null ? previous == null : packRef.equals(previous)) {
                return false;
            }
            ModdedModConfig updated = new ModdedModConfig(current.defaultPack, current.primaryWorld, current.routePlayersToPrimaryWorld,
                    current.mainWorldPack, current.mainWorldSeed, current.mainWorldAutoRestart, takeovers);
            instance = updated;
            write(configFile(), updated);
            return true;
        }
    }

    private static Path configFile() {
        return ModdedEngineBootstrap.loader().configDir().resolve("irisworldgen").resolve("modded.json");
    }

    private static ModdedModConfig load() {
        Path file = configFile();
        ModdedModConfig defaults = new ModdedModConfig("overworld", "", true, "", 0L, false, Map.of());
        if (!Files.isRegularFile(file)) {
            write(file, defaults);
            return defaults;
        }
        try {
            JSONObject json = new JSONObject(Files.readString(file, StandardCharsets.UTF_8));
            return new ModdedModConfig(
                    json.optString("defaultPack", defaults.defaultPack),
                    json.optString("primaryWorld", defaults.primaryWorld),
                    json.optBoolean("routePlayersToPrimaryWorld", defaults.routePlayersToPrimaryWorld),
                    json.optString("mainWorldPack", defaults.mainWorldPack),
                    json.optLong("mainWorldSeed", defaults.mainWorldSeed),
                    json.optBoolean("mainWorldAutoRestart", defaults.mainWorldAutoRestart),
                    readDimensionTakeovers(json.optJSONObject(DIMENSION_TAKEOVERS), file));
        } catch (RuntimeException | IOException e) {
            ModdedIrisLog.error("Iris modded config at {} is invalid; using defaults", file, e);
            return defaults;
        }
    }

    private static Map<String, String> readDimensionTakeovers(JSONObject json, Path file) {
        if (json == null) {
            return Map.of();
        }
        Map<String, String> takeovers = new TreeMap<>();
        for (String levelKey : json.keySet()) {
            try {
                ModdedDimensionTakeover takeover = ModdedDimensionTakeover.parse(levelKey, json.optString(levelKey, ""));
                takeovers.put(takeover.levelKey(), takeover.packRef());
            } catch (IllegalArgumentException invalid) {
                ModdedIrisLog.error("Iris ignored dimension takeover '{}' in {}: {}", levelKey, file, invalid.getMessage());
            }
        }
        return takeovers;
    }

    private static void write(Path file, ModdedModConfig config) {
        JSONObject json = new JSONObject();
        json.put("defaultPack", config.defaultPack);
        json.put("primaryWorld", config.primaryWorld);
        json.put("routePlayersToPrimaryWorld", config.routePlayersToPrimaryWorld);
        json.put("mainWorldPack", config.mainWorldPack);
        json.put("mainWorldSeed", config.mainWorldSeed);
        json.put("mainWorldAutoRestart", config.mainWorldAutoRestart);
        JSONObject takeovers = new JSONObject();
        for (Map.Entry<String, String> entry : new TreeMap<>(config.dimensionTakeovers).entrySet()) {
            takeovers.put(entry.getKey(), entry.getValue());
        }
        json.put(DIMENSION_TAKEOVERS, takeovers);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, json.toString(4), StandardCharsets.UTF_8);
        } catch (IOException e) {
            ModdedIrisLog.error("Iris failed to write modded config at {}", file, e);
        }
    }
}
