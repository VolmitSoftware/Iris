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
import art.arcane.iris.pack.ingest.catalog.WorldgenCatalog;
import art.arcane.iris.pack.ingest.catalog.WorldgenCatalogIndex;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class PatchDecoratorConverter {
    static final String SIMPLE_BLOCK = "minecraft:simple_block";
    private static final Set<String> LEGACY_PATCH_TYPES = Set.of("minecraft:flower", "minecraft:random_patch", "minecraft:no_bonemeal_flower");
    private static final int LEGACY_DEFAULT_TRIES = 128;
    private static final int LEGACY_DEFAULT_Y_SPREAD = 3;
    private static final double COLUMNS_PER_CHUNK = 256D;

    private PatchDecoratorConverter() {
    }

    static boolean handles(CatalogPlacedFeature feature) {
        String type = VanillaJson.namespaced(feature.featureType());
        return type.equals(SIMPLE_BLOCK) || LEGACY_PATCH_TYPES.contains(type);
    }

    static JsonObject convert(CatalogPlacedFeature feature, WorldgenCatalogIndex index, double canopy) throws IngestConversionException {
        String type = VanillaJson.namespaced(feature.featureType());
        JsonObject config = VanillaJson.featureConfig(feature.config());
        VanillaPlacementChain outer = VanillaPlacementChain.surfaceChain(VanillaJson.placement(feature.placement()));
        Set<String> whitelist = new LinkedHashSet<>(outer.whitelist());
        double expected;
        JsonElement provider;
        if (LEGACY_PATCH_TYPES.contains(type)) {
            int tries = VanillaJson.integer(config, "tries", LEGACY_DEFAULT_TRIES, "patch");
            int verticalSpread = Math.abs(VanillaJson.integer(config, "y_spread", LEGACY_DEFAULT_Y_SPREAD, "patch"));
            double surfaceHitChance = outer.surfaceHitChance() / (verticalSpread + 1D);
            NestedBlock nested = nestedBlock(config.get("feature"), index);
            whitelist.addAll(nested.whitelist());
            provider = nested.provider();
            expected = outer.expectedPlacements() * tries * surfaceHitChance;
        } else {
            provider = config.get("to_place");
            expected = outer.expectedPlacements() * outer.surfaceHitChance();
        }
        if (expected <= 0D) {
            throw new IngestConversionException("patch placement never places");
        }
        if (outer.leafBlocked()) {
            expected *= 1D - Math.max(0D, Math.min(1D, canopy));
        }
        List<JsonObject> palette = VanillaBlockStates.palette(provider);
        JsonObject decorator = new JsonObject();
        decorator.addProperty("chance", IngestJson.round(columnCoverage(expected)));
        JsonArray paletteArray = new JsonArray(palette.size());
        for (JsonObject entry : palette) {
            paletteArray.add(entry);
        }
        decorator.add("palette", paletteArray);
        if (!whitelist.isEmpty()) {
            JsonArray whitelistArray = new JsonArray(whitelist.size());
            for (String block : whitelist) {
                JsonObject entry = new JsonObject();
                entry.addProperty("block", block);
                whitelistArray.add(entry);
            }
            decorator.add("whitelist", whitelistArray);
        }
        return decorator;
    }

    static double columnCoverage(double expectedPlacements) {
        return 1D - Math.exp(-expectedPlacements / COLUMNS_PER_CHUNK);
    }

    private static NestedBlock nestedBlock(JsonElement holder, WorldgenCatalogIndex index) throws IngestConversionException {
        if (holder != null && holder.isJsonPrimitive()) {
            String key = VanillaJson.namespaced(holder.getAsString());
            Optional<CatalogPlacedFeature> placed = index.placedFeature(key);
            if (placed.isEmpty() || !VanillaJson.namespaced(placed.get().featureType()).equals(SIMPLE_BLOCK)) {
                throw new IngestConversionException("patch feature " + key + " is not a captured simple_block feature");
            }
            return new NestedBlock(VanillaJson.featureConfig(placed.get().config()).get("to_place"),
                    VanillaPlacementChain.filterWhitelist(VanillaJson.placement(placed.get().placement())));
        }
        JsonObject placed = VanillaJson.object(holder, "patch feature");
        JsonElement placement = placed.get("placement");
        Set<String> whitelist = placement == null || placement.isJsonNull()
                ? Set.of()
                : VanillaPlacementChain.filterWhitelist(VanillaJson.array(placement, "patch feature placement"));
        return new NestedBlock(configuredToPlace(placed.get("feature"), index.catalog()), whitelist);
    }

    private static JsonElement configuredToPlace(JsonElement configured, WorldgenCatalog catalog) throws IngestConversionException {
        if (configured != null && configured.isJsonPrimitive()) {
            String key = VanillaJson.namespaced(configured.getAsString());
            for (CatalogPlacedFeature candidate : catalog.placedFeatures()) {
                if (VanillaJson.namespaced(candidate.configuredFeature()).equals(key)
                        && VanillaJson.namespaced(candidate.featureType()).equals(SIMPLE_BLOCK)) {
                    return VanillaJson.featureConfig(candidate.config()).get("to_place");
                }
            }
            throw new IngestConversionException("patch configured feature " + key + " is not a captured simple_block feature");
        }
        JsonObject object = VanillaJson.object(configured, "patch configured feature");
        String type = VanillaJson.type(object, "type");
        if (!type.equals(SIMPLE_BLOCK)) {
            throw new IngestConversionException("patch places " + (type.isEmpty() ? "an unknown feature" : type) + " instead of simple_block");
        }
        return VanillaJson.featureConfig(object).get("to_place");
    }

    private record NestedBlock(JsonElement provider, Set<String> whitelist) {
    }
}
