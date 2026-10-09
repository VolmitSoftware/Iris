/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.engine.object.loot;

import art.arcane.iris.engine.object.entity.IrisAttributeModifier;
import art.arcane.iris.engine.object.annotations.ArrayType;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.MaxNumber;
import art.arcane.iris.engine.object.annotations.MinNumber;
import art.arcane.iris.engine.object.annotations.RegistryListItemType;
import art.arcane.iris.engine.object.annotations.Required;
import art.arcane.iris.engine.object.annotations.Snippet;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

@Snippet("loot")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents a loot entry")
@Data
@EqualsAndHashCode(doNotUseGetters = true)
@ToString(doNotUseGetters = true)
public class IrisLoot {
    public static final int MAX_AMOUNT = 64;

    @Desc("The target inventory slot types to fill this loot with. World generation only ever fills STORAGE; the other values are inert during worldgen.")
    private InventorySlotType slotTypes = InventorySlotType.STORAGE;
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
    @MinNumber(1)
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
    @ArrayType(min = 1, type = String.class)
    @Desc("The item flags to add")
    private KList<String> itemFlags = new KList<>();
    @Desc("Apply enchantments to this item")
    @ArrayType(min = 1, type = IrisEnchantment.class)
    private KList<IrisEnchantment> enchantments = new KList<>();
    @Desc("Apply attribute modifiers to this item")
    @ArrayType(min = 1, type = IrisAttributeModifier.class)
    private KList<IrisAttributeModifier> attributes = new KList<>();
    @ArrayType(min = 1, type = String.class)
    @Desc("Add lore to this item")
    private KList<String> lore = new KList<>();
    @RegistryListItemType
    @Required
    @Desc("This is the item or block type. Does not accept minecraft:*, only materials such as DIAMOND_SWORD or DIRT. The exception are modded materials, as they require a namespace.")
    private String type = "";
    @Desc("The dye color")
    private String dyeColor = null;
    @Desc("The leather armor color")
    private String leatherColor = null;
    @Desc("Defines a custom NBT Tag for the item.")
    private KMap<String, Object> customNbt;

    /**
     * The authored item key, exactly as written in the pack. Each platform resolves it against its own item registry.
     */
    public String getTypeKey() {
        return type;
    }

    /**
     * The authored dye colour name, or null when unset.
     */
    public String getDyeColorKey() {
        return dyeColor;
    }
}
