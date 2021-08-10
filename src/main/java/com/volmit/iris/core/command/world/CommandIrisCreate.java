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

package com.volmit.iris.core.command.world;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.core.tools.IrisWorldCreator;
import com.volmit.iris.engine.object.dimensional.IrisDimension;
import com.volmit.iris.engine.platform.PlatformChunkGenerator;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.VolmitSender;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.O;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;

import java.io.File;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

public class CommandIrisCreate extends MortarCommand {
    public CommandIrisCreate() {
        super("create", "c", "cr", "new", "+");
        requiresPermission(Iris.perm.studio);
        setCategory("Create");
        setDescription("Create a new Iris World!");
    }

    @Override
    public void addTabOptions(VolmitSender sender, String[] args, KList<String> list) {

        boolean seed = false;
        boolean type = false;

        File packsFolder = new File("plugins/Iris/packs/");
        packsFolder.mkdirs();

        for (String arg : args) {
            if (arg.equals("seed=")){
                list.add("seed=random");
                list.add("seed=1234");
            } else if (arg.startsWith("seed=")){
                seed = true;
            } else if (arg.equals("type=")){
                for (File dim : packsFolder.listFiles()){
                    if (dim.isDirectory()) {
                        list.add("type=" + dim.getName());
                    }
                }
                type = true;
            } else if (arg.startsWith("type=")){
                type = true;
            }
        }

        if (!seed){
            list.add("seed=random");
            list.add("seed=1234");
        }

        if (!type){
            for (File dim : packsFolder.listFiles()){
                if (dim.isDirectory()) {
                    list.add("type=" + dim.getName());
                }
            }
        }
    }

    @Override
    public boolean handle(VolmitSender sender, String[] args) {

        String worldName;
        File folder;
        String dimensionName;
        IrisDimension dimension;
        long seed;

        if (args.length < 1) {
            sender.sendMessage(getArgsUsage());
            return true;
        }

        worldName = args[0];

        if (worldName.equalsIgnoreCase("iris")) {
            sender.sendMessage("You cannot use the world name \"iris\" for creating worlds as Iris uses this directory for studio worlds.");
            sender.sendMessage("May we suggest the name \"IrisWorld\" instead?");
            return true;
        }

        folder = new File(worldName);

        if (folder.exists()) {
            sender.sendMessage("That world folder already exists!");
            return true;
        }

        dimensionName = IrisSettings.get().getGenerator().getDefaultWorldType();
        seed = new Random().nextLong(); //Random seed when creating a world

        for (String i : args) {
            dimensionName = i.startsWith("type=") ? i.split("\\Q=\\E")[1] : dimensionName;
            seed = i.startsWith("seed=") ? Long.parseLong(i.split("\\Q=\\E")[1]) : seed;
        }

        dimension = Iris.proj.installIntoWorld(sender, dimensionName, folder);

        if (dimension == null) {
            sender.sendMessage("Cannot find dimension '" + dimensionName + "'. Did you forget to /iris download " + dimensionName + "?");
            return true;
        }

        if (dimension.getEnvironment() == null){
            dimension.setEnvironment(World.Environment.NORMAL);
        }

        File iris = new File(folder, "iris");
        iris.mkdirs();

        onDone(sender, createWorld(sender, worldName, dimension, seed));
        return true;
    }

    @Override
    protected String getArgsUsage() {
        return "<name> [type=<type>] [seed=<seed>]";
    }

    /**
     * Ran when world is created
     * @param sender The sender to send updates to
     * @param world The created world
     */
    private void onDone(VolmitSender sender, World world){
        sender.sendMessage(world.getName() + " Spawn Area generated.");
        sender.sendMessage("You must remember to either have multiverse installed or use the Bukkit method to load this world with the Iris Generator on startup.");
        sender.sendMessage("Wiki: https://volmitsoftware.gitbook.io/iris/getting-started");

        if (sender.isPlayer()) {
            try {
                sender.player().teleport(world.getSpawnLocation());
            } catch (Throwable e) {
                Iris.reportError(e);
            }
        }

        O<Boolean> b = new O<>();
        b.set(true);

        J.a(() ->
        {
            while (!b.get()) {
                J.sleep(1000);
            }

            Bukkit.getScheduler().scheduleSyncDelayedTask(Iris.instance, () ->
            {
                world.save();
                sender.sendMessage("All Done!");
            });
        });
    }

    /**
     * Create a world with either Multiverse (preferred, if supported) or NMS
     * @param sender The sender to send updates to
     * @param worldName The name of the world to create
     * @param dimension The dimension to create the world with
     * @param seed The seed to use to generate
     * @return The created world
     */
    private World createWorld(VolmitSender sender, String worldName, IrisDimension dimension, long seed){
        if (Iris.linkMultiverseCore.isSupported()) {
            return createMultiverseWorld(sender, worldName, dimension, seed);
        } else {
            return createNMSWorld(sender, worldName, dimension, seed);
        }
    }

    /**
     * Create a world with Multiverse
     * @param sender The sender to send updates to
     * @param worldName The name of the world to create
     * @param dimension The dimension to create the world with
     * @param seed The seed to use to generate
     * @return The created world
     */
    public World createMultiverseWorld(VolmitSender sender, String worldName, IrisDimension dimension, long seed){

        if (!Iris.linkMultiverseCore.isSupported()){
            sender.sendMessage("A world was attempted to be created with Multiverse but it is not supported!");
            return null;
        }

        Iris.linkMultiverseCore.assignWorldType(worldName, dimension.getName());

        StringBuilder command = new StringBuilder("mv create")
                .append(worldName)
                .append(" ")
                .append(Iris.linkMultiverseCore.envName(dimension.getEnvironment()))
                .append(" -s ")
                .append(seed)
                .append(" -g Iris:")
                .append(dimension.getLoadKey());

        sender.sendMessage("Delegating " + command);
        Bukkit.dispatchCommand(sender, command.toString());
        return Bukkit.getWorld(worldName);
    }

    /**
     * Create a world using NMS
     * @param sender The sender to send updates to
     * @param worldName The name of the world to create
     * @param dimension The dimension to create the world with
     * @param seed The seed to use to generate
     * @return The created world
     */
    public World createNMSWorld(VolmitSender sender, String worldName, IrisDimension dimension, long seed){

        WorldCreator wc = new IrisWorldCreator()
                .dimension(dimension.getLoadKey())
                .name(worldName)
                .seed(seed)
                .productionMode()
                .create();
        PlatformChunkGenerator gen = (PlatformChunkGenerator) wc.generator();

        if (gen == null){
            sender.sendMessage("Failed to create generator! Gen is null!");
            return null;
        }

        AtomicReference<World> world = new AtomicReference<>();

        J.s(() -> {
            O<Boolean> done = new O<>();
            done.set(false);

            J.a(() ->
            {
                double last = 0;
                int req = 800;
                while (!done.get()) {

                    boolean shouldBeDone = false;
                    double v = (double) gen.getEngine().getGenerated() / req;

                    if (last > v || v > 1) {
                        shouldBeDone = true;
                        v = last;
                    } else {
                        last = v;
                    }

                    sender.sendMessage("Generating " + Form.pc(v) + (shouldBeDone ? " (Waiting on Server...)" : ""));
                    J.sleep(3000);
                }
            });

            world.set(INMS.get().createWorld(wc));
            done.set(true);
        });

        return world.get();
    }
}
