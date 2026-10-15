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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class IngestJson {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final int DECIMALS = 6;
    private static final double SMALLEST = 0.000001D;

    private IngestJson() {
    }

    static double round(double value) {
        double rounded = BigDecimal.valueOf(value).setScale(DECIMALS, RoundingMode.HALF_UP).doubleValue();
        return rounded == 0D && value > 0D ? SMALLEST : rounded;
    }

    static JsonArray stringArray(Collection<String> values) {
        JsonArray array = new JsonArray(values.size());
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    static List<String> strings(JsonElement element) {
        List<String> values = new ArrayList<>();
        if (element == null || !element.isJsonArray()) {
            return values;
        }
        for (JsonElement value : element.getAsJsonArray()) {
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                values.add(value.getAsString());
            }
        }
        return values;
    }

    static JsonObject read(Path file) throws IOException {
        try {
            JsonElement element = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!element.isJsonObject()) {
                throw new IOException("Pack file is not a JSON object: " + file);
            }
            return element.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new IOException("Pack file is not valid JSON: " + file, e);
        }
    }

    static String format(JsonElement element) {
        return GSON.toJson(element) + "\n";
    }
}
