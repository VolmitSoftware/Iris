/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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

import com.volmit.iris.Iris;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.util.data.Cuboid;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;

public class WorldEditLink {
    private static final AtomicCache<Boolean> active = new AtomicCache<>();

    public static Cuboid getSelection(Player p) {
        if (!hasWorldEdit())
            return null;

        try {
            Object instance = Class.forName("com.sk89q.worldedit.WorldEdit").getDeclaredMethod("getInstance").invoke(null);
            Object sessionManager = instance.getClass().getDeclaredMethod("getSessionManager").invoke(instance);
            Class<?> bukkitAdapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Object world = bukkitAdapter.getDeclaredMethod("adapt", World.class).invoke(null, p.getWorld());
            Object player = bukkitAdapter.getDeclaredMethod("adapt", Player.class).invoke(null, p);
            Object localSession = sessionManager.getClass().getDeclaredMethod("getIfPresent", Class.forName("com.sk89q.worldedit.session.SessionOwner")).invoke(sessionManager, player);
            if (localSession == null) return null;

            Object region = null;
            try {
                region = localSession.getClass().getDeclaredMethod("getSelection", Class.forName("com.sk89q.worldedit.world.World")).invoke(localSession, world);
            } catch (InvocationTargetException ignored) {
            }
            if (region == null) return null;

            Object min = region.getClass().getDeclaredMethod("getMinimumPoint").invoke(region);
            Object max = region.getClass().getDeclaredMethod("getMaximumPoint").invoke(region);
            return new Cuboid(p.getWorld(),
                    (int) min.getClass().getDeclaredMethod("x").invoke(min),
                    (int) min.getClass().getDeclaredMethod("y").invoke(min),
                    (int) min.getClass().getDeclaredMethod("z").invoke(min),
                    (int) min.getClass().getDeclaredMethod("x").invoke(max),
                    (int) min.getClass().getDeclaredMethod("y").invoke(max),
                    (int) min.getClass().getDeclaredMethod("z").invoke(max)
            );
        } catch (Throwable e) {
            Iris.error("Could not get selection");
            e.printStackTrace();
            active.reset();
            active.aquire(() -> false);
        }
        return null;
    }

    public static boolean hasWorldEdit() {
        return active.aquire(() -> Bukkit.getPluginManager().isPluginEnabled("WorldEdit"));
    }
}
