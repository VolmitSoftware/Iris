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

package com.volmit.iris.manager.command.world;

import com.volmit.iris.Iris;
import com.volmit.iris.util.*;

import java.io.File;

public class CommandIrisUpdateWorld extends MortarCommand {
    public CommandIrisUpdateWorld() {
        super("update-world", "^world");
        requiresPermission(Iris.perm.studio);
        setDescription("Update a world from a project.");
        setCategory("Studio");
    }

    @Override
    public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {

    }

    @Override
    public boolean handle(MortarSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("/iris update-world " + C.BOLD + "<WORLD> <PROJECT>");
            return true;
        }

        boolean fresh = false;

        for (String i : args) {
            if (i.equalsIgnoreCase("--fresh-download")) {
                fresh = true;
                break;
            }
        }

        boolean bfre = fresh;

        J.a(() ->
        {
            File folder = new File(args[0]);
            folder.mkdirs();

            if (bfre) {
                Iris.proj.downloadSearch(sender, args[1], false, true);
            }

            Iris.proj.installIntoWorld(sender, args[1], folder);
        });

        return true;
    }

    @Override
    protected String getArgsUsage() {
        return "<world> <project>";
    }
}
