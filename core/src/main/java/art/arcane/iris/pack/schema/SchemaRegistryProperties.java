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
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.structure.StructureSchemaKeys;
import art.arcane.iris.generation.runtime.ListFunction;
import art.arcane.iris.pack.schema.annotation.RegistryListBiome;
import art.arcane.iris.pack.schema.annotation.RegistryListBlockType;
import art.arcane.iris.pack.schema.annotation.RegistryListEnchantment;
import art.arcane.iris.pack.schema.annotation.RegistryListEntityType;
import art.arcane.iris.pack.schema.annotation.RegistryListFont;
import art.arcane.iris.pack.schema.annotation.RegistryListFunction;
import art.arcane.iris.pack.schema.annotation.RegistryListItemType;
import art.arcane.iris.pack.schema.annotation.RegistryListNativeJigsawPool;
import art.arcane.iris.pack.schema.annotation.RegistryListPotionEffect;
import art.arcane.iris.pack.schema.annotation.RegistryListResource;
import art.arcane.iris.pack.schema.annotation.RegistryListSpecialEntity;
import art.arcane.iris.pack.schema.annotation.RegistryListStructure;
import art.arcane.iris.pack.schema.annotation.RegistryListVanillaStructure;
import art.arcane.iris.pack.schema.annotation.RegistryListVanillaStructureSet;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;

import java.lang.reflect.Field;
import java.util.Arrays;

final class SchemaRegistryProperties {
    private static final String IRIS_STRUCTURE_DEFINITION = "enum-iris-structure-placement";
    private final SchemaBuilder builder;
    private final SchemaRegistryCatalog catalog;

    SchemaRegistryProperties(SchemaBuilder builder, SchemaRegistryCatalog catalog) {
        this.builder = builder;
        this.catalog = catalog;
    }

