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

import art.arcane.iris.builtin.RegistryKeyFamily;
import art.arcane.iris.builtin.structure.StructureAdjustment;
import art.arcane.iris.builtin.structure.StructureSetFrequency;
import art.arcane.iris.builtin.structure.StructureSettings;
import com.volmit.config.PackSettings;
import com.volmit.resource.ResourceId;
import com.volmit.vanilla.VanillaStructureSetup;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * What a dimension's {@link StructureSettings} say about the platform's own structures: which ones it disables, how
 * the starts of the others are adjusted, and how the platform sets them up. Importing is opt-in: unless the settings
 * are {@link StructureSettings#enabled}, every platform structure is disabled. Keys match ignoring case and surrounding
 * space; a family pattern also matches the keys it starts at a namespace, slash or underscore boundary
 * ({@link RegistryKeyFamily}).
 */
public final class ImportedStructures {
    private final StructureSettings settings;

    public ImportedStructures(StructureSettings settings) {
        this.settings = settings;
    }

    /** The settings the dimension of {@code settings} declares, or the defaults when it declares none. */
    public static ImportedStructures of(PackSettings settings) {
        return new ImportedStructures(settings.declared(StructureSettings.class).dimension().orElseGet(StructureSettings::new));
    }

    /**
     * Whether the dimension disables the platform structure {@code key}: every key unless it imports structures,
     * otherwise by family or by its exact key.
     */
    public boolean disables(String key) {
        if (!settings.enabled()) {
            return true;
        }
        String normalizedKey = normalize(key);
        if (normalizedKey.isEmpty()) {
            return false;
        }
        return settings.disabled().stream().anyMatch(pattern -> RegistryKeyFamily.matches(pattern, key))
                || settings.disabledExact().stream().map(ImportedStructures::normalize)
                .anyMatch(exact -> !exact.isEmpty() && exact.equals(normalizedKey));
    }

    /**
     * How the platform's own starts of {@code key} are adjusted: the underground shift for underground steps, then every
     * matching adjustment in the order the pack lists them.
     */
    public NativeStructureFit adjustment(String key, boolean underground) {
        NativeStructureFit adjustment = NativeStructureFit.NONE.shifted(underground ? settings.undergroundYShift() : 0);
        for (StructureAdjustment entry : settings.adjustments()) {
            if (entry != null && matchesAny(entry.match(), key)) {
                adjustment = adjustment.then(asAdjustment(entry));
            }
        }
        return adjustment;
    }

    /**
     * The structure set frequencies the dimension changes and whether it keeps data packs off the platform's definitions.
     * An override naming no namespaced key can match no registered set and is left out.
     */
    public VanillaStructureSetup setup() {
        VanillaStructureSetup setup = settings.datapackOverrides()
                ? VanillaStructureSetup.UNCHANGED
                : VanillaStructureSetup.UNCHANGED.protectingVanillaDefinitions();
        for (StructureSetFrequency override : settings.frequencyOverrides()) {
            Optional<ResourceId> structureSet = override == null ? Optional.empty() : structureSetId(override.structureSet());
            if (structureSet.isPresent()) {
                setup = setup.withFrequency(structureSet.get(), override.multiplier());
            }
        }
        return setup;
    }

    private static Optional<ResourceId> structureSetId(String key) {
        try {
            return Optional.of(ResourceId.parse(normalize(key)));
        } catch (IllegalArgumentException notAnId) {
            return Optional.empty();
        }
    }

    private static boolean matchesAny(List<String> patterns, String key) {
        return patterns.stream().anyMatch(pattern -> RegistryKeyFamily.matches(pattern, key));
    }

    private static NativeStructureFit asAdjustment(StructureAdjustment entry) {
        return new NativeStructureFit(entry.yShift(), entry.yBand().orElse(null), entry.preserveSourceY(),
                entry.stilt().orElse(null), entry.terrain().orElse(null));
    }

    private static String normalize(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }
}
