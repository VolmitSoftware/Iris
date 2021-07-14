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

package com.volmit.iris.scaffold;

import com.volmit.iris.Iris;
import com.volmit.iris.scaffold.engine.IrisAccess;
import com.volmit.iris.scaffold.engine.IrisAccessProvider;
import com.volmit.iris.util.KMap;
import com.volmit.iris.util.MortarSender;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class IrisWorlds {
    private static final KMap<String, IrisAccess> provisioned = new KMap<>();

    public static void register(World w, IrisAccess p) {
        provisioned.put(w.getUID().toString(), p);
    }

    public static boolean isIrisWorld(World world) {
        if (world == null) {
            return false;
        }

        if (provisioned.containsKey(world.getUID().toString())) {
            return true;
        }

        return world.getGenerator() instanceof IrisAccess || world.getGenerator() instanceof IrisAccessProvider;
    }

    public static IrisAccess access(World world) {
        if (isIrisWorld(world)) {
            if (provisioned.containsKey(world.getUID().toString())) {
                return provisioned.get(world.getUID().toString());
            }

            return world.getGenerator() instanceof IrisAccessProvider ? (((IrisAccessProvider) world.getGenerator()).getAccess()) : ((IrisAccess) world.getGenerator());
        }

        return null;
    }

    public static boolean evacuate(World world) {
        for (World i : Bukkit.getWorlds()) {
            if (!i.getName().equals(world.getName())) {
                for (Player j : world.getPlayers()) {
                    new MortarSender(j, Iris.instance.getTag()).sendMessage("You have been evacuated from this world.");
                    j.teleport(i.getSpawnLocation());
                }

                return true;
            }
        }

        return false;
    }
}
