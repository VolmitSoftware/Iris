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

package art.arcane.iris.modded;

import art.arcane.iris.modded.api.ModdedCustomContentRegistry;
import art.arcane.iris.modded.api.ModdedDataType;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBlockProperty;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformEntityType;
import art.arcane.iris.spi.PlatformItem;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.volmlib.util.data.UnresolvedKeyLog;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

public final class ModdedRegistries implements PlatformRegistries {
    private static final UnresolvedKeyLog NOT_READY = new UnresolvedKeyLog("Iris modded registry reads before server ready", 60_000L);

    private final Supplier<MinecraftServer> server;

    public ModdedRegistries(Supplier<MinecraftServer> server) {
        this.server = server;
    }

    @Override
    public PlatformBlockState block(String key) {
        return ModdedBlockResolution.get(key);
    }

    @Override
    public PlatformBlockState blockOrNull(String key) {
        return ModdedBlockResolution.getOrNull(key);
    }

    @Override
    public PlatformBlockState blockOrNull(String key, boolean warn) {
        return ModdedBlockResolution.getOrNull(key, warn);
    }

    @Override
    public boolean isInTag(PlatformBlockState block, String tag) {
        Identifier identifier = Identifier.tryParse(tag);
        if (identifier == null) {
            return false;
        }
        return ((BlockState) block.nativeHandle()).is(TagKey.create(Registries.BLOCK, identifier));
    }

    @Override
    public PlatformBlockState air() {
        return ModdedBlockResolution.getAir();
    }

    @Override
    public PlatformBlockState deepSlateOre(PlatformBlockState block, PlatformBlockState ore) {
        BlockState result = ModdedBlockResolution.toDeepSlateOre((BlockState) block.nativeHandle(), (BlockState) ore.nativeHandle());
        return ModdedBlockState.of(result, null);
    }

    @Override
    public PlatformBiome biome(String key) {
        Identifier identifier = Identifier.tryParse(key);
        if (identifier == null) {
            return null;
        }
        Registry<Biome> registry = biomeRegistry();
        if (registry == null) {
            return null;
        }
        Biome biome = registry.getValue(identifier);
        return biome == null ? null : ModdedBiome.of(biome, identifier.toString());
    }

    @Override
    public PlatformItem item(String key) {
        String normalized = key.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        Identifier identifier = Identifier.tryParse(normalized.indexOf(':') >= 0 ? normalized : "minecraft:" + normalized);
        if (identifier == null || !BuiltInRegistries.ITEM.containsKey(identifier)) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.getValue(identifier);
        return ModdedItem.of(item, identifier.toString());
    }

