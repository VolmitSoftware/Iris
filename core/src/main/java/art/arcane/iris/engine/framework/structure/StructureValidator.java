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

package art.arcane.iris.engine.framework.structure;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.IrisValidationContext;
import art.arcane.iris.core.pack.PackValidationIo;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import com.volmit.validation.ValidationContext;
import com.volmit.validation.Validator;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Module {@code iris:structure}'s check of a pack: every structure a dimension, region or biome places must assemble
 * ({@link StructureGraphValidation}), and a dimension that replaces a vanilla structure with an Iris one may only use
 * structures that always assemble and fit ({@link NativeReplacementValidation}). Graph diagnostics keep their
 * severity; everything else is an error, as when Iris's own pack validator ran these checks.
 */
public final class StructureValidator implements Validator {
    private static final List<String> STRUCTURE_HOST_FOLDERS = List.of("dimensions", "regions", "biomes");
    private static final String STRUCTURES_FIELD = "structures";

    @Override
    public void validate(ValidationContext pack) {
        IrisData data = IrisValidationContext.of(pack).data();
        StructureGraphValidation.Validation graphs = StructureGraphValidation.validate(data, placedStructureKeys(data.getDataFolder()));
        graphs.problems().forEach(pack::report);
        NativeReplacementValidation.validate(data.getDataFolder(), graphs.replacementOutputStructures(), graphs.sampledVerticalEnvelopes())
                .forEach(pack::report);
    }

    /**
     * The keys of the structures the dimension, region and biome files of the pack in {@code packFolder} place, read
     * from their JSON as written, like the other checks of these files.
     */
    public static Set<String> placedStructureKeys(File packFolder) {
        Set<String> structureKeys = new LinkedHashSet<>();
        if (packFolder == null || !packFolder.isDirectory()) {
            return structureKeys;
        }
        for (String folderName : STRUCTURE_HOST_FOLDERS) {
            File resourceFolder = new File(packFolder, folderName);
            if (!resourceFolder.isDirectory()) {
                continue;
            }
            for (File resourceFile : PackValidationIo.listJsonRecursive(resourceFolder)) {
                addPlacedKeys(structureKeys, PackValidationIo.readJson(resourceFile));
            }
        }
        return Set.copyOf(structureKeys);
    }

    private static void addPlacedKeys(Set<String> structureKeys, JSONObject resource) {
        JSONArray placements = resource == null ? null : resource.optJSONArray(STRUCTURES_FIELD);
        if (placements == null) {
            return;
        }
        for (int placementIndex = 0; placementIndex < placements.length(); placementIndex++) {
            JSONObject placement = placements.optJSONObject(placementIndex);
            JSONArray references = placement == null ? null : placement.optJSONArray(STRUCTURES_FIELD);
            if (references == null) {
                continue;
            }
            for (int referenceIndex = 0; referenceIndex < references.length(); referenceIndex++) {
                if (references.opt(referenceIndex) instanceof String structureKey && !structureKey.isBlank()) {
                    structureKeys.add(structureKey);
                }
            }
        }
    }
}
