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

package art.arcane.iris.pack.schema;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.RegistryMapBlockState;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.json.JSONObject;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Function;

final class SchemaPropertyBuilder {
    private final SchemaBuilder builder;
    private final SchemaEnums enums;
    private final SchemaBlockStates blockStates;
    private final SchemaRegistryProperties registryProperties;

    SchemaPropertyBuilder(SchemaBuilder builder) {
        this.builder = builder;
        SchemaRegistryCatalog catalog = new SchemaRegistryCatalog(builder);
        this.enums = new SchemaEnums(builder);
        this.blockStates = new SchemaBlockStates(builder);
        this.registryProperties = new SchemaRegistryProperties(builder, catalog);
    }

    JSONObject buildProperty(Field k, Class<?> cl) {
        JSONObject prop = new JSONObject();
        String type = builder.getType(k.getType());
        KList<String> description = new KList<>();
        prop.put("!required", k.isAnnotationPresent(Required.class));
        prop.put("type", type);

        String fancyType = switch (type) {
            case "boolean" -> "Boolean";
            case "integer" -> integerProperty(k, prop, description);
            case "number" -> numberProperty(k, prop, description);
            case "string" -> stringProperty(k, prop, description);
            case "object" -> objectProperty(k, prop);
            case "array" -> arrayProperty(k, cl, prop, description);
            default -> unexpectedType(type, k, cl);
        };

        return describeProperty(k, cl, prop, type, fancyType, description);
    }

    private String integerProperty(Field k, JSONObject prop, KList<String> description) {
        if (k.isAnnotationPresent(MinNumber.class)) {
            int min = (int) k.getDeclaredAnnotation(MinNumber.class).value();
            prop.put("minimum", min);
            description.add(SchemaBuilder.SYMBOL_LIMIT__N + " Minimum allowed is " + min);
        }
        if (k.isAnnotationPresent(MaxNumber.class)) {
            int max = (int) k.getDeclaredAnnotation(MaxNumber.class).value();
            prop.put("maximum", max);
            description.add(SchemaBuilder.SYMBOL_LIMIT__N + " Maximum allowed is " + max);
        }
        return "Integer";
    }

    private String numberProperty(Field k, JSONObject prop, KList<String> description) {
        if (k.isAnnotationPresent(MinNumber.class)) {
            double min = k.getDeclaredAnnotation(MinNumber.class).value();
            prop.put("minimum", min);
            description.add(SchemaBuilder.SYMBOL_LIMIT__N + " Minimum allowed is " + min);
        }
        if (k.isAnnotationPresent(MaxNumber.class)) {
            double max = k.getDeclaredAnnotation(MaxNumber.class).value();
            prop.put("maximum", max);
            description.add(SchemaBuilder.SYMBOL_LIMIT__N + " Maximum allowed is " + max);
        }
        return "Number";
    }

    private String stringProperty(Field k, JSONObject prop, KList<String> description) {
        if (k.isAnnotationPresent(MinNumber.class)) {
            int min = (int) k.getDeclaredAnnotation(MinNumber.class).value();
            prop.put("minLength", min);
            description.add(SchemaBuilder.SYMBOL_LIMIT__N + " Minimum Length allowed is " + min);
        }
        if (k.isAnnotationPresent(MaxNumber.class)) {
            int max = (int) k.getDeclaredAnnotation(MaxNumber.class).value();
            prop.put("maxLength", max);
            description.add(SchemaBuilder.SYMBOL_LIMIT__N + " Maximum Length allowed is " + max);
        }

        String registryType = registryProperties.stringRegistryType(k, prop, description);
        if (registryType != null) {
            return registryType;
        }
        if (SchemaKeyedTypes.isKeyed(k.getType())) {
            return enums.addEnum(k.getType(), prop, description, SchemaKeyedTypes.values(k.getType()), Function.identity());
        }
        if (k.getType().isEnum()) {
            return enums.addEnum(k.getType(), prop, description, SchemaEnums.enumNames(k.getType()), Function.identity());
        }
        return "Text";
    }

    private String objectProperty(Field k, JSONObject prop) {
        if (k.isAnnotationPresent(RegistryMapBlockState.class)) {
            blockStates.applyBlockStateMap(prop, k, k.getDeclaredAnnotation(RegistryMapBlockState.class).value());
            return "Block State";
        }

        String key = "obj-" + k.getType().getCanonicalName().replaceAll("\\Q.\\E", "-").toLowerCase();
        if (!builder.definitions.containsKey(key)) {
            builder.definitions.put(key, new JSONObject());
            builder.definitions.put(key, builder.buildProperties(k.getType()));
        }
        prop.put("$ref", "#/definitions/" + key);
        return k.getType().getSimpleName().replaceAll("\\QIris\\E", "") + " (Object)";
    }

