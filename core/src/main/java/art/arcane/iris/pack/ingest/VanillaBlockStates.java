/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.pack.ingest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class VanillaBlockStates {
    private VanillaBlockStates() {
    }

    static JsonObject irisBlock(JsonElement state) throws IngestConversionException {
        if (state != null && state.isJsonPrimitive() && state.getAsJsonPrimitive().isString()) {
            return parseStateString(state.getAsString());
        }
        JsonObject object = VanillaJson.object(state, "block state");
        if (object.has("id")) {
            return block(VanillaJson.string(object.get("id"), "block state id"), object.get("properties"));
        }
        if (object.has("Name")) {
            return block(VanillaJson.string(object.get("Name"), "block state Name"), object.get("Properties"));
        }
        throw new IngestConversionException("unsupported block state " + object);
    }

    static List<JsonObject> palette(JsonElement provider) throws IngestConversionException {
        if (provider != null && provider.isJsonPrimitive()) {
            throw new IngestConversionException("block state provider reference " + provider.getAsString() + " is not captured in the catalog");
        }
        JsonObject object = VanillaJson.object(provider, "block state provider");
        String type = VanillaJson.type(object, "type");
        Map<String, JsonObject> entries = new LinkedHashMap<>();
        switch (type) {
            case "" -> add(entries, irisBlock(object), 1);
            case "minecraft:simple_state_provider", "minecraft:simple" -> add(entries, irisBlock(object.get("state")), 1);
            case "minecraft:weighted_state_provider", "minecraft:weighted" -> addWeighted(entries, VanillaJson.array(object.get("entries"), "weighted entries"));
            case "minecraft:noise_provider", "minecraft:noise", "minecraft:dual_noise_provider", "minecraft:dual_noise" ->
                    addAll(entries, VanillaJson.array(object.get("states"), "noise states"));
            case "minecraft:noise_threshold_provider", "minecraft:noise_threshold" -> {
                add(entries, irisBlock(object.get("default_state")), 1);
                addAll(entries, VanillaJson.array(object.get("low_states"), "noise_threshold low_states"));
                addAll(entries, VanillaJson.array(object.get("high_states"), "noise_threshold high_states"));
            }
            default -> throw new IngestConversionException("unsupported block state provider " + type);
        }
        if (entries.isEmpty()) {
            throw new IngestConversionException("block state provider has no states");
        }
        List<JsonObject> palette = new ArrayList<>(entries.size());
        for (JsonObject entry : entries.values()) {
            JsonObject copy = entry.deepCopy();
            if (copy.has("weight") && copy.get("weight").getAsInt() == 1) {
                copy.remove("weight");
            }
            palette.add(copy);
        }
        return palette;
    }

    private static void addWeighted(Map<String, JsonObject> entries, JsonArray weighted) throws IngestConversionException {
        for (JsonElement entry : weighted) {
            JsonObject entryObject = VanillaJson.object(entry, "weighted entry");
            int weight = VanillaJson.integer(entryObject, "weight", 1, "weighted entry");
            if (weight > 0) {
                add(entries, irisBlock(entryObject.get("data")), weight);
            }
        }
    }

    private static void addAll(Map<String, JsonObject> entries, JsonArray states) throws IngestConversionException {
        for (JsonElement state : states) {
            add(entries, irisBlock(state), 1);
        }
    }

    private static void add(Map<String, JsonObject> entries, JsonObject block, int weight) {
        String key = block.toString();
        JsonObject existing = entries.get(key);
        if (existing == null) {
            JsonObject entry = block.deepCopy();
            entry.addProperty("weight", weight);
            entries.put(key, entry);
            return;
        }
        existing.addProperty("weight", existing.get("weight").getAsInt() + weight);
    }

    private static JsonObject parseStateString(String value) throws IngestConversionException {
        String trimmed = value.trim();
        int bracket = trimmed.indexOf('[');
        if (bracket < 0) {
            return block(trimmed, null);
        }
        if (!trimmed.endsWith("]")) {
            throw new IngestConversionException("malformed block state " + value);
        }
        JsonObject properties = new JsonObject();
        String body = trimmed.substring(bracket + 1, trimmed.length() - 1);
        for (String pair : body.split(",")) {
            if (pair.isBlank()) {
                continue;
            }
            int equals = pair.indexOf('=');
            if (equals <= 0) {
                throw new IngestConversionException("malformed block state " + value);
            }
            properties.addProperty(pair.substring(0, equals).trim(), pair.substring(equals + 1).trim());
        }
        return block(trimmed.substring(0, bracket), properties);
    }

    private static JsonObject block(String id, JsonElement properties) throws IngestConversionException {
        if (id.isBlank()) {
            throw new IngestConversionException("block state has an empty id");
        }
        JsonObject block = new JsonObject();
        block.addProperty("block", VanillaJson.namespaced(id));
        if (properties == null || properties.isJsonNull()) {
            return block;
        }
        JsonObject propertyObject = VanillaJson.object(properties, "block state properties");
        if (propertyObject.size() == 0) {
            return block;
        }
        JsonObject data = new JsonObject();
        for (Map.Entry<String, JsonElement> property : propertyObject.entrySet()) {
            data.addProperty(property.getKey(), property.getValue().getAsString());
        }
        block.add("data", data);
        return block;
    }
}