    String stringRegistryType(Field k, JSONObject prop, KList<String> description) {
        if (k.isAnnotationPresent(RegistryListResource.class)) {
            return resourceReference(k, prop, description);
        }
        if (k.isAnnotationPresent(RegistryListStructure.class)) {
            putIrisStructureDefinition();
            prop.put("$ref", "#/definitions/" + IRIS_STRUCTURE_DEFINITION);
            description.add(SchemaBuilder.SYMBOL_TYPE__N + "  Must be a valid vanilla, datapack, or imported Iris structure (use ctrl+space for auto complete!)");
            return "Structure";
        }
        if (k.isAnnotationPresent(RegistryListBlockType.class)) {
            catalog.putRegistryEnumRef(prop, "enum-block-type", catalog::blockTypes);
            description.add(SchemaBuilder.SYMBOL_TYPE__N + "  Must be a valid Block Type (use ctrl+space for auto complete!)");
            return "Block Type";
        }
        if (k.isAnnotationPresent(RegistryListNativeJigsawPool.class)) {
            catalog.putRegistryEnumRef(prop, "enum-native-jigsaw-pool", catalog::nativeJigsawPools);
            description.add(SchemaBuilder.SYMBOL_TYPE__N + "  Must be a registered vanilla, datapack, or modded template pool key (use ctrl+space for auto complete!)");
            return "Native Jigsaw Pool";
        }
        if (k.isAnnotationPresent(RegistryListVanillaStructure.class)) {
            if (k.getAnnotation(RegistryListVanillaStructure.class).prefixes()) {
                catalog.putRegistryEnumOrPrefixRef(prop, "enum-vanilla-structure-or-prefix",
                        "enum-vanilla-structure", catalog::vanillaStructures, SchemaRegistryCatalog.VANILLA_STRUCTURE_PREFIX_PATTERN);
                description.add(SchemaBuilder.SYMBOL_TYPE__N + "  Must be a vanilla/datapack structure key or a family/namespace prefix like 'minecraft:village' or 'nova_structures:' (use ctrl+space for auto complete!)");
            } else {
                catalog.putRegistryEnumRef(prop, "enum-vanilla-structure", catalog::vanillaStructures);
                description.add(SchemaBuilder.SYMBOL_TYPE__N + "  Must be a valid vanilla/datapack structure key (use ctrl+space for auto complete!)");
            }
            return "Vanilla Structure";
        }
        if (k.isAnnotationPresent(RegistryListVanillaStructureSet.class)) {
            catalog.putRegistryEnumRef(prop, "enum-vanilla-structure-set", catalog::vanillaStructureSets);
            description.add(SchemaBuilder.SYMBOL_TYPE__N + "  Must be a valid vanilla/datapack structure SET key (use ctrl+space for auto complete!)");
            return "Vanilla Structure Set";
        }
        if (k.isAnnotationPresent(RegistryListItemType.class)) {
            catalog.putRegistryEnumRef(prop, "enum-item-type", catalog::itemTypes);
            description.add(SchemaBuilder.SYMBOL_TYPE__N + "  Must be a valid Item Type (use ctrl+space for auto complete!)");
            return "Item Type";
        }
        if (k.isAnnotationPresent(RegistryListEntityType.class)) {
            catalog.putRegistryEnumRef(prop, "enum-entity-type", catalog::entityTypes);
            description.add(SchemaBuilder.SYMBOL_TYPE__N + "  Must be a valid Entity Type (use ctrl+space for auto complete!)");
            return "Entity Type";
        }
        if (k.isAnnotationPresent(RegistryListBiome.class)) {
            catalog.putRegistryEnumRef(prop, "enum-biome-type", catalog::biomeTypes);
            description.add(SchemaBuilder.SYMBOL_TYPE__N + "  Must be a valid vanilla, datapack, or mod biome key (use ctrl+space for auto complete!)");
            return "Biome Type";
        }
        if (k.isAnnotationPresent(RegistryListSpecialEntity.class)) {
            catalog.putRegistryEnumRef(prop, "enum-reg-specialentity", catalog::specialEntityTypes);
            description.add(SchemaBuilder.SYMBOL_TYPE__N + "  Must be a valid Custom Mob Type (use ctrl+space for auto complete!)");
            return "Custom Mob Type";
        }
        if (k.isAnnotationPresent(RegistryListFont.class)) {
            catalog.putRegistryEnumRef(prop, "enum-font", SchemaRegistryCatalog::fontTypes);
            description.add(SchemaBuilder.SYMBOL_TYPE__N + "  Must be a valid Font Family (use ctrl+space for auto complete!)");
            return "Font Family";
        }
        if (k.isAnnotationPresent(RegistryListEnchantment.class)) {
            catalog.putRegistryEnumRef(prop, "enum-enchantment", catalog::enchantTypes);
            description.add(SchemaBuilder.SYMBOL_TYPE__N + "  Must be a valid Enchantment Type (use ctrl+space for auto complete!)");
            return "Enchantment Type";
        }
        if (k.isAnnotationPresent(RegistryListPotionEffect.class)) {
            catalog.putRegistryEnumRef(prop, "enum-potion-effect-type", catalog::potionTypes);
            description.add(SchemaBuilder.SYMBOL_TYPE__N + "  Must be a valid Potion Effect Type (use ctrl+space for auto complete!)");
            return "Potion Effect Type";
        }
        if (k.isAnnotationPresent(RegistryListFunction.class)) {
            return functionReference(k, prop, description, "Text", false);
        }
        return null;
    }

