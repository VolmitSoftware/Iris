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

import art.arcane.iris.modded.catalog.ModdedWorldgenCatalogs;
import art.arcane.iris.pack.ingest.catalog.CatalogLevelStem;
import art.arcane.iris.pack.ingest.catalog.WorldgenCatalog;
import art.arcane.iris.pack.ingest.catalog.WorldgenCatalogStore;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.volmlib.util.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class ModdedDimensionReplacements {
    public static final String IRIS_GENERATOR = "irisworldgen:iris";
    private static final Set<String> VANILLA_LEVELS = Set.of("minecraft:overworld", "minecraft:the_nether",
            "minecraft:the_end");
    private static volatile RecordedCatalog recordedCatalog;
    private static final String PLACEHOLDER_BIOME_SOURCE = "minecraft:fixed";
    private static final String PLACEHOLDER_BIOME = "minecraft:plains";
    private static final String DIMENSIONS = "dimensions";
    private static final String JSON = ".json";

    private ModdedDimensionReplacements() {
    }

    public static String levelStemJson(String dimensionTypeKey, ModdedDimensionReplacement replacement) {
        JSONObject biomeSource = new JSONObject();
        biomeSource.put("type", PLACEHOLDER_BIOME_SOURCE);
        biomeSource.put("biome", PLACEHOLDER_BIOME);
        JSONObject generator = new JSONObject();
        generator.put("type", IRIS_GENERATOR);
        generator.put("biome_source", biomeSource);
        generator.put("dimension", replacement.packRef());
        JSONObject stem = new JSONObject();
        stem.put("type", Objects.requireNonNull(dimensionTypeKey, "dimensionTypeKey"));
        stem.put("generator", generator);
        return stem.toString(2) + "\n";
    }

    public static Path writeLevelStem(Path datapackRoot, ModdedDimensionReplacement replacement, String dimensionTypeKey)
            throws IOException {
        Path output = datapackRoot.resolve("data").resolve(replacement.namespace()).resolve("dimension");
        String[] segments = replacement.path().split("/");
        for (int index = 0; index < segments.length - 1; index++) {
            output = output.resolve(segments[index]);
        }
        output = output.resolve(segments[segments.length - 1] + ".json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, levelStemJson(dimensionTypeKey, replacement), StandardCharsets.UTF_8);
        return output;
    }

    public static void writeLevelStems(Path datapackRoot, Map<String, ModdedDimensionReplacement> configured,
                                       StagedTypes staged, Path worldRoot, ReplaceableLevels levels) throws IOException {
        for (ModdedDimensionReplacement replacement : new TreeMap<>(configured).values()) {
            if (!levels.replaceable(replacement.levelKey())) {
                ModdedIrisLog.error(unreplaceableReason(replacement.levelKey(), levels)
                        + " Iris writes no dimension override for it.");
                continue;
            }
            String typeRef = staged.typeRef(replacement, worldRoot);
            if (typeRef == null) {
                skipUnstaged(replacement, staged, worldRoot);
                continue;
            }
            writeLevelStem(datapackRoot, replacement, typeRef);
            ModdedIrisLog.info("Iris dimension replacement staged: {} generates from {} with dimension type {}",
                    replacement.levelKey(), replacement.packRef(), typeRef);
        }
    }

    public static Map<String, ModdedDimensionReplacement> configured() throws IOException {
        return canonical(ModdedModConfig.get().dimensionReplacements(), IrisPlatforms.get().packsFolderNoCreate().toPath());
    }

    public static Map<String, ModdedDimensionReplacement> stageable(Map<String, ModdedDimensionReplacement> configured,
                                                                   ReplaceableLevels levels) {
        Map<String, ModdedDimensionReplacement> stageable = new TreeMap<>();
        for (ModdedDimensionReplacement replacement : configured.values()) {
            if (levels.replaceable(replacement.levelKey())) {
                stageable.put(replacement.levelKey(), replacement);
            }
        }
        return Map.copyOf(stageable);
    }

    public static ReplaceableLevels replaceableLevels(Path worldRoot, Map<String, ModdedDimensionReplacement> configured)
            throws IOException {
        if (configured.isEmpty()) {
            return ReplaceableLevels.unknown();
        }
        return replaceableLevels(worldRoot);
    }

    public static ReplaceableLevels replaceableLevels(Path worldRoot) throws IOException {
        Set<String> applied = worldRoot == null
                ? Set.of()
                : ModdedReplacementReconciler.readState(worldRoot).applied().keySet();
        Optional<WorldgenCatalog> catalog = ModdedWorldgenCatalogs.latest();
        if (catalog.isEmpty()) {
            catalog = recordedCatalog();
        }
        return catalog.isEmpty()
                ? ReplaceableLevels.unknown()
                : ReplaceableLevels.of(catalog.get().levelStems(), applied);
    }

    public static String unreplaceableReason(String levelKey, ReplaceableLevels levels) {
        if (!levels.known()) {
            return "Iris refuses to replace " + levelKey + ": it has no record of which dimensions this server loads"
                    + " from level stems yet. Start the server once so Iris can read the dimension registry.";
        }
        return "Iris refuses to replace " + levelKey + ": it is not a dimension the server loads from a level stem;"
                + " a mod creates it while the server runs, so its data stays untouched.";
    }

    private static Optional<WorldgenCatalog> recordedCatalog() {
        Path file = ModdedWorldgenCatalogs.catalogFile();
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            long modified = Files.getLastModifiedTime(file).toMillis();
            RecordedCatalog cached = recordedCatalog;
            if (cached != null && cached.file().equals(file) && cached.modified() == modified) {
                return Optional.of(cached.catalog());
            }
            WorldgenCatalog catalog = WorldgenCatalogStore.read(file);
            recordedCatalog = new RecordedCatalog(file, modified, catalog);
            return Optional.of(catalog);
        } catch (IOException | RuntimeException unreadable) {
            ModdedIrisLog.error("Iris could not read the recorded worldgen catalog at {}; only the vanilla dimensions are replaceable until the next start records it",
                    file, unreadable);
            return Optional.empty();
        }
    }

    public static OptionalLong loadedSeed(String levelKey) {
        ModdedModConfig config = ModdedModConfig.loaded();
        if (config == null) {
            return OptionalLong.empty();
        }
        ModdedDimensionReplacement replacement = config.dimensionReplacements().get(levelKey);
        return replacement == null ? OptionalLong.empty() : replacement.seed();
    }

    static Map<String, ModdedDimensionReplacement> canonical(Map<String, ModdedDimensionReplacement> configured,
                                                            Path packsRoot) throws IOException {
        Map<String, ModdedDimensionReplacement> canonical = new TreeMap<>();
        for (ModdedDimensionReplacement replacement : configured.values()) {
            canonical.put(replacement.levelKey(), canonical(replacement, packsRoot));
        }
        return Map.copyOf(canonical);
    }

    public static ModdedDimensionReplacement canonical(ModdedDimensionReplacement replacement, Path packsRoot)
            throws IOException {
        Path pack = packsRoot.resolve(replacement.pack());
        if (!Files.isDirectory(pack)) {
            return replacement;
        }
        String packName = sameIgnoringCase(pack.toRealPath().getFileName().toString(), replacement.pack());
        Path dimensions = pack.resolve(DIMENSIONS);
        Path dimensionFile = dimensions.resolve(replacement.packDimension() + JSON);
        String dimension = replacement.packDimension();
        if (Files.isRegularFile(dimensionFile)) {
            String relative = dimensions.toRealPath().relativize(dimensionFile.toRealPath()).toString()
                    .replace(File.separatorChar, '/');
            dimension = sameIgnoringCase(relative.substring(0, relative.length() - JSON.length()), replacement.packDimension());
        }
        return replacement.withPack(packName, dimension);
    }

    public static boolean installed(ModdedDimensionReplacement replacement, Path packsRoot) {
        return Files.isRegularFile(packsRoot.resolve(replacement.pack()).resolve(DIMENSIONS)
                .resolve(replacement.packDimension() + JSON));
    }

    public static boolean matches(ModdedDimensionReplacement applied, ModdedDimensionReplacement configured) {
        if (applied == null || configured == null) {
            return applied == configured;
        }
        if (!applied.packRef().equals(configured.packRef())) {
            return false;
        }
        return configured.seed().isEmpty() || configured.seed().equals(applied.seed());
    }

    public static Set<String> changedLevels(Map<String, ModdedDimensionReplacement> applied,
                                            Map<String, ModdedDimensionReplacement> configured) {
        Set<String> levels = new TreeSet<>(applied.keySet());
        levels.addAll(configured.keySet());
        Set<String> changed = new TreeSet<>();
        for (String level : levels) {
            if (!matches(applied.get(level), configured.get(level))) {
                changed.add(level);
            }
        }
        return changed;
    }

    private static void skipUnstaged(ModdedDimensionReplacement replacement, StagedTypes staged, Path worldRoot)
            throws IOException {
        String reason = "pack '" + replacement.pack() + "' dimension '" + replacement.packDimension()
                + "' is not installed or failed validation" + caseHint(replacement, staged);
        if (worldRoot != null && ModdedReplacementReconciler.hasReplacementRecord(worldRoot, replacement.levelKey())) {
            throw new IOException("Iris refused to stage the replacement of " + replacement.levelKey() + ": " + reason
                    + ", and the level already has an applied replacement or Iris generation history. Restore the pack, or remove the "
                    + replacement.levelKey() + " entry from dimensionReplacements in modded.json to hand the level back to its own generator.");
        }
        ModdedIrisLog.error("Iris skipped the replacement of {}: {}, so the dimension keeps its own generator and the replacement stays unapplied",
                replacement.levelKey(), reason);
    }

    private static String caseHint(ModdedDimensionReplacement replacement, StagedTypes staged) {
        String wanted = replacement.pack() + ":" + replacement.packDimension();
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

    public static String fingerprint(Map<String, ModdedDimensionReplacement> configured) {
        StringBuilder canonical = new StringBuilder();
        for (Map.Entry<String, ModdedDimensionReplacement> entry : new TreeMap<>(configured).entrySet()) {
            canonical.append(entry.getKey()).append('=').append(entry.getValue().packRef()).append('\n');
        }
        return canonical.toString();
    }

    private record RecordedCatalog(Path file, long modified, WorldgenCatalog catalog) {
    }

    public record ReplaceableLevels(boolean known, Map<String, String> stemGenerators, Set<String> applied) {
        public ReplaceableLevels {
            stemGenerators = Map.copyOf(stemGenerators);
            applied = Set.copyOf(applied);
        }

        public static ReplaceableLevels unknown() {
            return new ReplaceableLevels(false, Map.of(), Set.of());
        }

        public static ReplaceableLevels of(List<CatalogLevelStem> stems, Set<String> applied) {
            Map<String, String> generators = new TreeMap<>();
            for (CatalogLevelStem stem : stems) {
                generators.put(stem.key(), stem.generatorType());
            }
            return new ReplaceableLevels(true, generators, applied);
        }

        public boolean replaceable(String levelKey) {
            if (VANILLA_LEVELS.contains(levelKey)) {
                return true;
            }
            String generator = stemGenerators.get(levelKey);
            if (generator == null) {
                return false;
            }
            return !IRIS_GENERATOR.equals(generator) || applied.contains(levelKey);
        }
    }

    public record StagedTypes(Map<String, String> installed, Map<Path, String> retained) {
        public static StagedTypes create() {
            return new StagedTypes(new LinkedHashMap<>(), new LinkedHashMap<>());
        }

        String typeRef(ModdedDimensionReplacement replacement, Path worldRoot) {
            if (worldRoot != null) {
                String retainedType = retained.get(replacement.storageFolder(worldRoot).toAbsolutePath().normalize());
                if (retainedType != null) {
                    return retainedType;
                }
            }
            return installed.get(replacement.pack() + ":" + replacement.packDimension());
        }
    }
}
