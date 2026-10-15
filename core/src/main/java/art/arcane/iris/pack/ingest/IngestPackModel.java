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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class IngestPackModel {
    private static final int DEFAULT_MIN_Y = -64;
    private static final int DEFAULT_MAX_Y = 320;
    private static final String DEFAULT_DERIVATIVE = "minecraft:the_void";

    private final IngestPack pack;
    private final WorldgenCatalogIndex index;
    private final String dimensionPath;
    private final String environment;
    private final int packMinY;
    private final int packMaxY;
    private final List<RegionProfile> regions;
    private final Map<String, String> reachableBiomes;

    private IngestPackModel(IngestPack pack, WorldgenCatalogIndex index, String dimensionPath, String environment,
                            int[] heightRange, List<RegionProfile> regions, Map<String, String> reachableBiomes) {
        this.pack = pack;
        this.index = index;
        this.dimensionPath = dimensionPath;
        this.environment = environment;
        this.packMinY = heightRange[0];
        this.packMaxY = heightRange[1];
        this.regions = regions;
        this.reachableBiomes = reachableBiomes;
    }

    static IngestPackModel load(IngestPack pack, String dimensionKey, WorldgenCatalogIndex index) throws IOException {
        String dimensionPath = IngestPack.dimensionPath(dimensionKey);
        JsonObject dimension = pack.require(dimensionPath);
        JsonElement environmentValue = dimension.get("environment");
        String environment = environmentValue != null && environmentValue.isJsonPrimitive()
                ? environmentValue.getAsString().trim().toUpperCase(Locale.ROOT)
                : "NORMAL";
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
        return new IngestPackModel(pack, index, dimensionPath, environment, heightRange(pack, dimension),
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
        String derivative = reachableBiomes.get(biomeKey);
        return derivative == null ? null : index.biome(derivative).map(IngestClimate::of).orElse(null);
    }

    VanillaHeights.VerticalBounds verticalBounds() {
        String typeKey = switch (environment) {
            case "NORMAL" -> "minecraft:overworld";
            case "NETHER" -> "minecraft:the_nether";
            case "THE_END" -> "minecraft:the_end";
            default -> "";
        };
        Optional<CatalogDimensionType> type = index.dimensionType(typeKey);
        if (type.isPresent() && type.get().height() > 0) {
            return new VanillaHeights.VerticalBounds(type.get().minY(), type.get().height());
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
            Optional<CatalogBiome> catalogBiome = index.biome(reachable.get(biome));
            catalogBiome.ifPresent(value -> landClimates.add(IngestClimate.of(value)));
        }
        return new RegionProfile(key, IngestClimate.average(landClimates), pools);
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

    record RegionProfile(String key, IngestClimate climate, Map<BiomeRole, List<String>> pools) {
        List<String> pool(BiomeRole role) {
            return pools.get(role);
        }
    }
}
