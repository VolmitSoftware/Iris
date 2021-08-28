package com.volmit.iris.core.decrees;

import com.google.gson.Gson;
import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.biome.IrisBiome;
import com.volmit.iris.engine.object.feature.IrisFeaturePositional;
import com.volmit.iris.engine.object.objects.IrisObject;
import com.volmit.iris.engine.object.objects.IrisObjectPlacement;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.decree.DecreeExecutor;
import com.volmit.iris.util.decree.DecreeOrigin;
import com.volmit.iris.util.decree.annotations.Decree;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.json.JSONObject;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.Spiraler;
import com.volmit.iris.util.plugin.VolmitSender;
import io.papermc.lib.PaperLib;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockVector;

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

@Decree(name = "what", aliases = "?", description = "Find information on the world around you", origin = DecreeOrigin.PLAYER)
public class DecWhat implements DecreeExecutor {

    @Decree(description = "Get information about the biome your are currently in")
    public void biome() {
        if (IrisToolbelt.isIrisWorld(player().getWorld())) {
            IrisBiome b = engine().getBiome(player().getLocation());
            sender().sendMessage(C.GREEN + "IrisBiome: " + b.getLoadKey() + " (" + b.getDerivative().name() + ")");
        } else {
            sender().sendMessage(C.YELLOW + "Non-Iris Biome: " + player().getLocation().getBlock().getBiome().name());
            if (player().getLocation().getBlock().getBiome().equals(Biome.CUSTOM)) {
                try {
                    sender().sendMessage(C.YELLOW + "Data Pack Biome: " + INMS.get().getTrueBiomeBaseKey(player().getLocation()) + " (ID: " + INMS.get().getTrueBiomeBaseId(INMS.get().getTrueBiomeBase(player().getLocation())) + ")");
                } catch (Throwable ee) {
                    Iris.reportError(ee);
                }
            }
        }
    }

    @Decree(description = "Get information about the block you are looking at")
    public void block() {
        Block target = player().getTargetBlockExact(128, FluidCollisionMode.NEVER);
        if (target == null) {
            sender().sendMessage(C.YELLOW + "Please look at a block, not at the sky");
            return;
        }

        BlockData bd = target.getBlockData();


        sender().sendMessage("Material: " + C.GREEN + bd.getMaterial().name());
        sender().sendMessage("Full: " + C.WHITE + bd.getAsString(true));

        if (B.isStorage(bd)) {
            sender().sendMessage(C.YELLOW + "* Storage Block (Loot Capable)");
        }

        if (B.isLit(bd)) {
            sender().sendMessage(C.YELLOW + "* Lit Block (Light Capable)");
        }

        if (B.isFoliage(bd)) {
            sender().sendMessage(C.YELLOW + "* Foliage Block");
        }

        if (B.isDecorant(bd)) {
            sender().sendMessage(C.YELLOW + "* Decorant Block");
        }

        if (B.isFluid(bd)) {
            sender().sendMessage(C.YELLOW + "* Fluid Block");
        }

        if (B.isFoliagePlantable(bd)) {
            sender().sendMessage(C.YELLOW + "* Plantable Foliage Block");
        }

        if (B.isSolid(bd)) {
            sender().sendMessage(C.YELLOW + "* Solid Block");
        }
    }

    @Decree(aliases = "nf", description = "Get the noise feature data in your chunk")
    public void features() {

        if (!IrisToolbelt.isIrisWorld(player().getWorld())){
            sender().sendMessage(C.RED + "Iris worlds only");
            return;
        }

        int n = 0;

        for (IrisFeaturePositional irisFeaturePositional : engine().getMantle().getFeaturesInChunk(player().getLocation().getChunk())) {
            sender().sendMessage("#" + n++ + " " + new JSONObject(new Gson().toJson(irisFeaturePositional)).toString(4));
        }
    }

    @Decree(description = "Get information about the item you are holding", sync = true)
    public void hand() {
        BlockData bd = player().getInventory().getItemInMainHand().getType().createBlockData();

        if (bd.getMaterial().equals(Material.AIR)){
            sender().sendMessage(C.YELLOW + "Please hold a block/item");
            return;
        }

        sender().sendMessage("Material: " + C.GREEN + bd.getMaterial().name());
        sender().sendMessage("Full: " + C.WHITE + bd.getAsString(true));

    }

    @Decree(aliases = "capture", description = "Get information about nearby structures")
    public void objects() {
        if (!IrisToolbelt.isIrisWorld(player().getWorld())){
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

                objects.compute(n1, (k1, v1) ->
                {
                    //noinspection ReplaceNullCheck
                    if (v1 == null) {
                        return new KMap<>();
                    }

                    return v1;
                }).compute(n2, (k, v) ->
                {
                    if (v == null) {
                        return new KList<String>().qaddIfMissing(n3);
                    }

                    v.addIfMissing(n3);
                    return v;
                });
            }
        }
    }

    public static String hrf(Duration duration) {
        return duration.toString().substring(2).replaceAll("(\\d[HMS])(?!$)", "$1 ").toLowerCase();
    }
}
