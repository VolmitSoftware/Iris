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

import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.volmlib.util.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class ModdedDimensionTakeovers {
    private static final String GENERATOR_TYPE = "irisworldgen:iris";
    private static final String PLACEHOLDER_BIOME_SOURCE = "minecraft:fixed";
    private static final String PLACEHOLDER_BIOME = "minecraft:plains";
    private static final String DIMENSIONS = "dimensions";
    private static final String JSON = ".json";

    private ModdedDimensionTakeovers() {
    }

    public static List<ModdedDimensionTakeover> parseAll(Map<String, String> configured) {
        List<ModdedDimensionTakeover> takeovers = new ArrayList<>(configured.size());
        for (Map.Entry<String, String> entry : new TreeMap<>(configured).entrySet()) {
            try {
                takeovers.add(ModdedDimensionTakeover.parse(entry.getKey(), entry.getValue()));
            } catch (IllegalArgumentException invalid) {
                ModdedIrisLog.error("Iris ignored dimension takeover {} -> {}: {}",
                        entry.getKey(), entry.getValue(), invalid.getMessage());
            }
        }
        return List.copyOf(takeovers);
    }

    public static String levelStemJson(String dimensionTypeKey, ModdedDimensionTakeover takeover) {
        JSONObject biomeSource = new JSONObject();
        biomeSource.put("type", PLACEHOLDER_BIOME_SOURCE);
        biomeSource.put("biome", PLACEHOLDER_BIOME);
        JSONObject generator = new JSONObject();
        generator.put("type", GENERATOR_TYPE);
        generator.put("biome_source", biomeSource);
        generator.put("dimension", takeover.packRef());
        JSONObject stem = new JSONObject();
        stem.put("type", Objects.requireNonNull(dimensionTypeKey, "dimensionTypeKey"));
        stem.put("generator", generator);
        return stem.toString(2) + "\n";
    }

    public static Path writeLevelStem(Path datapackRoot, ModdedDimensionTakeover takeover, String dimensionTypeKey)
            throws IOException {
        Path output = datapackRoot.resolve("data").resolve(takeover.namespace()).resolve("dimension");
        String[] segments = takeover.path().split("/");
        for (int index = 0; index < segments.length - 1; index++) {
            output = output.resolve(segments[index]);
        }
        output = output.resolve(segments[segments.length - 1] + ".json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, levelStemJson(dimensionTypeKey, takeover), StandardCharsets.UTF_8);
        return output;
    }

    public static void writeLevelStems(Path datapackRoot, Map<String, String> configured, StagedTypes staged,
                                       Path worldRoot) throws IOException {
        for (ModdedDimensionTakeover takeover : parseAll(configured)) {
            String typeRef = staged.typeRef(takeover, worldRoot);
            if (typeRef == null) {
                skipUnstaged(takeover, staged, worldRoot);
                continue;
            }
            writeLevelStem(datapackRoot, takeover, typeRef);
            ModdedIrisLog.info("Iris dimension takeover staged: {} generates from {} with dimension type {}",
                    takeover.levelKey(), takeover.packRef(), typeRef);
        }
    }

    public static Map<String, String> configured() throws IOException {
        return canonical(ModdedModConfig.get().dimensionTakeovers(), IrisPlatforms.get().packsFolderNoCreate().toPath());
    }

    static Map<String, String> canonical(Map<String, String> configured, Path packsRoot) throws IOException {
        Map<String, String> canonical = new TreeMap<>();
        for (ModdedDimensionTakeover takeover : parseAll(configured)) {
            canonical.put(takeover.levelKey(), canonical(takeover, packsRoot).packRef());
        }
        return Map.copyOf(canonical);
    }

    public static ModdedDimensionTakeover canonical(ModdedDimensionTakeover takeover, Path packsRoot) throws IOException {
        Path pack = packsRoot.resolve(takeover.pack());
        if (!Files.isDirectory(pack)) {
            return takeover;
        }
        String packName = sameIgnoringCase(pack.toRealPath().getFileName().toString(), takeover.pack());
        Path dimensions = pack.resolve(DIMENSIONS);
        Path dimensionFile = dimensions.resolve(takeover.packDimension() + JSON);
        String dimension = takeover.packDimension();
        if (Files.isRegularFile(dimensionFile)) {
            String relative = dimensions.toRealPath().relativize(dimensionFile.toRealPath()).toString()
                    .replace(File.separatorChar, '/');
            dimension = sameIgnoringCase(relative.substring(0, relative.length() - JSON.length()), takeover.packDimension());
        }
        return new ModdedDimensionTakeover(takeover.levelKey(), packName, dimension);
    }

    public static boolean installed(ModdedDimensionTakeover takeover, Path packsRoot) {
        return Files.isRegularFile(packsRoot.resolve(takeover.pack()).resolve(DIMENSIONS)
                .resolve(takeover.packDimension() + JSON));
    }

    public static Set<String> changedLevels(Map<String, String> applied, Map<String, String> configured) {
        Set<String> levels = new TreeSet<>(applied.keySet());
        levels.addAll(configured.keySet());
        Set<String> changed = new TreeSet<>();
        for (String level : levels) {
            if (!Objects.equals(applied.get(level), configured.get(level))) {
                changed.add(level);
            }
        }
        return changed;
    }

    private static void skipUnstaged(ModdedDimensionTakeover takeover, StagedTypes staged, Path worldRoot) throws IOException {
        String reason = "pack '" + takeover.pack() + "' dimension '" + takeover.packDimension()
                + "' is not installed or failed validation" + caseHint(takeover, staged);
        if (worldRoot != null && ModdedTakeoverReconciler.hasTakeoverRecord(worldRoot, takeover.levelKey())) {
            throw new IOException("Iris refused to stage the dimension takeover of " + takeover.levelKey() + ": " + reason
                    + ", and the level already has an applied takeover or Iris generation history. Restore the pack, or run /iris world takeover "
                    + takeover.levelKey() + " off to hand the level back to its own generator.");
        }
        ModdedIrisLog.error("Iris skipped the dimension takeover of {}: {}, so the dimension keeps its own generator and the takeover stays unapplied",
                takeover.levelKey(), reason);
    }

    private static String caseHint(ModdedDimensionTakeover takeover, StagedTypes staged) {
        String wanted = takeover.pack() + ":" + takeover.packDimension();
        for (String installed : staged.installed().keySet()) {
            if (installed.equalsIgnoreCase(wanted)) {
                return "; pack names and dimension keys are case-sensitive, the installed dimension is " + installed;
            }
        }
        return "";
    }

    private static String sameIgnoringCase(String actual, String configured) {
        return actual.equalsIgnoreCase(configured) ? actual : configured;
    }

    public static String fingerprint(Map<String, String> configured) {
        StringBuilder canonical = new StringBuilder();
        for (Map.Entry<String, String> entry : new TreeMap<>(configured).entrySet()) {
            canonical.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
        return canonical.toString();
    }

    public record StagedTypes(Map<String, String> installed, Map<Path, String> retained) {
        public static StagedTypes create() {
            return new StagedTypes(new LinkedHashMap<>(), new LinkedHashMap<>());
        }

        String typeRef(ModdedDimensionTakeover takeover, Path worldRoot) {
            if (worldRoot != null) {
                String retainedType = retained.get(takeover.storageFolder(worldRoot).toAbsolutePath().normalize());
                if (retainedType != null) {
                    return retainedType;
                }
            }
            return installed.get(takeover.pack() + ":" + takeover.packDimension());
        }
    }
}
