package art.arcane.iris.pack.datapack.export.compile;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.VanillaBlockState;
import art.arcane.iris.pack.datapack.export.WorldgenExportTargets;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;

public final class WorldgenSyntax {
    private final DataVersion target;

    private WorldgenSyntax(DataVersion target) {
        this.target = target;
    }

    public static WorldgenSyntax forTarget(DataVersion target) {
        if (!WorldgenExportTargets.SUPPORTED.contains(target)) {
            throw WorldgenExportTargets.unsupported(target.getVersion());
        }
        return new WorldgenSyntax(target);
    }

    public DataVersion target() {
        return target;
    }

    public String featureFolder() {
        return "worldgen/feature";
    }

    public JsonObject blockState(VanillaBlockState state) {
        JsonObject json = new JsonObject();
        json.addProperty("id", state.name());
        if (!state.properties().isEmpty()) {
            JsonObject properties = new JsonObject();
            for (Map.Entry<String, String> property : state.properties().entrySet()) {
                properties.addProperty(property.getKey(), property.getValue());
            }
            json.add("properties", properties);
        }
        return json;
    }

    public JsonObject provider(List<WeightedState> entries) {
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("A block state provider needs at least one state");
        }
        if (entries.size() == 1) {
            return blockState(entries.getFirst().state());
        }
        JsonArray weighted = new JsonArray(entries.size());
        for (WeightedState entry : entries) {
            JsonObject item = new JsonObject();
            item.add("data", blockState(entry.state()));
            item.addProperty("weight", entry.weight());
            weighted.add(item);
        }
        JsonObject provider = new JsonObject();
        provider.addProperty("type", "minecraft:weighted");
        provider.add("entries", weighted);
        return provider;
    }

    public JsonObject feature(String type, JsonObject config) {
        JsonObject feature = new JsonObject();
        feature.addProperty("type", type);
        for (Map.Entry<String, JsonElement> field : config.entrySet()) {
            feature.add(field.getKey(), field.getValue().deepCopy());
        }
        return feature;
    }
}
