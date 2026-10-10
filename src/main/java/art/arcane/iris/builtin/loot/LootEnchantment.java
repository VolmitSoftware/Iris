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

@Snippet("enchantment")
@Desc("Represents an enchantment & level")
public final class LootEnchantment {
    @Required
    @Ref(Reference.ENCHANTMENT)
    @Desc("The enchantment")
    private String enchantment;

    @MinNumber(1)
    @Desc("Minimum amount of this loot")
    private int minLevel = 1;

    @MinNumber(1)
    @Desc("Maximum amount of this loot")
    private int maxLevel = 1;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("The chance that this enchantment is applied (0 to 1)")
    private double chance = 1;

    public LootEnchantment() {
    }

    public String enchantment() {
        return enchantment;
    }

    public int minLevel() {
        return minLevel;
    }

    public int maxLevel() {
        return maxLevel;
    }

    public double chance() {
        return chance;
    }
}
