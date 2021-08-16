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
import com.volmit.iris.core.project.IrisProject;
import com.volmit.iris.core.project.loader.IrisData;
import com.volmit.iris.core.tools.IrisToolbelt;
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
import com.volmit.iris.engine.object.objects.IrisObject;
import com.volmit.iris.engine.object.regional.IrisRegion;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.data.B;
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
import com.volmit.iris.util.json.JSONArray;
import com.volmit.iris.util.json.JSONObject;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.noise.CNG;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.O;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import com.volmit.iris.util.scheduling.jobs.Job;
import com.volmit.iris.util.scheduling.jobs.JobCollection;
import com.volmit.iris.util.scheduling.jobs.QueueJob;
import com.volmit.iris.util.scheduling.jobs.SingleJob;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

@Decree(name = "studio", aliases = {"std", "s"}, description = "Studio Commands", studio = true)
public class DecStudio implements DecreeExecutor {
    @Decree(description = "Open a new studio world", aliases = "o", sync = true)
    public void open(
            @Param(defaultValue = "overworld", description = "The dimension to open a studio for", aliases = "dim")
                    IrisDimension dimension,
            @Param(defaultValue = "1337", description = "The seed to generate the studio with", aliases = "s")
                    long seed) {
        sender().sendMessage(C.GREEN + "Opening studio for the \"" + dimension.getName() + "\" pack (seed: " + seed + ")");
        Iris.proj.open(sender(), seed, dimension.getLoadKey());
    }

    @Decree(description = "Close an open studio project", aliases = "x", sync = true)
    public void close() {
        if (!Iris.proj.isProjectOpen()) {
            sender().sendMessage(C.RED + "No open studio projects.");
            return;
        }

        Iris.proj.close();
        sender().sendMessage(C.GREEN + "Project Closed.");
    }

    @Decree(description = "Create a new studio project", aliases = "+", sync = true)
    public void create(
            @Param(description = "The name of this new Iris Project.")
                    String name,
            @Param(description = "Copy the contents of an existing project in your packs folder and use it as a template in this new project.", contextual = true)
                    IrisDimension template)
    {
        if (template != null) {
            Iris.proj.create(sender(), name, template.getLoadKey());
        } else {
            Iris.proj.create(sender(), name);
        }
    }

