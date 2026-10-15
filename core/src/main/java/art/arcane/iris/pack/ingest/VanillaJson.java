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
import com.google.gson.JsonPrimitive;

import java.util.Locale;

final class VanillaJson {
    private VanillaJson() {
    }

    static String namespaced(String key) {
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        return normalized.indexOf(':') < 0 ? "minecraft:" + normalized : normalized;
    }

    static JsonObject featureConfig(JsonElement config) throws IngestConversionException {
        if (config == null || config.isJsonNull()) {
            throw new IngestConversionException("feature config was not captured in the catalog");
        }
        JsonObject object = object(config, "feature config");
        JsonElement nested = object.get("config");
        if (nested != null && nested.isJsonObject() && !object.has("targets") && !object.has("to_place")) {
            return nested.getAsJsonObject();
        }
        return object;
    }

    static JsonArray placement(JsonElement placement) throws IngestConversionException {
        if (placement == null || placement.isJsonNull()) {
            throw new IngestConversionException("placement modifiers were not captured in the catalog");
        }
        return array(placement, "placement modifiers");
    }

    static String type(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            return "";
        }
        return namespaced(value.getAsString());
    }

    static JsonObject object(JsonElement element, String context) throws IngestConversionException {
        if (element == null || !element.isJsonObject()) {
            throw new IngestConversionException(context + " is not a JSON object");
        }
        return element.getAsJsonObject();
    }

    static JsonArray array(JsonElement element, String context) throws IngestConversionException {
        if (element == null || !element.isJsonArray()) {
            throw new IngestConversionException(context + " is not a JSON array");
        }
        return element.getAsJsonArray();
    }

    static String string(JsonElement element, String context) throws IngestConversionException {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IngestConversionException(context + " is not a string");
        }
        return element.getAsString();
    }

    static double number(JsonElement element, String context) throws IngestConversionException {
        JsonPrimitive primitive = numeric(element, context);
        double value = primitive.getAsDouble();
        if (!Double.isFinite(value)) {
            throw new IngestConversionException(context + " is not a finite number");
        }
        return value;
    }

    static int integer(JsonElement element, String context) throws IngestConversionException {
        double value = number(element, context);
        if (value != Math.rint(value) || value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
            throw new IngestConversionException(context + " is not an integer");
        }
        return (int) value;
    }

    static double number(JsonObject object, String field, double fallback, String context) throws IngestConversionException {
        JsonElement value = object.get(field);
        return value == null || value.isJsonNull() ? fallback : number(value, context + "." + field);
    }

    static int integer(JsonObject object, String field, int fallback, String context) throws IngestConversionException {
        JsonElement value = object.get(field);
        return value == null || value.isJsonNull() ? fallback : integer(value, context + "." + field);
    }

    private static JsonPrimitive numeric(JsonElement element, String context) throws IngestConversionException {
        if (element == null || !element.isJsonPrimitive()) {
            throw new IngestConversionException(context + " is not a number");
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isNumber()) {
            return primitive;
        }
        if (primitive.isString()) {
            try {
                return new JsonPrimitive(Double.parseDouble(primitive.getAsString().trim()));
            } catch (NumberFormatException e) {
                throw new IngestConversionException(context + " is not a number: " + primitive.getAsString());
            }
        }
        throw new IngestConversionException(context + " is not a number");
    }
}
