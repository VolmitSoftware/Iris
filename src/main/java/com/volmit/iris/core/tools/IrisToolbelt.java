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

package com.volmit.iris.core.tools;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.gui.PregeneratorJob;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.core.pregenerator.PregenTask;
import com.volmit.iris.core.pregenerator.PregeneratorMethod;
import com.volmit.iris.core.pregenerator.methods.HybridPregenMethod;
import com.volmit.iris.core.service.StudioSVC;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisDimension;
import com.volmit.iris.engine.platform.PlatformChunkGenerator;
import com.volmit.iris.util.plugin.VolmitSender;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Something you really want to wear if working on Iris. Shit gets pretty hectic down there.
 * Hope you packed snacks & road sodas.
 */
public class IrisToolbelt {
    public static Map<String, Boolean> toolbeltConfiguration = new HashMap<>();

    /**
     * Will find / download / search for the dimension or return null
     * <p>
     * - You can provide a dimenson in the packs folder by the folder name
     * - You can provide a github repo by using (assumes branch is master unless specified)
     * - GithubUsername/repository
     * - GithubUsername/repository/branch
     *
     * @param dimension the dimension id such as overworld or flat
     * @return the IrisDimension or null
     */
    public static IrisDimension getDimension(String dimension) {
        File pack = Iris.instance.getDataFolder("packs", dimension);

        if (!pack.exists()) {
            Iris.service(StudioSVC.class).downloadSearch(new VolmitSender(Bukkit.getConsoleSender(), Iris.instance.getTag()), dimension, false, false);
        }

        if (!pack.exists()) {
            return null;
        }

        return IrisData.get(pack).getDimensionLoader().load(dimension);
    }

    /**
     * Create a world with plenty of options
     *
     * @return the creator builder
     */
    public static IrisCreator createWorld() {
        return new IrisCreator();
    }

    /**
     * Checks if the given world is an Iris World (same as access(world) != null)
     *
     * @param world the world
     * @return true if it is an Iris Access world
     */
    public static boolean isIrisWorld(World world) {
        if (world == null) {
            return false;
        }

        if (world.getGenerator() instanceof PlatformChunkGenerator f) {
            f.touch(world);
            return true;
        }

        return false;
    }

    public static boolean isIrisStudioWorld(World world) {
        return isIrisWorld(world) && access(world).isStudio();
    }

    /**
     * Get the Iris generator for the given world
     *
     * @param world the given world
     * @return the IrisAccess or null if it's not an Iris World
     */
    public static PlatformChunkGenerator access(World world) {
        if (isIrisWorld(world)) {
            return ((PlatformChunkGenerator) world.getGenerator());
        } /*else {
            Iris.warn("""
                    "---------- No World? ---------------
                    ⠀⣞⢽⢪⢣⢣⢣⢫⡺⡵⣝⡮⣗⢷⢽⢽⢽⣮⡷⡽⣜⣜⢮⢺⣜⢷⢽⢝⡽⣝
                    ⠸⡸⠜⠕⠕⠁⢁⢇⢏⢽⢺⣪⡳⡝⣎⣏⢯⢞⡿⣟⣷⣳⢯⡷⣽⢽⢯⣳⣫⠇
                    ⠀⠀⢀⢀⢄⢬⢪⡪⡎⣆⡈⠚⠜⠕⠇⠗⠝⢕⢯⢫⣞⣯⣿⣻⡽⣏⢗⣗⠏⠀
                    ⠀⠪⡪⡪⣪⢪⢺⢸⢢⢓⢆⢤⢀⠀⠀⠀⠀⠈⢊⢞⡾⣿⡯⣏⢮⠷⠁⠀⠀
                    ⠀⠀⠀⠈⠊⠆⡃⠕⢕⢇⢇⢇⢇⢇⢏⢎⢎⢆⢄⠀⢑⣽⣿⢝⠲⠉⠀⠀⠀⠀
                    ⠀⠀⠀⠀⠀⡿⠂⠠⠀⡇⢇⠕⢈⣀⠀⠁⠡⠣⡣⡫⣂⣿⠯⢪⠰⠂⠀⠀⠀⠀
                    ⠀⠀⠀⠀⡦⡙⡂⢀⢤⢣⠣⡈⣾⡃⠠⠄⠀⡄⢱⣌⣶⢏⢊⠂⠀⠀⠀⠀⠀⠀
                    ⠀⠀⠀⠀⢝⡲⣜⡮⡏⢎⢌⢂⠙⠢⠐⢀⢘⢵⣽⣿⡿⠁⠁⠀⠀⠀⠀⠀⠀⠀
                    ⠀⠀⠀⠀⠨⣺⡺⡕⡕⡱⡑⡆⡕⡅⡕⡜⡼⢽⡻⠏⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
                    ⠀⠀⠀⠀⣼⣳⣫⣾⣵⣗⡵⡱⡡⢣⢑⢕⢜⢕⡝⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
                    ⠀⠀⠀⣴⣿⣾⣿⣿⣿⡿⡽⡑⢌⠪⡢⡣⣣⡟⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
                    ⠀⠀⠀⡟⡾⣿⢿⢿⢵⣽⣾⣼⣘⢸⢸⣞⡟⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
                    ⠀⠀⠀⠀⠁⠇⠡⠩⡫⢿⣝⡻⡮⣒⢽⠋⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
                    """);
        }*/
        return null;
    }

