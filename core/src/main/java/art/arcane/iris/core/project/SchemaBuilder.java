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

import art.arcane.iris.spi.CapabilityProbe;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockProperty;
import art.arcane.iris.spi.PlatformNumericRange;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.core.structure.StructureSchemaKeys;
import art.arcane.iris.engine.framework.ListFunction;
import art.arcane.iris.engine.object.annotations.ArrayType;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.MaxNumber;
import art.arcane.iris.engine.object.annotations.MinNumber;
import art.arcane.iris.engine.object.annotations.RegistryListBiome;
import art.arcane.iris.engine.object.annotations.RegistryListBlockType;
import art.arcane.iris.engine.object.annotations.RegistryListEnchantment;
import art.arcane.iris.engine.object.annotations.RegistryListEntityType;
import art.arcane.iris.engine.object.annotations.RegistryListFont;
import art.arcane.iris.engine.object.annotations.RegistryListFunction;
import art.arcane.iris.engine.object.annotations.RegistryListItemType;
import art.arcane.iris.engine.object.annotations.RegistryListNativeJigsawPool;
import art.arcane.iris.engine.object.annotations.RegistryListPotionEffect;
import art.arcane.iris.engine.object.annotations.RegistryListResource;
import art.arcane.iris.engine.object.annotations.RegistryListSpecialEntity;
import art.arcane.iris.engine.object.annotations.RegistryListStructure;
import art.arcane.iris.engine.object.annotations.RegistryListVanillaStructure;
import art.arcane.iris.engine.object.annotations.RegistryListVanillaStructureSet;
import art.arcane.iris.engine.object.annotations.RegistryMapBlockState;
import art.arcane.iris.engine.object.annotations.Required;
import art.arcane.iris.engine.object.annotations.Snippet;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import org.jetbrains.annotations.NotNull;

