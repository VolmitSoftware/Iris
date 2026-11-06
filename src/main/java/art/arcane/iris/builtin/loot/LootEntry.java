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

package art.arcane.iris.builtin.loot;

import com.volmit.config.Desc;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;
import com.volmit.config.Ref;
import com.volmit.config.Reference;
import com.volmit.config.Required;
import com.volmit.config.Snippet;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Snippet("iris/loot/loot")
@Desc("Represents a loot entry")
public final class LootEntry {
    public static final int MAX_AMOUNT = 64;

    @Desc("The target inventory slot types to fill this loot with. World generation only ever fills STORAGE; the other values are inert during worldgen.")
    private LootSlot slotTypes = LootSlot.STORAGE;

    @MinNumber(1)
    @Desc("The sub rarity of this loot. Calculated after this loot table has been picked.")
    private int rarity = 1;

    @MinNumber(1)
    @MaxNumber(MAX_AMOUNT)
    @Desc("Minimum amount of this loot, from 1 to 64")
    private int minAmount = 1;

    @MinNumber(1)
    @MaxNumber(MAX_AMOUNT)
    @Desc("Maximum amount of this loot, from 1 to 64")
    private int maxAmount = 1;

    @Desc("The display name of this item")
    private String displayName = null;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Minimum durability percent")
    private double minDurability = 0;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Maximum durability percent")
    private double maxDurability = 1;

    @Desc("Define a custom model identifier 1.14+ only")
    private Integer customModel = null;

    @Desc("Set this to true to prevent it from being broken")
    private boolean unbreakable = false;

    @Desc("The item flags to add")
    private List<String> itemFlags = List.of();

    @Desc("Apply enchantments to this item")
    private List<LootEnchantment> enchantments = List.of();

    @Desc("Apply attribute modifiers to this item")
    private List<ItemAttributeModifier> attributes = List.of();

    @Desc("Add lore to this item")
    private List<String> lore = List.of();

    @Ref(Reference.ITEM)
    @Required
    @Desc("This is the item or block type. Does not accept minecraft:*, only materials such as DIAMOND_SWORD or DIRT. The exception are modded materials, as they require a namespace.")
    private String type = "";

    @Desc("The dye color")
    private String dyeColor = null;

    @Desc("The leather armor color")
    private String leatherColor = null;

    @Desc("Defines a custom NBT Tag for the item.")
    private Map<String, Object> customNbt;

    public LootEntry() {
    }

    public LootSlot slotTypes() {
        return slotTypes == null ? LootSlot.STORAGE : slotTypes;
    }

    public int rarity() {
        return rarity;
    }

    public int minAmount() {
        return minAmount;
    }

    public int maxAmount() {
        return maxAmount;
    }

    public String displayName() {
        return displayName;
    }

    public double minDurability() {
        return minDurability;
    }

    public double maxDurability() {
        return maxDurability;
    }

    public Integer customModel() {
        return customModel;
    }

    public boolean unbreakable() {
        return unbreakable;
    }

    public List<String> itemFlags() {
        return listOrEmpty(itemFlags);
    }

    public List<LootEnchantment> enchantments() {
        return listOrEmpty(enchantments);
    }

    public List<ItemAttributeModifier> attributes() {
        return listOrEmpty(attributes);
    }

    public List<String> lore() {
        return listOrEmpty(lore);
    }

    /** The authored item key, exactly as written in the pack. Each platform resolves it against its own item registry. */
    public String type() {
        return type;
    }

    public String dyeColor() {
        return dyeColor;
    }

    public String leatherColor() {
        return leatherColor;
    }

    /** The custom data of the item; empty when unset. */
    public Map<String, Object> customNbt() {
        return customNbt == null ? Map.of() : Collections.unmodifiableMap(customNbt);
    }

    private static <T> List<T> listOrEmpty(List<T> list) {
        return list == null ? List.of() : Collections.unmodifiableList(list);
    }
}
