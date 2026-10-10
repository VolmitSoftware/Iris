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
import java.util.Map;

/**
 * An item a module decided on, in host terms: what {@code com.volmit.item.ItemDrop} says, for adapters that cannot name
 * IrisApi types. The module made every random choice; the adapter only creates the item on its platform.
 *
 * @param damage the fraction of the item's durability already used, or null to leave it undamaged
 */
public record ContentItem(String item, int amount, String displayName, List<String> lore, List<Enchantment> enchantments,
                          List<Attribute> attributes, boolean unbreakable, List<String> flags, Integer customModel,
                          Double damage, String leatherColor, String dyeColor, Map<String, Object> customNbt) {
    /** An enchantment at the level the module decided. */
    public record Enchantment(String key, int level) {
    }

    /** An attribute modifier with the amount the module decided. */
    public record Attribute(String attribute, String name, String operation, double amount) {
    }
}
