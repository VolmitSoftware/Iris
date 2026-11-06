/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.core.studio.bridge;

import art.arcane.iris.core.studio.scene.GraphEdit;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.volmit.tooling.JsonPath;

import java.util.ArrayList;
import java.util.List;

/**
 * The body of an edit the studio sends, read into a {@link GraphEdit}:
 * <pre>
 * {"revision": "…", "edit": {"op": "set", "object": ["parts", 0, "stem"], "key": "length", "literal": "6"}}
 * </pre>
 * Ops: {@code set} (object, key, literal), {@code unset} (object, key), {@code append} (list, literal), {@code addItem}
 * (list), {@code delete} (path), {@code duplicate} (path), {@code move} (path, toList).
 */
record GraphEditRequest(String revision, GraphEdit edit) {
    /** @throws IllegalArgumentException naming what is missing or wrong */
    static GraphEditRequest parse(String body) {
        JsonObject root = object(JsonParser.parseString(body), "The body");
        JsonObject edit = object(root.get("edit"), "edit");
        GraphEdit parsed = switch (text(edit, "op")) {
            case "set" -> new GraphEdit.Set(path(edit, "object"), text(edit, "key"), text(edit, "literal"));
            case "unset" -> new GraphEdit.Unset(path(edit, "object"), text(edit, "key"));
            case "append" -> new GraphEdit.Append(path(edit, "list"), text(edit, "literal"));
            case "addItem" -> new GraphEdit.AddItem(path(edit, "list"));
            case "delete" -> new GraphEdit.Delete(path(edit, "path"));
            case "duplicate" -> new GraphEdit.Duplicate(path(edit, "path"));
            case "move" -> new GraphEdit.Move(path(edit, "path"), path(edit, "toList"));
            default -> throw new IllegalArgumentException("Unknown edit: " + text(edit, "op"));
        };
        return new GraphEditRequest(text(root, "revision"), parsed);
    }

    /** A whole-file write, as undoing and redoing send it: {@code {"revision": "…", "text": "…"}}. */
    record FileWrite(String revision, String text) {
        static FileWrite parse(String body) {
            JsonObject root = object(JsonParser.parseString(body), "The body");
            return new FileWrite(GraphEditRequest.text(root, "revision"), GraphEditRequest.text(root, "text"));
        }
    }

    private static JsonObject object(JsonElement element, String name) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException(name + " must be a JSON object");
        }
        return element.getAsJsonObject();
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(key + " must be a string");
        }
        return value.getAsString();
    }

    private static JsonPath path(JsonObject object, String key) {
        if (!(object.get(key) instanceof JsonArray steps)) {
            throw new IllegalArgumentException(key + " must be an array of member names and indices");
        }
        List<Object> path = new ArrayList<>();
        for (JsonElement step : steps) {
            if (!(step instanceof JsonPrimitive primitive)) {
                throw new IllegalArgumentException(key + " holds " + step + ", not a name or an index");
            }
            path.add(primitive.isNumber() ? Integer.valueOf(primitive.getAsInt()) : primitive.getAsString());
        }
        return new JsonPath(path);
    }
}
