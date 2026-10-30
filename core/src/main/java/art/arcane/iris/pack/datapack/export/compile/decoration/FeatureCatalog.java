package art.arcane.iris.pack.datapack.export.compile.decoration;

import art.arcane.iris.pack.datapack.export.VanillaResourceIdentifier;
import art.arcane.iris.pack.datapack.export.compile.WorldgenSyntax;
import art.arcane.iris.pack.datapack.export.ir.JsonResource;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public final class FeatureCatalog {
    private static final String PLACED_FOLDER = "worldgen/placed_feature";
    private static final String BLOCK_TAG_FOLDER = "tags/block";

    private final String namespace;
    private final WorldgenSyntax syntax;
    private final Map<String, String> idsByBody = new HashMap<>();
    private final SortedMap<String, JsonElement> files = new TreeMap<>();

    public FeatureCatalog(String namespace, WorldgenSyntax syntax) {
        this.namespace = VanillaResourceIdentifier.requireNamespace(namespace, "feature namespace");
        this.syntax = syntax;
    }

    public WorldgenSyntax syntax() {
        return syntax;
    }

    public String namespace() {
        return namespace;
    }

    public String feature(String name, String type, JsonObject config) {
        return register(syntax.featureFolder(), name, syntax.feature(type, config));
    }

    public String placed(String name, String featureId, JsonArray placement) {
        JsonObject placed = new JsonObject();
        placed.addProperty("feature", featureId);
        placed.add("placement", placement);
        return register(PLACED_FOLDER, name, placed);
    }

    public JsonObject inlinePlaced(String featureId, JsonArray placement) {
        JsonObject placed = new JsonObject();
        placed.addProperty("feature", featureId);
        placed.add("placement", placement);
        return placed;
    }

    public String blockTag(String name, List<String> values) {
        JsonArray entries = new JsonArray(values.size());
        for (String value : values) {
            entries.add(value);
        }
        JsonObject tag = new JsonObject();
        tag.add("values", entries);
        return register(BLOCK_TAG_FOLDER, name, tag);
    }

    public List<JsonResource> resources() {
        List<JsonResource> resources = new ArrayList<>(files.size());
        for (Map.Entry<String, JsonElement> file : files.entrySet()) {
            resources.add(new JsonResource(file.getKey(), file.getValue()));
        }
        return resources;
    }

    private String register(String folder, String name, JsonElement body) {
        String key = folder + "|" + canonical(body);
        String existing = idsByBody.get(key);
        if (existing != null) {
            return existing;
        }
        String base = VanillaResourceIdentifier.sanitizePath(name);
        String path = base;
        int suffix = 2;
        while (files.containsKey(file(folder, path))) {
            path = base + "_" + suffix++;
        }
        files.put(file(folder, path), body.deepCopy());
        String id = namespace + ":" + path;
        idsByBody.put(key, id);
        return id;
    }

    private String file(String folder, String path) {
        return "data/" + namespace + "/" + folder + "/" + path + ".json";
    }

    private static String canonical(JsonElement element) {
        if (element.isJsonObject()) {
            StringBuilder builder = new StringBuilder("{");
            for (Map.Entry<String, JsonElement> member : new TreeMap<>(element.getAsJsonObject().asMap()).entrySet()) {
                builder.append('"').append(member.getKey()).append("\":").append(canonical(member.getValue())).append(',');
            }
            return builder.append('}').toString();
        }
        if (element.isJsonArray()) {
            StringBuilder builder = new StringBuilder("[");
            for (JsonElement item : element.getAsJsonArray()) {
                builder.append(canonical(item)).append(',');
            }
            return builder.append(']').toString();
        }
        return element.toString();
    }
}
