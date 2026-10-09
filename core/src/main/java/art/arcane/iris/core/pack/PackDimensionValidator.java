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

import art.arcane.iris.engine.object.value.IrisRange;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.dimension.IrisDimensionType;
import art.arcane.iris.engine.object.world.IrisWorldBoundary;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

final class PackDimensionValidator {
    private PackDimensionValidator() {
    }

    static void validateDimensions(File packFolder, File[] dimensionFiles, List<String> blockingErrors, List<String> warnings) {
        File regionsFolder = new File(packFolder, "regions");
        File biomesFolder = new File(packFolder, "biomes");

        for (File dimFile : dimensionFiles) {
            String dimensionKey = PackValidationIo.stripExtension(dimFile.getName());
            JSONObject dimJson;
            try {
                dimJson = new JSONObject(Files.readString(dimFile.toPath(), StandardCharsets.UTF_8));
            } catch (Throwable e) {
                blockingErrors.add("Dimension '" + dimensionKey + "' has invalid JSON: " + e.getMessage());
                continue;
            }

            validateImportedStructurePolicy(dimensionKey, dimJson, blockingErrors, warnings);
            validateDimensionHeights(packFolder, dimensionKey, dimJson, blockingErrors);
            validateWorldBoundary(dimensionKey, dimJson, blockingErrors);

            JSONArray regionsArray = dimJson.optJSONArray("regions");
            if (regionsArray == null || regionsArray.length() == 0) {
                blockingErrors.add("Dimension '" + dimensionKey + "' declares no regions.");
                continue;
            }

            int resolvedRegions = 0;
            for (int i = 0; i < regionsArray.length(); i++) {
                String regionKey = regionsArray.optString(i, null);
                if (regionKey == null || regionKey.isBlank()) {
                    warnings.add("Dimension '" + dimensionKey + "' has a blank region entry at index " + i + ".");
                    continue;
                }
                File regionFile = new File(regionsFolder, regionKey + ".json");
                if (!regionFile.isFile()) {
                    blockingErrors.add("Dimension '" + dimensionKey + "' references missing region '" + regionKey + "'.");
                    continue;
                }

                JSONObject regionJson;
                try {
                    regionJson = new JSONObject(Files.readString(regionFile.toPath(), StandardCharsets.UTF_8));
                } catch (Throwable e) {
                    blockingErrors.add("Region '" + regionKey + "' has invalid JSON: " + e.getMessage());
                    continue;
                }

                int anyBiome = countBiomeRefs(regionJson, "landBiomes", biomesFolder, regionKey, warnings)
                        + countBiomeRefs(regionJson, "seaBiomes", biomesFolder, regionKey, warnings)
                        + countBiomeRefs(regionJson, "shoreBiomes", biomesFolder, regionKey, warnings)
                        + countBiomeRefs(regionJson, "caveBiomes", biomesFolder, regionKey, warnings);
                if (anyBiome == 0) {
                    blockingErrors.add("Region '" + regionKey + "' has no resolvable biomes.");
                }
                resolvedRegions++;
            }

            if (resolvedRegions == 0) {
                blockingErrors.add("Dimension '" + dimensionKey + "' has no resolvable regions.");
            }
        }
    }

    /** The dimension's {@code importedStructures} settings of the iris:structure module, which the platform's own structures follow. */
    static final String STRUCTURE_SETTINGS = "importedStructures";

