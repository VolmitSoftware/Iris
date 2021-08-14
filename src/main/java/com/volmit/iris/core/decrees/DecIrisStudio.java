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
import com.volmit.iris.core.gui.VisionGUI;
import com.volmit.iris.engine.object.basic.IrisPosition;
import com.volmit.iris.engine.object.biome.IrisBiome;
import com.volmit.iris.engine.object.common.IrisScript;
import com.volmit.iris.engine.object.dimensional.IrisDimension;
import com.volmit.iris.engine.object.loot.IrisLootTable;
import com.volmit.iris.engine.object.noise.IrisGenerator;
import com.volmit.iris.engine.object.regional.IrisRegion;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.decree.DecreeExecutor;
import com.volmit.iris.util.decree.DecreeOrigin;
import com.volmit.iris.util.decree.annotations.Decree;
import com.volmit.iris.util.decree.annotations.Param;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.function.Function2;
import com.volmit.iris.util.json.JSONCleaner;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;

import java.awt.*;
import java.io.File;
import java.util.function.Supplier;

@Decree(name = "studio", aliases = {"std", "s"}, description = "Studio Commands", studio = true)
public class DecIrisStudio implements DecreeExecutor {
    @Decree(description = "Open a new studio world", aliases = "o", sync = true)
    public void open(
            @Param(name = "dimension", defaultValue = "overworld", description = "The dimension to open a studio for", aliases = "dim")
                    IrisDimension dimension,
            @Param(name = "seed", defaultValue = "1337", description = "The seed to generate the studio with", aliases = "s")
                    long seed) {
        success("Opening studio for the \"" + dimension.getName() + "\" pack (seed: " + seed + ")");
        Iris.proj.open(sender(), seed, dimension.getLoadKey());
    }

    @Decree(description = "Close an open studio project", aliases = "x", sync = true)
    public void close() {
        if (!Iris.proj.isProjectOpen()) {
            error("No open studio projects.");
            return;
        }

        Iris.proj.close();
        success("Project Closed.");
    }

    @Decree(description = "Get the version of a pack", aliases = {"v", "ver"})
    public void version(
            @Param(name = "dimension", defaultValue = "overworld", description = "The dimension get the version of", aliases = "dim")
                    IrisDimension dimension
    ) {
        success("The \"" + dimension.getName() + "\" pack has version: " + dimension.getVersion());
    }

    @Decree(description = "Beatify a pack", aliases = {"beauty", "prettify"})
    public void beautify(
            @Param(name = "dimension", defaultValue = "overworld", description = "The to-beautify dimension", aliases = "dim")
                    IrisDimension dimension
    ) {
        File folder = dimension.getLoadFile();
        success("Cleaned " + Form.f(JSONCleaner.clean(sender(), folder)) + " JSON Files");
    }

    @Decree(description = "Beatify a pack - must be in studio!", aliases = {"beauty", "prettify"})
    public void beautify() {
        if (noStudio()) return;
        File folder = Iris.proj.getActiveProject().getPath();
        success("Cleaned " + Form.f(JSONCleaner.clean(sender(), folder)) + " JSON Files");
    }

    @Decree(description = "Convert objects in the \"convert\" folder", aliases = "conv")
    public void convert() {
        Iris.convert.check(sender());
    }


    @Decree(description = "Edit the biome you're currently in", aliases = {"ebiome", "eb"}, origin = DecreeOrigin.PLAYER)
    public void editbiome() {

        if (noStudio()) return;

        try {
            File f = engine().getBiome(
                    player().getLocation().getBlockX(),
                    player().getLocation().getBlockY(),
                    player().getLocation().getBlockZ()).getLoadFile();
            Desktop.getDesktop().open(f);
        } catch (Throwable e) {
            Iris.reportError(e);
            error("Cant find the file. Unsure why this happened.");
        }
    }

    @Decree(description = "Execute a script", aliases = {"ex", "exec", "run"}, origin = DecreeOrigin.PLAYER)
    public void execute(
            @Param(name = "script", description = "The script to run", aliases = {"s", "scr"})
                    IrisScript script
    ) {
        engine().getExecution().execute(script);
    }

    @Decree(description = "Open the noise explorer (External GUI)", aliases = "nmap")
    public void noise() {
        if (noGUI()) return;
        success("Opening Noise Explorer!");
        NoiseExplorerGUI.launch();
    }


