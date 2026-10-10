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

package art.arcane.iris.core.content;

import java.util.List;

/** Where a container's items come from and what a broken block drops, in host terms. */
public final class ContentLoot {
    private ContentLoot() {
    }

    /** One source of a container's items, in the order their items are added. */
    public sealed interface Part permits Items, PlatformTable {
    }

    /** Items a module drew from one of the pack's loot tables. */
    public record Items(List<ContentItem> items) implements Part {
    }

    /** A loot table of the platform, which the adapter draws with {@code seed} so the same container always gets the same items. */
    public record PlatformTable(String key, long seed) implements Part {
    }

    /**
     * What the modules drop for a broken block.
     *
     * @param replacesVanillaDrops whether the platform's own drops of the block are not dropped
     */
    public record BlockDrops(List<ContentItem> drops, boolean replacesVanillaDrops) {
    }
}
