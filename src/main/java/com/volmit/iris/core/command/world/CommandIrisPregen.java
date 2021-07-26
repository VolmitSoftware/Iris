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
import org.bukkit.entity.Player;

import java.awt.*;

public class CommandIrisPregen extends MortarCommand {
    public CommandIrisPregen() {
        super("pregen", "preg", "p");
        setDescription(
                """
                        Pregen this world with optional parameters:\s
                        '1k' = 1000 * 2 by 1000 * 2 blocks, '1c' = 2 by 2 chunks, and '1r' = 64 by 64 chunks.
                        If you are using the console or want to pregen a world you're not in:
                        also specify the name of the world. E.g. /ir pregen 5k world"""
        );
        requiresPermission(Iris.perm.studio);
        setCategory("Pregen");
    }

    @Override
    public void addTabOptions(VolmitSender sender, String[] args, KList<String> list) {
        list.add("stop");
        list.add("pause");
        list.add("resume");
        list.add("500");
        list.add("1000");
        list.add("10k");
        list.add("25k");
        list.add("10c");
        list.add("25c");
        list.add("5r");
        list.add("10r");
        for (World w : Bukkit.getServer().getWorlds()) {
            list.add(w.getName());
        }
    }

    @Override
    public boolean handle(VolmitSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("/iris pregen <radius|stop|pause|resume>");
            return true;
        }

        if (args[0].equalsIgnoreCase("stop") || args[0].equalsIgnoreCase("x")) {
            if (PregeneratorJob.shutdownInstance()) {
                sender.sendMessage("Stopped Pregen. Finishing last region file before shutting down...");
            } else {
                sender.sendMessage("No Active Pregens.");
            }
            return true;
        } else if (args[0].equalsIgnoreCase("pause") || args[0].equalsIgnoreCase("resume")) {
            if (PregeneratorJob.getInstance() != null) {
                PregeneratorJob.pauseResume();

                if (PregeneratorJob.isPaused()) {
                    sender.sendMessage("Pregen Paused");
                } else {
                    sender.sendMessage("Pregen Resumed");
                }
            } else {
                sender.sendMessage("No Active Pregens");
            }

            return true;
        } else if (sender.isPlayer()) {
            Player p = sender.player();
            World world;
            if (args.length != 2) {
                world = p.getWorld();
            } else {
                try {
                    world = Bukkit.getWorld(args[1]);
                } catch (Exception e) {
                    Iris.reportError(e);
                    sender.sendMessage("Could not find specified world");
                    sender.sendMessage("Please doublecheck your command. E.g. /ir pregen 5k world");
                    return true;
                }
            }
            try {
                IrisToolbelt.pregenerate(PregenTask
                        .builder()
                        .center(new Position2(0, 0))
                        .radius(((getVal(args[0]) >> 4) >> 5) + 1)
                        .build(), world);
            } catch (NumberFormatException e) {
                Iris.reportError(e);
                sender.sendMessage("Invalid argument in command");
                return true;
            } catch (NullPointerException e) {
                Iris.reportError(e);
                e.printStackTrace();
                sender.sendMessage("No radius specified (check error in console)");
            } catch (HeadlessException e) {
                Iris.reportError(e);
                sender.sendMessage("If you are seeing this and are using a hosted server, please turn off 'useServerLaunchedGUIs' in the settings");
            }

            return true;
        } else {
            if (args.length < 1) {
                sender.sendMessage("Please specify the radius of the pregen and the name of the world. E.g. /ir pregen 5k world");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage("Please specify the name of the world after the command. E.g. /ir pregen 5k world");
                return true;
            }
            World world = Bukkit.getWorld(args[1]);
            try {
                new PregeneratorJob(PregenTask
                        .builder()
                        .center(new Position2(0, 0))
                        .radius(((getVal(args[0]) >> 4) >> 5) + 1)
                        .build(),
                        new HybridPregenMethod(world, Runtime.getRuntime().availableProcessors()));
            } catch (NumberFormatException e) {
                Iris.reportError(e);
                sender.sendMessage("Invalid argument in command");
                return true;
            } catch (NullPointerException e) {
                Iris.reportError(e);
                sender.sendMessage("Not all required parameters specified");
            } catch (HeadlessException e) {
                Iris.reportError(e);
                sender.sendMessage("If you are seeing this and are using a hosted server, please turn off 'useServerLaunchedGUIs' in the settings");
            }

            return true;
        }
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

    @Override
    protected String getArgsUsage() {
        return "[radius]";
    }
}
