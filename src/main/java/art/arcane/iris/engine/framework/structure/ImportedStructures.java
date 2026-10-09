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

import art.arcane.iris.builtin.structure.StructureAdjustment;
import art.arcane.iris.builtin.structure.StructureSetFrequency;
import art.arcane.iris.builtin.structure.StructureSettings;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.config.PackSettings;
import com.volmit.resource.ResourceId;
import com.volmit.vanilla.VanillaStructureAdjustment;
import com.volmit.vanilla.VanillaStructureFit;
import com.volmit.vanilla.VanillaStructureSetup;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * What a dimension's {@link StructureSettings} say about the platform's own structures: which ones it disables, how
 * the starts of the others are adjusted, and how the platform sets them up. Keys match ignoring case and surrounding
 * space; a family pattern also matches the keys it starts at a namespace, slash or underscore boundary
 * ({@link #matchesKey}).
 */
public final class ImportedStructures {
    private final StructureSettings settings;

    public ImportedStructures(StructureSettings settings) {
        this.settings = settings;
    }

    /** The settings the dimension of {@code settings} declares, or the defaults when it declares none. */
    public static ImportedStructures of(PackSettings settings) {
        List<Entry<StructureSettings>> declared = settings.declared(StructureSettings.class);
        return new ImportedStructures(declared.isEmpty() ? new StructureSettings() : declared.getFirst().value());
    }

    /** Whether the dimension disables the platform structure {@code key}, by family or by its exact key. */
    public boolean disables(String key) {
        String normalizedKey = normalize(key);
        if (normalizedKey.isEmpty()) {
            return false;
        }
        return settings.disabled().stream().anyMatch(pattern -> matchesKey(pattern, key))
                || settings.disabledExact().stream().map(ImportedStructures::normalize)
                .anyMatch(exact -> !exact.isEmpty() && exact.equals(normalizedKey));
    }

    /**
     * How the platform's own starts of {@code key} are adjusted: the underground shift for underground steps, then every
     * matching adjustment in the order the pack lists them.
     */
    public VanillaStructureAdjustment adjustment(String key, boolean underground) {
        VanillaStructureAdjustment adjustment = VanillaStructureAdjustment.NONE.shifted(underground ? settings.undergroundYShift() : 0);
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

    /**
     * Whether the family pattern {@code pattern} matches the structure {@code key}: equal keys, or a pattern the key
     * starts with that ends in, or is followed in the key by, {@code :}, {@code /} or {@code _}. So
     * {@code minecraft:village} matches {@code minecraft:village_plains} and {@code nova_structures:} a whole namespace.
     */
    public static boolean matchesKey(String pattern, String key) {
        if (pattern == null || key == null) {
            return false;
        }
        String normalizedPattern = normalize(pattern);
        String normalizedKey = normalize(key);
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

    private static boolean matchesAny(List<String> patterns, String key) {
        return patterns.stream().anyMatch(pattern -> matchesKey(pattern, key));
    }

    private static VanillaStructureAdjustment asAdjustment(StructureAdjustment entry) {
        VanillaStructureFit fit = VanillaStructureFit.SOURCE;
        fit = entry.stilt().map(fit::withStilts).orElse(fit);
        fit = entry.terrain().map(fit::withTerrain).orElse(fit);
        VanillaStructureAdjustment adjustment = VanillaStructureAdjustment.NONE.shifted(entry.yShift()).withFit(fit);
        adjustment = entry.yBand().map(adjustment::withYBand).orElse(adjustment);
        return entry.preserveSourceY() ? adjustment.keepingSourceY() : adjustment;
    }

    private static String normalize(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }
}
