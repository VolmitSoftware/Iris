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

package com.volmit.iris.core.service;

import com.volmit.iris.Iris;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.object.IrisDimension;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.plugin.IrisService;
import com.volmit.iris.util.plugin.VolmitSender;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.VillagerCareerChangeEvent;

import java.util.List;

public class VillageSVC implements IrisService {
    @Override
    public void onEnable() {

    }

    @Override
    public void onDisable() {

    }

    @EventHandler
    public void on(VillagerCareerChangeEvent event) {

        if (!IrisToolbelt.isIrisWorld(event.getEntity().getWorld())) {
            return;
        }

        IrisDimension dim = IrisToolbelt.access(event.getEntity().getWorld())
                .getEngine().getDimension();

        if (!dim.isRemoveCartographersDueToCrash()) {
            return;
        }

        if (event.getProfession().equals(Villager.Profession.CARTOGRAPHER)) {
            event.setCancelled(true);

            Location eventLocation = event.getEntity().getLocation();

            int radius = dim.getNotifyPlayersOfCartographerCancelledRadius();

            if (radius == -1) {
                return;
            }

            List<Player> playersInWorld = event.getEntity().getWorld().getPlayers();

            String message = C.GOLD + "Iris does not allow cartographers in its world due to crashes.";

            Iris.info("Cancelled Cartographer Villager to prevent server crash at " + eventLocation + "!");

            if (radius == -2) {
                playersInWorld.stream().map(VolmitSender::new).forEach(v -> v.sendMessage(message));
            } else {
                playersInWorld.forEach(p -> {
                    if (p.getLocation().distance(eventLocation) < radius) {
                        new VolmitSender(p).sendMessage(message);
                    }
                });
            }

        }
    }

    /*
     * Replace or disable villager trade add event to prevent explorer map
     */
    /* Removed due to MC breaking stuff again. This event is now called after the cartographer maps are made,
    so it can fuck right off.
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
    */
}
