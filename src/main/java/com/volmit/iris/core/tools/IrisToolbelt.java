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

package com.volmit.iris.core.tools;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisDataManager;
import com.volmit.iris.core.gui.PregeneratorJob;
import com.volmit.iris.core.pregenerator.PregenTask;
import com.volmit.iris.core.pregenerator.PregeneratorMethod;
import com.volmit.iris.core.pregenerator.methods.HeadlessPregenMethod;
import com.volmit.iris.core.pregenerator.methods.HybridPregenMethod;
import com.volmit.iris.engine.framework.IrisAccess;
import com.volmit.iris.engine.object.IrisDimension;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.plugin.VolmitSender;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.File;

/**
 * Something you really want to wear if working on Iris. Shit gets pretty hectic down there.
 * Hope you packed snacks & road sodas.
 */
public class IrisToolbelt {
    /**
     * Will find / download / search for the dimension or return null
     *
     * - You can provide a dimenson in the packs folder by the folder name
     * - You can provide a github repo by using (assumes branch is master unless specified)
     *   - GithubUsername/repository
     *   - GithubUsername/repository/branch
     *
     * @param dimension the dimension id such as overworld or flat
     * @return the IrisDimension or null
     */
    public static IrisDimension getDimension(String dimension)
    {
        File pack = Iris.instance.getDataFolder("packs", dimension);

        if(!pack.exists())
        {
            Iris.proj.downloadSearch(new VolmitSender(Bukkit.getConsoleSender(), Iris.instance.getTag()), dimension, false, false);
        }

        if(!pack.exists())
        {
            return null;
        }

       return new IrisDataManager(pack).getDimensionLoader().load(dimension);
    }

    /**
     * Create a world with plenty of options
     * @return the creator builder
     */
    public static IrisCreator createWorld()
    {
        return new IrisCreator();
    }

    /**
     * Checks if the given world is an Iris World (same as access(world) != null)
     * @param world the world
     * @return true if it is an Iris Access world
     */
    public static boolean isIrisWorld(World world)
    {
        return access(world) != null;
    }

    /**
     * Get the Iris generator for the given world
     * @param world the given world
     * @return the IrisAccess or null if it's not an Iris World
     */
    public static IrisAccess access(World world)
    {
        return IrisWorlds.access(world);
    }

    /**
     * Start a pregenerator task
     * @param task the scheduled task
     * @param method the method to execute the task
     * @return the pregenerator job (already started)
     */
    public static PregeneratorJob pregenerate(PregenTask task, PregeneratorMethod method)
    {
        return new PregeneratorJob(task, method);
    }

    /**
     * Start a pregenerator task. If the supplied generator is headless, headless mode is used,
     * otherwise Hybrid mode is used.
     * @param task the scheduled task
     * @param access the Iris Generator
     * @return the pregenerator job (already started)
     */
    public static PregeneratorJob pregenerate(PregenTask task, IrisAccess access)
    {
        if(access.isHeadless())
        {
            return pregenerate(task, new HeadlessPregenMethod(access.getHeadlessGenerator().getWorld(), access.getHeadlessGenerator()));
        }

        return pregenerate(task, new HybridPregenMethod(access.getCompound().getWorld().realWorld(), Runtime.getRuntime().availableProcessors()));
    }

    /**
     * Evacuate all players from the world into literally any other world.
     * If there are no other worlds, kick them! Not the best but what's mine is mine sometimes...
     * @param world the world to evac
     */
    public static void evacuate(World world)
    {
        IrisWorlds.evacuate(world);
    }

    public static void test()
    {
        // Get IrisDataManager from a world
        IrisToolbelt.access(anyWorld).getCompound().getData();

        // Get Default Engine from world
        IrisToolbelt.access(anyWorld).getCompound().getDefaultEngine();

        // Get the engine at the given height
        IrisToolbelt.access(anyWorld).getCompound().getEngineForHeight(68);

        // IS THIS THING ON?
        boolean yes = IrisToolbelt.isIrisWorld(world);

        // GTFO for worlds (moves players to any other world, just not this one)
        IrisToolbelt.evacuate(world);

        IrisAccess access = IrisToolbelt.createWorld() // If you like builders...
            .name("myWorld") // The world name
            .dimension("terrifyinghands")
            .seed(69133742) // The world seed
            .headless(true)  // Headless make gen go fast
            .pregen(PregenTask // Define a pregen job to run
                .builder()
                    .center(new Position2(0,0)) // REGION coords (1 region = 32x32 chunks)
                    .radius(4)  // Radius in REGIONS. Rad of 4 means a 9x9 Region map.
                .build())
            .create();
    }
}
