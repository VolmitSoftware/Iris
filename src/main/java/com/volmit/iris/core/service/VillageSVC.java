/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.core.service;

import com.volmit.iris.Iris;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.object.IrisVillagerOverride;
import com.volmit.iris.engine.object.IrisVillagerTrade;
import com.volmit.iris.util.plugin.IrisService;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;

public class VillageSVC implements IrisService {
    @Override
    public void onEnable() {

    }

    @Override
    public void onDisable() {

    }

    /**
     * Replace or disable villager trade add event to prevent explorer map
     */
    @EventHandler
    public void on(VillagerAcquireTradeEvent event) {
        if(!IrisToolbelt.isIrisWorld((event.getEntity().getWorld()))) {
            return;
        }

        // Iris.info("Trade event: type " + event.getRecipe().getResult().getType() + " / meta " + event.getRecipe().getResult().getItemMeta() + " / data " + event.getRecipe().getResult().getData());
        if(!event.getRecipe().getResult().getType().equals(Material.FILLED_MAP)) {
            return;
        }

        IrisVillagerOverride override = IrisToolbelt.access(event.getEntity().getWorld()).getEngine()
            .getDimension().getPatchCartographers();

        if(override.isDisableTrade()) {
            event.setCancelled(true);
            Iris.debug("Cancelled cartographer trade @ " + event.getEntity().getLocation());
            return;
        }

        if(override.getValidItems() == null) {
            event.setCancelled(true);
            Iris.debug("Cancelled cartographer trade because no override items are valid @ " + event.getEntity().getLocation());
            return;
        }

        IrisVillagerTrade trade = override.getValidItems().getRandom();
        event.setRecipe(trade.convert());
        Iris.debug("Overrode cartographer trade with: " + trade + " to prevent allowing cartography map trades");
    }
}
