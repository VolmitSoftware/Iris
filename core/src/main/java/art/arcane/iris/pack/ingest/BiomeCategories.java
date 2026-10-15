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

package art.arcane.iris.pack.ingest;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

final class BiomeCategories {
    private static final Map<String, Set<String>> CATEGORY_TAGS = Map.ofEntries(
            Map.entry("forest", Set.of("is_forest", "is_birch_forest", "is_dark_forest", "is_flower_forest", "is_tree/deciduous")),
            Map.entry("taiga", Set.of("is_taiga", "is_tree/coniferous", "is_old_growth")),
            Map.entry("jungle", Set.of("is_jungle", "is_tree/jungle")),
            Map.entry("savanna", Set.of("is_savanna", "is_tree/savanna")),
            Map.entry("badlands", Set.of("is_badlands")),
            Map.entry("desert", Set.of("is_desert", "is_sandy")),
            Map.entry("mountain", Set.of("is_mountain", "is_mountain/peak", "is_mountain/slope", "is_peak", "is_slope", "is_hill", "is_windswept", "is_plateau")),
            Map.entry("snowy", Set.of("is_snowy", "is_icy")),
            Map.entry("swamp", Set.of("is_swamp")),
            Map.entry("mushroom", Set.of("is_mushroom")),
            Map.entry("plains", Set.of("is_plains", "is_meadow")),
            Map.entry("beach", Set.of("is_beach", "is_stony_shores")),
            Map.entry("ocean", Set.of("is_ocean", "is_deep_ocean")),
            Map.entry("cave", Set.of("is_cave", "is_underground")));

    private static final double UNKNOWN_DISTANCE = 0.5D;
    private static final String VOID_TAG = "is_void";

    private BiomeCategories() {
    }

    static Set<String> of(List<String> tags) {
        Set<String> paths = ModBiomeClassifier.conventionTags(tags);
        Set<String> categories = new TreeSet<>();
        for (Map.Entry<String, Set<String>> category : CATEGORY_TAGS.entrySet()) {
            for (String tag : category.getValue()) {
                if (paths.contains(tag)) {
                    categories.add(category.getKey());
                    break;
                }
            }
        }
        return categories;
    }

    static boolean isVoid(List<String> tags) {
        return ModBiomeClassifier.conventionTags(tags).contains(VOID_TAG);
    }

    static double distance(Set<String> first, Set<String> second) {
        if (first.isEmpty() || second.isEmpty()) {
            return UNKNOWN_DISTANCE;
        }
        Set<String> union = new HashSet<>(first);
        union.addAll(second);
        int shared = 0;
        for (String category : first) {
            if (second.contains(category)) {
                shared++;
            }
        }
        return (union.size() - shared) / (double) union.size();
    }
}
