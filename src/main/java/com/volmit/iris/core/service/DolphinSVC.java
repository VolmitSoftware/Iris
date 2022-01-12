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

import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.util.plugin.IrisService;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class DolphinSVC implements IrisService {
    @Override
    public void onEnable() {

    }

    @Override
    public void onDisable() {

    }

    /**
     * Prevents dolphins from being fed, to locate a treasure map.
     * Note: This results in odd dolphin behaviour, but it's the best we can do.
     */
    @EventHandler
    public void on(PlayerInteractEntityEvent event) {
        if(!IrisToolbelt.isIrisWorld(event.getPlayer().getWorld())) {
            return;
        }

        Material hand = event.getPlayer().getInventory().getItem(event.getHand()).getType();
        if(event.getRightClicked().getType().equals(EntityType.DOLPHIN) && (hand.equals(Material.TROPICAL_FISH) || hand.equals(Material.PUFFERFISH) || hand.equals(Material.COD) || hand.equals(Material.SALMON))) {
            event.setCancelled(true);
        }
    }
}
