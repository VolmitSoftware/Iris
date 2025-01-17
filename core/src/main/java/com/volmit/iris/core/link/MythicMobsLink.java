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

package com.volmit.iris.core.link;

import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;

public class MythicMobsLink {

    public MythicMobsLink() {

    }

    public boolean isEnabled() {
        return getPlugin() != null;
    }

    public Plugin getPlugin() {
        return Bukkit.getPluginManager().getPlugin("MythicMobs");
    }

    /**
     * Spawn a mythic mob at this location
     *
     * @param mob      The mob
     * @param location The location
     * @return The mob, or null if it can't be spawned
     */
    public @Nullable
    Entity spawnMob(String mob, Location location) {
        return isEnabled() ? MythicBukkit.inst().getMobManager().spawnMob(mob, location).getEntity().getBukkitEntity() : null;
    }

    public Collection<String> getMythicMobTypes() {
        return isEnabled() ? MythicBukkit.inst().getMobManager().getMobNames() : List.of();
    }
}
