package com.volmit.iris.engine.object.entity;


import com.volmit.iris.engine.object.annotations.*;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.inventory.ItemStack;

import java.util.List;


@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor

@SuppressWarnings("BooleanMethodIsAlwaysInverted")
@Desc("Override cartographer map trades with these items. ")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisEntityVillagerOverrideItems {

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
    public boolean isValidItems(){
        if (ingredient1 == null || result == null || minTrades <= 0 || maxTrades <= 0 || maxTrades < minTrades){
            return false;
        }
        return ingredient1.getType().isItem() && (ingredient2 == null || ingredient2.getType().isItem()) && result.getType().isItem();
    }

    /**
     * Get the ingredients
     * @return The list of 1 or 2 ingredients (depending on if ing2 is null)
     */
    public List<ItemStack> getIngredients() {
        if (!isValidItems()){
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
}
