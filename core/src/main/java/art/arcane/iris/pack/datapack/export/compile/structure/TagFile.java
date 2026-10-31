package art.arcane.iris.pack.datapack.export.compile.structure;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

record TagFile(boolean replace, List<JsonElement> values) {
    private static final String VANILLA_NAMESPACE = "minecraft";

    TagFile {
        values = List.copyOf(values);
    }

    static TagFile parse(JsonElement json) {
        if (json == null || !json.isJsonObject()) {
            throw new IllegalArgumentException("Tag file is not a JSON object");
        }
        JsonObject root = json.getAsJsonObject();
        JsonElement replace = root.get("replace");
        if (replace != null && !(replace.isJsonPrimitive() && replace.getAsJsonPrimitive().isBoolean())) {
            throw new IllegalArgumentException("Tag file 'replace' is not a boolean");
        }
        JsonElement values = root.get("values");
        if (values == null || !values.isJsonArray()) {
            throw new IllegalArgumentException("Tag file has no 'values' array");
        }
        LinkedHashSet<JsonElement> entries = new LinkedHashSet<>();
        for (JsonElement value : values.getAsJsonArray()) {
            if (id(value) == null) {
                throw new IllegalArgumentException("Tag file value '" + value + "' is neither an id nor an {id} object");
            }
            entries.add(value.deepCopy());
        }
        return new TagFile(replace != null && replace.getAsBoolean(), List.copyOf(entries));
    }

    static String member(String id) {
        String trimmed = id.trim().toLowerCase(Locale.ROOT);
        return trimmed.indexOf(':') < 0 ? VANILLA_NAMESPACE + ":" + trimmed : trimmed;
    }

    TagFile then(TagFile later) {
        return later.replace ? later : new TagFile(replace, union(values, later.values));
    }

    TagFile plus(Collection<? extends JsonElement> additions) {
        return new TagFile(replace, union(values, additions));
    }

    Set<String> directMembers() {
        Set<String> members = new TreeSet<>();
        for (JsonElement value : values) {
            String id = id(value);
            if (!id.startsWith("#")) {
                members.add(member(id));
            }
        }
        return members;
    }

    JsonObject json() {
        JsonObject root = new JsonObject();
        root.addProperty("replace", replace);
        JsonArray array = new JsonArray(values.size());
        for (JsonElement value : values) {
            array.add(value.deepCopy());
        }
        root.add("values", array);
        return root;
    }

    private static String id(JsonElement value) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            return value.getAsString();
        }
        if (value.isJsonObject()) {
            JsonElement id = value.getAsJsonObject().get("id");
            return id != null && id.isJsonPrimitive() && id.getAsJsonPrimitive().isString() ? id.getAsString() : null;
        }
        return null;
    }

    private static List<JsonElement> union(Collection<? extends JsonElement> first, Collection<? extends JsonElement> second) {
        LinkedHashSet<JsonElement> merged = new LinkedHashSet<>(first);
        merged.addAll(second);
        return List.copyOf(merged);
    }
}
