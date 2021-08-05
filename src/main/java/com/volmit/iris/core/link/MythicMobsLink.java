package com.volmit.iris.core.link;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.BiFunction;

public class MythicMobsLink {

    private Collection<String> mobs;
    private BiFunction<String, Location, Entity> spawnMobFunction;

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
     * @param mob The mob
     * @param location The location
     * @return The mob, or null if it can't be spawned
     */
    public @Nullable Entity spawnMob(String mob, Location location) {
        if (!isEnabled()) return null;

        if (spawnMobFunction != null) {
            return spawnMobFunction.apply(mob, location);
        }

        try {
            Class<?> mythicMobClass = Class.forName("io.lumine.xikage.mythicmobs.MythicMobs");
            Method getInst = mythicMobClass.getDeclaredMethod("inst");
            Object inst = getInst.invoke(null);
            Method getAPIHelper = mythicMobClass.getDeclaredMethod("getAPIHelper");
            Object apiHelper = getAPIHelper.invoke(inst);
            Method spawnMobMethod = apiHelper.getClass().getDeclaredMethod("spawnMythicMob", String.class, Location.class);

            spawnMobFunction = (str, loc) -> {
                try {
                    return (Entity) spawnMobMethod.invoke(apiHelper, str, loc);
                } catch (InvocationTargetException | IllegalAccessException e) {
                    e.printStackTrace();
                }
                return null;
            };

            return spawnMobFunction.apply(mob, location);
        } catch (Exception e) {
            e.printStackTrace();

        }
        return null;
    }

    public Collection<String> getMythicMobTypes() {
        if (mobs != null) {
            return mobs;
        }

        if(isEnabled()) {

            try {
                Class<?> mythicMobClass = Class.forName("io.lumine.xikage.mythicmobs.MythicMobs");
                Method getInst = mythicMobClass.getDeclaredMethod("inst");
                Object inst = getInst.invoke(null);
                Method getMobManager = mythicMobClass.getDeclaredMethod("getMobManager");
                Object mobManager = getMobManager.invoke(inst);
                Method getMobNames = mobManager.getClass().getDeclaredMethod("getMobNames");
                mobs = (Collection<String>) getMobNames.invoke(mobManager);
                return mobs;
            } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        return new ArrayList<>();
    }
}
