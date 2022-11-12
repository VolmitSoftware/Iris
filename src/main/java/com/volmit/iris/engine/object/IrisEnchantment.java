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

package com.volmit.iris.engine.object;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.object.annotations.*;
import com.volmit.iris.util.math.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;


@Snippet("enchantment")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents an enchantment & level")
@Data
public class IrisEnchantment {
    @Required
    @RegistryListEnchantment
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

    public void apply(RNG rng, ItemMeta meta) {
        try {
            Enchantment enchant = Enchantment.getByKey(NamespacedKey.minecraft(getEnchantment()));
            if (enchant == null) {
                Iris.warn("Unknown Enchantment: " + getEnchantment());
                return;
            }
            if (rng.nextDouble() < chance) {
                if (meta instanceof EnchantmentStorageMeta) {
                    ((EnchantmentStorageMeta) meta).addStoredEnchant(enchant, getLevel(rng), true);
                    return;
                }
                meta.addEnchant(enchant, getLevel(rng), true);
            }
        } catch (Throwable e) {
            Iris.reportError(e);

        }
    }

    public int getLevel(RNG rng) {
        return rng.i(getMinLevel(), getMaxLevel());
    }
}
