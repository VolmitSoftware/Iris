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
import com.volmit.world.Weighted;

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

            validateDimensionHeights(packFolder, dimensionKey, dimJson, blockingErrors);
            validateWorldBoundary(dimensionKey, dimJson, blockingErrors);

            JSONArray regionsArray = dimJson.optJSONArray("regions");
            if (regionsArray == null || regionsArray.length() == 0) {
                blockingErrors.add("Dimension '" + dimensionKey + "' declares no regions.");
                continue;
            }

            int resolvedRegions = 0;
            for (int i = 0; i < regionsArray.length(); i++) {
                String regionKey = PackValidationIo.entryKey(regionsArray, i, "region");
                validateWeight(regionsArray.optJSONObject(i), "Dimension '" + dimensionKey + "' regions[" + i + "]", blockingErrors);
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

                int anyBiome = countBiomeRefs(regionJson, "landBiomes", biomesFolder, regionKey, blockingErrors, warnings)
                        + countBiomeRefs(regionJson, "seaBiomes", biomesFolder, regionKey, blockingErrors, warnings)
                        + countBiomeRefs(regionJson, "shoreBiomes", biomesFolder, regionKey, blockingErrors, warnings)
                        + countBiomeRefs(regionJson, "caveBiomes", biomesFolder, regionKey, blockingErrors, warnings);
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

    private static void validateWeight(JSONObject entry, String path, List<String> blockingErrors) {
        if (entry == null) {
            return;
        }
        Integer weight = PackIntegerChecks.integerField(entry, "weight", Weighted.MIN_WEIGHT, path, blockingErrors);
        PackIntegerChecks.requireMinimum(path + ".weight", weight, Weighted.MIN_WEIGHT, blockingErrors);
        PackIntegerChecks.requireMaximum(path + ".weight", weight, Weighted.MAX_WEIGHT, blockingErrors);
    }

    private static int countBiomeRefs(JSONObject regionJson, String field, File biomesFolder, String regionKey,
                                      List<String> blockingErrors, List<String> warnings) {
        JSONArray arr = regionJson.optJSONArray(field);
        if (arr == null) {
            return 0;
        }
        int resolved = 0;
        for (int i = 0; i < arr.length(); i++) {
            String biomeKey = PackValidationIo.entryKey(arr, i, "biome");
            validateWeight(arr.optJSONObject(i), "Region '" + regionKey + "' " + field + "[" + i + "]", blockingErrors);
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