    @Override
    public PlatformEntityType entity(String key) {
        Identifier identifier = Identifier.tryParse(key);
        if (identifier == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(identifier)) {
            return null;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(identifier);
        return ModdedEntityType.of(type, identifier.toString());
    }

    @Override
    public List<String> blockKeys() {
        List<String> keys = new ArrayList<>();
        for (Identifier identifier : BuiltInRegistries.BLOCK.keySet()) {
            keys.add(identifier.toString());
        }
        return keys;
    }

    @Override
    public List<String> biomeKeys() {
        List<String> keys = new ArrayList<>();
        Registry<Biome> registry = biomeRegistry();
        if (registry == null) {
            warnNotReady("biome");
            return keys;
        }
        for (Identifier identifier : registry.keySet()) {
            keys.add(identifier.toString());
        }
        return keys;
    }

    @Override
    public List<String> structureKeys() {
        List<String> keys = new ArrayList<>();
        MinecraftServer instance = server.get();
        if (instance == null) {
            warnNotReady("structure");
            return keys;
        }
        for (Identifier identifier : instance.registryAccess().lookupOrThrow(Registries.STRUCTURE).keySet()) {
            keys.add(identifier.toString());
        }
        return keys;
    }

    @Override
    public List<String> itemKeys() {
        List<String> keys = new ArrayList<>();
        for (Identifier identifier : BuiltInRegistries.ITEM.keySet()) {
            keys.add(identifier.toString());
        }
        keys.addAll(ModdedCustomContentRegistry.providerKeys(ModdedDataType.ITEM));
        return keys;
    }

    @Override
    public List<String> specialEntityKeys() {
        return ModdedCustomContentRegistry.providerKeys(ModdedDataType.ENTITY);
    }

    @Override
    public List<String> entityKeys() {
        List<String> keys = new ArrayList<>();
        for (Identifier identifier : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            keys.add(identifier.toString());
        }
        return keys;
    }

    @Override
    public List<String> blockTypeKeys() {
        List<String> keys = new ArrayList<>();
        for (Identifier identifier : BuiltInRegistries.BLOCK.keySet()) {
            keys.add(identifier.toString());
        }
        keys.addAll(customBlockKeys());
        return keys;
    }

    @Override
    public List<String> enchantmentKeys() {
        List<String> keys = new ArrayList<>();
        Registry<Enchantment> registry = enchantmentRegistry();
        if (registry == null) {
            warnNotReady("enchantment");
            return keys;
        }
        for (Identifier identifier : registry.keySet()) {
            keys.add(identifier.toString());
        }
        return keys;
    }

    @Override
    public List<String> potionEffectKeys() {
        List<String> keys = new ArrayList<>();
        for (Identifier identifier : BuiltInRegistries.MOB_EFFECT.keySet()) {
            keys.add(identifier.toString());
        }
        return keys;
    }

    @Override
    public List<String> lootTableKeys() {
        List<String> keys = new ArrayList<>();
        MinecraftServer instance = server.get();
        if (instance == null) {
            warnNotReady("loot table");
            return keys;
        }
        HolderLookup.RegistryLookup<LootTable> registry = instance.reloadableRegistries().lookup()
                .lookupOrThrow(Registries.LOOT_TABLE);
        registry.listElementIds().forEach(key -> keys.add(key.identifier().toString()));
        return keys;
    }

    @Override
    public Map<String, List<PlatformBlockProperty>> blockStateProperties() {
        Map<String, List<PlatformBlockProperty>> properties = new LinkedHashMap<>();
        // One List instance per identical property group. SchemaBuilder groups consecutive entries by list
        // identity, so sharing collapses the emitted schema instead of writing a block-state object per block.
        Map<String, List<PlatformBlockProperty>> shared = new LinkedHashMap<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            BlockState defaultState = block.defaultBlockState();
            List<PlatformBlockProperty> converted = new ArrayList<>();
            for (Property<?> property : block.getStateDefinition().getProperties()) {
                converted.add(convertProperty(property, defaultState));
            }
            List<PlatformBlockProperty> group = shared.computeIfAbsent(groupSignature(converted), key -> List.copyOf(converted));
            properties.put(BuiltInRegistries.BLOCK.getKey(block).toString(), group);
        }
        List<PlatformBlockProperty> none = shared.computeIfAbsent(groupSignature(List.of()), key -> List.of());
        for (String key : customBlockKeys()) {
            properties.putIfAbsent(key, none);
        }
        return properties;
    }

    private static String groupSignature(List<PlatformBlockProperty> group) {
        StringBuilder signature = new StringBuilder(group.size() * 24);
        for (PlatformBlockProperty property : group) {
            signature.append(property.name()).append(':').append(property.jsonType()).append('=')
                    .append(property.defaultValue()).append(property.allowedValues()).append(';');
        }
        return signature.toString();
    }

    private static List<String> customBlockKeys() {
        List<String> keys = new ArrayList<>(ModdedCustomContentRegistry.aliasBlockKeys());
        keys.addAll(ModdedCustomContentRegistry.providerKeys(ModdedDataType.BLOCK));
        return keys;
    }

    private static void warnNotReady(String registryName) {
        if (NOT_READY.firstOccurrence(registryName)) {
            IrisLogging.warn("Iris registry read for '" + registryName + "' before the server is ready; returning empty");
        }
        String summary = NOT_READY.pollSummary();
        if (summary != null) {
            IrisLogging.warn(summary);
        }
    }

    private Registry<Biome> biomeRegistry() {
        MinecraftServer instance = server.get();
        if (instance == null) {
            return null;
        }
        return instance.registryAccess().lookupOrThrow(Registries.BIOME);
    }

    private Registry<Enchantment> enchantmentRegistry() {
        MinecraftServer instance = server.get();
        if (instance == null) {
            return null;
        }
        return instance.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
    }

    private static <T extends Comparable<T>> PlatformBlockProperty convertProperty(Property<T> property, BlockState defaultState) {
        T defaultValue = defaultState.getValue(property);
        Class<T> valueClass = property.getValueClass();
        List<Object> allowedValues = new ArrayList<>();
        if (valueClass == Boolean.class || valueClass == Integer.class) {
            for (T value : property.getPossibleValues()) {
                allowedValues.add(value);
            }
            String jsonType = valueClass == Boolean.class ? "boolean" : "integer";
            return new PlatformBlockProperty(property.getName(), jsonType, defaultValue, List.copyOf(allowedValues), null);
        }
        for (T value : property.getPossibleValues()) {
            allowedValues.add(property.getName(value));
        }
        return new PlatformBlockProperty(property.getName(), "string", property.getName(defaultValue), List.copyOf(allowedValues), null);
    }
}