    static void validateImportedStructurePolicy(String dimensionKey, JSONObject dimension,
                                                List<String> blockingErrors, List<String> warnings) {
        if (!dimension.has(STRUCTURE_SETTINGS)) {
            return;
        }
        if (dimension.isNull(STRUCTURE_SETTINGS)) {
            blockingErrors.add("Dimension '" + dimensionKey + "' importedStructures must be an object.");
            return;
        }
        JSONObject policy = dimension.optJSONObject(STRUCTURE_SETTINGS);
        if (policy == null) {
            blockingErrors.add("Dimension '" + dimensionKey + "' importedStructures must be an object.");
            return;
        }
        if (policy.has("mode")) {
            blockingErrors.add("Dimension '" + dimensionKey
                    + "' importedStructures.mode is not supported. Native structures are enabled by default; deny families in importedStructures.disabled or complete keys in importedStructures.disabledExact.");
        }
        if (policy.has("enabled")) {
            blockingErrors.add("Dimension '" + dimensionKey
                    + "' importedStructures.enabled is not supported. Native structures are enabled by default; deny families in importedStructures.disabled or complete keys in importedStructures.disabledExact.");
        }
        validateStructureKeyList(dimensionKey, policy, "disabled", blockingErrors);
        validateStructureKeyList(dimensionKey, policy, "disabledExact", blockingErrors);
        validateFrequencyOverrides(dimensionKey, policy, blockingErrors);
        JSONArray adjustments = policy.optJSONArray("adjustments");
        if (adjustments == null) {
            if (policy.has("adjustments")) {
                blockingErrors.add("Dimension '" + dimensionKey
                        + "' importedStructures.adjustments must be an array.");
            }
            return;
        }
        for (int index = 0; index < adjustments.length(); index++) {
            JSONObject adjustment = adjustments.optJSONObject(index);
            if (adjustment == null) {
                blockingErrors.add("Dimension '" + dimensionKey
                        + "' importedStructures.adjustments has a non-object entry at index " + index + ".");
                continue;
            }
            if (adjustment.has("clearVegetation")) {
                warnings.add("Dimension '" + dimensionKey + "' importedStructures.adjustments[" + index
                        + "].clearVegetation was removed and is ignored. Vegetation is always cleared inside structure piece envelopes.");
            }
            validateStructureKeyList(dimensionKey, adjustment, "match", blockingErrors);
            validateAdjustmentYBand(dimensionKey, adjustment, index, blockingErrors);
            PackStructurePlacementValidator.validateNativeTerrain("Dimension '" + dimensionKey
                    + "' importedStructures.adjustments[" + index + "]", adjustment, blockingErrors);
        }
    }

    private static void validateFrequencyOverrides(String dimensionKey, JSONObject policy,
                                                   List<String> blockingErrors) {
        if (!policy.has("frequencyOverrides")) {
            return;
        }
        JSONArray overrides = policy.optJSONArray("frequencyOverrides");
        if (overrides == null) {
            blockingErrors.add("Dimension '" + dimensionKey
                    + "' importedStructures.frequencyOverrides must be an array.");
            return;
        }
        for (int index = 0; index < overrides.length(); index++) {
            JSONObject override = overrides.optJSONObject(index);
            String path = "Dimension '" + dimensionKey
                    + "' importedStructures.frequencyOverrides[" + index + "]";
            if (override == null) {
                blockingErrors.add(path + " must be an object.");
                continue;
            }
            Object rawKey = override.opt("structureSet");
            if (!(rawKey instanceof String key)
                    || key.isBlank()
                    || !PackValidator.RESOURCE_KEY_PATTERN.matcher(key.trim()).matches()) {
                blockingErrors.add(path + ".structureSet must be a namespaced registry key.");
            }
            PackJsonFieldChecks.validateOptionalDoubleRange(
                    path, override, "multiplier", 0.01D, 16D, blockingErrors);
        }
    }

    private static void validateAdjustmentYBand(String dimensionKey, JSONObject adjustment, int index,
                                                List<String> blockingErrors) {
        if (!adjustment.has("yBand") || adjustment.opt("yBand") == JSONObject.NULL) {
            return;
        }
        String path = "Dimension '" + dimensionKey
                + "' importedStructures.adjustments[" + index + "].yBand";
        JSONObject band = adjustment.optJSONObject("yBand");
        if (band == null) {
            blockingErrors.add(path + " must be an object.");
            return;
        }
        PackJsonFieldChecks.validateOptionalIntegerRange(path, band, "min", -4064, 4064, blockingErrors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, band, "max", -4064, 4064, blockingErrors);
    }

    private static void validateStructureKeyList(String dimensionKey, JSONObject owner, String field,
                                                 List<String> blockingErrors) {
        if (!owner.has(field)) {
            return;
        }
        JSONArray keys = owner.optJSONArray(field);
        if (keys == null) {
            blockingErrors.add("Dimension '" + dimensionKey + "' structure policy field '"
                    + field + "' must be an array.");
            return;
        }
        for (int index = 0; index < keys.length(); index++) {
            Object value = keys.opt(index);
            if (!(value instanceof String key) || key.isBlank()) {
                blockingErrors.add("Dimension '" + dimensionKey + "' structure policy field '"
                        + field + "' has a blank or non-string entry at index " + index + ".");
            }
        }
    }

