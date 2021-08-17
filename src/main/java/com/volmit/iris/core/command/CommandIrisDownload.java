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

package com.volmit.iris.core.command;

import com.volmit.iris.Iris;
import com.volmit.iris.core.service.StudioSVC;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.VolmitSender;
import com.volmit.iris.util.scheduling.J;

import java.util.Arrays;

public class CommandIrisDownload extends MortarCommand {
    public CommandIrisDownload() {
        super("download", "down", "dl");
        requiresPermission(Iris.perm.studio);
        setDescription("Download a project.");
        setCategory("Studio");
    }


    @Override
    public void addTabOptions(VolmitSender sender, String[] args, KList<String> list) {

    }

    @Override
    public boolean handle(VolmitSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("/iris dl " + C.BOLD + "<NAME> [BRANCH=master]");
            return true;
        }

        boolean trim = false;
        String branch = "master";

        for (String i : Arrays.copyOfRange(args, 1, args.length)) {
            if (i.equals("-t") || i.equals("--trim")) {
                trim = true;
                break;
            } else if (i.startsWith("-")) {
                sender.sendMessage("Invalid parameter.");
                sender.sendMessage("/iris dl " + C.BOLD + "<NAME> [BRANCH=master]");
                return true;
            } else {
                branch = i;
                if (branch.toLowerCase().startsWith("branch=")) branch = branch.substring(7);
            }
        }

        boolean btrim = trim;

        String pack = args[0];

        if (!pack.contains("/")) {
            pack = "IrisDimensions/" + pack;
        }

        final String finalPack = pack + "/" + branch;

        J.a(() -> Iris.service(StudioSVC.class).downloadSearch(sender, finalPack, btrim));

        return true;
    }

    @Override
    protected String getArgsUsage() {
        return "<name> [branch=master] [-t/--trim]";
    }
}
