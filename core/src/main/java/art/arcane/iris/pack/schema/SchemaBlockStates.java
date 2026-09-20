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

import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.volmlib.nativelib.terrain.NativeBlockProperty;
import art.arcane.volmlib.nativelib.terrain.NativeNumericRange;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class SchemaBlockStates {
    private final SchemaBuilder builder;

    SchemaBlockStates(SchemaBuilder builder) {
        this.builder = builder;
    }

    void applyBlockStateMap(JSONObject prop, Field k, String blockType) {
        prop.put("!top", true);
        JSONArray any = new JSONArray();
        prop.put("anyOf", any);

        for (BlockStateGroup group : reconstructBlockStateGroups()) {
            List<String> blocks = group.blocks();
            if (blocks.isEmpty()) {
                continue;
            }

            String raw = blocks.get(0).replace(':', '_');
            String enumKey = "enum-block-state-" + raw;
            String propertiesKey = "obj-block-state-" + raw;

            any.put(new JSONObject()
                    .put("if", new JSONObject()
                            .put("properties", new JSONObject()
                                    .put(blockType, new JSONObject()
                                            .put("type", "string")
                                            .put("$ref", definitionReference(enumKey)))))
                    .put("then", new JSONObject()
                            .put("properties", new JSONObject()
                                    .put(k.getName(), new JSONObject()
                                            .put("type", "object")
                                            .put("$ref", definitionReference(propertiesKey)))))
                    .put("else", false));

            if (!builder.definitions.containsKey(enumKey)) {
                JSONArray filters = new JSONArray();
                blocks.forEach(filters::put);

                builder.definitions.put(enumKey, new JSONObject()
                        .put("type", "string")
                        .put("enum", filters));
            }

            if (!builder.definitions.containsKey(propertiesKey)) {
                JSONObject props = new JSONObject();
                for (NativeBlockProperty property : group.properties()) {
                    props.put(property.name(), buildBlockPropertyJson(property));
                }

                builder.definitions.put(propertiesKey, new JSONObject()
                        .put("type", "object")
                        .put("properties", props));
            }
        }
    }

    private static String definitionReference(String key) {
        return "#/definitions/" + key.replace("~", "~0").replace("/", "~1");
    }

    private static JSONObject buildBlockPropertyJson(NativeBlockProperty property) {
        JSONObject json = new JSONObject();
        json.put("type", property.jsonType());
        json.put("default", property.defaultValue());
        List<Object> allowed = property.allowedValues();
        if (!allowed.isEmpty()) {
            json.put("enum", new JSONArray(allowed));
        }
        if (property.hasNumericRange()) {
            NativeNumericRange range = property.numericRange();
            if ("integer".equals(property.jsonType())) {
                json.put("minimum", (long) range.minimum());
                json.put("maximum", (long) range.maximum());
            } else {
                json.put("minimum", range.minimum());
                json.put("maximum", range.maximum());
            }
            json.put("exclusiveMinimum", range.exclusiveMinimum());
            json.put("exclusiveMaximum", range.exclusiveMaximum());
        }
        return json;
    }

    private static List<BlockStateGroup> reconstructBlockStateGroups() {
        List<BlockStateGroup> groups = new ArrayList<>();
        List<NativeBlockProperty> currentProperties = null;
        List<String> currentBlocks = null;
        for (Map.Entry<String, List<NativeBlockProperty>> entry : IrisPlatforms.get().registries().blockStateProperties().entrySet()) {
            List<NativeBlockProperty> value = entry.getValue();
            if (currentProperties != null && value == currentProperties) {
                currentBlocks.add(entry.getKey());
            } else {
                currentBlocks = new ArrayList<>();
                currentBlocks.add(entry.getKey());
                currentProperties = value;
                groups.add(new BlockStateGroup(currentBlocks, value));
            }
        }
        return groups;
    }

    private record BlockStateGroup(List<String> blocks, List<NativeBlockProperty> properties) {
    }
}
