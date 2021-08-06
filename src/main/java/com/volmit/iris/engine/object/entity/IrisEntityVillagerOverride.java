package com.volmit.iris.engine.object.entity;

import com.volmit.iris.engine.object.annotations.DependsOn;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.Required;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor

@Desc("Override cartographer map trades with others or disable the trade altogether")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisEntityVillagerOverride {
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
            By default, this is 3 emeralds + 3 glass blocks -> 1 spyglass.
                Can trade 3 to 5 times""")
    private IrisEntityVillagerOverrideItems items = new IrisEntityVillagerOverrideItems()
            .setIngredient1(new ItemStack(Material.EMERALD, 3))
            .setIngredient2(new ItemStack(Material.GLASS, 3))
            .setResult(new ItemStack(Material.SPYGLASS))
            .setMinTrades(3)
            .setMaxTrades(5);
}
