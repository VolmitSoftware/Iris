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

package art.arcane.iris.studio.bridge;

import art.arcane.iris.pack.loading.IrisRegistrant;
import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.RegistryListBlockType;
import art.arcane.iris.pack.schema.annotation.RegistryListResource;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.iris.platform.reflect.KeyedType;
import art.arcane.volmlib.util.documentation.Description;
import com.google.gson.Gson;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The settings types of the pack as the node editor shows them, read from the pack's classes and the same annotations
 * the pack's schema is written from: each setting's kind, limits, default and description.
 */
final class SettingsTypes {
    /** The reference a block setting names, which the studio asks {@link StudioSuggestions} the blocks of. */
    static final String BLOCK_REFERENCE = "BLOCK_TYPE";
    private static final String IRIS_PACKAGE = "art.arcane.iris.";
    private static final String PLAIN_FORMAT = "PLAIN";
    private static final String KIND = "kind";
    private static final Gson GSON = new Gson();

    private SettingsTypes() {
    }

    /** {@code roots} and the types inside them, by class name. */
    static Map<String, TypeWire> reachableFrom(Collection<Class<?>> roots) {
        Map<String, TypeWire> described = new LinkedHashMap<>();
        Deque<Class<?>> pending = new ArrayDeque<>(roots);
        while (!pending.isEmpty()) {
            Class<?> type = pending.pop();
            if (described.containsKey(type.getName())) {
                continue;
            }
            Object defaults = newInstanceOrNull(type);
            List<OptionWire> options = settingsOf(type).stream().map(field -> optionOf(field, defaults, pending)).toList();
            described.put(type.getName(), new TypeWire(descriptionOf(type.getAnnotation(Description.class)), options));
        }
        return described;
    }

    /** Whether a setting of {@code type} is an object of settings of its own, such as a palette, rather than a plain value. */
    private static boolean isSettingsObject(Class<?> type) {
        return type.getName().startsWith(IRIS_PACKAGE) && !type.isEnum() && !type.isArray()
                && !KeyedType.isKeyed(type) && !Collection.class.isAssignableFrom(type) && !Map.class.isAssignableFrom(type);
    }

