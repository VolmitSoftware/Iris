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

import art.arcane.iris.spi.PlatformBiome;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;

/**
 * Interned Bukkit adapter for a neutral biome handle.
 */
public final class BukkitBiome implements PlatformBiome {
    private static final Cache<Biome, BukkitBiome> CACHE = Caffeine.newBuilder()
            .weakKeys()
            .maximumSize(4_096)
            .build();

    private final Biome biome;
    private final String key;
    private final String namespace;

    private BukkitBiome(Biome biome) {
        this.biome = biome;
        NamespacedKey biomeKey = biome.getKey();
        this.key = biomeKey.toString();
        this.namespace = biomeKey.getNamespace();
    }

    public static BukkitBiome of(Biome biome) {
        return CACHE.get(biome, BukkitBiome::new);
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public String namespace() {
        return namespace;
    }

    @Override
    public Object nativeHandle() {
        return biome;
    }
}
