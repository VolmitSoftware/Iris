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
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.function.Function;

final class SchemaEnums {
    private final SchemaBuilder builder;

    SchemaEnums(SchemaBuilder builder) {
        this.builder = builder;
    }

    @NotNull
    <T> String addEnumList(JSONObject prop, KList<String> description, ArrayType t, T[] values, Function<T, String> function) {
        JSONObject items = new JSONObject();
        String s = addEnum(t.type(), items, description, values, function);
        prop.put("items", items);

        return "List of " + s + "s";
    }

    static String[] enumNames(Class<?> enumType) {
        try {
            Object[] constants = enumType.getEnumConstants();
            String[] names = new String[constants.length];
            for (int index = 0; index < constants.length; index++) {
                names[index] = ((Enum<?>) constants[index]).name();
            }
            return names;
        } catch (LinkageError error) {
            return Arrays.stream(enumType.getDeclaredFields())
                    .filter(Field::isEnumConstant)
                    .map(Field::getName)
                    .toArray(String[]::new);
        }
    }

    @NotNull
    <T> String addEnum(Class<?> type, JSONObject prop, KList<String> description, T[] values, Function<T, String> function) {
        JSONArray a = new JSONArray();
        boolean advanced = type.isAnnotationPresent(Description.class);
        for (T gg : values) {
            if (advanced) {
                try {
                    JSONObject j = new JSONObject();
                    String name = function.apply(gg);
                    j.put("const", name);
                    Description dd = type.getField(name).getAnnotation(Description.class);
                    String desc = dd == null ? ("No Description for " + name) : dd.value();
                    j.put("description", desc);
                    j.put("x-intellij-html-description", desc.replace("\n", "<br>"));
                    a.put(j);
                } catch (Throwable e) {
                    IrisLogging.reportError(e);
                }
            } else {
                a.put(function.apply(gg));
            }
        }

        String key = (advanced ? "oneof-" : "") + "enum-" + type.getCanonicalName().replaceAll("\\Q.\\E", "-").toLowerCase();

        if (!builder.definitions.containsKey(key)) {
            JSONObject j = new JSONObject();
            j.put(advanced ? "oneOf" : "enum", a);
            builder.definitions.put(key, j);
        }

        prop.put("$ref", "#/definitions/" + key);
        description.add(SchemaBuilder.SYMBOL_TYPE__N + "  Must be a valid " + type.getSimpleName().replaceAll("\\QIris\\E", "") + " (use ctrl+space for auto complete!)");
        return type.getSimpleName().replaceAll("\\QIris\\E", "");
    }
}
