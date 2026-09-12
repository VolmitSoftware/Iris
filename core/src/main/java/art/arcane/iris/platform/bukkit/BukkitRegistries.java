/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.iris.platform.bukkit;

import art.arcane.iris.integration.Identifier;
import art.arcane.iris.integration.data.DataType;
import art.arcane.iris.platform.bukkit.nms.INMS;
import art.arcane.iris.platform.bukkit.nms.container.BlockProperty;
import art.arcane.iris.integration.ExternalDataSVC;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBlockProperty;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformEntityType;
import art.arcane.iris.spi.PlatformGenerationRegistry;
import art.arcane.iris.spi.PlatformItem;
import art.arcane.iris.spi.PlatformNumericRange;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.platform.reflect.KeyedType;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.loot.LootTables;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bukkit adapter resolving namespaced keys against the live Bukkit registries.
 */
public final class BukkitRegistries implements PlatformRegistries {
    @Override
    public PlatformGenerationRegistry generationRegistry() {
        return INMS.get().generationRegistry();
    }

    @Override
    public PlatformBlockState block(String key) {
        BlockData data = BukkitBlockResolution.get(key);
        return data == null ? null : BukkitBlockState.of(data);
    }

    // blockOrNull must stay null-honest to match the modded adapters, so it uses the strict lookup rather than
    // BukkitBlockResolution.getOrNull, which substitutes air for an unregistered key and feeds the Bukkit-only
    // IrisCompat rewrite table used by block().
    @Override
    public PlatformBlockState blockOrNull(String key) {
        BlockData data = BukkitBlockResolution.resolveOrNull(key);
        return data == null ? null : BukkitBlockState.of(data);
    }

    @Override
    public PlatformBlockState blockOrNull(String key, boolean warn) {
        BlockData data = BukkitBlockResolution.resolveOrNull(key, warn);
        return data == null ? null : BukkitBlockState.of(data);
    }

    @Override
    public PlatformBlockState air() {
        return BukkitBlockState.of(BukkitBlockResolution.getAir());
    }

    @Override
    public PlatformBlockState deepSlateOre(PlatformBlockState block, PlatformBlockState ore) {
        return BukkitBlockState.of(BukkitBlockResolution.toDeepSlateOre((BlockData) block.nativeHandle(), (BlockData) ore.nativeHandle()));
    }

    @Override
    public PlatformBiome biome(String key) {
        NamespacedKey namespacedKey = NamespacedKey.fromString(key);
        if (namespacedKey == null) {
            return null;
        }
        Biome biome = Registry.BIOME.get(namespacedKey);
        return biome == null ? null : BukkitBiome.of(biome);
    }

    @Override
    public PlatformItem item(String key) {
        Material material = Material.matchMaterial(key);
        return material == null ? null : BukkitItem.of(material);
    }

    @Override
    public PlatformEntityType entity(String key) {
        NamespacedKey namespacedKey = NamespacedKey.fromString(key);
        if (namespacedKey == null) {
            return null;
        }
        EntityType type = Registry.ENTITY_TYPE.get(namespacedKey);
        return type == null ? null : BukkitEntityType.of(type);
    }

    @Override
    public List<String> blockKeys() {
        List<String> keys = new ArrayList<>();
        for (Material material : Registry.MATERIAL) {
            if (material.isBlock()) {
                keys.add(material.getKey().toString());
            }
        }
        return keys;
    }

    @Override
    public List<String> biomeKeys() {
        List<String> keys = new ArrayList<>();
        for (Biome biome : Registry.BIOME) {
            keys.add(BukkitBiome.of(biome).key());
        }
        return keys;
    }

    @Override
    public List<String> structureKeys() {
        return new ArrayList<>(INMS.get().getStructureKeys());
    }

    @Override
    public List<String> itemKeys() {
        List<String> keys = new ArrayList<>();
        for (Material material : Registry.MATERIAL) {
            if (material.isItem()) {
                keys.add(material.getKey().toString());
            }
        }
        ExternalDataSVC external = IrisServices.getOrNull(ExternalDataSVC.class);
        if (external != null) {
            for (Identifier identifier : external.getAllIdentifiers(DataType.ITEM)) {
                keys.add(identifier.toString());
            }
        }
        return keys;
    }

    @Override
    public List<String> entityKeys() {
        List<String> keys = new ArrayList<>();
        for (EntityType type : Registry.ENTITY_TYPE) {
            NamespacedKey key = KeyedType.getKey(type);
            if (key != null) {
                keys.add(key.toString());
            }
        }
        return keys;
    }

    @Override
    public List<String> blockTypeKeys() {
        return new ArrayList<>(Arrays.asList(BukkitBlockResolution.getBlockTypes()));
    }

    @Override
    public List<String> specialEntityKeys() {
        ExternalDataSVC external = IrisServices.getOrNull(ExternalDataSVC.class);
        if (external == null) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        for (Identifier identifier : external.getAllIdentifiers(DataType.ENTITY)) {
            keys.add(identifier.toString());
        }
        return keys;
    }

    @Override
    public List<String> enchantmentKeys() {
        List<String> keys = new ArrayList<>();
        for (Enchantment enchantment : Registry.ENCHANTMENT) {
            NamespacedKey key = KeyedType.getKey(enchantment);
            if (key != null) {
                keys.add(key.toString());
            }
        }
        return keys;
    }

    @Override
    public List<String> potionEffectKeys() {
        List<String> keys = new ArrayList<>();
        for (PotionEffectType effect : Registry.EFFECT) {
            NamespacedKey key = KeyedType.getKey(effect);
            if (key != null) {
                keys.add(key.toString());
            }
        }
        return keys;
    }

    @Override
    public List<String> lootTableKeys() {
        List<String> keys = new ArrayList<>();
        for (LootTables table : LootTables.values()) {
            keys.add(table.getKey().toString());
        }
        return keys;
    }

    @Override
    public Map<String, List<PlatformBlockProperty>> blockStateProperties() {
        Map<String, List<PlatformBlockProperty>> properties = new LinkedHashMap<>();
        BukkitBlockResolution.getBlockStates().forEach((blocks, blockProperties) -> {
            if (blocks.isEmpty()) {
                return;
            }
            List<PlatformBlockProperty> converted = new ArrayList<>(blockProperties.size());
            for (BlockProperty blockProperty : blockProperties) {
                converted.add(toPlatformProperty(blockProperty));
            }
            List<PlatformBlockProperty> shared = List.copyOf(converted);
            for (String block : blocks) {
                properties.put(block, shared);
            }
        });
        return properties;
    }

    private static PlatformBlockProperty toPlatformProperty(BlockProperty property) {
        JSONObject json = property.buildJson();
        Object defaultValue = json.has("default") ? json.get("default") : null;
        List<Object> allowedValues = new ArrayList<>();
        if (json.has("enum")) {
            JSONArray values = json.getJSONArray("enum");
            for (int index = 0; index < values.length(); index++) {
                allowedValues.add(values.get(index));
            }
        }
        PlatformNumericRange range = null;
        if (json.has("minimum")) {
            range = new PlatformNumericRange(json.getDouble("minimum"), json.getDouble("maximum"), json.getBoolean("exclusiveMinimum"), json.getBoolean("exclusiveMaximum"));
        }
        return new PlatformBlockProperty(property.name(), json.getString("type"), defaultValue, List.copyOf(allowedValues), range);
    }
}
