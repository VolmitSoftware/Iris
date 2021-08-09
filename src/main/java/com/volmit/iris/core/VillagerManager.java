package com.volmit.iris.core;

import com.volmit.iris.Iris;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.object.villager.IrisVillagerOverride;
import com.volmit.iris.engine.object.villager.IrisVillagerTrade;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;

public class VillagerManager implements Listener {
    /**
     * Replace or disable villager trade add event to prevent explorer map
     */
    @EventHandler
    public void on(VillagerAcquireTradeEvent event) {
        if (!IrisToolbelt.isIrisWorld((event.getEntity().getWorld()))) {
            return;
        }

        // Iris.info("Trade event: type " + event.getRecipe().getResult().getType() + " / meta " + event.getRecipe().getResult().getItemMeta() + " / data " + event.getRecipe().getResult().getData());
        if (!event.getRecipe().getResult().getType().equals(Material.FILLED_MAP)) {
            return;
        }

        IrisVillagerOverride override = IrisToolbelt.access(event.getEntity().getWorld()).getEngine()
                .getDimension().getPatchCartographers();

        if (override.isDisableTrade()) {
            event.setCancelled(true);
            Iris.debug("Cancelled cartographer trade @ " + event.getEntity().getLocation());
            return;
        }

        if (override.getValidItems() == null) {
            event.setCancelled(true);
            Iris.debug("Cancelled cartographer trade because no override items are valid @ " + event.getEntity().getLocation());
            return;
        }

        IrisVillagerTrade trade = override.getValidItems().getRandom();
        event.setRecipe(trade.convert());
        Iris.debug("Overrode cartographer trade with: " + trade + " to prevent allowing cartography map trades");
    }
}
