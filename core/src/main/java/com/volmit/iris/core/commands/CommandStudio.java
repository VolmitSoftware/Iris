/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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
import com.volmit.iris.core.project.IrisProject;
import com.volmit.iris.core.service.ConversionSVC;
import com.volmit.iris.core.service.StudioSVC;
import com.volmit.iris.core.tools.IrisNoiseBenchmark;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.*;
import com.volmit.iris.engine.platform.PlatformChunkGenerator;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.decree.DecreeContext;
import com.volmit.iris.util.decree.DecreeExecutor;
import com.volmit.iris.util.decree.DecreeOrigin;
import com.volmit.iris.util.decree.annotations.Decree;
import com.volmit.iris.util.decree.annotations.Param;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.function.Function2;
import com.volmit.iris.util.json.JSONArray;
import com.volmit.iris.util.json.JSONObject;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.math.Spiraler;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.plugin.VolmitSender;
import com.volmit.iris.util.scheduling.ChronoLatch;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.O;
import com.volmit.iris.util.scheduling.jobs.QueueJob;
import io.papermc.lib.PaperLib;
import org.bukkit.*;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitRunnable;
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
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
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

    @Decree(name = "regen", description = "Regenerate nearby chunks.", aliases = "rg", sync = true, origin = DecreeOrigin.PLAYER)
    public void regen(
            @Param(name = "radius", description = "The radius of nearby cunks", defaultValue = "5")
            int radius
    ) {
        if (IrisToolbelt.isIrisWorld(player().getWorld())) {
            VolmitSender sender = sender();
            J.a(() -> {
                DecreeContext.touch(sender);
                PlatformChunkGenerator plat = IrisToolbelt.access(player().getWorld());
                Engine engine = plat.getEngine();
                try {
                    Chunk cx = player().getLocation().getChunk();
                    KList<Runnable> js = new KList<>();
                    BurstExecutor b = MultiBurst.burst.burst();
                    b.setMulticore(false);
                    int rad = engine.getMantle().getRealRadius();
                    for (int i = -(radius + rad); i <= radius + rad; i++) {
                        for (int j = -(radius + rad); j <= radius + rad; j++) {
                            engine.getMantle().getMantle().deleteChunk(i + cx.getX(), j + cx.getZ());
                        }
                    }

                    for (int i = -radius; i <= radius; i++) {
                        for (int j = -radius; j <= radius; j++) {
                            int finalJ = j;
                            int finalI = i;
                            b.queue(() -> plat.injectChunkReplacement(player().getWorld(), finalI + cx.getX(), finalJ + cx.getZ(), (f) -> {
                                synchronized (js) {
                                    js.add(f);
                                }
                            }));
                        }
                    }

                    b.complete();
                    sender().sendMessage(C.GREEN + "Regenerating " + Form.f(js.size()) + " Sections");
                    QueueJob<Runnable> r = new QueueJob<>() {
                        final KList<Future<?>> futures = new KList<>();

                        @Override
                        public void execute(Runnable runnable) {
                            futures.add(J.sfut(runnable));

                            if (futures.size() > 64) {
                                while (futures.isNotEmpty()) {
                                    try {
                                        futures.remove(0).get();
                                    } catch (InterruptedException |
                                             ExecutionException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }

                        @Override
                        public String getName() {
                            return "Regenerating";
                        }
                    };
                    r.queue(js);
                    r.execute(sender());
                } catch (Throwable e) {
                    sender().sendMessage("Unable to parse view-distance");
                }
            });
        } else {
            sender().sendMessage(C.RED + "You must be in an Iris World to use regen!");
        }
    }

    @Decree(description = "Convert objects in the \"convert\" folder")
    public void convert() {
        Iris.service(ConversionSVC.class).check(sender());
        //IrisConverter.convertSchematics(sender());
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
        var provider = Iris.service(StudioSVC.class).getActiveProject().getActiveProvider();
        provider.getEngine().hotload();
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


    @Decree(description = "Get all structures in a radius of chunks", aliases = "dist", origin = DecreeOrigin.PLAYER)
    public void distances(@Param(description = "The radius in chunks") int radius) {
        var engine = engine();
        if (engine == null) {
            sender().sendMessage(C.RED + "Only works in an Iris world!");
            return;
        }
        var sender = sender();
        int d = radius * 2;
        KMap<String, KList<Position2>> data = new KMap<>();
        var multiBurst = new MultiBurst("Distance Sampler", Thread.MIN_PRIORITY);
        var executor = multiBurst.burst(radius * radius);

        sender.sendMessage(C.GRAY + "Generating data...");
        var loc = player().getLocation();
        int totalTasks = d * d;
        AtomicInteger completedTasks = new AtomicInteger(0);
        int c = J.ar(() -> {
            sender.sendProgress((double) completedTasks.get() / totalTasks, "Finding structures");
        }, 0);

        new Spiraler(d, d, (x, z) -> executor.queue(() -> {
            var struct = engine.getStructureAt(x, z);
            if (struct != null) {
                data.computeIfAbsent(struct.getLoadKey(), (k) -> new KList<>()).add(new Position2(x, z));
            }
            completedTasks.incrementAndGet();
        })).setOffset(loc.getBlockX(), loc.getBlockZ()).drain();

        executor.complete();
        multiBurst.close();
        J.car(c);

        for (var key : data.keySet()) {
            var list = data.get(key);
            KList<Long> distances = new KList<>(list.size() - 1);
            for (int i = 0; i < list.size(); i++) {
                var pos = list.get(i);
                double dist = Integer.MAX_VALUE;
                for (var p : list) {
                    if (p.equals(pos)) continue;
                    dist = Math.min(dist, Math.sqrt(Math.pow(pos.getX() - p.getX(), 2) + Math.pow(pos.getZ() - p.getZ(), 2)));
                }
                if (dist == Integer.MAX_VALUE) continue;
                distances.add(Math.round(dist * 16));
            }
            long[] array = new long[distances.size()];
            for (int i = 0; i < distances.size(); i++) {
                array[i] = distances.get(i);
            }
            Arrays.sort(array);
            long min = array.length > 0 ? array[0] : 0;
            long max = array.length > 0 ? array[array.length - 1] : 0;
            long sum = Arrays.stream(array).sum();
            long avg = array.length > 0 ? Math.round(sum / (double) array.length) : 0;
            String msg = "%s: %s => min: %s/max: %s -> avg: %s".formatted(key, list.size(), min, max, avg);
            sender.sendMessage(msg);
        }
        if (data.isEmpty()) {
            sender.sendMessage(C.RED + "No data found!");
        } else {
            sender.sendMessage(C.GREEN + "Done!");
        }
    }


    @Decree(description = "Render a world map (External GUI)", aliases = "render")
    public void map() {
        if (noGUI()) return;
        if (noStudio()) return;
        VisionGUI.launch(IrisToolbelt.access(player().getWorld()).getEngine(), 0);
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
        IrisNoiseBenchmark noiseBenchmark = new IrisNoiseBenchmark(dimension, sender());
        noiseBenchmark.runAll();
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
