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

package art.arcane.iris.generation.cave;

import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.util.Set;

/**
 * Reads and writes the two cave types a pack may write as a plain number or as an object: {@link CaveValue} and
 * {@link CaveTunnelCoverage}. Both keep their object members to the ones named here, so a typo is an error, not a default.
 */
public final class CaveJson {
    private static final String MIN = "min";
    private static final String MAX = "max";
    private static final String SHARE = "share";
    private static final String SOFTNESS = "softness";
    private static final String STYLE = "style";
    private static final Set<String> VALUE_MEMBERS = Set.of(MIN, MAX, STYLE);
    private static final Set<String> COVERAGE_MEMBERS = Set.of(SHARE, SOFTNESS, STYLE);

    private CaveJson() {
    }

    public static void register(GsonBuilder builder) {
        builder.registerTypeAdapter(CaveValue.class, new ValueCodec());
        builder.registerTypeAdapter(CaveTunnelCoverage.class, new CoverageCodec());
    }

    private static final class ValueCodec implements JsonDeserializer<CaveValue>, JsonSerializer<CaveValue> {
        @Override
        public CaveValue deserialize(JsonElement json, Type type, JsonDeserializationContext context) {
            return readValue(json, context);
        }

        @Override
        public JsonElement serialize(CaveValue value, Type type, JsonSerializationContext context) {
            return writeValue(value, context);
        }
    }

    private static final class CoverageCodec implements JsonDeserializer<CaveTunnelCoverage>, JsonSerializer<CaveTunnelCoverage> {
        @Override
        public CaveTunnelCoverage deserialize(JsonElement json, Type type, JsonDeserializationContext context) {
            return readCoverage(json, context);
        }

        @Override
        public JsonElement serialize(CaveTunnelCoverage coverage, Type type, JsonSerializationContext context) {
            return writeCoverage(coverage, context);
        }
    }

    private static CaveValue readValue(JsonElement json, JsonDeserializationContext context) {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
            return CaveValue.of(json.getAsDouble());
        }
        if (!json.isJsonObject()) {
            throw new JsonParseException("A cave value is a number or {min, max} with an optional style, not " + json);
        }
        JsonObject object = json.getAsJsonObject();
        requireOnlyMembers(object, VALUE_MEMBERS, "A varying cave value has min, max and style, not ");
        if (!object.has(MIN) || !object.has(MAX)) {
            throw new JsonParseException("A varying cave value needs both min and max: " + object);
        }
        return CaveValue.between(number(object, MIN), number(object, MAX), styleOf(object, context));
    }

    private static JsonElement writeValue(CaveValue value, JsonSerializationContext context) {
        if (value.isWrittenAsPlainNumber()) {
            return context.serialize(value.min());
        }
        JsonObject object = new JsonObject();
        object.addProperty(MIN, value.min());
        object.addProperty(MAX, value.max());
        value.style().ifPresent(style -> object.add(STYLE, context.serialize(style)));
        return object;
    }

    private static CaveTunnelCoverage readCoverage(JsonElement json, JsonDeserializationContext context) {
        CaveTunnelCoverage everywhere = CaveTunnelCoverage.EVERYWHERE;
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
            return CaveTunnelCoverage.of(CaveValue.of(json.getAsDouble()), everywhere.softness(), null);
        }
        if (!json.isJsonObject()) {
            throw new JsonParseException("A tunnel coverage is a share or {share, softness, style}, not " + json);
        }
        JsonObject object = json.getAsJsonObject();
        requireOnlyMembers(object, COVERAGE_MEMBERS, "A tunnel coverage has share, softness and style, not ");
        CaveValue share = object.has(SHARE) ? context.deserialize(object.get(SHARE), CaveValue.class) : everywhere.share();
        CaveValue softness = object.has(SOFTNESS) ? context.deserialize(object.get(SOFTNESS), CaveValue.class) : everywhere.softness();
        return CaveTunnelCoverage.of(share, softness, styleOf(object, context));
    }

    private static JsonElement writeCoverage(CaveTunnelCoverage coverage, JsonSerializationContext context) {
        if (coverage.isWrittenAsShareOnly()) {
            return context.serialize(coverage.share(), CaveValue.class);
        }
        JsonObject object = new JsonObject();
        object.add(SHARE, context.serialize(coverage.share(), CaveValue.class));
        object.add(SOFTNESS, context.serialize(coverage.softness(), CaveValue.class));
        coverage.style().ifPresent(style -> object.add(STYLE, context.serialize(style)));
        return object;
    }

    private static void requireOnlyMembers(JsonObject object, Set<String> allowed, String message) {
        for (String member : object.keySet()) {
            if (!allowed.contains(member)) {
                throw new JsonParseException(message + member);
            }
        }
    }

    private static double number(JsonObject object, String member) {
        JsonElement written = object.get(member);
        if (written.isJsonPrimitive() && written.getAsJsonPrimitive().isNumber()) {
            return written.getAsDouble();
        }
        throw new JsonParseException(member + " needs a number, not " + written);
    }

    private static IrisGeneratorStyle styleOf(JsonObject object, JsonDeserializationContext context) {
        return object.has(STYLE) ? context.deserialize(object.get(STYLE), IrisGeneratorStyle.class) : null;
    }
}