    /**
     * Start a pregenerator task
     *
     * @param task   the scheduled task
     * @param method the method to execute the task
     * @return the pregenerator job (already started)
     */
    public static PregeneratorJob pregenerate(PregenTask task, PregeneratorMethod method, Engine engine) {
        return new PregeneratorJob(task, method, engine);
    }

    /**
     * Start a pregenerator task. If the supplied generator is headless, headless mode is used,
     * otherwise Hybrid mode is used.
     *
     * @param task the scheduled task
     * @param gen  the Iris Generator
     * @return the pregenerator job (already started)
     */
    public static PregeneratorJob pregenerate(PregenTask task, PlatformChunkGenerator gen) {
        return pregenerate(task, new HybridPregenMethod(gen.getEngine().getWorld().realWorld(),
                IrisSettings.getThreadCount(IrisSettings.get().getConcurrency().getParallelism())), gen.getEngine());
    }

    /**
     * Start a pregenerator task. If the supplied generator is headless, headless mode is used,
     * otherwise Hybrid mode is used.
     *
     * @param task  the scheduled task
     * @param world the World
     * @return the pregenerator job (already started)
     */
    public static PregeneratorJob pregenerate(PregenTask task, World world) {
        if (isIrisWorld(world)) {
            return pregenerate(task, access(world));
        }

        return pregenerate(task, new HybridPregenMethod(world, IrisSettings.getThreadCount(IrisSettings.get().getConcurrency().getParallelism())), null);
    }

    /**
     * Evacuate all players from the world into literally any other world.
     * If there are no other worlds, kick them! Not the best but what's mine is mine sometimes...
     *
     * @param world the world to evac
     */
    public static boolean evacuate(World world) {
        for (World i : Bukkit.getWorlds()) {
            if (!i.getName().equals(world.getName())) {
                for (Player j : world.getPlayers()) {
                    new VolmitSender(j, Iris.instance.getTag()).sendMessage("You have been evacuated from this world.");
                    j.teleport(i.getSpawnLocation());
                }

                return true;
            }
        }

        return false;
    }

    /**
     * Evacuate all players from the world
     *
     * @param world the world to leave
     * @param m     the message
     * @return true if it was evacuated.
     */
    public static boolean evacuate(World world, String m) {
        for (World i : Bukkit.getWorlds()) {
            if (!i.getName().equals(world.getName())) {
                for (Player j : world.getPlayers()) {
                    new VolmitSender(j, Iris.instance.getTag()).sendMessage("You have been evacuated from this world. " + m);
                    j.teleport(i.getSpawnLocation());
                }
                return true;
            }
        }

        return false;
    }

    public static boolean isStudio(World i) {
        return isIrisWorld(i) && access(i).isStudio();
    }

    public static void retainMantleDataForSlice(String className) {
        toolbeltConfiguration.put("retain.mantle." + className, true);
    }

    public static <T> T getMantleData(World world, int x, int y, int z, Class<T> of) {
        PlatformChunkGenerator e = access(world);
        if (e == null) {
            return null;
        }
        return e.getEngine().getMantle().getMantle().get(x, y - world.getMinHeight(), z, of);
    }

    public static <T> void deleteMantleData(World world, int x, int y, int z, Class<T> of) {
        PlatformChunkGenerator e = access(world);
        if (e == null) {
            return;
        }
        e.getEngine().getMantle().getMantle().remove(x, y - world.getMinHeight(), z, of);
    }

    public static boolean removeWorld(World world) throws IOException {
        return IrisCreator.removeFromBukkitYml(world.getName());
    }
}
