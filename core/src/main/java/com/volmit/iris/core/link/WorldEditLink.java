package com.volmit.iris.core.link;

import com.volmit.iris.util.data.Cuboid;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class WorldEditLink {
    public static Cuboid getSelection(Player p) {
        try {
            Object instance = Class.forName("com.sk89q.worldedit.WorldEdit").getDeclaredMethod("getInstance").invoke(null);
            Object sessionManager = instance.getClass().getDeclaredMethod("getSessionManager").invoke(instance);
            Object player = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter").getDeclaredMethod("adapt", Player.class).invoke(null, p);
            Object localSession = sessionManager.getClass().getDeclaredMethod("getIfPresent", Class.forName("com.sk89q.worldedit.session.SessionOwner")).invoke(sessionManager, player);
            Object world = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter").getDeclaredMethod("adapt", World.class).invoke(null, p.getWorld());
            Object region = localSession.getClass().getDeclaredMethod("getSelection", world.getClass()).invoke(localSession, world);
            Object min = region.getClass().getDeclaredMethod("getMinimumPoint").invoke(region);
            Object max = region.getClass().getDeclaredMethod("getMaximumPoint").invoke(region);
            return new Cuboid(p.getWorld(),
                    (int) min.getClass().getDeclaredMethod("getX").invoke(min),
                    (int) min.getClass().getDeclaredMethod("getY").invoke(min),
                    (int) min.getClass().getDeclaredMethod("getZ").invoke(min),
                    (int) min.getClass().getDeclaredMethod("getX").invoke(max),
                    (int) min.getClass().getDeclaredMethod("getY").invoke(max),
                    (int) min.getClass().getDeclaredMethod("getZ").invoke(max)
            );
        } catch (Throwable ignored) {

        }
        return null;
    }
}
