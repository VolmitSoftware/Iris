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


public class CommandIrisPregenStart extends MortarCommand {

    private static final KList<String> argus = new KList<>("radius=", "x=", "z=");

    public CommandIrisPregenStart() {
        super("start", "create", "c", "new", "+");
        requiresPermission(Iris.perm);
        setCategory("Pregen");
        setDescription("Create a new pregeneration task.");
    }

    @Override
    public void addTabOptions(VolmitSender sender, String[] args, KList<String> list) {

        if (args.length == 0) {
            return;
        }

        // Add arguments
        argus.forEach(p -> {
            boolean hasArg = false;
            for (String arg : args) {
                if (!arg.contains("=") || !p.contains("=") || arg.equals("=")) {
                    continue;
                }
                if (arg.split("=")[0].equals(p.split("=")[0])) {
                    hasArg = true;
                    break;
                }
            }
            if (!hasArg) {
                list.add(p);
            }
        });

        // Add -here
        boolean hasHere = false;
        for (String arg : args) {
            if (arg.equals("-here")) {
                hasHere = true;
                break;
            }
        }
        if (!hasHere) {
            list.add("-here");
        }

        // Add Iris worlds
        if (Bukkit.getWorlds().isEmpty()) {
            list.add("world=<name>");
        } else {
            Bukkit.getWorlds().forEach(w -> {
                if (IrisToolbelt.isIrisWorld(w)) {
                    list.add("world=" + w.getName());
                }
            });
        }
    }

    @Override
    protected String getArgsUsage() {
        return "<radius> [x=<centerX>] [z=<centerZ>] [world=<world>] [-here]";
    }

    @Override
    public boolean handle(VolmitSender sender, String[] args) {

        if (args.length == 0) {
            sender.sendMessage(getHelp());
            return true;
        }

        if (PregeneratorJob.getInstance() != null) {
            sender.sendMessage("Pregeneration task already ongoing. You can stop it with /ir p stop.");
            sender.sendMessage("Cannot create new pregen while one is already going. Cancelling...");
            return true;
        }

        World world = null;
        int width = -1;
        int height = -1;
        int x = 0;
        int z = 0;
        boolean here = false;

        // Check all arguments
        KList<String> failed = new KList<>();
        for (String a : args) {
            if (a.equals("-here")) {
                here = true;
            } else if (a.contains("=")) {
                String pre = a.split("=")[0];
                String val = a.split("=")[1];
                if (pre.equals("world")) {
                    world = Bukkit.getWorld(val);
                    if (world == null) {
                        failed.add(a + " (invalid world)");
                        sender.sendMessage("Entered world is " + val + ", but that world does not exist.");
                        sender.sendMessage("Cancelling the command.");
                        sender.sendMessage(getHelp());
                        return true;
                    }
                } else if (!isVal(val)) {
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

        // Checking if a radius was specified or forgotten
        if (width == -1 || height == -1) {
            sender.sendMessage("Radius not specified! Cancelling...");
            sender.sendMessage(getHelp());
            return true;
        }

        // World specified & cancelling `-here` if it's another world
        if (world == null) {
            if (sender.isPlayer()) {
                world = sender.player().getWorld();
            } else {
                sender.sendMessage("Must specify world=<name> if sending from console! Cancelling...");
                sender.sendMessage(getHelp());
                return true;
            }
        } else if (sender.isPlayer() && !world.equals(sender.player().getWorld()) && here) {
            sender.sendMessage("Ignoring `-here` because `world=` is specified!");
            here = false;
        }

        // Checking if -here is used
        if (here) {
            if (sender.isPlayer()) {
                x = sender.player().getLocation().getBlockX();
                z = sender.player().getLocation().getBlockZ();
            } else {
                sender.sendMessage("Specifying -here does not work from console!");
            }
        }

        // Build details print
        StringBuilder details = new StringBuilder("Pregeneration details:")
                .append("\n")
                .append("   - World        > ")
                .append(world.getName())
                .append("\n")
                .append("   - Radius > ")
                .append(width)
                .append("(")
                .append(width * 2)
                .append(" by ")
                .append(height * 2)
                .append(")\n")
                .append("   - Center x,z   > ")
                .append(x)
                .append(",")
                .append(z)
                .append("\n")

                // Append failed args
                .append(failed.isEmpty() ? "(No failed arguments)\n" : "FAILED ARGS:\n");
        for (String s : failed) {
            details.append(s).append("\n");
        }

        // Start pregen and append info to details
        if (pregenerate(world, width, height, x, z)) {
            details.append("Successfully started pregen.");
        } else {
            details.append("Failed to start pregen. Doublecheck your arguments!");
        }

        // Send details
        sender.sendMessage(details.toString());

        return true;
    }

    /**
     * Pregenerate a
     *
     * @param world  world with a
     * @param width  and
     * @param height with center
     * @param x      and
     * @param z      coords
     * @return true if successful
     */
    private boolean pregenerate(World world, int width, int height, int x, int z) {
        try {
            IrisToolbelt.pregenerate(PregenTask
                    .builder()
                    .center(new Position2(x, z))
                    .width((width >> 9 + 1) * 2)
                    .height((height >> 9 + 1) * 2)
                    .build(), world);
        } catch (Throwable e) {
            Iris.reportError(e);
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * Get the ingeger value from an argument that may contain `c` `chunks` `r` `regions` or `k`<br>
     * "5r" returns 5 * 512 = 2560
     *
     * @param arg the string argument to parse into a value
     * @return the integer value result
     */
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
     *
     * @param arg string value
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

    /**
     * Get command help
     *
     * @return help string
     */
    private String getHelp() {
        return """
                Create a new pregeneration task.
                Command usage:
                /iris pregen create [radius=<radius>] [x=<centerX>] [z=<centerZ>] [world=<world>] [-here]
                                
                Examples:
                /iris pregen start 5k -here
                /iris pregen start radius=5000 x=10r z=10r world=IrisWorld
                /iris pregen start 10k world=WorldName
                                
                <radius>:  Sets both width and height to a value
                <x> & <z>: Give the center point of the pregen
                -here:     Sets the center x and z to the current location
                <world>:   Specify a world name for generation
                                
                In radius, x and z multiply the value by c => 16, r => 512, k => 1000
                Example: entering '1000' is the same as '1k' (1 * 1000)
                Make sure to check https://docs.volmit.com/iris/pregeneration for guidance""";
    }
}
