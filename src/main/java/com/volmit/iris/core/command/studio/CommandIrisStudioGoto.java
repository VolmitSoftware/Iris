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

package com.volmit.iris.core.command.studio;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisDataManager;
import com.volmit.iris.core.tools.IrisWorlds;
import com.volmit.iris.engine.framework.IrisAccess;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisRegion;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.VolmitSender;
import com.volmit.iris.util.scheduling.J;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CommandIrisStudioGoto extends MortarCommand {
    public CommandIrisStudioGoto() {
        super("goto", "find", "g");
        setDescription("Find any region or biome");
        requiresPermission(Iris.perm.studio);
        setCategory("World");
    }

    @Override
    public void addTabOptions(VolmitSender sender, String[] args, KList<String> list) {
        if ((args.length == 0 || args.length == 1) && sender.isPlayer() && IrisWorlds.isIrisWorld(sender.player().getWorld())) {
            IrisDataManager data = IrisWorlds.access(sender.player().getWorld()).getData();
            if (data == null) {
                sender.sendMessage("Issue when loading tab completions. No data found (?)");
            } else if (args.length == 0) {
                list.add(data.getBiomeLoader().getPossibleKeys());
                list.add(data.getRegionLoader().getPossibleKeys());
            } else if (args.length == 1) {
                list.add(data.getBiomeLoader().getPossibleKeys(args[0]));
                list.add(data.getRegionLoader().getPossibleKeys(args[0]));
            }
        }
    }

    @Override
    public boolean handle(VolmitSender sender, String[] args) {
        try {
            if (args.length < 1) {
                sender.sendMessage("/iris std goto " + getArgsUsage());
                return true;
            }

            if (sender.isPlayer()) {
                Player p = sender.player();
                World world = p.getWorld();

                if (!IrisWorlds.isIrisWorld(world)) {
                    sender.sendMessage("You must be in an iris world.");
                    return true;
                }

                IrisAccess g = IrisWorlds.access(world);
                IrisBiome b = IrisDataManager.loadAnyBiome(args[0]);
                IrisRegion r = IrisDataManager.loadAnyRegion(args[0]);

                if (b != null) {
                    J.a(() -> {
                        Location l = g.lookForBiome(b, 10000, (v) -> sender.sendMessage("Looking for " + C.BOLD + C.WHITE + b.getName() + C.RESET + C.GRAY + ": Checked " + Form.f(v) + " Places"));

                        if (l == null) {
                            sender.sendMessage("Couldn't find " + b.getName() + ".");
                        } else {
                            sender.sendMessage("Found " + b.getName() + "!");
                            J.s(() -> sender.player().teleport(l));
                        }
                    });
                } else if (r != null) {
                    J.a(() -> {
                        Location l = g.lookForRegion(r, 60000, (v) -> sender.sendMessage(C.BOLD + "" + C.WHITE + r.getName() + C.RESET + C.GRAY + ": Checked " + Form.f(v) + " Places"));

                        if (l == null) {
                            sender.sendMessage("Couldn't find " + r.getName() + ".");
                        } else {
                            sender.sendMessage("Found " + r.getName() + "!");
                            J.s(() -> sender.player().teleport(l));
                        }
                    });
                } else {
                    sender.sendMessage(args[0] + " is not a biome or region in this dimension. (Biome teleportation works best!");
                }

                return true;
            } else {
                sender.sendMessage("Players only.");
            }
        } catch (Throwable e) {
            Iris.reportError(e);
            Iris.error("Failed goto!");
            e.printStackTrace();
            sender.sendMessage("We cant seem to aquire a lock on the biome cache. Please report the error in the console to our github. Thanks!");
        }

        return true;
    }

    @Override
    protected String getArgsUsage() {
        return "[biome/region]";
    }

    private List<File> listf(String directoryName) {
        File directory = new File(directoryName);
        List<File> files = new ArrayList<>();

        // Get all files from a directory.
        File[] fList = directory.listFiles();
        if (fList != null)
            for (File file : fList) {
                if (file.isFile()) {
                    files.add(file);
                } else if (file.isDirectory()) {
                    files.addAll(listf(file.getAbsolutePath()));
                }
            }
        return files;
    }
}