    String stringArrayRegistryType(Field k, JSONObject prop, KList<String> description) {
        if (k.isAnnotationPresent(RegistryListResource.class)) {
            return resourceItems(k, prop, description);
        }
        if (k.isAnnotationPresent(RegistryListStructure.class)) {
            putIrisStructureDefinition();
            putItemsReference(prop, IRIS_STRUCTURE_DEFINITION);
            description.add(SchemaBuilder.SYMBOL_TYPE__N + "  Must be a valid vanilla, datapack, or imported Iris structure (use ctrl+space for auto complete!)");
            return "List<Structure>";
        }
        if (k.isAnnotationPresent(RegistryListNativeJigsawPool.class)) {
            catalog.putRegistryEnumItems(prop, "enum-native-jigsaw-pool", catalog::nativeJigsawPools);
            description.add(SchemaBuilder.SYMBOL_TYPE__N + "  Must be a registered vanilla, datapack, or modded template pool key (use ctrl+space for auto complete!)");
            return "List<Native Jigsaw Pool>";
        }
        if (k.isAnnotationPresent(RegistryListVanillaStructure.class)) {
            if (k.getAnnotation(RegistryListVanillaStructure.class).prefixes()) {
                catalog.putRegistryEnumOrPrefixItems(prop, "enum-vanilla-structure-or-prefix",
                        "enum-vanilla-structure", catalog::vanillaStructures, SchemaRegistryCatalog.VANILLA_STRUCTURE_PREFIX_PATTERN);
                description.add(SchemaBuilder.SYMBOL_TYPE__N + "  Must be a vanilla/datapack structure key or a family/namespace prefix like 'minecraft:village' or 'nova_structures:' (use ctrl+space for auto complete!)");
            } else {
                catalog.putRegistryEnumItems(prop, "enum-vanilla-structure", catalog::vanillaStructures);
                description.add(SchemaBuilder.SYMBOL_TYPE__N + "  Must be a valid vanilla/datapack structure key (use ctrl+space for auto complete!)");
            }
            return "List<Vanilla Structure>";
        }
        if (k.isAnnotationPresent(RegistryListVanillaStructureSet.class)) {
            catalog.putRegistryEnumItems(prop, "enum-vanilla-structure-set", catalog::vanillaStructureSets);
            description.add(SchemaBuilder.SYMBOL_TYPE__N + "  Must be a valid vanilla/datapack structure set key (use ctrl+space for auto complete!)");
            return "List<Vanilla Structure Set>";
        }
        if (k.isAnnotationPresent(RegistryListBlockType.class)) {
            catalog.putRegistryEnumItems(prop, "enum-block-type", catalog::blockTypes);
            description.add(SchemaBuilder.SYMBOL_TYPE__N + "  Must be a valid Block Type (use ctrl+space for auto complete!)");
            return "List of Block Types";
        }
        if (k.isAnnotationPresent(RegistryListItemType.class)) {
            catalog.putRegistryEnumItems(prop, "enum-item-type", catalog::itemTypes);
            description.add(SchemaBuilder.SYMBOL_TYPE__N + "  Must be a valid Item Type (use ctrl+space for auto complete!)");
            return "List of Item Types";
        }
        if (k.isAnnotationPresent(RegistryListEntityType.class)) {
            catalog.putRegistryEnumItems(prop, "enum-entity-type", catalog::entityTypes);
            description.add(SchemaBuilder.SYMBOL_TYPE__N + "  Must be a valid Entity Type (use ctrl+space for auto complete!)");
            return "List of Entity Types";
        }
        if (k.isAnnotationPresent(RegistryListBiome.class)) {
            catalog.putRegistryEnumItems(prop, "enum-biome-type", catalog::biomeTypes);
            description.add(SchemaBuilder.SYMBOL_TYPE__N + "  Must be a valid vanilla, datapack, or mod biome key (use ctrl+space for auto complete!)");
            return "List of Biome Types";
        }
        if (k.isAnnotationPresent(RegistryListFont.class)) {
            catalog.putRegistryEnumItems(prop, "enum-font", SchemaRegistryCatalog::fontTypes);
            description.add(SchemaBuilder.SYMBOL_TYPE__N + "  Must be a valid Font Family (use ctrl+space for auto complete!)");
            return "List of Font Families";
        }
        if (k.isAnnotationPresent(RegistryListEnchantment.class)) {
            catalog.putRegistryEnumItems(prop, "enum-enchantment", catalog::enchantTypes);
            description.add(SchemaBuilder.SYMBOL_TYPE__N + "  Must be a valid Enchantment Type (use ctrl+space for auto complete!)");
            return "List of Enchantment Types";
        }
        if (k.isAnnotationPresent(RegistryListPotionEffect.class)) {
            catalog.putRegistryEnumItems(prop, "enum-potion-effect-type", catalog::potionTypes);
            description.add(SchemaBuilder.SYMBOL_TYPE__N + "  Must be a valid Potion Effect Type (use ctrl+space for auto complete!)");
            return "List of Potion Effect Types";
        }
        if (k.isAnnotationPresent(RegistryListFunction.class)) {
            return functionReference(k, prop, description, "List of Text", true);
        }
        return null;
    }

