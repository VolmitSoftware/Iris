package com.volmit.iris.core;

import com.volmit.iris.Iris;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.object.entity.IrisEntityVillagerOverride;
import com.volmit.iris.util.math.RNG;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.inventory.MerchantRecipe;

public class InteractionManager implements Listener {

    /**
     * Prevents dolphins from trying to locate a treasure map.
     * Note: This results in odd dolphin behaviour, but it's the best we can do.
     */
    @EventHandler
    public void on(EntityPickupItemEvent event){
        if (!IrisToolbelt.isIrisWorld(event.getEntity().getWorld())){
            return;
        }

        if (event.getEntityType().equals(EntityType.DOLPHIN)){
            event.setCancelled(true);
        }
    }

    /**
     * Replace or disable villager trade add event to prevent explorer map
     */
    @EventHandler
    public void on(VillagerAcquireTradeEvent event){
        if (!IrisToolbelt.isIrisWorld((event.getEntity().getWorld()))){
            return;
        }

        // Iris.info("Trade event: type " + event.getRecipe().getResult().getType() + " / meta " + event.getRecipe().getResult().getItemMeta() + " / data " + event.getRecipe().getResult().getData());
        if (event.getRecipe().getResult().getType().equals(Material.FILLED_MAP)){
            IrisEntityVillagerOverride override = IrisToolbelt.access(event.getEntity().getWorld()).getCompound().getRootDimension().getVillagerTrade();

            if (override.isDisableTrade()){
                event.setCancelled(true);
                Iris.debug("Cancelled cartographer trade @ " + event.getEntity().getLocation());
                return;
            }

            if (!override.getItems().isValidItems()){
                event.setCancelled(true);
                Iris.debug("Cancelled cartographer trade because override items not valid @ " + event.getEntity().getLocation());
                return;
            }

            MerchantRecipe recipe = new MerchantRecipe(override.getItems().getResult(), override.getItems().getAmount());
            recipe.setIngredients(override.getItems().getIngredients());
            event.setRecipe(recipe);
            Iris.debug("Overrode cartographer trade with: " + recipe + " to prevent allowing cartography map trades");
        }
    }
}
