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

package art.arcane.iris.core.project;

import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import com.volmit.FeatureAttachment.Target;
import com.volmit.config.Reference;
import com.volmit.host.schema.FeatureSchema;
import com.volmit.host.schema.ObjectType;
import com.volmit.host.schema.PackSchema;
import com.volmit.host.schema.SchemaOption;
import com.volmit.host.schema.TextFormat;
import com.volmit.host.schema.ValueKind;
import com.volmit.resource.ResourceId;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Renders the settings a pack's modules add, described by IrisApi as a {@link PackSchema}, into the JSON
 * schema studio writes: each feature as a property of its target under its id and, for built-ins, its
 * alias, each settings class once as a definition, references as completion lists (resource references as the keys
 * of the module resource kind they name). Settings with one option also accept that option's bare value; Iris's own
 * classes in built-in settings use Iris's own definitions, and so do IrisApi's value types, which read the same JSON as
 * an Iris class ({@code BlockSpec} as Iris's block data), with the snippet references their {@code @Snippet} allows.
 */
final class ModuleSchemaRenderer {
    private static final String DEFINITION_PREFIX = "module-";
    private static final String DEFINITION_REF = "#/definitions/";
    private static final String RESOURCE_ID_PATTERN = "^[a-z0-9_.-]+:[a-z0-9_./-]+$";
    private static final String BLOCK_STATE_PATTERN = "^[a-z0-9_.-]+:[a-z0-9_./-]+(\\[[a-z0-9_]+=[a-z0-9_.-]+(,[a-z0-9_]+=[a-z0-9_.-]+)*\\])?$";
    private static final String SNIPPET_PATTERN = "^snippet/";

    /** Points a string property at the completion list of a reference. */
    interface ReferenceCompletions {
        void complete(JSONObject property, Reference reference, TextFormat format);
    }

    /** Points a string property at the keys of the pack's resources of a module resource kind. */
    interface ResourceCompletions {
        void complete(JSONObject property, ResourceId kind);
    }

    /** The definition key of Iris's own description of one of its classes, built the first time it is asked for. */
    interface HostDefinitions {
        String definition(String typeName);
    }

    private final PackSchema schema;
    private final Map<String, JSONObject> definitions;
    private final ReferenceCompletions references;
    private final ResourceCompletions resources;
    private final HostDefinitions hostDefinitions;
    private final Function<String, Optional<String>> sameJsonHostTypes;
    private final Function<String, List<String>> snippetFiles;

    /**
     * @param sameJsonHostTypes the Iris class that reads the same JSON as a module type, for IrisApi's value types
     * @param snippetFiles lists the snippet references a pack has for a snippet folder, such as {@code snippet/thick}
     */
    ModuleSchemaRenderer(PackSchema schema, Map<String, JSONObject> definitions, ReferenceCompletions references,
                         ResourceCompletions resources, HostDefinitions hostDefinitions,
                         Function<String, Optional<String>> sameJsonHostTypes, Function<String, List<String>> snippetFiles) {
        this.schema = schema;
        this.definitions = definitions;
        this.references = references;
        this.resources = resources;
        this.hostDefinitions = hostDefinitions;
        this.sameJsonHostTypes = sameJsonHostTypes;
        this.snippetFiles = snippetFiles;
    }

    /**
     * Adds every feature at {@code target} to {@code properties} under its id and alias, and to
     * {@code constraints} that one JSON object may not hold both.
     */
    void addFeatures(Target target, JSONObject properties, JSONArray constraints) {
        for (FeatureSchema feature : schema.fields(target)) {
            JSONObject property = featureProperty(feature);
            properties.put(feature.key(), property);
            feature.alias().ifPresent(alias -> {
                properties.put(alias, property);
                constraints.put(notBoth(alias, feature.key()));
            });
        }
    }

    /** The schema of a file that holds one object of {@code type}: a snippet file, or a file of a module resource kind. */
    JSONObject fileSchema(ObjectType type) {
        JSONObject file = objectSchema(type);
        file.put("$schema", "http://json-schema.org/draft-07/schema#");
        JSONObject fileDefinitions = new JSONObject();
        definitions.forEach(fileDefinitions::put);
        file.put("definitions", fileDefinitions);
        return file;
    }

    /** The settings object, or with exactly one option also that option's bare value. */
    private JSONObject featureProperty(FeatureSchema feature) {
        JSONObject object = new JSONObject();
        object.put("$ref", DEFINITION_REF + definition(feature.settings().name()));
        JSONObject property = feature.shorthand()
                .map(option -> new JSONObject().put("anyOf", new JSONArray(List.of(object, optionSchema(option)))))
                .orElse(object);
        String description = feature.name() + " (" + feature.id() + "), from module " + feature.module().name()
                + " (" + feature.module().id() + ")" + (feature.settings().description().isEmpty() ? "" : ". " + feature.settings().description());
        property.put("description", description);
        return property;
    }

    private static JSONObject notBoth(String alias, String key) {
        JSONObject both = new JSONObject();
        both.put("required", new JSONArray(List.of(alias, key)));
        JSONObject not = new JSONObject();
        not.put("not", both);
        return not;
    }

    /** The definition key of a settings class, built the first time a property names it. */
    private String definition(String typeName) {
        String key = DEFINITION_PREFIX + typeName.replace('.', '-').replace('$', '-').toLowerCase(Locale.ROOT);
        if (definitions.containsKey(key)) {
            return key;
        }
        definitions.put(key, new JSONObject());
        ObjectType type = schema.type(typeName);
        JSONObject object = describedObject(type);
        definitions.put(key, type.snippet().isPresent() ? objectOrSnippet(object, type) : object);
        return key;
    }

    /** Iris's own description where an Iris class reads the same JSON, otherwise the module's. */
    private JSONObject describedObject(ObjectType type) {
        return sameJsonHostTypes.apply(type.name())
                .map(hostType -> new JSONObject().put("$ref", DEFINITION_REF + hostDefinitions.definition(hostType)))
                .orElseGet(() -> objectSchema(type));
    }

    private JSONObject objectOrSnippet(JSONObject object, ObjectType type) {
        String folder = type.snippet().orElseThrow();
        JSONObject reference = new JSONObject();
        reference.put("type", "string");
        JSONArray files = new JSONArray(List.<Object>copyOf(snippetFiles.apply(folder)));
        if (files.length() > 0) {
            reference.put("examples", files);
        }
        reference.put("pattern", SNIPPET_PATTERN);
        reference.put("description", "A snippet file under snippet/" + folder + "/, written as snippet/<name>");
        JSONObject either = new JSONObject();
        either.put("anyOf", new JSONArray(List.of(object, reference)));
        either.put("description", type.description());
        return either;
    }

    private JSONObject objectSchema(ObjectType type) {
        JSONObject object = new JSONObject();
        object.put("type", "object");
        object.put("description", type.description());
        JSONObject properties = new JSONObject();
        JSONArray required = new JSONArray();
        for (SchemaOption option : type.options()) {
            properties.put(option.name(), optionSchema(option));
            if (option.required()) {
                required.put(option.name());
            }
        }
        object.put("properties", properties);
        if (required.length() > 0) {
            object.put("required", required);
        }
        return object;
    }

    private JSONObject optionSchema(SchemaOption option) {
        JSONObject property = valueSchema(option.kind());
        if (!option.description().isEmpty()) {
            property.put("description", option.description());
        }
        option.minimum().ifPresent(minimum -> property.put("minimum", minimum));
        option.maximum().ifPresent(maximum -> property.put("maximum", maximum));
        option.defaultValue().ifPresent(value -> property.put("default", JSONObject.wrap(value)));
        return property;
    }

    private JSONObject valueSchema(ValueKind kind) {
        JSONObject value = new JSONObject();
        switch (kind) {
            case ValueKind.BooleanKind ignored -> value.put("type", "boolean");
            case ValueKind.IntegerKind ignored -> value.put("type", "integer");
            case ValueKind.DecimalKind ignored -> value.put("type", "number");
            case ValueKind.TextKind text -> {
                value.put("type", "string");
                patternOf(text.format()).ifPresent(pattern -> value.put("pattern", pattern));
            }
            case ValueKind.ReferenceKind reference -> {
                value.put("type", "string");
                references.complete(value, reference.reference(), reference.format());
            }
            case ValueKind.ResourceReferenceKind resource -> {
                value.put("type", "string");
                resources.complete(value, resource.kind());
            }
            case ValueKind.EnumKind enumKind -> {
                value.put("type", "string");
                value.put("enum", new JSONArray(enumKind.constants().stream().<Object>map(ValueKind.EnumConstant::name).toList()));
                value.put("enumDescriptions", new JSONArray(enumKind.constants().stream().<Object>map(ValueKind.EnumConstant::description).toList()));
            }
            case ValueKind.ListKind list -> {
                value.put("type", "array");
                value.put("items", valueSchema(list.element()));
                if (list.unique()) {
                    value.put("uniqueItems", true);
                }
            }
            case ValueKind.MapKind map -> {
                value.put("type", "object");
                value.put("additionalProperties", valueSchema(map.value()));
            }
            case ValueKind.ObjectKind object -> value.put("$ref", DEFINITION_REF + definition(object.type()));
            case ValueKind.HostKind host -> value.put("$ref", DEFINITION_REF + hostDefinitions.definition(host.type()));
            case ValueKind.AnyKind ignored -> {
            }
        }
        return value;
    }

    private static Optional<String> patternOf(TextFormat format) {
        return switch (format) {
            case PLAIN -> Optional.empty();
            case RESOURCE_ID -> Optional.of(RESOURCE_ID_PATTERN);
            case BLOCK_STATE -> Optional.of(BLOCK_STATE_PATTERN);
        };
    }

    /** The pattern a block state written as text matches, for completions that also accept properties. */
    static String blockStatePattern() {
        return BLOCK_STATE_PATTERN;
    }
}