import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public class SchemaBuilder {
    private static final String SYMBOL_LIMIT__N = "*";
    private static final String SYMBOL_TYPE__N = "";
    private static final String MINECRAFT_NAMESPACE = "minecraft:";
    /** Namespaced key or family/namespace prefix: "minecraft:village_plains", "minecraft:village", "nova_structures:". */
    private static final String VANILLA_STRUCTURE_PREFIX_PATTERN = "^[a-z0-9_.-]+:[a-z0-9_./-]*$";
    private static volatile JSONArray fontTypes;
    private final KMap<String, JSONObject> definitions;
    private final Class<?> root;
    private final KList<String> warnings;
    private final IrisData data;
    private JSONArray potionTypes;
    private JSONArray enchantTypes;

    public SchemaBuilder(Class<?> root, IrisData data) {
        this.data = data;
        warnings = new KList<>();
        this.definitions = new KMap<>();
        this.root = root;
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

    /**
     * Font families are only used for schema completion. Enumerating them touches AWT, which can fail outright on a
     * headless or mod-loader JVM - a failure degrades to no completion, never to a broken schema.
     */
    private static JSONArray fontTypes() {
        JSONArray cached = fontTypes;
        if (cached != null) {
            return cached;
        }
        JSONArray built = CapabilityProbe.attempt("java.awt font families",
                () -> new JSONArray(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()),
                new JSONArray());
        fontTypes = built;
        return built;
    }

    private JSONArray potionTypes() {
        if (potionTypes == null) {
            potionTypes = registryKeyForms(IrisPlatforms.get().registries().potionEffectKeys(), true);
        }
        return potionTypes;
    }

    private JSONArray enchantTypes() {
        if (enchantTypes == null) {
            enchantTypes = registryKeyForms(IrisPlatforms.get().registries().enchantmentKeys(), false);
        }
        return enchantTypes;
    }

    /**
     * Emits the full namespaced key for every registry entry, so mod and datapack content is addressable
     * unambiguously, plus the legacy short form for the vanilla namespace so existing packs stay valid.
     */
    private static JSONArray registryKeyForms(List<String> keys, boolean upperCaseLegacy) {
        JSONArray a = new JSONArray();
        Set<String> seen = new LinkedHashSet<>();
        if (keys != null) {
            for (String key : keys) {
                if (key == null || key.isBlank()) {
                    continue;
                }
                seen.add(key);
                if (key.startsWith(MINECRAFT_NAMESPACE) || key.indexOf(':') < 0) {
                    String path = stripNamespace(key);
                    seen.add(upperCaseLegacy ? path.toUpperCase(Locale.ROOT).replace(' ', '_') : path);
                }
            }
        }
        for (String key : seen) {
            a.put(key);
        }
        return a;
    }

    /**
     * Biome derivatives resolve through NamespacedKey.fromString, which accepts a full key or a bare vanilla path,
     * so both forms are offered.
     */
    private JSONArray biomeTypes() {
        return registryKeyForms(IrisPlatforms.get().registries().biomeKeys(), false);
    }

    private JSONArray entityTypes() {
        return keysAsArray(IrisPlatforms.get().registries().entityKeys());
    }

    private JSONArray specialEntityTypes() {
        return keysAsArray(IrisPlatforms.get().registries().specialEntityKeys());
    }

    private JSONArray vanillaStructures() {
        return keysAsArray(IrisPlatforms.get().registries().structureKeys());
    }

    private JSONArray vanillaStructureSets() {
        if (IrisPlatforms.get().structureHooks() == null) {
            return new JSONArray();
        }
        List<String> keys = IrisPlatforms.get().structureHooks().structureSetKeys();
        return keysAsArray(keys == null ? List.of() : keys);
    }

    private JSONArray nativeJigsawPools() {
        return keysAsArray(templatePoolKeys());
    }

    private static JSONArray keysAsArray(List<String> keys) {
        JSONArray a = new JSONArray();
        if (keys != null) {
            for (String key : keys) {
                if (key != null && !key.isBlank()) {
                    a.put(key);
                }
            }
        }
        return a;
    }

    /**
     * Registers a registry-backed enum definition under {@code definitionKey} and points {@code target} at it.
     * <p>
     * An empty key list means the registry has nothing to offer yet - the server is still booting, a modded registry
     * has not been frozen, or the host simply does not expose that catalog. Emitting {@code "enum": []} in that case
     * writes a schema that rejects every value the author could possibly type, turning a missing autocomplete list
     * into a pack that reads as broken in the editor. The reference is omitted instead, leaving the field
     * unconstrained, and the values are only computed when the definition does not exist yet.
     */
    private void putRegistryEnumRef(JSONObject target, String definitionKey, Supplier<JSONArray> values) {
        if (!definitions.containsKey(definitionKey)) {
            JSONArray built = values.get();
            if (built == null || built.length() == 0) {
                IrisLogging.debug("Schema enum '" + definitionKey + "' omitted: the registry returned no keys");
                return;
            }
            JSONObject definition = new JSONObject();
            definition.put("enum", built);
            definitions.put(definitionKey, definition);
        }
        target.put("$ref", "#/definitions/" + definitionKey);
    }

    /**
     * {@link #putRegistryEnumRef(JSONObject, String, Supplier)} for a list-typed property. When the enum is omitted no
     * {@code items} schema is written at all, which is valid and simply means "any element".
     */
    private void putRegistryEnumItems(JSONObject prop, String definitionKey, Supplier<JSONArray> values) {
        JSONObject items = new JSONObject();
        putRegistryEnumRef(items, definitionKey, values);
        if (items.has("$ref")) {
            prop.put("items", items);
        }
    }

    /**
     * A registry enum that ALSO accepts family/namespace prefixes ("minecraft:village",
     * "nova_structures:") — the runtime prefix-matching contract of importedStructures.disabled and
     * adjustments[].match. Emitted as anyOf(enum, pattern) so autocomplete still offers registered
     * keys while prefix entries validate instead of being rejected.
     */
    private void putRegistryEnumOrPrefixRef(JSONObject target, String definitionKey,
                                            String enumDefinitionKey, Supplier<JSONArray> values,
                                            String pattern) {
        if (!definitions.containsKey(definitionKey)) {
            JSONArray anyOf = new JSONArray();
            JSONObject enumRef = new JSONObject();
            try {
                putRegistryEnumRef(enumRef, enumDefinitionKey, values);
            } catch (RuntimeException e) {
                IrisLogging.debug("Schema enum '" + enumDefinitionKey + "' unavailable ("
                        + e.getMessage() + "); emitting prefix pattern only");
            }
            if (enumRef.has("$ref")) {
                anyOf.put(enumRef);
            }
            JSONObject prefix = new JSONObject();
            prefix.put("type", "string");
            prefix.put("pattern", pattern);
            anyOf.put(prefix);
            JSONObject definition = new JSONObject();
            definition.put("anyOf", anyOf);
            definitions.put(definitionKey, definition);
        }
        target.put("$ref", "#/definitions/" + definitionKey);
    }

    private void putRegistryEnumOrPrefixItems(JSONObject prop, String definitionKey,
                                              String enumDefinitionKey, Supplier<JSONArray> values,
                                              String pattern) {
        JSONObject items = new JSONObject();
        putRegistryEnumOrPrefixRef(items, definitionKey, enumDefinitionKey, values, pattern);
        prop.put("items", items);
    }

    private JSONArray itemTypes() {
        JSONArray a = new JSONArray();
        for (String key : IrisPlatforms.get().registries().itemKeys()) {
            a.put(key.startsWith(MINECRAFT_NAMESPACE) ? key.substring(MINECRAFT_NAMESPACE.length()) : key);
        }
        return a;
    }

    private JSONArray blockTypes() {
        JSONArray a = new JSONArray();
        for (String i : data.getBlockLoader().getPossibleKeys()) {
            a.put(i);
        }
        for (String i : IrisPlatforms.get().registries().blockTypeKeys()) {
            a.put(i);
        }
        return a;
    }

    private static String definitionReference(String key) {
        return "#/definitions/" + key.replace("~", "~0").replace("/", "~1");
    }

    private static String stripNamespace(String key) {
        int idx = key.indexOf(':');
        return idx < 0 ? key : key.substring(idx + 1);
    }

    private JSONObject buildProperties(Class<?> c) {
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

            JSONObject property = buildProperty(k, c);

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

    private JSONObject buildProperty(Field k, Class<?> cl) {
        JSONObject prop = new JSONObject();
        String type = getType(k.getType());
        KList<String> description = new KList<>();
        prop.put("!required", k.isAnnotationPresent(Required.class));
        prop.put("type", type);
        String fancyType = "Unknown Type";

        switch (type) {
            case "boolean" -> fancyType = "Boolean";
            case "integer" -> {
                fancyType = "Integer";
                if (k.isAnnotationPresent(MinNumber.class)) {
                    int min = (int) k.getDeclaredAnnotation(MinNumber.class).value();
                    prop.put("minimum", min);
                    description.add(SYMBOL_LIMIT__N + " Minimum allowed is " + min);
                }
                if (k.isAnnotationPresent(MaxNumber.class)) {
                    int max = (int) k.getDeclaredAnnotation(MaxNumber.class).value();
                    prop.put("maximum", max);
                    description.add(SYMBOL_LIMIT__N + " Maximum allowed is " + max);
                }
            }
            case "number" -> {
                fancyType = "Number";
                if (k.isAnnotationPresent(MinNumber.class)) {
                    double min = k.getDeclaredAnnotation(MinNumber.class).value();
                    prop.put("minimum", min);
                    description.add(SYMBOL_LIMIT__N + " Minimum allowed is " + min);
                }
                if (k.isAnnotationPresent(MaxNumber.class)) {
                    double max = k.getDeclaredAnnotation(MaxNumber.class).value();
                    prop.put("maximum", max);
                    description.add(SYMBOL_LIMIT__N + " Maximum allowed is " + max);
                }
            }
            case "string" -> {
                fancyType = "Text";
                if (k.isAnnotationPresent(MinNumber.class)) {
                    int min = (int) k.getDeclaredAnnotation(MinNumber.class).value();
                    prop.put("minLength", min);
                    description.add(SYMBOL_LIMIT__N + " Minimum Length allowed is " + min);
                }
                if (k.isAnnotationPresent(MaxNumber.class)) {
                    int max = (int) k.getDeclaredAnnotation(MaxNumber.class).value();
                    prop.put("maxLength", max);
                    description.add(SYMBOL_LIMIT__N + " Maximum Length allowed is " + max);
                }


                if (k.isAnnotationPresent(RegistryListResource.class)) {
                    RegistryListResource rr = k.getDeclaredAnnotation(RegistryListResource.class);
                    ResourceLoader<?> loader = data.getLoaders().get(rr.value());

                    if (loader != null) {
                        String key = "erz" + loader.getFolderName();

                        if (!definitions.containsKey(key)) {
                            JSONObject j = new JSONObject();
                            j.put("enum", new JSONArray(loader.getPossibleKeys()));
                            definitions.put(key, j);
                        }

                        fancyType = "Iris " + loader.getResourceTypeName();
                        prop.put("$ref", "#/definitions/" + key);
                        description.add(SYMBOL_TYPE__N + "  Must be a valid " + loader.getFolderName() + " (use ctrl+space for auto complete!)");
                    } else {
                        IrisLogging.error("Cannot find Registry Loader for type " + rr.value() + " used in " + k.getDeclaringClass().getCanonicalName() + " in field " + k.getName());
                    }
                } else if (k.isAnnotationPresent(RegistryListStructure.class)) {
                    String key = "enum-iris-structure-placement";

                    if (!definitions.containsKey(key)) {
                        JSONObject j = new JSONObject();
                        j.put("enum", new JSONArray(StructureSchemaKeys.collect(
                                Arrays.asList(data.getStructureLoader().getPossibleKeys()),
                                Arrays.asList(data.getJigsawPieceLoader().getPossibleKeys())).toArray(new String[0])));
                        definitions.put(key, j);
                    }

                    fancyType = "Structure";
                    prop.put("$ref", "#/definitions/" + key);
                    description.add(SYMBOL_TYPE__N + "  Must be a valid vanilla, datapack, or imported Iris structure (use ctrl+space for auto complete!)");

                } else if (k.isAnnotationPresent(RegistryListBlockType.class)) {
                    fancyType = "Block Type";
                    putRegistryEnumRef(prop, "enum-block-type", this::blockTypes);
                    description.add(SYMBOL_TYPE__N + "  Must be a valid Block Type (use ctrl+space for auto complete!)");

                } else if (k.isAnnotationPresent(RegistryListNativeJigsawPool.class)) {
                    fancyType = "Native Jigsaw Pool";
                    putRegistryEnumRef(prop, "enum-native-jigsaw-pool", this::nativeJigsawPools);
                    description.add(SYMBOL_TYPE__N + "  Must be a registered vanilla, datapack, or modded template pool key (use ctrl+space for auto complete!)");

                } else if (k.isAnnotationPresent(RegistryListVanillaStructure.class)) {
                    fancyType = "Vanilla Structure";
                    if (k.getAnnotation(RegistryListVanillaStructure.class).prefixes()) {
                        putRegistryEnumOrPrefixRef(prop, "enum-vanilla-structure-or-prefix",
                                "enum-vanilla-structure", this::vanillaStructures, VANILLA_STRUCTURE_PREFIX_PATTERN);
                        description.add(SYMBOL_TYPE__N + "  Must be a vanilla/datapack structure key or a family/namespace prefix like 'minecraft:village' or 'nova_structures:' (use ctrl+space for auto complete!)");
                    } else {
                        putRegistryEnumRef(prop, "enum-vanilla-structure", this::vanillaStructures);
                        description.add(SYMBOL_TYPE__N + "  Must be a valid vanilla/datapack structure key (use ctrl+space for auto complete!)");
                    }

                } else if (k.isAnnotationPresent(RegistryListVanillaStructureSet.class)) {
                    fancyType = "Vanilla Structure Set";
                    putRegistryEnumRef(prop, "enum-vanilla-structure-set", this::vanillaStructureSets);
                    description.add(SYMBOL_TYPE__N + "  Must be a valid vanilla/datapack structure SET key (use ctrl+space for auto complete!)");

                } else if (k.isAnnotationPresent(RegistryListItemType.class)) {
                    fancyType = "Item Type";
                    putRegistryEnumRef(prop, "enum-item-type", this::itemTypes);
                    description.add(SYMBOL_TYPE__N + "  Must be a valid Item Type (use ctrl+space for auto complete!)");

                } else if (k.isAnnotationPresent(RegistryListEntityType.class)) {
                    fancyType = "Entity Type";
                    putRegistryEnumRef(prop, "enum-entity-type", this::entityTypes);
                    description.add(SYMBOL_TYPE__N + "  Must be a valid Entity Type (use ctrl+space for auto complete!)");

                } else if (k.isAnnotationPresent(RegistryListBiome.class)) {
                    fancyType = "Biome Type";
                    putRegistryEnumRef(prop, "enum-biome-type", this::biomeTypes);
                    description.add(SYMBOL_TYPE__N + "  Must be a valid vanilla, datapack, or mod biome key (use ctrl+space for auto complete!)");

                } else if (k.isAnnotationPresent(RegistryListSpecialEntity.class)) {
                    fancyType = "Custom Mob Type";
                    putRegistryEnumRef(prop, "enum-reg-specialentity", this::specialEntityTypes);
                    description.add(SYMBOL_TYPE__N + "  Must be a valid Custom Mob Type (use ctrl+space for auto complete!)");
                } else if (k.isAnnotationPresent(RegistryListFont.class)) {
                    fancyType = "Font Family";
                    putRegistryEnumRef(prop, "enum-font", SchemaBuilder::fontTypes);
                    description.add(SYMBOL_TYPE__N + "  Must be a valid Font Family (use ctrl+space for auto complete!)");

                } else if (k.isAnnotationPresent(RegistryListEnchantment.class)) {
                    fancyType = "Enchantment Type";
                    putRegistryEnumRef(prop, "enum-enchantment", this::enchantTypes);
                    description.add(SYMBOL_TYPE__N + "  Must be a valid Enchantment Type (use ctrl+space for auto complete!)");
                } else if (k.isAnnotationPresent(RegistryListPotionEffect.class)) {
                    fancyType = "Potion Effect Type";
                    putRegistryEnumRef(prop, "enum-potion-effect-type", this::potionTypes);
                    description.add(SYMBOL_TYPE__N + "  Must be a valid Potion Effect Type (use ctrl+space for auto complete!)");
                } else if (k.isAnnotationPresent(RegistryListFunction.class)) {
                    Class<? extends ListFunction<KList<String>>> functionClass = k.getDeclaredAnnotation(RegistryListFunction.class).value();
                    try {
                        ListFunction<KList<String>> instance = functionClass.getDeclaredConstructor().newInstance();
                        String key = instance.key();
                        fancyType = instance.fancyName();

                        if (!definitions.containsKey(key)) {
                            JSONObject j = new JSONObject();
                            j.put("enum", instance.apply(data));
                            definitions.put(key, j);
                        }

                        prop.put("$ref", "#/definitions/" + key);
                        description.add(SYMBOL_TYPE__N + "  Must be a valid " + fancyType + " (use ctrl+space for auto complete!)");
                    } catch (Throwable e) {
                        IrisLogging.error("Could not execute apply method in " + functionClass.getName());
                    }
                } else if (SchemaKeyedTypes.isKeyed(k.getType())) {
                    fancyType = addEnum(k.getType(), prop, description, SchemaKeyedTypes.values(k.getType()), Function.identity());
                } else if (k.getType().isEnum()) {
                    fancyType = addEnum(k.getType(), prop, description, enumNames(k.getType()), Function.identity());
                }
            }
            case "object" -> {
                if (k.isAnnotationPresent(RegistryMapBlockState.class)) {
                    String blockType = k.getDeclaredAnnotation(RegistryMapBlockState.class).value();
                    fancyType = "Block State";
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

                        if (!definitions.containsKey(enumKey)) {
                            JSONArray filters = new JSONArray();
                            blocks.forEach(filters::put);

                            definitions.put(enumKey, new JSONObject()
                                    .put("type", "string")
                                    .put("enum", filters));
                        }

                        if (!definitions.containsKey(propertiesKey)) {
                            JSONObject props = new JSONObject();
                            for (PlatformBlockProperty property : group.properties()) {
                                props.put(property.name(), buildBlockPropertyJson(property));
                            }

                            definitions.put(propertiesKey, new JSONObject()
                                    .put("type", "object")
                                    .put("properties", props));
                        }
                    }
                } else {
                    fancyType = k.getType().getSimpleName().replaceAll("\\QIris\\E", "") + " (Object)";
                    String key = "obj-" + k.getType().getCanonicalName().replaceAll("\\Q.\\E", "-").toLowerCase();
                    if (!definitions.containsKey(key)) {
                        definitions.put(key, new JSONObject());
                        definitions.put(key, buildProperties(k.getType()));
                    }
                    prop.put("$ref", "#/definitions/" + key);
                }
            }
            case "array" -> {
                fancyType = "List of Something...?";
                ArrayType t = k.getDeclaredAnnotation(ArrayType.class);
                if (t != null) {
                    if (t.min() > 0) {
                        prop.put("minItems", t.min());
                        if (t.min() == 1) {
                            description.add(SYMBOL_LIMIT__N + " At least one entry must be defined, or just remove this list.");
                        } else {
                            description.add(SYMBOL_LIMIT__N + " Requires at least " + t.min() + " entries.");
                        }
                    }

                    String arrayType = getType(t.type());

                    switch (arrayType) {
                        case "integer" -> fancyType = "List of Integers";
                        case "number" -> fancyType = "List of Numbers";
                        case "object" -> {
                            fancyType = "List of " + t.type().getSimpleName().replaceAll("\\QIris\\E", "") + "s (Objects)";
                            String key = "obj-" + t.type().getCanonicalName().replaceAll("\\Q.\\E", "-").toLowerCase();
                            if (!definitions.containsKey(key)) {
                                definitions.put(key, new JSONObject());
                                definitions.put(key, buildProperties(t.type()));
                            }
                            JSONObject items = new JSONObject();
                            items.put("$ref", "#/definitions/" + key);
                            prop.put("items", items);
                        }
                        case "string" -> {
                            fancyType = "List of Text";

                            if (k.isAnnotationPresent(RegistryListResource.class)) {
                                RegistryListResource rr = k.getDeclaredAnnotation(RegistryListResource.class);
                                ResourceLoader<?> loader = data.getLoaders().get(rr.value());

                                if (loader != null) {
                                    fancyType = "List<" + loader.getResourceTypeName() + ">";
                                    String key = "erz" + loader.getFolderName();

                                    if (!definitions.containsKey(key)) {
                                        JSONObject j = new JSONObject();
                                        j.put("enum", new JSONArray(loader.getPossibleKeys()));
                                        definitions.put(key, j);
                                    }

                                    JSONObject items = new JSONObject();
                                    items.put("$ref", "#/definitions/" + key);
                                    prop.put("items", items);
                                    description.add(SYMBOL_TYPE__N + "  Must be a valid " + loader.getResourceTypeName() + " (use ctrl+space for auto complete!)");
                                } else {
                                    IrisLogging.error("Cannot find Registry Loader for type (list schema) " + rr.value() + " used in " + k.getDeclaringClass().getCanonicalName() + " in field " + k.getName());
                                }
                            } else if (k.isAnnotationPresent(RegistryListStructure.class)) {
                                fancyType = "List<Structure>";
                                String key = "enum-iris-structure-placement";

                                if (!definitions.containsKey(key)) {
                                    JSONObject j = new JSONObject();
                                    j.put("enum", new JSONArray(StructureSchemaKeys.collect(
                                            Arrays.asList(data.getStructureLoader().getPossibleKeys()),
                                            Arrays.asList(data.getJigsawPieceLoader().getPossibleKeys())).toArray(new String[0])));
                                    definitions.put(key, j);
                                }

                                JSONObject items = new JSONObject();
                                items.put("$ref", "#/definitions/" + key);
                                prop.put("items", items);
                                description.add(SYMBOL_TYPE__N + "  Must be a valid vanilla, datapack, or imported Iris structure (use ctrl+space for auto complete!)");
                            } else if (k.isAnnotationPresent(RegistryListNativeJigsawPool.class)) {
                                fancyType = "List<Native Jigsaw Pool>";
                                putRegistryEnumItems(prop, "enum-native-jigsaw-pool", this::nativeJigsawPools);
                                description.add(SYMBOL_TYPE__N + "  Must be a registered vanilla, datapack, or modded template pool key (use ctrl+space for auto complete!)");
                            } else if (k.isAnnotationPresent(RegistryListVanillaStructure.class)) {
                                fancyType = "List<Vanilla Structure>";
                                if (k.getAnnotation(RegistryListVanillaStructure.class).prefixes()) {
                                    putRegistryEnumOrPrefixItems(prop, "enum-vanilla-structure-or-prefix",
                                            "enum-vanilla-structure", this::vanillaStructures, VANILLA_STRUCTURE_PREFIX_PATTERN);
                                    description.add(SYMBOL_TYPE__N + "  Must be a vanilla/datapack structure key or a family/namespace prefix like 'minecraft:village' or 'nova_structures:' (use ctrl+space for auto complete!)");
                                } else {
                                    putRegistryEnumItems(prop, "enum-vanilla-structure", this::vanillaStructures);
                                    description.add(SYMBOL_TYPE__N + "  Must be a valid vanilla/datapack structure key (use ctrl+space for auto complete!)");
                                }
                            } else if (k.isAnnotationPresent(RegistryListVanillaStructureSet.class)) {
                                fancyType = "List<Vanilla Structure Set>";
                                putRegistryEnumItems(prop, "enum-vanilla-structure-set", this::vanillaStructureSets);
                                description.add(SYMBOL_TYPE__N + "  Must be a valid vanilla/datapack structure set key (use ctrl+space for auto complete!)");
                            } else if (k.isAnnotationPresent(RegistryListBlockType.class)) {
                                fancyType = "List of Block Types";
                                putRegistryEnumItems(prop, "enum-block-type", this::blockTypes);
                                description.add(SYMBOL_TYPE__N + "  Must be a valid Block Type (use ctrl+space for auto complete!)");
                            } else if (k.isAnnotationPresent(RegistryListItemType.class)) {
                                fancyType = "List of Item Types";
                                putRegistryEnumItems(prop, "enum-item-type", this::itemTypes);
                                description.add(SYMBOL_TYPE__N + "  Must be a valid Item Type (use ctrl+space for auto complete!)");
                            } else if (k.isAnnotationPresent(RegistryListEntityType.class)) {
                                fancyType = "List of Entity Types";
                                putRegistryEnumItems(prop, "enum-entity-type", this::entityTypes);
                                description.add(SYMBOL_TYPE__N + "  Must be a valid Entity Type (use ctrl+space for auto complete!)");
                            } else if (k.isAnnotationPresent(RegistryListBiome.class)) {
                                fancyType = "List of Biome Types";
                                putRegistryEnumItems(prop, "enum-biome-type", this::biomeTypes);
                                description.add(SYMBOL_TYPE__N + "  Must be a valid vanilla, datapack, or mod biome key (use ctrl+space for auto complete!)");
                            } else if (k.isAnnotationPresent(RegistryListFont.class)) {
                                fancyType = "List of Font Families";
                                putRegistryEnumItems(prop, "enum-font", SchemaBuilder::fontTypes);
                                description.add(SYMBOL_TYPE__N + "  Must be a valid Font Family (use ctrl+space for auto complete!)");
                            } else if (k.isAnnotationPresent(RegistryListEnchantment.class)) {
                                fancyType = "List of Enchantment Types";
                                putRegistryEnumItems(prop, "enum-enchantment", this::enchantTypes);
                                description.add(SYMBOL_TYPE__N + "  Must be a valid Enchantment Type (use ctrl+space for auto complete!)");
                            } else if (k.isAnnotationPresent(RegistryListPotionEffect.class)) {
                                fancyType = "List of Potion Effect Types";
                                putRegistryEnumItems(prop, "enum-potion-effect-type", this::potionTypes);
                                description.add(SYMBOL_TYPE__N + "  Must be a valid Potion Effect Type (use ctrl+space for auto complete!)");
                            } else if (k.isAnnotationPresent(RegistryListFunction.class)) {
                                Class<? extends ListFunction<KList<String>>> functionClass = k.getDeclaredAnnotation(RegistryListFunction.class).value();
                                try {
                                    ListFunction<KList<String>> instance = functionClass.getDeclaredConstructor().newInstance();
                                    String key = instance.key();
                                    fancyType = instance.fancyName();

                                    if (!definitions.containsKey(key)) {
                                        JSONObject j = new JSONObject();
                                        j.put("enum", instance.apply(data));
                                        definitions.put(key, j);
                                    }

                                    JSONObject items = new JSONObject();
                                    items.put("$ref", "#/definitions/" + key);
                                    prop.put("items", items);
                                    description.add(SYMBOL_TYPE__N + "  Must be a valid " + fancyType + " (use ctrl+space for auto complete!)");
                                } catch (Throwable e) {
                                    IrisLogging.error("Could not execute apply method in " + functionClass.getName());
                                }
                            } else if (SchemaKeyedTypes.isKeyed(t.type())) {
                                fancyType = addEnumList(prop, description, t, SchemaKeyedTypes.values(t.type()), Function.identity());
                            } else if (t.type().isEnum()) {
                                fancyType = addEnumList(prop, description, t, enumNames(t.type()), Function.identity());
                            }
                        }
                    }
                } else {
                    warnings.add("Undefined array type for field " + k.getName() + " (" + k.getType().getSimpleName() + ") in class " + cl.getSimpleName());
                }
            }
            default ->
                    warnings.add("Unexpected Schema Type: " + type + " for field " + k.getName() + " (" + k.getType().getSimpleName() + ") in class " + cl.getSimpleName());
        }

        KList<String> d = new KList<>();
        d.add("<h>" + k.getName() + "</h>");
        d.add(getFieldDescription(k) + "<hr></hr>");
        d.add("<h>" + fancyType + "</h>");
        String typeDesc = getDescription(k.getType());
        boolean present = !typeDesc.isBlank();
        if (present) d.add(typeDesc);

        Snippet snippet = k.getType().getDeclaredAnnotation(Snippet.class);
        if (snippet == null) {
            ArrayType array = k.getType().getDeclaredAnnotation(ArrayType.class);
            if (array != null) {
                snippet = array.type().getDeclaredAnnotation(Snippet.class);
            }
        }

        if (snippet != null) {
            String sm = snippet.value();
            if (present) d.add("    ");
            d.add("You can instead specify \"snippet/" + sm + "/some-name.json\" to use a snippet file instead of specifying it here.");
            present = false;
        }

        try {
            k.setAccessible(true);
            Object value = k.get(cl.getDeclaredConstructor().newInstance());

            if (value != null) {
                if (present) d.add("    ");
                if (value instanceof List) {
                    d.add(SYMBOL_LIMIT__N + " Default Value is an empty list");
                } else if (!k.getType().isPrimitive() && !(value instanceof Number) && !(value instanceof String) && !(value instanceof Enum<?>) && !SchemaKeyedTypes.isKeyed(k.getType())) {
                    d.add(SYMBOL_LIMIT__N + " Default Value is a default object (create this object to see default properties)");
                } else {
                    d.add(SYMBOL_LIMIT__N + " Default Value is " + value);
                }
            }
        } catch (Throwable ignored) {

        }

        description.forEach((g) -> d.add(g.trim()));
        String desc = d.toString("\n")
                .replace("<hr></hr>", "\n")
                .replace("<h>", "")
                .replace("</h>", "");
        String hDesc = d.toString("<br>");
        prop.put("type", type);
        prop.put("description", desc);
        prop.put("x-intellij-html-description", hDesc);
        return buildSnippet(prop, k.getType());
    }

    private JSONObject buildSnippet(JSONObject prop, Class<?> type) {
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

    @NotNull
    private <T> String addEnumList(JSONObject prop, KList<String> description, ArrayType t, T[] values, Function<T, String> function) {
        JSONObject items = new JSONObject();
        String s = addEnum(t.type(), items, description, values, function);
        prop.put("items", items);

        return "List of " + s + "s";
    }

    private static String[] enumNames(Class<?> enumType) {
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
    private <T> String addEnum(Class<?> type, JSONObject prop, KList<String> description, T[] values, Function<T, String> function) {
        JSONArray a = new JSONArray();
        boolean advanced = type.isAnnotationPresent(Desc.class);
        for (T gg : values) {
            if (advanced) {
                try {
                    JSONObject j = new JSONObject();
                    String name = function.apply(gg);
                    j.put("const", name);
                    Desc dd = type.getField(name).getAnnotation(Desc.class);
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

        if (!definitions.containsKey(key)) {
            JSONObject j = new JSONObject();
            j.put(advanced ? "oneOf" : "enum", a);
            definitions.put(key, j);
        }

        prop.put("$ref", "#/definitions/" + key);
        description.add(SYMBOL_TYPE__N + "  Must be a valid " + type.getSimpleName().replaceAll("\\QIris\\E", "") + " (use ctrl+space for auto complete!)");
        return type.getSimpleName().replaceAll("\\QIris\\E", "");
    }

    private JSONObject buildBlockPropertyJson(PlatformBlockProperty property) {
        JSONObject json = new JSONObject();
        json.put("type", property.jsonType());
        json.put("default", property.defaultValue());
        List<Object> allowed = property.allowedValues();
        if (!allowed.isEmpty()) {
            json.put("enum", new JSONArray(allowed));
        }
        if (property.hasNumericRange()) {
            PlatformNumericRange range = property.numericRange();
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

    private List<BlockStateGroup> reconstructBlockStateGroups() {
        List<BlockStateGroup> groups = new ArrayList<>();
        List<PlatformBlockProperty> currentProperties = null;
        List<String> currentBlocks = null;
        for (Map.Entry<String, List<PlatformBlockProperty>> entry : IrisPlatforms.get().registries().blockStateProperties().entrySet()) {
            List<PlatformBlockProperty> value = entry.getValue();
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

    private List<String> templatePoolKeys() {
        if (IrisPlatforms.get().structureHooks() == null) {
            return List.of();
        }
        List<String> keys = IrisPlatforms.get().structureHooks().templatePoolKeys();
        return keys == null ? List.of() : keys;
    }

    private String getType(Class<?> c) {
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

    private String getFieldDescription(Field r) {

        if (r.isAnnotationPresent(Desc.class)) {
            return r.getDeclaredAnnotation(Desc.class).value();
        }

        warnings.addIfMissing("Missing @Desc on field " + r.getName() + " (" + r.getType() + ") in " + r.getDeclaringClass().getCanonicalName());
        return "No Field Description";
    }

    private String getDescription(Class<?> r) {
        if (r.isAnnotationPresent(Desc.class)) {
            return r.getDeclaredAnnotation(Desc.class).value();
        }

        if (!r.isPrimitive() && !r.equals(KList.class) && !r.equals(KMap.class) && r.getCanonicalName().startsWith("art.arcane.")) {
            warnings.addIfMissing("Missing @Desc on " + r.getSimpleName() + " in " + (r.getDeclaringClass() != null ? r.getDeclaringClass().getCanonicalName() : " NOSRC"));
        }
        return "";
    }

    private record BlockStateGroup(List<String> blocks, List<PlatformBlockProperty> properties) {
    }
}
