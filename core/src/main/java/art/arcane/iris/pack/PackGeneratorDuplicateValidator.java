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

package art.arcane.iris.pack;

import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Warns when two generator files with identical content are both referenced by biomes. IrisGenerator's
 * equals ignores the load key and IrisComplex buckets generators into a HashSet, so only one of a
 * content-identical pair survives; biomes linked to the losing key resolve 0/0 height bounds and
 * contribute a flat band at fluid height.
 *
 * <p>The fingerprint is a conservative approximation of IrisGenerator.equals built from raw JSON
 * (sorted keys, canonical number formatting). It can miss a collision where one file spells out a
 * value the other leaves defaulted, and it ignores fields the POJO does not declare - acceptable
 * for a warning aimed at the copy-the-file-and-forget-the-seed mistake.
 */
final class PackGeneratorDuplicateValidator {
    private static final String GENERATOR_SNIPPET_FOLDER = "snippet/generator-layer/";

    private PackGeneratorDuplicateValidator() {
    }

    static List<String> validateDuplicateGenerators(File packFolder) {
        List<String> warnings = new ArrayList<>();
        if (packFolder == null || !packFolder.isDirectory()) {
            return warnings;
        }
        File generatorsFolder = new File(packFolder, "generators");
        if (!generatorsFolder.isDirectory()) {
            return warnings;
        }

        Set<String> referenced = collectReferencedGeneratorKeys(packFolder);
        Map<String, List<String>> byFingerprint = new TreeMap<>();
        List<File> generatorFiles = PackValidationIo.listJsonRecursive(generatorsFolder);
        generatorFiles.sort(Comparator.comparing(File::getPath));
        for (File generatorFile : generatorFiles) {
            String key = PackValidationIo.deriveKey(generatorsFolder, generatorFile);
            String fingerprint;
            try {
                fingerprint = fingerprint(new JSONObject(Files.readString(generatorFile.toPath(), StandardCharsets.UTF_8)));
            } catch (Throwable e) {
                continue;
            }
            byFingerprint.computeIfAbsent(fingerprint, ignored -> new ArrayList<>()).add(key);
        }

        for (List<String> group : byFingerprint.values()) {
            List<String> referencedKeys = group.stream().filter(referenced::contains).sorted().toList();
            if (referencedKeys.size() < 2) {
                continue;
            }
            warnings.add("Generators " + String.join(", ", referencedKeys)
                    + " have identical content and are both referenced by biomes. Iris buckets generators by value"
                    + " (IrisGenerator equals ignores the load key), so only one survives and biomes referencing the"
                    + " others get a zero height band. Give each generator a distinct value (for example a different"
                    + " seed) or point every biome at a single key.");
        }
        return warnings;
    }

    private static Set<String> collectReferencedGeneratorKeys(File packFolder) {
        Set<String> referenced = new HashSet<>();
        File biomesFolder = new File(packFolder, "biomes");
        if (!biomesFolder.isDirectory()) {
            return referenced;
        }
        for (File biomeFile : PackValidationIo.listJsonRecursive(biomesFolder)) {
            JSONObject biome;
            try {
                biome = new JSONObject(Files.readString(biomeFile.toPath(), StandardCharsets.UTF_8));
            } catch (Throwable e) {
                continue;
            }
            JSONArray links = biome.optJSONArray("generators");
            if (links == null) {
                continue;
            }
            for (int i = 0; i < links.length(); i++) {
                JSONObject link = links.optJSONObject(i);
                if (link == null) {
                    String reference = links.optString(i, null);
                    link = resolveGeneratorLayerSnippet(packFolder, reference);
                    if (link == null) {
                        continue;
                    }
                }
                referenced.add(link.optString("generator", "default"));
            }
        }
        return referenced;
    }

    private static JSONObject resolveGeneratorLayerSnippet(File packFolder, String reference) {
        if (reference == null || !reference.startsWith("snippet/")) {
            return null;
        }
        String resolved = reference.startsWith(GENERATOR_SNIPPET_FOLDER)
                ? reference
                : GENERATOR_SNIPPET_FOLDER + reference.substring("snippet/".length());
        File snippet = new File(packFolder, resolved + ".json");
        if (!snippet.isFile()) {
            return null;
        }
        try {
            return new JSONObject(Files.readString(snippet.toPath(), StandardCharsets.UTF_8));
        } catch (Throwable e) {
            return null;
        }
    }

    private static String fingerprint(Object value) {
        if (value instanceof JSONObject object) {
            Map<String, String> sorted = new TreeMap<>();
            for (String key : object.keySet()) {
                sorted.put(key, fingerprint(object.get(key)));
            }
            StringBuilder builder = new StringBuilder("{");
            sorted.forEach((key, child) -> builder.append(key).append('=').append(child).append(';'));
            return builder.append('}').toString();
        }
        if (value instanceof JSONArray array) {
            StringBuilder builder = new StringBuilder("[");
            for (int i = 0; i < array.length(); i++) {
                builder.append(fingerprint(array.get(i))).append(';');
            }
            return builder.append(']').toString();
        }
        if (value instanceof Number number) {
            // Gson deserializes 1 and 1.0 into the same field value; fingerprint them alike.
            return Double.toString(number.doubleValue());
        }
        return String.valueOf(value);
    }
}
