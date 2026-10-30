package art.arcane.iris.pack.datapack.export.compile.decoration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Collection;
import java.util.List;

public final class Placements {
    public static final String WORLD_SURFACE = "WORLD_SURFACE_WG";
    public static final String OCEAN_FLOOR = "OCEAN_FLOOR_WG";
    private static final int CHUNK_EDGE = 15;
    private static final int WEIGHT_SCALE = 1000;
    private static final int[][] CARDINALS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private Placements() {
    }

    public static List<JsonObject> everyColumn() {
        JsonObject cuboid = typed("minecraft:cuboid");
        cuboid.addProperty("xz_size", CHUNK_EDGE);
        cuboid.addProperty("y_size", 1);
        return List.of(cuboid, filter(insideWorldBounds(0, -1, 0)));
    }

    public static JsonObject heightmap(String heightmap) {
        JsonObject json = typed("minecraft:heightmap");
        json.addProperty("heightmap", heightmap);
        return json;
    }

    public static JsonObject verticalOffset(int y) {
        JsonObject json = typed("minecraft:offset");
        json.addProperty("x", 0);
        json.addProperty("y", y);
        json.addProperty("z", 0);
        return json;
    }

    public static JsonObject randomChance(double chance) {
        JsonObject json = typed("minecraft:random_chance");
        json.addProperty("chance", (float) chance);
        return json;
    }

    public static JsonObject biome() {
        return typed("minecraft:biome");
    }

    public static JsonObject inSquare() {
        return typed("minecraft:in_square");
    }

    public static JsonObject count(JsonElement count) {
        JsonObject json = typed("minecraft:count");
        json.add("count", count);
        return json;
    }

    public static JsonObject heightRange(JsonObject height) {
        JsonObject json = typed("minecraft:height_range");
        json.add("height", height);
        return json;
    }

    public static JsonObject uniformHeight(int minAboveBottom, int maxAboveBottom) {
        JsonObject json = typed("minecraft:uniform");
        json.add("min_inclusive", aboveBottom(minAboveBottom));
        json.add("max_inclusive", aboveBottom(maxAboveBottom));
        return json;
    }

    public static JsonObject trapezoidHeight(int minAboveBottom, int maxAboveBottom) {
        JsonObject json = typed("minecraft:trapezoid");
        json.add("min_inclusive", aboveBottom(minAboveBottom));
        json.add("max_inclusive", aboveBottom(maxAboveBottom));
        json.addProperty("plateau", 0);
        return json;
    }

    public static JsonObject fullHeight() {
        JsonObject bottom = new JsonObject();
        bottom.addProperty("above_bottom", 0);
        JsonObject top = new JsonObject();
        top.addProperty("below_top", 0);
        JsonObject json = typed("minecraft:uniform");
        json.add("min_inclusive", bottom);
        json.add("max_inclusive", top);
        return json;
    }

    public static JsonObject surfaceRelative(String heightmap, int maxInclusive) {
        JsonObject json = typed("minecraft:surface_relative_threshold_filter");
        json.addProperty("heightmap", heightmap);
        json.addProperty("max_inclusive", maxInclusive);
        return json;
    }

    public static JsonObject environmentScanUp(int maxSteps) {
        JsonObject json = typed("minecraft:environment_scan");
        json.addProperty("direction_of_search", "up");
        json.addProperty("max_steps", maxSteps);
        json.add("target_condition", solid(0, 0, 0));
        json.add("allowed_search_condition", air(0, 0, 0));
        return json;
    }

    public static JsonObject filter(JsonObject predicate) {
        JsonObject json = typed("minecraft:block_predicate_filter");
        json.add("predicate", predicate);
        return json;
    }

    public static JsonObject uniform(int min, int max) {
        JsonObject json = typed("minecraft:uniform");
        json.addProperty("min_inclusive", min);
        json.addProperty("max_inclusive", max);
        return json;
    }

    public static JsonElement intRange(int min, int max) {
        return min == max ? new JsonPrimitive(min) : uniform(min, max);
    }

    public static JsonElement meanCount(double mean) {
        double clamped = Math.max(0.0, mean);
        int lower = (int) Math.floor(clamped);
        int upperWeight = (int) Math.round((clamped - lower) * WEIGHT_SCALE);
        if (upperWeight == 0) {
            return new JsonPrimitive(lower);
        }
        if (upperWeight == WEIGHT_SCALE) {
            return new JsonPrimitive(lower + 1);
        }
        JsonArray distribution = new JsonArray(2);
        distribution.add(weighted(lower, WEIGHT_SCALE - upperWeight));
        distribution.add(weighted(lower + 1, upperWeight));
        JsonObject json = typed("minecraft:weighted_list");
        json.add("distribution", distribution);
        return json;
    }

    public static JsonObject solid(int x, int y, int z) {
        return offset(typed("minecraft:solid"), x, y, z);
    }