    @Decree(description = "Clean an Iris Project, optionally beautifying JSON & fixing block ids with missing keys. Also rebuilds the vscode schemas. ")
    public void clean(
            @Param(description = "The project to update", contextual = true)
                    IrisDimension project,

            @Param(defaultValue = "true", description = "Filters all valid JSON files with a beautifier (indentation: 4)")
                    boolean beautify,

            @Param(name = "fix-ids", defaultValue = "true", description = "Fixes any block ids used such as \"dirt\" will be converted to \"minecraft:dirt\"")
                    boolean fixIds,

            @Param(name = "rewrite-objects", defaultValue = "false", description = "Imports all objects and re-writes them cleaning up positions & block data in the process.")
                    boolean rewriteObjects
    ) {
        KList<Job> jobs = new KList<>();
        KList<File> files = new KList<File>();
        files(Iris.instance.getDataFolder("packs", project.getLoadKey()), files);
        MultiBurst burst = new MultiBurst("Cleaner", Thread.MIN_PRIORITY, Runtime.getRuntime().availableProcessors() * 2);

        jobs.add(new SingleJob("Updating Workspace", () -> {
            if (!new IrisProject(Iris.proj.getWorkspaceFolder(project.getLoadKey())).updateWorkspace()) {
                sender().sendMessage(C.GOLD + "Invalid project: " + project.getLoadKey() + ". Try deleting the code-workspace file and try again.");
            }
            J.sleep(250);
        }));

        sender().sendMessage("Files: " + files.size());

        if(fixIds)
        {
            QueueJob<File> r = new QueueJob<>() {
                @Override
                public void execute(File f) {
                    try {
                        JSONObject p = new JSONObject(IO.readAll(f));
                        fixBlocks(p);
                        J.sleep(1);
                        IO.writeAll(f, p.toString(4));

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public String getName() {
                    return "Fixing IDs";
                }
            };

            r.queue(files);
            jobs.add(r);
        }

        if(beautify)
        {
            QueueJob<File> r = new QueueJob<>() {
                @Override
                public void execute(File f) {
                    try {
                        JSONObject p = new JSONObject(IO.readAll(f));
                        IO.writeAll(f, p.toString(4));
                        J.sleep(1);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public String getName() {
                    return "Beautify";
                }
            };

            r.queue(files);
            jobs.add(r);
        }

        if(rewriteObjects)
        {
            QueueJob<Runnable> q = new QueueJob<>() {
                @Override
                public void execute(Runnable runnable) {
                    runnable.run();
                    J.sleep(50);
                }

                @Override
                public String getName() {
                    return "Rewriting Objects";
                }
            };

            IrisData data = new IrisData(Iris.proj.getWorkspaceFolder(project.getLoadKey()));
            for (String f : data.getObjectLoader().getPossibleKeys()) {
                CompletableFuture<?> gg = burst.complete(() ->{
                    File ff = data.getObjectLoader().findFile(f);
                    IrisObject oo = new IrisObject(0, 0, 0);
                    try {
                        oo.read(ff);
                    } catch (Throwable e) {
                        Iris.error("FAILER TO READ: " + f);
                        return;
                    }

                    try {
                        oo.write(ff);
                    } catch (IOException e) {
                        Iris.error("FAILURE TO WRITE: " + oo.getLoadFile());
                    }
                });

                q.queue(() -> {
                    try {
                        gg.get();
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }
                });
            }

            jobs.add(q);
        }

        jobs.add(new SingleJob("Finishing Up", burst::shutdownNow));

        new JobCollection("Cleaning", jobs).execute(sender());
    }

    @Decree(description = "Get the version of a pack")
    public void version(
            @Param(defaultValue = "overworld", description = "The dimension get the version of", aliases = "dim", contextual = true)
                    IrisDimension dimension
    ) {
        sender().sendMessage(C.GREEN + "The \"" + dimension.getName() + "\" pack has version: " + dimension.getVersion());
    }

    @Decree(description = "Convert objects in the \"convert\" folder")
    public void convert() {
        Iris.convert.check(sender());
    }


    @Decree(description = "Edit the biome you are currently in", aliases = {"ebiome", "eb"}, origin = DecreeOrigin.PLAYER)
    public void editbiome(
            @Param(contextual = true, description = "The biome to edit")
            IrisBiome biome
    ) {
        if (noStudio()) return;

        try {
            Desktop.getDesktop().open(biome.getLoadFile());
        } catch (Throwable e) {
            Iris.reportError(e);
            sender().sendMessage(C.RED + "Cant find the file. Unsure why this happened.");
        }
    }

    @Decree(description = "Execute a script", aliases = "run", origin = DecreeOrigin.PLAYER)
    public void execute(
            @Param(description = "The script to run")
                    IrisScript script
    ) {
        engine().getExecution().execute(script.getLoadKey());
    }

    @Decree(description = "Open the noise explorer (External GUI)", aliases = "nmap")
    public void noise() {
        if (noGUI()) return;
        sender().sendMessage(C.GREEN + "Opening Noise Explorer!");
        NoiseExplorerGUI.launch();
    }

    @Decree(description = "Charges all spawners in the area", aliases = "zzt", origin = DecreeOrigin.PLAYER)
    public void charge() {
        engine().getWorldManager().chargeEnergy();
    }

    @Decree(description = "Preview noise gens (External GUI)", aliases = {"generator", "gen"})
    public void explore(
            @Param(description = "The generator to explore", contextual = true)
                    IrisGenerator generator,
            @Param(description = "The seed to generate with", defaultValue = "12345")
                    long seed
    ){
        if (noGUI()) return;
        sender().sendMessage(C.GREEN + "Opening Noise Explorer!");

        Supplier<Function2<Double, Double, Double>> l = () -> {

            if (generator == null) {
                return (x, z) -> 0D;
            }

            return (x, z) -> generator.getHeight(x, z, new RNG(seed).nextParallelRNG(3245).lmax());
        };
        NoiseExplorerGUI.launch(l, "Custom Generator");
    }

    @Decree(description = "Find any biome or region", aliases = {"goto", "g"}, origin = DecreeOrigin.PLAYER)
    public void find(
            @Param(description = "The biome to find")
                    IrisBiome biome,
            @Param(description = "The region to find")
                    IrisRegion region
    ){
        if (!IrisToolbelt.isIrisWorld(world())){
            sender().sendMessage(C.RED + "You must be in an Iris world to use this command!");
            return;
        }

        if (biome == null && region == null){
            sender().sendMessage(C.RED + "You must specify a biome or region!");
            return;
        }

        IrisPosition l = null;
        if (region != null) {
            l = engine().lookForRegion(region, 10000, (v) -> sender().sendMessage("Looking for the " + C.BOLD + C.WHITE + region.getName() + C.RESET + C.GRAY + " region: Checked " + Form.f(v) + " Places"));
            if (l == null) {
                sender().sendMessage(C.YELLOW + "Couldn't find the " + region.getName() + " region.");
            } else {
                sender().sendMessage(C.GREEN + "Found the " + region.getName() + " region!.");
            }
        }

        if (l == null && biome != null) {
            l = engine().lookForBiome(biome, 10000, (v) -> sender().sendMessage("Looking for the " + C.BOLD + C.WHITE + biome.getName() + C.RESET + C.GRAY + " biome: Checked " + Form.f(v) + " Places"));
            if (l == null) {
                sender().sendMessage(C.YELLOW + "Couldn't find the " + biome.getName() + " biome.");
            } else {
                sender().sendMessage(C.GREEN + "Found the " + biome.getName() + " biome!.");
            }
        }

        if (l == null) {
            sender().sendMessage(C.RED + "Could not find the region and / or biome you specified.");
            return;
        }

        final IrisPosition finalL = l;
        J.s(() -> player().teleport(finalL.toLocation(world())));
    }

    @Decree(description = "Hotload a studio", aliases = "reload", origin = DecreeOrigin.PLAYER)
    public void hotload() {
        if (noStudio()) return;

        access().hotload();
    }

    @Decree(description = "Show loot if a chest were right here", origin = DecreeOrigin.PLAYER, sync = true)
    public void loot(
            @Param(description = "Fast insertion of items in virtual inventory (may cause performance drop)", defaultValue = "false")
            boolean fast,
            @Param(description = "Whether or not to append to the inventory currently open (if false, clears opened inventory)", defaultValue = "true")
            boolean add
    ) {
        if (noStudio()) return;

        KList<IrisLootTable> tables = engine().getLootTables(RNG.r, player().getLocation().getBlock());
        Inventory inv = Bukkit.createInventory(null, 27 * 2);

        try {
            engine().addItems(true, inv, RNG.r, tables, InventorySlotType.STORAGE, player().getLocation().getBlockX(), player().getLocation().getBlockY(), player().getLocation().getBlockZ(), 1);
        } catch (Throwable e){
            Iris.reportError(e);
            sender().sendMessage(C.RED + "Cannot add items to virtual inventory because of: " + e.getMessage());
            return;
        }


        O<Integer> ta = new O<>();
        ta.set(-1);

        ta.set(Bukkit.getScheduler().scheduleSyncRepeatingTask(Iris.instance, () ->
        {
            if (!player().getOpenInventory().getType().equals(InventoryType.CHEST)) {
                Bukkit.getScheduler().cancelTask(ta.get());
                sender().sendMessage(C.GREEN + "Opened inventory!");
                return;
            }

            if (!add) {
                inv.clear();
            }

            engine().addItems(true, inv, new RNG(RNG.r.imax()), tables, InventorySlotType.STORAGE, player().getLocation().getBlockX(), player().getLocation().getBlockY(), player().getLocation().getBlockZ(), 1);
        }, 0, fast ? 5 : 35));

        sender().sendMessage(C.GREEN + "Opening inventory now!");
        player().openInventory(inv);
    }

    @Decree(description = "Render a world map (External GUI)", aliases = "render")
    public void map()
    {
        if (noStudio()) return;

        if (noGUI()) return;

        VisionGUI.launch(engine(), 0);
        sender().sendMessage(C.GREEN + "Opening map!");
    }

    @Decree(description = "Package a dimension into a compressed format", aliases = "package")
    public void pkg(
            @Param(name = "dimension", description = "The dimension pack to compress", contextual = true)
            IrisDimension dimension,
            @Param(name = "obfuscate", description = "Whether or not to obfuscate the pack", defaultValue = "false")
            boolean obfuscate,
            @Param(name = "minify", description = "Whether or not to minify the pack", defaultValue = "true")
            boolean minify
    ){
        Iris.proj.compilePackage(sender(), dimension.getLoadKey(), obfuscate, minify);
    }

    @Decree(description = "Profiles the performance of a dimension", origin = DecreeOrigin.PLAYER)
    public void profile(
            @Param(description = "The dimension to profile", contextual = true)
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

        sender().sendMessage("Calculating Performance Metrics for Noise generators");

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

        sender().sendMessage("Calculating Interpolator Timings...");

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

        sender().sendMessage("Processing Generator Scores: ");

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
        sender().sendMessage("Score: " + Form.duration(m, 0));

        try {
            IO.writeAll(report, fileText.toString("\n"));
        } catch (IOException e) {
            Iris.reportError(e);
            e.printStackTrace();
        }

        sender().sendMessage(C.GREEN + "Done! " + report.getPath());
    }

    @Decree(description = "Summon an Iris Entity", origin = DecreeOrigin.PLAYER)
    public void summon(
            @Param(description = "The Iris Entity to spawn")
            IrisEntity entity
    ) {
        if (!sender().isPlayer()){
            sender().sendMessage(C.RED + "Players only (this is a config error. Ask support to add DecreeOrigin.PLAYER to the command you tried to run)");
            return;
        }
        if (IrisToolbelt.isIrisWorld(world())){
            sender().sendMessage(C.RED + "You can only spawn entities in Iris worlds!");
            return;
        }
        sender().sendMessage(C.GREEN + "Spawning entity");
        entity.spawn(engine(), player().getLocation().clone().add(0, 2, 0));
    }

    @Decree(description = "Teleport to the active studio world", aliases = "stp", origin = DecreeOrigin.PLAYER, sync = true)
    public void tpstudio(){
        if (!Iris.proj.isProjectOpen()){
            sender().sendMessage(C.RED + "No studio world is open!");
            return;
        }

        if (IrisToolbelt.isIrisWorld(world()) && engine().isStudio()){
            sender().sendMessage(C.RED + "You are already in a studio world!");
            return;
        }

        sender().sendMessage(C.GREEN + "Sending you to the studio world!");
        player().teleport(Iris.proj.getActiveProject().getActiveProvider().getTarget().getWorld().spawnLocation());
        player().setGameMode(GameMode.SPECTATOR);
    }

    @Decree(description = "Update your dimension project")
    public void update(
        @Param(description = "The dimension to update the workspace of", contextual = true)
                IrisDimension dimension
    ){
        if (new IrisProject(dimension.getLoadFile().getParentFile().getParentFile()).updateWorkspace()) {
            sender().sendMessage(C.GREEN + "Updated Code Workspace for " + dimension.getName());
        } else {
            sender().sendMessage(C.RED + "Invalid project: " + dimension.getName() + ". Try deleting the code-workspace file and try again.");
        }
    }

    @Decree(description = "Get information about the world around you", origin = DecreeOrigin.PLAYER)
    public void what(
            @Param(description = "Whether or not to show dimension information", defaultValue = "true")
                    boolean dimension,
            @Param(description = "Whether or not to show region information", defaultValue = "true")
                    boolean region,
            @Param(description = "Whether or not to show biome information", defaultValue = "true")
                    boolean biome,
            @Param(description = "Whether or not to show information about the block you are looking at", defaultValue = "true")
                    boolean look,
            @Param(description = "Whether or not to show information about the block you are holding", defaultValue = "true")
                    boolean hand
    ){
        // Data
        BlockData handHeld = player().getInventory().getItemInMainHand().getType().createBlockData();
        Block targetBlock = player().getTargetBlockExact(128, FluidCollisionMode.NEVER);
        BlockData targetBlockData;
        if (targetBlock == null) {
            targetBlockData = null;
        } else {
            targetBlockData = targetBlock.getBlockData();
        }
        IrisBiome currentBiome = engine().getBiome(player().getLocation());
        IrisRegion currentRegion = engine().getRegion(player().getLocation());
        IrisDimension currentDimension = engine().getDimension();

        // Biome, region & dimension
        if (dimension) {
            sender().sendMessage(C.GREEN + "" + C.BOLD + "Current dimension:" + C.RESET + "" + C.WHITE + currentDimension.getName());
        }
        if (region) {
            sender().sendMessage(C.GREEN + "" + C.BOLD + "Current region:" + C.RESET + "" + C.WHITE + currentRegion.getName());
        }
        if (biome) {
            sender().sendMessage(C.GREEN + "" + C.BOLD + "Current biome:" + C.RESET + "" + C.WHITE + currentBiome.getName());
        }

        // Target
        if (targetBlockData == null){
            sender().sendMessage(C.RED + "Not looking at any block");
        } else if (look) {
            sender().sendMessage(C.GREEN + "" + C.BOLD + "Looked-at block information");

            sender().sendMessage("Material: " + C.GREEN + targetBlockData.getMaterial().name());
            sender().sendMessage("Full: " + C.WHITE + targetBlockData.getAsString(true));

            if (B.isStorage(targetBlockData)) {
                sender().sendMessage(C.YELLOW + "* Storage Block (Loot Capable)");
            }

            if (B.isLit(targetBlockData)) {
                sender().sendMessage(C.YELLOW + "* Lit Block (Light Capable)");
            }

            if (B.isFoliage(targetBlockData)) {
                sender().sendMessage(C.YELLOW + "* Foliage Block");
            }

            if (B.isDecorant(targetBlockData)) {
                sender().sendMessage(C.YELLOW + "* Decorant Block");
            }

            if (B.isFluid(targetBlockData)) {
                sender().sendMessage(C.YELLOW + "* Fluid Block");
            }

            if (B.isFoliagePlantable(targetBlockData)) {
                sender().sendMessage(C.YELLOW + "* Plantable Foliage Block");
            }

            if (B.isSolid(targetBlockData)) {
                sender().sendMessage(C.YELLOW + "* Solid Block");
            }
        }

        // Hand-held
        if (!handHeld.getMaterial().equals(Material.AIR)) {
            sender().sendMessage(C.YELLOW + "No block held");
        } else if (hand) {
            sender().sendMessage(C.GREEN + "" + C.BOLD + "Hand-held block information");

            sender().sendMessage("Material: " + C.GREEN + handHeld.getMaterial().name());
            sender().sendMessage("Full: " + C.WHITE + handHeld.getAsString(true));
        }
    }

    /**
     * @return true if server GUIs are not enabled
     */
    private boolean noGUI() {
        if (!IrisSettings.get().isUseServerLaunchedGuis()){
            sender().sendMessage(C.RED + "You must have server launched GUIs enabled in the settings!");
            return true;
        }
        return false;
    }

    /**
     * @return true if no studio is open or the player is not in one
     */
    private boolean noStudio(){
        if (!sender().isPlayer()){
            sender().sendMessage(C.RED + "Players only (this is a config error. Ask support to add DecreeOrigin.PLAYER to the command you tried to run)");
            return true;
        }
        if (!Iris.proj.isProjectOpen()){
            sender().sendMessage(C.RED + "No studio world is open!");
            return true;
        }
        if (!engine().isStudio()){
            sender().sendMessage(C.RED + "You must be in a studio world!");
            return true;
        }
        return false;
    }



    public void files(File clean, KList<File> files)
    {
        if (clean.isDirectory()) {
            for (File i : clean.listFiles()) {
                files(i, files);
            }
        } else if (clean.getName().endsWith(".json")) {
            try {
                files.add(clean);
            } catch (Throwable e) {
                Iris.reportError(e);
                Iris.error("Failed to beautify " + clean.getAbsolutePath() + " You may have errors in your json!");
            }
        }
    }

    private void fixBlocks(JSONObject obj) {
        for (String i : obj.keySet()) {
            Object o = obj.get(i);

            if (i.equals("block") && o instanceof String && !o.toString().trim().isEmpty() && !o.toString().contains(":")) {
                obj.put(i, "minecraft:" + o);
            }

            if (o instanceof JSONObject) {
                fixBlocks((JSONObject) o);
            } else if (o instanceof JSONArray) {
                fixBlocks((JSONArray) o);
            }
        }
    }

    private void fixBlocks(JSONArray obj) {
        for (int i = 0; i < obj.length(); i++) {
            Object o = obj.get(i);

            if (o instanceof JSONObject) {
                fixBlocks((JSONObject) o);
            } else if (o instanceof JSONArray) {
                fixBlocks((JSONArray) o);
            }
        }
    }
}
