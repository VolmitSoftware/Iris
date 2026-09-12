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

package art.arcane.iris.pack;

import art.arcane.iris.generation.biome.IrisBiomeCustomSpawnType;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformEntityType;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

final class PackSpawnValidator {
    private PackSpawnValidator() {
    }

    static List<String> validateSpawnerEntityReferences(File spawnersFolder, File entitiesFolder) {
        List<String> blockingErrors = new ArrayList<>();
        if (spawnersFolder == null || !spawnersFolder.isDirectory()) {
            return blockingErrors;
        }

        Path entityRoot = entitiesFolder.toPath().toAbsolutePath().normalize();
        Set<Path> validEntityFiles = new HashSet<>();
        Map<Path, String> invalidEntityFiles = new HashMap<>();
        List<File> spawnerFiles = PackValidationIo.listJsonRecursive(spawnersFolder);
        spawnerFiles.sort(Comparator.comparing(File::getPath));
        for (File spawnerFile : spawnerFiles) {
            String spawnerKey = PackValidationIo.deriveKey(spawnersFolder, spawnerFile);
            JSONObject spawner;
            try {
                spawner = new JSONObject(Files.readString(spawnerFile.toPath(), StandardCharsets.UTF_8));
            } catch (Throwable e) {
                blockingErrors.add("Spawner '" + spawnerKey + "' has invalid JSON: " + e.getMessage());
                continue;
            }

            validateSpawnerSpawnEntries(spawnerKey, spawner, "spawns", entityRoot,
                    validEntityFiles, invalidEntityFiles, blockingErrors);
            validateSpawnerSpawnEntries(spawnerKey, spawner, "initialSpawns", entityRoot,
                    validEntityFiles, invalidEntityFiles, blockingErrors);
        }
        return blockingErrors;
    }

    private static void validateSpawnerSpawnEntries(String spawnerKey,
                                                    JSONObject spawner,
                                                    String field,
                                                    Path entityRoot,
                                                    Set<Path> validEntityFiles,
                                                    Map<Path, String> invalidEntityFiles,
                                                    List<String> blockingErrors) {
        if (!spawner.has(field)) {
            return;
        }
        JSONArray entries = spawner.optJSONArray(field);
        if (entries == null) {
            blockingErrors.add("Spawner '" + spawnerKey + "' " + field + " must be an array.");
            return;
        }

        for (int index = 0; index < entries.length(); index++) {
            JSONObject entry = entries.optJSONObject(index);
            if (entry == null) {
                blockingErrors.add("Spawner '" + spawnerKey + "' " + field
                        + " has a non-object entry at index " + index + ".");
                continue;
            }
            if (!entry.has("entity") || entry.isNull("entity")) {
                blockingErrors.add("Spawner '" + spawnerKey + "' " + field
                        + " has an entry without an entity reference at index " + index + ".");
                continue;
            }

            Object rawEntity = entry.get("entity");
            if (!(rawEntity instanceof String entityKey)) {
                blockingErrors.add("Spawner '" + spawnerKey + "' " + field
                        + " entity reference at index " + index + " must be a string.");
                continue;
            }
            if (entityKey.isBlank()) {
                blockingErrors.add("Spawner '" + spawnerKey + "' " + field
                        + " has a blank entity reference at index " + index + ".");
                continue;
            }

            Path entityFile;
            try {
                if (entityKey.indexOf('\\') >= 0) {
                    throw new IllegalArgumentException("backslash path separators are not portable");
                }
                entityFile = entityRoot.resolve(entityKey + ".json").normalize();
            } catch (RuntimeException e) {
                blockingErrors.add("Spawner '" + spawnerKey + "' " + field + " entry at index " + index
                        + " has invalid entity reference '" + entityKey + "': " + e.getMessage());
                continue;
            }
            if (!entityFile.startsWith(entityRoot)) {
                blockingErrors.add("Spawner '" + spawnerKey + "' " + field + " entry at index " + index
                        + " has unsafe entity reference '" + entityKey + "'.");
                continue;
            }
            if (!Files.isRegularFile(entityFile)) {
                blockingErrors.add("Spawner '" + spawnerKey + "' " + field + " entry at index " + index
                        + " references missing entity '" + entityKey + "'.");
                continue;
            }

            String invalidJson = invalidEntityFiles.get(entityFile);
            if (invalidJson != null) {
                blockingErrors.add("Spawner '" + spawnerKey + "' " + field + " entry at index " + index
                        + " references malformed entity '" + entityKey + "': " + invalidJson);
                continue;
            }
            if (validEntityFiles.contains(entityFile)) {
                continue;
            }

            try {
                new JSONObject(Files.readString(entityFile, StandardCharsets.UTF_8));
                validEntityFiles.add(entityFile);
            } catch (Throwable e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                invalidEntityFiles.put(entityFile, message);
                blockingErrors.add("Spawner '" + spawnerKey + "' " + field + " entry at index " + index
                        + " references malformed entity '" + entityKey + "': " + message);
            }
        }
    }

