package com.volmit.iris.core.link;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.util.data.Cuboid;
import com.volmit.iris.util.data.KCache;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.UUID;

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
            } catch (InvocationTargetException ignored) {}
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
            Iris.reportError(e);
            active.reset();
            active.aquire(() -> false);
        }
        return null;
    }

    public static boolean hasWorldEdit() {
        return active.aquire(() -> Bukkit.getPluginManager().isPluginEnabled("WorldEdit"));
    }
}
