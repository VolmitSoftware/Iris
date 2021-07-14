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

import com.volmit.iris.util.KList;
import io.lumine.xikage.mythicmobs.MythicMobs;
import io.lumine.xikage.mythicmobs.mobs.MythicMob;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public class MythicMobsLink {
    public MythicMobsLink() {

    }

    public boolean supported() {
        return getMythicMobs() != null;
    }

    public Entity spawn(String name, Location a) {
        if (!supported()) {
            return null;
        }

        MythicMobs m = (MythicMobs) getMythicMobs();
        return m.getMobManager().spawnMob(name, a).getEntity().getBukkitEntity();
    }

    public String[] getMythicMobTypes() {
        KList<String> v = new KList<>();

        if (supported()) {
            MythicMobs m = (MythicMobs) getMythicMobs();

            for (MythicMob i : m.getMobManager().getMobTypes()) {
                v.add(i.getInternalName());
            }
        }

        return v.toArray(new String[v.size()]);
    }

    public Plugin getMythicMobs() {
        Plugin p = Bukkit.getPluginManager().getPlugin("MythicMobs");

        return p;
    }
}
