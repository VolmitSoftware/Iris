package art.arcane.iris.pack.datapack.export.compile.surface;

import art.arcane.iris.pack.datapack.export.VanillaBlockState;
import art.arcane.iris.pack.datapack.export.compile.WorldgenSyntax;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Collection;
import java.util.List;

final class MaterialRules {
    static final int MIN_ANCHOR = -2032;
    static final int MAX_ANCHOR = 2031;

    private MaterialRules() {
    }

    static JsonObject sequence(List<JsonObject> rules) {
        JsonArray sequence = new JsonArray(rules.size());
        for (JsonObject rule : rules) {
            sequence.add(rule);
        }
        JsonObject json = typed("minecraft:sequence");
        json.add("sequence", sequence);
        return json;
    }

    static JsonObject condition(JsonObject condition, JsonObject rule) {
        JsonObject json = typed("minecraft:condition");
        json.add("if_true", condition);
        json.add("then_run", rule);
        return json;
    }

    static JsonObject guarded(List<JsonObject> conditions, JsonObject rule) {
        JsonObject guarded = rule;
        for (int index = conditions.size() - 1; index >= 0; index--) {
            guarded = condition(conditions.get(index), guarded);
        }
        return guarded;
    }

    static JsonObject block(WorldgenSyntax syntax, VanillaBlockState state) {
        JsonObject json = typed("minecraft:block");
        json.add("result_state", syntax.blockState(state));
        return json;
    }

    static JsonObject stoneDepth(int offset, int secondaryDepthRange, boolean ceiling) {
        JsonObject json = typed("minecraft:stone_depth");
        json.addProperty("offset", offset);
        json.addProperty("add_surface_depth", false);
        json.addProperty("secondary_depth_range", secondaryDepthRange);
        json.addProperty("surface_type", ceiling ? "ceiling" : "floor");
        return json;
    }

    static JsonObject biome(Collection<String> biomeIds) {
        JsonArray ids = new JsonArray(biomeIds.size());
        for (String id : biomeIds) {
            ids.add(id);
        }
        JsonObject json = typed("minecraft:biome");
        json.add("biome_is", ids);
        return json;
    }

    static JsonObject verticalGradient(String randomName, int trueAtAndBelow, int falseAtAndAbove) {
        JsonObject json = typed("minecraft:vertical_gradient");
        json.addProperty("random_name", randomName);
        json.add("true_at_and_below", absolute(trueAtAndBelow));
        json.add("false_at_and_above", absolute(falseAtAndAbove));
        return json;
    }

    static JsonObject yAbove(int anchor) {
        JsonObject json = typed("minecraft:y_above");
        json.add("anchor", absolute(anchor));
        json.addProperty("surface_depth_multiplier", 0);
        json.addProperty("add_stone_depth", false);
        return json;
    }

    static JsonObject notUnderwater() {
        JsonObject json = typed("minecraft:water");
        json.addProperty("offset", -1);
        json.addProperty("surface_depth_multiplier", 0);
        json.addProperty("add_stone_depth", false);
        return json;
    }

    static JsonObject not(JsonObject condition) {
        JsonObject json = typed("minecraft:not");
        json.add("invert", condition);
        return json;
    }

    static JsonObject noiseThreshold(String noise, double min, double max) {
        JsonObject json = typed("minecraft:noise_threshold");
        json.addProperty("noise", noise);
        json.addProperty("min_threshold", min);
        json.addProperty("max_threshold", max);
        json.addProperty("is_3d", true);
        return json;
    }

    static JsonObject oreVein(WorldgenSyntax syntax, OreVeinBlocks blocks, JsonElement density) {
        JsonObject json = typed("minecraft:ore_vein");
        json.add("ore_block", syntax.blockState(blocks.ore()));
        json.add("raw_ore_block", syntax.blockState(blocks.rawOre()));
        json.add("filler_block", syntax.blockState(blocks.filler()));
        json.addProperty("raw_ore_chance", blocks.rawOreChance());
        json.add("density", density);
        json.addProperty("richness", blocks.richness());
        json.addProperty("filler_gap", -1.0);
        return json;
    }

    static JsonObject rangeChoice(JsonElement input, double minInclusive, double maxExclusive) {
        JsonObject json = typed("minecraft:range_choice");
        json.add("input", input);
        json.addProperty("min_inclusive", minInclusive);
        json.addProperty("max_exclusive", maxExclusive);
        json.addProperty("when_in_range", 1.0);
        json.addProperty("when_out_of_range", 0.0);
        return json;
    }

    static int clampAnchor(long y) {
        return (int) Math.max(MIN_ANCHOR, Math.min(MAX_ANCHOR, y));
    }

    private static JsonObject absolute(int y) {
        JsonObject anchor = new JsonObject();
        anchor.addProperty("absolute", clampAnchor(y));
        return anchor;
    }

    private static JsonObject typed(String type) {
        JsonObject json = new JsonObject();
        json.addProperty("type", type);
        return json;
    }

    record OreVeinBlocks(VanillaBlockState filler, VanillaBlockState ore, VanillaBlockState rawOre, float richness, float rawOreChance) {
    }
}
