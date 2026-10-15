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

import art.arcane.iris.pack.ingest.catalog.CatalogPlacedFeature;
import art.arcane.iris.pack.ingest.catalog.WorldgenCatalogIndex;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class TreeFeatures {
    private static final Set<String> TREE_TYPES = Set.of("minecraft:tree", "minecraft:fallen_tree", "minecraft:huge_brown_mushroom",
            "minecraft:huge_red_mushroom", "minecraft:huge_fungus");
    private static final List<String> NESTED_FIELDS = List.of("feature", "features", "default", "feature_true", "feature_false");
    private static final String TAG_PREFIX = "#";
    private static final int MAX_DEPTH = 8;

    private final WorldgenCatalogIndex index;
    private final Map<String, CatalogPlacedFeature> byConfigured;

    TreeFeatures(WorldgenCatalogIndex index) {
        this.index = index;
        this.byConfigured = new HashMap<>();
        for (CatalogPlacedFeature feature : index.catalog().placedFeatures()) {
            if (feature.configuredFeature() != null) {
                byConfigured.putIfAbsent(VanillaJson.namespaced(feature.configuredFeature()), feature);
            }
        }
    }

    boolean isTree(CatalogPlacedFeature feature) {
        return configured(feature.featureType(), feature.config(), 0);
    }

    static boolean treeType(String type) {
        String namespaced = VanillaJson.namespaced(type);
        String path = namespaced.substring(namespaced.indexOf(':') + 1);
        return TREE_TYPES.contains(namespaced) || path.endsWith("tree") || path.startsWith("huge_");
    }

    private boolean configured(String type, JsonElement config, int depth) {
        if (type != null && treeType(type)) {
            return true;
        }
        if (depth >= MAX_DEPTH || config == null || !config.isJsonObject()) {
            return false;
        }
        JsonObject object = config.getAsJsonObject();
        for (String field : NESTED_FIELDS) {
            if (placed(object.get(field), depth + 1)) {
                return true;
            }
        }
        return false;
    }

    private boolean placed(JsonElement holder, int depth) {
        if (holder == null || holder.isJsonNull() || depth > MAX_DEPTH) {
            return false;
        }
        if (holder.isJsonArray()) {
            for (JsonElement entry : holder.getAsJsonArray()) {
                if (placed(entry, depth)) {
                    return true;
                }
            }
            return false;
        }
        if (holder.isJsonPrimitive()) {
            String key = holder.getAsString();
            if (key.startsWith(TAG_PREFIX)) {
                return false;
            }
            Optional<CatalogPlacedFeature> feature = index.placedFeature(VanillaJson.namespaced(key));
            return feature.isPresent() && configured(feature.get().featureType(), feature.get().config(), depth);
        }
        JsonObject object = holder.getAsJsonObject();
        if (object.has("chance")) {
            return placed(object.get("feature"), depth);
        }
        return configuredHolder(object.get("feature"), depth);
    }

    private boolean configuredHolder(JsonElement holder, int depth) {
        if (holder == null || holder.isJsonNull()) {
            return false;
        }
        if (holder.isJsonPrimitive()) {
            CatalogPlacedFeature feature = byConfigured.get(VanillaJson.namespaced(holder.getAsString()));
            return feature != null && configured(feature.featureType(), feature.config(), depth);
        }
        if (!holder.isJsonObject()) {
            return false;
        }
        JsonObject object = holder.getAsJsonObject();
        JsonElement type = object.get("type");
        return configured(type != null && type.isJsonPrimitive() ? type.getAsString() : null, object.get("config"), depth);
    }
}
