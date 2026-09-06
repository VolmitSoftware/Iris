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

import art.arcane.iris.core.compat.CompatFinding;
import art.arcane.iris.core.compat.PackCompatReport;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisImage;
import art.arcane.iris.engine.object.IrisObject;
import art.arcane.iris.engine.object.IrisObjectPlacement;
import art.arcane.iris.engine.object.IrisRegion;
import art.arcane.iris.engine.object.matter.IrisMatterObject;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.volmlib.util.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class PackValidator {
    static final String TRASH_ROOT = ".iris-trash";
    static final String DATAPACK_IMPORTS = "datapack-imports";
    static final String EXTERNAL_DATAPACKS = "externaldatapacks";
    static final String INTERNAL_DATAPACKS = "internaldatapacks";
    static final String DATAPACKS_FOLDER = "datapacks";
    static final String CACHE_FOLDER = "cache";
    static final String OBJECTS_FOLDER = "objects";
    static final String LOOT_FOLDER = "loot";
    static final String DIMENSIONS_FOLDER = "dimensions";
    static final String STRUCTURES_FOLDER = "structures";
    static final String JIGSAW_POOLS_FOLDER = "jigsaw-pools";
    static final String JIGSAW_PIECES_FOLDER = "jigsaw-pieces";
    static final List<String> STRUCTURE_HOST_FOLDERS = List.of(DIMENSIONS_FOLDER, "regions", "biomes");
    static final List<String> REMOVED_WORLDGEN_FIELDS = List.of("fluidBodies");
    static final List<String> UNSUPPORTED_STRUCTURE_TRANSFORM_FIELDS = List.of("rotation", "translate", "scale");
    static final Pattern RESOURCE_KEY_PATTERN = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    private static final int BLOCKING_REASON_LIMIT = 3;
    /**
     * Binary assets, not key-bearing registrants: an object is gated through the placements that can place it
     * (palette header only) and images carry no registry keys, so loading every one of them here would parse the
     * whole object library for nothing.
     */
    private static final Set<Class<?>> UNGATED_LOADERS =
            Set.of(IrisObject.class, IrisMatterObject.class, IrisImage.class);

    private PackValidator() {
    }

    public static PackValidationResult validate(File packFolder) {
        return validate(packFolder, true);
    }

    public static PackValidationResult validateForDatapackBootstrap(File packFolder) {
        return validate(packFolder, false);
    }

    public static PackValidationResult validateForPackaging(File packFolder) {
        return validate(packFolder, false);
    }

    public static void requireValidStaticObjects(File packFolder, String dimensionKey, JSONObject dimension) {
        List<String> errors = new ArrayList<>();
        PackDimensionValidator.validateStaticObjects(packFolder, dimensionKey, dimension, errors);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }
    }

    private static PackValidationResult validate(File packFolder, boolean validateLiveRegistries) {
        String packName = packFolder == null ? "<unknown>" : packFolder.getName();
        List<String> blockingErrors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        long validatedAt = System.currentTimeMillis();

        if (packFolder == null || !packFolder.isDirectory()) {
            blockingErrors.add("Pack folder does not exist or is not a directory.");
            return new PackValidationResult(packName, blockingErrors, warnings, validatedAt);
        }

        File dimensionsFolder = new File(packFolder, DIMENSIONS_FOLDER);
        if (!dimensionsFolder.isDirectory()) {
            blockingErrors.add("Missing dimensions/ folder.");
            return new PackValidationResult(packName, blockingErrors, warnings, validatedAt);
        }

        List<File> discoveredDimensions = PackValidationIo.listJsonRecursive(dimensionsFolder);
        if (discoveredDimensions.isEmpty()) {
            blockingErrors.add("No dimension JSON files under dimensions/.");
            return new PackValidationResult(packName, blockingErrors, warnings, validatedAt);
        }
        discoveredDimensions.sort((first, second) -> first.getPath().compareTo(second.getPath()));
        File[] dimensionFiles = discoveredDimensions.toArray(File[]::new);

        PackDimensionValidator.validateDimensions(packFolder, dimensionFiles, blockingErrors, warnings);
        PackImageMapValidator.Validation imageMaps = PackImageMapValidator.validate(
                packFolder, dimensionFiles, validateLiveRegistries);
        addDistinct(blockingErrors, imageMaps.errors());
        addDistinct(warnings, imageMaps.warnings());
        PackHydrologyValidator.Validation hydrologyValidation =
                PackHydrologyValidator.validate(packFolder, dimensionFiles);
        addDistinct(blockingErrors, hydrologyValidation.errors());
        addDistinct(warnings, hydrologyValidation.warnings());
        blockingErrors.addAll(PackCaveProfileValidator.validateLegacyFields(packFolder));
        PackLootValidator.LootGraphIssues lootIssues = PackLootValidator.validateLootGraph(packFolder);
        addDistinct(blockingErrors, lootIssues.errors());
        addDistinct(warnings, lootIssues.warnings());
        blockingErrors.addAll(PackObjectSurfaceValidator.validateRemovedWorldgenFields(packFolder));
        blockingErrors.addAll(PackObjectSurfaceValidator.validateObjectSurfaceSupport(packFolder));
        blockingErrors.addAll(PackObjectSurfaceValidator.validateUnsupportedStructureTransforms(packFolder));
        blockingErrors.addAll(PackObjectSurfaceValidator.validateStructureGraph(
                packFolder, validateLiveRegistries));
        StructureGraphPackValidator.Validation compiledStructures =
                StructureGraphPackValidator.validate(
                        packFolder.toPath(), PackObjectSurfaceValidator.collectPlacedStructureKeys(packFolder));
        addDistinct(blockingErrors, compiledStructures.errors());
        addDistinct(warnings, compiledStructures.warnings());
        blockingErrors.addAll(PackNativeStructureValidator.validateNativeStructureReplacements(
                packFolder,
                compiledStructures.replacementOutputStructures(),
                compiledStructures.sampledVerticalEnvelopes()));
        blockingErrors.addAll(PackSpawnValidator.validateSpawnerEntityReferences(
                new File(packFolder, "spawners"), new File(packFolder, "entities")));
        blockingErrors.addAll(PackSpawnValidator.validateCustomBiomeSpawns(
                new File(packFolder, "biomes"), PackSpawnValidator::resolveEntitySpawnCategory));
        blockingErrors.addAll(PackBiomeLayerValidator.validateLayers(new File(packFolder, "biomes")));
        blockingErrors.addAll(PackBiomeLayerValidator.validateDecoratorPalettes(
                new File(packFolder, "biomes"), new File(packFolder, "snippet/decorator")));
        PackStyledRangeDefaultValidator.Validation styledRanges = PackStyledRangeDefaultValidator.validate(packFolder);
        addDistinct(blockingErrors, styledRanges.errors());
        addDistinct(warnings, styledRanges.warnings());
        addDistinct(warnings, PackGeneratorDuplicateValidator.validateDuplicateGenerators(packFolder));

        // Strict content mode promotes unresolved keys and bad block properties from advisory to blocking. Palette
        // -sourced findings are exempt and stay warnings - see ContentKeyValidator.collectContentKeyIssues.
        ContentKeyValidator.ContentKeyIssues contentKeys = ContentKeyValidator.collectContentKeyIssues(packFolder);
        addDistinct(ContentKeyValidator.strictContent() ? blockingErrors : warnings, contentKeys.strict());
        addDistinct(warnings, contentKeys.advisory());

        String minecraftVersion = validateLiveRegistries && IrisPlatforms.isBound()
                ? IrisPlatforms.get().minecraftVersion()
                : null;
        List<CompatFinding> compatFindings = validateLiveRegistries
                ? gateContent(packFolder, dimensionFiles, minecraftVersion, blockingErrors, warnings)
                : List.of();

        return new PackValidationResult(
                packName, blockingErrors, warnings, validatedAt, compatFindings, minecraftVersion);
    }

    /**
     * Forces a complete load of the pack through {@link IrisData} so every registrant and every reachable
     * {@link IrisObjectPlacement} is evaluated by the version-content gate, then folds the resulting findings into
     * this validation. Runs on a detached loader that is closed again, and only on a cache miss (the caller's cache
     * fingerprint already covers the Minecraft version and the registry key sets).
     */
    private static List<CompatFinding> gateContent(
            File packFolder,
            File[] dimensionFiles,
            String minecraftVersion,
            List<String> blockingErrors,
            List<String> warnings
    ) {
        if (!IrisPlatforms.isBound()) {
            return List.of();
        }
        IrisData data = null;
        try {
            data = IrisData.openRuntime(packFolder);
            if (!data.getContentGate().ready()) {
                return List.of();
            }
            boolean dimensionExcluded = loadEverything(packFolder, data, dimensionFiles);
            List<CompatFinding> findings = data.getCompatReport().findings();
            applyCompatFindings(minecraftVersion, dimensionExcluded, findings, blockingErrors, warnings);
            return findings;
        } catch (Throwable e) {
            IrisLogging.reportError("Version-content gating failed for pack '" + packFolder.getName() + "'", e);
            return List.of();
        } finally {
            if (data != null) {
                try {
                    data.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /**
     * Loads every registrant of every loader type (dimensions first, so dimension-level fallbacks are in place before
     * anything else resolves) and evaluates every placement reachable from the dimensions, regions and biomes, plus
     * every static object on each dimension.
     *
     * @return true when any dimension in the pack cannot generate on this server
     */
    private static boolean loadEverything(File packFolder, IrisData data, File[] dimensionFiles) {
        boolean dimensionExcluded = false;
        File dimensionsFolder = new File(packFolder, DIMENSIONS_FOLDER);
        for (File dimensionFile : dimensionFiles) {
            IrisDimension dimension = data.getDimensionLoader().load(
                    PackValidationIo.deriveKey(dimensionsFolder, dimensionFile), false);
            if (dimension == null) {
                continue;
            }
            if (dimension.isCompatExcluded()) {
                dimensionExcluded = true;
            }
            // Static objects gate themselves (the walker never descends into them), so ask each one here.
            dimension.evaluateStaticObjectsCompat(data);
        }

        Set<IrisObjectPlacement> placements = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Map.Entry<Class<? extends IrisRegistrant>, ResourceLoader<? extends IrisRegistrant>> entry
                : data.getLoaders().entrySet()) {
            if (UNGATED_LOADERS.contains(entry.getKey())) {
                continue;
            }
            for (String key : entry.getValue().getPossibleKeys()) {
                collectPlacements(entry.getValue().load(key, false), placements);
            }
        }
        for (IrisObjectPlacement placement : placements) {
            placement.evaluateCompat(data);
        }
        return dimensionExcluded;
    }

    private static void collectPlacements(IrisRegistrant registrant, Set<IrisObjectPlacement> out) {
        if (registrant instanceof IrisBiome biome) {
            addPlacements(biome.getObjects(), out);
            return;
        }
        if (registrant instanceof IrisRegion region) {
            addPlacements(region.getObjects(), out);
        }
    }

    private static void addPlacements(Collection<IrisObjectPlacement> placements, Set<IrisObjectPlacement> out) {
        if (placements == null) {
            return;
        }
        for (IrisObjectPlacement placement : placements) {
            if (placement != null) {
                out.add(placement);
            }
        }
    }

    /**
     * Turns gate findings into validation output: an excluded dimension is blocking, and any
     * {@link ContentKeyValidator} warning about a key the gate already reported is dropped so the operator does not
     * read the same missing key twice.
     */
    static void applyCompatFindings(
            String minecraftVersion,
            boolean dimensionExcluded,
            List<CompatFinding> findings,
            List<String> blockingErrors,
            List<String> warnings
    ) {
        if (dimensionExcluded) {
            addDistinct(blockingErrors, List.of(excludedDimensionError(minecraftVersion, findings)));
        }
        if (findings == null || findings.isEmpty() || warnings == null) {
            return;
        }
        Set<String> reported = new LinkedHashSet<>();
        for (CompatFinding finding : findings) {
            reported.add("'" + finding.key() + "'");
        }
        warnings.removeIf(warning -> {
            for (String quoted : reported) {
                if (warning.contains(quoted)) {
                    return true;
                }
            }
            return false;
        });
    }

    /** {@code Pack cannot generate on Minecraft 26.1.2: minecraft:sulfur (block), minecraft:camel (entity)} */
    static String excludedDimensionError(String minecraftVersion, List<CompatFinding> findings) {
        StringBuilder sb = new StringBuilder(96);
        sb.append("Pack cannot generate on Minecraft ")
                .append(minecraftVersion == null || minecraftVersion.isBlank() ? "unknown" : minecraftVersion)
                .append(": ");
        Set<String> reasons = new LinkedHashSet<>();
        if (findings != null) {
            for (CompatFinding finding : findings) {
                if (reasons.size() == BLOCKING_REASON_LIMIT) {
                    break;
                }
                reasons.add(finding.key() + " (" + finding.registry().label() + ")");
            }
        }
        sb.append(reasons.isEmpty() ? "the dimension composes content this server does not have"
                : String.join(", ", reasons));
        return sb.toString();
    }

    /** Boot listing for a validated pack; empty when the gate found nothing. */
    public static List<String> compatBootLines(PackValidationResult result, String minecraftVersion, int perKeyCap) {
        if (result == null || result.getCompatFindings().isEmpty()) {
            return List.of();
        }
        return PackCompatReport.of(result.getCompatFindings())
                .bootLines(result.getPackName(), versionOf(result, minecraftVersion), perKeyCap);
    }

    /** One-line compat summary appended to the {@code Pack 'x' validated (...)} line; empty when nothing to say. */
    public static String compatSummary(PackValidationResult result, String minecraftVersion) {
        if (result == null || result.getCompatFindings().isEmpty()) {
            return "";
        }
        return PackCompatReport.of(result.getCompatFindings())
                .summaryLine(versionOf(result, minecraftVersion));
    }

    private static String versionOf(PackValidationResult result, String minecraftVersion) {
        if (minecraftVersion != null && !minecraftVersion.isBlank()) {
            return minecraftVersion;
        }
        return result.getMinecraftVersion();
    }

    private static void addDistinct(List<String> destination, List<String> additions) {
        for (String addition : additions) {
            if (!destination.contains(addition)) {
                destination.add(addition);
            }
        }
    }
}
