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

final class VanillaIntProviders {
    private static final int MAX_ENUMERATED_VALUES = 4096;

    private VanillaIntProviders() {
    }

    static IntStats stats(JsonElement provider) throws IngestConversionException {
        if (provider != null && provider.isJsonPrimitive()) {
            int value = VanillaJson.integer(provider, "int provider");
            return new IntStats(value, value, value);
        }
        JsonObject object = VanillaJson.object(provider, "int provider");
        String type = VanillaJson.type(object, "type");
        return switch (type) {
            case "minecraft:constant" -> stats(object.get("value"));
            case "minecraft:uniform" -> uniform(bounds(object));
            case "minecraft:biased_to_bottom" -> biasedToBottom(bounds(object));
            case "minecraft:trapezoid" -> trapezoid(object);
            case "minecraft:clamped" -> clamped(object);
            case "minecraft:clamped_normal" -> clampedNormal(object);
            case "minecraft:weighted_list" -> weightedList(object);
            default -> throw new IngestConversionException("unsupported int provider " + (type.isEmpty() ? object.toString() : type));
        };
    }

    static double probabilityOf(JsonElement provider, int value) throws IngestConversionException {
        if (provider != null && provider.isJsonPrimitive()) {
            return VanillaJson.integer(provider, "int provider") == value ? 1D : 0D;
        }
        JsonObject object = VanillaJson.object(provider, "int provider");
        String type = VanillaJson.type(object, "type");
        if (type.equals("minecraft:constant")) {
            return probabilityOf(object.get("value"), value);
        }
        if (type.equals("minecraft:trapezoid")) {
            return trapezoidProbability(object, value);
        }
        IntStats stats = stats(provider);
        if (value < stats.min() || value > stats.max()) {
            return 0D;
        }
        return 1D / ((long) stats.max() - stats.min() + 1L);
    }

    private static double trapezoidProbability(JsonObject object, int value) throws IngestConversionException {
        int min = VanillaJson.integer(object.get("min"), "trapezoid.min");
        int max = VanillaJson.integer(object.get("max"), "trapezoid.max");
        int plateau = VanillaJson.integer(object, "plateau", 0, "trapezoid");
        int range = max - min;
        if (range < 0 || value < min || value > max) {
            return 0D;
        }
        int plateauStart = Math.max(0, (range - plateau) / 2);
        int first = range - plateauStart;
        int offset = value - min;
        long pairs = 0L;
        for (int i = Math.max(0, offset - plateauStart); i <= Math.min(first, offset); i++) {
            pairs++;
        }
        return pairs / ((first + 1D) * (plateauStart + 1D));
    }

    private static IntStats uniform(int[] bounds) {
        return new IntStats((bounds[0] + bounds[1]) / 2.0D, bounds[0], bounds[1]);
    }

    private static IntStats biasedToBottom(int[] bounds) {
        return new IntStats(bounds[0] + (bounds[1] - bounds[0]) / 4.0D, bounds[0], bounds[1]);
    }

    private static IntStats trapezoid(JsonObject object) throws IngestConversionException {
        int min = VanillaJson.integer(object.get("min"), "trapezoid.min");
        int max = VanillaJson.integer(object.get("max"), "trapezoid.max");
        return new IntStats((min + max) / 2.0D, Math.min(min, max), Math.max(min, max));
    }

    private static IntStats clamped(JsonObject object) throws IngestConversionException {
        int low = VanillaJson.integer(object.get("min_inclusive"), "clamped.min_inclusive");
        int high = VanillaJson.integer(object.get("max_inclusive"), "clamped.max_inclusive");
        JsonElement source = object.get("source");
        int[] uniformSource = enumerableBounds(source);
        if (uniformSource != null && (long) uniformSource[1] - uniformSource[0] < MAX_ENUMERATED_VALUES) {
            double total = 0D;
            int count = uniformSource[1] - uniformSource[0] + 1;
            for (int value = uniformSource[0]; value <= uniformSource[1]; value++) {
                total += clamp(value, low, high);
            }
            return new IntStats(total / count, clamp(uniformSource[0], low, high), clamp(uniformSource[1], low, high));
        }
        IntStats inner = stats(source);
        return new IntStats(Math.max(low, Math.min(high, inner.mean())), clamp(inner.min(), low, high), clamp(inner.max(), low, high));
    }

    private static IntStats clampedNormal(JsonObject object) throws IngestConversionException {
        double mean = VanillaJson.number(object.get("mean"), "clamped_normal.mean");
        int low = (int) VanillaJson.number(object.has("min_inclusive") ? object.get("min_inclusive") : object.get("min"), "clamped_normal.min");
        int high = (int) VanillaJson.number(object.has("max_inclusive") ? object.get("max_inclusive") : object.get("max"), "clamped_normal.max");
        return new IntStats(Math.max(low, Math.min(high, mean)), low, high);
    }

    private static IntStats weightedList(JsonObject object) throws IngestConversionException {
        JsonArray distribution = VanillaJson.array(object.get("distribution"), "weighted_list.distribution");
        double weighted = 0D;
        double totalWeight = 0D;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (JsonElement entry : distribution) {
            JsonObject entryObject = VanillaJson.object(entry, "weighted_list entry");
            double weight = VanillaJson.number(entryObject, "weight", 1D, "weighted_list entry");
            IntStats inner = stats(entryObject.get("data"));
            weighted += inner.mean() * weight;
            totalWeight += weight;
            min = Math.min(min, inner.min());
            max = Math.max(max, inner.max());
        }
        if (totalWeight <= 0D) {
            throw new IngestConversionException("weighted_list has no weight");
        }
        return new IntStats(weighted / totalWeight, min, max);
    }

    private static int[] enumerableBounds(JsonElement source) throws IngestConversionException {
        if (source != null && source.isJsonPrimitive()) {
            int value = VanillaJson.integer(source, "clamped.source");
            return new int[]{value, value};
        }
        if (source == null || !source.isJsonObject()) {
            return null;
        }
        JsonObject object = source.getAsJsonObject();
        String type = VanillaJson.type(object, "type");
        if (type.equals("minecraft:uniform")) {
            return bounds(object);
        }
        if (type.equals("minecraft:constant")) {
            int value = VanillaJson.integer(object.get("value"), "clamped.source.value");
            return new int[]{value, value};
        }
        return null;
    }

    private static int[] bounds(JsonObject object) throws IngestConversionException {
        JsonObject holder = object.has("min_inclusive") || !object.has("value")
                ? object
                : VanillaJson.object(object.get("value"), "int provider value");
        int min = VanillaJson.integer(holder.get("min_inclusive"), "int provider min_inclusive");
        int max = VanillaJson.integer(holder.get("max_inclusive"), "int provider max_inclusive");
        if (max < min) {
            throw new IngestConversionException("int provider max_inclusive " + max + " is below min_inclusive " + min);
        }
        return new int[]{min, max};
    }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }

    record IntStats(double mean, int min, int max) {
    }
}
