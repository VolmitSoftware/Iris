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

package art.arcane.iris.spi;

import art.arcane.volmlib.nativelib.terrain.NativeBlockProperty;

import art.arcane.volmlib.nativelib.entity.NativeEntityType;

import art.arcane.volmlib.nativelib.item.NativeItem;

import art.arcane.volmlib.nativelib.terrain.NativeBiome;

import art.arcane.volmlib.nativelib.terrain.NativeBlockState;

import java.util.List;
import java.util.Map;

/**
 * Resolves namespaced string keys against the platform's live registries into interned neutral handles.
 * <p>
 * Keys are the pack's currency: {@code namespace:path} with optional {@code [prop=value,...]} block state
 * properties. Resolution runs on generation threads for every block a pack names, so implementations must be
 * thread-safe and must intern or cache their results - a key that resolves once should not re-parse.
 * <p>
 * Internal to Iris; not a published integration surface.
 */
public interface PlatformRegistries {
    default PlatformGenerationRegistry generationRegistry() {
        throw new UnsupportedOperationException(
                "The active platform does not expose generation registry definitions."
        );
    }

    /**
     * Resolves a block key through the platform's compatibility layer, which rewrites keys that moved between
     * Minecraft versions and consults registered custom-content providers. An unresolvable key is reported and
     * falls back to air; a key that cannot be parsed at all may come back null. Callers that need to tell
     * absence from air use {@link #blockOrNull(String)}.
     */
    NativeBlockState block(String key);

    /**
     * Resolves a block key, returning null instead of an air fallback when it does not resolve. Silent.
     * <p>
     * Unlike {@link #block(String)} this never consults the platform's compatibility layer. On Bukkit that layer only
     * sees keys the underlying lookup could not answer at all, so an unregistered key resolves to air through
     * {@link #block(String)} on every platform - the Bukkit-only legacy rewrite table cannot fork generation output.
     */
    NativeBlockState blockOrNull(String key);

    /**
     * {@link #blockOrNull(String)} with control over whether an unresolved key is logged. Pass
     * {@code warn = false} for speculative lookups.
     */
    NativeBlockState blockOrNull(String key, boolean warn);

    /**
     * The interned air state. Never null; identity-comparable across calls.
     */
    NativeBlockState air();

    /**
     * The deepslate variant of {@code ore} when {@code block} is deepslate, otherwise {@code ore} unchanged.
     * Lets ore placement follow the host stone without the pack enumerating variants.
     */
    NativeBlockState deepSlateOre(NativeBlockState block, NativeBlockState ore);

    /**
     * Resolves a biome key against the live biome registry, including datapack and mod biomes. Null when the
     * key does not parse or is not registered.
     */
    NativeBiome biome(String key);

    /**
     * Resolves an item key. Null when unknown.
     */
    NativeItem item(String key);

    /**
     * Resolves an entity type key. Null when unknown.
     */
    NativeEntityType entity(String key);

    /**
     * Every registered block state key, properties included. Drives schema completion and command
     * suggestions, not the generation path. Never null.
     */
    List<String> blockKeys();

    /**
     * Every registered biome key. Never null.
     */
    List<String> biomeKeys();

    /**
     * Every registered structure key. Never null.
     */
    List<String> structureKeys();

    /**
     * Every registered item key. Never null.
     */
    List<String> itemKeys();

    /**
     * Every registered entity type key. Never null.
     */
    List<String> entityKeys();

    /**
     * Every registered block key without state properties - the material-level view of
     * {@link #blockKeys()}. Never null.
     */
    List<String> blockTypeKeys();

    /**
     * Every entity key contributed by third-party content integrations rather than the vanilla entity registry -
     * Bukkit item/mob plugins on the Bukkit side, registered custom-content providers on mod loaders. Feeds pack
     * schema completion for custom mob types only; never the spawn path. Never null.
     * <p>
     * Defaults to empty so a platform with no integration surface needs no implementation, and so schema
     * generation never has to reference a platform-specific service type directly.
     */
    default List<String> specialEntityKeys() {
        return List.of();
    }

    /**
     * Every registered enchantment key. Never null.
     */
    List<String> enchantmentKeys();

    /**
     * Every registered potion effect key. Never null.
     */
    List<String> potionEffectKeys();

    List<String> lootTableKeys();

    /**
     * Block key to its declared state properties, used to generate pack schema enums and numeric ranges.
     * Keyed by material-level block key. Never null.
     */
    Map<String, List<NativeBlockProperty>> blockStateProperties();
}
