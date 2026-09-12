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

import art.arcane.iris.spi.CapabilityProbe;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;

import java.awt.GraphicsEnvironment;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

final class SchemaRegistryCatalog {
    /** Namespaced key or family/namespace prefix: "minecraft:village_plains", "minecraft:village", "nova_structures:". */
    static final String VANILLA_STRUCTURE_PREFIX_PATTERN = "^[a-z0-9_.-]+:[a-z0-9_./-]*$";
    private static final String MINECRAFT_NAMESPACE = "minecraft:";
    private static volatile JSONArray fontTypes;
    private final SchemaBuilder builder;
    private JSONArray potionTypes;
    private JSONArray enchantTypes;

    SchemaRegistryCatalog(SchemaBuilder builder) {
        this.builder = builder;
    }

    /**
     * Font families are only used for schema completion. Enumerating them touches AWT, which can fail outright on a
     * headless or mod-loader JVM - a failure degrades to no completion, never to a broken schema.
     */
    static JSONArray fontTypes() {
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

    JSONArray potionTypes() {
        if (potionTypes == null) {
            potionTypes = registryKeyForms(IrisPlatforms.get().registries().potionEffectKeys(), true);
        }
        return potionTypes;
    }

    JSONArray enchantTypes() {
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
    JSONArray biomeTypes() {
        return registryKeyForms(IrisPlatforms.get().registries().biomeKeys(), false);
    }

    JSONArray entityTypes() {
        return keysAsArray(IrisPlatforms.get().registries().entityKeys());
    }

    JSONArray specialEntityTypes() {
        return keysAsArray(IrisPlatforms.get().registries().specialEntityKeys());
    }

    JSONArray vanillaStructures() {
        return keysAsArray(IrisPlatforms.get().registries().structureKeys());
    }

    JSONArray vanillaStructureSets() {
        if (IrisPlatforms.get().structureHooks() == null) {
            return new JSONArray();
        }
        List<String> keys = IrisPlatforms.get().structureHooks().structureSetKeys();
        return keysAsArray(keys == null ? List.of() : keys);
    }

    JSONArray nativeJigsawPools() {
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
    void putRegistryEnumRef(JSONObject target, String definitionKey, Supplier<JSONArray> values) {
        if (!builder.definitions.containsKey(definitionKey)) {
            JSONArray built = values.get();
            if (built == null || built.length() == 0) {
                IrisLogging.debug("Schema enum '" + definitionKey + "' omitted: the registry returned no keys");
                return;
            }
            JSONObject definition = new JSONObject();
            definition.put("enum", built);
            builder.definitions.put(definitionKey, definition);
        }
        target.put("$ref", "#/definitions/" + definitionKey);
    }

    /**
     * {@link #putRegistryEnumRef(JSONObject, String, Supplier)} for a list-typed property. When the enum is omitted no
     * {@code items} schema is written at all, which is valid and simply means "any element".
     */
    void putRegistryEnumItems(JSONObject prop, String definitionKey, Supplier<JSONArray> values) {
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
    void putRegistryEnumOrPrefixRef(JSONObject target, String definitionKey,
                                    String enumDefinitionKey, Supplier<JSONArray> values,
                                    String pattern) {
        if (!builder.definitions.containsKey(definitionKey)) {
            JSONArray anyOf = new JSONArray();
            JSONObject enumRef = new JSONObject();
            try {
                putRegistryEnumRef(enumRef, enumDefinitionKey, values);
            } catch (RuntimeException e) {
                IrisLogging.reportError("Schema enum '" + enumDefinitionKey
                        + "' unavailable; emitting prefix pattern only", e);
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
            builder.definitions.put(definitionKey, definition);
        }
        target.put("$ref", "#/definitions/" + definitionKey);
    }

    void putRegistryEnumOrPrefixItems(JSONObject prop, String definitionKey,
                                      String enumDefinitionKey, Supplier<JSONArray> values,
                                      String pattern) {
        JSONObject items = new JSONObject();
        putRegistryEnumOrPrefixRef(items, definitionKey, enumDefinitionKey, values, pattern);
        prop.put("items", items);
    }

    JSONArray itemTypes() {
        JSONArray a = new JSONArray();
        for (String key : IrisPlatforms.get().registries().itemKeys()) {
            a.put(key.startsWith(MINECRAFT_NAMESPACE) ? key.substring(MINECRAFT_NAMESPACE.length()) : key);
        }
        return a;
    }

    JSONArray blockTypes() {
        JSONArray a = new JSONArray();
        for (String i : builder.data.getBlockLoader().getPossibleKeys()) {
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

    private List<String> templatePoolKeys() {
        if (IrisPlatforms.get().structureHooks() == null) {
            return List.of();
        }
        List<String> keys = IrisPlatforms.get().structureHooks().templatePoolKeys();
        return keys == null ? List.of() : keys;
    }
}
