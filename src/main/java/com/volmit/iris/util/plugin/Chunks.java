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

package com.volmit.iris.util.plugin;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class Chunks {
    public static boolean isSafe(World w, int x, int z) {
        return w.isChunkLoaded(x, z)
                && w.isChunkLoaded(x + 1, z)
                && w.isChunkLoaded(x, z + 1)
                && w.isChunkLoaded(x - 1, z)
                && w.isChunkLoaded(x, z - 1)
                && w.isChunkLoaded(x - 1, z - 1)
                && w.isChunkLoaded(x + 1, z + 1)
                && w.isChunkLoaded(x + 1, z - 1)
                && w.isChunkLoaded(x - 1, z + 1);
    }

    public static boolean isSafe(Location l) {
        return isSafe(l.getWorld(), l.getBlockX() >> 4, l.getBlockZ() >> 4);
    }

    public static boolean hasPlayersNearby(Location at) {
        try {
            return !at.getWorld().getNearbyEntities(at, 32, 32, 32, (i) -> i instanceof Player).isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
