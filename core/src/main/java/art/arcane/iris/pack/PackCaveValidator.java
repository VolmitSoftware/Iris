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

import art.arcane.iris.generation.cave.CaveBounds;
import art.arcane.iris.pack.loading.RemovedPackKeys;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Checks a pack's caves: a leftover {@code caveProfile}, which carves nothing any more, the caves that dimensions,
 * regions and biomes list under {@code caves} against the files of the {@code caves/} folder, and the numbers of each
 * cave file against what their keys allow.
 */
final class PackCaveValidator {
    static final String CAVES_FOLDER = "caves";
    static final String CAVES_KEY = "caves";
    static final String REMOVED_PROFILE_KEY = RemovedPackKeys.CAVE_PROFILE;
    static final String REMOVED_PROFILE_SNIPPET_FOLDER = "snippet/cave-profile";
    private static final String REMOVED_PROFILE_ADVICE = "was removed and carves no caves; list caves under \"" + CAVES_KEY + "\" instead";

    record Validation(List<String> errors, List<String> warnings) {
        Validation {
            errors = List.copyOf(errors);
            warnings = List.copyOf(warnings);
        }
    }

    private PackCaveValidator() {
    }

    static Validation validate(File packFolder) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (packFolder == null || !packFolder.isDirectory()) {
            return new Validation(errors, warnings);
        }
        File cavesFolder = new File(packFolder, CAVES_FOLDER);
        Set<String> caveKeys = validateCaveFiles(cavesFolder, errors);
        for (String folderName : PackValidator.STRUCTURE_HOST_FOLDERS) {
            validateHosts(packFolder, folderName, caveKeys, errors, warnings);
        }
        validateRemovedProfileSnippets(packFolder, warnings);
        return new Validation(errors, warnings);
    }

    private static Set<String> validateCaveFiles(File cavesFolder, List<String> errors) {
        Set<String> keys = new HashSet<>();
        if (!cavesFolder.isDirectory()) {
            return keys;
        }
        List<File> files = PackValidationIo.listJsonRecursive(cavesFolder);
        files.sort(Comparator.comparing(File::getPath));
        for (File file : files) {
            String key = PackValidationIo.deriveKey(cavesFolder, file);
            keys.add(key);
            validateCaveFile(key, file, errors);
        }
        return keys;
    }

    private static void validateCaveFile(String key, File file, List<String> errors) {
        try {
            var cave = JsonParser.parseString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            if (!cave.isJsonObject()) {
                errors.add("Cave '" + key + "' must be a JSON object.");
                return;
            }
            CaveBounds.valuesOutOfBounds(cave.getAsJsonObject()).forEach(problem -> errors.add("Cave '" + key + "' " + problem + "."));
        } catch (IOException | JsonParseException unreadable) {
            errors.add("Cave '" + key + "' is not valid JSON: " + unreadable.getMessage());
        }
    }

    private static void validateHosts(File packFolder, String folderName, Set<String> caveKeys, List<String> errors, List<String> warnings) {
        File hostFolder = new File(packFolder, folderName);
        if (!hostFolder.isDirectory()) {
            return;
        }
        List<File> files = PackValidationIo.listJsonRecursive(hostFolder);
        files.sort(Comparator.comparing(File::getPath));
        String hostType = PackStructurePlacementValidator.structureHostType(folderName);
        for (File file : files) {
            JSONObject host = PackValidationIo.readJson(file);
            if (host == null) {
                continue;
            }
            String hostLabel = hostType + " '" + PackValidationIo.deriveKey(hostFolder, file) + "'";
            if (host.has(REMOVED_PROFILE_KEY)) {
                warnings.add(hostLabel + " still has " + REMOVED_PROFILE_KEY + ", which " + REMOVED_PROFILE_ADVICE + ".");
            }
            validateListedCaves(hostLabel, host.optJSONArray(CAVES_KEY), caveKeys, errors);
        }
    }

    private static void validateListedCaves(String host, JSONArray listed, Set<String> caveKeys, List<String> errors) {
        if (listed == null) {
            return;
        }
        for (int index = 0; index < listed.length(); index++) {
            Object entry = listed.opt(index);
            if (!(entry instanceof String key) || key.isBlank()) {
                errors.add(host + " " + CAVES_KEY + "[" + index + "] must be the name of a file in " + CAVES_FOLDER + "/.");
                continue;
            }
            if (!caveKeys.contains(key)) {
                errors.add(host + " lists the cave '" + key + "', but the pack has no " + CAVES_FOLDER + "/" + key + ".json.");
            }
        }
    }

    private static void validateRemovedProfileSnippets(File packFolder, List<String> warnings) {
        File snippetFolder = new File(packFolder, REMOVED_PROFILE_SNIPPET_FOLDER);
        if (!snippetFolder.isDirectory()) {
            return;
        }
        List<File> files = PackValidationIo.listJsonRecursive(snippetFolder);
        files.sort(Comparator.comparing(File::getPath));
        for (File file : files) {
            warnings.add("Snippet '" + REMOVED_PROFILE_SNIPPET_FOLDER + "/" + PackValidationIo.deriveKey(snippetFolder, file)
                    + "' is a " + REMOVED_PROFILE_KEY + ", which " + REMOVED_PROFILE_ADVICE + ".");
        }
    }
}
