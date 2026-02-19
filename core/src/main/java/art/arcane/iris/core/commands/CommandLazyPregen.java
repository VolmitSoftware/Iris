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

package art.arcane.iris.core.commands;

import art.arcane.iris.Iris;
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.gui.PregeneratorJob;
import art.arcane.iris.core.pregenerator.LazyPregenerator;
import art.arcane.iris.core.pregenerator.PregenTask;
import art.arcane.iris.core.tools.IrisToolbelt;
import art.arcane.iris.engine.platform.PlatformChunkGenerator;
import art.arcane.iris.util.common.director.DirectorExecutor;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.iris.util.common.format.C;
import art.arcane.volmlib.util.math.Position2;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;

@Director(name = "lazypregen", aliases = "lazy", description = "Pregenerate your Iris worlds!")
public class CommandLazyPregen implements DirectorExecutor {
    public String worldName;
    @Director(description = "Pregenerate a world")
    public void start(
            @Param(description = "The radius of the pregen in blocks", aliases = "size")
            int radius,
            @Param(description = "The world to pregen", contextual = true)
            World world,
            @Param(aliases = "middle", description = "The center location of the pregen. Use \"me\" for your current location", defaultValue = "0,0")
            Vector center,
            @Param(aliases = "maxcpm", description = "Limit the chunks per minute the pregen will generate", defaultValue = "999999999")
            int cpm,
            @Param(aliases = "silent", description = "Silent generation", defaultValue = "false")
            boolean silent
            ) {

        worldName = world.getName();
        File worldDirectory = new File(Bukkit.getWorldContainer(), world.getName());
        File lazyFile = new File(worldDirectory, "lazygen.json");
        if (lazyFile.exists()) {
            sender().sendMessage(C.BLUE + "Lazy pregen is already in progress");
            Iris.info(C.YELLOW + "Lazy pregen is already in progress");
            return;
        }

        try {
            if (sender().isPlayer() && access() == null) {
                sender().sendMessage(C.RED + "The engine access for this world is null!");
                sender().sendMessage(C.RED + "Please make sure the world is loaded & the engine is initialized. Generate a new chunk, for example.");
            }

            PlatformChunkGenerator platform = IrisToolbelt.access(world);
            if (platform != null) {
                IrisToolbelt.applyPregenPerformanceProfile(platform.getEngine());
            }

            LazyPregenerator.LazyPregenJob pregenJob = LazyPregenerator.LazyPregenJob.builder()
                    .world(worldName)
                    .healingPosition(0)
                    .healing(false)
                    .chunksPerMinute(cpm)
                    .radiusBlocks(radius)
                    .position(0)
                    .silent(silent)
                    .build();

            File lazyGenFile = new File(worldDirectory, "lazygen.json");
            LazyPregenerator pregenerator = new LazyPregenerator(pregenJob, lazyGenFile);
            pregenerator.start();

            String msg = C.GREEN + "LazyPregen started in " + C.GOLD + worldName + C.GREEN + " of " + C.GOLD + (radius * 2) + C.GREEN + " by " + C.GOLD + (radius * 2) + C.GREEN + " blocks from " + C.GOLD + center.getX() + "," + center.getZ();
            sender().sendMessage(msg);
            Iris.info(msg);
        } catch (Throwable e) {
            sender().sendMessage(C.RED + "Epic fail. See console.");
            Iris.reportError(e);
            e.printStackTrace();
        }
    }

    @Director(description = "Stop the active pregeneration task", aliases = "x")
    public void stop(
            @Param(aliases = "world", description = "The world to pause")
            World world
    ) throws IOException {
        if (LazyPregenerator.getInstance() != null) {
            LazyPregenerator.getInstance().shutdownInstance(world);
            sender().sendMessage(C.LIGHT_PURPLE + "Closed lazygen instance for " + world.getName());
        } else {
            sender().sendMessage(C.YELLOW + "No active pregeneration tasks to stop");
        }
    }

    @Director(description = "Pause / continue the active pregeneration task", aliases = {"t", "resume", "unpause"})
    public void pause(
            @Param(aliases = "world", description = "The world to pause")
            World world
    ) {
        if (LazyPregenerator.getInstance() != null) {
            LazyPregenerator.getInstance().setPausedLazy(world);
            sender().sendMessage(C.GREEN + "Paused/unpaused Lazy Pregen, now: " + (LazyPregenerator.getInstance().isPausedLazy(world) ? "Paused" : "Running") + ".");
        } else {
            sender().sendMessage(C.YELLOW + "No active Lazy Pregen tasks to pause/unpause.");

        }
    }
}
