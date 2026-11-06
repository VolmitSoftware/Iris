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

package art.arcane.iris.engine.framework.structure;

import art.arcane.iris.builtin.structure.StructureAdjustment;
import art.arcane.iris.builtin.structure.StructureSetFrequency;
import art.arcane.iris.builtin.structure.StructureSettings;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.validation.Problem;
import com.volmit.validation.Subject;
import com.volmit.validation.ValidationContext;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Checks of the dimension's {@code importedStructures} settings that their types cannot state: a key in a list is
 * not blank, and a structure set is a namespaced registry key. Shapes, enum names and bounds are the decoder's.
 */
final class ImportedStructuresValidation {
    private static final String SETTINGS_KEY = "importedStructures";
    private static final Pattern REGISTRY_KEY = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    private ImportedStructuresValidation() {
    }

    static void validate(ValidationContext pack) {
        for (Entry<StructureSettings> entry : pack.settings().declared(StructureSettings.class)) {
            validate(pack, Subject.resource(entry.source()), entry.value());
        }
    }

    private static void validate(ValidationContext pack, Subject subject, StructureSettings settings) {
        requireKeys(pack, subject, SETTINGS_KEY + ".disabled", settings.disabled());
        requireKeys(pack, subject, SETTINGS_KEY + ".disabledExact", settings.disabledExact());
        List<StructureSetFrequency> overrides = settings.frequencyOverrides();
        for (int index = 0; index < overrides.size(); index++) {
            String structureSet = overrides.get(index).structureSet().trim();
            if (!REGISTRY_KEY.matcher(structureSet).matches()) {
                pack.report(Problem.error(subject, SETTINGS_KEY + ".frequencyOverrides[" + index
                        + "].structureSet is \"" + structureSet + "\"; it must be a namespaced key such as minecraft:villages"));
            }
        }
        List<StructureAdjustment> adjustments = settings.adjustments();
        for (int index = 0; index < adjustments.size(); index++) {
            requireKeys(pack, subject, SETTINGS_KEY + ".adjustments[" + index + "].match", adjustments.get(index).match());
        }
    }

    private static void requireKeys(ValidationContext pack, Subject subject, String path, List<String> keys) {
        for (int index = 0; index < keys.size(); index++) {
            String key = keys.get(index);
            if (key == null || key.isBlank()) {
                pack.report(Problem.error(subject, path + "[" + index + "] is blank; write a structure key"));
            }
        }
    }
}
