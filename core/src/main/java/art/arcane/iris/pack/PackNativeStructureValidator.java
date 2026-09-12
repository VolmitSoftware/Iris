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

import art.arcane.iris.structure.object.ObjectPlaceMode;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PackNativeStructureValidator {
    private PackNativeStructureValidator() {
    }

    static List<String> validateNativeStructureReplacements(
            File packFolder,
            Set<String> replacementOutputStructures,
            Map<String, List<StructureGraphPackValidator.SampledVerticalEnvelope>> sampledVerticalEnvelopes
    ) {
        List<String> blockingErrors = new ArrayList<>();
        if (packFolder == null || !packFolder.isDirectory()) {
            return blockingErrors;
        }
        Set<String> viableStructures = replacementOutputStructures == null
                ? Set.of() : replacementOutputStructures;
        Map<String, List<StructureGraphPackValidator.SampledVerticalEnvelope>> verticalEnvelopes =
                sampledVerticalEnvelopes == null ? Map.of() : sampledVerticalEnvelopes;
        File structuresFolder = new File(packFolder, PackValidator.STRUCTURES_FOLDER);
        Map<String, JSONObject> structures = new HashMap<>();
        for (File structureFile : PackValidationIo.listJsonRecursive(structuresFolder)) {
            JSONObject structure = PackValidationIo.readJson(structureFile);
            if (structure != null) {
                structures.put(PackValidationIo.deriveKey(structuresFolder, structureFile), structure);
            }
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
                JSONArray placements = resource.optJSONArray("structures");
                if (placements == null) {
                    continue;
                }
                String resourceKey = PackValidationIo.deriveKey(resourceFolder, resourceFile);
                for (int placementIndex = 0; placementIndex < placements.length(); placementIndex++) {
                    JSONObject placement = placements.optJSONObject(placementIndex);
                    if (placement == null || !placement.has("nativeSuppression")) {
                        continue;
                    }
                    Object rawSuppression = placement.opt("nativeSuppression");
                    if (!(rawSuppression instanceof String suppression)) {
                        blockingErrors.add(resourceType + " '" + resourceKey + "' structures["
                                + placementIndex + "].nativeSuppression must be NONE or REPLACE_SOURCE.");
                        continue;
                    }
                    if ("NONE".equals(suppression)) {
                        continue;
                    }
                    if (!"REPLACE_SOURCE".equals(suppression)) {
                        blockingErrors.add(resourceType + " '" + resourceKey + "' structures["
                                + placementIndex + "].nativeSuppression has unsupported value '"
                                + suppression + "'. Use NONE or REPLACE_SOURCE.");
                        continue;
                    }
                    if (!PackValidator.DIMENSIONS_FOLDER.equals(folderName)) {
                        blockingErrors.add(resourceType + " '" + resourceKey + "' structures["
                                + placementIndex + "] requests REPLACE_SOURCE, but native replacement is only"
                                + " valid on dimension-level placements.");
                        continue;
                    }
                    JSONArray references = placement.optJSONArray("structures");
                    JSONArray nativeStructures = placement.optJSONArray("nativeStructures");
                    if (nativeStructures != null && nativeStructures.length() > 0) {
                        continue;
                    }
                    if (references == null || references.length() == 0) {
                        blockingErrors.add("Dimension '" + resourceKey + "' structures[" + placementIndex
                                + "] requests REPLACE_SOURCE without any structure backend.");
                        continue;
                    }
                    for (int referenceIndex = 0; referenceIndex < references.length(); referenceIndex++) {
                        Object rawReference = references.opt(referenceIndex);
                        if (!(rawReference instanceof String structureKey) || structureKey.isBlank()) {
                            blockingErrors.add("Dimension '" + resourceKey + "' structures[" + placementIndex
                                    + "].structures[" + referenceIndex
                                    + "] must name an Iris structure for REPLACE_SOURCE.");
                            continue;
                        }
                        JSONObject structure = structures.get(structureKey);
                        if (structure == null) {
                            blockingErrors.add("Dimension '" + resourceKey + "' structures[" + placementIndex
                                    + "] cannot REPLACE_SOURCE with missing or invalid structure '"
                                    + structureKey + "'.");
                            continue;
                        }
                        String vanillaSource = structure.optString("vanillaSource", "").trim();
                        if (!PackValidator.RESOURCE_KEY_PATTERN.matcher(vanillaSource).matches()) {
                            blockingErrors.add("Dimension '" + resourceKey + "' structures[" + placementIndex
                                    + "] requests REPLACE_SOURCE for structure '" + structureKey
                                    + "', but its vanillaSource is not a valid namespaced registry key.");
                        }
                        if (!viableStructures.contains(structureKey)) {
                            blockingErrors.add("Dimension '" + resourceKey + "' structures[" + placementIndex
                                    + "] requests REPLACE_SOURCE for structure '" + structureKey
                                    + "', but that structure is not runtime-viable. Native generation will not"
                                    + " be used as a fallback.");
                            continue;
                        }
                        validateReplacementVerticalEnvelope(
                                resourceKey,
                                resource,
                                placementIndex,
                                placement,
                                structureKey,
                                structure,
                                verticalEnvelopes.get(structureKey),
                                blockingErrors);
                    }
                }
            }
        }
        return blockingErrors;
    }

    private static void validateReplacementVerticalEnvelope(
            String dimensionKey,
            JSONObject dimension,
            int placementIndex,
            JSONObject placement,
            String structureKey,
            JSONObject structure,
            List<StructureGraphPackValidator.SampledVerticalEnvelope> sampledVerticalEnvelopes,
            List<String> blockingErrors
    ) {
        String context = "Dimension '" + dimensionKey + "' structures[" + placementIndex
                + "] REPLACE_SOURCE structure '" + structureKey + "'";
        if (sampledVerticalEnvelopes == null || sampledVerticalEnvelopes.isEmpty()) {
            blockingErrors.add(context + " has no sampled vertical envelope. Native generation will not"
                    + " be used as a fallback.");
            return;
        }

        DimensionVerticalBounds worldBounds = resolveDimensionVerticalBounds(dimension, context, blockingErrors);
        PlacementVerticalBounds placementBounds = resolvePlacementVerticalBounds(placement, context, blockingErrors);
        ObjectPlaceMode placeMode = resolvePlaceMode(structure, context, blockingErrors);
        if (worldBounds == null || placementBounds == null || placeMode == null) {
            return;
        }

        for (StructureGraphPackValidator.SampledVerticalEnvelope sampled : sampledVerticalEnvelopes) {
            boolean exactY = placementBounds.underground()
                    || sampled.pieceCount() > 1
                    || placeMode == ObjectPlaceMode.STRUCTURE_PIECE
                    || placeMode == ObjectPlaceMode.FLOATING;
            if (!exactY) {
                continue;
            }

            long minimumYOffset = sampled.minimumYOffset();
            long maximumYOffset = sampled.maximumYOffset();
            boolean surfaceAligned = !placementBounds.underground()
                    && sampled.pieceCount() > 1
                    && placeMode != ObjectPlaceMode.STRUCTURE_PIECE
                    && placeMode != ObjectPlaceMode.FLOATING;
            if (surfaceAligned) {
                maximumYOffset -= minimumYOffset;
                minimumYOffset = 0L;
            }

            boolean fitsConfiguredRange;
            if (placementBounds.underground()) {
                long minimumAnchor = Math.max(
                        Math.max(placementBounds.minimumY(), worldBounds.minimumY()),
                        worldBounds.minimumY() - minimumYOffset);
                long maximumAnchor = Math.min(
                        Math.min(placementBounds.maximumY(), worldBounds.maximumY()),
                        worldBounds.maximumY() - maximumYOffset);
                fitsConfiguredRange = minimumAnchor <= maximumAnchor;
            } else {
                long minimumTerrainY = Math.max(placementBounds.minimumY(), worldBounds.minimumY());
                long maximumTerrainY = Math.min(placementBounds.maximumY(), worldBounds.maximumY());
                fitsConfiguredRange = minimumTerrainY <= maximumTerrainY
                        && minimumTerrainY + minimumYOffset >= worldBounds.minimumY()
                        && maximumTerrainY + maximumYOffset <= worldBounds.maximumY();
            }
            if (fitsConfiguredRange) {
                continue;
            }

            String alignment = surfaceAligned ? "surface-aligned " : "";
            blockingErrors.add(context + " sampled seed " + sampled.seed() + " has an " + alignment
                    + "exact-Y piece envelope " + minimumYOffset + ".." + maximumYOffset
                    + " relative to its anchor, which cannot fit placement band "
                    + placementBounds.minimumY() + ".." + placementBounds.maximumY()
                    + " inside writable world " + worldBounds.minimumY() + ".." + worldBounds.maximumY()
                    + ". Native generation will not be used as a fallback.");
            return;
        }
    }

    private static DimensionVerticalBounds resolveDimensionVerticalBounds(
            JSONObject dimension,
            String context,
            List<String> blockingErrors
    ) {
        long dimensionMinimum = -64L;
        long dimensionMaximum = 320L;
        if (dimension.has("dimensionHeight")) {
            JSONObject dimensionHeight = dimension.optJSONObject("dimensionHeight");
            if (dimensionHeight == null) {
                blockingErrors.add(context + " cannot validate its vertical envelope because dimensionHeight"
                        + " must be an object.");
                return null;
            }
            Long configuredMinimum = integralJsonNumber(dimensionHeight, "min", 16L);
            Long configuredMaximum = integralJsonNumber(dimensionHeight, "max", 32L);
            if (configuredMinimum == null || configuredMaximum == null) {
                blockingErrors.add(context + " cannot validate its vertical envelope because dimensionHeight"
                        + " min and max must be finite integer values.");
                return null;
            }
            dimensionMinimum = configuredMinimum;
            dimensionMaximum = configuredMaximum;
        }

        long writableMinimum = dimensionMinimum + 1L;
        long writableMaximum = dimensionMaximum - 1L;
        if (writableMinimum > writableMaximum) {
            blockingErrors.add(context + " cannot validate its vertical envelope because dimensionHeight "
                    + dimensionMinimum + ".." + dimensionMaximum + " has no writable structure range.");
            return null;
        }
        return new DimensionVerticalBounds(writableMinimum, writableMaximum);
    }

    private static PlacementVerticalBounds resolvePlacementVerticalBounds(
            JSONObject placement,
            String context,
            List<String> blockingErrors
    ) {
        boolean underground = false;
        if (placement.has("underground")) {
            Object rawUnderground = placement.opt("underground");
            if (!(rawUnderground instanceof Boolean configuredUnderground)) {
                blockingErrors.add(context + " cannot validate its vertical envelope because underground"
                        + " must be true or false.");
                return null;
            }
            underground = configuredUnderground;
        }
        Long configuredMinimum = integralJsonNumber(placement, "minHeight", -2032L);
        Long configuredMaximum = integralJsonNumber(placement, "maxHeight", 2032L);
        if (configuredMinimum == null || configuredMaximum == null) {
            blockingErrors.add(context + " cannot validate its vertical envelope because minHeight and"
                    + " maxHeight must be finite integer values.");
            return null;
        }
        long minimumY = underground
                ? Math.min(configuredMinimum, configuredMaximum) : configuredMinimum;
        long maximumY = underground
                ? Math.max(configuredMinimum, configuredMaximum) : configuredMaximum;
        return new PlacementVerticalBounds(minimumY, maximumY, underground);
    }

    private static ObjectPlaceMode resolvePlaceMode(
            JSONObject structure,
            String context,
            List<String> blockingErrors
    ) {
        if (!structure.has("placeMode")) {
            return ObjectPlaceMode.STRUCTURE_PIECE;
        }
        Object rawPlaceMode = structure.opt("placeMode");
        if (!(rawPlaceMode instanceof String placeModeName)) {
            blockingErrors.add(context + " cannot validate its vertical envelope because placeMode must name"
                    + " an Iris object place mode.");
            return null;
        }
        try {
            return ObjectPlaceMode.valueOf(placeModeName);
        } catch (IllegalArgumentException exception) {
            blockingErrors.add(context + " cannot validate its vertical envelope because placeMode '"
                    + placeModeName + "' is not supported.");
            return null;
        }
    }

    private static Long integralJsonNumber(JSONObject owner, String field, long defaultValue) {
        if (!owner.has(field)) {
            return defaultValue;
        }
        Object rawValue = owner.opt(field);
        if (!(rawValue instanceof Number number)) {
            return null;
        }
        double value = number.doubleValue();
        if (!Double.isFinite(value) || value != Math.rint(value)
                || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            return null;
        }
        return (long) value;
    }

    private record DimensionVerticalBounds(long minimumY, long maximumY) {
    }

    private record PlacementVerticalBounds(long minimumY, long maximumY, boolean underground) {
    }
}
