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

package art.arcane.iris.structure.nativegen;

import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.documentation.Description;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("Denies native placed features in specific registry biomes only. Iris custom biomes match through the vanilla derivative that supplies their features, so a rule for 'minecraft:forest' also covers Iris biomes derived from it.")
@Data
public class IrisImportedFeatureBiomeRule {
    private static final String TAG_PREFIX = "#";

    @ArrayType(type = String.class, min = 1)
    @Description("Registry biomes this rule applies to: an exact key such as 'minecraft:forest', a namespace such as 'minecraft:', or a biome tag such as '#minecraft:is_forest'.")
    private KList<String> biomes = new KList<>();

    @ArrayType(type = String.class, min = 1)
    @Description("Placed feature families denied in the matched biomes, matched like importedFeatures.disabled on namespace, slash, or underscore boundaries.")
    private KList<String> disabled = new KList<>();

    @ArrayType(type = String.class, min = 1)
    @Description("Exact placed feature keys denied in the matched biomes after trimming and case normalization.")
    private KList<String> disabledExact = new KList<>();

    public boolean appliesTo(String biomeKey, Set<String> biomeTags) {
        String normalizedBiome = normalize(biomeKey);
        for (String entry : Objects.requireNonNull(biomes, "importedFeatures.biomeRules.biomes must not be null")) {
            String normalizedEntry = normalize(entry);
            if (normalizedEntry.isEmpty()) {
                continue;
            }
            if (normalizedEntry.startsWith(TAG_PREFIX)) {
                if (biomeTags.contains(normalizedEntry.substring(TAG_PREFIX.length()))) {
                    return true;
                }
                continue;
            }
            if (normalizedEntry.endsWith(":") ? normalizedBiome.startsWith(normalizedEntry) : normalizedBiome.equals(normalizedEntry)) {
                return true;
            }
        }
        return false;
    }

    public boolean denies(String featureKey) {
        for (String entry : Objects.requireNonNull(disabled, "importedFeatures.biomeRules.disabled must not be null")) {
            if (IrisImportedStructureControl.matchesKey(entry, featureKey)) {
                return true;
            }
        }
        String normalizedFeature = normalize(featureKey);
        for (String entry : Objects.requireNonNull(disabledExact, "importedFeatures.biomeRules.disabledExact must not be null")) {
            if (!normalize(entry).isEmpty() && normalize(entry).equals(normalizedFeature)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
