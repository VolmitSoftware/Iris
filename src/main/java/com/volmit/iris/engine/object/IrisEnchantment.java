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
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.MaxNumber;
import com.volmit.iris.engine.object.annotations.MinNumber;
import com.volmit.iris.engine.object.annotations.Required;
import com.volmit.iris.engine.object.annotations.Snippet;
import com.volmit.iris.util.math.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Field;

@Snippet("enchantment")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents an enchantment & level")
@Data
public class IrisEnchantment {
    @Required
    @Desc("The enchantment")
    private String enchantment = "";

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
            if(rng.nextDouble() < chance) {
                if(meta instanceof EnchantmentStorageMeta) {
                    ((EnchantmentStorageMeta) meta).addStoredEnchant(getEnchant(), getLevel(rng), true);
                    return;
                }
                meta.addEnchant(getEnchant(), getLevel(rng), true);
            }
        } catch(Throwable e) {
            Iris.reportError(e);

        }
    }

    public Enchantment getEnchant() {
        for(Field i : Enchantment.class.getDeclaredFields()) {
            if(i.getType().equals(Enchantment.class) && i.getName().equals(getEnchantment())) {
                try {
                    return (Enchantment) i.get(null);
                } catch(IllegalArgumentException | IllegalAccessException e) {
                    e.printStackTrace();
                    Iris.reportError(e);
                }
            }
        }

        return null;
    }

    public int getLevel(RNG rng) {
        return rng.i(getMinLevel(), getMaxLevel());
    }
}
