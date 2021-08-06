package com.volmit.iris.core;

import com.volmit.iris.Iris;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.object.villager.IrisVillagerOverride;
import com.volmit.iris.engine.object.villager.IrisVillagerTrade;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class InteractionManager implements Listener {

    /**
     * Prevents dolphins from being fed, to locate a treasure map.
     * Note: This results in odd dolphin behaviour, but it's the best we can do.
     */
    @EventHandler
    public void on(PlayerInteractEntityEvent event){
        if (!IrisToolbelt.isIrisWorld(event.getPlayer().getWorld())){
            return;
        }

        Material hand = event.getPlayer().getInventory().getItem(event.getHand()).getType();
        if (event.getRightClicked().getType().equals(EntityType.DOLPHIN) && (hand.equals(Material.TROPICAL_FISH) || hand.equals(Material.PUFFERFISH) || hand.equals(Material.COD) || hand.equals(Material.SALMON))){
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
            IrisVillagerOverride override = IrisToolbelt.access(event.getEntity().getWorld()).getCompound().getRootDimension().getPatchCartographers();

            if (override.isDisableTrade()){
                event.setCancelled(true);
                Iris.debug("Cancelled cartographer trade @ " + event.getEntity().getLocation());
                return;
            }

            if (override.getValidItems() == null){
                event.setCancelled(true);
                Iris.debug("Cancelled cartographer trade because no override items are valid @ " + event.getEntity().getLocation());
                return;
            }

            IrisVillagerTrade trade = override.getValidItems().getRandom();
            event.setRecipe(trade.convert());
            Iris.debug("Overrode cartographer trade with: " + trade + " to prevent allowing cartography map trades");
        }
    }
}
