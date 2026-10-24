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
import com.volmit.resource.ContentKey;

import java.util.OptionalInt;

/**
 * IrisApi's view of the bound platform's items: the platform's registry for keys of its own namespace, and the item
 * providers of Iris's external data service for every other item ({@code itemsadder:ruby}, {@code mmoitems_SWORD:KATANA}),
 * which is how packs name custom items.
 */
public final class IrisItemRegistry implements ItemRegistry {
    private static final String PLATFORM_NAMESPACE = "minecraft";

    @Override
    public OptionalInt maxDurability(ContentKey item) {
        if (isCustomItem(item)) {
            return IrisServices.get(ExternalDataSVC.class).maxDurability(identifierOf(item));
        }
        PlatformItem platform = IrisPlatforms.get().registries().item(item.toString());
        return platform == null ? OptionalInt.empty() : OptionalInt.of(platform.maxDurability());
    }

    /** Whether {@code item} is one an external provider resolves rather than the platform. */
    public static boolean isCustomItem(ContentKey item) {
        return switch (item) {
            case ContentKey.Provided ignored -> true;
            case ContentKey.Registered registered -> !PLATFORM_NAMESPACE.equals(registered.id().namespace());
        };
    }

    /** {@code item} as Iris's external data service names it. */
    public static Identifier identifierOf(ContentKey item) {
        return switch (item) {
            case ContentKey.Provided provided -> new Identifier(provided.provider(), provided.name());
            case ContentKey.Registered registered -> new Identifier(registered.id().namespace(), registered.id().path());
        };
    }
}
