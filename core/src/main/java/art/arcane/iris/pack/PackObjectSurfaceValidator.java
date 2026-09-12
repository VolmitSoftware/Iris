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

import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class PackObjectSurfaceValidator {
    private PackObjectSurfaceValidator() {
    }

    static Set<String> collectPlacedStructureKeys(File packFolder) {
        Set<String> structureKeys = new LinkedHashSet<>();
        if (packFolder == null || !packFolder.isDirectory()) {
            return structureKeys;
        }
        for (String folderName : PackValidator.STRUCTURE_HOST_FOLDERS) {
            File resourceFolder = new File(packFolder, folderName);
            if (!resourceFolder.isDirectory()) {
                continue;
            }
            for (File resourceFile : PackValidationIo.listJsonRecursive(resourceFolder)) {
                JSONObject resource = PackValidationIo.readJson(resourceFile);
                if (resource == null) {
                    continue;
                }
                JSONArray placements = resource.optJSONArray("structures");
                if (placements == null) {
                    continue;
                }
                for (int placementIndex = 0; placementIndex < placements.length(); placementIndex++) {
                    JSONObject placement = placements.optJSONObject(placementIndex);
                    if (placement == null) {
                        continue;
                    }
                    JSONArray references = placement.optJSONArray("structures");
                    if (references == null) {
                        continue;
                    }
                    for (int referenceIndex = 0; referenceIndex < references.length(); referenceIndex++) {
                        Object rawReference = references.opt(referenceIndex);
                        if (rawReference instanceof String structureKey && !structureKey.isBlank()) {
                            structureKeys.add(structureKey);
                        }
                    }
                }
            }
        }
        return Set.copyOf(structureKeys);
    }

    static List<String> validateUnsupportedStructureTransforms(File packFolder) {
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
            String resourceType = PackStructurePlacementValidator.structureHostType(folderName);
            for (File resourceFile : resourceFiles) {
                String resourceKey = PackValidationIo.deriveKey(resourceFolder, resourceFile);
                JSONObject resource = PackStructurePlacementValidator.readGraphJson(
                        resourceFile, resourceType, resourceKey, blockingErrors);
                if (resource == null) {
                    continue;
                }

                JSONArray placements = resource.optJSONArray("structures");
                if (placements == null) {
                    continue;
                }
                for (int placementIndex = 0; placementIndex < placements.length(); placementIndex++) {
                    JSONObject placement = placements.optJSONObject(placementIndex);
                    if (placement == null) {
                        continue;
                    }
                    for (String field : PackValidator.UNSUPPORTED_STRUCTURE_TRANSFORM_FIELDS) {
                        if (placement.has(field)) {
                            blockingErrors.add(resourceType + " '" + resourceKey + "' structures[" + placementIndex
                                    + "] declares unsupported field '" + field
                                    + "'. Structure placement transforms are not supported; remove the field.");
                        }
                    }
                }
            }
        }
        return blockingErrors;
    }

    static List<String> validateStructureGraph(File packFolder) {
        return validateStructureGraph(packFolder, true);
    }

    static List<String> validateStructureGraph(File packFolder, boolean validateLiveRegistries) {
        List<String> blockingErrors = new ArrayList<>();
        if (packFolder == null || !packFolder.isDirectory()) {
            return blockingErrors;
        }

        File structuresFolder = new File(packFolder, PackValidator.STRUCTURES_FOLDER);
        File poolsFolder = new File(packFolder, PackValidator.JIGSAW_POOLS_FOLDER);
        File piecesFolder = new File(packFolder, PackValidator.JIGSAW_PIECES_FOLDER);
        File objectsFolder = new File(packFolder, PackValidator.OBJECTS_FOLDER);
        Set<String> structureKeys = ContentKeyValidator.deriveRegistrantKeysExact(structuresFolder);
        Set<String> poolKeys = ContentKeyValidator.deriveRegistrantKeysExact(poolsFolder);
        Set<String> pieceKeys = ContentKeyValidator.deriveRegistrantKeysExact(piecesFolder);
        Set<String> objectKeys = ContentKeyValidator.deriveObjectKeysExact(objectsFolder);

        PackStructurePlacementValidator.validateStructurePlacements(
                packFolder, structureKeys, validateLiveRegistries, blockingErrors);
        PackStructurePlacementValidator.validateStructureStartPools(structuresFolder, poolKeys, blockingErrors);
        PackStructurePlacementValidator.validateJigsawPools(poolsFolder, poolKeys, pieceKeys, blockingErrors);
        PackStructurePlacementValidator.validateJigsawPieces(piecesFolder, poolKeys, objectKeys, blockingErrors);
        return blockingErrors;
    }

    static List<String> validateRemovedWorldgenFields(File packFolder) {
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
            String resourceType = PackStructurePlacementValidator.structureHostType(folderName);
            for (File resourceFile : resourceFiles) {
                JSONObject resource = PackValidationIo.readJson(resourceFile);
                if (resource == null) {
                    continue;
                }
                String resourceKey = PackValidationIo.deriveKey(resourceFolder, resourceFile);
                for (String field : PackValidator.REMOVED_WORLDGEN_FIELDS) {
                    if (resource.has(field)) {
                        blockingErrors.add(resourceType + " '" + resourceKey + "' declares removed field '"
                                + field + "'. Remove it because fluid-body generation is not supported.");
                    }
                }
            }
        }
        return blockingErrors;
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
            String resourceType = PackStructurePlacementValidator.structureHostType(folderName);
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
            if (placement.has("surfaceOpeningClearance")) {
                blockingErrors.add(placementPath + " declares removed field 'surfaceOpeningClearance'. "
                        + "Use surfaceSupportBuffer instead.");
            }
            PackJsonFieldChecks.validateOptionalIntegerRange(
                    placementPath, placement, "surfaceSupportBuffer", 0, 16, blockingErrors);
            PackJsonFieldChecks.validateOptionalIntegerRange(
                    placementPath, placement, "surfaceSupportDepth", 1, 16, blockingErrors);
            PackJsonFieldChecks.validateOptionalBoolean(
                    placementPath, placement, "requireSurfaceSupport", blockingErrors);
        }
    }
}