    @Decree(description = "Preview noise gens (External GUI)", aliases = {"generator", "gen"})
    public void explore(
            @Param(name = "generator", description = "The generator to explore", aliases = {"gen", "g"})
                    IrisGenerator generator,
            @Param(name = "seed", description = "The seed to generate with", aliases = "s", defaultValue = "12345")
                    long seed) {
        if (noGUI()) return;
        success("Opening Noise Explorer!");

        Supplier<Function2<Double, Double, Double>> l = () -> {

            if (generator == null) {
                return (x, z) -> 0D;
            }

            return (x, z) -> generator.getHeight(x, z, new RNG(seed).nextParallelRNG(3245).lmax());
        };
        NoiseExplorerGUI.launch(l, "Custom Generator");
    }

    @Decree(description = "Find any biome", aliases = {"goto", "g"}, origin = DecreeOrigin.PLAYER)
    public void find(
            @Param(name = "biome", description = "The biome to find", aliases = "b")
                    IrisBiome biome
    ) {
        J.a(() -> {
            IrisPosition l = engine().lookForBiome(biome, 10000, (v) -> message("Looking for " + C.BOLD + C.WHITE + biome.getName() + C.RESET + C.GRAY + ": Checked " + Form.f(v) + " Places"));

            if (l == null) {
                error("Couldn't find " + biome.getName() + ".");
            } else {
                success("Found " + biome.getName() + "!");
                J.s(() -> player().teleport(l.toLocation(world())));
            }
        });
    }

    @Decree(description = "Find any region", aliases = {"goto", "g"}, origin = DecreeOrigin.PLAYER)
    public void find(
            @Param(name = "region", description = "The region to find", aliases = "r")
                    IrisRegion region
    ) {
        J.a(() -> {
            IrisPosition l = engine().lookForRegion(region, 10000, (v) -> message("Looking for " + C.BOLD + C.WHITE + region.getName() + C.RESET + C.GRAY + ": Checked " + Form.f(v) + " Places"));

            if (l == null) {
                error("Couldn't find " + region.getName() + ".");
            } else {
                success("Found " + region.getName() + "!");
                J.s(() -> player().teleport(l.toLocation(world())));
            }
        });
    }

    @Decree(description = "Hotload a studio", aliases = {"hot", "h", "reload"}, origin = DecreeOrigin.PLAYER)
    public void hotload() {
        if (noStudio()) return;

        access().hotload();
    }

    @Decree(description = "Show loot if a chest were right here", origin = DecreeOrigin.PLAYER)
    public void loot()
    {
        if (noStudio()) return;

        KList<IrisLootTable> tables = engine().getLootTables(RNG.r, player().getLocation().getBlock());
        Inventory inv = Bukkit.createInventory(null, 27 * 2);
    }

    @Decree(description = "Render a world map (External GUI)", aliases = "render")
    public void map()
    {
        if (noStudio()) return;

        if (noGUI()) return;

        VisionGUI.launch(engine(), 0);
        success("Opening map!");
    }

    @Decree(description = "Package a dimension into a compressed format", aliases = "package")
    public void pkg(
            @Param(name = "dimension", aliases = {"d", "dim"}, description = "The dimension pack to compress")
            IrisDimension dimension,
            @Param(name = "obfuscate", aliases = "o", description = "Whether or not to obfuscate the pack", defaultValue = "false")
            boolean obfuscate,
            @Param(name = "minify", aliases = "m", description = "Whether or not to minify the pack", defaultValue = "true")
            boolean minify
    ){
        J.a(() -> {
            Iris.proj.compilePackage(sender(), dimension, obfuscate, minify);
        });
    }

    /**
     * @return true if server GUIs are not enabled
     */
    private boolean noGUI() {
        if (!IrisSettings.get().isUseServerLaunchedGuis()){
            error("You must have server launched GUIs enabled in the settings!");
            return true;
        }
        return false;
    }

    /**
     * @return true if no studio is open & the player
     */
    private boolean noStudio(){
        if (!sender().isPlayer()){
            error("Players only (this is a config error. Ask support to add DecreeOrigin.PLAYER)");
            return true;
        }
        if (!Iris.proj.isProjectOpen()){
            error("No studio world is open!");
            return true;
        }
        if (!engine().isStudio()){
            error("You must be in a studio world!");
            return true;
        }
        return false;
    }
}
