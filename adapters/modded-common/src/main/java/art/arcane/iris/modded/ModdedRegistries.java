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
import art.arcane.iris.spi.PlatformGenerationRegistry;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.volmlib.nativelib.entity.NativeEntityType;
import art.arcane.volmlib.nativelib.item.NativeItem;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeRegistryAccess;
import art.arcane.volmlib.nativelib.terrain.NativeBiome;
import art.arcane.volmlib.nativelib.terrain.NativeBlockProperty;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.data.UnresolvedKeyLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class ModdedRegistries implements PlatformRegistries {
    private static final UnresolvedKeyLog NOT_READY = new UnresolvedKeyLog("Iris modded registry reads before server ready", 60_000L);

    private final NativeRegistryAccess nativeAccess;
    private final Supplier<PlatformGenerationRegistry> generationRegistry;

    public ModdedRegistries(NativeRegistryAccess nativeAccess, Supplier<PlatformGenerationRegistry> generationRegistry) {
        this.nativeAccess = nativeAccess;
        this.generationRegistry = generationRegistry;
    }

    @Override
    public PlatformGenerationRegistry generationRegistry() {
        return generationRegistry.get();
    }

    @Override
    public NativeBlockState block(String key) {
        return ModdedBlockResolution.BLOCKS.get(key);
    }

    @Override
    public NativeBlockState blockOrNull(String key) {
        return ModdedBlockResolution.BLOCKS.getOrNull(key);
    }

    @Override
    public NativeBlockState blockOrNull(String key, boolean warn) {
        return ModdedBlockResolution.BLOCKS.getOrNull(key, warn);
    }

    @Override
    public NativeBlockState air() {
        return ModdedBlockResolution.BLOCKS.getAir();
    }

    @Override
    public NativeBlockState deepSlateOre(NativeBlockState block, NativeBlockState ore) {
        return nativeAccess.deepSlateOre(block, ore);
    }

    @Override
    public NativeBiome biome(String key) {
        return nativeAccess.biome(key);
    }

    @Override
    public NativeItem item(String key) {
        return nativeAccess.item(key);
    }

    @Override
    public NativeEntityType entity(String key) {
        return nativeAccess.entity(key);
    }

    @Override
    public List<String> blockKeys() {
        return nativeAccess.blockKeys();
    }

    @Override
    public List<String> biomeKeys() {
        return nativeAccess.biomeKeys();
    }

    @Override
    public List<String> structureKeys() {
        return nativeAccess.structureKeys();
    }

    @Override
    public List<String> entityKeys() {
        return nativeAccess.entityKeys();
    }

    @Override
    public List<String> enchantmentKeys() {
        return nativeAccess.enchantmentKeys();
    }

    @Override
    public List<String> potionEffectKeys() {
        return nativeAccess.potionEffectKeys();
    }

    @Override
    public List<String> lootTableKeys() {
        return nativeAccess.lootTableKeys();
    }

    @Override
    public List<String> itemKeys() {
        List<String> keys = nativeAccess.itemKeys();
        keys.addAll(ModdedCustomContentRegistry.providerKeys(ModdedDataType.ITEM));
        return keys;
    }

    @Override
    public List<String> specialEntityKeys() {
        return ModdedCustomContentRegistry.providerKeys(ModdedDataType.ENTITY);
    }

    @Override
    public List<String> blockTypeKeys() {
        List<String> keys = nativeAccess.blockKeys();
        keys.addAll(customBlockKeys());
        return keys;
    }

    @Override
    public Map<String, List<NativeBlockProperty>> blockStateProperties() {
        Map<String, List<NativeBlockProperty>> properties = nativeAccess.blockStateProperties();
        List<NativeBlockProperty> none = List.of();
        for (List<NativeBlockProperty> group : properties.values()) {
            if (group.isEmpty()) {
                none = group;
                break;
            }
        }
        for (String key : customBlockKeys()) {
            properties.putIfAbsent(key, none);
        }
        return properties;
    }

    private static List<String> customBlockKeys() {
        List<String> keys = new ArrayList<>(ModdedCustomContentRegistry.aliasBlockKeys());
        keys.addAll(ModdedCustomContentRegistry.providerKeys(ModdedDataType.BLOCK));
        return keys;
    }

    static void warnNotReady(String registryName) {
        if (NOT_READY.firstOccurrence(registryName)) {
            IrisLogging.warn("Iris registry read for '" + registryName + "' before the server is ready; returning empty");
        }
        String summary = NOT_READY.pollSummary();
        if (summary != null) {
            IrisLogging.warn(summary);
        }
    }
}
