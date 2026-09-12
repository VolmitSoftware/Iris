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
import java.util.List;
import java.util.Locale;

/**
 * Catches the shared IrisStyledRange class default (min 16, max 32) leaking into pack content.
 * The POJO cannot distinguish an empty object from an explicit 16/32 after Gson runs, so the raw
 * JSON is the only place this mistake is still visible: an objects[].densityStyle of {} places
 * 16-32 objects per chunk, and a caveProfile.densityThreshold of {} hollows the whole cave range.
 */
final class PackStyledRangeDefaultValidator {
    private static final String STYLE_RANGE_SNIPPET_FOLDER = "snippet/style-range/";

    record Validation(List<String> errors, List<String> warnings) {
        Validation {
            errors = List.copyOf(errors);
            warnings = List.copyOf(warnings);
        }
    }

    private PackStyledRangeDefaultValidator() {
    }

    static Validation validate(File packFolder) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (packFolder == null || !packFolder.isDirectory()) {
            return new Validation(errors, warnings);
        }

        for (String folderName : PackValidator.STRUCTURE_HOST_FOLDERS) {
            File resourceFolder = new File(packFolder, folderName);
            if (!resourceFolder.isDirectory()) {
                continue;
            }
            List<File> resourceFiles = PackValidationIo.listJsonRecursive(resourceFolder);
            resourceFiles.sort(Comparator.comparing(File::getPath));
            String type = hostType(folderName);
            for (File resourceFile : resourceFiles) {
                String key = PackValidationIo.deriveKey(resourceFolder, resourceFile);
                JSONObject json;
                try {
                    json = new JSONObject(Files.readString(resourceFile.toPath(), StandardCharsets.UTF_8));
                } catch (Throwable e) {
                    continue;
                }

                validateObjectPlacements(packFolder, type, key, json, errors, warnings);
                validateCaveProfileThreshold(packFolder, type, key, json.optJSONObject("caveProfile"), errors, warnings);
            }
        }
        return new Validation(errors, warnings);
    }

    private static void validateObjectPlacements(File packFolder, String type, String key, JSONObject json,
                                                 List<String> errors, List<String> warnings) {
        JSONArray objects = json.optJSONArray("objects");
        if (objects == null) {
            return;
        }
        for (int i = 0; i < objects.length(); i++) {
            JSONObject placement = objects.optJSONObject(i);
            if (placement == null || !placement.has("densityStyle") || placement.isNull("densityStyle")) {
                continue;
            }
            checkRange(packFolder, placement.opt("densityStyle"),
                    type + " '" + key + "' objects[" + i + "].densityStyle",
                    "16-32 objects per chunk", "remove densityStyle to use the scalar density field",
                    errors, warnings);
        }
    }

    private static void validateCaveProfileThreshold(File packFolder, String type, String key, JSONObject caveProfile,
                                                     List<String> errors, List<String> warnings) {
        if (caveProfile == null || !caveProfile.has("densityThreshold") || caveProfile.isNull("densityThreshold")) {
            return;
        }
        checkRange(packFolder, caveProfile.opt("densityThreshold"),
                type + " '" + key + "' caveProfile.densityThreshold",
                "a carve threshold of 16-32, hollowing the entire vertical range",
                "remove densityThreshold to keep the cave profile default",
                errors, warnings);
    }

    private static void checkRange(File packFolder, Object raw, String path, String consequence, String removal,
                                   List<String> errors, List<String> warnings) {
        JSONObject range = null;
        String via = "";
        if (raw instanceof JSONObject inline) {
            range = inline;
        } else if (raw instanceof String reference && reference.startsWith("snippet/")) {
            String resolved = reference.startsWith(STYLE_RANGE_SNIPPET_FOLDER)
                    ? reference
                    : STYLE_RANGE_SNIPPET_FOLDER + reference.substring("snippet/".length());
            File snippet = new File(packFolder, resolved + ".json");
            if (!snippet.isFile()) {
                return; // Missing snippets are reported by the content-key machinery.
            }
            try {
                range = new JSONObject(Files.readString(snippet.toPath(), StandardCharsets.UTF_8));
                via = " (via snippet '" + resolved + "')";
            } catch (Throwable e) {
                return;
            }
        }
        if (range == null) {
            return;
        }

        boolean hasMin = range.has("min") && !range.isNull("min");
        boolean hasMax = range.has("max") && !range.isNull("max");
        if (!hasMin && !hasMax) {
            errors.add(path + via + " omits both min and max, which resolves to the shared IrisStyledRange default of "
                    + consequence + ". Set min and max explicitly, or " + removal + ".");
        } else if (!hasMin || !hasMax) {
            String missing = hasMin ? "max" : "min";
            String defaultValue = hasMin ? "32" : "16";
            warnings.add(path + via + " omits " + missing + ", which falls back to the shared IrisStyledRange default of "
                    + defaultValue + ". Set it explicitly if that is not intended.");
        }
    }

    private static String hostType(String folderName) {
        String singular = folderName.endsWith("s") ? folderName.substring(0, folderName.length() - 1) : folderName;
        return singular.substring(0, 1).toUpperCase(Locale.ROOT) + singular.substring(1);
    }
}
