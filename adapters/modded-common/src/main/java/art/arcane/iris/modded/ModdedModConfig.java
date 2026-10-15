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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public final class ModdedModConfig {
    private static final Object LOCK = new Object();
    private static final String DIMENSION_REPLACEMENTS = "dimensionReplacements";
    private static volatile ModdedModConfig instance;

    private final String defaultPack;
    private final String primaryWorld;
    private final boolean routePlayersToPrimaryWorld;
    private final String mainWorldPack;
    private final long mainWorldSeed;
    private final boolean mainWorldAutoRestart;
    private final Map<String, ModdedDimensionReplacement> dimensionReplacements;
    private final List<String> replacementProblems;

    private ModdedModConfig(String defaultPack, String primaryWorld, boolean routePlayersToPrimaryWorld,
                           String mainWorldPack, long mainWorldSeed, boolean mainWorldAutoRestart,
                           Replacements replacements) {
        this.defaultPack = defaultPack;
        this.primaryWorld = primaryWorld == null ? "" : primaryWorld.trim();
        this.routePlayersToPrimaryWorld = routePlayersToPrimaryWorld;
        this.mainWorldPack = mainWorldPack == null ? "" : mainWorldPack.trim();
        this.mainWorldSeed = mainWorldSeed;
        this.mainWorldAutoRestart = mainWorldAutoRestart;
        this.dimensionReplacements = Map.copyOf(replacements.entries());
        this.replacementProblems = List.copyOf(replacements.problems());
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

    public static ModdedModConfig loaded() {
        return instance;
    }

    public static void setPrimaryWorld(String dimensionId) {
        synchronized (LOCK) {
            ModdedModConfig current = writable();
            ModdedModConfig updated = new ModdedModConfig(current.defaultPack, dimensionId, current.routePlayersToPrimaryWorld,
                    current.mainWorldPack, current.mainWorldSeed, current.mainWorldAutoRestart, current.replacements());
            instance = updated;
            write(configFile(), updated);
        }
    }

    public static void setMainWorld(String packRef, long seed) {
        synchronized (LOCK) {
            ModdedModConfig current = writable();
            ModdedModConfig updated = new ModdedModConfig(current.defaultPack, current.primaryWorld, current.routePlayersToPrimaryWorld,
                    packRef == null ? "" : packRef.trim(), seed, current.mainWorldAutoRestart, current.replacements());
            instance = updated;
            write(configFile(), updated);
        }
    }

    public static boolean setDimensionReplacement(ModdedDimensionReplacement replacement) {
        return updateDimensionReplacement(replacement.levelKey(), replacement);
    }

    public static boolean clearDimensionReplacement(String levelKey) {
        return updateDimensionReplacement(levelKey, null);
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

    public Map<String, ModdedDimensionReplacement> dimensionReplacements() {
        return dimensionReplacements;
    }

    public List<String> replacementProblems() {
        return replacementProblems;
    }

    static ModdedModConfig read(Path file) {
        ModdedModConfig defaults = defaults();
        JSONObject json;
        try {
            json = new JSONObject(Files.readString(file, StandardCharsets.UTF_8));
        } catch (RuntimeException | IOException unreadable) {
            ModdedIrisLog.error("Iris modded config at {} is unreadable; using defaults", file, unreadable);
            return new ModdedModConfig(defaults.defaultPack, defaults.primaryWorld, defaults.routePlayersToPrimaryWorld,
                    defaults.mainWorldPack, defaults.mainWorldSeed, defaults.mainWorldAutoRestart,
                    new Replacements(Map.of(), List.of("Iris could not read " + file + ": " + unreadable.getMessage())));
        }
        return new ModdedModConfig(
                json.optString("defaultPack", defaults.defaultPack),
                json.optString("primaryWorld", defaults.primaryWorld),
                json.optBoolean("routePlayersToPrimaryWorld", defaults.routePlayersToPrimaryWorld),
                json.optString("mainWorldPack", defaults.mainWorldPack),
                json.optLong("mainWorldSeed", defaults.mainWorldSeed),
                json.optBoolean("mainWorldAutoRestart", defaults.mainWorldAutoRestart),
                readDimensionReplacements(json, file));
    }

    private Replacements replacements() {
        return new Replacements(dimensionReplacements, replacementProblems);
    }

    private static ModdedModConfig writable() {
        ModdedModConfig current = get();
        if (!current.replacementProblems.isEmpty()) {
            throw new IllegalStateException("Iris will not rewrite " + configFile()
                    + " while its dimension replacements are unreadable: " + String.join("; ", current.replacementProblems)
                    + ". Fix the file and restart first.");
        }
        return current;
    }

    private static ModdedModConfig defaults() {
        return new ModdedModConfig("overworld", "", true, "", 0L, false, new Replacements(Map.of(), List.of()));
    }

    private static boolean updateDimensionReplacement(String levelKey, ModdedDimensionReplacement replacement) {
        synchronized (LOCK) {
            ModdedModConfig current = writable();
            Map<String, ModdedDimensionReplacement> replacements = new TreeMap<>(current.dimensionReplacements);
            ModdedDimensionReplacement previous = replacement == null
                    ? replacements.remove(levelKey)
                    : replacements.put(levelKey, replacement);
            if (replacement == null ? previous == null : replacement.equals(previous)) {
                return false;
            }
            ModdedModConfig updated = new ModdedModConfig(current.defaultPack, current.primaryWorld, current.routePlayersToPrimaryWorld,
                    current.mainWorldPack, current.mainWorldSeed, current.mainWorldAutoRestart, new Replacements(replacements, List.of()));
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
        if (!Files.isRegularFile(file)) {
            ModdedModConfig defaults = defaults();
            write(file, defaults);
            return defaults;
        }
        return read(file);
    }

    private static Replacements readDimensionReplacements(JSONObject json, Path file) {
        if (!json.has(DIMENSION_REPLACEMENTS) || json.isNull(DIMENSION_REPLACEMENTS)) {
            return new Replacements(Map.of(), List.of());
        }
        JSONObject section = json.optJSONObject(DIMENSION_REPLACEMENTS);
        if (section == null) {
            return new Replacements(Map.of(), List.of("Iris could not read " + DIMENSION_REPLACEMENTS + " in " + file
                    + ": it must be an object mapping dimension keys to replacements"));
        }
        Map<String, ModdedDimensionReplacement> replacements = new TreeMap<>();
        List<String> problems = new ArrayList<>();
        for (String levelKey : new TreeSet<>(section.keySet())) {
            try {
                ModdedDimensionReplacement replacement = ModdedDimensionReplacement.fromJson(levelKey, section.opt(levelKey));
                replacements.put(replacement.levelKey(), replacement);
            } catch (IllegalArgumentException invalid) {
                problems.add("Iris could not read dimension replacement '" + levelKey + "' in " + file + ": " + invalid.getMessage());
            }
        }
        for (String problem : problems) {
            ModdedIrisLog.error(problem);
        }
        return new Replacements(replacements, problems);
    }

    private static void write(Path file, ModdedModConfig config) {
        JSONObject json = new JSONObject();
        json.put("defaultPack", config.defaultPack);
        json.put("primaryWorld", config.primaryWorld);
        json.put("routePlayersToPrimaryWorld", config.routePlayersToPrimaryWorld);
        json.put("mainWorldPack", config.mainWorldPack);
        json.put("mainWorldSeed", config.mainWorldSeed);
        json.put("mainWorldAutoRestart", config.mainWorldAutoRestart);
        JSONObject replacements = new JSONObject();
        for (Map.Entry<String, ModdedDimensionReplacement> entry : new TreeMap<>(config.dimensionReplacements).entrySet()) {
            replacements.put(entry.getKey(), entry.getValue().toJson());
        }
        json.put(DIMENSION_REPLACEMENTS, replacements);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, json.toString(4), StandardCharsets.UTF_8);
        } catch (IOException e) {
            ModdedIrisLog.error("Iris failed to write modded config at {}", file, e);
        }
    }

    private record Replacements(Map<String, ModdedDimensionReplacement> entries, List<String> problems) {
    }
}
