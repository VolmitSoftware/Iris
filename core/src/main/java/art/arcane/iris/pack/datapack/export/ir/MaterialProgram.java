package art.arcane.iris.pack.datapack.export.ir;

import art.arcane.iris.pack.datapack.export.VanillaResourceIdentifier;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public record MaterialProgram(JsonElement rule) {
    public MaterialProgram {
        rule = rule.deepCopy();
    }

    public static MaterialProgram rock(String block) {
        VanillaResourceIdentifier.requireIdentifier(block, "block id");
        return new MaterialProgram(blockRule(block));
    }

    private static JsonObject blockRule(String block) {
        JsonObject state = new JsonObject();
        state.addProperty("id", block);
        JsonObject rule = new JsonObject();
        rule.addProperty("type", "minecraft:block");
        rule.add("result_state", state);
        return rule;
    }
}
