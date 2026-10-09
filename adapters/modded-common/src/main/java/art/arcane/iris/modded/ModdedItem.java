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

import art.arcane.iris.spi.PlatformItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.concurrent.ConcurrentHashMap;

public final class ModdedItem implements PlatformItem {
    private static final ConcurrentHashMap<String, ModdedItem> CACHE = new ConcurrentHashMap<>();

    private final Item item;
    private final String key;
    private final String namespace;

    private ModdedItem(Item item, String key) {
        this.item = item;
        this.key = key;
        int colon = key.indexOf(':');
        this.namespace = colon >= 0 ? key.substring(0, colon) : "minecraft";
    }

    public static ModdedItem of(Item item, String key) {
        return CACHE.computeIfAbsent(key, (String k) -> new ModdedItem(item, k));
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
        return item;
    }

    @Override
    public int maxDurability() {
        return new ItemStack(item).getMaxDamage();
    }
}