    private String arrayProperty(Field k, Class<?> cl, JSONObject prop, KList<String> description) {
        ArrayType t = k.getDeclaredAnnotation(ArrayType.class);
        if (t == null) {
            builder.warnings.add("Undefined array type for field " + k.getName() + " (" + k.getType().getSimpleName() + ") in class " + cl.getSimpleName());
            return "List of Something...?";
        }

        if (t.min() > 0) {
            prop.put("minItems", t.min());
            if (t.min() == 1) {
                description.add(SchemaBuilder.SYMBOL_LIMIT__N + " At least one entry must be defined, or just remove this list.");
            } else {
                description.add(SchemaBuilder.SYMBOL_LIMIT__N + " Requires at least " + t.min() + " entries.");
            }
        }

        return switch (builder.getType(t.type())) {
            case "integer" -> "List of Integers";
            case "number" -> "List of Numbers";
            case "object" -> objectArrayProperty(t, prop);
            case "string" -> stringArrayProperty(k, t, prop, description);
            default -> "List of Something...?";
        };
    }

    private String objectArrayProperty(ArrayType t, JSONObject prop) {
        String key = "obj-" + t.type().getCanonicalName().replaceAll("\\Q.\\E", "-").toLowerCase();
        if (!builder.definitions.containsKey(key)) {
            builder.definitions.put(key, new JSONObject());
            builder.definitions.put(key, builder.buildProperties(t.type()));
        }
        JSONObject items = new JSONObject();
        items.put("$ref", "#/definitions/" + key);
        prop.put("items", items);
        return "List of " + t.type().getSimpleName().replaceAll("\\QIris\\E", "") + "s (Objects)";
    }

    private String stringArrayProperty(Field k, ArrayType t, JSONObject prop, KList<String> description) {
        String registryType = registryProperties.stringArrayRegistryType(k, prop, description);
        if (registryType != null) {
            return registryType;
        }
        if (SchemaKeyedTypes.isKeyed(t.type())) {
            return enums.addEnumList(prop, description, t, SchemaKeyedTypes.values(t.type()), Function.identity());
        }
        if (t.type().isEnum()) {
            return enums.addEnumList(prop, description, t, SchemaEnums.enumNames(t.type()), Function.identity());
        }
        return "List of Text";
    }

    private String unexpectedType(String type, Field k, Class<?> cl) {
        builder.warnings.add("Unexpected Schema Type: " + type + " for field " + k.getName() + " (" + k.getType().getSimpleName() + ") in class " + cl.getSimpleName());
        return "Unknown Type";
    }

    private JSONObject describeProperty(Field k, Class<?> cl, JSONObject prop, String type, String fancyType, KList<String> description) {
        KList<String> d = new KList<>();
        d.add("<h>" + k.getName() + "</h>");
        d.add(builder.getFieldDescription(k) + "<hr></hr>");
        d.add("<h>" + fancyType + "</h>");
        String typeDesc = builder.getDescription(k.getType());
        boolean present = !typeDesc.isBlank();
        if (present) {
            d.add(typeDesc);
        }

        present = appendSnippetHint(k, d, present);
        appendDefaultValue(k, cl, d, present);

        description.forEach((g) -> d.add(g.trim()));
        String desc = d.toString("\n")
                .replace("<hr></hr>", "\n")
                .replace("<h>", "")
                .replace("</h>", "");
        String hDesc = d.toString("<br>");
        prop.put("type", type);
        prop.put("description", desc);
        prop.put("x-intellij-html-description", hDesc);
        return builder.buildSnippet(prop, k.getType());
    }

    private boolean appendSnippetHint(Field k, KList<String> d, boolean present) {
        Snippet snippet = k.getType().getDeclaredAnnotation(Snippet.class);
        if (snippet == null) {
            ArrayType array = k.getType().getDeclaredAnnotation(ArrayType.class);
            if (array != null) {
                snippet = array.type().getDeclaredAnnotation(Snippet.class);
            }
        }

        if (snippet == null) {
            return present;
        }

        String sm = snippet.value();
        if (present) {
            d.add("    ");
        }
        d.add("You can instead specify \"snippet/" + sm + "/some-name.json\" to use a snippet file instead of specifying it here.");
        return false;
    }

    private void appendDefaultValue(Field k, Class<?> cl, KList<String> d, boolean present) {
        Object instance;
        try {
            instance = cl.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError e) {
            return;
        }

        try {
            k.setAccessible(true);
            Object value = k.get(instance);

            if (value != null) {
                if (present) {
                    d.add("    ");
                }
                if (value instanceof List) {
                    d.add(SchemaBuilder.SYMBOL_LIMIT__N + " Default Value is an empty list");
                } else if (!k.getType().isPrimitive() && !(value instanceof Number) && !(value instanceof String) && !(value instanceof Enum<?>) && !SchemaKeyedTypes.isKeyed(k.getType())) {
                    d.add(SchemaBuilder.SYMBOL_LIMIT__N + " Default Value is a default object (create this object to see default properties)");
                } else {
                    d.add(SchemaBuilder.SYMBOL_LIMIT__N + " Default Value is " + value);
                }
            }
        } catch (Throwable e) {
            IrisLogging.reportError("Schema default value unavailable for "
                    + cl.getCanonicalName() + "." + k.getName(), e);
        }
    }
}
