package com.volmit.iris.core.command.pregen;

import com.volmit.iris.Iris;
import com.volmit.iris.core.gui.PregeneratorJob;
import com.volmit.iris.core.pregenerator.PregenTask;
import com.volmit.iris.core.pregenerator.methods.HybridPregenMethod;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.VolmitSender;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.checkerframework.checker.units.qual.K;

import java.util.Arrays;

public class CommandIrisPregenCreate extends MortarCommand {

    public CommandIrisPregenCreate() {
        super("create", "c", "new", "+");
        requiresPermission(Iris.perm);
        setCategory("Pregen");
        setDescription("Create a new pregeneration task");
    }

    @Override
    public void addTabOptions(VolmitSender sender, String[] args, KList<String> list) {

        list.add("5000");
        list.add("size=5000 world=IrisWorld x=500 z=-1000");
        list.add("5000 world=IrisWorld x=500 z=-1000");
        list.add("world=IrisWorld x=500 z=-1000");
        for (World w : Bukkit.getServer().getWorlds()) {
            list.add(w.getName());
        }
    }

    @Override
    protected String getArgsUsage() {
        return null;
    }

    @Override
    public boolean handle(VolmitSender sender, String[] args) {

        if (PregeneratorJob.getInstance() != null) {
            sender.sendMessage("Pregeneration task already ongoing. You can stop it with /ir p stop");
            return true;
        }

        World world = null;
        int width = -1;
        int height = -1;
        int x = 0;
        int z = 0;

        KList<String> failed = new KList<>();
        for (String a : args) {
            if (a.contains("=")) {
                String pre = a.split("=")[0];
                String val = a.split("=")[1];
                if (pre.equals("world")){
                    world = Bukkit.getWorld(val);
                } else if (!isVal(val)){
                    sender.sendMessage("Parameters other than `world=<name>` require a number (+ c|chunk|r|region|k), given: '" + a + "' is invalid");
                } else {
                    switch (pre) {
                        case "width" -> width = getVal(val);
                        case "height" -> height = getVal(val);
                        case "radius" -> {
                            width = getVal(val);
                            height = getVal(val);
                        }
                        case "x" -> x = getVal(val);
                        case "z" -> z = getVal(val);
                    }
                }
            } else if (isVal(a)) {
                width = getVal(a);
                height = getVal(a);
            } else {
                failed.add(a);
            }
        }

        if (width == -1 || height == -1){
            sender.sendMessage("Size not specified");
            sender.sendMessage(getArgsUsage());
        }

        world = world == null ? sender.player().getWorld() : world;

        KList<String> details = new KList<>(
                "Pregeneration details:",
                "       - World        > " + world.getName(),
                "       - Width/Height > " + width + "/" + height,
                "       - Center x,z   > " + x + "," + z,
                failed.isEmpty() ? "(No failed arguments)" : "FAILED ARGS: " + failed
        );


        if (pregenerate(world, width, height, x, z)){
            sender.sendMessage("Successfully started pregen");
        } else {
            sender.sendMessage("Failed to start pregen. Doublecheck your arguments!");
        }
        sender.sendMessage(details.array());

        return true;
    }

    /**
     * Pregenerate a
     * @param world world with a
     * @param width and
     * @param height with center
     * @param x and
     * @param z coords
     * @return true if successful
     */
    private boolean pregenerate(World world, int width, int height, int x, int z){
        try {
            IrisToolbelt.pregenerate(PregenTask
                    .builder()
                    .center(new Position2(x, z))
                    .width(width >> 9 + 1)
                    .height(height >> 9 + 1)
                    .build(), world);
        } catch (Throwable e){
            Iris.reportError(e);
            return false;
        }
        return true;
    }

    private int getVal(String arg) {

        if (arg.toLowerCase().endsWith("c") || arg.toLowerCase().endsWith("chunks")) {
            return Integer.parseInt(arg.toLowerCase().replaceAll("\\Qc\\E", "").replaceAll("\\Qchunks\\E", "")) * 16;
        }

        if (arg.toLowerCase().endsWith("r") || arg.toLowerCase().endsWith("regions")) {
            return Integer.parseInt(arg.toLowerCase().replaceAll("\\Qr\\E", "").replaceAll("\\Qregions\\E", "")) * 512;
        }

        if (arg.toLowerCase().endsWith("k")) {
            return Integer.parseInt(arg.toLowerCase().replaceAll("\\Qk\\E", "")) * 1000;
        }

        return Integer.parseInt(arg.toLowerCase());
    }

    /**
     * Checks if the
     * @param arg argument
     * @return is valid -> true
     */
    private boolean isVal(String arg) {
        try {
            Integer.parseInt(
                    arg.toLowerCase()
                            .replace("chunks", "")
                            .replace("c", "")
                            .replace("regions", "")
                            .replace("r", "")
                            .replace("k", "")
            );
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }
}
