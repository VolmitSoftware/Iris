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
import com.volmit.config.Required;
import com.volmit.config.Snippet;

@Snippet("attribute-modifier")
@Desc("Represents an attribute modifier for an item or an entity. This allows you to create modifications to basic game attributes such as MAX_HEALTH or ARMOR_VALUE.")
public final class ItemAttributeModifier {
    @Required
    @Desc("The Attribute type key such as max_health or minecraft:armor. This type is pulled from the game attributes. Zombie & Horse attributes will not work on non-zombie/horse entities.\nUsing an attribute on an item will have affects when held, or worn. There is no way to specify further granularity as the game picks this depending on the item type.")
    private String attribute = null;

    @Required
    @Desc("The Attribute Name is used internally only for the game. This value should be unique to all other attributes applied to this item/entity. It is not shown in game.")
    private String name = "";

    @Desc("The application operation: ADD_NUMBER (default), ADD_SCALAR or MULTIPLY_SCALAR_1. Add Number adds to the default value. \nAdd scalar_1 will multiply by 1 for example if the health is 20 and you multiply_scalar_1 by 0.5, the health will result in 30, not 10. Use negative values to achieve that.")
    private String operation = "ADD_NUMBER";

    @Desc("Minimum amount for this modifier. Iris randomly chooses an amount, this is the minimum it can choose randomly for this attribute.")
    private double minAmount = 1;

    @Desc("Maximum amount for this modifier Iris randomly chooses an amount, this is the maximum it can choose randomly for this attribute.")
    private double maxAmount = 1;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("The chance that this attribute is applied (0 to 1). If the chance is 0.5 (50%), then Iris will only apply this attribute 50% of the time.")
    private double chance = 1;

    public ItemAttributeModifier() {
    }

    public String attribute() {
        return attribute;
    }

    public String name() {
        return name;
    }

    public String operation() {
        return operation;
    }

    public double minAmount() {
        return minAmount;
    }

    public double maxAmount() {
        return maxAmount;
    }

    public double chance() {
        return chance;
    }
}
