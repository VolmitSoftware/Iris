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

import art.arcane.iris.builtin.structure.IrisStructureRef;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockProperty;
import art.arcane.iris.spi.PlatformNumericRange;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.core.modules.IrisValues;
import art.arcane.iris.core.modules.ModuleResourceFiles;
import art.arcane.iris.core.modules.ModuleTargets;
import art.arcane.iris.engine.object.image.IrisImageMap;
import art.arcane.iris.engine.object.noise.NoiseStyle;
import art.arcane.iris.engine.object.noise.expression.IrisExpression;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.structure.IrisStructure;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.region.IrisRegion;
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
import art.arcane.iris.engine.object.annotations.RegistryListPotionEffect;
import art.arcane.iris.engine.object.annotations.RegistryListResource;
import art.arcane.iris.engine.object.annotations.RegistryListSpecialEntity;
import art.arcane.iris.engine.object.annotations.RegistryListVanillaStructure;
import art.arcane.iris.engine.object.annotations.RegistryMapBlockState;
import art.arcane.iris.engine.object.annotations.Required;
import art.arcane.iris.engine.object.annotations.Snippet;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import com.volmit.config.Reference;
import com.volmit.host.ResourceKindRegistration;
import com.volmit.host.schema.ObjectType;
import com.volmit.host.schema.ResourceKindSchema;
import com.volmit.host.schema.TextFormat;
import com.volmit.host.schema.ValueKind;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.resource.ResourceKind;
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
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class SchemaBuilder {
    private static final String SYMBOL_LIMIT__N = "*";
    private static final String SYMBOL_TYPE__N = "";
    private static final String MINECRAFT_NAMESPACE = "minecraft:";
    /** Namespaced key or family/namespace prefix: "minecraft:village_plains", "minecraft:village", "nova_structures:". */
    private static final String VANILLA_STRUCTURE_PREFIX_PATTERN = "^[a-z0-9_.-]+:[a-z0-9_./-]*$";
    private static final String LOOT_TABLE_DEFINITION = "loot-table-key";
    private static final Map<ResourceId, Class<?>> IRIS_TYPES_OF_HOST_KINDS = Map.of(
            PackResources.BIOMES.id(), IrisBiome.class,
            PackResources.REGIONS.id(), IrisRegion.class,
            PackResources.OBJECTS.id(), IrisObject.class,
            PackResources.EXPRESSIONS.id(), IrisExpression.class,
            PackResources.IMAGE_MAPS.id(), IrisImageMap.class);
    private static volatile JSONArray fontTypes;
    private final KMap<String, JSONObject> definitions;
    private final Class<?> root;
    private final KList<String> warnings;
    private final IrisData data;
    private JSONArray potionTypes;
    private JSONArray enchantTypes;
    private ModuleSchemaRenderer moduleSchemas;

    public SchemaBuilder(Class<?> root, IrisData data) {
        this.data = data;
        warnings = new KList<>();
        this.definitions = new KMap<>();
        this.root = root;
    }

    /** The schema of a module snippet file, which holds one object of {@code type}. */
    public static JSONObject moduleSnippet(ObjectType type, IrisData data) {
        return new SchemaBuilder(type.getClass(), data).moduleSchemas().fileSchema(type);
    }

    /**
     * The schema of a file of a module resource kind: Iris's own description of the class a built-in kind reads into,
     * as its loader described it, or the module's description of its class.
     */
    public static JSONObject moduleResource(ResourceKindSchema kind, IrisData data) {
        if (kind.type() instanceof ValueKind.HostKind host) {
            return new SchemaBuilder(hostType(host.type()), data).construct();
        }
        ObjectType type = data.getModules().pack().schema().type(((ValueKind.ObjectKind) kind.type()).type());
        return new SchemaBuilder(kind.getClass(), data).moduleSchemas().fileSchema(type);
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
        JSONArray built;
        try {
            built = new JSONArray(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        } catch (Throwable e) {
            IrisLogging.debug("Schema font family enumeration unavailable: " + e.getMessage());
            built = new JSONArray();
        }
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

    private JSONArray lootTableKeys() {
        return keysAsArray(IrisPlatforms.get().registries().lootTableKeys());
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
     * adjustments[].match ({@code Reference.STRUCTURE_PREFIX}). Emitted as anyOf(enum, pattern) so autocomplete still offers registered
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
        addModuleFeatures(c, properties);

        if (required.length() > 0) {
            o.put("required", required);
        }
        if (extended.length() > 0) {
            o.put("allOf", extended);
        }

        o.put("properties", properties);


        return buildSnippet(o, c);
    }

    /** The pack modules' settings at the target whose JSON {@code c} is, such as a biome's. */
    private void addModuleFeatures(Class<?> c, JSONObject properties) {
        ModuleTargets.ofResource(c).ifPresent(target -> moduleSchemas().addFeatures(target, properties));
    }

    private ModuleSchemaRenderer moduleSchemas() {
        if (moduleSchemas == null) {
            moduleSchemas = new ModuleSchemaRenderer(data.getModules().pack().schema(), definitions,
                    this::completeReference, this::completeResourceReference, this::completeIrisExtension, this::hostDefinition,
                    typeName -> IrisValues.irisClassOf(typeName).map(Class::getName), data::getPossibleSnippets);
        }
        return moduleSchemas;
    }

    /** Iris's own definition of one of its classes that a built-in module's settings hold, as Iris's fields describe it. */
    private String hostDefinition(String typeName) {
        return objectDefinition(hostType(typeName));
    }

    private static Class<?> hostType(String typeName) {
        try {
            return Class.forName(typeName, false, SchemaBuilder.class.getClassLoader());
        } catch (ClassNotFoundException missing) {
            throw new IllegalStateException("Built-in settings name Iris class " + typeName + ", which Iris does not have", missing);
        }
    }

    /** The definition key of a class an Iris field holds: the module's own description of a held type, else Iris's. */
    private String objectDefinition(Class<?> type) {
        if (isHeldType(type)) {
            return moduleSchemas().definition(type.getName());
        }
        String key = objectDefinitionKey(type);
        if (!definitions.containsKey(key)) {
            definitions.put(key, new JSONObject());
            definitions.put(key, buildProperties(type));
        }
        return key;
    }

    /** Whether a built-in module registered {@code type} because Iris's classes hold it, so its fields are the module's. */
    private boolean isHeldType(Class<?> type) {
        return type.isAnnotationPresent(com.volmit.config.Desc.class) && moduleSchemas().describes(type.getName());
    }

    private static String objectDefinitionKey(Class<?> type) {
        return "obj-" + type.getCanonicalName().replaceAll("\\Q.\\E", "-").toLowerCase();
    }

    /** Completion for a module option that names something, from the same lists Iris's own fields use. */
    private void completeReference(JSONObject property, Reference reference, TextFormat format) {
        switch (reference) {
            case BLOCK -> {
                if (format == TextFormat.BLOCK_STATE) {
                    putRegistryEnumOrPrefixRef(property, "enum-block-state", "enum-block-type", this::blockTypes,
                            ModuleSchemaRenderer.blockStatePattern());
                } else {
                    putRegistryEnumRef(property, "enum-block-type", this::blockTypes);
                }
            }
            case ITEM -> putRegistryEnumRef(property, "enum-item-type", this::itemTypes);
            case ENTITY -> putRegistryEnumRef(property, "enum-entity-type", this::entityTypes);
            case BIOME -> putRegistryEnumRef(property, "enum-biome-type", this::biomeTypes);
            case STRUCTURE -> putRegistryEnumRef(property, "enum-vanilla-structure", this::vanillaStructures);
            case STRUCTURE_PREFIX -> putRegistryEnumOrPrefixRef(property, "enum-vanilla-structure-or-prefix",
                    "enum-vanilla-structure", this::vanillaStructures, VANILLA_STRUCTURE_PREFIX_PATTERN);
            case STRUCTURE_SET -> putRegistryEnumRef(property, "enum-vanilla-structure-set", this::vanillaStructureSets);
            case TEMPLATE_POOL -> putRegistryEnumRef(property, "enum-native-jigsaw-pool", this::nativeJigsawPools);
            case POTION_EFFECT -> putRegistryEnumRef(property, "enum-potion-effect-type", this::potionTypes);
            case ENCHANTMENT -> putRegistryEnumRef(property, "enum-enchantment", this::enchantTypes);
            case LOOT_TABLE -> putRegistryEnumRef(property, LOOT_TABLE_DEFINITION, this::lootTableKeys);
            case NOISE_STYLE -> addEnum(NoiseStyle.class, property, new KList<>(), enumNames(NoiseStyle.class), Function.identity());
        }
    }

    /** Completion for an option whose field carries an Iris-extension annotation, such as {@link IrisStructureRef}. */
    private void completeIrisExtension(JSONObject property, String typeName, String optionName) {
        Optional<Field> field = declaredField(typeName, optionName);
        if (field.isEmpty() || !field.get().isAnnotationPresent(IrisStructureRef.class)) {
            return;
        }
        putPackResourceRef(property, IrisStructure.class);
    }

    private static Optional<Field> declaredField(String typeName, String fieldName) {
        try {
            return Optional.of(Class.forName(typeName, false, SchemaBuilder.class.getClassLoader()).getDeclaredField(fieldName));
        } catch (ClassNotFoundException | NoSuchFieldException notIris) {
            return Optional.empty();
        }
    }

    /** A list of pack resource keys studio completes: the folder the resources live in and what they are called. */
    private record PackResourceList(String folder, String typeName) {
    }

    /**
     * Points {@code property} at the keys of the pack's resources of {@code type}, read by Iris's loader for it or by
     * the module resource kind read into it; null when the pack has neither.
     */
    private PackResourceList putPackResourceRef(JSONObject property, Class<?> type) {
        ResourceLoader<?> loader = data.getLoaders().get(irisTypeOf(type));
        if (loader != null) {
            return putResourceKeys(property, new PackResourceList(loader.getFolderName(), loader.getResourceTypeName()),
                    Arrays.asList(loader.getPossibleKeys()));
        }
        return moduleKinds().stream()
                .filter(kind -> kind.type() == type)
                .findFirst()
                .map(kind -> putModuleResourceRef(property, kind))
                .orElse(null);
    }

    /** The Iris class a host kind reads into, for the API type the kind serves (IrisObject for GeneratedObject); else {@code type}. */
    private static Class<?> irisTypeOf(Class<?> type) {
        return Stream.of(PackResources.BIOMES, PackResources.REGIONS, PackResources.OBJECTS, PackResources.EXPRESSIONS,
                        PackResources.IMAGE_MAPS)
                .filter(kind -> kind.type() == type)
                .findFirst()
                .<Class<?>>map(kind -> IRIS_TYPES_OF_HOST_KINDS.get(kind.id()))
                .orElse(type);
    }

    /** Completion for a module option naming a resource of the host or module resource kind {@code kindId}. */
    private void completeResourceReference(JSONObject property, ResourceId kindId) {
        Class<?> irisType = IRIS_TYPES_OF_HOST_KINDS.get(kindId);
        if (irisType != null) {
            putPackResourceRef(property, irisType);
            return;
        }
        moduleKinds().stream()
                .filter(kind -> kind.id().equals(kindId))
                .findFirst()
                .ifPresent(kind -> putModuleResourceRef(property, kind));
    }

    private PackResourceList putModuleResourceRef(JSONObject property, ResourceKind<?> kind) {
        List<String> keys = data.getPackResources().ids(kind).stream().map(ResourceId::path).toList();
        return putResourceKeys(property, new PackResourceList(kind.folder(), ModuleResourceFiles.typeName(kind)), keys);
    }

    private PackResourceList putResourceKeys(JSONObject property, PackResourceList list, List<String> keys) {
        String key = "erz" + list.folder();
        if (!definitions.containsKey(key)) {
            JSONObject enumeration = new JSONObject();
            enumeration.put("enum", new JSONArray(keys.toArray(new String[0])));
            definitions.put(key, enumeration);
        }
        property.put("$ref", "#/definitions/" + key);
        return list;
    }

    private List<ResourceKind<?>> moduleKinds() {
        return data.getModules().pack().catalog().resourceKinds().stream()
                .<ResourceKind<?>>map(ResourceKindRegistration::kind)
                .toList();
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
                    PackResourceList resources = putPackResourceRef(prop, rr.value());

                    if (resources != null) {
                        fancyType = "Iris " + resources.typeName();
                        description.add(SYMBOL_TYPE__N + "  Must be a valid " + resources.folder() + " (use ctrl+space for auto complete!)");
                    } else {
                        IrisLogging.error("Cannot find Registry Loader for type " + rr.value() + " used in " + k.getDeclaringClass().getCanonicalName() + " in field " + k.getName());
                    }
                } else if (k.isAnnotationPresent(RegistryListBlockType.class)) {
                    fancyType = "Block Type";
                    putRegistryEnumRef(prop, "enum-block-type", this::blockTypes);
                    description.add(SYMBOL_TYPE__N + "  Must be a valid Block Type (use ctrl+space for auto complete!)");

                } else if (k.isAnnotationPresent(RegistryListVanillaStructure.class)) {
                    fancyType = "Vanilla Structure";
                    putRegistryEnumRef(prop, "enum-vanilla-structure", this::vanillaStructures);
                    description.add(SYMBOL_TYPE__N + "  Must be a valid vanilla/datapack structure key (use ctrl+space for auto complete!)");

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
                                                        .put("$ref", "#/definitions/" + enumKey))))
                                .put("then", new JSONObject()
                                        .put("properties", new JSONObject()
                                                .put(k.getName(), new JSONObject()
                                                        .put("type", "object")
                                                        .put("$ref", "#/definitions/" + propertiesKey))))
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
                    prop.put("$ref", "#/definitions/" + objectDefinition(k.getType()));
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
                            JSONObject items = new JSONObject();
                            items.put("$ref", "#/definitions/" + objectDefinition(t.type()));
                            prop.put("items", items);
                        }
                        case "string" -> {
                            fancyType = "List of Text";

                            if (k.isAnnotationPresent(RegistryListResource.class)) {
                                RegistryListResource rr = k.getDeclaredAnnotation(RegistryListResource.class);
                                JSONObject items = new JSONObject();
                                PackResourceList resources = putPackResourceRef(items, rr.value());

                                if (resources != null) {
                                    fancyType = "List<" + resources.typeName() + ">";
                                    prop.put("items", items);
                                    description.add(SYMBOL_TYPE__N + "  Must be a valid " + resources.typeName() + " (use ctrl+space for auto complete!)");
                                } else {
                                    IrisLogging.error("Cannot find Registry Loader for type (list schema) " + rr.value() + " used in " + k.getDeclaringClass().getCanonicalName() + " in field " + k.getName());
                                }
                            } else if (k.isAnnotationPresent(RegistryListVanillaStructure.class)) {
                                fancyType = "List<Vanilla Structure>";
                                putRegistryEnumItems(prop, "enum-vanilla-structure", this::vanillaStructures);
                                description.add(SYMBOL_TYPE__N + "  Must be a valid vanilla/datapack structure key (use ctrl+space for auto complete!)");
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

        Optional<String> snippetFolder = snippetFolder(k.getType());
        if (snippetFolder.isEmpty()) {
            ArrayType array = k.getType().getDeclaredAnnotation(ArrayType.class);
            if (array != null) {
                snippetFolder = snippetFolder(array.type());
            }
        }

        if (snippetFolder.isPresent()) {
            String sm = snippetFolder.get();
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

    /** The snippet folder of a class, as Iris's or the module's {@code @Snippet} names it. */
    private static Optional<String> snippetFolder(Class<?> type) {
        Snippet snippet = type.getDeclaredAnnotation(Snippet.class);
        if (snippet != null) {
            return Optional.of(snippet.value());
        }
        com.volmit.config.Snippet moduleSnippet = type.getDeclaredAnnotation(com.volmit.config.Snippet.class);
        return moduleSnippet == null ? Optional.empty() : Optional.of(moduleSnippet.value());
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
        anyOf.put("!required", type.isAnnotationPresent(Required.class));

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
        boolean advanced = type.isAnnotationPresent(Desc.class) || type.isAnnotationPresent(com.volmit.config.Desc.class);
        for (T gg : values) {
            if (advanced) {
                try {
                    JSONObject j = new JSONObject();
                    String name = function.apply(gg);
                    j.put("const", name);
                    String desc = constantDescription(type, name);
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

    private static String constantDescription(Class<?> enumType, String name) throws NoSuchFieldException {
        Field constant = enumType.getField(name);
        Desc described = constant.getAnnotation(Desc.class);
        if (described != null) {
            return described.value();
        }
        com.volmit.config.Desc moduleDescribed = constant.getAnnotation(com.volmit.config.Desc.class);
        return moduleDescribed == null ? "No Description for " + name : moduleDescribed.value();
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

        if (!c.isAnnotationPresent(Desc.class) && !c.isAnnotationPresent(com.volmit.config.Desc.class)
                && c.getCanonicalName().startsWith("art.arcane.iris.")) {
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

        if (r.isAnnotationPresent(com.volmit.config.Desc.class)) {
            return r.getDeclaredAnnotation(com.volmit.config.Desc.class).value();
        }

        if (!r.isPrimitive() && !r.equals(KList.class) && !r.equals(KMap.class) && r.getCanonicalName().startsWith("art.arcane.")) {
            warnings.addIfMissing("Missing @Desc on " + r.getSimpleName() + " in " + (r.getDeclaringClass() != null ? r.getDeclaringClass().getCanonicalName() : " NOSRC"));
        }
        return "";
    }

    private record BlockStateGroup(List<String> blocks, List<PlatformBlockProperty> properties) {
    }
}