    private static int countBiomeRefs(JSONObject regionJson, String field, File biomesFolder, String regionKey, List<String> warnings) {
        JSONArray arr = regionJson.optJSONArray(field);
        if (arr == null) {
            return 0;
        }
        int resolved = 0;
        for (int i = 0; i < arr.length(); i++) {
            String biomeKey = arr.optString(i, null);
            if (biomeKey == null || biomeKey.isBlank()) {
                continue;
            }
            File biomeFile = new File(biomesFolder, biomeKey + ".json");
            if (!biomeFile.isFile()) {
                warnings.add("Region '" + regionKey + "' references missing biome '" + biomeKey + "' in " + field + ".");
                continue;
            }
            resolved++;
        }
        return resolved;
    }

    /**
     * Mirrors the IrisDimensionType constructor checks so a pack that would throw at world creation
     * fails validation instead. Defaults must match the POJOs exactly: dimensionHeight absent means
     * the IrisDimension field initializer (-64..320); present-but-partial means the IrisRange field
     * initializers (min 16, max 32); logicalHeight absent means 256.
     */
    static void validateDimensionHeights(File packFolder, String dimensionKey, JSONObject dimJson, List<String> blockingErrors) {
        JSONObject range = resolveDimensionHeight(packFolder, dimJson);
        if (range == null && dimJson.has("dimensionHeight") && !dimJson.isNull("dimensionHeight")) {
            if (!(dimJson.opt("dimensionHeight") instanceof String)) {
                blockingErrors.add("Dimension '" + dimensionKey + "' dimensionHeight must be an object or a range snippet reference.");
            }
            // Unresolvable snippet references are reported by the content-key machinery.
            return;
        }

        int minY;
        int maxY;
        if (range == null) {
            minY = -64;
            maxY = 320;
        } else {
            minY = (int) range.optDouble("min", 16D);
            maxY = (int) range.optDouble("max", 32D);
        }
        int height = maxY - minY;
        int logicalHeight = dimJson.optInt("logicalHeight", 256);

        if (height < IrisDimensionType.MIN_HEIGHT || height > IrisDimensionType.MAX_HEIGHT) {
            blockingErrors.add("Dimension '" + dimensionKey + "' dimensionHeight span (max - min) is " + height
                    + "; it must be between " + IrisDimensionType.MIN_HEIGHT + " and " + IrisDimensionType.MAX_HEIGHT + ".");
        } else if ((height & (IrisDimensionType.HEIGHT_STEP - 1)) != 0) {
            blockingErrors.add("Dimension '" + dimensionKey + "' dimensionHeight span (max - min) is " + height
                    + "; it must be a multiple of " + IrisDimensionType.HEIGHT_STEP + ".");
        }
        if (minY < IrisDimensionType.MIN_MIN_Y || minY > IrisDimensionType.MAX_MIN_Y) {
            blockingErrors.add("Dimension '" + dimensionKey + "' dimensionHeight.min is " + minY
                    + "; it must be between " + IrisDimensionType.MIN_MIN_Y + " and " + IrisDimensionType.MAX_MIN_Y + ".");
        } else if ((minY & (IrisDimensionType.HEIGHT_STEP - 1)) != 0) {
            blockingErrors.add("Dimension '" + dimensionKey + "' dimensionHeight.min is " + minY
                    + "; it must be a multiple of " + IrisDimensionType.HEIGHT_STEP + ".");
        }
        if (logicalHeight < 0) {
            blockingErrors.add("Dimension '" + dimensionKey + "' logicalHeight is " + logicalHeight + "; it cannot be negative.");
        } else if (logicalHeight > height) {
            blockingErrors.add("Dimension '" + dimensionKey + "' logicalHeight is " + logicalHeight
                    + "; it cannot be greater than the dimension height of " + height + ".");
        }
    }

