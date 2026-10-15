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
import art.arcane.iris.pack.ingest.catalog.CatalogDimensionType;
import art.arcane.iris.pack.ingest.catalog.WorldgenCatalogIndex;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class IngestPackModel {
    private static final int DEFAULT_MIN_Y = -64;
    private static final int DEFAULT_MAX_Y = 320;
    private static final String DEFAULT_DERIVATIVE = "minecraft:the_void";
    private static final Set<String> PLANTABLE_BLOCKS = Set.of("minecraft:grass_block", "minecraft:dirt", "minecraft:coarse_dirt",
            "minecraft:podzol", "minecraft:rooted_dirt", "minecraft:mycelium", "minecraft:moss_block", "minecraft:pale_moss_block",
            "minecraft:mud", "minecraft:muddy_mangrove_roots", "minecraft:farmland");

    private final IngestPack pack;
    private final WorldgenCatalogIndex index;
    private final String dimensionPath;
    private final String environment;
    private final String borrowedDimensionType;
    private final int packMinY;
    private final int packMaxY;
    private final List<RegionProfile> regions;
    private final Map<String, String> reachableBiomes;
    private final Map<String, Double> plantableShares = new HashMap<>();

    private IngestPackModel(IngestPack pack, WorldgenCatalogIndex index, String dimensionPath, JsonObject dimension,
                            int[] heightRange, List<RegionProfile> regions, Map<String, String> reachableBiomes) {
        this.pack = pack;
        this.index = index;
        this.dimensionPath = dimensionPath;
        this.environment = text(dimension.get("environment"), "NORMAL").toUpperCase(Locale.ROOT);
        this.borrowedDimensionType = text(dimension.get("borrowedDimensionType"), "");
        this.packMinY = heightRange[0];
        this.packMaxY = heightRange[1];
        this.regions = regions;
        this.reachableBiomes = reachableBiomes;
    }

    static IngestPackModel load(IngestPack pack, String dimensionKey, WorldgenCatalogIndex index) throws IOException {
        String dimensionPath = IngestPack.dimensionPath(dimensionKey);
        JsonObject dimension = pack.require(dimensionPath);
        Map<String, String> reachable = new LinkedHashMap<>();
        List<String> regionKeys = IngestJson.strings(dimension.get("regions"));
        List<JsonObject> regionJson = new ArrayList<>(regionKeys.size());
        List<String> loadedRegionKeys = new ArrayList<>(regionKeys.size());
        for (String regionKey : regionKeys) {
            Optional<JsonObject> region = pack.find(IngestPack.regionPath(regionKey));
            if (region.isPresent()) {
                regionJson.add(region.get());
                loadedRegionKeys.add(regionKey);
            }
        }
        collectReachable(pack, regionJson, reachable);
        List<RegionProfile> profiles = new ArrayList<>(regionJson.size());
        for (int position = 0; position < regionJson.size(); position++) {
            profiles.add(profile(loadedRegionKeys.get(position), regionJson.get(position), reachable, index));
        }
        return new IngestPackModel(pack, index, dimensionPath, dimension, heightRange(pack, dimension),
                Collections.unmodifiableList(profiles), Collections.unmodifiableMap(reachable));
    }

    IngestPack pack() {
        return pack;
    }

    String dimensionPath() {
        return dimensionPath;
    }

    String environment() {
        return environment;
    }

    int packMinY() {
        return packMinY;
    }

    List<RegionProfile> regions() {
        return regions;
    }

    Map<String, String> reachableBiomes() {
        return reachableBiomes;
    }

    IngestClimate biomeClimate(String biomeKey) {
        return climateReference(index, reachableBiomes.get(biomeKey)).map(IngestClimate::of).orElse(null);
    }

    double plantableShare(String biomeKey) throws IOException {
        Double cached = plantableShares.get(biomeKey);
        if (cached != null) {
            return cached;
        }
        double share = topLayerPlantableShare(biomeKey);
        plantableShares.put(biomeKey, share);
        return share;
    }

    private double topLayerPlantableShare(String biomeKey) throws IOException {
        Optional<JsonObject> biome = pack.find(IngestPack.biomePath(biomeKey));
        JsonElement layers = biome.map((JsonObject value) -> value.get("layers")).orElse(null);
        if (layers == null || !layers.isJsonArray() || layers.getAsJsonArray().isEmpty()) {
            return 1D;
        }
        JsonObject top = pack.snippet(layers.getAsJsonArray().get(0), "biome-palette");
        JsonElement palette = top == null ? null : top.get("palette");
        if (palette == null || !palette.isJsonArray() || palette.getAsJsonArray().isEmpty()) {
            return 1D;
        }
        double total = 0D;
        double plantable = 0D;
        for (JsonElement entry : palette.getAsJsonArray()) {
            if (!entry.isJsonObject() || !entry.getAsJsonObject().has("block")) {
                continue;
            }
            double weight = Math.max(0D, number(entry.getAsJsonObject().get("weight"), 1D));
            total += weight;
            if (PLANTABLE_BLOCKS.contains(VanillaJson.namespaced(entry.getAsJsonObject().get("block").getAsString()))) {
                plantable += weight;
            }
        }
        return total <= 0D ? 1D : plantable / total;
    }

    Set<String> biomeCategories(String biomeKey) {
        return climateReference(index, reachableBiomes.get(biomeKey)).map((CatalogBiome biome) -> BiomeCategories.of(biome.tags())).orElse(Set.of());
    }

    private static Optional<CatalogBiome> climateReference(WorldgenCatalogIndex index, String derivative) {
        if (derivative == null) {
            return Optional.empty();
        }
        Optional<CatalogBiome> biome = index.biome(derivative);
        return biome.isPresent() && !BiomeCategories.isVoid(biome.get().tags()) && !biome.get().key().equals(DEFAULT_DERIVATIVE)
                ? biome
                : Optional.empty();
    }

    VanillaHeights.VerticalBounds verticalBounds() {
        if (!borrowedDimensionType.isEmpty()) {
            Optional<CatalogDimensionType> borrowed = index.dimensionType(borrowedDimensionType);
            if (borrowed.isPresent() && borrowed.get().height() > 0) {
                return new VanillaHeights.VerticalBounds(borrowed.get().minY(), borrowed.get().height());
            }
        }
        return new VanillaHeights.VerticalBounds(packMinY, Math.max(1, packMaxY - packMinY));
    }

    private static RegionProfile profile(String key, JsonObject region, Map<String, String> reachable, WorldgenCatalogIndex index) {
        Map<BiomeRole, List<String>> pools = new EnumMap<>(BiomeRole.class);
        for (BiomeRole role : BiomeRole.values()) {
            List<String> pool = new ArrayList<>();
            for (String biome : IngestJson.strings(region.get(role.poolField()))) {
                if (reachable.containsKey(biome) && !pool.contains(biome)) {
                    pool.add(biome);
                }
            }
            pools.put(role, Collections.unmodifiableList(pool));
        }
        List<IngestClimate> landClimates = new ArrayList<>();
        for (String biome : pools.get(BiomeRole.LAND)) {
            Optional<CatalogBiome> catalogBiome = climateReference(index, reachable.get(biome));
            catalogBiome.ifPresent(value -> landClimates.add(IngestClimate.of(value)));
        }
        return new RegionProfile(key, IngestClimate.average(landClimates), (int) number(region.get("rarity"), 1D), pools);
    }

    private static void collectReachable(IngestPack pack, List<JsonObject> regions, Map<String, String> reachable) throws IOException {
        ArrayDeque<String> queue = new ArrayDeque<>();
        for (JsonObject region : regions) {
            for (BiomeRole role : BiomeRole.values()) {
                queue.addAll(IngestJson.strings(region.get(role.poolField())));
            }
        }
        while (!queue.isEmpty()) {
            String biomeKey = queue.removeFirst();
            if (reachable.containsKey(biomeKey)) {
                continue;
            }
            Optional<JsonObject> biome = pack.find(IngestPack.biomePath(biomeKey));
            if (biome.isEmpty()) {
                continue;
            }
            JsonElement derivative = biome.get().get("derivative");
            String derivativeKey = derivative != null && derivative.isJsonPrimitive()
                    ? VanillaJson.namespaced(derivative.getAsString())
                    : DEFAULT_DERIVATIVE;
            reachable.put(biomeKey, derivativeKey);
            queue.addAll(IngestJson.strings(biome.get().get("children")));
        }
    }

    private static int[] heightRange(IngestPack pack, JsonObject dimension) throws IOException {
        JsonObject range = pack.snippet(dimension.get("dimensionHeight"), "range");
        if (range == null) {
            return new int[]{DEFAULT_MIN_Y, DEFAULT_MAX_Y};
        }
        return new int[]{(int) number(range.get("min"), DEFAULT_MIN_Y), (int) number(range.get("max"), DEFAULT_MAX_Y)};
    }

    private static String text(JsonElement value, String fallback) {
        return value != null && value.isJsonPrimitive() ? value.getAsString().trim() : fallback;
    }

    private static double number(JsonElement value, double fallback) {
        if (value == null || !value.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.getAsString().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    record RegionProfile(String key, IngestClimate climate, int rarity, Map<BiomeRole, List<String>> pools) {
        List<String> pool(BiomeRole role) {
            return pools.get(role);
        }
    }
}
