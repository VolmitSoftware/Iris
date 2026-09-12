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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class PackBiomeLayerValidator {
    private PackBiomeLayerValidator() {
    }

    static List<String> validateLayers(File biomesFolder) {
        List<String> blockingErrors = new ArrayList<>();
        if (biomesFolder == null || !biomesFolder.isDirectory()) {
            return blockingErrors;
        }

        List<File> biomeFiles = PackValidationIo.listJsonRecursive(biomesFolder);
        biomeFiles.sort(Comparator.comparing(File::getPath));
        for (File biomeFile : biomeFiles) {
            String biomeKey = PackValidationIo.deriveKey(biomesFolder, biomeFile);
            JSONObject biome = PackValidationIo.readJson(biomeFile);
            if (biome == null) {
                continue;
            }

            validateLayerArray(biome, "layers", biomeKey, blockingErrors);
            validateLayerArray(biome, "caveCeilingLayers", biomeKey, blockingErrors);
        }
        return blockingErrors;
    }

    static List<String> validateDecoratorPalettes(File biomesFolder, File decoratorSnippetsFolder) {
        List<String> blockingErrors = new ArrayList<>();
        if (biomesFolder != null && biomesFolder.isDirectory()) {
            List<File> biomeFiles = PackValidationIo.listJsonRecursive(biomesFolder);
            biomeFiles.sort(Comparator.comparing(File::getPath));
            for (File biomeFile : biomeFiles) {
                String biomeKey = PackValidationIo.deriveKey(biomesFolder, biomeFile);
                JSONObject biome = PackValidationIo.readJson(biomeFile);
                if (biome == null || !biome.has("decorators") || biome.isNull("decorators")) {
                    continue;
                }
                JSONArray decorators = biome.optJSONArray("decorators");
                if (decorators == null) {
                    blockingErrors.add("Biome '" + biomeKey + "' decorators must be an array.");
                    continue;
                }
                validateDecoratorArray(decorators, "Biome '" + biomeKey + "' decorators", blockingErrors);
            }
        }

        if (decoratorSnippetsFolder != null && decoratorSnippetsFolder.isDirectory()) {
            List<File> snippetFiles = PackValidationIo.listJsonRecursive(decoratorSnippetsFolder);
            snippetFiles.sort(Comparator.comparing(File::getPath));
            for (File snippetFile : snippetFiles) {
                String snippetKey = PackValidationIo.deriveKey(decoratorSnippetsFolder, snippetFile);
                JSONObject snippet = PackValidationIo.readJson(snippetFile);
                if (snippet != null) {
                    validateDecoratorPalette(snippet, "Decorator snippet '" + snippetKey + "'", blockingErrors);
                }
            }
        }
        return blockingErrors;
    }

    private static void validateDecoratorArray(JSONArray decorators, String path, List<String> blockingErrors) {
        for (int index = 0; index < decorators.length(); index++) {
            Object rawDecorator = decorators.opt(index);
            if (rawDecorator instanceof String) {
                continue;
            }
            if (!(rawDecorator instanceof JSONObject decorator)) {
                blockingErrors.add(path + "[" + index + "] must be an object or snippet reference.");
                continue;
            }
            validateDecoratorPalette(decorator, path + "[" + index + "]", blockingErrors);
        }
    }

    private static void validateDecoratorPalette(JSONObject decorator, String path, List<String> blockingErrors) {
        JSONArray palette = decorator.optJSONArray("palette");
        if (palette == null || palette.length() == 0) {
            blockingErrors.add(path + " must declare a non-empty palette.");
        }
    }

    private static void validateLayerArray(JSONObject biome, String field, String biomeKey,
                                       List<String> blockingErrors) {
        if (!biome.has(field) || biome.isNull(field)) {
            return;
        }

        JSONArray array = biome.optJSONArray(field);
        if (array == null) {
            blockingErrors.add("Biome '" + biomeKey + "' " + field + " must be an array.");
        }
    }
}
