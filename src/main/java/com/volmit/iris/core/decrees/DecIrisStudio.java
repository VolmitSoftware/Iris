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
import com.volmit.iris.core.gui.NoiseExplorerGUI;
import com.volmit.iris.core.gui.VisionGUI;
import com.volmit.iris.core.project.IrisProject;
import com.volmit.iris.core.project.loader.IrisData;
import com.volmit.iris.engine.object.basic.IrisPosition;
import com.volmit.iris.engine.object.biome.IrisBiome;
import com.volmit.iris.engine.object.biome.IrisBiomePaletteLayer;
import com.volmit.iris.engine.object.common.IrisScript;
import com.volmit.iris.engine.object.dimensional.IrisDimension;
import com.volmit.iris.engine.object.entity.IrisEntity;
import com.volmit.iris.engine.object.loot.IrisLootTable;
import com.volmit.iris.engine.object.meta.InventorySlotType;
import com.volmit.iris.engine.object.noise.IrisGenerator;
import com.volmit.iris.engine.object.noise.IrisInterpolator;
import com.volmit.iris.engine.object.noise.IrisNoiseGenerator;
import com.volmit.iris.engine.object.noise.NoiseStyle;
import com.volmit.iris.engine.object.regional.IrisRegion;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.decree.DecreeExecutor;
import com.volmit.iris.util.decree.DecreeOrigin;
import com.volmit.iris.util.decree.annotations.Decree;
import com.volmit.iris.util.decree.annotations.Param;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.function.Function2;
import com.volmit.iris.util.function.NoiseProvider;
import com.volmit.iris.util.interpolation.InterpolationMethod;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.json.JSONCleaner;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.noise.CNG;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.O;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.function.Supplier;