    public static JsonObject air(int x, int y, int z) {
        JsonObject json = typed("minecraft:matching_block_tag");
        json.addProperty("tag", "minecraft:air");
        return offset(json, x, y, z);
    }

    public static JsonObject water(int x, int y, int z) {
        JsonArray fluids = new JsonArray(2);
        fluids.add("minecraft:water");
        fluids.add("minecraft:flowing_water");
        JsonObject json = typed("minecraft:matching_fluids");
        json.add("fluids", fluids);
        return offset(json, x, y, z);
    }

    public static JsonObject matchingBlocks(Collection<String> blocks, int x, int y, int z) {
        JsonArray ids = new JsonArray(blocks.size());
        for (String block : blocks) {
            ids.add(block);
        }
        JsonObject json = typed("minecraft:matching_blocks");
        json.add("blocks", ids);
        return offset(json, x, y, z);
    }

    public static JsonObject matchingBiomes(Collection<String> biomeIds) {
        JsonArray ids = new JsonArray(biomeIds.size());
        for (String id : biomeIds) {
            ids.add(id);
        }
        JsonObject json = typed("minecraft:matching_biomes");
        json.add("biomes", ids);
        return json;
    }

    public static JsonObject wouldSurvive(JsonObject state) {
        JsonObject json = typed("minecraft:would_survive");
        json.add("state", state);
        return json;
    }

    public static JsonObject insideWorldBounds(int x, int y, int z) {
        return offset(typed("minecraft:inside_world_bounds"), x, y, z);
    }

    public static JsonObject not(JsonObject predicate) {
        JsonObject json = typed("minecraft:not");
        json.add("predicate", predicate);
        return json;
    }

    public static JsonObject allOf(List<JsonObject> predicates) {
        return predicates.size() == 1 ? predicates.getFirst() : combined("minecraft:all_of", predicates);
    }

    public static JsonObject anyOf(List<JsonObject> predicates) {
        return predicates.size() == 1 ? predicates.getFirst() : combined("minecraft:any_of", predicates);
    }

    public static JsonObject steeperThan(int rise, int groundOffset) {
        JsonObject[] directions = new JsonObject[CARDINALS.length * 2];
        for (int index = 0; index < CARDINALS.length; index++) {
            int[] cardinal = CARDINALS[index];
            directions[index * 2] = solid(cardinal[0] * 3, groundOffset + rise, cardinal[1] * 3);
            directions[index * 2 + 1] = not(solid(cardinal[0] * 3, groundOffset - rise + 1, cardinal[1] * 3));
        }
        return anyOf(List.of(directions));
    }

    public static JsonObject dropAtLeast(int drop) {
        JsonObject[] directions = new JsonObject[CARDINALS.length];
        for (int index = 0; index < CARDINALS.length; index++) {
            directions[index] = not(solid(CARDINALS[index][0], 1 - drop, CARDINALS[index][1]));
        }
        return anyOf(List.of(directions));
    }

    public static JsonObject exposed() {
        JsonObject[] directions = new JsonObject[CARDINALS.length];
        for (int index = 0; index < CARDINALS.length; index++) {
            directions[index] = not(solid(CARDINALS[index][0], 0, CARDINALS[index][1]));
        }
        return allOf(List.of(solid(0, 0, 0), anyOf(List.of(directions))));
    }

    public static JsonObject stepUpBeside() {
        JsonObject[] directions = new JsonObject[CARDINALS.length];
        for (int index = 0; index < CARDINALS.length; index++) {
            int[] cardinal = CARDINALS[index];
            directions[index] = allOf(List.of(solid(cardinal[0], 0, cardinal[1]), not(solid(cardinal[0], 1, cardinal[1]))));
        }
        return anyOf(List.of(directions));
    }

    public static JsonObject typed(String type) {
        JsonObject json = new JsonObject();
        json.addProperty("type", type);
        return json;
    }

    private static JsonObject combined(String type, List<JsonObject> predicates) {
        JsonArray list = new JsonArray(predicates.size());
        for (JsonObject predicate : predicates) {
            list.add(predicate);
        }
        JsonObject json = typed(type);
        json.add("predicates", list);
        return json;
    }

    private static JsonObject aboveBottom(int value) {
        JsonObject json = new JsonObject();
        json.addProperty("above_bottom", value);
        return json;
    }

    private static JsonObject offset(JsonObject predicate, int x, int y, int z) {
        if (x == 0 && y == 0 && z == 0) {
            return predicate;
        }
        JsonArray offset = new JsonArray(3);
        offset.add(x);
        offset.add(y);
        offset.add(z);
        predicate.add("offset", offset);
        return predicate;
    }

    private static JsonObject weighted(int data, int weight) {
        JsonObject json = new JsonObject();
        json.addProperty("data", data);
        json.addProperty("weight", weight);
        return json;
    }
}
