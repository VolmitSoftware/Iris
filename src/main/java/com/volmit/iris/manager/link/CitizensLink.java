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

package com.volmit.iris.manager.link;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public class CitizensLink {
    public CitizensLink() {

    }

    public boolean supported() {
        return getCitizens() != null;
    }

    // public Entity spawn(EntityType type, String npcType, Location a)
    // {
    // if(!supported())
    // {
    // return null;
    // }
    //
    // NPC npc = CitizensAPI.getNPCRegistry().createNPC(type, "");
    // npc.spawn(a);
    // return npc.getEntity();
    // }

    public Plugin getCitizens() {
        Plugin p = Bukkit.getPluginManager().getPlugin("Citizens");

        return p;
    }
}