    static List<String> validateCustomBiomeSpawns(File biomesFolder, Function<String, SpawnCategoryResolution> categoryResolver) {
        List<String> blockingErrors = new ArrayList<>();
        if (biomesFolder == null || !biomesFolder.isDirectory()) {
            return blockingErrors;
        }

        List<File> biomeFiles = PackValidationIo.listJsonRecursive(biomesFolder);
        biomeFiles.sort(Comparator.comparing(File::getPath));
        for (File biomeFile : biomeFiles) {
            String biomeKey = PackValidationIo.deriveKey(biomesFolder, biomeFile);
            JSONObject biome;
            try {
                biome = new JSONObject(Files.readString(biomeFile.toPath(), StandardCharsets.UTF_8));
            } catch (Throwable e) {
                blockingErrors.add("Biome '" + biomeKey + "' has invalid JSON: " + e.getMessage());
                continue;
            }

            JSONArray derivatives = biome.optJSONArray("customDerivitives");
            if (derivatives == null) {
                if (biome.has("customDerivitives") && !biome.isNull("customDerivitives")) {
                    blockingErrors.add("Biome '" + biomeKey + "' customDerivitives must be an array.");
                }
                continue;
            }
            for (int derivativeIndex = 0; derivativeIndex < derivatives.length(); derivativeIndex++) {
                JSONObject derivative = derivatives.optJSONObject(derivativeIndex);
                if (derivative == null) {
                    blockingErrors.add("Biome '" + biomeKey + "' has a non-object custom derivative at index " + derivativeIndex + ".");
                    continue;
                }
                validateCustomBiomeDerivativeTags(biomeKey, derivative, derivativeIndex, blockingErrors);
                validateCustomBiomeDerivativeSpawns(
                        biomeKey, derivative, derivativeIndex, categoryResolver, blockingErrors);
            }
        }
        return blockingErrors;
    }

    private static void validateCustomBiomeDerivativeTags(String biomeKey,
                                                           JSONObject derivative,
                                                           int derivativeIndex,
                                                           List<String> blockingErrors) {
        if (!derivative.has("tags") || derivative.isNull("tags")) {
            return;
        }
        String derivativeId = derivative.optString("id", "#" + derivativeIndex);
        JSONArray tags = derivative.optJSONArray("tags");
        if (tags == null) {
            blockingErrors.add("Biome '" + biomeKey + "' custom derivative '" + derivativeId
                    + "' tags must be an array.");
            return;
        }
        for (int tagIndex = 0; tagIndex < tags.length(); tagIndex++) {
            Object rawTag = tags.opt(tagIndex);
            if (!(rawTag instanceof String tag)) {
                blockingErrors.add("Biome '" + biomeKey + "' custom derivative '" + derivativeId
                        + "' has a non-string tag at index " + tagIndex + ".");
                continue;
            }
            String normalized = tag.trim().toLowerCase(Locale.ROOT);
            if (normalized.indexOf(':') < 0) {
                normalized = "minecraft:" + normalized;
            }
            if (!isSafeResourceKey(normalized)) {
                blockingErrors.add("Biome '" + biomeKey + "' custom derivative '" + derivativeId
                        + "' has invalid tag '" + tag + "'.");
            }
        }
    }

    private static boolean isSafeResourceKey(String key) {
        if (!PackValidator.RESOURCE_KEY_PATTERN.matcher(key).matches()) {
            return false;
        }
        int separator = key.indexOf(':');
        String[] segments = key.substring(separator + 1).split("/");
        for (String segment : segments) {
            if (segment.equals("..")) {
                return false;
            }
        }
        return true;
    }

