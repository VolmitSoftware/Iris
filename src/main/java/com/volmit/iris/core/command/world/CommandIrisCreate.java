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
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.core.tools.IrisWorldCreator;
import com.volmit.iris.engine.object.dimensional.IrisDimension;
import com.volmit.iris.engine.platform.PlatformChunkGenerator;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.exceptions.IrisException;
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
            if (arg.equals("seed=")) {
                list.add("seed=random");
                list.add("seed=1234");
            } else if (arg.startsWith("seed=")) {
                seed = true;
            } else if (arg.equals("type=")) {
                for (File dim : packsFolder.listFiles()) {
                    if (dim.isDirectory()) {
                        list.add("type=" + dim.getName());
                    }
                }
                type = true;
            } else if (arg.startsWith("type=")) {
                type = true;
            }
        }

        if (!seed) {
            list.add("seed=random");
            list.add("seed=1234");
        }

        if (!type) {
            for (File dim : packsFolder.listFiles()) {
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

        String finalDimensionName = dimensionName;



        long finalSeed = seed;
        J.a(() -> {
            try {
                IrisToolbelt.createWorld()
                        .dimension(finalDimensionName)
                        .name(worldName)
                        .seed(finalSeed)
                        .sender(sender)
                        .studio(false)
                        .create();
            } catch (IrisException e) {
                e.printStackTrace();
                sender.sendMessage("Creation Failed! Check Console.");
            }
        });
        return true;
    }

    @Override
    protected String getArgsUsage() {
        return "<name> [type=<type>] [seed=<seed>]";
    }
}
