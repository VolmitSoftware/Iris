package art.arcane.iris.pack.datapack.export.compile.structure;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Set;

final class StructureJson {
    private static final String START_HEIGHT = "start_height";
    private static final Set<String> JIGSAW_TYPES = Set.of("minecraft:jigsaw", "jigsaw");
    private static final Set<String> UNDERGROUND_STEPS = Set.of("underground_structures", "underground_decoration", "strongholds");
    private static final List<String> RANGE_BOUNDS = List.of("min_inclusive", "max_inclusive");

    private StructureJson() {
    }

    static boolean jigsaw(JsonObject structure) {
        JsonElement type = structure.get("type");
        return type != null && type.isJsonPrimitive() && JIGSAW_TYPES.contains(type.getAsString());
    }

    static boolean underground(JsonObject structure) {
        JsonElement step = structure.get("step");
        return step != null && step.isJsonPrimitive() && UNDERGROUND_STEPS.contains(step.getAsString());
    }

    static void scope(JsonObject structure, String biomeTag) {
        structure.addProperty("biomes", "#" + biomeTag);
    }

    static boolean shiftStartHeight(JsonObject structure, int offset) {
        JsonElement height = structure.get(START_HEIGHT);
        if (height == null) {
            return false;
        }
        JsonElement shifted = height.deepCopy();
        if (!shift(shifted, offset)) {
            return false;
        }
        structure.add(START_HEIGHT, shifted);
        return true;
    }

    static void band(JsonObject structure, int minimum, int maximum) {
        JsonObject height = new JsonObject();
        height.addProperty("type", "minecraft:uniform");
        height.add("min_inclusive", absolute(minimum));
        height.add("max_inclusive", absolute(maximum));
        structure.add(START_HEIGHT, height);
        structure.remove("project_start_to_heightmap");
    }

    private static JsonObject absolute(int y) {
        JsonObject anchor = new JsonObject();
        anchor.addProperty("absolute", y);
        return anchor;
    }

    private static boolean shift(JsonElement height, int offset) {
        if (!height.isJsonObject()) {
            return false;
        }
        JsonObject provider = height.getAsJsonObject();
        if (provider.has("absolute")) {
            return add(provider, "absolute", offset);
        }
        if (provider.has("above_bottom")) {
            return add(provider, "above_bottom", offset);
        }
        if (provider.has("below_top")) {
            return add(provider, "below_top", -offset);
        }
        JsonElement type = provider.get("type");
        if (type == null || !type.isJsonPrimitive()) {
            return false;
        }
        return switch (type.getAsString()) {
            case "minecraft:constant", "constant" -> provider.has("value") && shift(provider.get("value"), offset);
            case "minecraft:uniform", "uniform", "minecraft:biased_to_bottom", "biased_to_bottom",
                 "minecraft:very_biased_to_bottom", "very_biased_to_bottom", "minecraft:trapezoid", "trapezoid" -> shiftBounds(provider, offset);
            case "minecraft:weighted_list", "weighted_list" -> shiftWeighted(provider, offset);
            default -> false;
        };
    }

    private static boolean shiftBounds(JsonObject provider, int offset) {
        for (String bound : RANGE_BOUNDS) {
            if (!provider.has(bound) || !shift(provider.get(bound), offset)) {
                return false;
            }
        }
        return true;
    }

    private static boolean shiftWeighted(JsonObject provider, int offset) {
        JsonElement distribution = provider.get("distribution");
        if (distribution == null || !distribution.isJsonArray()) {
            return false;
        }
        for (JsonElement entry : distribution.getAsJsonArray()) {
            if (!entry.isJsonObject() || !entry.getAsJsonObject().has("data") || !shift(entry.getAsJsonObject().get("data"), offset)) {
                return false;
            }
        }
        return true;
    }

    private static boolean add(JsonObject anchor, String key, int offset) {
        JsonElement value = anchor.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return false;
        }
        anchor.addProperty(key, value.getAsInt() + offset);
        return true;
    }
}