    private static void validateCustomBiomeDerivativeSpawns(String biomeKey,
                                                             JSONObject derivative,
                                                             int derivativeIndex,
                                                             Function<String, SpawnCategoryResolution> categoryResolver,
                                                             List<String> blockingErrors) {
        JSONArray spawns = derivative.optJSONArray("spawns");
        if (spawns == null) {
            if (derivative.has("spawns") && !derivative.isNull("spawns")) {
                String derivativeId = derivative.optString("id", "#" + derivativeIndex);
                blockingErrors.add("Biome '" + biomeKey + "' custom derivative '" + derivativeId
                        + "' spawns must be an array.");
            }
            return;
        }
        String derivativeId = derivative.optString("id", "#" + derivativeIndex);
        for (int spawnIndex = 0; spawnIndex < spawns.length(); spawnIndex++) {
            JSONObject spawn = spawns.optJSONObject(spawnIndex);
            if (spawn == null) {
                blockingErrors.add("Biome '" + biomeKey + "' custom derivative '" + derivativeId
                        + "' has a non-object spawn at index " + spawnIndex + ".");
                continue;
            }

            String type = spawn.optString("type", "").trim().toLowerCase(Locale.ROOT);
            if (type.isEmpty()) {
                blockingErrors.add("Biome '" + biomeKey + "' custom derivative '" + derivativeId
                        + "' has a spawn without an entity type at index " + spawnIndex + ".");
                continue;
            }
            String typeKey = type.indexOf(':') >= 0 ? type : "minecraft:" + type;
            SpawnCategoryResolution resolution;
            try {
                resolution = categoryResolver == null ? null : categoryResolver.apply(typeKey);
            } catch (Throwable e) {
                IrisLogging.reportError("PackValidator failed to resolve spawn category for '" + typeKey + "'", e);
                blockingErrors.add("Biome '" + biomeKey + "' custom derivative '" + derivativeId
                        + "' spawn category lookup failed for '" + typeKey + "': " + e.getMessage());
                continue;
            }
            if (resolution != null && !resolution.entityKnown()) {
                blockingErrors.add("Biome '" + biomeKey + "' custom derivative '" + derivativeId
                        + "' spawn references unknown entity type '" + typeKey + "'.");
                continue;
            }
            String expectedGroup = resolution == null ? null : resolution.category();
            String group = spawn.optString("group", "").trim();
            if (group.isEmpty()) {
                if (expectedGroup != null && !expectedGroup.isBlank()
                        && !IrisBiomeCustomSpawnType.MISC.name().equalsIgnoreCase(expectedGroup)) {
                    blockingErrors.add("Biome '" + biomeKey + "' custom derivative '" + derivativeId
                            + "' spawn '" + typeKey + "' must declare group '"
                            + expectedGroup.toUpperCase(Locale.ROOT) + "'.");
                }
                continue;
            }

            IrisBiomeCustomSpawnType configuredGroup;
            try {
                configuredGroup = IrisBiomeCustomSpawnType.valueOf(group);
            } catch (IllegalArgumentException e) {
                blockingErrors.add("Biome '" + biomeKey + "' custom derivative '" + derivativeId
                        + "' spawn '" + typeKey + "' declares unknown group '" + group + "'.");
                continue;
            }

            if (expectedGroup != null && !expectedGroup.isBlank()
                    && !configuredGroup.name().equalsIgnoreCase(expectedGroup)) {
                blockingErrors.add("Biome '" + biomeKey + "' custom derivative '" + derivativeId
                        + "' spawn '" + typeKey + "' declares group '" + configuredGroup.name()
                        + "' but the live entity registry requires '" + expectedGroup.toUpperCase(Locale.ROOT) + "'.");
            }
        }
    }

    static SpawnCategoryResolution resolveEntitySpawnCategory(String typeKey) {
        if (!IrisPlatforms.isBound()) {
            return null;
        }
        PlatformRegistries registries = IrisPlatforms.get().registries();
        if (registries == null) {
            return null;
        }
        PlatformEntityType entityType = registries.entity(typeKey);
        return entityType == null
                ? SpawnCategoryResolution.unknown()
                : SpawnCategoryResolution.known(entityType.spawnCategory());
    }

    record SpawnCategoryResolution(boolean entityKnown, String category) {
        static SpawnCategoryResolution unknown() {
            return new SpawnCategoryResolution(false, null);
        }

        static SpawnCategoryResolution known(String category) {
            return new SpawnCategoryResolution(true, category);
        }
    }
}