@Decree(name = "studio", aliases = {"std", "s"}, description = "Studio Commands", studio = true)
public class DecIrisStudio implements DecreeExecutor, DecreeStudioExtension {
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
        File folder = dimension.getLoadFile().getParentFile().getParentFile();
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
                    long seed
    ){
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
    ){
        IrisPosition l = engine().lookForBiome(biome, 10000, (v) -> message("Looking for " + C.BOLD + C.WHITE + biome.getName() + C.RESET + C.GRAY + ": Checked " + Form.f(v) + " Places"));

        if (l == null) {
            error("Couldn't find " + biome.getName() + ".");
        } else {
            success("Found " + biome.getName() + "!");
            J.s(() -> player().teleport(l.toLocation(world())));
        }
    }

    @Decree(description = "Find any region", aliases = {"goto", "g"}, origin = DecreeOrigin.PLAYER)
    public void find(
            @Param(name = "region", description = "The region to find", aliases = "r")
                    IrisRegion region
    ){
        IrisPosition l = engine().lookForRegion(region, 10000, (v) -> message("Looking for " + C.BOLD + C.WHITE + region.getName() + C.RESET + C.GRAY + ": Checked " + Form.f(v) + " Places"));

        if (l == null) {
            error("Couldn't find " + region.getName() + ".");
        } else {
            success("Found " + region.getName() + "!");
            J.s(() -> player().teleport(l.toLocation(world())));
        }
    }

    @Decree(description = "Hotload a studio", aliases = {"hot", "h", "reload"}, origin = DecreeOrigin.PLAYER)
    public void hotload() {
        if (noStudio()) return;

        access().hotload();
    }

    @Decree(description = "Show loot if a chest were right here", origin = DecreeOrigin.PLAYER)
    public void loot(
            @Param(name = "fast", aliases = "f", description = "Fast insertion of items in virtual inventory (may cause performance drop)", defaultValue = "false")
            boolean fast,
            @Param(name = "add", aliases = "a", description = "Whether or not to append to the inventory currently open (if false, clears opened inventory)", defaultValue = "true")
            boolean add
    ) {
        if (noStudio()) return;

        KList<IrisLootTable> tables = engine().getLootTables(RNG.r, player().getLocation().getBlock());
        Inventory inv = Bukkit.createInventory(null, 27 * 2);

        try {
            engine().addItems(true, inv, RNG.r, tables, InventorySlotType.STORAGE, player().getLocation().getBlockX(), player().getLocation().getBlockY(), player().getLocation().getBlockZ(), 1);
        } catch (Throwable e){
            Iris.reportError(e);
            error("Cannot add items to virtual inventory because of: " + e.getMessage());
            return;
        }


        O<Integer> ta = new O<>();
        ta.set(-1);

        ta.set(Bukkit.getScheduler().scheduleSyncRepeatingTask(Iris.instance, () ->
        {
            if (!player().getOpenInventory().getType().equals(InventoryType.CHEST)) {
                Bukkit.getScheduler().cancelTask(ta.get());
                success("Opened inventory!");
                return;
            }

            if (!add) {
                inv.clear();
            }

            engine().addItems(true, inv, new RNG(RNG.r.imax()), tables, InventorySlotType.STORAGE, player().getLocation().getBlockX(), player().getLocation().getBlockY(), player().getLocation().getBlockZ(), 1);
        }, 0, fast ? 5 : 35));

        success("Opening inventory now!");
        player().openInventory(inv);
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
        Iris.proj.compilePackage(sender(), dimension, obfuscate, minify);
    }

    @Decree(description = "Profiles a dimension's performance", origin = DecreeOrigin.PLAYER)
    public void profile(
            @Param(name = "dimension", aliases = {"d", "dim"}, description = "The dimension to profile")
            IrisDimension dimension
    ){
        File pack = dimension.getLoadFile().getParentFile().getParentFile();
        File report = Iris.instance.getDataFile("profile.txt");
        IrisProject project = new IrisProject(pack);
        IrisData data = new IrisData(pack);

        KList<String> fileText = new KList<>();

        KMap<NoiseStyle, Double> styleTimings = new KMap<>();
        KMap<InterpolationMethod, Double> interpolatorTimings = new KMap<>();
        KMap<String, Double> generatorTimings = new KMap<>();
        KMap<String, Double> biomeTimings = new KMap<>();
        KMap<String, Double> regionTimings = new KMap<>();

        message("Calculating Performance Metrics for Noise generators");

        for (NoiseStyle i : NoiseStyle.values()) {
            CNG c = i.create(new RNG(i.hashCode()));

            for (int j = 0; j < 3000; j++) {
                c.noise(j, j + 1000, j * j);
                c.noise(j, -j);
            }

            PrecisionStopwatch px = PrecisionStopwatch.start();

            for (int j = 0; j < 100000; j++) {
                c.noise(j, j + 1000, j * j);
                c.noise(j, -j);
            }

            styleTimings.put(i, px.getMilliseconds());
        }

        fileText.add("Noise Style Performance Impacts: ");

        for (NoiseStyle i : styleTimings.sortKNumber()) {
            fileText.add(i.name() + ": " + styleTimings.get(i));
        }

        fileText.add("");

        message("Calculating Interpolator Timings...");

        for (InterpolationMethod i : InterpolationMethod.values()) {
            IrisInterpolator in = new IrisInterpolator();
            in.setFunction(i);
            in.setHorizontalScale(8);

            NoiseProvider np = (x, z) -> Math.random();

            for (int j = 0; j < 3000; j++) {
                in.interpolate(j, -j, np);
            }

            PrecisionStopwatch px = PrecisionStopwatch.start();

            for (int j = 0; j < 100000; j++) {
                in.interpolate(j + 10000, -j - 100000, np);
            }

            interpolatorTimings.put(i, px.getMilliseconds());
        }

        fileText.add("Noise Interpolator Performance Impacts: ");

        for (InterpolationMethod i : interpolatorTimings.sortKNumber()) {
            fileText.add(i.name() + ": " + interpolatorTimings.get(i));
        }

        fileText.add("");

        message("Processing Generator Scores: ");

        KMap<String, KList<String>> btx = new KMap<>();

        for (String i : data.getGeneratorLoader().getPossibleKeys()) {
            KList<String> vv = new KList<>();
            IrisGenerator g = data.getGeneratorLoader().load(i);
            KList<IrisNoiseGenerator> composites = g.getAllComposites();
            double score = 0;
            int m = 0;
            for (IrisNoiseGenerator j : composites) {
                m++;
                score += styleTimings.get(j.getStyle().getStyle());
                vv.add("Composite Noise Style " + m + " " + j.getStyle().getStyle().name() + ": " + styleTimings.get(j.getStyle().getStyle()));
            }

            score += interpolatorTimings.get(g.getInterpolator().getFunction());
            vv.add("Interpolator " + g.getInterpolator().getFunction().name() + ": " + interpolatorTimings.get(g.getInterpolator().getFunction()));
            generatorTimings.put(i, score);
            btx.put(i, vv);
        }

        fileText.add("Project Generator Performance Impacts: ");

        for (String i : generatorTimings.sortKNumber()) {
            fileText.add(i + ": " + generatorTimings.get(i));

            btx.get(i).forEach((ii) -> fileText.add("  " + ii));
        }

        fileText.add("");

        KMap<String, KList<String>> bt = new KMap<>();

        for (String i : data.getBiomeLoader().getPossibleKeys()) {
            KList<String> vv = new KList<>();
            IrisBiome b = data.getBiomeLoader().load(i);
            double score = 0;

            int m = 0;
            for (IrisBiomePaletteLayer j : b.getLayers()) {
                m++;
                score += styleTimings.get(j.getStyle().getStyle());
                vv.add("Palette Layer " + m + ": " + styleTimings.get(j.getStyle().getStyle()));
            }

            score += styleTimings.get(b.getBiomeStyle().getStyle());
            vv.add("Biome Style: " + styleTimings.get(b.getBiomeStyle().getStyle()));
            score += styleTimings.get(b.getChildStyle().getStyle());
            vv.add("Child Style: " + styleTimings.get(b.getChildStyle().getStyle()));
            biomeTimings.put(i, score);
            bt.put(i, vv);
        }

        fileText.add("Project Biome Performance Impacts: ");

        for (String i : biomeTimings.sortKNumber()) {
            fileText.add(i + ": " + biomeTimings.get(i));

            bt.get(i).forEach((ff) -> fileText.add("  " + ff));
        }

        fileText.add("");

        for (String i : data.getRegionLoader().getPossibleKeys()) {
            IrisRegion b = data.getRegionLoader().load(i);
            double score = 0;

            score += styleTimings.get(b.getLakeStyle().getStyle());
            score += styleTimings.get(b.getRiverStyle().getStyle());
            regionTimings.put(i, score);
        }

        fileText.add("Project Region Performance Impacts: ");

        for (String i : regionTimings.sortKNumber()) {
            fileText.add(i + ": " + regionTimings.get(i));
        }

        fileText.add("");

        double m = 0;
        for (double i : biomeTimings.v()) {
            m += i;
        }
        m /= biomeTimings.size();
        double mm = 0;
        for (double i : generatorTimings.v()) {
            mm += i;
        }
        mm /= generatorTimings.size();
        m += mm;
        double mmm = 0;
        for (double i : regionTimings.v()) {
            mmm += i;
        }
        mmm /= regionTimings.size();
        m += mmm;

        fileText.add("Average Score: " + m);
        message("Score: " + Form.duration(m, 0));

        try {
            IO.writeAll(report, fileText.toString("\n"));
        } catch (IOException e) {
            Iris.reportError(e);
            e.printStackTrace();
        }

        success("Done! " + report.getPath());
    }

    @Decree(description = "Summon an Iris Entity", origin = DecreeOrigin.PLAYER)
    public void summon(
            @Param(description = "The Iris Entity to spawn", aliases = "e", name = "entity")
            IrisEntity entity
    ) {
        if (noStudio()){
            return;
        }
        success("Spawning entity");
        entity.spawn(engine(), player().getLocation().clone().add(0, 2, 0));
    }

    
}
