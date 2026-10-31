package art.arcane.iris.pack.datapack.export.compile.object;

import art.arcane.iris.pack.datapack.export.compile.WorldgenSyntax;
import art.arcane.iris.pack.datapack.export.compile.decoration.Placements;
import art.arcane.iris.pack.datapack.export.lower.ExportCapabilities;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.List;

final class FeatureJson {
    private static final int OFFSET_LIMIT = 16;

    private final WorldgenSyntax syntax;

    FeatureJson(ExportCapabilities capabilities) {
        syntax = WorldgenSyntax.forTarget(capabilities.target());
    }

    WorldgenSyntax syntax() {
        return syntax;
    }

    List<JsonObject> chanceAndCount(double chance, int count, double share) {
        List<JsonObject> modifiers = new ArrayList<>();
        if (chance < 1) {
            modifiers.add(Placements.randomChance(chance));
        }
        if (count > 1) {
            modifiers.add(Placements.count(new JsonPrimitive(count)));
        }
        if (share < 1) {
            modifiers.add(Placements.randomChance(share));
        }
        return modifiers;
    }

    List<JsonObject> offset(int x, int yMin, int yMax, int z) {
        List<JsonObject> modifiers = new ArrayList<>();
        int remainingX = x;
        int remainingZ = z;
        int remainingY = yMin;
        int spread = yMax - yMin;
        while (remainingX != 0 || remainingZ != 0 || remainingY != 0 || spread > 0) {
            int stepX = clampStep(remainingX);
            int stepZ = clampStep(remainingZ);
            int stepY = clampStep(remainingY);
            int stepSpread = Math.min(spread, OFFSET_LIMIT - Math.abs(stepY));
            JsonObject offset = new JsonObject();
            offset.addProperty("type", "minecraft:offset");
            offset.addProperty("x", stepX);
            offset.add("y", stepSpread > 0 ? Placements.uniform(stepY, stepY + stepSpread) : new JsonPrimitive(stepY));
            offset.addProperty("z", stepZ);
            modifiers.add(offset);
            remainingX -= stepX;
            remainingZ -= stepZ;
            remainingY -= stepY;
            spread -= stepSpread;
        }
        return modifiers;
    }

    static JsonArray vector(int x, int y, int z) {
        JsonArray vector = new JsonArray(3);
        vector.add(x);
        vector.add(y);
        vector.add(z);
        return vector;
    }

    static JsonObject absolute(int y) {
        JsonObject anchor = new JsonObject();
        anchor.addProperty("absolute", y);
        return anchor;
    }

    private static int clampStep(int remaining) {
        return Math.max(-OFFSET_LIMIT, Math.min(OFFSET_LIMIT, remaining));
    }
}
