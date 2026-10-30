package art.arcane.iris.pack.datapack.export.compile.structure;

import art.arcane.iris.structure.jigsaw.IrisJigsawConfiguration;
import art.arcane.iris.structure.jigsaw.IrisJigsawHeightmap;
import art.arcane.iris.structure.jigsaw.IrisJigsawLiquidSettings;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Locale;

final class JigsawOverrides {
    private static final String START_JIGSAW_NAME = "start_jigsaw_name";
    private static final String MAX_DISTANCE = "max_distance_from_center";
    private static final String PADDING = "dimension_padding";
    private static final String HEIGHTMAP = "project_start_to_heightmap";
    private static final String REMOVE_START_NAME = "NONE";
    private static final int MAX_HORIZONTAL_DISTANCE = 128;

    private JigsawOverrides() {
    }

    static void apply(JsonObject structure, IrisJigsawConfiguration jigsaw) {
        if (present(jigsaw.getStartPool())) {
            structure.addProperty("start_pool", TagFile.member(jigsaw.getStartPool()));
        }
        if (present(jigsaw.getStartJigsawName())) {
            if (jigsaw.getStartJigsawName().trim().equalsIgnoreCase(REMOVE_START_NAME)) {
                structure.remove(START_JIGSAW_NAME);
            } else {
                structure.addProperty(START_JIGSAW_NAME, TagFile.member(jigsaw.getStartJigsawName()));
            }
        }
        if (jigsaw.getMaxDepth() != null) {
            structure.addProperty("size", jigsaw.getMaxDepth());
        }
        if (jigsaw.getMaxDistanceHorizontal() != null || jigsaw.getMaxDistanceVertical() != null) {
            distance(structure, jigsaw.getMaxDistanceHorizontal(), jigsaw.getMaxDistanceVertical());
        }
        if (jigsaw.getUseExpansionHack() != null) {
            structure.addProperty("use_expansion_hack", jigsaw.getUseExpansionHack());
        }
        heightmap(structure, jigsaw.getProjectStartToHeightmap());
        if (jigsaw.getDimensionPaddingBottom() != null || jigsaw.getDimensionPaddingTop() != null) {
            padding(structure, jigsaw.getDimensionPaddingBottom(), jigsaw.getDimensionPaddingTop());
        }
        IrisJigsawLiquidSettings liquid = jigsaw.getLiquidSettings();
        if (liquid != null && liquid != IrisJigsawLiquidSettings.SOURCE) {
            structure.addProperty("liquid_settings", liquid.name().toLowerCase(Locale.ROOT));
        }
    }

    private static void distance(JsonObject structure, Integer horizontal, Integer vertical) {
        JsonElement current = structure.get(MAX_DISTANCE);
        Integer currentHorizontal = null;
        Integer currentVertical = null;
        if (number(current)) {
            currentHorizontal = current.getAsInt();
            currentVertical = current.getAsInt();
        } else if (current != null && current.isJsonObject()) {
            JsonObject bounds = current.getAsJsonObject();
            currentHorizontal = number(bounds.get("horizontal")) ? bounds.get("horizontal").getAsInt() : null;
            currentVertical = number(bounds.get("vertical")) ? bounds.get("vertical").getAsInt() : null;
        }
        int resolvedHorizontal = horizontal != null ? horizontal : currentHorizontal != null ? currentHorizontal : MAX_HORIZONTAL_DISTANCE;
        Integer resolvedVertical = vertical != null ? vertical : currentVertical;
        if (resolvedVertical != null && resolvedVertical == resolvedHorizontal) {
            structure.addProperty(MAX_DISTANCE, resolvedHorizontal);
            return;
        }
        JsonObject bounds = new JsonObject();
        bounds.addProperty("horizontal", resolvedHorizontal);
        if (resolvedVertical != null) {
            bounds.addProperty("vertical", resolvedVertical);
        }
        structure.add(MAX_DISTANCE, bounds);
    }

    private static void heightmap(JsonObject structure, IrisJigsawHeightmap heightmap) {
        if (heightmap == null || heightmap == IrisJigsawHeightmap.SOURCE) {
            return;
        }
        if (heightmap == IrisJigsawHeightmap.NONE) {
            structure.remove(HEIGHTMAP);
            return;
        }
        structure.addProperty(HEIGHTMAP, heightmap.name());
    }

    private static void padding(JsonObject structure, Integer bottom, Integer top) {
        JsonElement current = structure.get(PADDING);
        int currentBottom = 0;
        int currentTop = 0;
        if (number(current)) {
            currentBottom = current.getAsInt();
            currentTop = current.getAsInt();
        } else if (current != null && current.isJsonObject()) {
            JsonObject bounds = current.getAsJsonObject();
            currentBottom = number(bounds.get("bottom")) ? bounds.get("bottom").getAsInt() : 0;
            currentTop = number(bounds.get("top")) ? bounds.get("top").getAsInt() : 0;
        }
        JsonObject padding = new JsonObject();
        padding.addProperty("bottom", bottom != null ? bottom : currentBottom);
        padding.addProperty("top", top != null ? top : currentTop);
        structure.add(PADDING, padding);
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean number(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber();
    }
}
