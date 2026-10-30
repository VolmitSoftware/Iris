package art.arcane.iris.pack.datapack.export.emit;

import art.arcane.iris.pack.datapack.export.VanillaResourceIdentifier;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public final class FunctionTable {
    private final String namespace;
    private final SortedMap<String, JsonElement> files = new TreeMap<>();
    private final Map<String, String> idsByCanonicalBody = new HashMap<>();

    public FunctionTable(String namespace) {
        this.namespace = VanillaResourceIdentifier.requireNamespace(namespace, "density function namespace");
    }

    public String namespace() {
        return namespace;
    }

    public String register(String id, JsonElement body) {
        VanillaResourceIdentifier.requirePath(id, "density function id");
        String canonicalBody = canonical(body).toString();
        String existingId = idsByCanonicalBody.get(canonicalBody);
        if (existingId != null) {
            return reference(existingId);
        }
        if (files.containsKey(id)) {
            throw new IllegalArgumentException("Density function '" + reference(id) + "' is already registered with a different body");
        }
        files.put(id, body.deepCopy());
        idsByCanonicalBody.put(canonicalBody, id);
        return reference(id);
    }

    public String registerNamed(String id, JsonElement body) {
        VanillaResourceIdentifier.requirePath(id, "density function id");
        String canonicalBody = canonical(body).toString();
        JsonElement existing = files.get(id);
        if (existing != null && !canonical(existing).toString().equals(canonicalBody)) {
            throw new IllegalArgumentException("Density function '" + reference(id) + "' is already registered with a different body");
        }
        files.put(id, body.deepCopy());
        idsByCanonicalBody.putIfAbsent(canonicalBody, id);
        return reference(id);
    }

    public SortedMap<String, JsonElement> files() {
        return Collections.unmodifiableSortedMap(files);
    }

    void replace(String id, JsonElement body) {
        JsonElement previous = files.get(id);
        if (previous == null) {
            throw new IllegalArgumentException("Density function '" + reference(id) + "' is not registered");
        }
        idsByCanonicalBody.remove(canonical(previous).toString(), id);
        files.put(id, body.deepCopy());
        idsByCanonicalBody.putIfAbsent(canonical(body).toString(), id);
    }

    private String reference(String id) {
        return namespace + ":" + id;
    }

    private static JsonElement canonical(JsonElement element) {
        if (element.isJsonObject()) {
            SortedMap<String, JsonElement> members = new TreeMap<>(element.getAsJsonObject().asMap());
            JsonObject sorted = new JsonObject();
            for (Map.Entry<String, JsonElement> member : members.entrySet()) {
                sorted.add(member.getKey(), canonical(member.getValue()));
            }
            return sorted;
        }
        if (element.isJsonArray()) {
            JsonArray source = element.getAsJsonArray();
            JsonArray sorted = new JsonArray(source.size());
            for (JsonElement item : source) {
                sorted.add(canonical(item));
            }
            return sorted;
        }
        return element;
    }
}
