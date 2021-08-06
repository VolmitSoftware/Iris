package com.volmit.iris.core.command.pregen;

import com.volmit.iris.Iris;
import com.volmit.iris.core.gui.PregeneratorJob;
import com.volmit.iris.core.pregenerator.PregenTask;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.VolmitSender;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.checkerframework.checker.units.qual.K;

public class CommandIrisPregenCreate extends MortarCommand {

    public CommandIrisPregenCreate() {
        super("create", "c", "new", "+");
        requiresPermission(Iris.perm);
        setCategory("Pregen");
        setDescription("""
                Create a new pregeneration task.
                Command usage & examples:
                /iris pregen create [radius=<radius>] [width=<width>] [height=<height>] [x=<centerX>] [z=<centerZ>] [world=<world>] [-here]
                /iris pregen create radius=5000 x=10r z=10r world=IrisWorld
                /iris pregen create 5k -here
                
                <radius>:  Sets both width and height to a value.
                <x> & <z>: Give the center point of the pregeneration.
                <world>:   Specify a specific world name for generation as well (required for console)
                -here:     If added, the center location is set to your position (player only)
                
                For all numeric values (radius, centerX, etc.) you may use:
                c => 16, r => 512, k => 1000
                Example: entering '1000' is the same as '1k' (1 * 1000)
                https://docs.volmit.com/iris/pregeneration""");
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
        return "<radius> [width=<width>] [height=<height>] [x=<centerX>] [z=<centerZ>] [world=<world>] [-here]";
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
        boolean here = false;

        KList<String> failed = new KList<>();
        for (String a : args) {
            if (a.equals("-here")){
                here = true;
            } else if (a.contains("=")) {
                String pre = a.split("=")[0];
                String val = a.split("=")[1];
                if (pre.equals("world")){
                    world = Bukkit.getWorld(val);
                    if (world == null){
                        failed.add(a + " (invalid world)");
                        sender.sendMessage("Entered world is " + val + ", but that world does not exist.");
                        sender.sendMessage("Cancelling the command.");
                        sender.sendMessage(getDescription());
                        return true;
                    }
                } else if (!isVal(val)){
                    failed.add(a + " (non-value)");
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
                        default -> failed.add(a + " (no type)");
                    }
                }
            } else if (isVal(a)) {
                width = getVal(a);
                height = getVal(a);
            } else {
                failed.add(a + " (nothing)");
            }
        }

        if (width == -1 || height == -1){
            sender.sendMessage("Radius or (width & height) not specified. Cancelling...");
            sender.sendMessage(getDescription());
            return true;
        }

        if (world == null){
            if (sender.isPlayer()){
                world = sender.player().getWorld();
            } else {
                sender.sendMessage("Must specify world=<name> if sending from console! Cancelling...");
                sender.sendMessage(getDescription());
                return true;
            }
        }

        if (here){
            if (sender.isPlayer()) {
                x = sender.player().getLocation().getBlockX();
                z = sender.player().getLocation().getBlockZ();
            } else {
                sender.sendMessage("Specifying -here does not work from console!");
            }
        }

        KList<String> details = new KList<>(
                "Pregeneration details:",
                "   - World        > " + world.getName(),
                "   - Width/Height > " + width + "/" + height,
                "   - Center x,z   > " + x + "," + z,
                failed.isEmpty() ? "(No failed arguments)" : "FAILED ARGS:"
        );
        failed.forEach(s ->
            details.add("   - " + s)
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
            e.printStackTrace();
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