    // The fields a file can set, a parent class's first, as the pack reads them.
    private static List<Field> settingsOf(Class<?> type) {
        List<Field> settings = new ArrayList<>();
        Class<?> parent = type.getSuperclass();
        if (parent != null && parent.getName().startsWith(IRIS_PACKAGE)) {
            settings.addAll(settingsOf(parent));
        }
        Arrays.stream(type.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())
                        && !Modifier.isTransient(field.getModifiers()))
                .forEach(settings::add);
        return settings;
    }

    private static OptionWire optionOf(Field field, Object defaults, Deque<Class<?>> pending) {
        MinNumber minimum = field.getAnnotation(MinNumber.class);
        MaxNumber maximum = field.getAnnotation(MaxNumber.class);
        ArrayType arrayType = field.getAnnotation(ArrayType.class);
        Integer minItems = arrayType == null || arrayType.min() <= 0 ? null : arrayType.min();
        return new OptionWire(field.getName(), descriptionOf(field.getAnnotation(Description.class)), field.isAnnotationPresent(Required.class),
                minimum == null ? null : minimum.value(), maximum == null ? null : maximum.value(), minItems,
                defaultOf(field, defaults), kindOf(field, pending));
    }

    private static Map<String, Object> kindOf(Field field, Deque<Class<?>> pending) {
        Class<?> type = field.getType();
        if (Collection.class.isAssignableFrom(type)) {
            return listOf(kindOfValue(elementTypeOf(field), field, pending));
        }
        if (Map.class.isAssignableFrom(type)) {
            return wire("map", "value", kindOfValue(typeArgument(field, 1), field, pending));
        }
        return kindOfValue(type, field, pending);
    }

    // A list's or map's elements take the registry the field names, as a list of blocks does.
    private static Map<String, Object> kindOfValue(Class<?> type, Field field, Deque<Class<?>> pending) {
        if (type == null) {
            return wire("any");
        }
        if (type == boolean.class || type == Boolean.class) {
            return wire("boolean");
        }
        if (type == int.class || type == long.class || type == short.class || type == byte.class || type == Integer.class || type == Long.class) {
            return wire("integer");
        }
        if (type == double.class || type == float.class || type == Double.class || type == Float.class) {
            return wire("decimal");
        }
        if (type == String.class) {
            return textKindOf(field);
        }
        if (type.isEnum()) {
            return wire("enum", "constants", Arrays.stream(type.getEnumConstants()).map(constant -> constantOf((Enum<?>) constant)).toList());
        }
        if (KeyedType.isKeyed(type)) {
            return wire("enum", "constants", Arrays.stream(KeyedType.values(type)).map(name -> Map.of("name", name, "description", "")).toList());
        }
        if (isSettingsObject(type)) {
            pending.add(type);
            return wire("object", "type", type.getName());
        }
        return wire("any");
    }

    private static Map<String, Object> textKindOf(Field field) {
        if (field.isAnnotationPresent(RegistryListBlockType.class)) {
            return wire("reference", "reference", BLOCK_REFERENCE, "format", PLAIN_FORMAT);
        }
        RegistryListResource resource = field.getAnnotation(RegistryListResource.class);
        String folder = resource == null ? null : folderOf(resource.value());
        if (folder != null) {
            return wire("resource", "resource", folder);
        }
        return wire("text", "format", PLAIN_FORMAT);
    }

    private static String folderOf(Class<? extends IrisRegistrant> registrant) {
        Object instance = newInstanceOrNull(registrant);
        return instance instanceof IrisRegistrant resource ? resource.getFolderName() : null;
    }

    private static Map<String, Object> listOf(Map<String, Object> element) {
        Map<String, Object> list = wire("list", "element", element);
        list.put("unique", false);
        return list;
    }

    private static Class<?> elementTypeOf(Field field) {
        ArrayType arrayType = field.getAnnotation(ArrayType.class);
        return arrayType != null ? arrayType.type() : typeArgument(field, 0);
    }

    private static Class<?> typeArgument(Field field, int index) {
        if (!(field.getGenericType() instanceof ParameterizedType parameterized) || parameterized.getActualTypeArguments().length <= index) {
            return null;
        }
        Type argument = parameterized.getActualTypeArguments()[index];
        return argument instanceof Class<?> type ? type : null;
    }

    private static Map<String, String> constantOf(Enum<?> constant) {
        try {
            Description description = constant.getDeclaringClass().getField(constant.name()).getAnnotation(Description.class);
            return Map.of("name", constant.name(), "description", descriptionOf(description));
        } catch (NoSuchFieldException undocumented) {
            return Map.of("name", constant.name(), "description", "");
        }
    }

    // Only plain values are shown as defaults; an object or list is described by its own settings.
    private static Object defaultOf(Field field, Object defaults) {
        if (defaults == null) {
            return null;
        }
        Object value;
        try {
            field.setAccessible(true);
            value = field.get(defaults);
        } catch (ReflectiveOperationException | RuntimeException unreadable) {
            return null;
        }
        return switch (value) {
            case null -> null;
            case Boolean plain -> plain;
            case Number plain -> plain;
            case String plain -> plain;
            case Enum<?> constant -> constant.name();
            default -> null;
        };
    }

    private static Object newInstanceOrNull(Class<?> type) {
        try {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError noPlainConstructor) {
            return null;
        }
    }

    private static String descriptionOf(Description description) {
        return description == null ? "" : description.value();
    }

    private static Map<String, Object> wire(String kind, Object... members) {
        Map<String, Object> wire = new LinkedHashMap<>();
        wire.put(KIND, kind);
        for (int index = 0; index + 1 < members.length; index += 2) {
            wire.put((String) members[index], members[index + 1]);
        }
        return wire;
    }

    /** A settings type: what it is for, and the settings a file of it may write. */
    record TypeWire(String description, List<OptionWire> options) {
    }

    /** One setting; a limit or default the pack does not give is null. */
    record OptionWire(String name, String description, boolean required, Double minimum, Double maximum, Integer minItems,
                      Object defaultValue, Map<String, Object> kind) {
    }
}
