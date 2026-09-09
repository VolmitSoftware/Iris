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

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformStructureHooks;
import art.arcane.iris.spi.PlatformStructureHooks.JigsawSourceMetadata;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class PackStructurePlacementValidator {
    private static final Set<String> TERRAIN_ENVELOPE_MODES = Set.of(
            "BORE", "FORCE_CARVE", "VACUUM", "FLATTEN", "ENCASE");

    private PackStructurePlacementValidator() {
    }

    static void validateStructurePlacements(File packFolder,
                                            Set<String> structureKeys,
                                            boolean validateLiveRegistries,
                                            List<String> blockingErrors) {
        RegistrySnapshot registries = registrySnapshot(validateLiveRegistries, blockingErrors);
        if (registries == null) {
            return;
        }
        Map<String, String> placementIds = new HashMap<>();
        Map<String, String> anonymousGridIdentities = new HashMap<>();
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
                String resourceKey = PackValidationIo.deriveKey(resourceFolder, resourceFile);
                if (PackValidator.DIMENSIONS_FOLDER.equals(folderName)) {
                    validateImportedStructureAdjustmentEnvelopes(
                            resourceKey, resource, registries, blockingErrors);
                }
                Object rawPlacements = resource.opt("structures");
                JSONArray placements = resource.optJSONArray("structures");
                if (placements == null) {
                    if (resource.has("structures") && rawPlacements != JSONObject.NULL) {
                        blockingErrors.add(resourceType + " '" + resourceKey + "'.structures must be an array.");
                    }
                    continue;
                }
                for (int placementIndex = 0; placementIndex < placements.length(); placementIndex++) {
                    JSONObject placement = placements.optJSONObject(placementIndex);
                    String placementPath = resourceType + " '" + resourceKey + "' structures["
                            + placementIndex + "]";
                    if (placement == null) {
                        blockingErrors.add(placementPath + " must be an object.");
                        continue;
                    }
                    validatePlacementConfiguration(placementPath, placement, blockingErrors);
                    validatePlacementIdentity(
                            placementPath, placement, placementIds, anonymousGridIdentities, blockingErrors);
                    JSONArray references = placement.optJSONArray("structures");
                    JSONArray nativeStructures = placement.optJSONArray("nativeStructures");
                    if (placement.has("structures") && placement.opt("structures") != JSONObject.NULL
                            && references == null) {
                        blockingErrors.add(placementPath + ".structures must be an array.");
                    }
                    if (placement.has("nativeStructures") && placement.opt("nativeStructures") != JSONObject.NULL
                            && nativeStructures == null) {
                        blockingErrors.add(placementPath + ".nativeStructures must be an array.");
                    }
                    boolean hasIrisStructures = references != null && references.length() > 0;
                    boolean hasNativeStructures = nativeStructures != null && nativeStructures.length() > 0;
                    if (hasIrisStructures == hasNativeStructures) {
                        blockingErrors.add(placementPath
                                + " must declare exactly one non-empty backend: structures or nativeStructures.");
                        continue;
                    }
                    if (hasNativeStructures) {
                        Object anchor = placement.opt("anchor");
                        if (anchor != null && anchor != JSONObject.NULL && !"LEGACY".equals(anchor)) {
                            blockingErrors.add(placementPath
                                    + ".anchor must be omitted, null, or LEGACY for nativeStructures.");
                        }
                        validateNativeStructures(
                                placementPath, nativeStructures,
                                registries.structures(), registries.jigsaws(),
                                registries.pools(), registries.jigsawMetadataResolver(),
                                registries.hooks(), blockingErrors);
                        continue;
                    }
                    validateEditableStructureTerrain(placementPath, placement, blockingErrors);
                    Set<String> editableStructureKeys = new HashSet<>();
                    for (int referenceIndex = 0; referenceIndex < references.length(); referenceIndex++) {
                        Object rawReference = references.opt(referenceIndex);
                        if (!(rawReference instanceof String structureKey) || structureKey.isBlank()) {
                            blockingErrors.add(placementPath + ".structures[" + referenceIndex
                                    + "] must name a non-blank Iris structure.");
                            continue;
                        }
                        String normalizedStructureKey = structureKey.trim().toLowerCase(Locale.ROOT);
                        if (!editableStructureKeys.add(normalizedStructureKey)) {
                            blockingErrors.add(placementPath + ".structures[" + referenceIndex
                                    + "] duplicates Iris structure '" + structureKey + "'.");
                            continue;
                        }
                        if (!structureKeys.contains(structureKey)) {
                            blockingErrors.add(resourceType + " '" + resourceKey + "' structures["
                                    + placementIndex + "].structures[" + referenceIndex
                                    + "] references missing structure '" + structureKey + "'.");
                        }
                    }
                }
            }
        }
    }

    private static RegistrySnapshot registrySnapshot(boolean validateLiveRegistries,
                                                     List<String> blockingErrors) {
        if (!validateLiveRegistries || !IrisPlatforms.isBound()) {
            return new RegistrySnapshot(Set.of(), Set.of(), Set.of(), null, null);
        }
        try {
            PlatformStructureHooks hooks = IrisPlatforms.get().structureHooks();
            if (hooks == null) {
                throw new IllegalStateException("The active platform did not provide structure registry hooks");
            }
            Set<String> structures = normalizeRegistryKeys(
                    hooks.structureKeys(), "structure");
            Set<String> jigsaws = normalizeRegistryKeys(
                    hooks.jigsawStructureKeys(), "jigsaw structure");
            Set<String> pools = normalizeRegistryKeys(
                    hooks.templatePoolKeys(), "template pool");
            if (structures.isEmpty()) {
                throw new IllegalStateException("The active structure registry is empty");
            }
            if (jigsaws.isEmpty()) {
                throw new IllegalStateException("The active jigsaw structure registry is empty");
            }
            if (pools.isEmpty()) {
                throw new IllegalStateException("The active template pool registry is empty");
            }
            return new RegistrySnapshot(
                    structures, jigsaws, pools, new JigsawMetadataResolver(hooks), hooks);
        } catch (RuntimeException | LinkageError e) {
            IrisLogging.reportError("Could not read the live structure registries during pack validation", e);
            blockingErrors.add("Could not validate native structure references against the live registries: "
                    + failureMessage(e) + ".");
            return null;
        }
    }

    private static Set<String> normalizeRegistryKeys(List<String> registered, String registryName) {
        if (registered == null) {
            throw new IllegalStateException("The active " + registryName + " registry returned null");
        }
        Set<String> keys = new HashSet<>();
        for (String key : registered) {
            if (key != null && !key.isBlank()) {
                keys.add(key.toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(keys);
    }

    private static String failureMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private static void validatePlacementConfiguration(String path, JSONObject placement,
                                                       List<String> blockingErrors) {
        PackJsonFieldChecks.validateOptionalEnum(path, placement, "distribution",
                Set.of("RANDOM_SPREAD", "DENSITY", "CONCENTRIC_RINGS"), blockingErrors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, placement, "spacing", 1, 4096, blockingErrors);
        PackJsonFieldChecks.validateOptionalIntegerRange(
                path, placement, "separation", 0, Integer.MAX_VALUE, blockingErrors);
        PackJsonFieldChecks.validateOptionalIntegerRange(
                path, placement, "salt", Integer.MIN_VALUE, Integer.MAX_VALUE, blockingErrors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, placement, "density", 0D, 1D, blockingErrors);
        PackJsonFieldChecks.validateOptionalIntegerRange(
                path, placement, "ringCount", 1, Integer.MAX_VALUE, blockingErrors);
        PackJsonFieldChecks.validateOptionalIntegerRange(
                path, placement, "ringDistance", 1, Integer.MAX_VALUE, blockingErrors);
        PackJsonFieldChecks.validateOptionalIntegerRange(
                path, placement, "ringSpread", 1, Integer.MAX_VALUE, blockingErrors);
        PackJsonFieldChecks.validateOptionalIntegerRange(
                path, placement, "minHeight", Integer.MIN_VALUE, Integer.MAX_VALUE, blockingErrors);
        PackJsonFieldChecks.validateOptionalIntegerRange(
                path, placement, "maxHeight", Integer.MIN_VALUE, Integer.MAX_VALUE, blockingErrors);
        PackJsonFieldChecks.validateOptionalBoolean(path, placement, "underground", blockingErrors);
        PackJsonFieldChecks.validateOptionalBoolean(path, placement, "underwater", blockingErrors);
        PackJsonFieldChecks.validateOptionalEnum(path, placement, "nativeSuppression",
                Set.of("NONE", "REPLACE_SOURCE"), blockingErrors);

        Integer spacing = integerValue(placement, "spacing", 32);
        Integer separation = integerValue(placement, "separation", 8);
        if (spacing != null && separation != null && separation >= spacing) {
            blockingErrors.add(path + ".separation must be smaller than spacing.");
        }
        Integer minimumHeight = integerValue(placement, "minHeight", -2032);
        Integer maximumHeight = integerValue(placement, "maxHeight", 2032);
        if (minimumHeight != null && maximumHeight != null && minimumHeight > maximumHeight) {
            blockingErrors.add(path + " has an inverted height band: minHeight must not exceed maxHeight.");
        }
        Integer ringCount = integerValue(placement, "ringCount", 128);
        Integer ringDistance = integerValue(placement, "ringDistance", 32);
        Integer ringSpread = integerValue(placement, "ringSpread", 3);
        if (ringCount != null && ringDistance != null && ringSpread != null
                && ringCount > 0 && ringDistance > 0 && ringSpread > 0) {
            long ringRadius = (long) Math.ceilDiv(ringCount, ringSpread) * ringDistance;
            if (ringRadius > Integer.MAX_VALUE) {
                blockingErrors.add(path + " concentric ring radius exceeds the supported chunk coordinate range.");
            }
        }

        if (placement.has("placementId")) {
            Object rawPlacementId = placement.opt("placementId");
            if (!(rawPlacementId instanceof String placementId)) {
                blockingErrors.add(path + ".placementId must be a string.");
            } else if (!placementId.isEmpty() && placementId.isBlank()) {
                blockingErrors.add(path + ".placementId must be omitted or non-blank.");
            } else if (!placementId.equals(placementId.trim())) {
                blockingErrors.add(path + ".placementId must not contain leading or trailing whitespace.");
            }
        }
        validateStilt(path, placement, blockingErrors);
        validateNativeTerrain(path, placement, blockingErrors);
    }

    private static void validatePlacementIdentity(String path, JSONObject placement,
                                                  Map<String, String> placementIds,
                                                  Map<String, String> anonymousGridIdentities,
                                                  List<String> blockingErrors) {
        Object rawPlacementId = placement.opt("placementId");
        if (rawPlacementId instanceof String placementId && !placementId.isBlank()) {
            String normalizedId = placementId.trim();
            String existing = placementIds.putIfAbsent(normalizedId, path);
            if (existing != null) {
                blockingErrors.add(path + ".placementId duplicates '" + normalizedId
                        + "' already declared by " + existing + ".");
            }
            return;
        }
        String gridIdentity = anonymousGridIdentity(placement);
        String existing = anonymousGridIdentities.putIfAbsent(gridIdentity, path);
        if (existing != null) {
            blockingErrors.add(path + " duplicates an anonymous placement grid already declared by "
                    + existing + "; give distinct placements unique placementId values.");
        }
    }

    private static String anonymousGridIdentity(JSONObject placement) {
        StringBuilder identity = new StringBuilder();
        appendIdentity(identity, placement.optString("distribution", "RANDOM_SPREAD"));
        appendIdentity(identity, integerValue(placement, "salt", 165745296));
        appendIdentity(identity, integerValue(placement, "spacing", 32));
        appendIdentity(identity, integerValue(placement, "separation", 8));
        Object rawDensity = placement.has("density") ? placement.opt("density") : 0.02D;
        appendIdentity(identity, rawDensity instanceof Number number
                ? Double.doubleToLongBits(number.doubleValue()) : rawDensity);
        appendIdentity(identity, integerValue(placement, "ringCount", 128));
        appendIdentity(identity, integerValue(placement, "ringDistance", 32));
        appendIdentity(identity, integerValue(placement, "ringSpread", 3));
        appendIdentity(identity, integerValue(placement, "minHeight", -2032));
        appendIdentity(identity, integerValue(placement, "maxHeight", 2032));
        appendIdentity(identity, placement.opt("underground") instanceof Boolean underground && underground);
        appendIdentity(identity, placement.opt("underwater") instanceof Boolean underwater && underwater);
        JSONArray structures = placement.optJSONArray("structures");
        JSONArray nativeStructures = placement.optJSONArray("nativeStructures");
        if (structures != null && structures.length() > 0) {
            appendIdentity(identity, "iris");
            List<String> orderedStructures = new ArrayList<>(structures.length());
            for (int index = 0; index < structures.length(); index++) {
                orderedStructures.add(String.valueOf(structures.opt(index)));
            }
            orderedStructures.sort(String::compareTo);
            for (String structure : orderedStructures) {
                appendIdentity(identity, structure);
            }
        } else {
            appendIdentity(identity, "native");
            if (nativeStructures != null) {
                List<String> orderedSources = new ArrayList<>(nativeStructures.length());
                for (int index = 0; index < nativeStructures.length(); index++) {
                    JSONObject source = nativeStructures.optJSONObject(index);
                    String sourceKey = source == null ? "null" : source.optString("structure", "");
                    Integer weight = source == null ? null : integerValue(source, "weight", 1);
                    orderedSources.add(sourceKey.length() + ":" + sourceKey + ":" + weight);
                }
                orderedSources.sort(String::compareTo);
                for (String source : orderedSources) {
                    appendIdentity(identity, source);
                }
            }
        }
        return identity.toString();
    }

    private static void appendIdentity(StringBuilder identity, Object value) {
        String text = String.valueOf(value);
        identity.append(text.length()).append(':').append(text).append('|');
    }

    private static void validateStilt(String path, JSONObject placement, List<String> blockingErrors) {
        if (!placement.has("stilt") || placement.opt("stilt") == JSONObject.NULL) {
            return;
        }
        JSONObject stilt = placement.optJSONObject("stilt");
        if (stilt == null) {
            blockingErrors.add(path + ".stilt must be an object.");
            return;
        }
        PackJsonFieldChecks.validateOptionalIntegerRange(
                path + ".stilt", stilt, "maxDepth", 1, 4064, blockingErrors);
        PackJsonFieldChecks.validateOptionalIntegerRange(
                path + ".stilt", stilt, "spacing", 1, 64, blockingErrors);
        PackJsonFieldChecks.validateOptionalBoolean(
                path + ".stilt", stilt, "supportNonOccluding", blockingErrors);
        if (stilt.has("palette") && stilt.opt("palette") != JSONObject.NULL
                && stilt.optJSONObject("palette") == null) {
            blockingErrors.add(path + ".stilt.palette must be an object.");
        }
    }

    private static Integer integerValue(JSONObject object, String field, int defaultValue) {
        if (!object.has(field)) {
            return defaultValue;
        }
        Object rawValue = object.opt(field);
        if (!(rawValue instanceof Number number)) {
            return null;
        }
        double doubleValue = number.doubleValue();
        if (!Double.isFinite(doubleValue) || Math.rint(doubleValue) != doubleValue
                || doubleValue < Integer.MIN_VALUE || doubleValue > Integer.MAX_VALUE) {
            return null;
        }
        return number.intValue();
    }

    private static void validateNativeStructures(String placementPath,
                                                 JSONArray nativeStructures,
                                                 Set<String> registeredStructures,
                                                 Set<String> registeredJigsaws,
                                                 Set<String> registeredPools,
                                                 JigsawMetadataResolver jigsawMetadataResolver,
                                                 PlatformStructureHooks hooks,
                                                 List<String> blockingErrors) {
        Set<String> sourceKeys = new HashSet<>();
        long totalWeight = 0L;
        for (int sourceIndex = 0; sourceIndex < nativeStructures.length(); sourceIndex++) {
            String sourcePath = placementPath + ".nativeStructures[" + sourceIndex + "]";
            JSONObject source = nativeStructures.optJSONObject(sourceIndex);
            if (source == null) {
                blockingErrors.add(sourcePath + " must be an object.");
                continue;
            }
            String structureKey = source.optString("structure", "").trim();
            if (!PackValidator.RESOURCE_KEY_PATTERN.matcher(structureKey).matches()) {
                blockingErrors.add(sourcePath + ".structure must be a namespaced registry key.");
            } else if (!registeredStructures.isEmpty()
                    && !registeredStructures.contains(structureKey.toLowerCase(Locale.ROOT))) {
                blockingErrors.add(sourcePath + ".structure '" + structureKey
                        + "' is not a registered structure.");
            }
            if (!structureKey.isEmpty() && !sourceKeys.add(structureKey.toLowerCase(Locale.ROOT))) {
                blockingErrors.add(sourcePath + ".structure duplicates native source '" + structureKey + "'.");
            }
            Integer weight = PackLootValidator.lootInteger(source, "weight", 1, sourcePath, blockingErrors);
            PackLootValidator.requireMinimum(sourcePath + ".weight", weight, 1, blockingErrors);
            if (weight != null && weight > 0) {
                totalWeight += weight;
            }
            String normalizedStructureKey = structureKey.toLowerCase(Locale.ROOT);
            boolean registeredJigsaw = registeredJigsaws.contains(normalizedStructureKey);
            JSONObject jigsaw = source.optJSONObject("jigsaw");
            if (source.has("jigsaw") && source.opt("jigsaw") != JSONObject.NULL && jigsaw == null) {
                blockingErrors.add(sourcePath + ".jigsaw must be an object.");
            } else if (jigsaw != null) {
                if (!registeredJigsaws.isEmpty() && !registeredJigsaw) {
                    blockingErrors.add(sourcePath
                            + ".jigsaw requires a registered jigsaw structure.");
                }
                validateJigsawAssembly(
                        sourcePath + ".jigsaw", jigsaw, registeredPools, blockingErrors);
            }
            JigsawSourceMetadata sourceMetadata = registeredJigsaw
                    ? resolveJigsawMetadata(sourcePath, normalizedStructureKey,
                    jigsawMetadataResolver, blockingErrors)
                    : null;
            if (registeredJigsaw && sourceMetadata == null) {
                continue;
            }
            if (registeredJigsaw || jigsaw != null) {
                validateReferenceEnvelope(
                        sourcePath, normalizedStructureKey, jigsaw,
                        sourceMetadata, hooks, blockingErrors);
            }
        }
        if (totalWeight > Integer.MAX_VALUE) {
            blockingErrors.add(placementPath + ".nativeStructures total weight exceeds "
                    + Integer.MAX_VALUE + ".");
        }
    }

    private static void validateReferenceEnvelope(String sourcePath, String structureKey,
                                                  JSONObject jigsaw, JigsawSourceMetadata sourceMetadata,
                                                  PlatformStructureHooks hooks,
                                                  List<String> blockingErrors) {
        boolean overriddenDistance = jigsaw != null && jigsaw.has("maxDistanceHorizontal");
        Integer maximumDistance = overriddenDistance
                ? integerValue(jigsaw, "maxDistanceHorizontal", -1)
                : sourceMetadata == null ? null : sourceMetadata.maxDistanceHorizontal();
        if (maximumDistance == null || maximumDistance < 0) {
            return;
        }
        Integer startElementSpan = effectiveStartElementSpan(
                sourcePath, structureKey, jigsaw, sourceMetadata, hooks, blockingErrors);
        if (startElementSpan == null) {
            return;
        }
        long assemblySpan = Math.max(maximumDistance, startElementSpan);
        if (assemblySpan > 128L) {
            String distancePath = overriddenDistance
                    ? ".jigsaw.maxDistanceHorizontal" : " registered jigsaw source max-distance";
            blockingErrors.add(sourcePath + distancePath + " and its actual structure content"
                    + " (" + startElementSpan + "-block maximum start element and "
                    + maximumDistance + "-block maximum assembly distance) must not exceed Minecraft's"
                    + " 128-block (8-chunk) structure reference range.");
        }
    }

    private static Integer effectiveStartElementSpan(String sourcePath, String structureKey,
                                                     JSONObject jigsaw,
                                                     JigsawSourceMetadata sourceMetadata,
                                                     PlatformStructureHooks hooks,
                                                     List<String> blockingErrors) {
        int sourceSpan = sourceMetadata == null ? 0 : sourceMetadata.maxStartElementHorizontalSpan();
        if (jigsaw == null || !jigsaw.has("startPool")) {
            return sourceSpan;
        }
        String startPool = jigsaw.optString("startPool", "").trim();
        if (startPool.isEmpty() || hooks == null) {
            return sourceSpan;
        }
        try {
            int resolvedSpan = hooks.jigsawStartPoolHorizontalSpan(structureKey, startPool);
            if (resolvedSpan < 0) {
                throw new IllegalStateException("negative horizontal span " + resolvedSpan);
            }
            return resolvedSpan;
        } catch (RuntimeException | LinkageError error) {
            IrisLogging.reportError("Could not resolve the effective native jigsaw start-pool span", error);
            blockingErrors.add(sourcePath + ".jigsaw.startPool '" + startPool
                    + "' could not resolve a bounded live horizontal span: "
                    + failureMessage(error) + ".");
            return null;
        }
    }

    private static void validateImportedStructureAdjustmentEnvelopes(
            String dimensionKey, JSONObject dimension, RegistrySnapshot registries,
            List<String> blockingErrors) {
        JSONObject policy = dimension.optJSONObject("importedStructures");
        if (policy == null) {
            return;
        }
        JSONArray adjustments = policy.optJSONArray("adjustments");
        if (adjustments == null) {
            return;
        }
        List<String> orderedStructureKeys = new ArrayList<>(registries.structures());
        orderedStructureKeys.sort(String::compareTo);
        Map<String, EffectiveTerrainAdjustment> effective = new HashMap<>();
        for (int adjustmentIndex = 0; adjustmentIndex < adjustments.length(); adjustmentIndex++) {
            JSONObject adjustment = adjustments.optJSONObject(adjustmentIndex);
            if (adjustment == null || adjustment.optJSONObject("terrain") == null) {
                continue;
            }
            JSONArray matches = adjustment.optJSONArray("match");
            if (matches == null || matches.length() == 0) {
                continue;
            }
            String path = "Dimension '" + dimensionKey
                    + "' importedStructures.adjustments[" + adjustmentIndex + "]";
            for (String structureKey : orderedStructureKeys) {
                if (matchesStructure(matches, structureKey)) {
                    effective.put(structureKey, new EffectiveTerrainAdjustment(path, adjustment));
                }
            }
        }
        for (String structureKey : orderedStructureKeys) {
            EffectiveTerrainAdjustment adjustment = effective.get(structureKey);
            if (adjustment == null) {
                continue;
            }
            JSONObject terrain = adjustment.adjustment().optJSONObject("terrain");
            if (terrain == null || !TERRAIN_ENVELOPE_MODES.contains(
                    terrain.optString("mode", "SOURCE").toUpperCase(Locale.ROOT))) {
                continue;
            }
            String sourcePath = adjustment.path() + " matched registered structure '"
                    + structureKey + "'";
            if (registries.jigsaws().contains(structureKey)) {
                JigsawSourceMetadata metadata = resolveJigsawMetadata(
                        sourcePath, structureKey,
                        registries.jigsawMetadataResolver(), blockingErrors);
                if (metadata == null) {
                    continue;
                }
                validateReferenceEnvelope(
                        sourcePath, structureKey, null, metadata,
                        registries.hooks(), blockingErrors);
            }
        }
    }

    private static boolean matchesStructure(JSONArray matches, String structureKey) {
        for (int matchIndex = 0; matchIndex < matches.length(); matchIndex++) {
            Object rawPattern = matches.opt(matchIndex);
            if (rawPattern instanceof String pattern
                    && matchesStructurePattern(pattern, structureKey)) {
                return true;
            }
        }
        return false;
    }

    private static JigsawSourceMetadata resolveJigsawMetadata(
            String sourcePath, String structureKey,
            JigsawMetadataResolver resolver, List<String> blockingErrors) {
        if (resolver == null) {
            return null;
        }
        MetadataResolution resolution = resolver.resolve(structureKey);
        if (resolution.metadata() != null) {
            return resolution.metadata();
        }
        blockingErrors.add(sourcePath + " could not resolve live metadata for registered jigsaw source '"
                + structureKey + "': " + resolution.failure() + ".");
        return null;
    }

    private static boolean matchesStructurePattern(String pattern, String structureKey) {
        String normalizedPattern = pattern == null ? "" : pattern.trim().toLowerCase(Locale.ROOT);
        String normalizedKey = structureKey == null ? "" : structureKey.trim().toLowerCase(Locale.ROOT);
        if (normalizedPattern.isEmpty() || !normalizedKey.startsWith(normalizedPattern)) {
            return false;
        }
        if (normalizedKey.length() == normalizedPattern.length()) {
            return true;
        }
        char patternEnd = normalizedPattern.charAt(normalizedPattern.length() - 1);
        if (patternEnd == ':' || patternEnd == '/' || patternEnd == '_') {
            return true;
        }
        char boundary = normalizedKey.charAt(normalizedPattern.length());
        return boundary == '/' || boundary == '_';
    }

    private static void validateJigsawAssembly(String path, JSONObject assembly,
                                               Set<String> registeredPools,
                                               List<String> blockingErrors) {
        PackJsonFieldChecks.validateOptionalResourceKey(path, assembly, "startPool", false, blockingErrors);
        String startPool = assembly.optString("startPool", "").trim();
        if (!startPool.isEmpty() && !registeredPools.isEmpty()
                && !registeredPools.contains(startPool.toLowerCase(Locale.ROOT))) {
            blockingErrors.add(path + ".startPool '" + startPool
                    + "' is not a registered template pool.");
        }
        PackJsonFieldChecks.validateOptionalResourceKey(path, assembly, "startJigsawName", true, blockingErrors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, assembly, "maxDepth", 0, 20, blockingErrors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, assembly, "maxDistanceHorizontal", 1, 128, blockingErrors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, assembly, "maxDistanceVertical", 1, 4064, blockingErrors);
        PackJsonFieldChecks.validateOptionalIntegerRange(
                path, assembly, "dimensionPaddingBottom", 0, Integer.MAX_VALUE, blockingErrors);
        PackJsonFieldChecks.validateOptionalIntegerRange(
                path, assembly, "dimensionPaddingTop", 0, Integer.MAX_VALUE, blockingErrors);
        if (assembly.has("useExpansionHack")
                && !(assembly.opt("useExpansionHack") instanceof Boolean)) {
            blockingErrors.add(path + ".useExpansionHack must be a boolean.");
        }
        PackJsonFieldChecks.validateOptionalEnum(path, assembly, "projectStartToHeightmap",
                Set.of("SOURCE", "NONE", "WORLD_SURFACE_WG", "WORLD_SURFACE",
                        "OCEAN_FLOOR_WG", "OCEAN_FLOOR", "MOTION_BLOCKING",
                        "MOTION_BLOCKING_NO_LEAVES"), blockingErrors);
        PackJsonFieldChecks.validateOptionalEnum(path, assembly, "liquidSettings",
                Set.of("SOURCE", "IGNORE_WATERLOGGING", "APPLY_WATERLOGGING"), blockingErrors);
    }

    static void validateNativeTerrain(String path, JSONObject placement,
                                      List<String> blockingErrors) {
        JSONObject terrain = placement.optJSONObject("terrain");
        if (placement.has("terrain") && placement.opt("terrain") != JSONObject.NULL && terrain == null) {
            blockingErrors.add(path + ".terrain must be an object.");
            return;
        }
        if (terrain == null) {
            return;
        }
        PackJsonFieldChecks.validateOptionalEnum(path + ".terrain", terrain, "mode",
                Set.of("SOURCE", "PRESERVE", "BORE", "FORCE_CARVE", "VACUUM", "FLATTEN", "ENCASE"), blockingErrors);
        PackJsonFieldChecks.validateOptionalEnum(path + ".terrain", terrain, "shape",
                Set.of("BOX", "ROUNDED", "ERODED"), blockingErrors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path + ".terrain", terrain,
                "horizontalPadding", 0, 128, blockingErrors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path + ".terrain", terrain,
                "flattenRange", 0, 128, blockingErrors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path + ".terrain", terrain,
                "ceilingPadding", 0, 128, blockingErrors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path + ".terrain", terrain,
                "floorPadding", 0, 64, blockingErrors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path + ".terrain", terrain,
                "erosionStrength", 0D, 1D, blockingErrors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path + ".terrain", terrain,
                "erosionFrequency", 0.001D, 1D, blockingErrors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path + ".terrain", terrain,
                "lobeFrequency", 0D, 1D, blockingErrors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path + ".terrain", terrain,
                "lobeStrength", 0D, 1D, blockingErrors);
        if (terrain.has("encasePalette") && terrain.opt("encasePalette") != JSONObject.NULL
                && terrain.optJSONObject("encasePalette") == null) {
            blockingErrors.add(path + ".terrain.encasePalette must be an object.");
        }
    }

    private static void validateEditableStructureTerrain(
            String path, JSONObject placement, List<String> blockingErrors) {
        JSONObject terrain = placement.optJSONObject("terrain");
        if (terrain == null) {
            return;
        }
        String mode = terrain.optString("mode", "SOURCE");
        if ("VACUUM".equals(mode) || "FLATTEN".equals(mode) || "ENCASE".equals(mode)) {
            blockingErrors.add(path + ".terrain.mode " + mode
                    + " cannot target editable Iris structures; use nativeStructures or "
                    + "importedStructures.adjustments for native terrain preparation.");
        }
    }

    static void validateStructureStartPools(File structuresFolder,
                                            Set<String> poolKeys,
                                            List<String> blockingErrors) {
        if (!structuresFolder.isDirectory()) {
            return;
        }
        List<File> structureFiles = PackValidationIo.listJsonRecursive(structuresFolder);
        structureFiles.sort(Comparator.comparing(File::getPath));
        for (File structureFile : structureFiles) {
            String structureKey = PackValidationIo.deriveKey(structuresFolder, structureFile);
            JSONObject structure = readGraphJson(structureFile, "Structure", structureKey, blockingErrors);
            if (structure == null || isLegacyStructureIndex(structureKey, structure)) {
                continue;
            }
            String startPool = structure.optString("startPool", "").trim();
            if (startPool.isEmpty()) {
                blockingErrors.add("Structure '" + structureKey + "' does not declare a startPool.");
            } else if (!poolKeys.contains(startPool)) {
                blockingErrors.add("Structure '" + structureKey + "' references missing start pool '"
                        + startPool + "'.");
            }
        }
    }

    static void validateJigsawPools(File poolsFolder,
                                    Set<String> poolKeys,
                                    Set<String> pieceKeys,
                                    List<String> blockingErrors) {
        if (!poolsFolder.isDirectory()) {
            return;
        }
        List<File> poolFiles = PackValidationIo.listJsonRecursive(poolsFolder);
        poolFiles.sort(Comparator.comparing(File::getPath));
        for (File poolFile : poolFiles) {
            String poolKey = PackValidationIo.deriveKey(poolsFolder, poolFile);
            JSONObject pool = readGraphJson(poolFile, "Jigsaw pool", poolKey, blockingErrors);
            if (pool == null) {
                continue;
            }
            JSONArray entries = pool.optJSONArray("pieces");
            if (entries != null) {
                for (int entryIndex = 0; entryIndex < entries.length(); entryIndex++) {
                    JSONObject entry = entries.optJSONObject(entryIndex);
                    if (entry == null) {
                        continue;
                    }
                    String pieceKey = entry.optString("piece", "").trim();
                    if (!pieceKey.isEmpty() && !pieceKeys.contains(pieceKey)) {
                        blockingErrors.add("Jigsaw pool '" + poolKey + "' pieces[" + entryIndex
                                + "] references missing piece '" + pieceKey + "'.");
                    }
                }
            }
            String fallback = pool.optString("fallback", "").trim();
            if (!fallback.isEmpty() && !poolKeys.contains(fallback)) {
                blockingErrors.add("Jigsaw pool '" + poolKey + "' references missing fallback pool '"
                        + fallback + "'.");
            }
        }
    }

    static void validateJigsawPieces(File piecesFolder,
                                     Set<String> poolKeys,
                                     Set<String> objectKeys,
                                     List<String> blockingErrors) {
        if (!piecesFolder.isDirectory()) {
            return;
        }
        List<File> pieceFiles = PackValidationIo.listJsonRecursive(piecesFolder);
        pieceFiles.sort(Comparator.comparing(File::getPath));
        for (File pieceFile : pieceFiles) {
            String pieceKey = PackValidationIo.deriveKey(piecesFolder, pieceFile);
            JSONObject piece = readGraphJson(pieceFile, "Jigsaw piece", pieceKey, blockingErrors);
            if (piece == null) {
                continue;
            }
            String objectKey = piece.optString("object", "").trim();
            if (objectKey.isEmpty()) {
                blockingErrors.add("Jigsaw piece '" + pieceKey + "' does not declare an object.");
            } else if (!objectKeys.contains(objectKey)) {
                blockingErrors.add("Jigsaw piece '" + pieceKey + "' references missing object '"
                        + objectKey + "'.");
            }
            JSONArray connectors = piece.optJSONArray("connectors");
            if (connectors == null) {
                continue;
            }
            for (int connectorIndex = 0; connectorIndex < connectors.length(); connectorIndex++) {
                JSONObject connector = connectors.optJSONObject(connectorIndex);
                if (connector == null) {
                    continue;
                }
                String poolKey = connector.optString("pool", "").trim();
                if (!poolKey.isEmpty() && !poolKeys.contains(poolKey)) {
                    blockingErrors.add("Jigsaw piece '" + pieceKey + "' connectors[" + connectorIndex
                            + "] references missing pool '" + poolKey + "'.");
                }
            }
        }
    }

    static JSONObject readGraphJson(File file,
                                    String resourceType,
                                    String resourceKey,
                                    List<String> blockingErrors) {
        try {
            return new JSONObject(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            String reason = e.getMessage();
            if (reason == null || reason.isBlank()) {
                reason = e.getClass().getSimpleName();
            }
            blockingErrors.add(resourceType + " '" + resourceKey + "' has invalid JSON: " + reason);
            return null;
        }
    }

    private static boolean isLegacyStructureIndex(String structureKey, JSONObject structure) {
        return "structure-index".equals(structureKey)
                && structure.has("counts")
                && structure.has("structureSets")
                && structure.has("iris");
    }

    static String structureHostType(String folderName) {
        return switch (folderName) {
            case "dimensions" -> "Dimension";
            case "regions" -> "Region";
            case "biomes" -> "Biome";
            default -> "Resource";
        };
    }

    private record RegistrySnapshot(Set<String> structures, Set<String> jigsaws, Set<String> pools,
                                    JigsawMetadataResolver jigsawMetadataResolver,
                                    PlatformStructureHooks hooks) {
    }

    private static final class JigsawMetadataResolver {
        private final PlatformStructureHooks hooks;
        private final Map<String, MetadataResolution> resolutions = new HashMap<>();

        private JigsawMetadataResolver(PlatformStructureHooks hooks) {
            this.hooks = hooks;
        }

        private MetadataResolution resolve(String structureKey) {
            MetadataResolution cached = resolutions.get(structureKey);
            if (cached != null) {
                return cached;
            }
            MetadataResolution resolved;
            try {
                JigsawSourceMetadata metadata = hooks.jigsawSourceMetadata(structureKey);
                if (metadata == null) {
                    throw new IllegalStateException(
                            "The active structure registry returned null jigsaw metadata for '"
                                    + structureKey + "'");
                }
                resolved = new MetadataResolution(metadata, null);
            } catch (RuntimeException | LinkageError error) {
                IrisLogging.reportError(
                        "Could not resolve live jigsaw metadata for registered structure '"
                                + structureKey + "' during pack validation", error);
                resolved = new MetadataResolution(null, failureMessage(error));
            }
            resolutions.put(structureKey, resolved);
            return resolved;
        }
    }

    private record MetadataResolution(JigsawSourceMetadata metadata, String failure) {
    }

    private record EffectiveTerrainAdjustment(String path, JSONObject adjustment) {
    }
}