    private String resourceReference(Field k, JSONObject prop, KList<String> description) {
        RegistryListResource rr = k.getDeclaredAnnotation(RegistryListResource.class);
        ResourceLoader<?> loader = builder.data.getLoaders().get(rr.value());

        if (loader == null) {
            IrisLogging.error("Cannot find Registry Loader for type " + rr.value() + " used in " + k.getDeclaringClass().getCanonicalName() + " in field " + k.getName());
            return "Text";
        }

        String key = "erz" + loader.getFolderName();
        putResourceDefinition(key, loader);
        prop.put("$ref", "#/definitions/" + key);
        description.add(SchemaBuilder.SYMBOL_TYPE__N + "  Must be a valid " + loader.getFolderName() + " (use ctrl+space for auto complete!)");
        return "Iris " + loader.getResourceTypeName();
    }

    private String resourceItems(Field k, JSONObject prop, KList<String> description) {
        RegistryListResource rr = k.getDeclaredAnnotation(RegistryListResource.class);
        ResourceLoader<?> loader = builder.data.getLoaders().get(rr.value());

        if (loader == null) {
            IrisLogging.error("Cannot find Registry Loader for type (list schema) " + rr.value() + " used in " + k.getDeclaringClass().getCanonicalName() + " in field " + k.getName());
            return "List of Text";
        }

        String key = "erz" + loader.getFolderName();
        putResourceDefinition(key, loader);
        putItemsReference(prop, key);
        description.add(SchemaBuilder.SYMBOL_TYPE__N + "  Must be a valid " + loader.getResourceTypeName() + " (use ctrl+space for auto complete!)");
        return "List<" + loader.getResourceTypeName() + ">";
    }

    private String functionReference(Field k, JSONObject prop, KList<String> description, String fallbackType, boolean asItems) {
        Class<? extends ListFunction<KList<String>>> functionClass = k.getDeclaredAnnotation(RegistryListFunction.class).value();
        String fancyType = fallbackType;

        try {
            ListFunction<KList<String>> instance = functionClass.getDeclaredConstructor().newInstance();
            String key = instance.key();
            fancyType = instance.fancyName();

            if (!builder.definitions.containsKey(key)) {
                JSONObject j = new JSONObject();
                j.put("enum", instance.apply(builder.data));
                builder.definitions.put(key, j);
            }

            if (asItems) {
                putItemsReference(prop, key);
            } else {
                prop.put("$ref", "#/definitions/" + key);
            }
            description.add(SchemaBuilder.SYMBOL_TYPE__N + "  Must be a valid " + fancyType + " (use ctrl+space for auto complete!)");
        } catch (Throwable e) {
            IrisLogging.reportError("Could not execute apply method in " + functionClass.getName(), e);
        }

        return fancyType;
    }

    private void putResourceDefinition(String key, ResourceLoader<?> loader) {
        if (!builder.definitions.containsKey(key)) {
            JSONObject j = new JSONObject();
            j.put("enum", new JSONArray(loader.getPossibleKeys()));
            builder.definitions.put(key, j);
        }
    }

    private void putIrisStructureDefinition() {
        if (!builder.definitions.containsKey(IRIS_STRUCTURE_DEFINITION)) {
            JSONObject j = new JSONObject();
            j.put("enum", new JSONArray(StructureSchemaKeys.collect(
                    Arrays.asList(builder.data.getStructureLoader().getPossibleKeys()),
                    Arrays.asList(builder.data.getJigsawPieceLoader().getPossibleKeys())).toArray(new String[0])));
            builder.definitions.put(IRIS_STRUCTURE_DEFINITION, j);
        }
    }

    private void putItemsReference(JSONObject prop, String key) {
        JSONObject items = new JSONObject();
        items.put("$ref", "#/definitions/" + key);
        prop.put("items", items);
    }
}
