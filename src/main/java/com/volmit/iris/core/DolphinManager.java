package com.volmit.iris.core;

import com.volmit.iris.Iris;
import com.volmit.iris.core.tools.IrisToolbelt;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class DolphinManager implements Listener {

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
}
