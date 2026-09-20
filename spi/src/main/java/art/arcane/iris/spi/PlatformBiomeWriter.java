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

import art.arcane.volmlib.nativelib.terrain.NativeBiome;

import java.util.List;

/**
 * Resolves pack biome keys to platform biome ids for mantle injection and enumerates the platform's biome registry.
 * <p>
 * Called from generation threads for every biome the pack names, so implementations must be thread-safe and
 * should cache their registry lookups.
 * <p>
 * Internal to Iris; not a published integration surface.
 */
public interface PlatformBiomeWriter {
    /**
     * The numeric registry id the host uses for {@code key}, which is what gets written into biome storage.
     * <p>
     * Ids are registry-order dependent and therefore valid only for the current server session; never persist
     * one. Adapters resolve the key directly, then fall back to a derived match, and finally to a safe default
     * id rather than failing - so a nonsense key yields a wrong biome, not an exception. Validate keys with
     * {@link PlatformRegistries#biome(String)} when you need to know they exist.
     */
    int biomeIdFor(String key);

    /**
     * Every biome in the host registry, including datapack and mod biomes. Never null.
     */
    List<NativeBiome> allBiomes();
}
