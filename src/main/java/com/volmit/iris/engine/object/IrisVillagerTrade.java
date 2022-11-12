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
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

import java.util.List;


@Snippet("villager-trade")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@SuppressWarnings("BooleanMethodIsAlwaysInverted")
@Desc("Represents a villager trade.")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisVillagerTrade {

    @Required
    @RegistryListItemType
    @Desc("The first, required, ingredient for the trade.\nNote: this MUST be an item, and may not be a non-obtainable block!")
    private ItemStack ingredient1;

    @RegistryListItemType
    @Desc("The second, optional, ingredient for the trade.\nNote: this MUST be an item, and may not be a non-obtainable block!")
    private ItemStack ingredient2 = null;

    @Required
    @RegistryListItemType
    @Desc("The result of the trade.\nNote: this MUST be an item, and may not be a non-obtainable block!")
    private ItemStack result;

    @Desc("The min amount of times this trade can be done. Default 3")
    @MinNumber(1)
    @MaxNumber(64)
    private int minTrades = 3;

    @Desc("The max amount of times this trade can be done. Default 5")
    @MinNumber(1)
    @MaxNumber(64)
    private int maxTrades = 5;

    /**
     * @return true if:<br>
     * ingredient 1 & result are non-null,<br>
     * mintrades > 0, maxtrades > 0, maxtrades > mintrades, and<br>
     * ingredient 1, (if defined ingredient 2) and the result are valid items
     */
    public boolean isValidItems() {
        KList<String> warnings = new KList<>();
        if (ingredient1 == null) {
            warnings.add("Ingredient 1 is null");
        }

        if (result == null) {
            warnings.add("Result is null");
        }

        if (minTrades <= 0) {
            warnings.add("Negative minimal trades");
        }

        if (maxTrades <= 0) {
            warnings.add("Negative maximal trades");
        }

        if (minTrades > maxTrades) {
            warnings.add("More minimal than maximal trades");
        }

        if (ingredient1 != null && !ingredient1.getType().isItem()) {
            warnings.add("Ingredient 1 is not an item");
        }

        if (ingredient2 != null && !ingredient2.getType().isItem()) {
            warnings.add("Ingredient 2 is not an item");
        }

        if (result != null && !result.getType().isItem()) {
            warnings.add("Result is not an item");
        }

        if (warnings.isEmpty()) {
            return true;
        } else {
            Iris.warn("Faulty item in cartographer item overrides: " + this);
            warnings.forEach(w -> Iris.warn("   " + w));
            return false;
        }
    }

    /**
     * Get the ingredients
     *
     * @return The list of 1 or 2 ingredients (depending on if ing2 is null)
     */
    public List<ItemStack> getIngredients() {
        if (!isValidItems()) {
            return null;
        }
        return ingredient2 == null ? new KList<>(ingredient1) : new KList<>(ingredient1, ingredient2);
    }

    /**
     * @return the amount of trades (RNG.r.i(min, max))
     */
    public int getAmount() {
        return RNG.r.i(minTrades, maxTrades);
    }

    /**
     * @return the trade as a merchant recipe
     */
    public MerchantRecipe convert() {
        MerchantRecipe recipe = new MerchantRecipe(getResult(), getAmount());
        recipe.setIngredients(getIngredients());
        return recipe;
    }
}
