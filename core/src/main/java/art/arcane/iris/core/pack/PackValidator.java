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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates a pack before Iris loads it, packages it or opens it in studio: Iris's own checks of its files, then the
 * validators the pack's modules register ({@link ModulePackValidation}). Blocking errors stop the pack from loading;
 * warnings are only shown.
 */
public final class PackValidator {
    static final String TRASH_ROOT = ".iris-trash";
    static final String DATAPACK_IMPORTS = "datapack-imports";
    static final String EXTERNAL_DATAPACKS = "externaldatapacks";
    static final String INTERNAL_DATAPACKS = "internaldatapacks";
    static final String DATAPACKS_FOLDER = "datapacks";
    static final String CACHE_FOLDER = "cache";
    static final String OBJECTS_FOLDER = "objects";
    static final String DIMENSIONS_FOLDER = "dimensions";
    static final String STRUCTURES_FOLDER = "structures";
    static final String JIGSAW_POOLS_FOLDER = "jigsaw-pools";
    static final String JIGSAW_PIECES_FOLDER = "jigsaw-pieces";
    static final List<String> STRUCTURE_HOST_FOLDERS = List.of(DIMENSIONS_FOLDER, "regions", "biomes");
    static final Pattern RESOURCE_KEY_PATTERN = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

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

        File[] dimensionFiles = dimensionsFolder.listFiles(f -> f.isFile() && f.getName().endsWith(".json"));
        if (dimensionFiles == null || dimensionFiles.length == 0) {
            blockingErrors.add("No dimension JSON files under dimensions/.");
            return new PackValidationResult(packName, blockingErrors, warnings, validatedAt);
        }

        PackDimensionValidator.validateDimensions(packFolder, dimensionFiles, blockingErrors, warnings);
        PackImageMapValidator.Validation imageMaps = PackImageMapValidator.validate(
                packFolder, dimensionFiles, validateLiveRegistries);
        addDistinct(blockingErrors, imageMaps.errors());
        addDistinct(warnings, imageMaps.warnings());
        blockingErrors.addAll(PackObjectSurfaceValidator.validateObjectSurfaceSupport(packFolder));
        blockingErrors.addAll(PackObjectSurfaceValidator.validateHostFilesParse(packFolder));
        blockingErrors.addAll(PackObjectSurfaceValidator.validateStructureGraph(
                packFolder, validateLiveRegistries));
        blockingErrors.addAll(PackSpawnValidator.validateSpawnerEntityReferences(
                new File(packFolder, "spawners"), new File(packFolder, "entities")));
        blockingErrors.addAll(PackSpawnValidator.validateCustomBiomeSpawns(
                new File(packFolder, "biomes"), PackSpawnValidator::resolveEntitySpawnCategory));
        blockingErrors.addAll(PackBiomeLayerValidator.validateCeilingLayerCounts(new File(packFolder, "biomes")));
        blockingErrors.addAll(PackBiomeLayerValidator.validateDecoratorPalettes(
                new File(packFolder, "biomes"), new File(packFolder, "snippet/iris/decorant/decorator")));
        PackStyledRangeDefaultValidator.Validation styledRanges = PackStyledRangeDefaultValidator.validate(packFolder);
        addDistinct(blockingErrors, styledRanges.errors());
        addDistinct(warnings, styledRanges.warnings());
        addDistinct(warnings, PackGeneratorDuplicateValidator.validateDuplicateGenerators(packFolder));

        // Strict content mode promotes unresolved keys and bad block properties from advisory to blocking. Palette
        // -sourced findings are exempt and stay warnings - see ContentKeyValidator.collectContentKeyIssues.
        ContentKeyValidator.ContentKeyIssues contentKeys = ContentKeyValidator.collectContentKeyIssues(packFolder);
        addDistinct(ContentKeyValidator.strictContent() ? blockingErrors : warnings, contentKeys.strict());
        addDistinct(warnings, contentKeys.advisory());

        ModulePackValidation.Findings modules = ModulePackValidation.validate(packFolder);
        addDistinct(blockingErrors, modules.errors());
        addDistinct(warnings, modules.warnings());

        return new PackValidationResult(packName, blockingErrors, warnings, validatedAt);
    }

    private static void addDistinct(List<String> destination, List<String> additions) {
        for (String addition : additions) {
            if (!destination.contains(addition)) {
                destination.add(addition);
            }
        }
    }
}
