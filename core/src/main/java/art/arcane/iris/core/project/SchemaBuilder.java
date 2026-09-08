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

package art.arcane.iris.core.project;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.Required;
import art.arcane.iris.engine.object.annotations.Snippet;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Modifier;
import java.util.Map;

public class SchemaBuilder {
    static final String SYMBOL_LIMIT__N = "*";
    static final String SYMBOL_TYPE__N = "";
    final KMap<String, JSONObject> definitions;
    final KList<String> warnings;
    final IrisData data;
    private final Class<?> root;
    private final SchemaPropertyBuilder propertyBuilder;

    public SchemaBuilder(Class<?> root, IrisData data) {
        this.data = data;
        warnings = new KList<>();
        this.definitions = new KMap<>();
        this.root = root;
        this.propertyBuilder = new SchemaPropertyBuilder(this);
    }

    public JSONObject construct() {
        JSONObject schema = new JSONObject();
        schema.put("$schema", "http://json-schema.org/draft-07/schema#");
        schema.put("$id", "https://volmit.com/iris-schema/" + root.getSimpleName().toLowerCase() + ".json");

        JSONObject props = buildProperties(root);

        for (String i : props.keySet()) {
            if (!schema.has(i)) {
                schema.put(i, props.get(i));
            }
        }

        JSONObject defs = new JSONObject();

        for (Map.Entry<String, JSONObject> entry : definitions.entrySet()) {
            defs.put(entry.getKey(), entry.getValue());
        }

        schema.put("definitions", defs);

        for (String i : warnings) {
            IrisLogging.warn(root.getSimpleName() + ": " + i);
        }

        return schema;
    }

    JSONObject buildProperties(Class<?> c) {
        JSONObject o = new JSONObject();
        JSONObject properties = new JSONObject();
        String desc = getDescription(c);
        o.put("description", desc);
        o.put("x-intellij-html-description", desc.replace("\n", "<br>"));
        o.put("type", getType(c));
        JSONArray required = new JSONArray();
        JSONArray extended = new JSONArray();

        Class<?> parent = c.getSuperclass();
        while (parent != null && IrisRegistrant.class.isAssignableFrom(parent)) {
            buildProperties(properties, required, extended, parent);
            parent = parent.getSuperclass();
        }

        buildProperties(properties, required, extended, c);

        if (required.length() > 0) {
            o.put("required", required);
        }
        if (extended.length() > 0) {
            o.put("allOf", extended);
        }

        o.put("properties", properties);


        return buildSnippet(o, c);
    }

    private void buildProperties(JSONObject properties, JSONArray required, JSONArray extended, Class<?> c) {
        for (Field k : c.getDeclaredFields()) {
            if (Modifier.isStatic(k.getModifiers()) || Modifier.isFinal(k.getModifiers()) || Modifier.isTransient(k.getModifiers())) {
                continue;
            }

            try {
                k.setAccessible(true);
            } catch (InaccessibleObjectException e) {
                continue;
            }

            JSONObject property = propertyBuilder.buildProperty(k, c);

            if (Boolean.TRUE == property.remove("!top")) {
                extended.put(property);
                continue;
            }

            if (Boolean.TRUE == property.remove("!required")) {
                required.put(k.getName());
            }

            properties.put(k.getName(), property);
        }
    }

    JSONObject buildSnippet(JSONObject prop, Class<?> type) {
        Snippet snippet = type.getDeclaredAnnotation(Snippet.class);
        if (snippet == null) return prop;

        JSONObject anyOf = new JSONObject();
        JSONArray arr = new JSONArray();
        JSONObject str = new JSONObject();
        str.put("type", "string");
        String key = "enum-snippet-" + snippet.value();
        str.put("$ref", "#/definitions/" + key);

        if (!definitions.containsKey(key)) {
            JSONObject enumObj = new JSONObject();
            JSONArray snl = new JSONArray();
            data.getPossibleSnippets(snippet.value()).forEach(snl::put);
            enumObj.put("enum", snl);
            JSONObject patternObj = new JSONObject();
            patternObj.put("type", "string");
            patternObj.put("pattern", "^snippet/" + snippet.value() + "/");
            JSONArray snippetAlt = new JSONArray();
            snippetAlt.put(enumObj);
            snippetAlt.put(patternObj);
            JSONObject j = new JSONObject();
            j.put("anyOf", snippetAlt);
            definitions.put(key, j);
        }

        arr.put(prop);
        arr.put(str);
        str.put("description", prop.getString("description"));
        str.put("x-intellij-html-description", prop.getString("x-intellij-html-description"));
        anyOf.put("anyOf", arr);
        anyOf.put("description", prop.getString("description"));
        anyOf.put("x-intellij-html-description", prop.getString("x-intellij-html-description"));
        anyOf.put("!required", Boolean.TRUE.equals(prop.remove("!required")) || type.isAnnotationPresent(Required.class));

        return anyOf;
    }

    String getType(Class<?> c) {
        if (c.equals(int.class) || c.equals(Integer.class) || c.equals(long.class) || c.equals(Long.class)) {
            return "integer";
        }

        if (c.equals(float.class) || c.equals(double.class) || c.equals(Float.class) || c.equals(Double.class)) {
            return "number";
        }

        if (c.equals(boolean.class) || c.equals(Boolean.class)) {
            return "boolean";
        }

        if (c.equals(String.class) || c.isEnum() || SchemaKeyedTypes.isKeyed(c)) {
            return "string";
        }

        if (c.equals(KList.class)) {
            return "array";
        }

        if (c.equals(KMap.class)) {
            return "object";
        }

        if (!c.isAnnotationPresent(Desc.class) && c.getCanonicalName().startsWith("art.arcane.iris.")) {
            warnings.addIfMissing("Unsupported Type: " + c.getCanonicalName() + " Did you forget @Desc?");
        }

        return "object";
    }

    String getFieldDescription(Field r) {

        if (r.isAnnotationPresent(Desc.class)) {
            return r.getDeclaredAnnotation(Desc.class).value();
        }

        warnings.addIfMissing("Missing @Desc on field " + r.getName() + " (" + r.getType() + ") in " + r.getDeclaringClass().getCanonicalName());
        return "No Field Description";
    }

    String getDescription(Class<?> r) {
        if (r.isAnnotationPresent(Desc.class)) {
            return r.getDeclaredAnnotation(Desc.class).value();
        }

        if (!r.isPrimitive() && !r.equals(KList.class) && !r.equals(KMap.class) && r.getCanonicalName().startsWith("art.arcane.")) {
            warnings.addIfMissing("Missing @Desc on " + r.getSimpleName() + " in " + (r.getDeclaringClass() != null ? r.getDeclaringClass().getCanonicalName() : " NOSRC"));
        }
        return "";
    }
}
