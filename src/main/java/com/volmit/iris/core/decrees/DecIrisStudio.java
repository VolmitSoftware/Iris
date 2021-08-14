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

package com.volmit.iris.core.decrees;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.gui.NoiseExplorerGUI;
import com.volmit.iris.core.project.loader.IrisData;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.basic.IrisPosition;
import com.volmit.iris.engine.object.biome.IrisBiome;
import com.volmit.iris.engine.object.common.IrisScript;
import com.volmit.iris.engine.object.dimensional.IrisDimension;
import com.volmit.iris.engine.object.jigsaw.IrisJigsawStructure;
import com.volmit.iris.engine.object.noise.IrisGenerator;
import com.volmit.iris.engine.object.regional.IrisRegion;
import com.volmit.iris.util.decree.DecreeExecutor;
import com.volmit.iris.util.decree.DecreeOrigin;
import com.volmit.iris.util.decree.annotations.Decree;
import com.volmit.iris.util.decree.annotations.Param;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.function.Function2;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.json.JSONCleaner;
import com.volmit.iris.util.json.JSONObject;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.plugin.VolmitSender;
import com.volmit.iris.util.scheduling.J;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.function.Supplier;

@Decree(name = "studio", aliases = {"std", "s"}, description = "Studio Commands", studio = true)
public class DecIrisStudio implements DecreeExecutor
{
    @Decree(description = "Open a new studio world", aliases = "o", sync = true)
    public void open(
            @Param(name = "dimension", defaultValue = "overworld", description = "The dimension to open a studio for", aliases = "dim")
                    IrisDimension dimension,
            @Param(name = "seed", defaultValue = "1337", description = "The seed to generate the studio with", aliases = "s")
                    long seed)
    {
        sender().sendMessage(C.GREEN + "Opening studio for the \"" + dimension.getName() + "\" pack (seed: " + seed + ")");
        Iris.proj.open(sender(), seed, dimension.getLoadKey());
    }

    @Decree(description = "Close an open studio project", aliases = "x", sync = true)
    public void close()
    {
        if (!Iris.proj.isProjectOpen()) {
            sender().sendMessage(C.RED + "No open studio projects.");
            return;
        }

        Iris.proj.close();
        sender().sendMessage(C.GREEN + "Project Closed.");
    }

    @Decree(description = "Get the version of a pack", aliases = {"v", "ver"})
    public void version(
            @Param(name = "dimension", defaultValue = "overworld", description = "The dimension get the version of", aliases = "dim")
                    IrisDimension dimension
    )
    {
        sender().sendMessage(C.GREEN + "The \"" + dimension.getName() + "\" pack has version: " + dimension.getVersion());
    }

    @Decree(description = "Beatify a pack", aliases = {"beauty", "prettify"})
    public void beautify(
            @Param(name = "dimension", defaultValue = "overworld", description = "The to-beautify dimension", aliases = "dim")
                    IrisDimension dimension
    )
    {
        File folder = dimension.getLoadFile();
        sender().sendMessage("Cleaned " + Form.f(JSONCleaner.clean(sender(), folder)) + " JSON Files");
    }

    @Decree(description = "Beatify a pack - must be in studio!", aliases = {"beauty", "prettify"})
    public void beautify()
    {
        File folder = Iris.proj.getActiveProject().getPath();
        sender().sendMessage("Cleaned " + Form.f(JSONCleaner.clean(sender(), folder)) + " JSON Files");
    }

    @Decree(description = "Convert objects in the \"convert\" folder", aliases = "conv")
    public void convert()
    {
        Iris.convert.check(sender());
    }


