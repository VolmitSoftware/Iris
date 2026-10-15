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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashSet;
import java.util.Set;

final class VanillaPlacementChain {
    private static final Set<String> SURFACE_HEIGHTMAPS = Set.of("MOTION_BLOCKING", "MOTION_BLOCKING_NO_LEAVES", "WORLD_SURFACE", "WORLD_SURFACE_WG");
    private static final Set<String> LEAF_BLOCKED_HEIGHTMAPS = Set.of("MOTION_BLOCKING", "WORLD_SURFACE", "WORLD_SURFACE_WG");
    private static final int NOISE_SAMPLES = 201;

    private final Set<String> whitelist = new LinkedHashSet<>();
    private double expectedPlacements = 1D;
    private double surfaceHitChance = 1D;
    private boolean surface;
    private boolean leafBlocked;

    private VanillaPlacementChain() {
    }

    static VanillaPlacementChain surfaceChain(JsonArray placement) throws IngestConversionException {
        VanillaPlacementChain chain = new VanillaPlacementChain();
        chain.read(placement);
        if (!chain.surface) {
            throw new IngestConversionException("placement does not snap to a surface heightmap");
        }
        return chain;
    }

    static Set<String> filterWhitelist(JsonArray placement) throws IngestConversionException {
        VanillaPlacementChain chain = new VanillaPlacementChain();
        for (JsonElement modifier : placement) {
            JsonObject object = VanillaJson.object(modifier, "placement modifier");
            String type = VanillaJson.type(object, "type");
            if (!type.equals("minecraft:block_predicate_filter")) {
                throw new IngestConversionException("unsupported nested placement modifier " + type);
            }
            chain.collectWhitelist(object.get("predicate"));
        }
        return chain.whitelist;
    }

    double expectedPlacements() {
        return expectedPlacements;
    }

    double surfaceHitChance() {
        return surfaceHitChance;
    }

    boolean leafBlocked() {
        return leafBlocked;
    }

    static double expectedCount(JsonArray placement) throws IngestConversionException {
        double expected = 1D;
        for (JsonElement modifier : placement) {
            JsonObject object = VanillaJson.object(modifier, "placement modifier");
            switch (VanillaJson.type(object, "type")) {
                case "minecraft:count", "minecraft:count_on_every_layer" -> expected *= Math.max(0D, VanillaIntProviders.stats(object.get("count")).mean());
                case "minecraft:rarity_filter" -> expected /= Math.max(1D, VanillaJson.number(object.get("chance"), "rarity_filter.chance"));
                case "minecraft:random_chance" -> expected *= clampProbability(VanillaJson.number(object.get("chance"), "random_chance.chance"));
                case "minecraft:noise_threshold_count" -> expected *= (VanillaJson.number(object.get("below_noise"), "noise_threshold_count.below_noise")
                        + VanillaJson.number(object.get("above_noise"), "noise_threshold_count.above_noise")) / 2D;
                case "minecraft:noise_based_count" -> expected *= noiseBasedMean(object);
                default -> {
                }
            }
        }
        return expected;
    }

    Set<String> whitelist() {
        return whitelist;
    }

    private void read(JsonArray placement) throws IngestConversionException {
        for (JsonElement modifier : placement) {
            JsonObject object = VanillaJson.object(modifier, "placement modifier");
            String type = VanillaJson.type(object, "type");
            switch (type) {
                case "minecraft:count" -> expectedPlacements *= Math.max(0D, VanillaIntProviders.stats(object.get("count")).mean());
                case "minecraft:rarity_filter" -> expectedPlacements /= Math.max(1D, VanillaJson.number(object.get("chance"), "rarity_filter.chance"));
                case "minecraft:random_chance" -> expectedPlacements *= clampProbability(VanillaJson.number(object.get("chance"), "random_chance.chance"));
                case "minecraft:noise_threshold_count" -> expectedPlacements *= (VanillaJson.number(object.get("below_noise"), "noise_threshold_count.below_noise")
                        + VanillaJson.number(object.get("above_noise"), "noise_threshold_count.above_noise")) / 2D;
                case "minecraft:noise_based_count" -> expectedPlacements *= noiseBasedMean(object);
                case "minecraft:in_square", "minecraft:biome" -> {
                }
                case "minecraft:heightmap" -> readHeightmap(object);
                case "minecraft:offset" -> readVerticalOffset(object.get("y"));
                case "minecraft:random_offset" -> readVerticalOffset(object.get("y_spread"));
                case "minecraft:block_predicate_filter" -> collectWhitelist(object.get("predicate"));
                default -> throw new IngestConversionException("unsupported placement modifier " + (type.isEmpty() ? object.toString() : type));
            }
        }
    }

    private void readHeightmap(JsonObject object) throws IngestConversionException {
        String heightmap = VanillaJson.string(object.get("heightmap"), "heightmap.heightmap");
        if (!SURFACE_HEIGHTMAPS.contains(heightmap)) {
            throw new IngestConversionException("heightmap " + heightmap + " is not a dry surface placement");
        }
        surface = true;
        leafBlocked = LEAF_BLOCKED_HEIGHTMAPS.contains(heightmap);
    }

    private void readVerticalOffset(JsonElement provider) throws IngestConversionException {
        if (!surface || provider == null || provider.isJsonNull()) {
            return;
        }
        surfaceHitChance *= VanillaIntProviders.probabilityOf(provider, 0);
    }

    private void collectWhitelist(JsonElement predicate) throws IngestConversionException {
        if (predicate == null || !predicate.isJsonObject()) {
            return;
        }
        JsonObject object = predicate.getAsJsonObject();
        String type = VanillaJson.type(object, "type");
        if (type.equals("minecraft:all_of") && object.get("predicates") != null && object.get("predicates").isJsonArray()) {
            for (JsonElement inner : object.getAsJsonArray("predicates")) {
                collectWhitelist(inner);
            }
            return;
        }
        if (!type.equals("minecraft:matching_blocks") || !isBelowOffset(object.get("offset"))) {
            return;
        }
        JsonElement blocks = object.get("blocks");
        if (blocks != null && blocks.isJsonArray()) {
            for (JsonElement block : blocks.getAsJsonArray()) {
                whitelist.add(VanillaJson.namespaced(VanillaJson.string(block, "matching_blocks.blocks")));
            }
            return;
        }
        whitelist.add(VanillaJson.namespaced(VanillaJson.string(blocks, "matching_blocks.blocks")));
    }

    private static boolean isBelowOffset(JsonElement offset) {
        if (offset == null || !offset.isJsonArray() || offset.getAsJsonArray().size() != 3) {
            return false;
        }
        JsonArray values = offset.getAsJsonArray();
        return values.get(0).getAsInt() == 0 && values.get(1).getAsInt() == -1 && values.get(2).getAsInt() == 0;
    }

    private static double noiseBasedMean(JsonObject object) throws IngestConversionException {
        double ratio = VanillaJson.number(object.get("noise_to_count_ratio"), "noise_based_count.noise_to_count_ratio");
        double offset = VanillaJson.number(object, "noise_offset", 0D, "noise_based_count");
        double total = 0D;
        for (int sample = 0; sample < NOISE_SAMPLES; sample++) {
            double noise = -1D + 2D * sample / (NOISE_SAMPLES - 1D);
            total += Math.max(0D, Math.ceil((noise + offset) * ratio));
        }
        return total / NOISE_SAMPLES;
    }

    private static double clampProbability(double value) {
        return Math.max(0D, Math.min(1D, value));
    }
}
