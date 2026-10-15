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

package art.arcane.iris.modded;

import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class ModdedDatapackTagMerge {
    private static final String REPLACE = "replace";
    private static final String VALUES = "values";
    private static final String REMOVE = "remove";

    private ModdedDatapackTagMerge() {
    }

    static boolean isTag(Path relative) {
        return relative.getNameCount() >= 4
                && "data".equals(relative.getName(0).toString())
                && "tags".equals(relative.getName(2).toString())
                && relative.getFileName().toString().endsWith(".json");
    }

    static void merge(Path source, Path destination) throws IOException {
        JSONObject incoming = read(source);
        JSONObject existing = read(destination);
        JSONObject tag = new JSONObject();
        tag.put(REPLACE, existing.optBoolean(REPLACE, false) || incoming.optBoolean(REPLACE, false));
        tag.put(VALUES, union(existing, incoming, VALUES, source, destination));
        if (existing.has(REMOVE) || incoming.has(REMOVE)) {
            tag.put(REMOVE, union(existing, incoming, REMOVE, source, destination));
        }
        Files.writeString(destination, tag.toString(4), StandardCharsets.UTF_8);
    }

    private static JSONArray union(JSONObject existing, JSONObject incoming, String field, Path source, Path destination)
            throws IOException {
        Map<String, Object> entries = new LinkedHashMap<>();
        collect(existing, field, entries, destination);
        collect(incoming, field, entries, source);
        JSONArray merged = new JSONArray();
        for (Object entry : entries.values()) {
            merged.put(entry);
        }
        return merged;
    }

    private static JSONObject read(Path tag) throws IOException {
        try {
            return new JSONObject(Files.readString(tag, StandardCharsets.UTF_8));
        } catch (RuntimeException invalid) {
            throw new IOException("Iris cannot merge the invalid datapack tag " + tag + ".", invalid);
        }
    }

    private static void collect(JSONObject tag, String field, Map<String, Object> entries, Path origin) throws IOException {
        JSONArray values = tag.optJSONArray(field);
        if (values == null) {
            return;
        }
        for (int index = 0; index < values.length(); index++) {
            Object entry = values.get(index);
            String key = entry instanceof JSONObject object ? object.optString("id", "") : entry.toString();
            if (key.isBlank()) {
                throw new IOException("Iris cannot merge datapack tag " + origin + ": " + field + " entry " + index + " has no id.");
            }
            entries.putIfAbsent(key, entry);
        }
    }
}
