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

import art.arcane.volmlib.nativelib.item.NativeItem;
import org.bukkit.Material;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Interned Bukkit adapter for a neutral item handle backed by Material.
 */
public final class BukkitItem implements NativeItem {
    private static final ConcurrentHashMap<String, BukkitItem> CACHE = new ConcurrentHashMap<>();

    private final Material material;
    private final String key;
    private final String namespace;

    private BukkitItem(Material material, String key) {
        this.material = material;
        this.key = key;
        int colon = key.indexOf(':');
        this.namespace = colon >= 0 ? key.substring(0, colon) : "minecraft";
    }

    public static BukkitItem of(Material material) {
        String key = material.getKey().toString();
        return CACHE.computeIfAbsent(key, (String k) -> new BukkitItem(material, k));
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
        return material;
    }
}
