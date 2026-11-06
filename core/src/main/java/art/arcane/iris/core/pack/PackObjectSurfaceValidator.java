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

package art.arcane.iris.core.pack;

import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class PackObjectSurfaceValidator {
    private PackObjectSurfaceValidator() {
    }

    /** The dimension, region and biome files that are not JSON objects, one blocking error each. */
    static List<String> validateHostFilesParse(File packFolder) {
        List<String> blockingErrors = new ArrayList<>();
        if (packFolder == null || !packFolder.isDirectory()) {
            return blockingErrors;
        }

        for (String folderName : PackValidator.STRUCTURE_HOST_FOLDERS) {
            File resourceFolder = new File(packFolder, folderName);
            if (!resourceFolder.isDirectory()) {
                continue;
            }
            List<File> resourceFiles = PackValidationIo.listJsonRecursive(resourceFolder);
            resourceFiles.sort(Comparator.comparing(File::getPath));
            String resourceType = structureHostType(folderName);
            for (File resourceFile : resourceFiles) {
                String resourceKey = PackValidationIo.deriveKey(resourceFolder, resourceFile);
                requireJsonObject(resourceFile, resourceType, resourceKey, blockingErrors);
            }
        }
        return blockingErrors;
    }

    private static void requireJsonObject(File file, String resourceType, String resourceKey, List<String> blockingErrors) {
        try {
            new JSONObject(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException unreadable) {
            String reason = unreadable.getMessage() == null || unreadable.getMessage().isBlank()
                    ? unreadable.getClass().getSimpleName()
                    : unreadable.getMessage();
            blockingErrors.add(resourceType + " '" + resourceKey + "' has invalid JSON: " + reason);
        }
    }

    private static String structureHostType(String folderName) {
        return switch (folderName) {
            case "dimensions" -> "Dimension";
            case "regions" -> "Region";
            case "biomes" -> "Biome";
            default -> "Resource";
        };
    }

    static List<String> validateObjectSurfaceSupport(File packFolder) {
        List<String> blockingErrors = new ArrayList<>();
        if (packFolder == null || !packFolder.isDirectory()) {
            return blockingErrors;
        }

        for (String folderName : PackValidator.STRUCTURE_HOST_FOLDERS) {
            File resourceFolder = new File(packFolder, folderName);
            if (!resourceFolder.isDirectory()) {
                continue;
            }
            List<File> resourceFiles = PackValidationIo.listJsonRecursive(resourceFolder);
            resourceFiles.sort(Comparator.comparing(File::getPath));
            String resourceType = structureHostType(folderName);
            for (File resourceFile : resourceFiles) {
                JSONObject resource = PackValidationIo.readJson(resourceFile);
                if (resource == null) {
                    continue;
                }
                String path = resourceType + " '" + PackValidationIo.deriveKey(resourceFolder, resourceFile) + "'";
                if ("dimensions".equals(folderName)) {
                    PackJsonFieldChecks.validateOptionalIntegerRange(
                            path, resource, "objectSurfaceSupportBuffer", 0, 16, blockingErrors);
                    PackJsonFieldChecks.validateOptionalBoolean(
                            path, resource, "requireObjectSurfaceSupport", blockingErrors);
                }
                validateObjectPlacementSurfaceSupport(path, resource.optJSONArray("objects"), blockingErrors);
            }
        }
        return blockingErrors;
    }

    private static void validateObjectPlacementSurfaceSupport(String path, JSONArray placements,
                                                              List<String> blockingErrors) {
        if (placements == null) {
            return;
        }
        for (int i = 0; i < placements.length(); i++) {
            JSONObject placement = placements.optJSONObject(i);
            if (placement == null) {
                continue;
            }
            String placementPath = path + ".objects[" + i + "]";
            PackJsonFieldChecks.validateOptionalIntegerRange(
                    placementPath, placement, "surfaceSupportBuffer", 0, 16, blockingErrors);
            PackJsonFieldChecks.validateOptionalIntegerRange(
                    placementPath, placement, "surfaceSupportDepth", 1, 16, blockingErrors);
            PackJsonFieldChecks.validateOptionalBoolean(
                    placementPath, placement, "requireSurfaceSupport", blockingErrors);
        }
    }
}
