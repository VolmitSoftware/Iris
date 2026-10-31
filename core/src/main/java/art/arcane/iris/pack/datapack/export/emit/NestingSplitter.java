package art.arcane.iris.pack.datapack.export.emit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class NestingSplitter {
    private NestingSplitter() {
    }

    public static void split(FunctionTable table, int maxDepth) {
        if (maxDepth < 1) {
            throw new IllegalArgumentException("Nesting limit must be positive, got " + maxDepth);
        }
        for (String id : List.copyOf(table.files().keySet())) {
            JsonElement body = table.files().get(id);
            if (depth(body) <= maxDepth) {
                continue;
            }
            JsonElement reduced = body.deepCopy();
            int part = 0;
            while (true) {
                Map<JsonElement, Integer> depths = new IdentityHashMap<>();
                if (measure(reduced, depths) <= maxDepth) {
                    break;
                }
                Extraction extraction = deepestExtractable(reduced, depths, maxDepth);
                if (extraction == null) {
                    throw new IllegalStateException("Density function '" + table.namespace() + ":" + id + "' nests deeper than "
                            + maxDepth + " levels with no density function node left to move into its own file");
                }
                String partId;
                do {
                    partId = id + "/part" + part++;
                } while (table.files().containsKey(partId));
                extraction.replace(new JsonPrimitive(table.register(partId, extraction.subtree())));
            }
            table.replace(id, reduced);
        }
    }

    public static int depth(JsonElement element) {
        return measure(element, null);
    }

    private static int measure(JsonElement element, Map<JsonElement, Integer> depths) {
        int depth = 0;
        if (element.isJsonObject()) {
            int children = 0;
            for (Map.Entry<String, JsonElement> member : element.getAsJsonObject().entrySet()) {
                children = Math.max(children, measure(member.getValue(), depths));
            }
            depth = children + 1;
        } else if (element.isJsonArray()) {
            int children = 0;
            for (JsonElement item : element.getAsJsonArray()) {
                children = Math.max(children, measure(item, depths));
            }
            depth = children + 1;
        }
        if (depths != null && depth > 0) {
            depths.put(element, depth);
        }
        return depth;
    }

    private static Extraction deepestExtractable(JsonElement root, Map<JsonElement, Integer> depths, int maxDepth) {
        JsonElement current = root;
        while (current.isJsonObject() || current.isJsonArray()) {
            Extraction deepest = deepestChild(current, depths);
            if (deepest == null) {
                return null;
            }
            JsonElement child = deepest.subtree();
            if (isDensityFunction(child) && depths.get(child) <= maxDepth) {
                return deepest;
            }
            current = child;
        }
        return null;
    }

    private static Extraction deepestChild(JsonElement container, Map<JsonElement, Integer> depths) {
        Extraction deepest = null;
        int deepestDepth = 0;
        if (container.isJsonObject()) {
            JsonObject object = container.getAsJsonObject();
            for (Map.Entry<String, JsonElement> member : object.entrySet()) {
                int depth = depths.getOrDefault(member.getValue(), 0);
                if (depth > deepestDepth) {
                    deepestDepth = depth;
                    deepest = new Extraction(object, member.getKey(), -1, member.getValue());
                }
            }
            return deepest;
        }
        JsonArray array = container.getAsJsonArray();
        for (int index = 0; index < array.size(); index++) {
            int depth = depths.getOrDefault(array.get(index), 0);
            if (depth > deepestDepth) {
                deepestDepth = depth;
                deepest = new Extraction(array, null, index, array.get(index));
            }
        }
        return deepest;
    }

    private static boolean isDensityFunction(JsonElement element) {
        if (!element.isJsonObject()) {
            return false;
        }
        JsonElement type = element.getAsJsonObject().get("type");
        return type != null && type.isJsonPrimitive() && type.getAsJsonPrimitive().isString();
    }

    private record Extraction(JsonElement parent, String key, int index, JsonElement subtree) {
        private void replace(JsonElement replacement) {
            if (parent.isJsonObject()) {
                parent.getAsJsonObject().add(key, replacement);
            } else {
                parent.getAsJsonArray().set(index, replacement);
            }
        }
    }
}