    @Decree(description = "Edit the biome you're currently in", aliases = {"ebiome", "eb"}, origin = DecreeOrigin.PLAYER)
    public void editbiome()
    {
        if (!Iris.proj.isProjectOpen()){
            sender().sendMessage(C.RED + "The is no studio currently open!");
            return;
        }

        if (!Iris.proj.getActiveProject().getActiveProvider().getEngine().getWorld().realWorld().equals(sender().player().getWorld())){
            sender().sendMessage(C.RED + "You must be in a studio world to edit a biome!");
            return;
        }


        try {
            File f = Iris.proj.getActiveProject().getActiveProvider().getEngine().getBiome(
                    sender().player().getLocation().getBlockX(),
                    sender().player().getLocation().getBlockY(),
                    sender().player().getLocation().getBlockZ()).getLoadFile();
            Desktop.getDesktop().open(f);
        } catch (Throwable e) {
            Iris.reportError(e);
            sender().sendMessage("Cant find the file. Unsure why this happened.");
        }
    }

    @Decree(description = "Execute a script", aliases = {"ex", "exec", "run"}, origin = DecreeOrigin.PLAYER)
    public void execute(
            @Param(name = "script", description = "The script to run", aliases = {"s", "scr"})
            IrisScript script
    )
    {
        IrisToolbelt.access(sender().player().getWorld()).getEngine().getExecution().execute(script);
    }

    @Decree(description = "Open the noise explorer (must have a local server!)", aliases = "nmap")
    public void noise()
    {
        if (!IrisSettings.get().isUseServerLaunchedGuis()){
            sender().sendMessage(C.RED + "To use Iris noise GUIs, please enable serverLaunchedGUIs in the settings");
            return;
        }
        sender().sendMessage(C.GREEN + "Opening Noise Explorer!");
        NoiseExplorerGUI.launch();
    }


    @Decree(description = "Preview created noise generators", aliases = {"generator", "gen"})
    public void explore(
            @Param(name = "generator", description = "The generator to explore", aliases = {"gen", "g"})
                    IrisGenerator generator,
            @Param(name = "seed", description = "The seed to generate with", aliases = "s", defaultValue = "12345")
                    long seed)
    {
        if (!IrisSettings.get().isUseServerLaunchedGuis()){
            sender().sendMessage(C.RED + "To use Iris noise GUIs, please enable serverLaunchedGUIs in the settings");
            return;
        }
        sender().sendMessage(C.GREEN + "Opening Noise Explorer!");

        Supplier<Function2<Double, Double, Double>> l = () -> {

            if (generator == null) {
                return (x, z) -> 0D;
            }

            return (x, z) -> generator.getHeight(x, z, new RNG(seed).nextParallelRNG(3245).lmax());
        };
    }

    @Decree(description = "Find any biome", aliases = {"goto", "g"}, origin = DecreeOrigin.PLAYER)
    public void find(
            @Param(name = "biome", description = "The biome to find", aliases = "b")
            IrisBiome biome
    )
    {
        J.a(() -> {
            IrisPosition l = engine().lookForBiome(biome, 10000, (v) -> sender().sendMessage("Looking for " + C.BOLD + C.WHITE + biome.getName() + C.RESET + C.GRAY + ": Checked " + Form.f(v) + " Places"));

            if (l == null) {
                sender().sendMessage("Couldn't find " + biome.getName() + ".");
            } else {
                sender().sendMessage("Found " + biome.getName() + "!");
                J.s(() -> sender().player().teleport(l.toLocation(world())));
            }
        });
    }

    @Decree(description = "Find any region", aliases = {"goto", "g"}, origin = DecreeOrigin.PLAYER)
    public void find(
            @Param(name = "region", description = "The region to find", aliases = "r")
                    IrisRegion region
    )
    {
        J.a(() -> {
            IrisPosition l = engine().lookForRegion(region, 10000, (v) -> sender().sendMessage("Looking for " + C.BOLD + C.WHITE + region.getName() + C.RESET + C.GRAY + ": Checked " + Form.f(v) + " Places"));

            if (l == null) {
                sender().sendMessage("Couldn't find " + region.getName() + ".");
            } else {
                sender().sendMessage("Found " + region.getName() + "!");
                J.s(() -> sender().player().teleport(l.toLocation(world())));
            }
        });
    }
}
