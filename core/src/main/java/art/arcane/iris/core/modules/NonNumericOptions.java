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

package art.arcane.iris.core.modules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Finds the option a pack wrote as something other than a number although its type holds one, so a failed decode can
 * name it: Gson reports only the text it could not parse.
 */
final class NonNumericOptions {
    private static final List<String> LIBRARY_PACKAGES = List.of("java.", "javax.", "com.google.gson.");

    private NonNumericOptions() {
    }

    /** The path of the first number option of {@code type} that {@code written} holds as a non-number, if any. */
    static Optional<String> first(Type type, JsonElement written) {
        return find(type, written, "");
    }

    private static Optional<String> find(Type type, JsonElement written, String path) {
        Class<?> raw = rawClass(type);
        if (raw == null || written == null || written.isJsonNull()) {
            return Optional.empty();
        }
        if (isNumber(raw)) {
            return readsAsNumber(written) ? Optional.empty() : Optional.of(path);
        }
        if (Collection.class.isAssignableFrom(raw) && written.isJsonArray()) {
            return findInItems(itemType(type), written.getAsJsonArray(), path);
        }
        if (raw.isArray() && written.isJsonArray()) {
            return findInItems(raw.getComponentType(), written.getAsJsonArray(), path);
        }
        if (written.isJsonObject() && readsFields(raw)) {
            return findInOptions(raw, written.getAsJsonObject(), path);
        }
        return Optional.empty();
    }

    private static Optional<String> findInItems(Type itemType, JsonArray items, String path) {
        for (int index = 0; index < items.size(); index++) {
            Optional<String> found = find(itemType, items.get(index), path + "[" + index + "]");
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> findInOptions(Class<?> owner, JsonObject options, String path) {
        for (Class<?> type = owner; readsFields(type); type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                    continue;
                }
                String name = memberName(field);
                Optional<String> found = find(field.getGenericType(), options.get(name), path.isEmpty() ? name : path + "." + name);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    /** Whether Gson reads {@code written} as a number: a number, or a string that spells one. */
    private static boolean readsAsNumber(JsonElement written) {
        if (!written.isJsonPrimitive()) {
            return false;
        }
        if (written.getAsJsonPrimitive().isNumber()) {
            return true;
        }
        try {
            Double.parseDouble(written.getAsString());
            return true;
        } catch (NumberFormatException notANumber) {
            return false;
        }
    }

    private static boolean isNumber(Class<?> type) {
        return Number.class.isAssignableFrom(type)
                || (type.isPrimitive() && type != boolean.class && type != char.class && type != void.class);
    }

    private static Type itemType(Type collection) {
        return collection instanceof ParameterizedType parameterized && parameterized.getActualTypeArguments().length == 1
                ? parameterized.getActualTypeArguments()[0]
                : Object.class;
    }

    private static Class<?> rawClass(Type type) {
        if (type instanceof Class<?> raw) {
            return raw;
        }
        return type instanceof ParameterizedType parameterized && parameterized.getRawType() instanceof Class<?> raw ? raw : null;
    }

    private static boolean readsFields(Class<?> type) {
        if (type == null || type == Object.class || type.isPrimitive() || type.isArray() || type.isEnum() || type.isInterface()) {
            return false;
        }
        String name = type.getName();
        return LIBRARY_PACKAGES.stream().noneMatch(name::startsWith);
    }

    private static String memberName(Field field) {
        SerializedName serializedName = field.getAnnotation(SerializedName.class);
        return serializedName == null ? field.getName() : serializedName.value();
    }
}
