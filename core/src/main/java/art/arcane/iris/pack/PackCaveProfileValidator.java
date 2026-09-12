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

import art.arcane.volmlib.util.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class PackCaveProfileValidator {
    private static final String CAVE_PROFILE_SNIPPET_FOLDER = "snippet/cave-profile";
    private static final List<LegacyField> LEGACY_FIELDS = List.of(
            new LegacyField("allowWater", "allowFluid"),
            new LegacyField("waterMinDepthBelowSurface", "fluidMinDepthBelowSurface"),
            new LegacyField("waterRequiresFloor", "fluidRequiresFloor")
    );

    private PackCaveProfileValidator() {
    }

    static List<String> validateLegacyFields(File packFolder) {
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
                JSONObject caveProfile = resource.optJSONObject("caveProfile");
                if (caveProfile == null) {
                    continue;
                }
                String resourceKey = PackValidationIo.deriveKey(resourceFolder, resourceFile);
                validateFields(resourceType + " '" + resourceKey + "' caveProfile.", "caveProfile.", caveProfile,
                        blockingErrors);
            }
        }

        File snippetFolder = new File(packFolder, CAVE_PROFILE_SNIPPET_FOLDER);
        if (!snippetFolder.isDirectory()) {
            return blockingErrors;
        }
        List<File> snippetFiles = PackValidationIo.listJsonRecursive(snippetFolder);
        snippetFiles.sort(Comparator.comparing(File::getPath));
        for (File snippetFile : snippetFiles) {
            JSONObject caveProfile = PackValidationIo.readJson(snippetFile);
            if (caveProfile == null) {
                continue;
            }
            String snippetKey = PackValidationIo.deriveKey(snippetFolder, snippetFile);
            validateFields("Cave-profile snippet '" + snippetKey + "' ", "", caveProfile, blockingErrors);
        }
        return blockingErrors;
    }

    private static void validateFields(String location, String replacementPrefix, JSONObject caveProfile,
                                       List<String> blockingErrors) {
        for (LegacyField field : LEGACY_FIELDS) {
            if (caveProfile.has(field.oldName())) {
                blockingErrors.add(location + field.oldName() + " was removed; use " + replacementPrefix
                        + field.newName()
                        + ". Cave aquifers use the dimension fluidPalette, which defaults to water.");
            }
        }
    }

    private record LegacyField(String oldName, String newName) {
    }
}