    static void validateWorldBoundary(String dimensionKey, JSONObject dimension, List<String> blockingErrors) {
        if (!dimension.has("worldBoundary")) {
            return;
        }
        if (dimension.isNull("worldBoundary")) {
            blockingErrors.add("Dimension '" + dimensionKey + "' worldBoundary must be an object.");
            return;
        }
        JSONObject boundary = dimension.optJSONObject("worldBoundary");
        if (boundary == null) {
            blockingErrors.add("Dimension '" + dimensionKey + "' worldBoundary must be an object.");
            return;
        }

        String context = "Dimension '" + dimensionKey + "' worldBoundary";
        validateFiniteNumber(boundary, "size", 1D, IrisWorldBoundary.MAXIMUM_SIZE, context, blockingErrors);
        validateInteger(boundary, "warningDistance", 0, Integer.MAX_VALUE, context, blockingErrors);
        validateFiniteNumber(boundary, "damageBuffer", 0D, Double.MAX_VALUE, context, blockingErrors);
        validateFiniteNumber(boundary, "damageAmount", 0D, Double.MAX_VALUE, context, blockingErrors);

        if (!boundary.has("center")) {
            return;
        }
        if (boundary.isNull("center")) {
            blockingErrors.add(context + ".center must be an object.");
            return;
        }
        JSONObject center = boundary.optJSONObject("center");
        if (center == null) {
            blockingErrors.add(context + ".center must be an object.");
            return;
        }
        validateFiniteNumber(center, "x", -IrisWorldBoundary.MAXIMUM_CENTER,
                IrisWorldBoundary.MAXIMUM_CENTER, context + ".center", blockingErrors);
        validateFiniteNumber(center, "z", -IrisWorldBoundary.MAXIMUM_CENTER,
                IrisWorldBoundary.MAXIMUM_CENTER, context + ".center", blockingErrors);
    }

    private static void validateFiniteNumber(JSONObject owner, String field, double minimum, double maximum,
                                             String context, List<String> blockingErrors) {
        if (!owner.has(field)) {
            return;
        }
        Object raw = owner.opt(field);
        if (!(raw instanceof Number number)) {
            blockingErrors.add(context + "." + field + " must be a number.");
            return;
        }
        double value = number.doubleValue();
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            blockingErrors.add(context + "." + field + " must be finite and between "
                    + decimalLabel(minimum) + " and " + decimalLabel(maximum) + ".");
        }
    }

    private static void validateInteger(JSONObject owner, String field, int minimum, int maximum,
                                        String context, List<String> blockingErrors) {
        if (!owner.has(field)) {
            return;
        }
        Object raw = owner.opt(field);
        if (!(raw instanceof Number number)) {
            blockingErrors.add(context + "." + field + " must be an integer.");
            return;
        }
        double value = number.doubleValue();
        if (!Double.isFinite(value) || value != Math.rint(value) || value < minimum || value > maximum) {
            blockingErrors.add(context + "." + field + " must be an integer between "
                    + minimum + " and " + maximum + ".");
        }
    }

    private static String decimalLabel(double value) {
        if (Math.abs(value) <= Long.MAX_VALUE && value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    private static JSONObject resolveDimensionHeight(File packFolder, JSONObject dimJson) {
        if (!dimJson.has("dimensionHeight") || dimJson.isNull("dimensionHeight")) {
            return null;
        }

        JSONObject inline = dimJson.optJSONObject("dimensionHeight");
        if (inline != null) {
            return inline;
        }

        String reference = dimJson.optString("dimensionHeight", null);
        if (reference == null || !reference.startsWith("snippet/")) {
            return null;
        }
        // Mirror IrisData's snippet adapter: canonical snippet/range/... is used verbatim; any other
        // snippet/... reference is re-rooted under this field's snippet folder.
        if (!reference.startsWith("snippet/range/")) {
            reference = "snippet/range/" + reference.substring("snippet/".length());
        }
        File snippet = new File(packFolder, reference + ".json");
        if (!snippet.isFile()) {
            return null;
        }
        try {
            return new JSONObject(Files.readString(snippet.toPath(), StandardCharsets.UTF_8));
        } catch (Throwable e) {
            return null;
        }
    }
}
