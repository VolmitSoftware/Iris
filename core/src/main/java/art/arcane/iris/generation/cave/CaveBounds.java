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

import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The numbers a cave file writes outside what their keys allow, read from the {@link MinNumber} and {@link MaxNumber} of
 * each key and reported by their place in the file. A {@link CaveValue} is not a plain number, so the schema cannot
 * bound its ends: this does. It reads the file as written, so a style that names a snippet needs no resolving.
 */
public final class CaveBounds {
    private static final String CAVE_PACKAGE = IrisCave.class.getPackageName();
    private static final String MIN = "min";
    private static final String MAX = "max";
    private static final String COVERAGE_SHARE = "share";

    private CaveBounds() {
    }

    /** Each number of the cave file outside what its key allows, such as {@code fade is 200; it must be at most 128}. */
    public static List<String> valuesOutOfBounds(JsonObject cave) {
        List<String> problems = new ArrayList<>();
        collect(IrisCave.class, cave, "", problems);
        return problems;
    }

    private static void collect(Class<?> type, JsonObject written, String path, List<String> problems) {
        for (Map.Entry<String, JsonElement> member : written.entrySet()) {
            Field field = declaredField(type, member.getKey());
            if (field != null) {
                collectMember(field, member.getValue(), path.isEmpty() ? member.getKey() : path + "." + member.getKey(), problems);
            }
        }
    }

    private static void collectMember(Field field, JsonElement value, String path, List<String> problems) {
        Class<?> type = field.getType();
        if (type == CaveValue.class) {
            collectValue(field, value, path, problems);
        } else if (type == CaveTunnelCoverage.class && value.isJsonPrimitive()) {
            collectValue(declaredField(CaveTunnelCoverage.class, COVERAGE_SHARE), value, path + "." + COVERAGE_SHARE, problems);
        } else if (value.isJsonObject() && type.getPackageName().equals(CAVE_PACKAGE)) {
            collect(type, value.getAsJsonObject(), path, problems);
        } else if (isNumber(value)) {
            requireWithin(field, value.getAsDouble(), path, problems);
        }
    }

    private static void collectValue(Field field, JsonElement value, String path, List<String> problems) {
        if (isNumber(value)) {
            requireWithin(field, value.getAsDouble(), path, problems);
            return;
        }
        if (!value.isJsonObject() || !isNumber(value.getAsJsonObject().get(MIN)) || !isNumber(value.getAsJsonObject().get(MAX))) {
            return;
        }
        double min = value.getAsJsonObject().get(MIN).getAsDouble();
        double max = value.getAsJsonObject().get(MAX).getAsDouble();
        if (min == max) {
            requireWithin(field, min, path, problems);
            return;
        }
        requireWithin(field, min, path + "." + MIN, problems);
        requireWithin(field, max, path + "." + MAX, problems);
    }

    private static boolean isNumber(JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber();
    }

    private static Field declaredField(Class<?> type, String name) {
        try {
            return type.getDeclaredField(name);
        } catch (NoSuchFieldException unknown) {
            return null;
        }
    }

    private static void requireWithin(Field field, double value, String path, List<String> problems) {
        MinNumber least = field.getAnnotation(MinNumber.class);
        MaxNumber most = field.getAnnotation(MaxNumber.class);
        if (least != null && value < least.value()) {
            problems.add(path + " is " + shortNumber(value) + "; it must be at least " + shortNumber(least.value()));
        } else if (most != null && value > most.value()) {
            problems.add(path + " is " + shortNumber(value) + "; it must be at most " + shortNumber(most.value()));
        }
    }

    private static String shortNumber(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }
}
