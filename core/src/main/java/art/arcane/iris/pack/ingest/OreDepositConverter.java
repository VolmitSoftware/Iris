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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

final class OreDepositConverter {
    static final Set<String> ORE_TYPES = Set.of("minecraft:ore", "minecraft:scattered_ore");
    private static final int MAX_PER_CHUNK = 2048;
    private static final int MAX_SIZE = 8192;

    private OreDepositConverter() {
    }

    static boolean handles(CatalogPlacedFeature feature) {
        return ORE_TYPES.contains(VanillaJson.namespaced(feature.featureType()));
    }

    static List<JsonObject> convert(CatalogPlacedFeature feature, VanillaHeights.VerticalBounds bounds, int packMinY) throws IngestConversionException {
        JsonObject config = VanillaJson.featureConfig(feature.config());
        Placement placement = placement(VanillaJson.placement(feature.placement()), bounds);
        int size = VanillaJson.integer(config.get("size"), "ore size");
        if (size <= 0) {
            throw new IngestConversionException("ore size " + size + " never places blocks");
        }
        double discard = VanillaJson.number(config, "discard_chance_on_air_exposure", 0D, "ore");
        String shape = VanillaJson.namespaced(feature.featureType()).equals("minecraft:scattered_ore") ? "VANILLA_SCATTERED" : "VANILLA_ELLIPSOID";
        List<JsonObject> deposits = new ArrayList<>();
        for (Target target : targets(VanillaJson.array(config.get("targets"), "ore targets"))) {
            JsonObject deposit = new JsonObject();
            deposit.addProperty("minHeight", placement.band().min() - packMinY);
            deposit.addProperty("maxHeight", placement.band().max() - packMinY);
            deposit.addProperty("heightDistribution", placement.band().distribution());
            deposit.addProperty("surfaceClearance", 0);
            deposit.addProperty("minPerChunk", placement.minPerChunk());
            deposit.addProperty("maxPerChunk", placement.maxPerChunk());
            if (placement.spawnChance() < 1D) {
                deposit.addProperty("spawnChance", IngestJson.round(placement.spawnChance()));
            }
            deposit.addProperty("minSize", Math.min(size, MAX_SIZE));
            deposit.addProperty("maxSize", Math.min(size, MAX_SIZE));
            deposit.addProperty("shape", shape);
            if (discard > 0D) {
                deposit.addProperty("discardChanceOnAirExposure", IngestJson.round(Math.min(1D, discard)));
            }
            if (!target.hosts().any()) {
                deposit.add("replaceableBlocks", IngestJson.stringArray(target.hosts().blocks()));
            }
            JsonArray palette = new JsonArray();
            palette.add(target.block());
            deposit.add("palette", palette);
            deposits.add(deposit);
        }
        if (deposits.isEmpty()) {
            throw new IngestConversionException("ore has no reachable targets");
        }
        return deposits;
    }

    private static List<Target> targets(JsonArray targets) throws IngestConversionException {
        List<Target> result = new ArrayList<>(targets.size());
        SortedSet<String> claimed = new TreeSet<>();
        for (JsonElement entry : targets) {
            JsonObject target = VanillaJson.object(entry, "ore target");
            JsonObject block = VanillaBlockStates.irisBlock(target.get("state"));
            VanillaRuleTests.HostSet hosts = VanillaRuleTests.hosts(target.get("target"));
            if (hosts.any()) {
                if (!claimed.isEmpty()) {
                    throw new IngestConversionException("ore target accepting any block follows restricted targets");
                }
                result.add(new Target(block, hosts));
                return result;
            }
            SortedSet<String> remaining = new TreeSet<>(hosts.blocks());
            remaining.removeAll(claimed);
            if (remaining.isEmpty()) {
                continue;
            }
            claimed.addAll(remaining);
            result.add(new Target(block, VanillaRuleTests.HostSet.of(remaining)));
        }
        return result;
    }

    private static Placement placement(JsonArray modifiers, VanillaHeights.VerticalBounds bounds) throws IngestConversionException {
        long minCount = 1L;
        long maxCount = 1L;
        double spawnChance = 1D;
        VanillaHeights.HeightBand band = null;
        for (JsonElement modifier : modifiers) {
            JsonObject object = VanillaJson.object(modifier, "placement modifier");
            String type = VanillaJson.type(object, "type");
            switch (type) {
                case "minecraft:count" -> {
                    VanillaIntProviders.IntStats stats = VanillaIntProviders.stats(object.get("count"));
                    minCount = Math.min(MAX_PER_CHUNK, minCount * Math.max(0, stats.min()));
                    maxCount = Math.min(MAX_PER_CHUNK, maxCount * Math.max(0, stats.max()));
                }
                case "minecraft:rarity_filter" -> spawnChance /= Math.max(1D, VanillaJson.number(object.get("chance"), "rarity_filter.chance"));
                case "minecraft:random_chance" -> spawnChance *= Math.max(0D, Math.min(1D, VanillaJson.number(object.get("chance"), "random_chance.chance")));
                case "minecraft:in_square", "minecraft:biome" -> {
                }
                case "minecraft:height_range" -> band = VanillaHeights.band(object.get("height"), bounds);
                default -> throw new IngestConversionException("unsupported ore placement modifier " + (type.isEmpty() ? object.toString() : type));
            }
        }
        if (band == null) {
            throw new IngestConversionException("ore placement has no height_range");
        }
        if (maxCount <= 0L || spawnChance <= 0D) {
            throw new IngestConversionException("ore placement never places");
        }
        return new Placement((int) minCount, (int) maxCount, spawnChance, band);
    }

    private record Target(JsonObject block, VanillaRuleTests.HostSet hosts) {
    }

    private record Placement(int minPerChunk, int maxPerChunk, double spawnChance, VanillaHeights.HeightBand band) {
    }
}
