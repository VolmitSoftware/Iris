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
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.gui.NoiseExplorerGUI;
import com.volmit.iris.core.gui.VisionGUI;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.core.project.IrisProject;
import com.volmit.iris.core.service.ConversionSVC;
import com.volmit.iris.core.service.StudioSVC;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.*;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.collection.KSet;
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
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.math.Spiraler;
import com.volmit.iris.util.noise.CNG;
import com.volmit.iris.util.scheduling.O;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import io.papermc.lib.PaperLib;
import org.bukkit.*;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Objects;
import java.util.function.Supplier;

@Decree(name = "studio", aliases = {"std", "s"}, description = "Studio Commands", studio = true)
public class CommandStudio implements DecreeExecutor {
    private CommandFind find;
    private CommandEdit edit;

    public static String hrf(Duration duration) {
        return duration.toString().substring(2).replaceAll("(\\d[HMS])(?!$)", "$1 ").toLowerCase();
    }

    @Decree(description = "Download a project.", aliases = "dl")
    public void download(
            @Param(name = "pack", description = "The pack to download", defaultValue = "overworld", aliases = "project")
            String pack,
            @Param(name = "branch", description = "The branch to download from", defaultValue = "master")
            String branch,
            @Param(name = "trim", description = "Whether or not to download a trimmed version (do not enable when editing)", defaultValue = "false")
            boolean trim,
            @Param(name = "overwrite", description = "Whether or not to overwrite the pack with the downloaded one", aliases = "force", defaultValue = "false")
            boolean overwrite
    ) {
        new CommandIris().download(pack, branch, trim, overwrite);
    }

    @Decree(description = "Open a new studio world", aliases = "o", sync = true)
    public void open(
            @Param(defaultValue = "default", description = "The dimension to open a studio for", aliases = "dim")
            IrisDimension dimension,
            @Param(defaultValue = "1337", description = "The seed to generate the studio with", aliases = "s")
            long seed) {
        sender().sendMessage(C.GREEN + "Opening studio for the \"" + dimension.getName() + "\" pack (seed: " + seed + ")");
        Iris.service(StudioSVC.class).open(sender(), seed, dimension.getLoadKey());
    }

    @Decree(description = "Open VSCode for a dimension", aliases = {"vsc", "edit"})
    public void vscode(
            @Param(defaultValue = "default", description = "The dimension to open VSCode for", aliases = "dim")
            IrisDimension dimension
    ) {
        sender().sendMessage(C.GREEN + "Opening VSCode for the \"" + dimension.getName() + "\" pack");
        Iris.service(StudioSVC.class).openVSCode(sender(), dimension.getLoadKey());
    }

    @Decree(description = "Close an open studio project", aliases = {"x", "c"}, sync = true)
    public void close() {
        if (!Iris.service(StudioSVC.class).isProjectOpen()) {
            sender().sendMessage(C.RED + "No open studio projects.");
            return;
        }

        Iris.service(StudioSVC.class).close();
        sender().sendMessage(C.GREEN + "Project Closed.");
    }

    @Decree(description = "Create a new studio project", aliases = "+", sync = true)
    public void create(
            @Param(description = "The name of this new Iris Project.")
            String name,
            @Param(description = "Copy the contents of an existing project in your packs folder and use it as a template in this new project.", contextual = true)
            IrisDimension template) {
        if (template != null) {
            Iris.service(StudioSVC.class).create(sender(), name, template.getLoadKey());
        } else {
            Iris.service(StudioSVC.class).create(sender(), name);
        }
    }

    @Decree(description = "Get the version of a pack")
    public void version(
            @Param(defaultValue = "default", description = "The dimension get the version of", aliases = "dim", contextual = true)
            IrisDimension dimension
    ) {
        sender().sendMessage(C.GREEN + "The \"" + dimension.getName() + "\" pack has version: " + dimension.getVersion());
    }

    @Decree(description = "Convert objects in the \"convert\" folder")
    public void convert() {
        Iris.service(ConversionSVC.class).check(sender());
    }


