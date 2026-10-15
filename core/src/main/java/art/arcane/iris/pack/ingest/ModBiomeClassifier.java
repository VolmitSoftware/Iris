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

import art.arcane.iris.pack.ingest.catalog.CatalogBiome;
import art.arcane.iris.pack.ingest.catalog.WorldgenCatalog;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class ModBiomeClassifier {
    private static final Set<String> CAVE_TAGS = Set.of("is_cave", "is_caves", "caves", "is_underground", "underground");
    private static final Set<String> END_TAGS = Set.of("is_end", "is_the_end", "is_outer_end_island");

    private final String environment;
    private final String requiredTag;
    private final boolean requiredTagCaptured;

    ModBiomeClassifier(String environment, WorldgenCatalog catalog) {
        this.environment = environment;
        this.requiredTag = switch (environment) {
            case "NORMAL" -> "is_overworld";
            case "NETHER" -> "is_nether";
            case "THE_END" -> "is_end";
            default -> "";
        };
        this.requiredTagCaptured = !requiredTag.isEmpty() && anyBiomeTagged(catalog, requiredTag);
    }

    Classification classify(CatalogBiome biome) {
        Set<String> tags = conventionTags(biome.tags());
        if (environment.equals("NORMAL") && tags.contains("is_nether")) {
            return Classification.skip("tagged for the nether, not this overworld dimension");
        }
        if (environment.equals("NORMAL") && !Collections.disjoint(tags, END_TAGS)) {
            return Classification.skip("tagged for the end, not this overworld dimension");
        }
        if (requiredTagCaptured && !tags.contains(requiredTag)) {
            return Classification.skip("not tagged minecraft:" + requiredTag + " for this " + environment.toLowerCase(Locale.ROOT) + " dimension");
        }
        if (tags.contains("is_river")) {
            return Classification.skip("river biome; Iris hydrology owns rivers");
        }
        if (tags.contains("is_ocean") || tags.contains("is_deep_ocean")) {
            return Classification.of(BiomeRole.SEA);
        }
        if (tags.contains("is_beach")) {
            return Classification.of(BiomeRole.SHORE);
        }
        for (String tag : tags) {
            if (CAVE_TAGS.contains(tag)) {
                return Classification.of(BiomeRole.CAVE);
            }
        }
        return Classification.of(BiomeRole.LAND);
    }

    private static boolean anyBiomeTagged(WorldgenCatalog catalog, String tag) {
        for (CatalogBiome biome : catalog.biomes()) {
            if (conventionTags(biome.tags()).contains(tag)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> conventionTags(List<String> tags) {
        Set<String> paths = new HashSet<>();
        for (String tag : tags) {
            String key = VanillaJson.namespaced(tag);
            int colon = key.indexOf(':');
            String namespace = key.substring(0, colon);
            if (namespace.equals("minecraft") || namespace.equals("c")) {
                paths.add(key.substring(colon + 1));
            }
        }
        return paths;
    }

    record Classification(BiomeRole role, String skipReason) {
        static Classification of(BiomeRole role) {
            return new Classification(role, null);
        }

        static Classification skip(String reason) {
            return new Classification(null, reason);
        }

        boolean skipped() {
            return role == null;
        }
    }
}
