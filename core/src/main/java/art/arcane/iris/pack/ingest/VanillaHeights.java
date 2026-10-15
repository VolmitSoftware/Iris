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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

final class VanillaHeights {
    static final String UNIFORM = "UNIFORM";
    static final String TRIANGLE = "TRIANGLE";

    private VanillaHeights() {
    }

    static HeightBand band(JsonElement provider, VerticalBounds bounds) throws IngestConversionException {
        JsonObject object = VanillaJson.object(provider, "height provider");
        String type = VanillaJson.type(object, "type");
        if (type.isEmpty() && isAnchor(object)) {
            int y = bounds.resolve(object);
            return new HeightBand(y, y, UNIFORM);
        }
        return switch (type) {
            case "minecraft:constant" -> constant(object, bounds);
            case "minecraft:uniform" -> range(object, bounds, UNIFORM);
            case "minecraft:trapezoid" -> range(object, bounds,
                    VanillaJson.integer(object, "plateau", 0, "trapezoid") == 0 ? TRIANGLE : UNIFORM);
            default -> throw new IngestConversionException("unsupported height provider " + (type.isEmpty() ? object.toString() : type));
        };
    }

    private static HeightBand constant(JsonObject object, VerticalBounds bounds) throws IngestConversionException {
        int y = bounds.resolve(object.get("value"));
        return new HeightBand(y, y, UNIFORM);
    }

    private static HeightBand range(JsonObject object, VerticalBounds bounds, String distribution) throws IngestConversionException {
        int min = bounds.resolve(object.get("min_inclusive"));
        int max = bounds.resolve(object.get("max_inclusive"));
        if (max < min) {
            throw new IngestConversionException("empty height range " + min + ".." + max);
        }
        return new HeightBand(min, max, distribution);
    }

    private static boolean isAnchor(JsonObject object) {
        return object.has("absolute") || object.has("above_bottom") || object.has("below_top");
    }

    record VerticalBounds(int minY, int height) {
        int top() {
            return minY + height - 1;
        }

        int resolve(JsonElement anchor) throws IngestConversionException {
            if (anchor != null && anchor.isJsonPrimitive()) {
                return VanillaJson.integer(anchor, "vertical anchor");
            }
            JsonObject object = VanillaJson.object(anchor, "vertical anchor");
            if (object.has("absolute")) {
                return VanillaJson.integer(object.get("absolute"), "vertical anchor absolute");
            }
            if (object.has("above_bottom")) {
                return minY + VanillaJson.integer(object.get("above_bottom"), "vertical anchor above_bottom");
            }
            if (object.has("below_top")) {
                return top() - VanillaJson.integer(object.get("below_top"), "vertical anchor below_top");
            }
            throw new IngestConversionException("unsupported vertical anchor " + object);
        }
    }

    record HeightBand(int min, int max, String distribution) {
    }
}
