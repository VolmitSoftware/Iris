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

import com.volmit.iris.engine.object.annotations.*;
import com.volmit.iris.util.collection.KList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

@Snippet("villager-override")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Override cartographer map trades with others or disable the trade altogether")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisVillagerOverride {
    @Desc("""
            Disable the trade altogether.
            If a cartographer villager gets a new explorer map trade:
            If this is enabled -> the trade is removed
            If this is disabled -> the trade is replaced with the "override" setting below
            Default is true, so if you omit this, trades will be removed.""")
    private boolean disableTrade = true;

    @DependsOn("disableTrade")
    @Required
    @Desc("""
            The items to override the cartographer trade with.
            By default, this is:
                3 emeralds + 3 glass blocks -> 1 spyglass.
                Can trade 3 to 5 times""")
    @ArrayType(min = 1, type = IrisVillagerTrade.class)
    private KList<IrisVillagerTrade> items = new KList<>(new IrisVillagerTrade()
            .setIngredient1(new ItemStack(Material.EMERALD, 3))
            .setIngredient2(new ItemStack(Material.GLASS, 3))
            .setResult(new ItemStack(Material.SPYGLASS))
            .setMinTrades(3)
            .setMaxTrades(5));

    public KList<IrisVillagerTrade> getValidItems() {
        KList<IrisVillagerTrade> valid = new KList<>();
        getItems().stream().filter(IrisVillagerTrade::isValidItems).forEach(valid::add);
        return valid.size() == 0 ? null : valid;
    }
}
