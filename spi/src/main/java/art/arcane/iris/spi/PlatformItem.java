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

/**
 * Neutral handle for a resolved item type backed by an adapter-owned native handle.
 * <p>
 * Describes an item type, not a stack - no count, no components. Immutable and safe to share across threads.
 * Internal to Iris; not a published integration surface.
 *
 * @see PlatformRegistries#item(String)
 */
public interface PlatformItem {
    /**
     * Canonical {@code namespace:path} item key. Never null.
     */
    String key();

    /**
     * Namespace half of {@link #key()}. Never null.
     */
    String namespace();

    /**
     * The adapter's backing item object - {@code org.bukkit.Material} on Bukkit, an {@code Item} registry value
     * on a mod loader. Never null. Only code inside the owning adapter may cast it.
     */
    Object nativeHandle();

    /**
     * The damage a stack of this item takes before it breaks, 0 for an item that cannot be damaged.
     */
    int maxDurability();
}
