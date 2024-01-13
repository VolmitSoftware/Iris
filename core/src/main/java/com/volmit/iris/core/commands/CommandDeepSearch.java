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

package com.volmit.iris.core.commands;

import com.volmit.iris.Iris;
import com.volmit.iris.core.pregenerator.DeepSearchPregenerator;
import com.volmit.iris.core.pregenerator.PregenTask;
import com.volmit.iris.core.pregenerator.TurboPregenerator;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.util.data.Dimension;
import com.volmit.iris.util.decree.DecreeExecutor;
import com.volmit.iris.util.decree.annotations.Decree;
import com.volmit.iris.util.decree.annotations.Param;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.math.Position2;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;

@Decree(name = "DeepSearch", aliases = "search", description = "Pregenerate your Iris worlds!")
public class CommandDeepSearch implements DecreeExecutor {
    public String worldName;
    @Decree(description = "DeepSearch a world")
    public void start(
            @Param(description = "The radius of the pregen in blocks", aliases = "size")
            int radius,
            @Param(description = "The world to pregen", contextual = true)
            World world,
            @Param(aliases = "middle", description = "The center location of the pregen. Use \"me\" for your current location", defaultValue = "0,0")
            Vector center
            ) {

        worldName = world.getName();
        File worldDirectory = new File(Bukkit.getWorldContainer(), world.getName());
        File TurboFile = new File(worldDirectory, "DeepSearch.json");
        if (TurboFile.exists()) {
            if (DeepSearchPregenerator.getInstance() != null) {
                sender().sendMessage(C.BLUE + "DeepSearch is already in progress");
                Iris.info(C.YELLOW + "DeepSearch is already in progress");
                return;
            } else {
                try {
                    TurboFile.delete();
                } catch (Exception e){
                   Iris.error("Failed to delete the old instance file of DeepSearch!");
                   return;
                }
            }
        }

        try {
            if (sender().isPlayer() && access() == null) {
                sender().sendMessage(C.RED + "The engine access for this world is null!");
                sender().sendMessage(C.RED + "Please make sure the world is loaded & the engine is initialized. Generate a new chunk, for example.");
            }

            DeepSearchPregenerator.DeepSearchJob DeepSearchJob = DeepSearchPregenerator.DeepSearchJob.builder()
                    .world(worldName)
                    .radiusBlocks(radius)
                    .position(0)
                    .build();

            File SearchGenFile = new File(worldDirectory, "DeepSearch.json");
            DeepSearchPregenerator pregenerator = new DeepSearchPregenerator(DeepSearchJob, SearchGenFile);
            pregenerator.start();

            String msg = C.GREEN + "DeepSearch started in " + C.GOLD + worldName + C.GREEN + " of " + C.GOLD + (radius * 2) + C.GREEN + " by " + C.GOLD + (radius * 2) + C.GREEN + " blocks from " + C.GOLD + center.getX() + "," + center.getZ();
            sender().sendMessage(msg);
            Iris.info(msg);
        } catch (Throwable e) {
            sender().sendMessage(C.RED + "Epic fail. See console.");
            Iris.reportError(e);
            e.printStackTrace();
        }
    }

    @Decree(description = "Stop the active DeepSearch task", aliases = "x")
    public void stop(@Param(aliases = "world", description = "The world to pause") World world) throws IOException {
        DeepSearchPregenerator DeepSearchInstance = DeepSearchPregenerator.getInstance();
        File worldDirectory = new File(Bukkit.getWorldContainer(), world.getName());
        File turboFile = new File(worldDirectory, "DeepSearch.json");

        if (DeepSearchInstance != null) {
            DeepSearchInstance.shutdownInstance(world);
            sender().sendMessage(C.LIGHT_PURPLE + "Closed Turbogen instance for " + world.getName());
        } else if (turboFile.exists() && turboFile.delete()) {
            sender().sendMessage(C.LIGHT_PURPLE + "Closed Turbogen instance for " + world.getName());
        } else if (turboFile.exists()) {
            Iris.error("Failed to delete the old instance file of Turbo Pregen!");
        } else {
            sender().sendMessage(C.YELLOW + "No active pregeneration tasks to stop");
        }
    }

    @Decree(description = "Pause / continue the active pregeneration task", aliases = {"t", "resume", "unpause"})
    public void pause(
            @Param(aliases = "world", description = "The world to pause")
            World world
    ) {
        if (TurboPregenerator.getInstance() != null) {
            TurboPregenerator.setPausedTurbo(world);
            sender().sendMessage(C.GREEN + "Paused/unpaused Turbo Pregen, now: " + (TurboPregenerator.isPausedTurbo(world) ? "Paused" : "Running") + ".");
        } else {
            File worldDirectory = new File(Bukkit.getWorldContainer(), world.getName());
            File TurboFile = new File(worldDirectory, "DeepSearch.json");
            if (TurboFile.exists()){
                TurboPregenerator.loadTurboGenerator(world.getName());
                sender().sendMessage(C.YELLOW + "Started DeepSearch back up!");
            } else {
                sender().sendMessage(C.YELLOW + "No active DeepSearch tasks to pause/unpause.");
            }

        }
    }
}
