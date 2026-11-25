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

package art.arcane.iris.studio.scene;

import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.volmlib.util.documentation.Description;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The settings of a scene entry as a form, with short lines about it. {@link #of} reads the plain numbers, switches and
 * choices of a settings object; a value typed into the form is carried by a request and applied to a copy with
 * {@link #withOverrides}, so the pack's files stay as they are. Settings nested in lists or other objects are not shown.
 */
public record SceneForm(String title, List<Field> fields, List<String> notes) {
    /** @param kind boolean, integer, decimal or enum; {@code choices} lists an enum's names */
    public record Field(String name, String description, String kind, Object value, Object defaultValue, Double minimum, Double maximum,
                        List<String> choices, boolean changed) {
    }

    /** @param hidden names of fields left out of the form */
    public static SceneForm of(String title, Object settings, Set<String> hidden, Map<String, String> overrides, List<String> notes) {
        Object fresh = newInstance(settings.getClass());
        List<Field> fields = new ArrayList<>();
        for (java.lang.reflect.Field field : settings.getClass().getDeclaredFields()) {
            String kind = kindOf(field);
            if (kind == null || hidden.contains(field.getName())) {
                continue;
            }
            fields.add(fieldOf(field, kind, settings, fresh, overrides.containsKey(field.getName())));
        }
        return new SceneForm(title, fields, notes);
    }

    /** A copy of the settings with the typed values set; a value that does not read as its field's type is ignored. */
    @SuppressWarnings("unchecked")
    public static <T> T withOverrides(T settings, Map<String, String> overrides) {
        T copy = (T) newInstance(settings.getClass());
        for (java.lang.reflect.Field field : settings.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            try {
                field.set(copy, field.get(settings));
                String typed = overrides.get(field.getName());
                if (typed != null && kindOf(field) != null) {
                    field.set(copy, parse(field.getType(), typed));
                }
            } catch (IllegalAccessException | IllegalArgumentException unreadable) {
                continue;
            }
        }
        return copy;
    }

    /** What the pack says about an enum constant, or its readable name when it says nothing. */
    public static String describe(Enum<?> constant) {
        try {
            Description description = constant.getDeclaringClass().getField(constant.name()).getAnnotation(Description.class);
            return description == null ? SceneEntry.readable(constant.name()) : description.value();
        } catch (NoSuchFieldException undocumented) {
            return SceneEntry.readable(constant.name());
        }
    }

    private static Field fieldOf(java.lang.reflect.Field field, String kind, Object settings, Object fresh, boolean changed) {
        field.setAccessible(true);
        try {
            Description description = field.getAnnotation(Description.class);
            MinNumber minimum = field.getAnnotation(MinNumber.class);
            MaxNumber maximum = field.getAnnotation(MaxNumber.class);
            List<String> choices = field.getType().isEnum()
                    ? Arrays.stream(field.getType().getEnumConstants()).map(constant -> ((Enum<?>) constant).name()).toList()
                    : List.of();
            return new Field(field.getName(), description == null ? "" : description.value(), kind, plain(field.get(settings)),
                    plain(field.get(fresh)), minimum == null ? null : minimum.value(), maximum == null ? null : maximum.value(), choices, changed);
        } catch (IllegalAccessException unreadable) {
            throw new IllegalStateException(unreadable);
        }
    }

    private static Object plain(Object value) {
        return value instanceof Enum<?> constant ? constant.name() : value;
    }

    private static String kindOf(java.lang.reflect.Field field) {
        if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
            return null;
        }
        Class<?> type = field.getType();
        if (type == boolean.class) {
            return "boolean";
        }
        if (type == int.class || type == long.class) {
            return "integer";
        }
        if (type == double.class || type == float.class) {
            return "decimal";
        }
        return type.isEnum() ? "enum" : null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object parse(Class<?> type, String text) {
        if (type == boolean.class) {
            return Boolean.parseBoolean(text);
        }
        if (type == int.class) {
            return (int) Math.round(Double.parseDouble(text));
        }
        if (type == long.class) {
            return Math.round(Double.parseDouble(text));
        }
        if (type == double.class) {
            return Double.parseDouble(text);
        }
        if (type == float.class) {
            return (float) Double.parseDouble(text);
        }
        return Enum.valueOf((Class<? extends Enum>) type, text);
    }

    private static Object newInstance(Class<?> type) {
        try {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException noPlainConstructor) {
            throw new IllegalStateException(type.getSimpleName() + " has no plain constructor", noPlainConstructor);
        }
    }
}