    @Decree(description = "Execute a script", aliases = "run", origin = DecreeOrigin.PLAYER)
    public void execute(
            @Param(description = "The script to run")
            IrisScript script
    ) {
        engine().getExecution().execute(script.getLoadKey());
    }

    @Decree(description = "Open the noise explorer (External GUI)", aliases = {"nmap", "n"})
    public void noise() {
        if (noGUI()) return;
        sender().sendMessage(C.GREEN + "Opening Noise Explorer!");
        NoiseExplorerGUI.launch();
    }

    @Decree(description = "Charges all spawners in the area", aliases = "zzt", origin = DecreeOrigin.PLAYER)
    public void charge() {
        if (!IrisToolbelt.isIrisWorld(world())) {
            sender().sendMessage(C.RED + "You must be in an Iris world to charge spawners!");
            return;
        }
        sender().sendMessage(C.GREEN + "Charging spawners!");
        engine().getWorldManager().chargeEnergy();
    }

    @Decree(description = "Preview noise gens (External GUI)", aliases = {"generator", "gen"})
    public void explore(
            @Param(description = "The generator to explore", contextual = true)
            IrisGenerator generator,
            @Param(description = "The seed to generate with", defaultValue = "12345")
            long seed
    ) {
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

    @Decree(description = "Hotload a studio", aliases = {"reload", "h"})
    public void hotload() {
        if (!Iris.service(StudioSVC.class).isProjectOpen()) {
            sender().sendMessage(C.RED + "No studio world open!");
            return;
        }
        Iris.service(StudioSVC.class).getActiveProject().getActiveProvider().getEngine().hotload();
        sender().sendMessage(C.GREEN + "Hotloaded");
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
        } catch (Throwable e) {
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
    public void map(
            @Param(name = "world", description = "The world to open the generator for", contextual = true)
            World world
    ) {
        if (noGUI()) return;

        if (!IrisToolbelt.isIrisWorld(world)) {
            sender().sendMessage(C.RED + "You need to be in or specify an Iris-generated world!");
            return;
        }

        VisionGUI.launch(IrisToolbelt.access(world).getEngine(), 0);
        sender().sendMessage(C.GREEN + "Opening map!");
    }

    @Decree(description = "Package a dimension into a compressed format", aliases = "package")
    public void pkg(
            @Param(name = "dimension", description = "The dimension pack to compress", contextual = true, defaultValue = "default")
            IrisDimension dimension,
            @Param(name = "obfuscate", description = "Whether or not to obfuscate the pack", defaultValue = "false")
            boolean obfuscate,
            @Param(name = "minify", description = "Whether or not to minify the pack", defaultValue = "true")
            boolean minify
    ) {
        Iris.service(StudioSVC.class).compilePackage(sender(), dimension.getLoadKey(), obfuscate, minify);
    }

    @Decree(description = "Profiles the performance of a dimension", origin = DecreeOrigin.PLAYER)
    public void profile(
            @Param(description = "The dimension to profile", contextual = true, defaultValue = "default")
            IrisDimension dimension
    ) {
        File pack = dimension.getLoadFile().getParentFile().getParentFile();
        File report = Iris.instance.getDataFile("profile.txt");
        IrisProject project = new IrisProject(pack);
        IrisData data = IrisData.get(pack);

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

    @Decree(description = "Spawn an Iris entity", aliases = "summon", origin = DecreeOrigin.PLAYER)
    public void spawn(
            @Param(description = "The entity to spawn")
            IrisEntity entity,
            @Param(description = "The location to spawn the entity at", contextual = true)
            Vector location
    ) {
        if (!IrisToolbelt.isIrisWorld(player().getWorld())) {
            sender().sendMessage(C.RED + "You have to be in an Iris world to spawn entities properly. Trying to spawn the best we can do.");
        }
        entity.spawn(engine(), new Location(world(), location.getX(), location.getY(), location.getZ()));
    }

    @Decree(description = "Teleport to the active studio world", aliases = "stp", origin = DecreeOrigin.PLAYER, sync = true)
    public void tpstudio() {
        if (!Iris.service(StudioSVC.class).isProjectOpen()) {
            sender().sendMessage(C.RED + "No studio world is open!");
            return;
        }

        if (IrisToolbelt.isIrisWorld(world()) && engine().isStudio()) {
            sender().sendMessage(C.RED + "You are already in a studio world!");
            return;
        }

        sender().sendMessage(C.GREEN + "Sending you to the studio world!");
        player().teleport(Iris.service(StudioSVC.class).getActiveProject().getActiveProvider().getTarget().getWorld().spawnLocation());
        player().setGameMode(GameMode.SPECTATOR);
    }

    @Decree(description = "Update your dimension projects VSCode workspace")
    public void update(
            @Param(description = "The dimension to update the workspace of", contextual = true, defaultValue = "default")
            IrisDimension dimension
    ) {
        sender().sendMessage(C.GOLD + "Updating Code Workspace for " + dimension.getName() + "...");
        if (new IrisProject(dimension.getLoader().getDataFolder()).updateWorkspace()) {
            sender().sendMessage(C.GREEN + "Updated Code Workspace for " + dimension.getName());
        } else {
            sender().sendMessage(C.RED + "Invalid project: " + dimension.getName() + ". Try deleting the code-workspace file and try again.");
        }
    }

    @Decree(aliases = "find-objects", description = "Get information about nearby structures")
    public void objects() {
        if (!IrisToolbelt.isIrisWorld(player().getWorld())) {
            sender().sendMessage(C.RED + "You must be in an Iris world");
            return;
        }

        World world = player().getWorld();

        if (!IrisToolbelt.isIrisWorld(world)) {
            sender().sendMessage("You must be in an iris world.");
            return;
        }
        KList<Chunk> chunks = new KList<>();
        int bx = player().getLocation().getChunk().getX();
        int bz = player().getLocation().getChunk().getZ();

        try {
            Location l = player().getTargetBlockExact(48, FluidCollisionMode.NEVER).getLocation();

            int cx = l.getChunk().getX();
            int cz = l.getChunk().getZ();
            new Spiraler(3, 3, (x, z) -> chunks.addIfMissing(world.getChunkAt(x + cx, z + cz))).drain();
        } catch (Throwable e) {
            Iris.reportError(e);
        }

        new Spiraler(3, 3, (x, z) -> chunks.addIfMissing(world.getChunkAt(x + bx, z + bz))).drain();
        sender().sendMessage("Capturing IGenData from " + chunks.size() + " nearby chunks.");
        try {
            File ff = Iris.instance.getDataFile("reports/" + M.ms() + ".txt");
            PrintWriter pw = new PrintWriter(ff);
            pw.println("=== Iris Chunk Report ===");
            pw.println("== General Info ==");
            pw.println("Iris Version: " + Iris.instance.getDescription().getVersion());
            pw.println("Bukkit Version: " + Bukkit.getBukkitVersion());
            pw.println("MC Version: " + Bukkit.getVersion());
            pw.println("PaperSpigot: " + (PaperLib.isPaper() ? "Yup!" : "Nope!"));
            pw.println("Report Captured At: " + new Date());
            pw.println("Chunks: (" + chunks.size() + "): ");

            for (Chunk i : chunks) {
                pw.println("- [" + i.getX() + ", " + i.getZ() + "]");
            }

            int regions = 0;
            long size = 0;
            String age = "No idea...";

            try {
                for (File i : Objects.requireNonNull(new File(world.getWorldFolder(), "region").listFiles())) {
                    if (i.isFile()) {
                        size += i.length();
                    }
                }
            } catch (Throwable e) {
                Iris.reportError(e);
            }

            try {
                FileTime creationTime = (FileTime) Files.getAttribute(world.getWorldFolder().toPath(), "creationTime");
                age = hrf(Duration.of(M.ms() - creationTime.toMillis(), ChronoUnit.MILLIS));
            } catch (IOException e) {
                Iris.reportError(e);
            }

            KList<String> biomes = new KList<>();
            KList<String> caveBiomes = new KList<>();
            KMap<String, KMap<String, KList<String>>> objects = new KMap<>();

            for (Chunk i : chunks) {
                for (int j = 0; j < 16; j += 3) {

                    for (int k = 0; k < 16; k += 3) {

                        assert engine() != null;
                        IrisBiome bb = engine().getSurfaceBiome((i.getX() * 16) + j, (i.getZ() * 16) + k);
                        IrisBiome bxf = engine().getCaveBiome((i.getX() * 16) + j, (i.getZ() * 16) + k);
                        biomes.addIfMissing(bb.getName() + " [" + Form.capitalize(bb.getInferredType().name().toLowerCase()) + "] " + " (" + bb.getLoadFile().getName() + ")");
                        caveBiomes.addIfMissing(bxf.getName() + " (" + bxf.getLoadFile().getName() + ")");
                        exportObjects(bb, pw, engine(), objects);
                        exportObjects(bxf, pw, engine(), objects);
                    }
                }
            }

            regions = Objects.requireNonNull(new File(world.getWorldFolder().getPath() + "/region").list()).length;

            pw.println();
            pw.println("== World Info ==");
            pw.println("World Name: " + world.getName());
            pw.println("Age: " + age);
            pw.println("Folder: " + world.getWorldFolder().getPath());
            pw.println("Regions: " + Form.f(regions));
            pw.println("Chunks: max. " + Form.f(regions * 32 * 32));
            pw.println("World Size: min. " + Form.fileSize(size));
            pw.println();
            pw.println("== Biome Info ==");
            pw.println("Found " + biomes.size() + " Biome(s): ");

            for (String i : biomes) {
                pw.println("- " + i);
            }
            pw.println();

            pw.println("== Object Info ==");

            for (String i : objects.k()) {
                pw.println("- " + i);

                for (String j : objects.get(i).k()) {
                    pw.println("  @ " + j);

                    for (String k : objects.get(i).get(j)) {
                        pw.println("    * " + k);
                    }
                }
            }

            pw.println();
            pw.close();

            sender().sendMessage("Reported to: " + ff.getPath());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Iris.reportError(e);
        }
    }

    private void exportObjects(IrisBiome bb, PrintWriter pw, Engine g, KMap<String, KMap<String, KList<String>>> objects) {
        String n1 = bb.getName() + " [" + Form.capitalize(bb.getInferredType().name().toLowerCase()) + "] " + " (" + bb.getLoadFile().getName() + ")";
        int m = 0;
        KSet<String> stop = new KSet<>();
        for (IrisObjectPlacement f : bb.getObjects()) {
            m++;
            String n2 = "Placement #" + m + " (" + f.getPlace().size() + " possible objects)";

            for (String i : f.getPlace()) {
                String nn3 = i + ": [ERROR] Failed to find object!";

                try {
                    if (stop.contains(i)) {
                        continue;
                    }

                    File ff = g.getData().getObjectLoader().findFile(i);
                    BlockVector sz = IrisObject.sampleSize(ff);
                    nn3 = i + ": size=[" + sz.getBlockX() + "," + sz.getBlockY() + "," + sz.getBlockZ() + "] location=[" + ff.getPath() + "]";
                    stop.add(i);
                } catch (Throwable e) {
                    Iris.reportError(e);
                }

                String n3 = nn3;
                objects.computeIfAbsent(n1, (k1) -> new KMap<>())
                        .computeIfAbsent(n2, (k) -> new KList<>()).addIfMissing(n3);
            }
        }
    }

    /**
     * @return true if server GUIs are not enabled
     */
    private boolean noGUI() {
        if (!IrisSettings.get().getGui().isUseServerLaunchedGuis()) {
            sender().sendMessage(C.RED + "You must have server launched GUIs enabled in the settings!");
            return true;
        }
        return false;
    }

    /**
     * @return true if no studio is open or the player is not in one
     */
    private boolean noStudio() {
        if (!sender().isPlayer()) {
            sender().sendMessage(C.RED + "Players only!");
            return true;
        }
        if (!Iris.service(StudioSVC.class).isProjectOpen()) {
            sender().sendMessage(C.RED + "No studio world is open!");
            return true;
        }
        if (!engine().isStudio()) {
            sender().sendMessage(C.RED + "You must be in a studio world!");
            return true;
        }
        return false;
    }


    public void files(File clean, KList<File> files) {
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
