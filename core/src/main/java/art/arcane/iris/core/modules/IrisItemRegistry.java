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

package art.arcane.iris.core.modules;

import art.arcane.iris.core.link.Identifier;
import art.arcane.iris.core.service.ExternalDataSVC;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.spi.PlatformItem;
import com.volmit.item.ItemRegistry;

import java.util.OptionalInt;

/**
 * IrisApi's view of the bound platform's items: the platform's registry for plain keys, and the item providers of
 * Iris's external data service for keys with a namespace of their own ({@code itemsadder:ruby}), which is how packs name
 * custom items.
 */
public final class IrisItemRegistry implements ItemRegistry {
    private static final String MINECRAFT_PREFIX = "minecraft:";
    private static final char NAMESPACE_SEPARATOR = ':';

    @Override
    public OptionalInt maxDurability(String key) {
        if (isCustomItem(key)) {
            return customItemDurability(key);
        }
        PlatformItem item = IrisPlatforms.get().registries().item(key);
        return item == null ? OptionalInt.empty() : OptionalInt.of(item.maxDurability());
    }

    /** Whether {@code key} names an item of an external provider, which resolves it, rather than of the platform. */
    public static boolean isCustomItem(String key) {
        return !key.startsWith(MINECRAFT_PREFIX) && key.indexOf(NAMESPACE_SEPARATOR) >= 0;
    }

    private static OptionalInt customItemDurability(String key) {
        return IrisServices.get(ExternalDataSVC.class).maxDurability(Identifier.fromString(key));
    }
}
