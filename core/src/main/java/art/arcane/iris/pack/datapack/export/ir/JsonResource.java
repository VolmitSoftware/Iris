package art.arcane.iris.pack.datapack.export.ir;

import art.arcane.iris.pack.datapack.export.VanillaResourceIdentifier;
import com.google.gson.JsonElement;

public record JsonResource(String path, JsonElement json) {
    public JsonResource {
        VanillaResourceIdentifier.requirePath(path, "json resource path");
        json = json.deepCopy();
    }
}
