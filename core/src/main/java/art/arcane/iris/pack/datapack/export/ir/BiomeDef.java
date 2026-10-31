package art.arcane.iris.pack.datapack.export.ir;

import art.arcane.iris.pack.datapack.export.VanillaResourceIdentifier;
import com.google.gson.JsonObject;

public record BiomeDef(String id, JsonObject json) {
    public BiomeDef {
        VanillaResourceIdentifier.requirePath(id, "biome id");
        json = json.deepCopy();
    }
}
