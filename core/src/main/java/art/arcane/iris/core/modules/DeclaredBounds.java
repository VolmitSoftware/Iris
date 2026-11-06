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

package art.arcane.iris.core.modules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import com.volmit.config.JsonForms;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinItems;
import com.volmit.config.MinNumber;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The bounds IrisApi's {@link MinNumber}, {@link MaxNumber} and {@link MinItems} declare, enforced on decoded settings.
 * Only options the JSON writes are checked: a default is the module's own choice.
 */
final class DeclaredBounds {
    private static final List<String> LIBRARY_PACKAGES = List.of("java.", "javax.", "com.google.gson.");

    private DeclaredBounds() {
    }

    /**
     * Checks every option of {@code decoded} that {@code written} holds, and the options of the settings objects
     * below it, naming each as {@code path.option}, or {@code option} for an empty path.
     *
     * @throws JsonParseException naming the first option out of its bounds
     */
    static void require(Object decoded, JsonObject written, String path) {
        for (Class<?> type = decoded.getClass(); readsFields(type); type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                    continue;
                }
                JsonElement member = written.get(memberName(field));
                if (member != null) {
                    requireOption(decoded, field, member, path.isEmpty() ? memberName(field) : path + "." + memberName(field));
                }
            }
        }
    }

    /**
     * Checks the option {@code field} of {@code owner}, written as {@code written} and named {@code path}, such as the
     * root option of a rooted feature, which the pack writes as the whole value under the feature's key.
     */
    static void requireOption(Object owner, Field field, JsonElement written, String path) {
        Object value = valueOf(field, owner);
        requireWithinBounds(field, value, path);
        requireNested(value, written, path);
    }

    private static void requireWithinBounds(Field field, Object value, String path) {
        MinNumber minimum = field.getAnnotation(MinNumber.class);
        if (minimum != null && value instanceof Number number && number.doubleValue() < minimum.value()) {
            throw new JsonParseException(path + " is " + number + "; it must be at least " + label(minimum.value()));
        }
        MaxNumber maximum = field.getAnnotation(MaxNumber.class);
        if (maximum != null && value instanceof Number number && number.doubleValue() > maximum.value()) {
            throw new JsonParseException(path + " is " + number + "; it must be at most " + label(maximum.value()));
        }
        requireMinimumItems(field, value, path);
    }

    private static void requireMinimumItems(Field field, Object value, String path) {
        MinItems minimumItems = field.getAnnotation(MinItems.class);
        if (minimumItems == null || value == null) {
            return;
        }
        int count = itemsOf(value).size();
        if (count < minimumItems.value()) {
            throw new JsonParseException(path + " has " + count + " items; it must have at least " + minimumItems.value());
        }
    }

    private static void requireNested(Object value, JsonElement written, String path) {
        if (value == null) {
            return;
        }
        if (written.isJsonArray()) {
            requireItems(value, written.getAsJsonArray(), path);
            return;
        }
        if (written.isJsonObject() && readsFields(value.getClass())) {
            require(value, written.getAsJsonObject(), path);
        }
    }

    private static void requireItems(Object value, JsonArray written, String path) {
        List<?> items = itemsOf(value);
        for (int index = 0; index < Math.min(items.size(), written.size()); index++) {
            Object item = items.get(index);
            JsonElement writtenItem = written.get(index);
            if (item != null && writtenItem.isJsonObject() && readsFields(item.getClass())) {
                require(item, writtenItem.getAsJsonObject(), path + "[" + index + "]");
            }
        }
    }

    private static boolean readsFields(Class<?> type) {
        if (type == null || type == Object.class || type.isPrimitive() || type.isArray() || type.isEnum()
                || type.isInterface() || type.isAnnotationPresent(JsonForms.class)) {
            return false;
        }
        String name = type.getName();
        return LIBRARY_PACKAGES.stream().noneMatch(name::startsWith);
    }

    private static String memberName(Field field) {
        SerializedName serializedName = field.getAnnotation(SerializedName.class);
        return serializedName == null ? field.getName() : serializedName.value();
    }

    private static Object valueOf(Field field, Object owner) {
        try {
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException | RuntimeException unreadable) {
            return null;
        }
    }

    private static List<?> itemsOf(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        if (value instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        if (!value.getClass().isArray()) {
            return List.of();
        }
        List<Object> items = new ArrayList<>();
        for (int index = 0; index < Array.getLength(value); index++) {
            items.add(Array.get(value, index));
        }
        return items;
    }

    private static String label(double bound) {
        return bound == Math.rint(bound) && Math.abs(bound) < 1e15 ? Long.toString((long) bound) : Double.toString(bound);
    }
}
